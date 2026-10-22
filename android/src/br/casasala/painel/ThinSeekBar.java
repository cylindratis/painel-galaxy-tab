package br.casasala.painel;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.SeekBar;
/** Uses the native SeekBar input behavior with a small, legible custom track. */
public final class ThinSeekBar extends SeekBar {
 private final Paint ink=new Paint(3);
 private final VisualTheme theme;
 private final float density;
 public ThinSeekBar(Context context,VisualTheme t){super(context);theme=t;density=getResources().getDisplayMetrics().density;setPadding((int)(12*density),0,(int)(12*density),0);}
 protected synchronized void onDraw(Canvas canvas){
  float x=12*density,right=getWidth()-x,y=getHeight()/2f,pos=x+(right-x)*getProgress()/Math.max(1f,getMax());
  if("skeuo".equals(theme.id)){ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeWidth(6*density);ink.setColor(0xff85928f);canvas.drawLine(x,y,right,y,ink);ink.setStrokeWidth(2*density);ink.setColor(0xfff4f7f5);canvas.drawLine(x,y+3*density,right,y+3*density,ink);}
  ink.setStrokeWidth(3*density);ink.setStrokeCap(Paint.Cap.ROUND);ink.setColor(theme.edge);canvas.drawLine(x,y,right,y,ink);
  ink.setColor(isEnabled()?theme.accent:theme.muted);if(pos>x)canvas.drawLine(x,y,pos,y,ink);
  ink.setStyle(Paint.Style.FILL);ink.setColor(theme.panel);canvas.drawCircle(pos,y,8*density,ink);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*density);ink.setColor(isEnabled()?theme.ink:theme.muted);canvas.drawCircle(pos,y,8*density,ink);ink.setStyle(Paint.Style.FILL);
 }
}
