package com.e7.bmfarm;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Only Android's user-enabled accessibility API; no shell/root fallback. */
public final class PhoneAccessibility extends AccessibilityService {
 public static final String GAME="com.zlongame.cn.epicseven";
 private static volatile PhoneAccessibility instance;
 private final Handler main=new Handler(Looper.getMainLooper());
 public static boolean ready(){return instance!=null;}
 @Override protected void onServiceConnected(){instance=this;}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
 private Rect foregroundOnMain()throws IOException{
  if(((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked())throw new IOException("屏幕已锁定，停止操作");
  for(AccessibilityWindowInfo w:getWindows()){
   if(!w.isFocused())continue;
   if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)throw new IOException("有系统窗口遮挡，请回到游戏");
   AccessibilityNodeInfo node=w.getRoot();
   if(node==null)throw new IOException("无法读取前台游戏身份，请重新开启无障碍");
   try{
    if(!GAME.contentEquals(node.getPackageName()==null?"":node.getPackageName()))throw new IOException("国服第七史诗不在前台，已停止");
    Rect bounds=new Rect();w.getBoundsInScreen(bounds);return bounds;
   }finally{node.recycle();}
  }
  throw new IOException("无法核对游戏窗口，请关闭分屏和系统弹窗");
 }
 public static Rect gameBounds()throws Exception{
  PhoneAccessibility s=instance;if(s==null)throw new IOException("无障碍服务未开启或已断开");
  CountDownLatch done=new CountDownLatch(1);AtomicReference<Rect> value=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
  s.main.post(()->{try{value.set(s.foregroundOnMain());}catch(Exception e){error.set(e);}finally{done.countDown();}});
  if(!done.await(2000,TimeUnit.MILLISECONDS))throw new IOException("前台窗口核对超时");
  if(error.get()!=null)throw error.get();return value.get();
 }
 public static boolean[] captureForeground(String ownPackage)throws Exception{
  PhoneAccessibility s=instance;if(s==null)throw new IOException("无障碍服务不可用");
  CountDownLatch done=new CountDownLatch(1);AtomicReference<boolean[]> value=new AtomicReference<>();
  s.main.post(()->{
   boolean game=false,ownOverlay=false,visibleGame=false;
   try{
    for(AccessibilityWindowInfo w:s.getWindows()){
     AccessibilityNodeInfo node=w.getRoot();if(node==null)continue;
     try{
      String pkg=node.getPackageName()==null?"":node.getPackageName().toString();
      if(GAME.equals(pkg)&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){visibleGame=true;if(w.isFocused())game=true;}
      if(w.isFocused()&&ownPackage.equals(pkg)&&w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)ownOverlay=true;
     }finally{node.recycle();}
    }
    value.set(new boolean[]{game,ownOverlay&&visibleGame});
   }catch(RuntimeException e){value.set(new boolean[]{false,false});}finally{done.countDown();}
  });
  if(!done.await(2000,TimeUnit.MILLISECONDS)||value.get()==null)throw new IOException("前台核对超时");return value.get();
 }
 public static void gesture(int x1,int y1,int x2,int y2,int duration,Rect expected,BooleanSupplier cancelled)throws Exception{
  PhoneAccessibility s=instance;if(s==null)throw new IOException("无障碍服务已断开");
  CountDownLatch done=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();
  final java.util.concurrent.atomic.AtomicBoolean abandoned=new java.util.concurrent.atomic.AtomicBoolean();
  s.main.post(()->{
   try{
    if(abandoned.get()||cancelled.getAsBoolean())throw new InterruptedException("stopped");
    Rect current=s.foregroundOnMain();
    if(!current.equals(expected)||!current.contains(x1,y1)||!current.contains(x2,y2))throw new IOException("窗口位置已变化，请重新检查画面");
    Path path=new Path();path.moveTo(x1,y1);if(x1!=x2||y1!=y2)path.lineTo(x2,y2);
    GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,Math.max(60,duration))).build();
    if(!s.dispatchGesture(g,new GestureResultCallback(){
     @Override public void onCompleted(GestureDescription g){done.countDown();}
     @Override public void onCancelled(GestureDescription g){error.set(new IOException("手势被系统或手动触摸中断，已停止"));done.countDown();}
    },s.main))throw new IOException("系统拒绝执行无障碍手势");
   }catch(Exception e){error.set(e);done.countDown();}
  });
  try{if(!done.await(duration+2500L,TimeUnit.MILLISECONDS))throw new IOException("无障碍手势超时");}
  finally{abandoned.set(true);}
  if(error.get()!=null)throw error.get();
 }
}
