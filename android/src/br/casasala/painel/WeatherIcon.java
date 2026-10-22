package br.casasala.painel;
import android.content.Context;import android.graphics.Canvas;import android.graphics.Paint;import android.graphics.Path;import android.view.View;
/** WMO weather symbols, drawn with one native stroke system. */
public final class WeatherIcon extends View {
 private final Paint p=new Paint(3);private final int code,ink,accent;
 public WeatherIcon(Context c,int wmo,VisualTheme t){super(c);code=wmo;ink=t.ink;accent=t.accent;setContentDescription(WeatherPanel.condition(code));}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/48f,getHeight()/48f);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);p.setColor(accent);
  if(code<=3){canvas.drawCircle(18,17,7,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(18+(float)Math.cos(a)*11,17+(float)Math.sin(a)*11,18+(float)Math.cos(a)*14,17+(float)Math.sin(a)*14,p);}}
  if(code!=0){p.setColor(ink);Path cloud=new Path();cloud.moveTo(12,32);cloud.cubicTo(2,31,5,19,14,21);cloud.cubicTo(17,7,35,10,36,23);cloud.cubicTo(47,21,49,34,38,34);cloud.lineTo(12,34);canvas.drawPath(cloud,p);}
  p.setColor(accent);if(code>=51&&code<=86){for(int i=0;i<3;i++)canvas.drawLine(16+i*10,39,13+i*10,44,p);}if(code>=95){Path bolt=new Path();bolt.moveTo(26,34);bolt.lineTo(20,41);bolt.lineTo(28,41);bolt.lineTo(23,47);canvas.drawPath(bolt,p);}if(code>=45&&code<=48){canvas.drawLine(9,40,39,40,p);canvas.drawLine(14,45,34,45,p);}canvas.restore();
 }
}
