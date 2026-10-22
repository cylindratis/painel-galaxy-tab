package br.casasala.painel;
import android.graphics.Canvas;import android.graphics.ColorFilter;import android.graphics.Paint;import android.graphics.Path;import android.graphics.RectF;import android.graphics.PixelFormat;import android.graphics.drawable.Drawable;
/** Shared 24-unit, two-unit stroke icons for navigation and controls. No icon font. */
public final class PanelIcon extends Drawable{
 private final Paint p=new Paint(3);private final String name;private final int ink;private int opacity=255;
 public PanelIcon(String type,int color,int pixels){name=type;ink=color;setBounds(0,0,pixels,pixels);}
 public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor((ink&0xffffff)|((((ink>>>24)*opacity/255)&255)<<24));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
  if(name.equals("power")){canvas.drawArc(new RectF(4,4,20,21),-45,270,false,p);canvas.drawLine(12,2,12,11,p);}
  else if(name.equals("mic")){canvas.drawRoundRect(new RectF(8,2,16,15),4,4,p);canvas.drawArc(new RectF(5,7,19,19),0,180,false,p);canvas.drawLine(12,19,12,23,p);canvas.drawLine(8,23,16,23,p);}
  else if(name.equals("tv")){canvas.drawRoundRect(new RectF(2,3,22,18),2,2,p);canvas.drawLine(8,22,16,22,p);canvas.drawLine(12,18,12,22,p);}
  else if(name.equals("home")){Path path=new Path();path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(19,21);path.lineTo(19,9);path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);canvas.drawPath(path,p);}
  else if(name.equals("music")){canvas.drawCircle(7,18,3,p);canvas.drawCircle(18,16,3,p);canvas.drawLine(10,18,10,5,p);canvas.drawLine(21,16,21,3,p);canvas.drawLine(10,5,21,3,p);canvas.drawLine(10,9,21,7,p);}
  else if(name.equals("weather")){canvas.drawCircle(8,8,4,p);canvas.drawLine(8,1,8,0,p);canvas.drawLine(1,8,0,8,p);canvas.drawLine(3,3,2,2,p);canvas.drawLine(13,3,14,2,p);Path cloud=new Path();cloud.moveTo(6,20);cloud.cubicTo(0,17,5,11,9,14);cloud.cubicTo(12,6,21,9,20,15);cloud.cubicTo(26,16,22,22,18,21);cloud.lineTo(6,21);canvas.drawPath(cloud,p);}
  else if(name.equals("clock")){canvas.drawCircle(12,12,9,p);canvas.drawLine(12,6,12,12,p);canvas.drawLine(12,12,17,15,p);}
  else if(name.equals("bulb")){Path path=new Path();path.moveTo(8,16);path.cubicTo(8,13,5,12,5,8);path.cubicTo(5,-1,19,-1,19,8);path.cubicTo(19,12,16,13,16,16);path.close();canvas.drawPath(path,p);canvas.drawLine(8,19,16,19,p);canvas.drawLine(10,22,14,22,p);}
  else if(name.equals("grid")){for(int y=0;y<2;y++)for(int x=0;x<2;x++)canvas.drawRoundRect(new RectF(3+x*11,3+y*11,10+x*11,10+y*11),1,1,p);}
  else if(name.equals("list")){for(int y=0;y<3;y++){canvas.drawLine(3,5+y*7,4,5+y*7,p);canvas.drawLine(8,5+y*7,21,5+y*7,p);}}
  else if(name.equals("search")){canvas.drawCircle(10,10,6,p);canvas.drawLine(15,15,21,21,p);}
  else if(name.equals("queue")){for(int y=0;y<3;y++)canvas.drawLine(3,5+y*6,14,5+y*6,p);Path t=new Path();t.moveTo(18,12);t.lineTo(23,16);t.lineTo(18,20);t.close();canvas.drawPath(t,p);}
  else if(name.equals("stop")){canvas.drawRoundRect(new RectF(5,5,19,19),2,2,p);}
  else if(name.equals("volumeDown")||name.equals("volumeUp")){Path v=new Path();v.moveTo(3,9);v.lineTo(7,9);v.lineTo(12,5);v.lineTo(12,19);v.lineTo(7,15);v.lineTo(3,15);v.close();canvas.drawPath(v,p);canvas.drawLine(16,12,23,12,p);if(name.equals("volumeUp"))canvas.drawLine(19.5f,8.5f,19.5f,15.5f,p);}
  canvas.restore();
 }
 public void setAlpha(int a){opacity=a;}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
