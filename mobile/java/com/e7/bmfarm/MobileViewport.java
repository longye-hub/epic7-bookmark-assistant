package com.e7.bmfarm;

/** Immutable physical-screen mapping. Body is centred; header uses left/right safe edges. */
public final class MobileViewport {
 public final int screenWidth,screenHeight,left,top,right,bottom;
 public final double scale,bodyLeft;
 public MobileViewport(int sw,int sh,int l,int t,int r,int b){
  if(sw<=sh||l<0||t<0||r>sw||b>sh||r<=l||b<=t||(r-l)/(double)(b-t)<1.76||(r-l)/(double)(b-t)>2.8||b-t<480)
   throw new IllegalArgumentException("请使用横屏全屏游戏；当前区域无法可靠适配");
  screenWidth=sw;screenHeight=sh;left=l;top=t;right=r;bottom=b;
  scale=(b-t)/720.0;bodyLeft=(l+r-1280*scale)/2.0;
 }
 public static MobileViewport detect(ShopVision.Frame f,int l,int t,int r,int b){
  int maxX=(r-l)/5,maxY=(b-t)/8,ol=l,ot=t,or=r,ob=b;
  while(l<ol+maxX&&blackColumn(f,l,t,b))l++;
  while(r>or-maxX&&blackColumn(f,r-1,t,b))r--;
  while(t<ot+maxY&&blackRow(f,t,l,r))t++;
  while(b>ob-maxY&&blackRow(f,b-1,l,r))b--;
  return new MobileViewport(f.width,f.height,l,t,r,b);
 }
 private static boolean dark(int p){return (p>>16&255)<24&&(p>>8&255)<24&&(p&255)<24;}
 private static boolean blackColumn(ShopVision.Frame f,int x,int t,int b){int misses=0;for(int y=t;y<b;y+=3)if(!dark(f.pixels[y*f.width+x])&&++misses>2)return false;return true;}
 private static boolean blackRow(ShopVision.Frame f,int y,int l,int r){int misses=0;for(int x=l;x<r;x+=3)if(!dark(f.pixels[y*f.width+x])&&++misses>2)return false;return true;}
 public int[] point(int x,int y){
  if(x<0||x>=1280||y<65||y>=720)throw new IllegalArgumentException("输入位置超出商店操作区域");
  return new int[]{(int)Math.round(bodyLeft+x*scale),(int)Math.round(top+y*scale)};
 }
 public ShopVision.Frame normalize(ShopVision.Frame raw){
  if(raw.width!=screenWidth||raw.height!=screenHeight)throw new IllegalArgumentException("屏幕尺寸已变化，请重新检查画面");
  int[] out=new int[1280*720];
  for(int y=0;y<720;y++)for(int x=0;x<1280;x++){
   double origin=y<65?(x<400?left:right-1280*scale):bodyLeft;
   double sx=origin+(x+.5)*scale-.5,sy=top+(y+.5)*scale-.5;
   out[y*1280+x]=sample(raw,sx,sy);
  }
  if(scale>1.1){
   int[] sharp=out.clone();
   for(int y=1;y<719;y++)for(int x=1;x<1279;x++){int i=y*1280+x,p=0xff000000;
    for(int shift=0;shift<=16;shift+=8){int c=out[i]>>shift&255;double avg=((out[i-1]>>shift&255)+(out[i+1]>>shift&255)+(out[i-1280]>>shift&255)+(out[i+1280]>>shift&255))/4.0;int v=(int)Math.round(c+.6*(c-avg));p|=Math.max(0,Math.min(255,v))<<shift;}sharp[i]=p;
   }out=sharp;
  }
  return new ShopVision.Frame(1280,720,out);
 }
 private static int sample(ShopVision.Frame f,double x,double y){
  int x0=Math.max(0,Math.min(f.width-1,(int)Math.floor(x))),y0=Math.max(0,Math.min(f.height-1,(int)Math.floor(y)));
  int x1=Math.min(f.width-1,x0+1),y1=Math.min(f.height-1,y0+1);double a=Math.max(0,x-x0),b=Math.max(0,y-y0);
  int p=f.pixels[y0*f.width+x0],q=f.pixels[y0*f.width+x1],r=f.pixels[y1*f.width+x0],s=f.pixels[y1*f.width+x1],out=0xff000000;
  for(int shift=0;shift<=16;shift+=8){double v=((p>>shift&255)*(1-a)+(q>>shift&255)*a)*(1-b)+((r>>shift&255)*(1-a)+(s>>shift&255)*a)*b;out|=((int)Math.round(v))<<shift;}
  return out;
 }
 public boolean sameGeometry(MobileViewport o){return o!=null&&screenWidth==o.screenWidth&&screenHeight==o.screenHeight&&left==o.left&&right==o.right&&top==o.top&&bottom==o.bottom;}
 public String description(){return screenWidth+"×"+screenHeight+" / 区域 "+left+","+top+"—"+right+","+bottom;}
}
