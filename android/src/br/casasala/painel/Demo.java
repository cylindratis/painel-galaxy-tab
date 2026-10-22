package br.casasala.painel;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

final class Demo {
    private final boolean[] lights = {true,true,true,false,false,false,false};
    private final JSONObject[] settings=new JSONObject[7];
    private String playingId="",playingType="Audio";
    private long position=450000000,observed=System.currentTimeMillis();
    Demo(){for(int i=0;i<7;i++)try{settings[i]=new JSONObject().put("brightness",75).put("mode","white").put("temperature",50).put("hue",35).put("saturation",100);}catch(Exception ignored){}}
    private String title = "Escolha uma música";
    private boolean paused = true;
    private final HashMap<String, String> itemNames = new HashMap<String, String>();

    synchronized JSONObject get(String path, JSONObject body) throws Exception {
        if (path.equals("/v1/devices")) {
            String[] names = {"Teto 1", "Teto 2", "Teto 3", "Abajur", "Lâmpada 1", "Lâmpada 2", "Lâmpada 3"};
            JSONArray devices = new JSONArray();
            for (int i=0;i<7;i++){JSONObject d=new JSONObject(settings[i].toString()).put("id","demo"+i).put("name",names[i]).put("room",i<4?"Sala":i<6?"Quarto 1":"Quarto 2").put("online",true).put("on",lights[i]).put("rgbw",true);devices.put(d);}
            return new JSONObject().put("devices", devices);
        }
        if (path.startsWith("/v1/devices/demo")) {
            int index=Integer.parseInt(path.substring(path.length()-1));
            if(body.has("on"))lights[index]=body.getBoolean("on");else{settings[index]=new JSONObject(body.toString());lights[index]=body.optInt("brightness")>0;}
            return new JSONObject().put("confirmed", true);
        }
        if(path.equals("/v1/weather")){
            JSONObject w=new JSONObject().put("configured",true).put("city","Cidade de exemplo").put("temperature",24).put("apparent",25).put("humidity",63).put("wind",12).put("pressure",1013).put("code",2).put("high",27).put("low",18).put("rain",20);
            JSONArray days=new JSONArray();java.util.Calendar date=java.util.Calendar.getInstance();
            for(int i=0;i<7;i++){String day=new java.text.SimpleDateFormat("yyyy-MM-dd").format(date.getTime());days.put(new JSONObject().put("time",day).put("weather_code",i%3==0?61:2).put("temperature_2m_min",18+i%3).put("temperature_2m_max",27+i%4).put("precipitation_probability_max",20+i*8).put("precipitation_sum",i%3==0?4.2:0).put("wind_speed_10m_max",14+i).put("uv_index_max",5.1).put("sunrise",day+"T05:45").put("sunset",day+"T18:21"));date.add(java.util.Calendar.DAY_OF_YEAR,1);}return w.put("days",days);
        }
        if (path.equals("/v1/sessions")) {
            JSONObject session = new JSONObject().put("Id", "demo-tv").put("DeviceId", "demo-tv").put("DeviceName", "Samsung da sala (exemplo)")
                .put("Client", "Jellyfin").put("PlayState", new JSONObject().put("IsPaused", paused).put("CanSeek",true).put("PositionTicks",Math.min(2100000000L,position+(paused?0:(System.currentTimeMillis()-observed)*10000))))
                .put("SupportedCommands", new JSONArray().put("VolumeUp").put("VolumeDown"));
            if (!title.equals("Escolha uma música")) session.put("NowPlayingItem", new JSONObject().put("Id",playingId).put("Type",playingType).put("Name", title)
                .put("Artists", new JSONArray().put("Artista de exemplo")).put("RunTimeTicks", 2100000000));
            return new JSONObject().put("sessions", new JSONArray().put(session));
        }
        if (path.startsWith("/v1/library")) {
            String type = path.indexOf("kind=MusicAlbum") >= 0 ? "MusicAlbum" : path.indexOf("kind=Playlist") >= 0 ? "Playlist" :
                path.indexOf("kind=MusicArtist") >= 0 ? "MusicArtist" : path.indexOf("kind=Movie") >= 0 ? "Movie" :
                path.indexOf("kind=Series") >= 0 ? "Series" : path.indexOf("kind=Season") >= 0 ? "Season" : path.indexOf("kind=Episode") >= 0 ? "Episode" : "Audio";
            String[] names = {"Fim de tarde", "Casa tranquila", "Luz da manhã", "Domingo na sala", "Entre as janelas", "Depois da chuva"};
            if (type.equals("Movie")) names = new String[]{"Horizonte", "Uma viagem de verão", "A casa das histórias", "O último trem"};
            if (type.equals("Series")) names = new String[]{"Histórias da cidade", "Entre mundos", "Pequenas descobertas"};
            if (type.equals("Season")) names = new String[]{"Temporada 1", "Temporada 2"};
            if (type.equals("Episode")) names = new String[]{"1. Um novo começo", "2. A visita", "3. O encontro"};
            JSONArray items = new JSONArray();
            for (int i = 0; i < names.length; i++) {
                String id = "demo-" + type + "-" + i;
                itemNames.put(id, names[i]);
                items.put(new JSONObject().put("Id", id).put("Name", names[i])
                    .put("Type", type).put("Artists", new JSONArray().put(type.equals("Audio") ? "Artista de exemplo" : "Biblioteca de exemplo")).put("RunTimeTicks", 2100000000));
            }
            return new JSONObject().put("items", items).put("total", items.length()).put("start", 0);
        }
        if (path.equals("/v1/play")) {
            String[] names = {"Fim de tarde", "Casa tranquila", "Luz da manhã", "Domingo na sala", "Entre as janelas", "Depois da chuva"};
            String id = body.getString("item_id");playingId=id;playingType=id.indexOf("Movie")>=0?"Movie":id.indexOf("Episode")>=0?"Episode":"Audio";position=0;observed=System.currentTimeMillis();
            title = itemNames.containsKey(id) ? itemNames.get(id) : names[Integer.parseInt(id.substring(id.lastIndexOf('-') + 1))];
            paused = false;
        }
        if (path.equals("/v1/command")) {
            String cmd = body.getString("command");
            if(!paused)position+=(System.currentTimeMillis()-observed)*10000;observed=System.currentTimeMillis();
            if(cmd.equals("Seek"))position=body.optLong("position_ticks");
            if (cmd.equals("Pause")) paused = true;
            if (cmd.equals("Unpause")) paused = false;
            if (cmd.equals("Stop")) { paused = true; title = "Escolha uma música"; }
        }
        return new JSONObject().put("accepted", true);
    }
}
