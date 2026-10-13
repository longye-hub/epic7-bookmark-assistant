package com.e7.bmfarm;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One consent token, one VirtualDisplay. Resize in place; never reuse consent. */
public final class PhoneCapture implements AutoCloseable {
 private final Context context;
 private final java.util.function.Consumer<String> revoked;
 private final HandlerThread thread=new HandlerThread("PhoneCapture");
 private Handler handler;
 private MediaProjection projection;
 private VirtualDisplay display;
 private ImageReader reader;
 private int width,height;
 private volatile boolean closed;
 private Request request;
 private static final class Request {final CountDownLatch done=new CountDownLatch(1);ShopVision.Frame frame;Exception error;}
 public PhoneCapture(Context c,int result,Intent data,java.util.function.Consumer<String> revoked)throws Exception{
  context=c;this.revoked=revoked;thread.start();handler=new Handler(thread.getLooper());
  try{
   projection=((MediaProjectionManager)c.getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(result,data);
   if(projection==null)throw new IOException("屏幕捕获授权无效，请重新授权");
   projection.registerCallback(new MediaProjection.Callback(){
    @Override public void onStop(){if(!closed){close();revoked.accept("屏幕共享已结束，请回助手重新授权");}}
    @Override public void onCapturedContentResize(int w,int h){
     if(closed)return;
     handler.postDelayed(()->{
      if(closed)return;DisplayMetrics m=metrics();
      if(w!=m.widthPixels||h!=m.heightPixels){close();revoked.accept("请授权共享整个屏幕；单个应用共享无法可靠映射点击位置");return;}
      resizeToScreen();
     },120);
    }
   },handler);
   DisplayMetrics m=metrics();width=m.widthPixels;height=m.heightPixels;
   // Keep only consent here. No screen surface is attached until a verified game capture request.
  }catch(Exception e){close();throw e;}
 }
 private DisplayMetrics metrics(){DisplayMetrics m=new DisplayMetrics();((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);return m;}
 private ImageReader createReader(int w,int h){
  ImageReader r=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
  r.setOnImageAvailableListener(source->{
   try(Image image=source.acquireLatestImage()){
    if(image==null||request==null)return;
    Request q=request;request=null;
    try{
     Image.Plane plane=image.getPlanes()[0];int stride=plane.getRowStride(),pixelStride=plane.getPixelStride();
     ByteBuffer buf=plane.getBuffer();int w1=image.getWidth(),h1=image.getHeight();
     int[] pixels=new int[w1*h1];
     for(int y=0;y<h1;y++)for(int x=0;x<w1;x++){int i=y*stride+x*pixelStride;pixels[y*w1+x]=0xff000000|((buf.get(i)&255)<<16)|((buf.get(i+1)&255)<<8)|(buf.get(i+2)&255);}
     q.frame=new ShopVision.Frame(w1,h1,pixels);
    }catch(Exception e){q.error=e;}finally{if(display!=null)display.setSurface(null);q.done.countDown();}
   }catch(IllegalStateException ignored){}
  },handler);return r;
 }
 public void resizeToScreen(){handler.post(()->{
  if(closed||display==null)return;DisplayMetrics m=metrics();
  if(m.widthPixels==width&&m.heightPixels==height)return;
  failRequest("屏幕发生旋转或尺寸变化，请重新检查画面");
  ImageReader old=reader;reader=createReader(m.widthPixels,m.heightPixels);
  display.resize(m.widthPixels,m.heightPixels,m.densityDpi);display.setSurface(reader.getSurface());
  width=m.widthPixels;height=m.heightPixels;old.close();
 });}
 private void failRequest(String msg){if(display!=null)display.setSurface(null);if(request!=null){request.error=new IOException(msg);request.done.countDown();request=null;}}
 public ShopVision.Frame capture()throws Exception{
  if(closed)throw new IOException("屏幕捕获已停止，请回助手重新授权");
  Request q=new Request();
  handler.post(()->{
   if(closed){q.error=new IOException("屏幕捕获已停止");q.done.countDown();return;}
   failRequest("重叠截图请求已取消");
   if(reader!=null)try(Image stale=reader.acquireLatestImage()){}catch(IllegalStateException ignored){}
   request=q;
   try{
    if(display==null){DisplayMetrics m=metrics();width=m.widthPixels;height=m.heightPixels;reader=createReader(width,height);display=projection.createVirtualDisplay("手机书签助手",width,height,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);}
    else display.setSurface(reader.getSurface());
   }catch(Exception e){q.error=e;request=null;q.done.countDown();}
  });
  boolean completed=false;
  try{completed=q.done.await(3000,TimeUnit.MILLISECONDS);if(!completed)throw new IOException("未收到新截图，请检查屏幕共享与游戏状态");if(q.error!=null)throw q.error;return q.frame;}
  finally{if(!completed)handler.post(()->{if(request==q){failRequest("截图请求已取消");}else q.frame=null;});}
 }
 @Override public void close(){
  if(closed)return;closed=true;
  if(handler!=null)handler.post(()->{failRequest("屏幕捕获已结束");if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();thread.quitSafely();});
 }
}
