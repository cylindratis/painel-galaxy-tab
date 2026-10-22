package br.casasala.painel;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.json.JSONArray;
import org.json.JSONObject;

/** Direct local Tuya switch control. No cloud, Home Assistant, or intermediary server. */
public final class TuyaLan {
    private static final Map<String, Object> LOCKS = new HashMap<String, Object>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FRAME = 65536;
    private TuyaLan() { }

    private static Object lock(JSONObject device) {
        String key = device.optString("device_id", device.optString("ip"));
        synchronized (LOCKS) {
            Object value = LOCKS.get(key);
            if (value == null) { value = new Object(); LOCKS.put(key, value); }
            return value;
        }
    }

    public static boolean supported(double version) {
        return version == 3.1 || version == 3.2 || version == 3.3 || version == 3.4 || version == 3.5;
    }

    public static void validate(JSONObject device) throws Exception {
        String id = device.optString("device_id", "");
        String ip = device.optString("ip", "");
        String key = device.optString("local_key", "");
        if (id.length() == 0 || id.length() > 128) throw new Exception("Informe o ID real do dispositivo Tuya.");
        if (!validIp(ip)) throw new Exception("Informe o endereço IPv4 local do dispositivo.");
        if (key.getBytes("UTF-8").length != 16) throw new Exception("A chave local Tuya precisa ter 16 bytes.");
        if (!supported(device.optDouble("version", 0))) throw new Exception("Protocolo Tuya não suportado. Escolha de 3.1 a 3.5.");
        String dp = device.optString("switch_dp", "");
        if (!dp.matches("[0-9]{1,5}") || Integer.parseInt(dp) < 1) throw new Exception("Informe o DP booleano do canal de ligar/desligar.");
    }

