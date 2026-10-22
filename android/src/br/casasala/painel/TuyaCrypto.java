package br.casasala.painel;

import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Small, API-8-compatible wire crypto. GCM uses the system AES block primitive. */
final class TuyaCrypto {
    private TuyaCrypto() { }

    static byte[] slice(byte[] input, int first, int last) {
        byte[] result = new byte[last - first];
        System.arraycopy(input, first, result, 0, result.length);
        return result;
    }

    static byte[] aes(byte[] key, byte[] input, boolean encrypt, boolean padding) throws Exception {
        Cipher cipher = Cipher.getInstance(padding ? "AES/ECB/PKCS5Padding" : "AES/ECB/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(input);
    }

    static byte[] hmac(byte[] key, byte[] input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(input);
    }

    static String md5(byte[] input) throws Exception {
        byte[] bytes = MessageDigest.getInstance("MD5").digest(input);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            if (value < 16) out.append('0');
            out.append(Integer.toHexString(value));
        }
        return out.toString();
    }

    static boolean equal(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int difference = 0;
        for (int i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
        return difference == 0;
    }

    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static byte[] base64(byte[] input) throws Exception {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length; i += 3) {
            int a = input[i] & 255, b = i + 1 < input.length ? input[i + 1] & 255 : 0;
            int c = i + 2 < input.length ? input[i + 2] & 255 : 0;
            out.append(B64[a >>> 2]); out.append(B64[((a & 3) << 4) | (b >>> 4)]);
            out.append(i + 1 < input.length ? B64[((b & 15) << 2) | (c >>> 6)] : '=');
            out.append(i + 2 < input.length ? B64[c & 63] : '=');
        }
        return out.toString().getBytes("US-ASCII");
    }

    static byte[] unbase64(byte[] input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int bits = 0, value = 0;
        for (int i = 0; i < input.length; i++) {
            int c = input[i] & 255;
            if (c == '=') break;
            if (c == 9 || c == 10 || c == 13 || c == 32) continue;
            int v = c >= 'A' && c <= 'Z' ? c - 'A' : c >= 'a' && c <= 'z' ? c - 'a' + 26 :
                c >= '0' && c <= '9' ? c - '0' + 52 : c == '+' ? 62 : c == '/' ? 63 : -1;
            if (v < 0) throw new Exception("Resposta Tuya inválida.");
            value = (value << 6) | v; bits += 6;
            if (bits >= 8) { bits -= 8; out.write((value >>> bits) & 255); }
        }
        return out.toByteArray();
    }

    /** Encrypt returns ciphertext followed by the 16-byte authentication tag. */
    static byte[] gcmEncrypt(byte[] key, byte[] iv, byte[] plain, byte[] aad) throws Exception {
        return gcm(key, iv, plain, aad, true);
    }

    /** Decrypt verifies the appended tag before exposing plaintext. */
    static byte[] gcmDecrypt(byte[] key, byte[] iv, byte[] cipherAndTag, byte[] aad) throws Exception {
        return gcm(key, iv, cipherAndTag, aad, false);
    }

    private static byte[] gcm(byte[] key, byte[] iv, byte[] input, byte[] aad, boolean encrypt) throws Exception {
        if (iv.length != 12 || (!encrypt && input.length < 16)) throw new Exception("Quadro Tuya GCM inválido.");
        Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] hash = aes.doFinal(new byte[16]);
        byte[] initial = new byte[16];
        System.arraycopy(iv, 0, initial, 0, 12); initial[15] = 1;
        int size = input.length - (encrypt ? 0 : 16);
        byte[] counter = initial.clone();
        byte[] output = new byte[size + (encrypt ? 16 : 0)];
        // Decryption authenticates ciphertext first; failed tags never return plaintext.
        byte[] ciphertext = encrypt ? output : input;
        if (!encrypt) {
            byte[] tag = tag(aes, initial, hash, aad, input, size);
            if (!equal(tag, slice(input, size, input.length))) throw new Exception("Autenticação Tuya falhou. Confira a chave local.");
        }
        for (int offset = 0; offset < size; offset += 16) {
            for (int j = 15; j >= 12; j--) { counter[j]++; if (counter[j] != 0) break; }
            byte[] stream = aes.doFinal(counter);
            int count = Math.min(16, size - offset);
            for (int j = 0; j < count; j++) output[offset + j] = (byte)(input[offset + j] ^ stream[j]);
        }
        if (encrypt) {
            byte[] tag = tag(aes, initial, hash, aad, ciphertext, size);
            System.arraycopy(tag, 0, output, size, 16);
        }
        return output;
    }

    private static byte[] tag(Cipher aes, byte[] initial, byte[] hash, byte[] aad, byte[] ciphertext, int length) throws Exception {
        byte[] state = new byte[16];
        if (aad == null) aad = new byte[0];
        ghash(state, hash, aad, aad.length);
        ghash(state, hash, ciphertext, length);
        byte[] lengths = new byte[16];
        putLong(lengths, 0, ((long)aad.length) * 8); putLong(lengths, 8, ((long)length) * 8);
        xor(state, lengths); multiply(state, hash);
        byte[] mask = aes.doFinal(initial);
        xor(state, mask);
        return state;
    }

    private static void putLong(byte[] data, int start, long value) {
        for (int i = 7; i >= 0; i--) { data[start + i] = (byte)value; value >>>= 8; }
    }

    private static void ghash(byte[] state, byte[] hash, byte[] data, int size) {
        for (int offset = 0; offset < size; offset += 16) {
            for (int i = 0; i < Math.min(16, size - offset); i++) state[i] ^= data[offset + i];
            multiply(state, hash);
        }
    }

    private static void xor(byte[] a, byte[] b) { for (int i = 0; i < 16; i++) a[i] ^= b[i]; }

    private static void multiply(byte[] state, byte[] hash) {
        byte[] z = new byte[16], v = hash.clone();
        for (int bit = 0; bit < 128; bit++) {
            int selected = ((state[bit >>> 3] & 255) >>> (7 - (bit & 7))) & 1;
            int mask = -selected;
            for (int i = 0; i < 16; i++) z[i] ^= (byte)(v[i] & mask);
            boolean low = (v[15] & 1) != 0;
            int carry = 0;
            for (int i = 0; i < 16; i++) { int current = v[i] & 255; v[i] = (byte)((current >>> 1) | carry); carry = (current & 1) << 7; }
            if (low) v[0] ^= (byte)0xe1;
        }
        System.arraycopy(z, 0, state, 0, 16);
    }
}
