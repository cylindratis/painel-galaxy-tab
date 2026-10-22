package br.casasala.painel;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Moulded enamel / metal: restrained bevel, recessed pressed state; no bitmap or noise. */
public final class TactileSurface extends Drawable{
 private final Paint p=new Paint(3);private final RectF rect=new RectF();private final float d;private final int color;private final boolean down;private int alpha=255;
 public TactileSurface(float density,int c,boolean pressed){d=density;color=c;down=pressed;}
 private int mix(int c,int b,float f){return Color.rgb((int)(Color.red(c)*(1-f)+Color.red(b)*f),(int)(Color.green(c)*(1-f)+Color.green(b)*f),(int)(Color.blue(c)*(1-f)+Color.blue(b)*f));}
 public void draw(Canvas c){Rect b=getBounds();if(b.width()<2||b.height()<2)return;p.setAlpha(alpha);p.setStyle(Paint.Style.FILL);rect.set(b.left+d,b.top+d,b.right-d,b.bottom-d);float radius=7*d;
  p.setShader(new LinearGradient(0,rect.top,0,rect.bottom,new int[]{mix(color,down?Color.BLACK:Color.WHITE,down?.13f:.22f),color,mix(color,down?Color.WHITE:Color.BLACK,down?.03f:.08f)},new float[]{0,.5f,1},Shader.TileMode.CLAMP));c.drawRoundRect(rect,radius,radius,p);p.setShader(null);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(mix(color,Color.BLACK,.32f));c.drawRoundRect(rect,radius,radius,p);rect.inset(1.5f*d,1.5f*d);p.setColor(down?0x40000000:0xaaffffff);c.drawRoundRect(rect,Math.max(1,radius-d),Math.max(1,radius-d),p);p.setStyle(Paint.Style.FILL);
 }
 public void setAlpha(int value){alpha=value;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}