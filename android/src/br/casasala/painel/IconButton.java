package br.casasala.painel;
import android.content.Context;import android.graphics.Canvas;import android.widget.Button;
/** Icon-only actions retain their native text and content description for accessibility. */
public final class IconButton extends Button{
 private final PanelIcon icon;private final int size;
 public IconButton(Context c,String label,String glyph,VisualTheme theme,boolean selected){super(c);setText(label);setContentDescription(label);setTextColor(0x00000000);setMinimumWidth(0);setMinWidth(0);setMinimumHeight(0);setMinHeight(0);size=Math.round(22*c.getResources().getDisplayMetrics().density);icon=new PanelIcon(glyph,theme.controlInk(selected),size);setBackgroundDrawable(theme.buttonBackground(c,selected?theme.accent:theme.panel,selected));}
 protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate((getWidth()-size)/2f,(getHeight()-size)/2f);icon.setAlpha(isEnabled()?255:70);icon.draw(c);c.restore();}
}
