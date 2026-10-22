package br.casasala.painel;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Calendar;

/** Native dial; no allocations or network in the animation path. */
public final class AnalogClockView extends View {
    private final Paint paint=new Paint(3);
    private final RectF face=new RectF();
    private final Calendar time=Calendar.getInstance();
    private final String[] numerals={"12","1","2","3","4","5","6","7","8","9","10","11"};
    private final float[] sin=new float[60],cos=new float[60];
    private VisualTheme theme;
    private Bitmap dialCache;
    private boolean showSeconds=true,bauhaus=true,running;
    private int interval=17;
    private final Runnable frame=new Runnable(){public void run(){if(!running)return;invalidate();postDelayed(this,interval);}};
    public AnalogClockView(Context context,VisualTheme palette){super(context);theme=palette;paint.setTypeface(LcarsTypeface.get(context,palette));for(int i=0;i<60;i++){sin[i]=(float)Math.sin(i*Math.PI/30);cos[i]=(float)Math.cos(i*Math.PI/30);}setContentDescription("Relógio analógico");}
    public void setTheme(VisualTheme palette){theme=palette;clearCache();invalidate();}
    public void setShowSeconds(boolean show){showSeconds=show;animate();invalidate();}
    public void setStyle(String style){bauhaus="bauhaus".equals(style);clearCache();invalidate();}
    public void setFrameRate(int rate){interval=rate>=60?17:33;animate();}
    public void tick(){invalidate();}
    private void animate(){removeCallbacks(frame);if(running&&showSeconds)postDelayed(frame,interval);}
    protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;animate();}
    protected void onDetachedFromWindow(){running=false;removeCallbacks(frame);clearCache();super.onDetachedFromWindow();}
    protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);running=visibility==VISIBLE;animate();}
    protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);running=visibility==VISIBLE&&isShown();animate();}
    protected void onMeasure(int w,int h){int d=Math.round(244*getResources().getDisplayMetrics().density);setMeasuredDimension(resolveSize(d,w),resolveSize(d,h));}
    protected void onDraw(Canvas c){
        float side=Math.min(getWidth(),getHeight());if(side<40)return;float x=getWidth()/2f,y=getHeight()/2f;
        if(dialCache==null||dialCache.getWidth()!=getWidth()||dialCache.getHeight()!=getHeight()){
            clearCache();dialCache=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);drawFace(new Canvas(dialCache),side,x,y);
        }
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);c.drawBitmap(dialCache,0,0,null);
        int ink=isBauhaus()?0xff242421:theme.clockInk;
        time.setTimeInMillis(System.currentTimeMillis());float sec=time.get(Calendar.SECOND)+time.get(Calendar.MILLISECOND)/1000f,minute=time.get(Calendar.MINUTE)+sec/60f,hour=time.get(Calendar.HOUR)+minute/60f;
        if(isBauhaus()){c.save();c.rotate(hour*30,x,y);paint.setColor(ink);c.drawRect(x-side*.026f,y-side*.225f,x+side*.026f,y+side*.048f,paint);c.restore();hand(c,x,y,minute*6,side*.32f,side*.027f,0xff245f9e,side*.05f);}
        else{hand(c,x,y,hour*30,side*.22f,side*.034f,ink,side*.04f);hand(c,x,y,minute*6,side*.32f,side*.022f,ink,side*.05f);}
        if(showSeconds)hand(c,x,y,sec*6,side*.39f,Math.max(1,side*.006f),isBauhaus()?0xffbd3026:theme.clockAccent,side*.085f);
        paint.setStyle(Paint.Style.FILL);paint.setColor(showSeconds?(isBauhaus()?0xffbd3026:theme.clockAccent):ink);c.drawCircle(x,y,side*.021f,paint);
    }
    private boolean isBauhaus(){return bauhaus&&!theme.id.equals("lcars");}
    private void clearCache(){if(dialCache!=null){dialCache.recycle();dialCache=null;}}
    private void drawFace(Canvas c,float side,float x,float y){
        face.set(x-side*.49f,y-side*.49f,x+side*.49f,y+side*.49f);paint.setStyle(Paint.Style.FILL);paint.setColor(isBauhaus()?0xfffaf6eb:theme.clockFace);paint.setAlpha(255);
        c.drawRoundRect(face,side*(isBauhaus()?.025f:.085f),side*(isBauhaus()?.025f:.085f),paint);int ink=isBauhaus()?0xff242421:theme.clockInk;paint.setStrokeCap(Paint.Cap.BUTT);
        for(int i=0;i<60;i++){boolean hour=i%5==0;float outer=side*.435f,inner=outer-side*(hour?.046f:.016f);paint.setColor(ink);paint.setAlpha(hour?255:95);paint.setStrokeWidth(side*(hour?.012f:.003f));c.drawLine(x+sin[i]*inner,y-cos[i]*inner,x+sin[i]*outer,y-cos[i]*outer,paint);}
        paint.setAlpha(255);paint.setColor(ink);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(side*.085f);float baseline=-(paint.ascent()+paint.descent())/2;
        for(int i=0;i<12;i++)if(!isBauhaus() || i%3==0)c.drawText(numerals[i],x+sin[i*5]*side*.32f,y-cos[i*5]*side*.32f+baseline,paint);
        if(theme.id.equals("lcars")){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(side*.016f);RectF ring=new RectF(x-side*.475f,y-side*.475f,x+side*.475f,y+side*.475f);int[] colors={0xffffb56c,0xffb5a1dc,0xffd992aa};for(int k=0;k<3;k++){paint.setColor(colors[k]);c.drawArc(ring,k*120+5,108,false,paint);}paint.setStyle(Paint.Style.FILL);}
        if(isBauhaus()){paint.setColor(0xffe3b82d);c.drawCircle(x,y+side*.22f,side*.055f,paint);}
    }
    private void hand(Canvas c,float x,float y,float degrees,float length,float width,int color,float tail){double a=degrees*Math.PI/180;float dx=(float)Math.sin(a),dy=-(float)Math.cos(a);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);c.drawLine(x-dx*tail,y-dy*tail,x+dx*length,y+dy*length,paint);paint.setStyle(Paint.Style.FILL);}
}
