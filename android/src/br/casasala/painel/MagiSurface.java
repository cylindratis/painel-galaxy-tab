package br.casasala.painel;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Crisp chamfered terminal panels, no bitmaps or animation. */
public final class MagiSurface extends Drawable {
 private final Paint p=new Paint(3);private final Path path=new Path();private final float d;private final int fill,edge;private int alpha=255;
 public MagiSurface(float density,int color,int border){d=density;fill=color;edge=border;}
 public void draw(Canvas c){Rect b=getBounds();float k=0,l=b.left+1,t=b.top+1,r=b.right-1,bt=b.bottom-1;path.reset();path.moveTo(l+k,t);path.lineTo(r,t);path.lineTo(r,bt-k);path.lineTo(r-k,bt);path.lineTo(l,bt);path.lineTo(l,t+k);path.close();p.setAlpha(alpha);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,d));p.setColor(edge);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
 public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
