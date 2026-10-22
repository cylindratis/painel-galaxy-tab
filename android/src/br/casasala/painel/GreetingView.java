package br.casasala.painel;
import android.content.Context;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import java.util.Calendar;
/** One quiet motion: names turn on a shared baseline, without re-rendering the dashboard. */
public final class GreetingView extends LinearLayout {
 private final Handler handler=new Handler(); private final TextSwitcher names;private final TextView salutation;
 private final String[] people;private static final String DEFAULT_NAMES=""; private int index;private boolean attached;
 public GreetingView(final Context c,final VisualTheme theme){super(c);String raw=c.getSharedPreferences("casa-sala",0).getString("greeting_names",DEFAULT_NAMES).trim();people=raw.length()==0?new String[0]:raw.split(",");for(int i=0;i<people.length;i++)people[i]=people[i].trim();setGravity(Gravity.CENTER_VERTICAL);
  salutation=new TextView(c);salutation.setTextColor(theme.ink);if(theme.id.equals("lcars"))salutation.setTypeface(LcarsTypeface.get(c,theme));salutation.setTextSize(25);salutation.setSingleLine(true);salutation.setIncludeFontPadding(false);addView(salutation);
  names=new TextSwitcher(c);names.setFactory(new ViewSwitcher.ViewFactory(){public View makeView(){TextView t=new TextView(c);t.setTextColor(theme.accent);t.setTextSize(25);t.setTypeface(theme.id.equals("lcars")?LcarsTypeface.get(c,theme):android.graphics.Typeface.DEFAULT,theme.id.equals("lcars")?0:1);t.setSingleLine(true);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}});
  AnimationSet in=new AnimationSet(true);in.addAnimation(new AlphaAnimation(0,1));in.addAnimation(new TranslateAnimation(0,0,12,0));in.setDuration(320);
  AnimationSet out=new AnimationSet(true);out.addAnimation(new AlphaAnimation(1,0));out.addAnimation(new TranslateAnimation(0,0,0,-12));out.setDuration(260);names.setInAnimation(in);names.setOutAnimation(out);addView(names,new LayoutParams(0,-2,1));update();if(people.length>0)names.setCurrentText(people[0]);else names.setVisibility(GONE);
 }
 private void update(){int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);salutation.setText((hour<12?"Bom dia, ":hour<18?"Boa tarde, ":"Boa noite, ").replace(", ",people.length==0?"":", "));}
 private final Runnable turn=new Runnable(){public void run(){if(!attached)return;update();if(people.length>1){index=(index+1)%people.length;names.setText(people[index]);}handler.postDelayed(this,people.length>1?4800:60000);}};
 protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;handler.postDelayed(turn,4800);}
 protected void onDetachedFromWindow(){attached=false;handler.removeCallbacks(turn);super.onDetachedFromWindow();}
 protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(names==null)return;handler.removeCallbacks(turn);if(visibility==VISIBLE&&attached)handler.postDelayed(turn,4800);}
}