    private static boolean validIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].matches("[0-9]{1,3}") || Integer.parseInt(parts[i]) > 255) return false;
            }
            int first = Integer.parseInt(parts[0]);
            return first > 0 && first < 224; // IPv4 unicast only; never send switch controls to a broadcast/multicast range.
        } catch (Exception ignored) { return false; }
    }

    public static JSONObject status(JSONObject device) {
        if (device == null) return failure(null, new Exception("Informe o dispositivo Tuya."));
        synchronized (lock(device)) {
            Client client = null;
            try {
                validate(device);
                client = new Client(device);
                return state(device, client.query(), null);
            } catch (Exception error) { return failure(device, error); }
            finally { if (client != null) client.close(); }
        }
    }

    public static JSONObject set(JSONObject device, boolean on) {
        if (device == null) return failure(null, new Exception("Informe o dispositivo Tuya."));
        synchronized (lock(device)) {
            Client client = null;
            try {
                validate(device);
                client = new Client(device);
                client.control(on);
                JSONObject response = state(device, client.query(), Boolean.valueOf(on));
                return response;
            } catch (Exception error) {
                JSONObject result = failure(device, error);
                try { result.put("confirmed", false); } catch (Exception ignored) { }
                return result;
            } finally { if (client != null) client.close(); }
        }
    }

    public static JSONObject setValues(JSONObject device, JSONObject values) {
        if(device==null)return failure(null,new Exception("Informe o dispositivo Tuya."));
        synchronized(lock(device)) {
            Client client=null;
            try {
                validate(device);
                client=new Client(device); client.control(values);
                JSONObject result=state(device,client.query(),null);
                result.put("confirmed",LightingProfile.confirmed(values,result.optJSONObject("dps")));
                return result;
            }catch(Exception e){JSONObject result=failure(device,e);try{result.put("confirmed",false);}catch(Exception ignored){}return result;}
            finally{if(client!=null)client.close();}
        }
    }

    public static JSONArray list(JSONArray devices) {
        JSONArray result = new JSONArray();
        if (devices == null || devices.length() == 0) return result;
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, devices.length()));
        java.util.ArrayList<Future<JSONObject>> futures = new java.util.ArrayList<Future<JSONObject>>();
        try {
            for (int i = 0; i < devices.length(); i++) {
                final JSONObject device = devices.optJSONObject(i);
                futures.add(executor.submit(new Callable<JSONObject>() { public JSONObject call() { return status(device == null ? new JSONObject() : device); } }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try { result.put(futures.get(i).get(14, TimeUnit.SECONDS)); }
                catch (Exception error) { futures.get(i).cancel(true); result.put(failure(devices.optJSONObject(i), error)); }
            }
        } finally { executor.shutdownNow(); }
        return result;
    }

    private static JSONObject base(JSONObject device) {
        JSONObject result = new JSONObject();
        if (device == null) device = new JSONObject();
        try { result.put("id", device.optString("id", device.optString("device_id"))).put("name", device.optString("name", "Dispositivo Tuya"))
            .put("room", device.optString("room", "Casa")).put("online", false).put("on", JSONObject.NULL); }
        catch (Exception ignored) { }
        return result;
    }

    private static JSONObject state(JSONObject device, JSONObject payload, Boolean desired) throws Exception {
        JSONObject result = base(device);
        JSONObject dps = payload == null ? null : payload.optJSONObject("dps");
        if (dps == null && payload != null && payload.optJSONObject("data") != null) dps = payload.optJSONObject("data").optJSONObject("dps");
        if(dps!=null){ result.put("dps",dps); JSONObject light=LightingProfile.describe(device,dps); java.util.Iterator<?> keys=light.keys();while(keys.hasNext()){String key=String.valueOf(keys.next());result.put(key,light.opt(key));} }
        result.put("rgbw",LightingProfile.enabled(device));
        Object value = dps == null ? null : dps.opt(device.optString("switch_dp"));
        if (value instanceof Boolean) result.put("online", true).put("on", value);
        else result.put("error", "Sem DP booleano confirmado. Confira o protocolo e o switch_dp.");
        if (desired != null) result.put("confirmed", value instanceof Boolean && desired.equals(value));
        return result;
    }

    private static JSONObject failure(JSONObject device, Exception error) {
        JSONObject result = base(device);
        String message;
        if (error instanceof SocketTimeoutException) message = "Dispositivo sem resposta. Confira IP, chave, protocolo e outros clientes LAN.";
        else if (error instanceof java.net.ConnectException || error instanceof java.net.NoRouteToHostException) message = "Não foi possível conectar ao dispositivo na rede local.";
        else if (error instanceof java.io.IOException) message = "A conexão local com o dispositivo foi interrompida.";
        else {
            message = error.getMessage();
            if (message == null || !(message.startsWith("Informe") || message.startsWith("A chave") || message.startsWith("Protocolo") ||
                message.startsWith("Quadro Tuya") || message.startsWith("Autenticação Tuya") || message.startsWith("O dispositivo") || message.startsWith("Resposta Tuya")))
                message = "Falha no protocolo local. Confira a chave, o protocolo e o DP do dispositivo.";
        }
        try { result.put("error", message); } catch (Exception ignored) { }
        return result;
    }

    private static final class Frame {
        int command;
        byte[] payload;
        Frame(int c, byte[] p) { command = c; payload = p; }
    }

    private static final class Client {
        final JSONObject config;
        final String id, dp;
        final double version;
        final byte[] original;
        byte[] key;
        Socket socket;
        InputStream input;
        OutputStream output;
        int sequence = 1;
        boolean device22;
        long deadline = System.currentTimeMillis() + 10000;

        Client(JSONObject device) throws Exception {
            config = device; id = device.getString("device_id"); dp = device.optString("switch_dp");
            version = device.getDouble("version"); device22 = version == 3.2 || device.optBoolean("device22", false);
            original = device.getString("local_key").getBytes("UTF-8"); key = original;
            socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(device.getString("ip"), 6668), 2500);
                socket.setSoTimeout(2800); socket.setTcpNoDelay(true);
                input = socket.getInputStream(); output = socket.getOutputStream();
                if (version >= 3.4) negotiate();
            } catch (Exception error) { close(); throw error; }
        }

        void close() { try { if (socket != null) socket.close(); } catch (Exception ignored) { } }

        void negotiate() throws Exception {
            byte[] nonce = new byte[16]; RANDOM.nextBytes(nonce);
            send(3, nonce);
            Frame response = read();
            if (response.command != 4) throw new Exception("Autenticação Tuya falhou. Confira o protocolo.");
            byte[] raw = version == 3.4 ? TuyaCrypto.aes(original, response.payload, false, true) : response.payload;
            if (raw.length < 48 || !TuyaCrypto.equal(TuyaCrypto.hmac(original, nonce), TuyaCrypto.slice(raw, 16, 48)))
                throw new Exception("Autenticação Tuya falhou. Confira a chave local.");
            byte[] remote = TuyaCrypto.slice(raw, 0, 16);
            send(5, TuyaCrypto.hmac(original, remote));
            byte[] mixed = new byte[16];
            for (int i = 0; i < 16; i++) mixed[i] = (byte)(nonce[i] ^ remote[i]);
            if (version == 3.4) key = TuyaCrypto.aes(original, mixed, true, false);
            else key = TuyaCrypto.slice(TuyaCrypto.gcmEncrypt(original, TuyaCrypto.slice(nonce, 0, 12), mixed, null), 0, 16);
        }

        JSONObject query() throws Exception {
            int command = version >= 3.4 ? 16 : device22 ? 13 : 10;
            JSONObject payload = new JSONObject();
            if (version < 3.4) {
                payload.put("devId", id).put("uid", id).put("t", String.valueOf(System.currentTimeMillis() / 1000));
                if (!device22) payload.put("gwId", id);
                else {
                    JSONObject requested=new JSONObject().put(dp,JSONObject.NULL);
                    String[] fields={"mode_dp","brightness_dp","temperature_dp","color_dp"};
                    for(int i=0;i<fields.length;i++)if(config.optString(fields[i]).length()>0)requested.put(config.optString(fields[i]),JSONObject.NULL);
                    payload.put("dps",requested);
                }
            }
            send(command, payload.toString().getBytes("UTF-8"));
            try { return awaitState(); }
            catch (Device22 error) {
                if (version == 3.3 && !device22) { device22 = true; return query(); }
                throw new Exception("Resposta Tuya não compatível com esse DP.");
            }
        }

        JSONObject awaitState() throws Exception {
            for (int i = 0; i < 6; i++) {
                Frame frame = read();
                if (frame.payload.length == 0) continue;
                if (frame.command == 4 || frame.command == 5 || frame.command == 9) continue;
                byte[] raw = decode(frame.payload);
                if (raw.length == 0) continue;
                String json = new String(raw, "UTF-8");
                if (json.indexOf("data unvalid") >= 0) throw new Device22();
                JSONObject data = new JSONObject(json);
                JSONObject dps = data.optJSONObject("dps");
                if (dps == null && data.optJSONObject("data") != null) dps = data.optJSONObject("data").optJSONObject("dps");
                if (dps != null && dps.has(dp)) return data;
                if (data.has("Error") || data.has("error")) throw new Exception("O dispositivo recusou a consulta local.");
            }
            throw new Exception("Resposta Tuya sem estado confirmado.");
        }

        void control(boolean on) throws Exception {
            control(new JSONObject().put(dp,on));
        }

        void control(JSONObject values) throws Exception {
            JSONObject payload;
            int command;
            if (version >= 3.4) {
                command = 13;
                payload = new JSONObject().put("protocol", 5).put("t", System.currentTimeMillis() / 1000)
                    .put("data", new JSONObject().put("dps", values));
            } else {
                command = 7;
                payload = new JSONObject().put("devId", id).put("uid", id).put("t", String.valueOf(System.currentTimeMillis() / 1000)).put("dps", values);
            }
            send(command, payload.toString().getBytes("UTF-8"));
            // Consume one acknowledgment. A missing/empty ACK never confirms a switch state.
            try { read(); } catch (SocketTimeoutException ignored) { }
        }

        byte[] decode(byte[] payload) throws Exception {
            if (version == 3.4) payload = TuyaCrypto.aes(key, payload, false, true);
            if (payload.length >= 19 && payload[0] == '3' && payload[1] == '.' && payload[2] == '1') {
                payload = TuyaCrypto.aes(key, TuyaCrypto.unbase64(TuyaCrypto.slice(payload, 19, payload.length)), false, true);
            } else if (version >= 3.2) {
                if (payload.length >= 15 && payload[0] == '3' && payload[1] == '.') payload = TuyaCrypto.slice(payload, 15, payload.length);
                else if (version < 3.4 && device22 && payload.length >= 15 && (payload.length & 15) != 0)
                    payload = TuyaCrypto.slice(payload, 15, payload.length);
                if (version < 3.4) payload = TuyaCrypto.aes(key, payload, false, true);
            }
            return payload;
        }

        void send(int command, byte[] raw) throws Exception {
            byte[] body = raw;
            boolean header = !(command == 3 || command == 4 || command == 5 || command == 9 || command == 10 || command == 16 || command == 18 || command == 64);
            if (version >= 3.4) {
                if (header) body = concat(versionHeader(), body);
                if (version == 3.4) body = TuyaCrypto.aes(key, body, true, true);
            } else if (version >= 3.2) {
                body = TuyaCrypto.aes(key, body, true, true);
                if (header) body = concat(versionHeader(), body);
            } else if (command == 7) {
                byte[] encrypted = TuyaCrypto.base64(TuyaCrypto.aes(key, body, true, true));
                byte[] mdInput = concat(concat("data=".getBytes("US-ASCII"), encrypted), concat("||lpv=3.1||".getBytes("US-ASCII"), key));
                body = concat(("3.1" + TuyaCrypto.md5(mdInput).substring(8, 24)).getBytes("US-ASCII"), encrypted);
            }
            output.write(pack(command, body)); output.flush();
        }

        byte[] versionHeader() throws Exception {
            byte[] bytes = new byte[15];
            System.arraycopy(String.valueOf(version).getBytes("US-ASCII"), 0, bytes, 0, 3);
            return bytes;
        }

        byte[] pack(int command, byte[] body) throws Exception {
            if (version == 3.5) {
                byte[] header = new byte[18];
                putInt(header, 0, 0x6699); putInt(header, 6, sequence++); putInt(header, 10, command); putInt(header, 14, body.length + 28);
                byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
                byte[] encrypted = TuyaCrypto.gcmEncrypt(key, iv, body, TuyaCrypto.slice(header, 4, header.length));
                return concat(concat(concat(header, iv), encrypted), intBytes(0x9966));
            }
            int footer = version == 3.4 ? 36 : 8;
            byte[] header = new byte[16];
            putInt(header, 0, 0x55aa); putInt(header, 4, sequence++); putInt(header, 8, command); putInt(header, 12, body.length + footer);
            byte[] data = concat(header, body);
            byte[] check;
            if (version == 3.4) check = TuyaCrypto.hmac(key, data);
            else { CRC32 crc = new CRC32(); crc.update(data); check = intBytes((int)crc.getValue()); }
            return concat(concat(data, check), intBytes(0xaa55));
        }

        Frame read() throws Exception {
            byte[] first = readBytes(4);
            int prefix = integer(first, 0);
            if (prefix == 0x6699 && version == 3.5) {
                byte[] header = concat(first, readBytes(14));
                int length = integer(header, 14), command = integer(header, 10);
                if (length < 32 || length > MAX_FRAME) throw new Exception("Quadro Tuya GCM inválido.");
                byte[] body = readBytes(length + 4);
                if (integer(body, body.length - 4) != 0x9966) throw new Exception("Quadro Tuya com final inválido.");
                byte[] raw = TuyaCrypto.gcmDecrypt(key, TuyaCrypto.slice(body, 0, 12), TuyaCrypto.slice(body, 12, body.length - 4), TuyaCrypto.slice(header, 4, header.length));
                if (raw.length < 4) throw new Exception("Quadro Tuya sem código de resposta.");
                if (integer(raw, 0) != 0) throw new Exception("O dispositivo recusou o comando local.");
                return new Frame(command, TuyaCrypto.slice(raw, 4, raw.length));
            }
            if (prefix != 0x55aa || version == 3.5) throw new Exception("Quadro Tuya inválido. Confira o protocolo.");
            byte[] header = concat(first, readBytes(12));
            int command = integer(header, 8), length = integer(header, 12), footer = version == 3.4 ? 36 : 8;
            if (length < footer + 4 || length > MAX_FRAME) throw new Exception("Quadro Tuya inválido.");
            byte[] body = readBytes(length);
            if (integer(body, length - 4) != 0xaa55) throw new Exception("Quadro Tuya com final inválido.");
            byte[] data = concat(header, TuyaCrypto.slice(body, 0, body.length - footer));
            byte[] expected;
            if (version == 3.4) expected = TuyaCrypto.hmac(key, data);
            else { CRC32 crc = new CRC32(); crc.update(data); expected = intBytes((int)crc.getValue()); }
            if (!TuyaCrypto.equal(expected, TuyaCrypto.slice(body, body.length - footer, body.length - 4))) throw new Exception("Autenticação Tuya falhou. Confira a chave local.");
            if (integer(body, 0) != 0) throw new Exception("O dispositivo recusou o comando local.");
            return new Frame(command, TuyaCrypto.slice(body, 4, body.length - footer));
        }

        byte[] readBytes(int size) throws Exception {
            byte[] out = new byte[size]; int offset = 0;
            while (offset < size) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) throw new SocketTimeoutException();
                socket.setSoTimeout((int)Math.min(2800, remaining));
                int count = input.read(out, offset, size - offset);
                if (count < 0) throw new EOFException();
                offset += count;
            }
            return out;
        }
    }

    private static final class Device22 extends Exception { private static final long serialVersionUID = 1L; }
    private static byte[] intBytes(int value) { byte[] bytes = new byte[4]; putInt(bytes, 0, value); return bytes; }
    private static void putInt(byte[] out, int start, int value) {
        out[start] = (byte)(value >>> 24); out[start + 1] = (byte)(value >>> 16); out[start + 2] = (byte)(value >>> 8); out[start + 3] = (byte)value;
    }
    private static int integer(byte[] bytes, int start) {
        return ((bytes[start] & 255) << 24) | ((bytes[start + 1] & 255) << 16) | ((bytes[start + 2] & 255) << 8) | (bytes[start + 3] & 255);
    }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length]; System.arraycopy(a, 0, out, 0, a.length); System.arraycopy(b, 0, out, a.length, b.length); return out;
    }
}
