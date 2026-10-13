package com.e7.bmfarm;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FloatService extends Service {

    private WindowManager wm;
    private LinearLayout panel;
    private LinearLayout ball;
    private boolean expanded = false;
    private WindowManager.LayoutParams lpBall, lpPanel;
    private TextView logView;
    private TextView statusView;
    private MascotView mascotView;
    private UiSizing sizing;
    private volatile String statusText="就绪";
    private volatile int statusColor=0xFFCCFFCC;
    private android.content.SharedPreferences history;
    private PendingPersistence persistence;
    private volatile PhoneCapture phoneCapture;
    private MobileViewport viewport;
    private android.graphics.Rect gameBounds;
    private long frameAt;
    private static volatile FloatService instance;
    public static boolean active(){return instance!=null && instance.phoneCapture!=null;}
    public static void pauseForSettings(){FloatService s=instance;if(s!=null){s.requestStop();s.endCapture();}}
    private java.util.concurrent.ScheduledExecutorService captureWatch;
    private synchronized void endCapture(){
        if(captureWatch!=null){captureWatch.shutdownNow();captureWatch=null;}
        if(phoneCapture!=null){phoneCapture.close();phoneCapture=null;}
        viewport=null;gameBounds=null;stopForeground(true);
    }
    private void watchCapture(){
        final CapturePrivacyPolicy policy=new CapturePrivacyPolicy(android.os.SystemClock.elapsedRealtime());
        final PhoneCapture session=phoneCapture;
        captureWatch=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        captureWatch.scheduleWithFixedDelay(()->{
            if(destroyed||phoneCapture!=session)return;
            boolean locked=((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()||!PhoneAccessibility.ready();
            boolean[] state;
            try{state=PhoneAccessibility.captureForeground(getPackageName());}catch(Exception e){state=new boolean[]{false,false};}
            if(policy.shouldEnd(state[0],state[1],locked,android.os.SystemClock.elapsedRealtime()))ui.post(()->{
                if(phoneCapture!=session)return;requestStop();endCapture();log("已离开游戏、锁屏或授权闲置，共享已结束；回助手重新授权");
            });
        },0,400,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private void foregroundNotification(){
        android.app.NotificationManager manager=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new android.app.NotificationChannel("capture","屏幕识别服务",android.app.NotificationManager.IMPORTANCE_LOW));
        android.app.PendingIntent stop=android.app.PendingIntent.getService(this,1,new Intent(this,FloatService.class).setAction("STOP"),android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.PendingIntent open=android.app.PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification n=new android.app.Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("手机书签助手").setContentText("画面仅在本机识别；点停止结束共享和自动操作").setOngoing(true).setContentIntent(open).addAction(new android.app.Notification.Action.Builder(null,"停止",stop).build()).build();
        if(android.os.Build.VERSION.SDK_INT>=29)startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(1,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(destroyed||vision==null||intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(phoneCapture!=null)return START_NOT_STICKY;
        try{
            Intent consent=intent.getParcelableExtra("consent");
            if(consent==null)throw new IOException("缺少屏幕共享授权");
            foregroundNotification();
            phoneCapture=new PhoneCapture(this,intent.getIntExtra("result",0),consent,message->ui.post(()->{requestStop();log(message);android.widget.Toast.makeText(this,message,1).show();stopSelf();}));
            instance=this;log("免 Root 捕获已就绪，请进入秘密商店顶部，点检查画面或开始");
            watchCapture();
        }catch(Exception e){log("屏幕捕获失败："+e.getMessage());android.widget.Toast.makeText(this,e.getMessage(),1).show();stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        if(running)requestStop();viewport=null;
        endCapture();
        if(phoneCapture!=null)phoneCapture.resizeToScreen();
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();sizing=new UiSizing(m.density,m.widthPixels,m.heightPixels);
        if(expanded){wm.removeView(panel);expanded=false;}panel=null;lpPanel=null;
        if(ball!=null){lpBall.width=sizing.px(72);lpBall.height=sizing.px(72*(float)MascotTimeline.HEIGHT/MascotTimeline.WIDTH);lpBall.gravity=Gravity.TOP|Gravity.START;lpBall.x=24;lpBall.y=90;wm.updateViewLayout(ball,lpBall);}
        log("屏幕尺寸已变化，重新检查后再开始");
    }
    private volatile boolean destroyed;
    private android.app.AlertDialog recoveryDialog;
    private EditText etMystic, etCovenant, etMax;
    private volatile boolean running = false;
    private Thread worker;
    private final StringBuilder logBuf = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());


    private ShopVision vision;
    

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        foregroundNotification();
        try {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        sizing=new UiSizing(metrics.density,metrics.widthPixels,metrics.heightPixels);
        history=getSharedPreferences("run-history",MODE_PRIVATE);
        persistence=new PendingPersistence(new PendingPersistence.Store(){
            public String read(){return history.getString("pending","");}
            public boolean commit(String value){android.content.SharedPreferences.Editor editor=history.edit();if(value==null)editor.remove("pending");else editor.putString("pending",value);return editor.commit();}
            public void retain(String value){android.content.SharedPreferences.Editor editor=history.edit();if(value==null)editor.remove("pending");else editor.putString("pending",value);editor.apply();}
        });
        String last=history.getString("last-report","");
        if(!last.isEmpty()) {statusText="上次："+history.getString("last-status","已停止");log("上次记录："+last);}
        if(!history.getString("pending","").isEmpty()){statusText="有未确认交易，请点核验后再开始";statusColor=0xFFFFDD88;}
        buildBall();     // 初始只显示小球，不挡画面
        loadTemplates();
        log(vision != null ? "手机版 v1.0.2 就绪：奥芙原骨骼完整动画已内置，点人物展开" : "模板加载失败，禁止运行");
        log(AppIdentity.AUTHOR_LINE);
        setStatus(vision==null?"模板加载失败，禁止开始":statusText,vision==null?0xFFFF9999:statusColor,vision==null||!history.getString("pending","").isEmpty()?"停":"签");
        } catch(IOException|RuntimeException e){log("悬浮窗初始化失败："+e.getMessage());android.widget.Toast.makeText(this,"悬浮窗无法开启，请查看日志与权限设置",android.widget.Toast.LENGTH_LONG).show();stopSelf();}
    }

    /** 无背景人物：保留拖动与展开面板交互。 */
    private void buildBall() throws IOException {
        ball = new LinearLayout(this);
        ball.setOrientation(LinearLayout.VERTICAL);
        ball.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ball.setGravity(Gravity.CENTER);
        mascotView=new MascotView(this);
        ball.addView(mascotView,new LinearLayout.LayoutParams(-1,-1));

        lpBall = new WindowManager.LayoutParams(
                sizing.px(72), sizing.px(72*(float)MascotTimeline.HEIGHT/MascotTimeline.WIDTH),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lpBall.gravity = Gravity.TOP | Gravity.END;
        lpBall.setTitle("E7FarmBall");
        lpBall.x = sizing.px(6);
        lpBall.y = sizing.px(260);
        wm.addView(ball, lpBall);
        dragOnClick(ball, lpBall, () -> toggle());
    }

    /** 点击展开/收起面板 */
    private void toggle() {
        if (expanded) {
            panelFocusable(false);
            wm.removeView(panel);
            expanded = false;
        } else {
            if (running) { requestStop(); log("展开面板，已停止自动化；可查看日志或重新开始"); }
            if (panel == null) buildPanel();
            if (lpPanel == null) {
                lpPanel = new WindowManager.LayoutParams(
                        sizing.width,
                        sizing.height,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                lpPanel.gravity = Gravity.TOP | Gravity.START;
                lpPanel.setTitle("E7FarmPanel");
                lpPanel.x = 24; lpPanel.y = 32;
            }
            wm.addView(panel, lpPanel);
            expanded = true;
            lpBall.gravity=Gravity.TOP|Gravity.END;lpBall.x=sizing.px(6);lpBall.y=sizing.px(260);wm.updateViewLayout(ball,lpBall);
            synchronized (logBuf) { logView.setText(logBuf.toString()); }
        }
    }

    /** 拖动 + 区分点击（移动小于阈值按点击处理） */
    private void dragOnClick(View v, final WindowManager.LayoutParams lp, final Runnable onClick) {
        v.setOnTouchListener(new View.OnTouchListener() {
            int dx, dy; float sx, sy; boolean moved;
            @Override public boolean onTouch(View vv, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    sx = e.getRawX(); sy = e.getRawY();
                    if(lp!=null) {
                        int[] position=new int[2];v.getLocationOnScreen(position);
                        lp.gravity=Gravity.TOP|Gravity.START;
                        lp.x=position[0];lp.y=position[1];
                        wm.updateViewLayout(v,lp);
                    }
                    dx = lp==null?0:lp.x; dy = lp==null?0:lp.y; moved = false;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float mx = e.getRawX() - sx, my = e.getRawY() - sy;
                    if (Math.abs(mx) > 8 || Math.abs(my) > 8) moved = true;
                    if (lp != null && moved) {
                        android.util.DisplayMetrics m=getResources().getDisplayMetrics();
                        lp.x = Math.max(0,Math.min(m.widthPixels-v.getWidth(),dx+(int)mx));
                        lp.y = Math.max(0,Math.min(m.heightPixels-v.getHeight(),dy+(int)my));
                        wm.updateViewLayout(v, lp);
                    }
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved && onClick != null) onClick.run();
                }
                return true;
            }
        });
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE1C1C22);
        panel.setPadding(sizing.px(8),sizing.px(6),sizing.px(8),sizing.px(8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("手机书签助手");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(14));
        bar.addView(title);
        panel.addView(bar);
        dragOnClick(bar, null, () -> toggle());   // 点标题栏收起
        statusView=new TextView(this);
        statusView.setText(statusText);statusView.setTextColor(statusColor);
        statusView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(11));
        statusView.setMaxLines(2);
        panel.addView(statusView,new LinearLayout.LayoutParams(-1,sizing.px(38)));

        // 输入区：每行 = 常显标签 + 输入框
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        etCovenant = mkRow(inputs, "誓约目标(个，5/包)", "9999");
        etMystic = mkRow(inputs, "神秘奖牌(个，50/包)", "9999");
        etMax = mkRow(inputs, "最多刷新次数", "0");
        panel.addView(inputs);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this);
        start.setText("开始");
        start.setOnClickListener(v -> startFarm());
        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(v -> { panelFocusable(false); requestStop(); log("已请求停止，后续步骤不再点击"); });
        Button collapse = new Button(this);
        collapse.setText("收起");
        collapse.setOnClickListener(v -> { panelFocusable(false); toggle(); });
        Button exit = new Button(this);
        exit.setText("退出");
        exit.setTextColor(0xFFFF8888);
        exit.setOnClickListener(v -> { panelFocusable(false); log("退出，清理悬浮窗"); exitAll(); });
        Button verify=new Button(this);verify.setText("核验");verify.setOnClickListener(v->{panelFocusable(false);verifyPending();});
        for(Button button:new Button[]{start,stop,collapse,verify,exit}) {
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(12));
            button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);
            button.setPadding(sizing.px(3),0,sizing.px(3),0);
            btns.addView(button,new LinearLayout.LayoutParams(0,sizing.px(42),1));
        }
        panel.addView(btns);
        Button inspect=new Button(this);inspect.setText("检查画面（不刷新、不购买）");inspect.setTextSize(11);inspect.setMinHeight(0);inspect.setMinimumHeight(0);inspect.setPadding(0,0,0,0);inspect.setOnClickListener(v->{panelFocusable(false);inspectScreen();});panel.addView(inspect,new LinearLayout.LayoutParams(-1,sizing.px(30)));

        logView = new TextView(this);
        logView.setTextColor(0xFFCCFFCC);
        logView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(10));
        ScrollView sv = new ScrollView(this);
        sv.addView(logView);
        LinearLayout.LayoutParams lps = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,0,1);
        sv.setLayoutParams(lps);
        panel.addView(sv);
    }

    /** 一行输入：常显标签 + 数字输入框 */
    private EditText mkRow(LinearLayout parent, String label, String def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFB0C4B0);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(11));
        row.addView(tv);
        EditText et = new EditText(this);
        et.setText(def);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sizing.px(11));
        et.setMinHeight(0);et.setMinimumHeight(0);
        et.setPadding(sizing.px(4),sizing.px(4),sizing.px(4),sizing.px(4));
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        // 点输入框时临时允许面板获焦（键盘才弹得出），点别处/按钮后交还
        et.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                panelFocusable(true);
                v.requestFocus();
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                sizing.px(34), 1f);
        lp.setMargins(sizing.px(5),sizing.px(1),0,sizing.px(1));
        et.setLayoutParams(lp);
        row.addView(et);
        parent.addView(row);
        return et;
    }

    private void log(final String s) {
        final String text;
        synchronized (logBuf) {
            logBuf.append(s).append('\n');
            if (logBuf.length() > 8000) logBuf.delete(0, 4000);
            text = logBuf.toString();
        }
        if (logView != null && expanded) {
            ui.post(() -> logView.setText(text));
        }
    }
    private void setStatus(String text,int color,String ballText) {
        statusText=text;statusColor=color;
        ui.post(() -> {
            if(destroyed)return;
            if(statusView!=null){statusView.setText(text);statusView.setTextColor(color);}
            if(mascotView!=null)mascotView.setMode(MascotTimeline.fromMarker(ballText));
        });
    }

    private void loadTemplates() {
        try {
            Map<String, ShopVision.Template> templates = new HashMap<>();
            for (String name : getAssets().list("")) {
                if (!name.endsWith(".png")) continue;
                String key = name.substring(0, name.length() - 4);
                Bitmap bitmap;
                try (java.io.InputStream in = getAssets().open(name)) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
                if (bitmap == null) throw new IOException("模板解码失败: " + name);
                ShopVision.Frame frame = frame(bitmap);
                bitmap.recycle();
                templates.put(key, new ShopVision.Template(frame,
                        key.equals("sold") || key.equals("available") ? 650
                                : key.startsWith("currency_digit_") ? 1850 : 1350));
            }
            vision = new ShopVision(templates);
        } catch (Exception e) { vision = null; log("模板加载失败: " + e.getMessage()); }
    }

    /** 面板焦点开关：打字时临时可获焦，用完立即交还（否则游戏失焦会黑屏/卡启动） */
    private void panelFocusable(boolean f) {
        if (lpPanel == null || !expanded) return;
        if(!f){Object input=getSystemService(INPUT_METHOD_SERVICE);if(input instanceof android.view.inputmethod.InputMethodManager)((android.view.inputmethod.InputMethodManager)input).hideSoftInputFromWindow(panel.getWindowToken(),0);}
        int want = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | (f ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        if (lpPanel.flags != want) {
            lpPanel.flags = want;
            wm.updateViewLayout(panel, lpPanel);
        }
    }

    private void startFarm() {
        if(phoneCapture==null){log("屏幕共享已结束，请回助手重新授权");ui.post(()->{try{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException ignored){}});return;}
        panelFocusable(false);
        if(!history.getString("pending","").isEmpty()){log("存在未确认交易，禁止重复开始；请先点击核验");setStatus("存在未确认交易，请先核验",0xFFFFDD88,"停");return;}
        if (vision == null) { log("缺少有效模板，禁止运行"); return; }
        if (worker != null && worker.isAlive()) {
            requestStop();
            log("上一次运行正在停止，请在线程结束后再开始");
            return;
        }
        final int wantCov = parseInt(etCovenant), wantMys = parseInt(etMystic);
        final int maxRefresh = parseInt(etMax);
        if (wantCov < 0 || wantMys < 0 || maxRefresh < 0 || wantCov>9999||wantMys>9999||maxRefresh>9999 || (wantCov == 0 && wantMys == 0 && maxRefresh == 0)) {
            log("输入无效：请填0至9999；购买目标或刷新上限至少一个为正数");
            setStatus("输入无效，请检查0至9999范围",0xFFFF9999,"停");
            return;
        }
        if (expanded) toggle();
        // Keep the ball away from the product buttons and confirmation dialogs.
        lpBall.gravity = Gravity.TOP | Gravity.START;
        lpBall.x = 24; lpBall.y = 90;
        wm.updateViewLayout(ball, lpBall);
        viewport=null;gameBounds=null;
        running = true;keepAwake(true);
        setStatus("等待秘密商店，最多30秒",0xFFFFDD88,"等");
        worker = new Thread(() -> farm(wantCov, wantMys, maxRefresh), "E7FarmWorker");
        worker.start();
    }

    private void keepAwake(boolean enabled){ui.post(()->{if(destroyed||ball==null)return;if(enabled)lpBall.flags|=WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;else lpBall.flags&=~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;try{wm.updateViewLayout(ball,lpBall);}catch(RuntimeException ignored){}});}
    private void requestStop() {
        running = false;keepAwake(false);
        Thread w = worker;
        if (w != null) w.interrupt();
        endCapture();
        
        setStatus("已请求停止；已确认的游戏交易仍需核验",0xFFFFDD88,"停");
    }

    private boolean cancelled() {
        return !running || Thread.currentThread() != worker || Thread.currentThread().isInterrupted();
    }

    private void exitAll() {
        requestStop();
        if(recoveryDialog!=null){recoveryDialog.dismiss();recoveryDialog=null;}
        try { if (panel != null && expanded) wm.removeView(panel); } catch (Exception e) { }
        try { if (ball != null) wm.removeView(ball); } catch (Exception e) { }
        expanded = false;
        stopSelf();
    }

    private int parseInt(EditText et) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return -1; }
    }

    private void requireGameForeground() throws Exception {
        if(cancelled())throw new InterruptedException("stopped");
        if(!android.provider.Settings.canDrawOverlays(this))throw new IOException("悬浮窗权限已撤销");
        android.graphics.Rect now=PhoneAccessibility.gameBounds();
        if(gameBounds!=null&&!gameBounds.equals(now))throw new IOException("游戏窗口已改变，停止后重新检查画面");
    }
    private void clearPending() throws IOException {
        persistence.clear();
    }
    private void clearAfterManualReview() {
        if(worker!=null&&worker.isAlive()){log("请等待当前线程结束后清除记录");return;}
        running=true;setStatus("正在保存人工核验结果",0xFFFFDD88,"等");
        worker=new Thread(()->{try{clearPending();setStatus("记录已人工核对清除，可重新开始",0xFFCCFFCC,"签");log("待核验记录已人工核对清除");}catch(Exception e){setStatus("保存失败，记录仍保留",0xFFFFDD88,"停");log("核验保存失败："+e.getMessage());}finally{running=false;}},"E7ClearJournalWorker");worker.start();
    }
    private void showManualClear(String message) {
        ui.post(()-> {
            if(destroyed)return;
            try {
                android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                        .setTitle("核对未确认交易").setMessage(message+"。只有在你已人工确认实际结果后，才清除记录并重新开始。此操作不会购买或刷新。")
                        .setNegativeButton("保留记录",(d,w)->{})
                        .setPositiveButton("已核对，清除记录",(d,w)->clearAfterManualReview()).create();
                recoveryDialog=dialog;dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.show();
            }catch(RuntimeException ex){log("无法显示核验提示，请检查悬浮窗权限："+ex.getMessage());}
        });
    }
    private void verifyPending() {
        String saved=history.getString("pending","");
        if(saved.isEmpty()){log("没有待核验交易");return;}
        if(worker!=null&&worker.isAlive()){requestStop();log("请等待当前线程结束后核验");return;}
        if(expanded)toggle();
        running=true;viewport=null;gameBounds=null;
        worker=new Thread(()-> {
            try {
                PendingOperation operation=PendingOperation.decode(saved);
                requireGameForeground();ShopVision.Frame f=screencap();
                if(operation.completed(vision,f)) {
                    String recovered=operation.product==null?"恢复核验：刷新1次，天空石3":"恢复核验："+operation.product.label+operation.product.quantity+"个，金币"+operation.product.price;
                    String oldReport=history.getString("last-report","");String oldStatus=history.getString("last-status","");
                    if(!history.edit().remove("pending").putString("last-report",oldReport+"\n"+recovered).putString("last-status","已恢复核验，未重试消费").commit()) {
                        history.edit().putString("pending",saved).putString("last-report",oldReport).putString("last-status",oldStatus).apply();
                        throw new IOException("无法保存恢复记录，原记录已保留");
                    }
                    String message="上次"+operation.label()+"已核验成功；本次尚未开始";
                    log(recovered);
                    log(message);setStatus(message,0xFFCCFFCC,"签");
                } else {
                    int balance=operation.balance(vision,f);
                    String message=vision.state(f)!=ShopVision.State.SHOP||balance<0?"请先让游戏返回商店并核对余额，再点核验":"当前余额"+balance+"，原余额"+operation.before+"，成功后应为"+operation.after;
                    log(message);setStatus(message,0xFFFFDD88,"停");
                    if(vision.state(f)==ShopVision.State.SHOP&&balance>=0)showManualClear(message);
                }
            } catch(IllegalArgumentException e){log("待核验记录损坏，禁止自动重试");setStatus("记录损坏，请人工核对后清除",0xFFFFDD88,"停");showManualClear("待核验记录无法解析，请先人工核对游戏余额与售罄状态");
            } catch(Exception e){log("核验未完成："+e.getMessage());setStatus("核验未完成，请查看日志",0xFFFFDD88,"停");}
            finally {running=false;}
        },"E7VerificationWorker");worker.start();
    }

    private static ShopVision.Frame frame(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return new ShopVision.Frame(w, h, pixels);
    }

    private ShopVision.Frame screencap() throws Exception {
        if(phoneCapture==null)throw new IOException("屏幕共享未就绪，请返回助手授权");
        if(cancelled())throw new InterruptedException("stopped");
        android.graphics.Rect bounds=PhoneAccessibility.gameBounds();
        ShopVision.Frame raw=phoneCapture.capture();
        {
            if(cancelled())throw new InterruptedException("stopped");
            if(!bounds.equals(PhoneAccessibility.gameBounds()))throw new IOException("截屏期间游戏窗口发生变化");
                        if(viewport==null){
                MobileViewport candidate=MobileViewport.detect(raw,bounds.left,bounds.top,bounds.right,bounds.bottom);
                ShopVision.Frame normalized=candidate.normalize(raw);
                if(vision.state(normalized)!=ShopVision.State.SHOP||vision.gold(normalized)<0||vision.skystones(normalized)<0||vision.refreshButton(normalized)==null)
                    throw new IOException("尚未匹配秘密商店、余额和刷新入口，请保持横屏全屏并停在商店顶部");
                viewport=candidate;gameBounds=new android.graphics.Rect(bounds);log("画面适配："+viewport.description());
            }
            if(!bounds.equals(gameBounds))throw new IOException("窗口尺寸已变化，请重新开始");
            ShopVision.Frame result=viewport.normalize(raw);frameAt=android.os.SystemClock.elapsedRealtime();return result;
        }
    }
    private void input(int x1,int y1,int x2,int y2,int ms)throws Exception{
        requireGameForeground();
        if(viewport==null||android.os.SystemClock.elapsedRealtime()-frameAt>5000)throw new IOException("操作依据的截图已过期，已停止");
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);
        if(m.widthPixels!=viewport.screenWidth||m.heightPixels!=viewport.screenHeight)throw new IOException("屏幕已旋转，取消手势");
        int[] a=viewport.point(x1,y1),b=viewport.point(x2,y2);
        Thread issuer=Thread.currentThread();
        PhoneAccessibility.gesture(a[0],a[1],b[0],b[1],ms,gameBounds,()->!InputGuard.permitted(running,destroyed,worker,issuer));
    }
    private void inspectScreen(){
        if(worker!=null&&worker.isAlive()){log("请先停止当前任务");return;}
        if(expanded)toggle();running=true;viewport=null;gameBounds=null;
        worker=new Thread(()->{try{
            ShopVision.Frame f=screencap();
            String text="画面检查通过：金币与天空石可识别；誓约"+(vision.findCard(f,ShopVision.Product.COVENANT)!=null?"有":"当前画面无")+"，神秘"+(vision.findCard(f,ShopVision.Product.MYSTIC)!=null?"有":"当前画面无");
            log(text);setStatus("画面检查通过，未刷新或购买",0xFFCCFFCC,"签");
        }catch(Exception e){log("画面检查："+e.getMessage());setStatus("画面检查未通过，请查看日志",0xFFFFDD88,"停");}finally{running=false;endCapture();log("检查已结束并释放共享；开始任务前需回助手重新授权");}},"PhoneScreenCheck");worker.start();
    }

    private void farm(int wantCov, int wantMys, int maxRefresh) {
        FarmEngine engine = new FarmEngine(vision, new FarmEngine.Port() {
            @Override public ShopVision.Frame capture() throws Exception { return screencap(); }
            @Override public void tap(int x, int y) throws Exception {
                requireGameForeground();
                input(x,y,x,y,70);
            }
            @Override public void swipe(int x1,int y1,int x2,int y2,int ms) throws Exception {
                requireGameForeground();
                input(x1,y1,x2,y2,ms);
            }
            @Override public void pause(long ms) throws Exception { Thread.sleep(ms); }
            @Override public boolean cancelled() { return FloatService.this.cancelled(); }
            @Override public void log(String s) { FloatService.this.log(s); }
            @Override public void status(String s) {boolean wait=s.startsWith("等待")||s.contains("确认");setStatus(s,wait?0xFFFFDD88:0xFFCCFFCC,wait?"等":"刷");}
            @Override public void pending(PendingOperation operation) throws Exception {persistence.save(operation.encode());}
            @Override public void verified() throws Exception {clearPending();}
        }, wantCov, wantMys, maxRefresh);
        log("开始：誓约目标" + wantCov + "个，神秘奖牌目标" + wantMys + "个，刷新上限" + maxRefresh);
        log(wantCov==0&&wantMys==0?"只刷新模式：两类购买目标均为0，不购买任何商品":"商品整包购买，达到目标后停止；实际数量可能向上取整到每包数量");
        try {
            engine.run();
        } catch (InterruptedException e) {
            engine.result().stopReason="用户停止";
            log("已停止");
        } catch (Exception e) {
            engine.result().stopReason=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            log("运行异常，已停止: " + e.getMessage());
        } finally {
            FarmEngine.Result r = engine.result();
            if (r.pendingPurchase != null) log(r.pendingPurchase.label
                    + "确认流程未完成，结果待核验；请先核对售罄状态与金币，避免重复购买");
            if (r.pendingRefresh) log("刷新确认流程未完成，结果待核验；请先核对天空石余额，不能把下方核验数当作实际总消耗");
            String summary="誓约"+r.covenant+"个，神秘奖牌"+r.mystic+"个；金币合计"+r.gold+"；刷新"+r.refreshes+"次，天空石合计"+r.skystones;
            log("核验记录："+summary);
            String finalStatus=(r.pendingPurchase!=null||r.pendingRefresh)?"结果待核验，请核对游戏余额和售罄":r.completed?"完成，已停止":r.stopReason.isEmpty()?"已停止":r.stopReason;
            history.edit().putString("last-report",summary).putString("last-status",finalStatus).apply();
            setStatus(finalStatus,(r.pendingPurchase!=null||r.pendingRefresh)?0xFFFFDD88:r.completed?0xFFCCFFCC:r.stopReason.equals("用户停止")?0xFFFFDD88:0xFFFF9999,r.completed?"完":"停");
            if(!r.completed)ui.post(()->android.widget.Toast.makeText(this,finalStatus,android.widget.Toast.LENGTH_LONG).show());
            if (worker == Thread.currentThread()) {running = false;keepAwake(false);}
            endCapture();log("本次共享已结束；再次运行请回助手重新授权");
        }
    }

    @Override public void onDestroy() {
        destroyed=true;
        if(instance==this)instance=null;
        endCapture();
        if(phoneCapture!=null){phoneCapture.close();phoneCapture=null;}
        stopForeground(true);
        requestStop();
        if(recoveryDialog!=null){try{recoveryDialog.dismiss();}catch(Exception e){}recoveryDialog=null;}
        if (panel != null && expanded) try { wm.removeView(panel); } catch (Exception e) { }
        if (ball != null) try { wm.removeView(ball); } catch (Exception e) { }
        if (mascotView != null) mascotView.release();
        expanded = false;
        super.onDestroy();
    }
}








