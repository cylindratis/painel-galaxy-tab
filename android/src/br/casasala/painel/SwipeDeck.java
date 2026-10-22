package br.casasala.painel;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.ViewFlipper;
/** Horizontal paging that leaves vertical scrolling to the forecast. */
public final class SwipeDeck extends ViewFlipper {
 private float x,y;private boolean horizontal;
 public SwipeDeck(Context c){super(c);}
 public boolean onInterceptTouchEvent(MotionEvent e){
  if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();horizontal=false;}
  if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-x,dy=e.getY()-y;if(Math.abs(dx)>24*getResources().getDisplayMetrics().density&&Math.abs(dx)>Math.abs(dy)){horizontal=true;return true;}}
  return super.onInterceptTouchEvent(e);
 }
 public boolean onTouchEvent(MotionEvent e){
  if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();return true;}
  if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-x;if(Math.abs(dx)>32*getResources().getDisplayMetrics().density){if(dx<0)showNext();else showPrevious();}horizontal=false;return true;}
  return horizontal||super.onTouchEvent(e);
 }
}
