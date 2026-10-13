package com.e7.bmfarm;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;

/** Launch-page UI; no screenshot/automation/storage records ever cross update IPC. */
public final class UpdateUi {
 private final Activity activity;private final String pkg;private final int installed;private final String name;
 private final SharedPreferences prefs;private final TextView status;private final Button check;
 private final Handler main=new Handler(Looper.getMainLooper());private boolean pending,bound,destroyed;private int generation;
 private final Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),this::handleResult));
 private boolean handleResult(Message msg){
  if(msg.what!=UpdateService.RESULT||msg.arg1!=generation||!pending||destroyed)return true;
  String text=msg.getData().getString("metadata");releaseBinding();
  if(activity.isFinishing())return true;
  if(text==null){status.setText("更新检查失败，可重试或打开源码仓库。");return true;}
  try{
   UpdateInfo info=UpdateInfo.parse(text,pkg);
   if(!info.newerThan(installed)){status.setText("当前已是该更新渠道的最新版本（"+name+"）。");return true;}
   status.setText("发现新版 "+info.versionName+("preview".equals(info.channel)?"（测试版）":""));
   new AlertDialog.Builder(activity).setTitle("发现助手更新")
    .setMessage("当前："+name+"\n新版："+info.versionName+("preview".equals(info.channel)?"（测试版）":"")+"\n\n"+info.notes+"\n\n更新前请停止任务并核对待确认交易。下载后由系统确认覆盖安装，不要先卸载。")
    .setPositiveButton("前往下载",(d,w)->open(info.downloadUrl)).setNeutralButton("版本说明",(d,w)->open(info.releaseUrl)).setNegativeButton("稍后",null).show();
  }catch(Exception e){status.setText("更新信息校验未通过，不打开安装包链接。");}
  return true;
 }
 private final Runnable timeout=this::onTimeout;
 private void onTimeout(){if(pending){releaseBinding();status.setText("更新检查超时，请稍后重试。");}}
 private final ServiceConnection connection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName name,IBinder binder){
   if(!pending)return;
   Message request=Message.obtain(null,UpdateService.CHECK);request.replyTo=reply;request.arg1=generation;
   // Deliberately no Bundle, version, screen, identifiers or configurable URL.
   try{new Messenger(binder).send(request);}catch(RemoteException e){releaseBinding();status.setText("更新服务未就绪，请重试。");}
  }
  @Override public void onServiceDisconnected(ComponentName name){if(pending){releaseBinding();status.setText("更新检查已中断，请重试。");}}
 };
 public UpdateUi(Activity a,LinearLayout parent){
  activity=a;pkg=a.getPackageName();int code=0;String version="未知";
  try{android.content.pm.PackageInfo info=a.getPackageManager().getPackageInfo(pkg,0);code=info.versionCode;version=info.versionName;}catch(Exception ignored){}
  installed=code;name=version;prefs=a.getSharedPreferences("update-controls",Context.MODE_PRIVATE);
  TextView current=new TextView(a);current.setText("当前版本："+name);current.setTextSize(14);parent.addView(current);
  CheckBox auto=new CheckBox(a);auto.setText("启动时自动检查更新（每天最多一次）");auto.setChecked(prefs.getBoolean("auto",true));auto.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("auto",on).apply();if(!on)cancel();});parent.addView(auto);
  TextView privacy=new TextView(a);privacy.setText("仅请求本仓库公开版本信息；不上传画面、余额或设备标识。");privacy.setTextSize(12);parent.addView(privacy);
  check=new Button(a);check.setText("手动检查最新版本");check.setOnClickListener(v->check());parent.addView(check);
  status=new TextView(a);status.setText("可手动检查更新。");status.setTextSize(13);parent.addView(status);
  TextView repo=new TextView(a);repo.setText("源码仓库：\n"+UpdateInfo.REPOSITORY);repo.setTextSize(12);parent.addView(repo);
  Button source=new Button(a);source.setText("查看源码仓库");source.setOnClickListener(v->open(UpdateInfo.REPOSITORY));parent.addView(source);
 }
 public void automatic(){
  if(!prefs.getBoolean("auto",true))return;
  long last=prefs.getLong("last-attempt",0),now=System.currentTimeMillis();
  if(prefs.getInt("checked-code",-1)!=installed||now<last||now-last>=86400000L)check();
 }
 private void check(){
  if(pending||destroyed||activity.isFinishing())return;
  generation++;pending=true;check.setEnabled(false);status.setText("正在读取公开更新信息…");
  prefs.edit().putLong("last-attempt",System.currentTimeMillis()).putInt("checked-code",installed).apply();
  try{bound=activity.bindService(new Intent(activity,UpdateService.class),connection,Context.BIND_AUTO_CREATE);if(!bound)throw new IllegalStateException();main.postDelayed(timeout,25000);}
  catch(RuntimeException e){releaseBinding();status.setText("无法启动更新检查，可打开源码仓库。");}
 }
 private void releaseBinding(){pending=false;main.removeCallbacks(timeout);if(bound){bound=false;try{activity.unbindService(connection);}catch(RuntimeException ignored){}}check.setEnabled(true);}
 public void cancel(){if(pending){releaseBinding();status.setText("更新检查已取消。");}}
 public void destroy(){destroyed=true;releaseBinding();}
 private void open(String url){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException e){Toast.makeText(activity,"未找到浏览器，请复制仓库地址访问",Toast.LENGTH_LONG).show();}}
}
