package br.casasala.painel;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Original vector frame inspired by TNG LCARS: no screen captures or animated noise. */
public final class LcarsFrame extends Drawable{
 private final Paint p=new Paint(3);private final RectF rect=new RectF();private final float d;private int alpha=255;
 public LcarsFrame(float density){d=density;}
 private void color(int c){p.setColor(c);p.setAlpha(alpha);}
 public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height(),rail=24*d,r=28*d;if(w<r*3||h<r*3)return;c.save();c.translate(b.left,b.top);color(0xff000000);c.drawRect(0,0,w,h,p);
  color(0xffffb56c);rect.set(0,0,w,62*d);c.drawRoundRect(rect,r,r,p);color(0xff000000);c.drawRect(rail,8*d,w,64*d,p);
  color(0xffffb56c);rect.set(0,h-62*d,w,h);c.drawRoundRect(rect,r,r,p);color(0xff000000);c.drawRect(rail,h-64*d,w,h-8*d,p);
  color(0xffb5a1dc);c.drawRect(0,64*d,rail,h*.47f,p);color(0xffd992aa);c.drawRect(0,h*.47f+5*d,rail,h-64*d,p);
  color(0xff000000);c.drawRect(w*.60f,0,w*.60f+5*d,8*d,p);c.drawRect(w*.82f,0,w*.82f+5*d,8*d,p);c.drawRect(w*.72f,h-8*d,w*.72f+5*d,h,p);
  color(0xffb5a1dc);c.drawRect(w*.60f+5*d,0,w*.82f,8*d,p);color(0xffd992aa);c.drawRect(w*.82f+5*d,0,w,8*d,p);c.restore();
 }
 public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.OPAQUE;}
}
