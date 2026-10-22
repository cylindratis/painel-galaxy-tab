package br.casasala.painel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Public weather data over HTTP: no account, bridge, token or TLS override. */
public final class WeatherClient {
    private static final int MAX_BYTES = 256 * 1024;
    private static final String AGENT = "CasaSala/0.2 (Android 2.2; public weather)";

    private WeatherClient() { }

    public static JSONObject fetch(String city, double latitude, double longitude) throws Exception {
        validateCoordinates(latitude, longitude);
        String url = "http://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
            + "&current=temperature_2m,relative_humidity_2m,weather_code,apparent_temperature,wind_speed_10m,surface_pressure"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,uv_index_max,sunrise,sunset"
            + "&hourly=temperature_2m,precipitation_probability&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=7";
        JSONObject raw = read(url);
        try {
            JSONObject current = raw.getJSONObject("current"), daily = raw.getJSONObject("daily");
            String label = city == null ? "" : city.trim();
            if (label.length() == 0) label = "Clima local";
            if (label.length() > 160) label = label.substring(0, 160);
            JSONArray days=new JSONArray();
            String[] keys={"time","weather_code","temperature_2m_max","temperature_2m_min","precipitation_probability_max","precipitation_sum","wind_speed_10m_max","uv_index_max","sunrise","sunset"};
            for(int i=0;i<daily.getJSONArray("time").length();i++){
                JSONObject day=new JSONObject();for(int j=0;j<keys.length;j++){JSONArray a=daily.optJSONArray(keys[j]);if(a!=null)day.put(keys[j],a.opt(i));}days.put(day);
            }
            return new JSONObject().put("configured", true).put("city", label).put("days",days).put("hourly",raw.optJSONObject("hourly"))
                .put("apparent",current.opt("apparent_temperature")).put("wind",current.opt("wind_speed_10m")).put("pressure",current.opt("surface_pressure"))
                .put("temperature", current.getDouble("temperature_2m"))
                .put("humidity", current.getInt("relative_humidity_2m"))
                .put("code", current.getInt("weather_code"))
                .put("high", daily.getJSONArray("temperature_2m_max").getDouble(0))
                .put("low", daily.getJSONArray("temperature_2m_min").getDouble(0))
                .put("rain", daily.getJSONArray("precipitation_probability_max").getInt(0))
                .put("updated", current.optString("time", ""))
                .put("source", "Open-Meteo");
        } catch (Exception error) {
            throw new Exception("A previsão retornou dados incompletos. Tente atualizar em instantes.");
        }
    }

    public static JSONArray geocode(String name) throws Exception {
        String term = name == null ? "" : name.trim();
        if (term.length() < 2 || term.length() > 120) {
            throw new Exception("Digite o nome da cidade, com 2 a 120 caracteres.");
        }
        String url = "http://geocoding-api.open-meteo.com/v1/search?name="
            + URLEncoder.encode(term, "UTF-8") + "&count=8&language=pt&format=json";
        JSONObject raw = read(url);
        JSONArray results = raw.optJSONArray("results"), cities = new JSONArray();
        if (results == null) return cities;
        try {
            for (int i = 0; i < results.length() && i < 8; i++) {
                JSONObject item = results.getJSONObject(i);
                double latitude = item.getDouble("latitude"), longitude = item.getDouble("longitude");
                validateCoordinates(latitude, longitude);
                JSONObject result = new JSONObject().put("name", item.getString("name"))
                    .put("country", item.isNull("country") ? "" : item.optString("country", ""))
                    .put("admin1", item.isNull("admin1") ? "" : item.optString("admin1", ""))
                    .put("latitude", latitude).put("longitude", longitude);
                cities.put(result);
            }
        } catch (Exception error) {
            throw new Exception("A busca de cidades retornou dados incompletos. Tente novamente.");
        }
        return cities;
    }

    private static void validateCoordinates(double latitude, double longitude) throws Exception {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90 || latitude > 90
            || Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180 || longitude > 180) {
            throw new Exception("Coordenadas inválidas. Latitude: -90 a 90; longitude: -180 a 180.");
        }
    }

    private static JSONObject read(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code != 200) {
                if (code == 429) throw new Exception("Limite temporário de consultas do clima. Aguarde alguns minutos.");
                if (code >= 300 && code < 400) throw new Exception("O serviço de clima mudou de endereço. Atualize o aplicativo.");
                throw new Exception("Serviço de clima indisponível (HTTP " + code + ").");
            }
            if (connection.getContentLength() > MAX_BYTES) throw new Exception("Resposta do clima muito grande.");
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (data.size() + count > MAX_BYTES) throw new Exception("Resposta do clima muito grande.");
                    data.write(buffer, 0, count);
                }
            } finally { input.close(); }
            return new JSONObject(new String(data.toByteArray(), "UTF-8"));
        } catch (java.net.SocketTimeoutException error) {
            throw new Exception("O serviço de clima demorou para responder. Tente novamente.");
        } catch (java.io.IOException error) {
            throw new Exception("Sem acesso ao serviço de clima. Confira a conexão Wi-Fi.");
        } catch (org.json.JSONException error) {
            throw new Exception("O serviço de clima retornou uma resposta inválida.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
