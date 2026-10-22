package br.casasala.painel;
import android.content.Context;import android.graphics.Typeface;
/** Static TrueType asset, supported by the original Android Typeface API. */
public final class LcarsTypeface{
 private static Typeface face;
 public static Typeface get(Context c,VisualTheme theme){if(!theme.id.equals("lcars")||c.getSharedPreferences("casa-sala",0).getBoolean("lcars_system_font",false))return Typeface.DEFAULT;if(face==null)try{face=Typeface.createFromAsset(c.getAssets(),"fonts/BarlowCondensed-SemiBold.ttf");}catch(RuntimeException unavailable){return Typeface.DEFAULT;}return face;}
 private LcarsTypeface(){}
}
