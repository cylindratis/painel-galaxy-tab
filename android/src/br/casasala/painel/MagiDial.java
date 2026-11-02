package br.casasala.painel;
import android.graphics.*;import java.util.Calendar;
/** Original three-core clock diagram based on the user's MAGI reference. Values are local time. */
public final class MagiDial {
 private static final Paint valuePaint=new Paint(3);
 private static void polygon(Canvas c,Paint p,float[] points,int fill,int edge,float side){Path path=new Path();path.moveTo(points[0]*side,points[1]*side);for(int i=2;i<points.length;i+=2)path.lineTo(points[i]*side,points[i+1]*side);path.close();p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(side*.004f);p.setColor(edge);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
 public static void face(Canvas c,float side,VisualTheme theme,Typeface serif){Paint p=new Paint(3);int edge=theme.accent,fill=theme.id.equals("magi-red")?0xffff6249:0xff54bee7;p.setColor(0xff163c2d);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(side*.002f);for(int i=0;i<7;i++)c.drawCircle(side*.5f,side*.49f,side*(.15f+i*.048f),p);p.setStyle(Paint.Style.FILL);p.setColor(edge);p.setStrokeWidth(side*.012f);c.drawLine(side*.5f,side*.36f,side*.5f,side*.55f,p);c.drawLine(side*.3f,side*.63f,side*.7f,side*.63f,p);
 polygon(c,p,new float[]{.36f,.03f,.64f,.03f,.68f,.08f,.68f,.34f,.58f,.43f,.43f,.43f,.33f,.34f,.33f,.08f},fill,edge,side);
 polygon(c,p,new float[]{.03f,.47f,.27f,.47f,.42f,.60f,.42f,.86f,.03f,.86f},fill,edge,side);
 polygon(c,p,new float[]{.73f,.47f,.97f,.47f,.97f,.86f,.58f,.86f,.58f,.60f},fill,edge,side);
 p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(side*.034f);p.setColor(0xff071215);c.drawText("BALTHASAR·2",side*.505f,side*.12f,p);c.drawText("CASPER·3",side*.225f,side*.56f,p);c.drawText("MELCHIOR·1",side*.775f,side*.56f,p);
 p.setTextSize(side*.027f);c.drawText("HORAS",side*.505f,side*.38f,p);c.drawText("MINUTOS",side*.225f,side*.81f,p);c.drawText("SEGUNDOS",side*.775f,side*.81f,p);
 p.setTypeface(serif);p.setColor(edge);p.setTextSize(side*.073f);c.drawText("MAGI",side*.50f,side*.525f,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(side*.026f);p.setColor(theme.clockAccent);c.drawText("HORA LOCAL",side*.5f,side*.94f,p);
 }
 public static void values(Canvas c,float side,Calendar time,boolean seconds,Typeface serif){Paint p=valuePaint;p.setTypeface(serif);p.setTextAlign(Paint.Align.CENTER);p.setColor(0xff071215);p.setTextSize(side*.175f);c.drawText(two(time.get(Calendar.HOUR_OF_DAY)),side*.505f,side*.30f,p);c.drawText(two(time.get(Calendar.MINUTE)),side*.225f,side*.735f,p);c.drawText(seconds?two(time.get(Calendar.SECOND)):"—",side*.775f,side*.735f,p);}
 private static String two(int n){return n<10?"0"+n:String.valueOf(n);}
 private MagiDial(){}
}
