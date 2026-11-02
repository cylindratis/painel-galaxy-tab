package br.casasala.painel;
import android.content.Context;import android.graphics.Typeface;
/** The user's tablet supplies Helvetica as its system sans-serif. */
public final class MagiTypeface {
 public static Typeface get(Context c,boolean heading){return heading?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;}
 private MagiTypeface(){}
}
