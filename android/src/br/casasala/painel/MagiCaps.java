package br.casasala.painel;
import android.text.method.TransformationMethod;import android.graphics.Rect;import android.view.View;import java.util.Locale;
/** Visual uppercase only; underlying names, IDs and credentials are preserved. */
public final class MagiCaps implements TransformationMethod {
 public CharSequence getTransformation(CharSequence source,View view){return source==null?"":source.toString().toUpperCase(new Locale("pt","BR"));}
 public void onFocusChanged(View view,CharSequence source,boolean focused,int direction,Rect previous){}
}
