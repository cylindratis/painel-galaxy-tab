package br.casasala.painel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Native Jellyfin HTTP API adapter. All public operations must run off the UI thread. */
public final class JellyfinClient {
    private static final int PAGE_SIZE = 40;
    private static final int MAX_QUEUE = 200;
    private static final int MAX_RESPONSE = 4 * 1024 * 1024;
    private final String base, token, userId, deviceId;

    public JellyfinClient(String server, String accessToken, String user, String device) throws Exception {
        base = normalizeBase(server);
        token = cleanHeader(accessToken == null ? "" : accessToken);
        userId = user == null ? "" : user.trim();
        deviceId = validDeviceId(device);
    }

    /** Accepts a server root or a copied /web URL, retaining a reverse-proxy base path. */
    public static String normalizeBase(String input) throws Exception {
        if (input == null || input.trim().length() == 0) throw new Exception("Informe o endereço do servidor Jellyfin.");
        String value = input.trim();
        URL url;
        try { url = new URL(value); }
        catch (Exception ignored) { throw new Exception("Use o endereço completo, como http://192.0.2.10:8096."); }
        if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https")) || url.getHost().length() == 0 ||
            url.getUserInfo() != null) throw new Exception("Endereço Jellyfin inválido. Não inclua usuário ou senha na URL.");
        String path = url.getPath();
        int web = path.indexOf("/web/");
        if (web >= 0) path = path.substring(0, web);
        else if (path.endsWith("/web")) path = path.substring(0, path.length() - 4);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (web < 0 && !url.getPath().endsWith("/web") && (url.getQuery() != null || url.getRef() != null))
            throw new Exception("Informe apenas a raiz do servidor Jellyfin, sem parâmetros ou token.");
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), path).toExternalForm();
    }

    public static JSONObject initiateQuickConnect(String server, String device) throws Exception {
        JellyfinClient client = new JellyfinClient(server, "", "", device);
        try { return client.object("/QuickConnect/Initiate", null, "POST", new JSONObject()); }
        catch (HttpError error) {
            if (error.status == 401 || error.status == 403)
                throw new Exception("Quick Connect está desativado neste servidor. Ative-o no painel Jellyfin ou entre com usuário e senha.");
            throw error;
        }
    }

    public static JSONObject testServer(String server, String device) throws Exception {
        JSONObject info = new JellyfinClient(server, "", "", device).object("/System/Info/Public", null, "GET", null);
        if (info.optString("Id").length() == 0 && info.optString("ServerName").length() == 0)
            throw new Exception("O endereço respondeu, mas não identificou um servidor Jellyfin.");
        return info;
    }

    public static JSONObject pollQuickConnect(String server, String secret, String device) throws Exception {
        if (secret == null || secret.length() == 0 || secret.length() > 1024) throw new Exception("Solicite um novo código Quick Connect.");
        JellyfinClient client = new JellyfinClient(server, "", "", device);
        try { return client.object("/QuickConnect/Connect", values("Secret", secret), "GET", null); }
        catch (HttpError error) {
            if (error.status == 404) throw new Exception("O código Quick Connect expirou. Gere outro código.");
            if (error.status == 401) throw new Exception("Quick Connect está desativado no servidor.");
            throw error;
        }
    }

    public static JSONObject authenticateQuickConnect(String server, String secret, String device) throws Exception {
        if (secret == null || secret.length() == 0 || secret.length() > 1024) throw new Exception("Solicite um novo código Quick Connect.");
        return new JellyfinClient(server, "", "", device).object("/Users/AuthenticateWithQuickConnect", null,
                "POST", new JSONObject().put("Secret", secret));
    }

    public static JSONObject authenticateByName(String server, String username, String password, String device) throws Exception {
        if (username == null || username.trim().length() == 0) throw new Exception("Informe seu usuário Jellyfin.");
        JSONObject body = new JSONObject().put("Username", username.trim()).put("Pw", password == null ? "" : password);
        return new JellyfinClient(server, "", "", device).object("/Users/AuthenticateByName", null, "POST", body);
    }

    /** Same /v1 abstraction as the optional bridge, so the UI can switch transports. */
    public JSONObject request(String path, JSONObject body) throws Exception {
        if (token.length() == 0) throw new Exception("Conecte sua conta Jellyfin em Ajustes.");
        int question = path.indexOf('?');
        String route = question < 0 ? path : path.substring(0, question);
        Map<String, String> params = parseQuery(question < 0 ? "" : path.substring(question + 1));
        if (body == null && route.equals("/v1/sessions")) return new JSONObject().put("sessions", sessions());
        if (body == null && route.equals("/v1/library")) return library(params);
        if (body == null && route.startsWith("/v1/lyrics/")) {
            try{return object("/Audio/"+identifier(route.substring(11))+"/Lyrics",null,"GET",null);}
            catch(HttpError e){if(e.status==404)return new JSONObject().put("Lyrics",new JSONArray());throw e;}
        }
        if (body != null && route.equals("/v1/play")) return play(body);
        if (body != null && route.equals("/v1/command")) return command(body);
        if (body == null && route.equals("/v1/jellyfin/status")) {
            JSONObject user = object("/Users/Me", null, "GET", null);
            return new JSONObject().put("connected", true).put("user", user.optString("Name"));
        }
        throw new Exception("Operação Jellyfin não suportada.");
    }

    public byte[] art(String itemId) throws Exception {
        return transport("/Items/" + identifier(itemId) + "/Images/Primary",
            values("MaxWidth", "420", "MaxHeight", "630", "Quality", "75", "Format", "Jpg"), "GET", null);
    }

    public byte[] bytes(String path) throws Exception {
        if (!path.startsWith("/v1/art/")) throw new Exception("Imagem inválida.");
        return art(path.substring("/v1/art/".length()));
    }

    public JSONObject item(String id) throws Exception { return object("/Items/"+identifier(id),userQuery(),"GET",null); }

    public JSONArray audioQueue(JSONObject chosen) throws Exception {
        String kind=chosen.optString("Type"),id=identifier(chosen.optString("Id"));
        if(kind.equals("Audio"))return new JSONArray().put(item(id));
        if(!kind.equals("MusicAlbum") && !kind.equals("Playlist") && !kind.equals("MusicArtist")&&!kind.equals("MusicGenre"))throw new Exception("No tablet, escolha música, álbum ou playlist. Para vídeos selecione a TV.");
        Map<String,String> q=userQuery();q.put("Limit","200");q.put("IncludeItemTypes","Audio");q.put("Recursive","true");q.put("SortBy",kind.equals("MusicArtist")?"Album,ParentIndexNumber,IndexNumber,SortName":"ParentIndexNumber,IndexNumber,SortName");
        String route="/Items";
        if(kind.equals("Playlist")){route="/Playlists/"+id+"/Items";q.remove("SortBy");}
        else q.put(kind.equals("MusicArtist")?"ArtistIds":kind.equals("MusicGenre")?"GenreIds":"ParentId",id);
        JSONArray out=new JSONArray();int start=0;
        while(true){q.put("StartIndex",String.valueOf(start));JSONObject raw=object(route,q,"GET",null);JSONArray list=raw.optJSONArray("Items");if(raw.optInt("TotalRecordCount")>10000)throw new Exception("Esta seleção passa de 10 mil músicas. Escolha um álbum para caber na memória do tablet.");
        for(int i=0;list!=null&&i<list.length();i++){JSONObject track=list.getJSONObject(i);if("Audio".equals(track.optString("Type")))out.put(pick(track,new String[]{"Id","Type","Name","Artists","Album","AlbumId","RunTimeTicks","ImageTags","IndexNumber","ParentIndexNumber"}));}int count=list==null?0:list.length();start+=count;if(count==0||start>=raw.optInt("TotalRecordCount",start))break;if(start>=10000)throw new Exception("Seleção muito grande para o tablet.");}
        if(out.length()==0)throw new Exception("Nenhuma música nesta seleção.");
        return out;
    }

    public JSONObject voiceSelection(String term,String mode)throws Exception{
        if(token.length()==0)throw new Exception("Conecte o Jellyfin em Ajustes.");Map<String,String> q=userQuery();q.put("SearchTerm",term);q.put("Limit","100");q.put("Recursive","true");String route=mode.equals("artist")?"/Artists":mode.equals("genre")?"/MusicGenres":"/Items";if(mode.equals("track"))q.put("IncludeItemTypes","Audio");else if(mode.equals("genre"))q.put("IncludeItemTypes","Audio");
        JSONObject raw=object(route,q,"GET",null);JSONArray list=raw.optJSONArray("Items");JSONObject found=null;int matches=0;for(int i=0;list!=null&&i<list.length();i++){JSONObject item=list.getJSONObject(i);if(VoiceIntent.fold(item.optString("Name")).equals(VoiceIntent.fold(term))){found=item;matches++;}}if(matches>1)throw new Exception("Há mais de uma seleção com esse nome. Escolha na biblioteca.");if(found==null&&list!=null&&list.length()==1)found=list.getJSONObject(0);if(found==null)throw new Exception(list!=null&&list.length()>1?"Encontrei vários resultados. Diga o nome completo ou escolha na biblioteca.":"Não encontrei essa seleção no Jellyfin. Confira os nomes e gêneros da biblioteca.");return new JSONObject(found.toString()).put("Type",mode.equals("genre")?"MusicGenre":mode.equals("artist")?"MusicArtist":"Audio");
    }

    public String audioUrl(String id,int startMs) throws Exception {
        return base+"/Audio/"+identifier(id)+"/stream.mp3?AudioCodec=mp3&AudioBitRate=128000&Static=false&Container=mp3&DeviceId="+URLEncoder.encode(deviceId,"UTF-8")
            +"&StartTimeTicks="+((long)startMs*10000)+"&ApiKey="+URLEncoder.encode(token,"UTF-8");
    }

    private JSONArray sessions() throws Exception {
        Map<String, String> params = values("ActiveWithinSeconds", "300");
        if (userId.length() > 0) params.put("ControllableByUserId", identifier(userId));
        Object payload = json("/Sessions", params, "GET", null);
        if (!(payload instanceof JSONArray)) throw new Exception("O servidor não retornou a lista de sessões Jellyfin.");
        JSONArray source = (JSONArray) payload, result = new JSONArray();
        String[] keys = {"Id", "DeviceId", "DeviceName", "Client", "NowPlayingItem", "PlayState", "SupportedCommands", "PlayableMediaTypes"};
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null || !item.optBoolean("SupportsRemoteControl") || deviceId.equals(item.optString("DeviceId"))) continue;
            result.put(pick(item, keys));
        }
        return result;
    }

    private JSONObject session(String id) throws Exception {
        identifier(id);
        JSONArray found = sessions();
        for (int i = 0; i < found.length(); i++) {
            JSONObject item = found.optJSONObject(i);
            if (item != null && id.equals(item.optString("Id"))) return item;
        }
        throw new Exception("Abra o Jellyfin na TV e selecione a TV novamente. A sessão pode ter mudado ou não permitir controle remoto.");
    }

    private JSONObject library(Map<String, String> params) throws Exception {
        String kind = get(params, "kind", "Audio");
        if (!(kind.equals("Audio") || kind.equals("MusicAlbum") || kind.equals("Playlist") || kind.equals("MusicArtist") ||
              kind.equals("Movie") || kind.equals("Series") || kind.equals("Season") || kind.equals("Episode")))
            throw new Exception("Categoria de biblioteca inválida.");
        int start = boundedNumber(get(params, "start", "0"), 0, 1000000);
        Map<String, String> query = userQuery();
        query.put("Limit", String.valueOf(PAGE_SIZE));
        query.put("StartIndex", String.valueOf(start));
        query.put("SortBy", kind.equals("Season") || kind.equals("Episode") ? "ParentIndexNumber,IndexNumber,SortName" : "SortName");
        query.put("SortOrder", "Ascending");
        query.put("Fields", "PrimaryImageAspectRatio,Overview");
        query.put("ImageTypeLimit", "1");
        query.put("EnableImageTypes", "Primary");
        String search = get(params, "q", "");
        if (search.length() > 200) search = search.substring(0, 200);
        if (search.length() > 0) query.put("SearchTerm", search);
        String parent = get(params, "parent", "");
        String endpoint = "/Items";
        if (get(params, "playlist", "").length() > 0) {
            endpoint = "/Playlists/" + identifier(params.get("playlist")) + "/Items";
            query.remove("SortBy");
            query.remove("SortOrder");
        } else if (kind.equals("MusicArtist")) {
            endpoint = "/Artists";
            if (parent.length() > 0) query.put("ParentId", identifier(parent));
        } else {
            query.put("IncludeItemTypes", kind);
            query.put("Recursive", kind.equals("Season") ? "false" : "true");
            if (parent.length() > 0) query.put("ParentId", identifier(parent));
            if (get(params, "artist", "").length() > 0) query.put("ArtistIds", identifier(params.get("artist")));
        }
        JSONObject payload = object(endpoint, query, "GET", null);
        JSONArray source = payload.optJSONArray("Items"), result = new JSONArray();
        String[] keys = {"Id", "Name", "Type", "Artists", "Album", "RunTimeTicks", "ImageTags", "ProductionYear",
                         "IndexNumber", "ParentIndexNumber", "SeriesName", "SeriesId", "SeasonId", "AlbumId", "Overview", "UserData"};
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item != null) result.put(pick(item, keys));
            }
        }
        return new JSONObject().put("items", result).put("total", payload.optInt("TotalRecordCount", result.length())).put("start", start);
    }

    private JSONObject play(JSONObject body) throws Exception {
        String sessionId = identifier(body.optString("session_id"));
        String itemId = identifier(body.optString("item_id"));
        JSONObject target = session(sessionId);
        JSONObject item = object("/Items/" + itemId, userQuery(), "GET", null);
        String kind = item.optString("Type");
        List<String> ids = new ArrayList<String>();
        boolean audio = kind.equals("Audio") || kind.equals("MusicAlbum") || kind.equals("Playlist") || kind.equals("MusicArtist");
        if (kind.equals("Audio") || kind.equals("Movie") || kind.equals("Episode")) {
            ids.add(itemId);
        } else if (kind.equals("MusicAlbum") || kind.equals("Playlist") || kind.equals("MusicArtist")) {
            Map<String, String> query = userQuery();
            query.put("Limit", String.valueOf(MAX_QUEUE + 1));
            String endpoint;
            if (kind.equals("Playlist")) endpoint = "/Playlists/" + itemId + "/Items";
            else {
                endpoint = "/Items";
                query.put("IncludeItemTypes", "Audio");
                query.put("Recursive", "true");
                query.put("SortBy", "ParentIndexNumber,IndexNumber,SortName");
                query.put("SortOrder", "Ascending");
                query.put(kind.equals("MusicArtist") ? "ArtistIds" : "ParentId", itemId);
            }
            int start = 0;
            while (true) {
                query.put("StartIndex", String.valueOf(start));
                JSONObject page = object(endpoint, query, "GET", null);
                JSONArray tracks = page.optJSONArray("Items");
                int count = tracks == null ? 0 : tracks.length();
                for (int i = 0; i < count; i++) {
                    JSONObject track = tracks.optJSONObject(i);
                    if (track != null && "Audio".equals(track.optString("Type"))) ids.add(identifier(track.optString("Id")));
                }
                start += count;
                if (ids.size() > MAX_QUEUE) throw new Exception("Escolha uma seleção de até 200 músicas para tocar de uma vez.");
                if (count == 0 || start >= page.optInt("TotalRecordCount", start)) break;
                if (start >= MAX_QUEUE) throw new Exception("Escolha uma seleção de até 200 itens para tocar de uma vez.");
            }
        } else if (kind.equals("Series") || kind.equals("Season")) {
            throw new Exception("Abra a série e escolha um episódio para reproduzir.");
        } else throw new Exception("Este item não pode ser reproduzido pelo painel.");
        if (ids.isEmpty()) throw new Exception("Nenhuma faixa de áudio foi encontrada nessa seleção.");
        JSONArray mediaTypes = target.optJSONArray("PlayableMediaTypes");
        if (mediaTypes != null && mediaTypes.length() > 0 && !contains(mediaTypes, audio ? "Audio" : "Video"))
            throw new Exception("O cliente Jellyfin selecionado não anuncia suporte a este tipo de mídia.");
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) combined.append(',');
            combined.append(ids.get(i));
        }
        object("/Sessions/" + sessionId + "/Playing", values("ItemIds", combined.toString(), "PlayCommand", "PlayNow"), "POST", null);
        return new JSONObject().put("accepted", true).put("tracks", ids.size()).put("message", "Comando enviado à TV.");
    }

    private JSONObject command(JSONObject body) throws Exception {
        String id = identifier(body.optString("session_id"));
        JSONObject target = session(id);
        String command = body.optString("command");
        String endpoint;
        if(command.equals("Seek")) {
            JSONObject state=target.optJSONObject("PlayState"),item=target.optJSONObject("NowPlayingItem");
            long ticks=body.optLong("position_ticks",-1);
            if(state==null || !state.optBoolean("CanSeek") || item==null)throw new Exception("A TV não permite avançar nesta reprodução.");
            if(ticks<0 || ticks>item.optLong("RunTimeTicks"))throw new Exception("Posição de reprodução inválida.");
            object("/Sessions/"+id+"/Playing/Seek",values("SeekPositionTicks",String.valueOf(ticks)),"POST",null);
            return new JSONObject().put("accepted",true);
        }
        if (command.equals("Pause") || command.equals("Unpause") || command.equals("Stop") || command.equals("NextTrack") || command.equals("PreviousTrack"))
            endpoint = "/Sessions/" + id + "/Playing/" + command;
        else if (command.equals("VolumeUp") || command.equals("VolumeDown") || command.equals("Mute") || command.equals("Unmute")) {
            if (!contains(target.optJSONArray("SupportedCommands"), command))
                throw new Exception("Este cliente Jellyfin não anuncia esse controle de volume.");
            endpoint = "/Sessions/" + id + "/Command/" + command;
        } else throw new Exception("Comando Jellyfin inválido.");
        object(endpoint, null, "POST", null);
        return new JSONObject().put("accepted", true).put("message", "Comando enviado à TV.");
    }

    private Map<String, String> userQuery() throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (userId.length() > 0) result.put("UserId", identifier(userId));
        return result;
    }

    private JSONObject object(String path, Map<String, String> query, String method, JSONObject body) throws Exception {
        Object value = json(path, query, method, body);
        if (!(value instanceof JSONObject)) throw new Exception("Resposta Jellyfin incompatível.");
        return (JSONObject)value;
    }

    private Object json(String path, Map<String, String> query, String method, JSONObject body) throws Exception {
        byte[] data = transport(path, query, method, body);
        if (data.length == 0) return new JSONObject();
        try { return new JSONTokener(new String(data, "UTF-8")).nextValue(); }
        catch (Exception ignored) { throw new Exception("O endereço não retornou a API Jellyfin. Confira o servidor e o prefixo da URL."); }
    }

    private byte[] transport(String path, Map<String, String> query, String method, JSONObject body) throws Exception {
        StringBuilder full = new StringBuilder(base).append(path);
        if (query != null && !query.isEmpty()) {
            full.append('?');
            boolean first = true;
            for (Map.Entry<String, String> pair : query.entrySet()) {
                if (pair.getValue() == null) continue;
                if (!first) full.append('&');
                first = false;
                full.append(URLEncoder.encode(pair.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(pair.getValue(), "UTF-8"));
            }
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(full.toString()).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json,image/jpeg");
            connection.setRequestProperty("Authorization", authorizationHeader());
            if (method.equals("POST")) {
                byte[] data = body == null ? new byte[0] : body.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(data.length);
                OutputStream out = connection.getOutputStream();
                try { out.write(data); } finally { out.close(); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == 401) throw new HttpError(status, "O Jellyfin recusou o login. Conecte novamente por Quick Connect ou confira usuário e senha.");
                if (status == 403) throw new HttpError(status, "O usuário Jellyfin não tem permissão para esta operação ou para controlar a TV.");
                if (status >= 300 && status < 400) throw new HttpError(status, "O endereço redireciona para outro local. Use a URL direta do servidor Jellyfin.");
                if (status == 404) throw new HttpError(status, "O servidor não encontrou este recurso Jellyfin. Confira o endereço e atualize a seleção.");
                throw new HttpError(status, "O Jellyfin respondeu HTTP " + status + ". Tente novamente em instantes.");
            }
            InputStream stream = connection.getInputStream();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    data.write(buffer, 0, read);
                    if (data.size() > MAX_RESPONSE) throw new Exception("A resposta Jellyfin excedeu o limite do painel.");
                }
            } finally { stream.close(); }
            return data.toByteArray();
        } catch (SSLException error) {
            throw new Exception("Este Android antigo não conseguiu negociar o HTTPS do Jellyfin. Use o endereço HTTP na rede local ou a ponte opcional, sem desativar certificados.");
        } catch (SocketTimeoutException error) {
            throw new Exception("O Jellyfin demorou para responder. Confira se o servidor está ligado e acessível pelo Wi-Fi.");
        } catch (IOException error) {
            throw new Exception("Não foi possível acessar o Jellyfin. Confira o endereço, o Wi-Fi e se o servidor está ligado.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Standard header works with legacy authorization disabled (Jellyfin 12+). */
    public String authorizationHeader() {
        return "MediaBrowser Client=\"Painel\", Device=\"Galaxy Tab\", DeviceId=\""+deviceId+"\", Version=\"0.8.0\""+(token.length()>0?", Token=\""+token.replace("\\","\\\\").replace("\"","\\\"")+"\"":"");
    }

    private static String cleanHeader(String value) throws Exception {
        if (value.length() > 4096) throw new Exception("Credencial Jellyfin inválida.");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || ch > 126) throw new Exception("Credencial Jellyfin inválida.");
        }
        return value;
    }

    private static String validDeviceId(String value) throws Exception {
        return identifier(value == null ? "" : value);
    }

    private static String identifier(String value) throws Exception {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw new Exception("Identificador Jellyfin inválido.");
        return value;
    }

    private static int boundedNumber(String input, int low, int high) throws Exception {
        try {
            int value = Integer.parseInt(input);
            if (value >= low && value <= high) return value;
        } catch (NumberFormatException ignored) { }
        throw new Exception("Página de biblioteca inválida.");
    }

    private static Map<String, String> parseQuery(String text) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (text.length() == 0) return result;
        if (text.length() > 16384) throw new Exception("Consulta muito grande.");
        String[] entries = text.split("&");
        for (int i = 0; i < entries.length; i++) {
            int equals = entries[i].indexOf('=');
            String key = equals < 0 ? entries[i] : entries[i].substring(0, equals);
            String value = equals < 0 ? "" : entries[i].substring(equals + 1);
            result.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return result;
    }

    private static String get(Map<String, String> params, String key, String fallback) {
        String value = params.get(key);
        return value == null ? fallback : value;
    }

    private static Map<String, String> values(String... entries) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < entries.length; i += 2) result.put(entries[i], entries[i + 1]);
        return result;
    }

    private static JSONObject pick(JSONObject source, String[] keys) throws Exception {
        JSONObject result = new JSONObject();
        for (int i = 0; i < keys.length; i++) if (source.has(keys[i])) result.put(keys[i], source.opt(keys[i]));
        return result;
    }

    private static boolean contains(JSONArray array, String value) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) if (value.equalsIgnoreCase(array.optString(i))) return true;
        return false;
    }

    private static final class HttpError extends Exception {
        final int status;
        HttpError(int code, String message) { super(message); status = code; }
    }
}
