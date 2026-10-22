package br.casasala.painel;
import android.graphics.Path;import android.graphics.RectF;
import android.content.Context;import android.graphics.Bitmap;import android.graphics.Canvas;import android.graphics.Paint;import android.widget.ImageView;
/** Aspect is imposed by the media type, never by a full-width letterbox. */
public final class MediaArtView extends ImageView{
 private final Path mask=new Path();private final float radius;
 private final float aspect;private final Paint p=new Paint(3);private final int ink;private boolean loaded;
 public MediaArtView(Context c,float ratio,VisualTheme theme){super(c);aspect=ratio;radius=(theme.id.equals("home")?16:theme.id.equals("bauhaus")?3:8)*c.getResources().getDisplayMetrics().density;ink=theme.muted;setBackgroundColor(theme.panel);setScaleType(ScaleType.CENTER_CROP);}
 public void setImageBitmap(Bitmap bitmap){loaded=bitmap!=null;super.setImageBitmap(bitmap);}
 protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);mask.reset();mask.addRoundRect(new RectF(0,0,w,h),radius,radius,Path.Direction.CW);}
 public void draw(Canvas canvas){int saved=canvas.save();canvas.clipPath(mask);super.draw(canvas);canvas.restoreToCount(saved);}
 protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w/aspect));}
 protected void onDraw(Canvas c){super.onDraw(c);if(!loaded){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.3f;p.setColor(ink);p.setStrokeWidth(2*getResources().getDisplayMetrics().density);p.setStyle(Paint.Style.STROKE);if(aspect<.9f)c.drawRect(x-r,y-r*1.4f,x+r,y+r*1.4f,p);else{c.drawCircle(x,y,r,p);c.drawCircle(x,y,r*.17f,p);}}}
}
