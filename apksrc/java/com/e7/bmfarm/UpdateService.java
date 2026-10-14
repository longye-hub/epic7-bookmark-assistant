package com.e7.bmfarm;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.*;
/** Private :updates process; IPC accepts a check signal and returns public text only. */
public final class UpdateService extends Service {
 public static final int CHECK=1,RESULT=2;
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),msg->{
  if(msg.what!=CHECK||msg.replyTo==null)return true;
  CheckResult result=new CheckResult(msg.replyTo,msg.arg1);
  io.execute(()->{
   String text=null;try{text=UpdateTransport.fetch(getPackageName());}catch(Exception ignored){}
   result.complete("metadata",text);
  });
  io.execute(()->{
   String text=null;try{text=UpdateTransport.fetchAnnouncement();}catch(Exception ignored){}
   result.complete("announcement",text);
  });return true;
 }));
 private static final class CheckResult {
  final Messenger reply;final int generation;final Bundle data=new Bundle();int completed;
  CheckResult(Messenger r,int g){reply=r;generation=g;}
  synchronized void complete(String key,String text){
   if(text!=null)data.putString(key,text);
   if(++completed!=2)return;
   Message result=Message.obtain(null,RESULT);result.arg1=generation;result.setData(data);
   try{reply.send(result);}catch(RemoteException ignored){}
  }
 }
 @Override public void onCreate(){super.onCreate();java.net.CookieHandler.setDefault(null);java.net.Authenticator.setDefault(null);}
 @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
 @Override public void onDestroy(){UpdateTransport.cancel();io.shutdownNow();super.onDestroy();}
}
