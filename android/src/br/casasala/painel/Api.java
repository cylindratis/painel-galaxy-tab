package br.casasala.painel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

final class Api {
    static byte[] bytes(String base, String token, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json,image/jpeg");
        try {
            if (body != null) {
                byte[] data = body.toString().getBytes("UTF-8");
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(data.length);
                OutputStream stream = connection.getOutputStream();
                try { stream.write(data); } finally { stream.close(); }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (input != null) {
                try {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        if (output.size() > 4 * 1024 * 1024) throw new Exception("Resposta muito grande.");
                    }
                } finally { input.close(); }
            }
            if (status < 200 || status >= 300) {
                String message = "A ponte respondeu HTTP " + status + ".";
                try { message = new JSONObject(new String(output.toByteArray(), "UTF-8")).optString("error", message); }
                catch (Exception ignored) { }
                throw new Exception(message);
            }
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }

    static JSONObject json(String base, String token, String path, JSONObject body) throws Exception {
        return new JSONObject(new String(bytes(base, token, path, body), "UTF-8"));
    }
}
