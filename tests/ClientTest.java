import br.casasala.painel.JellyfinClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClientTest {
    private static int count = 0;
    private static void expect(boolean ok, String detail) throws Exception {
        if (!ok) throw new Exception("FAIL: " + detail);
        count++;
    }
    public static void main(String[] args) throws Exception {
        String base = args[0] + "/jellyfin";
        expect(JellyfinClient.normalizeBase(base + "/web/index.html#!/home").equals(base), "normalize copied web URL");
        expect(JellyfinClient.testServer(base, "own-device").getString("ServerName").equals("Fixture"), "public info");
        JSONObject initiate = JellyfinClient.initiateQuickConnect(base, "own-device");
        expect(initiate.getString("Code").equals("123456"), "QuickConnect initiation");
        expect(!JellyfinClient.pollQuickConnect(base, initiate.getString("Secret"), "own-device").getBoolean("Authenticated"), "QuickConnect pending");
        JSONObject auth = JellyfinClient.authenticateQuickConnect(base, "secret-fixture", "own-device");
        expect(auth.getString("AccessToken").equals("fixture-token"), "QuickConnect exchange");
        expect(auth.getJSONObject("User").getString("Id").equals("user1"), "QuickConnect user");
        expect(JellyfinClient.authenticateByName(base, "test", "pw", "own-device").getString("AccessToken").equals("fixture-token"), "password login");
        JellyfinClient client = new JellyfinClient(base, "fixture-token", "user1", "own-device");
        JSONArray sessions = client.request("/v1/sessions", null).getJSONArray("sessions");
        expect(sessions.length() == 1 && sessions.getJSONObject(0).getString("Id").equals("tv-session"), "remote session filtering");
        String[] types = {"Audio", "Movie", "Series", "Season", "Episode", "MusicAlbum", "Playlist", "MusicArtist"};
        for (int i = 0; i < types.length; i++) {
            JSONArray rows = client.request("/v1/library?kind=" + types[i] + "&parent=parent1&q=Jo%C3%A3o%20%26%20Maria", null).getJSONArray("items");
            expect(rows.getJSONObject(0).getString("Type").equals(types[i]), "library type " + types[i]);
            expect(!rows.getJSONObject(0).has("Path"), "private library field omitted");
        }
        expect(client.request("/v1/library?kind=Audio&playlist=playlist1", null).getJSONArray("items").length() == 2, "playlist ordered browse");
        String[] play = {"song1", "movie1", "episode1", "album1", "playlist1"};
        for (int i = 0; i < play.length; i++) {
            JSONObject body = new JSONObject().put("session_id", "tv-session").put("item_id", play[i]);
            expect(client.request("/v1/play", body).getBoolean("accepted"), "play " + play[i]);
        }
        expect(client.request("/v1/command", new JSONObject().put("session_id", "tv-session").put("command", "Pause")).getBoolean("accepted"), "pause");
        expect(client.request("/v1/command", new JSONObject().put("session_id", "tv-session").put("command", "VolumeUp")).getBoolean("accepted"), "volume capability");
        boolean rejected = false;
        try { client.request("/v1/play", new JSONObject().put("session_id", "expired").put("item_id", "song1")); }
        catch (Exception error) { rejected = error.getMessage().indexOf("selecione") >= 0; }
        expect(rejected, "stale session refused");
        rejected = false;
        try { client.request("/v1/play", new JSONObject().put("session_id", "tv-session").put("item_id", "series1")); }
        catch (Exception error) { rejected = error.getMessage().indexOf("episódio") >= 0; }
        expect(rejected, "series opens episodes instead of invalid playback");
        rejected = false;
        try { new JellyfinClient(base, "bad-token", "user1", "own-device").request("/v1/sessions", null); }
        catch (Exception error) { rejected = error.getMessage().indexOf("bad-token") < 0 && error.getMessage().indexOf("recusou") >= 0; }
        expect(rejected, "auth errors redact credentials and upstream body");
        expect(client.art("song1").length == 3, "art bytes");
        expect(client.request("/v1/jellyfin/status", null).getString("user").equals("Tester"), "authenticated status");
        System.out.println("PASS " + count + " native Jellyfin HTTP assertions");
    }
}
