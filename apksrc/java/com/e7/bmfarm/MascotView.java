package com.e7.bmfarm;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
/** Play whole original 260x304 frames with one uniform display scale. */
public final class MascotView extends View {
 private final MascotTimeline timeline=new MascotTimeline();
 private final Bitmap[] frames=new Bitmap[MascotTimeline.FRAME_COUNT];
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final RectF target=new RectF();
 private boolean attached,released;
 private final Runnable tick=new Runnable(){public void run(){
  if(!attached||released)return;
  long now=SystemClock.uptimeMillis();invalidate();long delay=timeline.delayUntilNext(now);if(delay>0)postDelayed(this,delay);
 }};
 public MascotView(Context context)throws IOException {
  super(context);
  try{
   BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
   for(int i=0;i<MascotTimeline.FRAME_COUNT;i++){
    try(InputStream in=context.getAssets().open(String.format(java.util.Locale.ROOT,"mascot/frame-%03d.png",i))){frames[i]=BitmapFactory.decodeStream(in,null,options);}
    if(frames[i]==null||frames[i].getWidth()!=MascotTimeline.WIDTH||frames[i].getHeight()!=MascotTimeline.HEIGHT||!frames[i].hasAlpha())throw new IOException("原动画帧缺失或尺寸不符："+i);
   }
  }catch(IOException|RuntimeException e){release();throw e;}
  setBackgroundColor(Color.TRANSPARENT);setContentDescription("奥芙悬浮窗：点击展开，拖动移动");
 }
 public void setMode(MascotTimeline.Mode mode){if(released||timeline.mode()==mode)return;timeline.setMode(mode,SystemClock.uptimeMillis());removeCallbacks(tick);if(attached)post(tick);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;removeCallbacks(tick);post(tick);}
 @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(tick);super.onDetachedFromWindow();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);if(released)return;
  float scale=Math.min(getWidth()/(float)MascotTimeline.WIDTH,getHeight()/(float)MascotTimeline.HEIGHT),w=MascotTimeline.WIDTH*scale,h=MascotTimeline.HEIGHT*scale;
  target.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
  canvas.drawBitmap(frames[timeline.frame(SystemClock.uptimeMillis())],null,target,paint);
 }
 public void release(){if(released)return;released=true;removeCallbacks(tick);for(Bitmap b:frames)if(b!=null)b.recycle();}
}

