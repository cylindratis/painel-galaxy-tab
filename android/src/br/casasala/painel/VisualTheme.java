package br.casasala.painel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Small, dependency-free palettes and native surfaces for Android 2.2. */
public final class VisualTheme {
    public final String id;
    public final String title;
    public final int bg, panel, ink, muted, accent, edge, onPanel, nav;
    public final int accentInk, clockFace, clockInk, clockAccent;

    private VisualTheme(String name, String label, int background, int surface,
                        int text, int secondary, int highlight, int border,
                        int enabledSurface, int navigation, int highlightText,
                        int dial, int dialText, int secondHand) {
        id = name;
        title = label;
        bg = background;
        panel = surface;
        ink = text;
        muted = secondary;
        accent = highlight;
        edge = border;
        onPanel = enabledSurface;
        nav = navigation;
        accentInk = highlightText;
        clockFace = dial;
        clockInk = dialText;
        clockAccent = secondHand;
    }

    public static VisualTheme get(String id) {
        if("lcars".equals(id))return new VisualTheme("lcars","LCARS · TNG",0xff000000,0xff100d17,0xffffd5a3,0xffc6b7e6,0xffffb56c,0xff76628b,0xff2b213c,0xff000000,0xff100d17,0xff000000,0xffffd5a3,0xffb5a1dc);
        if ("bauhaus".equals(id)) {
            return new VisualTheme("bauhaus", "Bauhaus", 0xfff1ecdf, 0xfffaf6eb, 0xff20201e, 0xff615e54,
                0xff20201e, 0xffd6d0c1, 0xfff4df93, 0xfff1ecdf, 0xfffaf6eb, 0xfffaf6eb, 0xff20201e, 0xffbd3026);
        }
        if ("light".equals(id)) {
            return new VisualTheme("light", "Branco",
                0xfff7f7f4, 0xffffffff, 0xff202322, 0xff62665f,
                0xff236b50, 0xffd9e1dc, 0xffdfeee4, 0xffe9efeb,
                0xffffffff, 0xfffbfbf7, 0xff25352c, 0xffc75c35);
        }
        if ("skeuo".equals(id)) {
            return new VisualTheme("skeuo", "Tátil",
                0xffd9ddde, 0xffedf0ee, 0xff202a29, 0xff4c5b59,
                0xff365e58, 0xffa1acab, 0xffd7e4da, 0xffb8c1c1,
                0xfff9fbf5, 0xfff4f1e7, 0xff25342f, 0xffbb6633);
        }
        if ("home".equals(id)) {
            return new VisualTheme("home", "Casa",
                0xffdce8ed, 0xfff8fbfc, 0xff1c3040, 0xff506979,
                0xff256caa, 0xffc0d3df, 0xffd7e8f6, 0xffd0dfe6,
                0xffffffff, 0xfff9fbfc, 0xff243d51, 0xffe68b25);
        }
        return new VisualTheme("dark", "Escuro",
            0xff101b1b, 0xff1a2929, 0xffedf3ed, 0xffa1b6b0,
            0xffb0ead0, 0xff30413e, 0xff28453c, 0xff142120,
            0xff11221a, 0xff101919, 0xffe9efe7, 0xffffae55);
    }

    /**
     * A rounded native drawable, with tactile bevels only in the skeuomorphic
     * palette. Pass the desired surface/button color; its contrast is preserved.
     */
    public Drawable background(Context context, int color, boolean emphasized) {
        float density = context.getResources().getDisplayMetrics().density;
        float radius = ("skeuo".equals(id) ? 10 : "home".equals(id) ? 19 : "bauhaus".equals(id) ? 3 : 8) * density;
        int stroke = Math.max(1, Math.round(density));
        GradientDrawable surface;
        if ("skeuo".equals(id)) return new TactileSurface(density,color,false);
        if ("home".equals(id)) {
            surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{mix(color, 0xffffffff, emphasized ? .10f : .025f), color});
        } else {
            surface = new GradientDrawable();
            surface.setColor(color);
        }
        surface.setCornerRadius(radius);
        surface.setStroke(stroke, emphasized ? mix(color, ink, .10f) : edge);
        return surface;
    }

    /** Native pressed/focused feedback, sharing the theme's surfaces and outline. */
    public Drawable buttonBackground(Context context,int color,boolean selected){
        if("lcars".equals(id)){android.graphics.drawable.StateListDrawable lcars=new android.graphics.drawable.StateListDrawable();int fill=selected?accent:0xffb5a1dc;lcars.addState(new int[]{-android.R.attr.state_enabled},lcarsButton(context,0xff61516f));lcars.addState(new int[]{android.R.attr.state_pressed},lcarsButton(context,0xfff1c4da));lcars.addState(new int[]{android.R.attr.state_focused},lcarsButton(context,0xffffead0));lcars.addState(new int[]{},lcarsButton(context,fill));return lcars;}
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},"skeuo".equals(id)?new TactileSurface(context.getResources().getDisplayMetrics().density,color,true):background(context,mix(color,ink,.12f),true));
        states.addState(new int[]{android.R.attr.state_focused},background(context,mix(color,accent,.12f),true));
        states.addState(new int[]{},background(context,color,selected));return states;
    }

    public int controlInk(boolean selected){return "lcars".equals(id)?0xff100d17:selected?accentInk:ink;}
    private Drawable lcarsButton(Context c,int fill){GradientDrawable shape=new GradientDrawable();shape.setColor(fill);float r=24*c.getResources().getDisplayMetrics().density;shape.setCornerRadii(new float[]{0,0,r,r,r,r,0,0});return shape;}

    /** Soft full-page backgrounds; call setBackgroundDrawable on the root. */
    public Drawable pageBackground() {
        if ("home".equals(id)) {
            return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xffd5e4ed, 0xffedf0e6, 0xffd5e7e2});
        }
        if ("skeuo".equals(id)) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xffe4e8e8, bg, 0xffc3cccb});
        }
        GradientDrawable plain = new GradientDrawable();
        plain.setColor(bg);
        return plain;
    }

    private static int mix(int first, int second, float amount) {
        float remainder = 1f - amount;
        int a = Math.round((first >>> 24) * remainder + (second >>> 24) * amount);
        int r = Math.round(((first >> 16) & 255) * remainder + ((second >> 16) & 255) * amount);
        int g = Math.round(((first >> 8) & 255) * remainder + ((second >> 8) & 255) * amount);
        int b = Math.round((first & 255) * remainder + (second & 255) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
