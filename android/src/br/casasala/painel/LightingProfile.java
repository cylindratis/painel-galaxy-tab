package br.casasala.painel;

import android.graphics.Color;
import java.util.Locale;
import java.util.Iterator;
import org.json.JSONObject;

/** Explicit, editable RGBW mapping. Never guesses writes from manufacturer names. */
public final class LightingProfile {
    private LightingProfile() { }
    public static JSONObject apply(JSONObject config, String profile) throws Exception {
        JSONObject d = new JSONObject(config.toString());
        d.put("light_profile", profile);
        if (profile.equals("A") || profile.equals("B")) {
            int start = profile.equals("A") ? 1 : 20;
            d.put("switch_dp", String.valueOf(start)).put("mode_dp", String.valueOf(start+1))
             .put("brightness_dp", String.valueOf(start+2)).put("temperature_dp", String.valueOf(start+3))
             .put("color_dp", String.valueOf(start+4)).put("brightness_min", profile.equals("A") ? 25 : 10)
             .put("brightness_max", profile.equals("A") ? 255 : 1000).put("color_format", profile.equals("A") ? "rgb8" : "hsv16");
        }
        return d;
    }
    public static boolean enabled(JSONObject d) { return d != null && d.optString("mode_dp").length()>0 && d.optString("color_dp").length()>0; }
    public static void validate(JSONObject d) throws Exception {
        if (!enabled(d)) throw new Exception("Configure o perfil RGBW desta lâmpada em Personalizar.");
        String[] fields = {"switch_dp","mode_dp","brightness_dp","temperature_dp","color_dp"};
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int i=0;i<fields.length;i++) { String key=d.optString(fields[i]); if(!key.matches("[0-9]{1,5}") || Integer.parseInt(key)<1 || !seen.add(key)) throw new Exception("Os cinco DPs precisam ser numéricos e diferentes."); }
        int min=d.optInt("brightness_min"),max=d.optInt("brightness_max");
        if(min<1 || max<=min || max>65535) throw new Exception("Confira os limites de brilho.");
        if(!d.optString("color_format").equals("rgb8") && !d.optString("color_format").equals("hsv16")) throw new Exception("Formato de cor: rgb8 ou hsv16.");
    }
    public static JSONObject values(JSONObject d, JSONObject action) throws Exception {
        JSONObject values = new JSONObject();
        if(action.has("on")) return values.put(d.getString("switch_dp"), action.getBoolean("on"));
        validate(d);
        int percent=action.optInt("brightness",100);
        if(percent<0 || percent>100) throw new Exception("Brilho deve estar entre 0 e 100%.");
        values.put(d.getString("switch_dp"), percent>0);
        if(percent==0) return values;
        int max=d.getInt("brightness_max"), min=d.getInt("brightness_min");
        String mode=action.optString("mode","white");
        if(mode.equals("colour")) {
            float hue=(float)action.optDouble("hue",0), saturation=(float)action.optDouble("saturation",100);
            if(hue<0 || hue>360 || saturation<0 || saturation>100) throw new Exception("Cor fora do intervalo.");
            values.put(d.getString("mode_dp"), "colour").put(d.getString("color_dp"), color(hue,saturation/100f,percent/100f,d.getString("color_format")));
        } else if(mode.equals("white")) {
            int temperature=action.optInt("temperature",50);
            if(temperature<0 || temperature>100) throw new Exception("Temperatura deve estar entre 0 e 100%.");
            values.put(d.getString("mode_dp"),"white").put(d.getString("brightness_dp"),Math.max(min,Math.round(max*percent/100f)))
                  .put(d.getString("temperature_dp"), Math.round(max*temperature/100f));
        } else throw new Exception("Modo RGBW inválido.");
        return values;
    }
    public static String color(float h,float s,float v,String format) {
        if(format.equals("rgb8")) {
            int rgb=Color.HSVToColor(new float[]{h,s,v});
            return String.format(Locale.US,"%02x%02x%02x%04x%02x%02x",Color.red(rgb),Color.green(rgb),Color.blue(rgb),Math.round(h),(int)(s*255),(int)(v*255));
        }
        return String.format(Locale.US,"%04x%04x%04x",Math.round(h),(int)(s*1000),(int)(v*1000));
    }
    public static JSONObject describe(JSONObject config, JSONObject dps) throws Exception {
        JSONObject result=new JSONObject(); if(dps==null || !enabled(config)) return result;
        String mode=dps.optString(config.optString("mode_dp")); result.put("mode",mode);
        int max=config.optInt("brightness_max",1000);
        if(dps.has(config.optString("brightness_dp"))) result.put("brightness",Math.round(dps.optInt(config.optString("brightness_dp"))*100f/max));
        if(dps.has(config.optString("temperature_dp"))) result.put("temperature",Math.round(dps.optInt(config.optString("temperature_dp"))*100f/max));
        String hex=dps.optString(config.optString("color_dp"));
        try {
            if(hex.length()==12) {result.put("hue",Integer.parseInt(hex.substring(0,4),16)).put("saturation",Integer.parseInt(hex.substring(4,8),16)/10f); if(mode.equals("colour"))result.put("brightness",Integer.parseInt(hex.substring(8,12),16)/10f);}
            if(hex.length()==14) {result.put("hue",Integer.parseInt(hex.substring(6,10),16)).put("saturation",Integer.parseInt(hex.substring(10,12),16)*100f/255);if(mode.equals("colour"))result.put("brightness",Integer.parseInt(hex.substring(12,14),16)*100f/255);}
        }catch(Exception ignored){}
        return result;
    }
    public static boolean confirmed(JSONObject expected,JSONObject actual) {
        if(actual==null)return false;
        Iterator<?> keys=expected.keys();
        while(keys.hasNext()){String key=String.valueOf(keys.next());if(!actual.has(key)||!String.valueOf(expected.opt(key)).equalsIgnoreCase(String.valueOf(actual.opt(key))))return false;}
        return true;
    }
}
