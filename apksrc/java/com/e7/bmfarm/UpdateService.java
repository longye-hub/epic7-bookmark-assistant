package com.e7.bmfarm;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.*;
/** Private :updates process; IPC accepts a check signal and returns public metadata only. */
public final class UpdateService extends Service {
 public static final int CHECK=1,RESULT=2;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),msg->{
  if(msg.what!=CHECK||msg.replyTo==null)return true;
  Messenger reply=msg.replyTo;
  int generation=msg.arg1;
  io.execute(()->{
   Bundle data=new Bundle();
   try{data.putString("metadata",UpdateTransport.fetch(getPackageName()));}
   catch(Exception e){data.putString("error","无法获取更新信息，请稍后重试或打开源码仓库。");}
   Message result=Message.obtain(null,RESULT);result.arg1=generation;result.setData(data);try{reply.send(result);}catch(RemoteException ignored){}
  });return true;
 }));
 @Override public void onCreate(){super.onCreate();java.net.CookieHandler.setDefault(null);java.net.Authenticator.setDefault(null);}
 @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
 @Override public void onDestroy(){UpdateTransport.cancel();io.shutdownNow();super.onDestroy();}
}
