package com.e7.bmfarm;
/** Screen-bounded overlay sizing; float DPI factors are never truncated. */
public final class UiSizing {
    public final float scale;
    public final int width,height;
    public UiSizing(float density,int screenWidth,int screenHeight) {
        if(density<=0||screenWidth<240||screenHeight<240)throw new IllegalArgumentException("invalid display metrics");
        scale=Math.min(density,Math.min((screenWidth-48)/336f,(screenHeight-64)/330f));
        width=px(336);height=px(330);
    }
    public int px(float units){return Math.max(1,Math.round(units*scale));}
}
