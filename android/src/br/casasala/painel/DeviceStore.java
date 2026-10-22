package br.casasala.painel;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device configuration and presentation are kept separately from live state. */
final class DeviceStore {
    private final SharedPreferences prefs;
    DeviceStore(SharedPreferences preferences) { prefs = preferences; }
    JSONArray local() { return array("local_devices"); }
    JSONArray array(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); } }
    JSONObject map(String key) { try { return new JSONObject(prefs.getString(key, "{}")); } catch (Exception e) { return new JSONObject(); } }
    void putMap(String key, String id, Object value) {
        JSONObject data = map(key);
        try { data.put(id, value); } catch (Exception ignored) { }
        prefs.edit().putString(key, data.toString()).commit();
    }
    boolean hidden(String id) { return map("device_hidden").optBoolean(id); }
    String name(JSONObject d) { return map("device_names").optString(d.optString("id"), d.optString("name", "Dispositivo")); }
    String room(JSONObject d) { return map("device_rooms").optString(d.optString("id"), d.optString("room", "Casa")); }
    void saveLocal(JSONObject device) throws Exception {
        JSONArray all = local(), next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < all.length(); i++) {
            JSONObject old = all.getJSONObject(i);
            if (old.optString("id").equals(device.optString("id"))) { next.put(device); replaced = true; }
            else next.put(old);
        }
        if (!replaced) next.put(device);
        prefs.edit().putString("local_devices", next.toString()).commit();
    }
    JSONObject findLocal(String id) {
        JSONArray all = local();
        for (int i = 0; i < all.length(); i++) if (id.equals(all.optJSONObject(i).optString("id"))) return all.optJSONObject(i);
        return null;
    }
    void remove(String id) {
        JSONArray all = local(), next = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (!id.equals(all.optJSONObject(i).optString("id"))) next.put(all.optJSONObject(i));
        prefs.edit().putString("local_devices", next.toString()).commit();
    }
    JSONArray ordered(JSONArray source, boolean includeHidden) {
        final JSONArray order = array("device_order");
        ArrayList<JSONObject> list = new ArrayList<JSONObject>();
        for (int i = 0; source != null && i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && (includeHidden || !hidden(item.optString("id")))) list.add(item);
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            private int index(JSONObject object) {
                for (int i = 0; i < order.length(); i++) if (order.optString(i).equals(object.optString("id"))) return i;
                return 100000;
            }
            public int compare(JSONObject a, JSONObject b) { return index(a) - index(b); }
        });
        JSONArray result = new JSONArray();
        for (JSONObject object : list) result.put(object);
        return result;
    }
    void move(JSONArray source, String id, int delta) {
        JSONArray all = ordered(source, true);
        ArrayList<String> ids = new ArrayList<String>();
        for (int i = 0; i < all.length(); i++) ids.add(all.optJSONObject(i).optString("id"));
        int from = ids.indexOf(id), to = from + delta;
        if (from < 0 || to < 0 || to >= ids.size()) return;
        Collections.swap(ids, from, to);
        JSONArray result = new JSONArray(); for (String value : ids) result.put(value);
        prefs.edit().putString("device_order", result.toString()).commit();
    }
}
