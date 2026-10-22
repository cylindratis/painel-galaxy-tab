package br.casasala.painel;
import android.content.Context;import android.view.View;import android.view.ViewGroup;
/** A square instrument fitted inside its actual remaining space, in either orientation. */
public final class ClockFrame extends ViewGroup{
 public ClockFrame(Context c){super(c);}
 protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);if(MeasureSpec.getMode(width)==MeasureSpec.UNSPECIFIED)w=240;if(MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED)h=w;setMeasuredDimension(w,h);int side=Math.max(1,Math.min(w,h));if(getChildCount()>0)getChildAt(0).measure(MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY));}
 protected void onLayout(boolean changed,int l,int t,int r,int b){if(getChildCount()==0)return;View child=getChildAt(0);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight(),x=(getWidth()-w)/2,y=(getHeight()-h)/2;child.layout(x,y,x+w,y+h);}
}