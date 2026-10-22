package br.casasala.test;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import java.util.*;
public final class V8ReleaseCheck extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();Activity activity=null;int code=Activity.RESULT_CANCELED;try{
  SharedPreferences prefs=getTargetContext().getSharedPreferences("casa-sala",0);prefs.edit().clear().putBoolean("demo",true).putBoolean("clock_seconds",false).putString("theme","lcars").commit();
  Typeface f=Typeface.createFromAsset(getTargetContext().getAssets(),"fonts/BarlowCondensed-SemiBold.ttf");if(f.equals(Typeface.DEFAULT))throw new Exception("TTF fallback");
  if(br.casasala.painel.LcarsTypeface.get(getTargetContext(),br.casasala.painel.VisualTheme.get("lcars")).equals(Typeface.DEFAULT))throw new Exception("LCARS font not selected");
  prefs.edit().putBoolean("lcars_system_font",true).commit();if(!br.casasala.painel.LcarsTypeface.get(getTargetContext(),br.casasala.painel.VisualTheme.get("lcars")).equals(Typeface.DEFAULT))throw new Exception("System font option failed");prefs.edit().putBoolean("lcars_system_font",false).commit();
  Intent intent=new Intent();intent.setClassName("br.casasala.painel","br.casasala.painel.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(intent);SystemClock.sleep(500);
  result.putString("stream","PASS public release APK on API 8: bundled TTF, LCARS custom font, system font fallback and Activity launch.\n");code=Activity.RESULT_OK;
 }catch(Throwable e){result.putString("stream","FAIL "+e);}finally{final Activity a=activity;if(a!=null)runOnMainSync(new Runnable(){public void run(){a.finish();}});finish(code,result);}}
}
