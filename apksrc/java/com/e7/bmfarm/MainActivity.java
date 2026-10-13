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
    private static final int RC_OVERLAY = 1001;
    private SharedPreferences noticePreferences;
    private Button go;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        noticePreferences = getSharedPreferences(UsageNotice.PREFERENCES, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("第七史诗 · 神秘商店刷书签助手");
        title.setTextSize(18);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("1. 先进入秘密商店，首次开始请停在列表顶部\n"
                + "2. 点下面按钮开启悬浮窗\n"
                + "3. 默认两类目标9999、刷新0；修改刷新上限后点开始\n"
                + "4. 运行时自动收起面板；展开面板会停止，可查看日志\n"
                + "每包誓约5个/神秘50个；刷新0不换批，仍按目标购买当前商品\n"
                + "两类目标都填0表示只刷新；刷新后直接检查顶部再查看底部\n"
                + "本机校准：1280x720横屏，需要模拟器开启 root 权限");
        hint.setTextSize(13);
        hint.setPadding(0, pad, 0, pad);
        root.addView(hint);

        go = new Button(this);
        go.setText("开启悬浮窗");
        go.setEnabled(hasAcknowledgedNotice());
        go.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(it, RC_OVERLAY);
            } else {
                startFloat();
            }
        });
        root.addView(go);

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
                }));
        dialog.show();
    }

    private void startFloat() {
        if (!hasAcknowledgedNotice()) return;
        startService(new Intent(this, FloatService.class));
        Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == RC_OVERLAY && Settings.canDrawOverlays(this)) {
            startFloat();
        }
    }
}
