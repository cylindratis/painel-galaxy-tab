package br.casasala.painel;
import android.content.Context;import android.graphics.Canvas;import android.graphics.Paint;import android.graphics.Path;import android.widget.Button;
/** Accessible native Button with vector transport artwork, no font glyph dependency. */
public final class TransportButton extends Button{
 private final Paint paint=new Paint(3);private final String command;private final int ink;
 public TransportButton(Context c,String label,String cmd,VisualTheme theme,boolean primary){super(c);command=cmd;ink=theme.controlInk(primary);setText(label);setContentDescription(label);setTextColor(0x00000000);setMinimumWidth(0);setMinWidth(0);setMinimumHeight(0);setMinHeight(0);setBackgroundDrawable(theme.buttonBackground(c,primary?theme.accent:theme.panel,primary));}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);float d=getResources().getDisplayMetrics().density;canvas.scale(d,d);paint.setColor(isEnabled()?ink:(ink&0x00ffffff)|0x55000000);paint.setStyle(Paint.Style.FILL);
  if(command.equals("Pause")){canvas.drawRect(-8,-11,-3,11,paint);canvas.drawRect(3,-11,8,11,paint);}else{boolean prev=command.equals("PreviousTrack");boolean next=command.equals("NextTrack");if(prev)canvas.scale(-1,1);Path t=new Path();t.moveTo(-7,-11);t.lineTo(10,0);t.lineTo(-7,11);t.close();canvas.drawPath(t,paint);if(prev||next)canvas.drawRect(10,-11,13,11,paint);}canvas.restore();
 }
}
