package com.e7.bmfarm;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;

/** Launch-page public notices. Only check signals cross IPC; reading status stays local. */
public final class UpdateUi {
 private final Activity activity;private final String pkg;private final int installed;private final String name;
 private final SharedPreferences prefs;private final TextView status;private final Button check,readNotice;
 private final Handler main=new Handler(Looper.getMainLooper());
 private boolean pending,bound,destroyed,canShow=true,requestedNotice;private int generation;
 private AnnouncementInfo cached;private AlertDialog dialog;
 private final Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),this::handleResult));
 private boolean handleResult(Message msg){
  if(msg.what!=UpdateService.RESULT||msg.arg1!=generation||!pending||destroyed)return true;
  String metadata=msg.getData().getString("metadata"),noticeText=msg.getData().getString("announcement");releaseBinding();
  if(!canShow||activity.isFinishing())return true;
  NoticeResult result=NoticeResult.parse(metadata,noticeText,pkg);
  String versionStatus=result.update==null?"版本信息读取失败，可重试。":result.update.newerThan(installed)?"发现新版 "+result.update.versionName:"当前已是该更新渠道的最新版本（"+name+"）。";
  if(result.announcement!=null&&(cached==null||result.announcement.sequence>=cached.sequence)){
   cached=result.announcement;
   prefs.edit().putString("public-announcement",noticeText).apply();
  }
  readNotice.setEnabled(cached!=null&&cached.active);
  status.setText(versionStatus+"\n"+(result.announcement==null?"公告读取失败；本机已有公告仍可查看。":result.announcement.active?"公告已检查。":"目前没有有效公告。"));
  Runnable offer=()->{if(canShow&&!destroyed&&!activity.isFinishing()&&result.update!=null&&result.update.newerThan(installed))showUpdate(result.update);};
  if(result.announcement!=null&&result.announcement.active&&(requestedNotice||result.announcement.unreadAfter(prefs.getLong("notice-read-sequence",0)))){
   showAnnouncement(result.announcement,offer);
  }else{if(requestedNotice&&result.announcement!=null&&!result.announcement.active)Toast.makeText(activity,"目前没有有效公告",Toast.LENGTH_LONG).show();offer.run();}
  requestedNotice=false;
  return true;
 }
 private void showUpdate(UpdateInfo info){
  dialog=new AlertDialog.Builder(activity).setTitle("发现助手更新")
   .setMessage("当前："+name+"\n新版："+info.versionName+("preview".equals(info.channel)?"（测试版）":"")+"\n\n"+info.notes+"\n\n更新前请停止任务并核对待确认交易。下载后由系统确认覆盖安装，不要先卸载。")
   .setPositiveButton("前往下载",(d,w)->open(info.downloadUrl)).setNeutralButton("版本说明",(d,w)->open(info.releaseUrl)).setNegativeButton("稍后",null).create();
  dialog.setOnDismissListener(d->dialog=null);dialog.show();
 }
 private void showAnnouncement(AnnouncementInfo info,Runnable next){
  TextView body=new TextView(activity);body.setText(info.body);body.setTextSize(15);
  int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad/2,pad,pad/2);
  ScrollView scroll=new ScrollView(activity);scroll.addView(body);
  LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
  int height=Math.max(Math.round(48*activity.getResources().getDisplayMetrics().density),Math.min(activity.getResources().getDisplayMetrics().heightPixels/3,Math.round(240*activity.getResources().getDisplayMetrics().density)));
  content.addView(scroll,new LinearLayout.LayoutParams(-1,height));
  AlertDialog shown=new AlertDialog.Builder(activity).setTitle(info.title).setView(content).setPositiveButton("知道了",null).setNegativeButton("稍后",null).create();
  dialog=shown;
  shown.setOnShowListener(d->shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   long acknowledged=Math.max(prefs.getLong("notice-read-sequence",0),info.sequence);
   if(!prefs.edit().putLong("notice-read-sequence",acknowledged).commit()){Toast.makeText(activity,"阅读状态保存失败，请重试",Toast.LENGTH_LONG).show();return;}
   shown.dismiss();
  }));
  shown.setOnDismissListener(d->{if(dialog==shown)dialog=null;if(canShow&&!destroyed&&!activity.isFinishing())next.run();});shown.show();
 }
 private final Runnable timeout=this::onTimeout;
 private void onTimeout(){if(pending){releaseBinding();status.setText("更新和公告检查超时，请稍后重试。");}}
 private final ServiceConnection connection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName name,IBinder binder){
   if(!pending)return;
   Message request=Message.obtain(null,UpdateService.CHECK);request.replyTo=reply;request.arg1=generation;
   // No Bundle, local version, acknowledgement, screen, account, identifier or configurable URL.
   try{new Messenger(binder).send(request);}catch(RemoteException e){releaseBinding();status.setText("通知服务未就绪，请重试。");}
  }
  @Override public void onServiceDisconnected(ComponentName name){if(pending){releaseBinding();status.setText("通知检查已中断，请重试。");}}
 };
 public UpdateUi(Activity a,LinearLayout parent){
  activity=a;pkg=a.getPackageName();int code=0;String version="未知";
  try{android.content.pm.PackageInfo info=a.getPackageManager().getPackageInfo(pkg,0);code=info.versionCode;version=info.versionName;}catch(Exception ignored){}
  installed=code;name=version;prefs=a.getSharedPreferences("update-controls",Context.MODE_PRIVATE);
  try{cached=AnnouncementInfo.parse(prefs.getString("public-announcement",null));}catch(java.io.IOException ignored){}
  TextView current=new TextView(a);current.setText("当前版本："+name);current.setTextSize(14);parent.addView(current);
  CheckBox auto=new CheckBox(a);auto.setText("启动时检查更新和公告（每天最多一次）");auto.setChecked(prefs.getBoolean("auto",true));auto.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("auto",on).apply();if(!on)cancel();});parent.addView(auto);
  TextView privacy=new TextView(a);privacy.setText("仅读取公开更新和公告；不上传画面、余额、设备标识或已读状态。");privacy.setTextSize(12);parent.addView(privacy);
  check=new Button(a);check.setText("手动检查更新和公告");check.setOnClickListener(v->check(false));parent.addView(check);
  status=new TextView(a);status.setText("可手动检查更新和公告。");status.setTextSize(13);parent.addView(status);
  readNotice=new Button(a);readNotice.setText("查看最近公告（本机缓存）");readNotice.setEnabled(cached!=null&&cached.active);
  readNotice.setOnClickListener(v->{if(cached!=null&&cached.active){canShow=false;dismissDialog();canShow=true;showAnnouncement(cached,()->{});}else check(true);});parent.addView(readNotice);
  TextView repo=new TextView(a);repo.setText("源码仓库：\n"+UpdateInfo.REPOSITORY);repo.setTextSize(12);parent.addView(repo);
  Button source=new Button(a);source.setText("查看源码仓库");source.setOnClickListener(v->open(UpdateInfo.REPOSITORY));parent.addView(source);
 }
 public void automatic(){
  if(!prefs.getBoolean("auto",true))return;
  long last=prefs.getLong("last-attempt",0),now=System.currentTimeMillis();
  if(prefs.getInt("checked-code",-1)!=installed||now<last||now-last>=86400000L)check(false);
 }
 private void check(boolean showNotice){
  if(pending||destroyed||activity.isFinishing())return;
  canShow=false;dismissDialog();canShow=true;requestedNotice=showNotice;
  generation++;pending=true;check.setEnabled(false);status.setText("正在读取公开更新和公告…");
  prefs.edit().putLong("last-attempt",System.currentTimeMillis()).putInt("checked-code",installed).apply();
  try{bound=activity.bindService(new Intent(activity,UpdateService.class),connection,Context.BIND_AUTO_CREATE);if(!bound)throw new IllegalStateException();main.postDelayed(timeout,35000);}
  catch(RuntimeException e){releaseBinding();status.setText("无法启动通知检查，可打开源码仓库。");}
 }
 private void releaseBinding(){pending=false;main.removeCallbacks(timeout);if(bound){bound=false;try{activity.unbindService(connection);}catch(RuntimeException ignored){}}check.setEnabled(true);}
 private void dismissDialog(){AlertDialog shown=dialog;dialog=null;if(shown!=null)shown.dismiss();}
 public void cancel(){canShow=false;requestedNotice=false;dismissDialog();if(pending){releaseBinding();status.setText("通知检查已取消。");}}
 public void destroy(){destroyed=true;cancel();releaseBinding();}
 private void open(String url){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException e){Toast.makeText(activity,"未找到浏览器，请复制仓库地址访问",Toast.LENGTH_LONG).show();}}
}
