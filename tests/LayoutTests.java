package com.e7.bmfarm;
public final class LayoutTests {
    public static void main(String[]args) {
        int checks=0;
        for(float density:new float[]{.75f,1f,1.5f,2f,3f}) {
            for(int[] screen:new int[][]{{1280,720},{720,1280},{640,360},{320,240}}) {
                UiSizing s=new UiSizing(density,screen[0],screen[1]);
                if(s.width<=0||s.height<=0||s.width+48>screen[0]||s.height+64>screen[1])throw new AssertionError("overlay outside screen");
                if(s.px(56)<=0||s.px(56)>screen[1])throw new AssertionError("invalid ball bounds");
                checks+=2;
            }
        }
        if(new UiSizing(1.5f,1280,720).px(56)!=84)throw new AssertionError("fractional DPI must retain 1.5 scale");
        System.out.println("LAYOUT_PASS "+(checks+1)+" assertions; no game input");
    }
}
