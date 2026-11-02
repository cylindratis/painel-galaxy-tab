package br.casasala.painel;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Original MAGI-inspired frame. The three names are fictional terminals, not device status. */
public final class MagiFrame extends Drawable {
 private final Paint p=new Paint(3);private final float d;private final VisualTheme theme;private final Typeface title;
 public MagiFrame(android.content.Context context,VisualTheme t){d=context.getResources().getDisplayMetrics().density;theme=t;title=MagiTypeface.get(context,true);}
 public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);float w=b.width(),h=b.height();p.setColor(theme.bg);c.drawRect(0,0,w,h,p);p.setColor(theme.accent);c.drawRect(0,0,w,2*d,p);p.setTypeface(title);p.setTextSize(20*d);c.drawText("MAGI",10*d,24*d,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(11*d);p.setColor(theme.clockAccent);p.setTextAlign(Paint.Align.RIGHT);c.drawText(theme.id.equals("magi-red")?"CONTROLE / ALERTA":"CONTROLE / DOMÉSTICO",w-12*d,23*d,p);p.setTextAlign(Paint.Align.LEFT);p.setColor(theme.accent);c.drawRect(0,h-3*d,w*.66f,h,p);p.setColor(theme.clockAccent);c.drawRect(w*.66f+4*d,h-3*d,w,h,p);c.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.OPAQUE;}
}
