package com.e7.bmfarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

public class MainActivity extends Activity {
    private static final int RC_OVERLAY = 1001, RC_CAPTURE = 1002;
    private TextView permissions;
    private boolean notificationAsked;
    private SharedPreferences noticePreferences;
    private Button go;
    private UpdateUi updates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FloatService.pauseForSettings();
        noticePreferences = getSharedPreferences(UsageNotice.PREFERENCES, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("手机书签助手");
        title.setTextSize(18);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("免 Root · 国服第七史诗 · Android 8.0 及以上\n\n"
            + "1. 开启悬浮窗和无障碍服务，然后授权屏幕共享（整个屏幕）\n"
            + "2. 回到游戏，横屏全屏打开秘密商店，停在列表顶部\n"
            + "3. 点人物展开面板，先检查画面；设置刷新次数后开始\n"
            + "默认书签和奖牌目标9999，刷新0；刷新0仍会购买当前商品\n"
            + "两类目标均为0时只刷新。点人物展开会停止；通知栏也可停止\n\n"
            + "画面仅在本机处理，不上传、不录音。切出游戏、锁屏或撤销权限会停止。无障碍只用于核对前台窗口及执行商店点击/滑动。\n"
            + "若系统提示受限设置，请在本应用的系统信息页按系统提示允许；不同品牌入口不同。运行时请保持游戏前台和屏幕常亮。");
        hint.setTextSize(13);
        hint.setPadding(0, pad, 0, pad);
        root.addView(hint);

        go = new Button(this);
        go.setText("授权屏幕共享并开启悬浮窗");
        go.setEnabled(hasAcknowledgedNotice());
        go.setOnClickListener(v -> startFloat());
        permissions=new TextView(this);root.addView(permissions);
        Button overlay=new Button(this);overlay.setText("① 悬浮窗权限");overlay.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));root.addView(overlay);
        Button access=new Button(this);access.setText("② 无障碍服务");access.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(access);
        Button info=new Button(this);info.setText("应用系统信息 / 权限设置");info.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));root.addView(info);
        root.addView(go);
        updates=new UpdateUi(this,root);

        TextView noticeTitle = new TextView(this);
        noticeTitle.setText(UsageNotice.TITLE);
        noticeTitle.setTextSize(15);
        noticeTitle.setPadding(0, pad, 0, pad / 2);
        root.addView(noticeTitle);

        TextView notice = new TextView(this);
        notice.setText(UsageNotice.BODY);
        notice.setTextSize(13);
        root.addView(notice);

        TextView author = new TextView(this);
        author.setText(AppIdentity.AUTHOR_LINE);
        author.setTextSize(14);
        author.setPadding(0, pad, 0, 0);
        root.addView(author);

        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        if (!hasAcknowledgedNotice()) showUsageNotice();
        else updates.automatic();
    }

    private boolean hasAcknowledgedNotice() {
        return noticePreferences.getBoolean(UsageNotice.ACKNOWLEDGED, false);
    }

    private void showUsageNotice() {
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad / 2);
        TextView body = new TextView(this);
        body.setText(UsageNotice.BODY);
        body.setTextSize(14);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        TextView author = new TextView(this);
        author.setText(AppIdentity.AUTHOR_LINE);
        author.setTextSize(14);
        author.setPadding(0, pad / 2, 0, 0);
        content.addView(author);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        int bodyHeight = Math.max(Math.round(48 * density),
                Math.min(Math.round(screenHeight * 0.4f), screenHeight - Math.round(260 * density)));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, bodyHeight));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(UsageNotice.TITLE)
                .setView(content)
                .setPositiveButton(UsageNotice.ACKNOWLEDGE, null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!noticePreferences.edit().putBoolean(UsageNotice.ACKNOWLEDGED, true).commit()) {
                        Toast.makeText(this, "阅读记录保存失败，请重试", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    go.setEnabled(true);
                    dialog.dismiss();
                    updates.automatic();
                }));
        dialog.show();
    }

    private void openSettings(Intent intent){try{startActivity(intent);}catch(RuntimeException e){Toast.makeText(this,"请从手机设置进入本应用权限页",1).show();}}
    @Override protected void onResume(){super.onResume();if(permissions!=null)permissions.setText("悬浮窗："+(Settings.canDrawOverlays(this)?"已允许":"未允许")+"    无障碍："+(PhoneAccessibility.ready()?"已开启":"未开启"));}
    private void startFloat() {
        if(updates!=null)updates.cancel();
        if(!hasAcknowledgedNotice())return;
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"请先允许悬浮窗权限",1).show();return;}
        if(!PhoneAccessibility.ready()){Toast.makeText(this,"请先开启手机书签助手的无障碍服务",1).show();return;}
        if(FloatService.active()){finish();return;}
        if(Build.VERSION.SDK_INT>=33&&!notificationAsked&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){notificationAsked=true;requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1003);return;}
        android.media.projection.MediaProjectionManager manager=(android.media.projection.MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent capture=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
        try{startActivityForResult(capture,RC_CAPTURE);}catch(RuntimeException e){Toast.makeText(this,"系统无法开启屏幕共享："+e.getMessage(),1).show();}
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==1003){notificationAsked=true;startFloat();}}
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==RC_CAPTURE){
            if(res!=RESULT_OK||data==null){Toast.makeText(this,"未授权屏幕共享，没有启动",0).show();return;}
            if(!Settings.canDrawOverlays(this)||!PhoneAccessibility.ready()){Toast.makeText(this,"权限状态已变化，请重新检查",1).show();return;}
            try{startForegroundService(new Intent(this,FloatService.class).putExtra("result",res).putExtra("consent",data));finish();}
            catch(RuntimeException e){Toast.makeText(this,"启动失败："+e.getMessage(),1).show();}
        }
    }
    @Override protected void onPause(){if(updates!=null)updates.cancel();super.onPause();}
    @Override protected void onDestroy(){if(updates!=null)updates.destroy();super.onDestroy();}
}
