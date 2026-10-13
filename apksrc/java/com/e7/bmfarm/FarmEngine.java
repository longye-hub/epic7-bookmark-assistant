package com.e7.bmfarm;

/** Purchasing/refresher protocol. Port can replay saved frames with recorded input. */
public final class FarmEngine {
    public interface Port {
        ShopVision.Frame capture() throws Exception;
        void tap(int x,int y) throws Exception;
        void swipe(int x1,int y1,int x2,int y2,int ms) throws Exception;
        void pause(long ms) throws Exception;
        boolean cancelled();
        void log(String message);
        default long nowMillis() {return System.nanoTime()/1000000;}
        default void status(String message) {}
        default void pending(PendingOperation operation) throws Exception {}
        default void verified() throws Exception {}
    }
    public static final class Result {
        public int covenant,mystic,refreshes,skystones;
        public long gold;
        public ShopVision.Product pendingPurchase;
        public boolean pendingRefresh;
        public String stopReason="";
        public boolean completed;
    }
    private final ShopVision vision;
    private final Port port;
    private final int targetCov,targetMys,maxRefresh;
    private final Result result=new Result();
    private String scanProblem="";
    public FarmEngine(ShopVision vision,Port port,int targetCov,int targetMys,int maxRefresh) {
        if(targetCov<0||targetMys<0||maxRefresh<0||targetCov>9999||targetMys>9999||maxRefresh>9999||(targetCov==0&&targetMys==0&&maxRefresh==0)) throw new IllegalArgumentException("目标或刷新上限无效");
        this.vision=vision; this.port=port; this.targetCov=targetCov; this.targetMys=targetMys; this.maxRefresh=maxRefresh;
    }
    public Result result() {return result;}
    private boolean alive() {return !port.cancelled();}
    private ShopVision.Frame capture() throws Exception {return alive()?port.capture():null;}
    private boolean tap(int x,int y) throws Exception {if(!alive()) return false; port.tap(x,y); return alive();}
    private ShopVision.Frame waitFrame(long ms) throws Exception {if(!alive()) return null;port.pause(ms);return capture();}
    private boolean fail(String message) {result.stopReason=message;port.log(message+"；停止，未确认成功不计数");return false;}
    private ShopVision.Frame shopFrame() throws Exception {
        for(int i=0;i<12&&alive();i++) {
            ShopVision.Frame f=capture();
            if(f==null) return null;
            ShopVision.State s=vision.state(f);
            if(s==ShopVision.State.SHOP) return f;
            if(s==ShopVision.State.BUY_CONFIRM||s==ShopVision.State.REFRESH_CONFIRM) return null;
            port.status("等待商店加载，不点击");
            port.pause(400);
        }
        return null;
    }

    public boolean purchase(ShopVision.Hit original) throws Exception {
        if(original==null||!alive()) return false;
        ShopVision.Frame before=capture();
        ShopVision.Hit live=before==null?null:vision.findCard(before,original.product);
        if(live==null||Math.abs(live.y-original.y)>4) return fail("购买前商品或界面已变化");
        int goldBefore=vision.gold(before);
        if(goldBefore<0) return fail("金币余额无法识别，请确认画面清晰且没有遮挡");
        if(goldBefore<live.price) return fail("金币不足：现有"+goldBefore+"，需要"+live.price);
        port.status("购买"+live.product.label+"，核验商品和余额");
        port.log("发现 "+live.product.label+"，金币 "+live.price+"，点购买按钮");
        if(!tap(1200,live.buttonY)) return false;
        boolean ready=false;
        for(int i=0;i<8 && alive();i++) {
            ShopVision.Frame f=waitFrame(350);
            if(f==null) return false;
            ShopVision.State state=vision.state(f);
            if(state==ShopVision.State.BUY_CONFIRM) {
                if(vision.dialogProduct(f)!=live.product) return fail("购买弹窗商品或价格不符");
                ready=true;break;
            }
            if(state==ShopVision.State.REFRESH_CONFIRM) return fail("出现了刷新弹窗");
        }
        if(!ready) return fail("购买弹窗未能确认");
        // This is the purchase button; refresh uses a different location.
        result.pendingPurchase=live.product;
        port.pending(new PendingOperation(live.product,live.y,goldBefore,goldBefore-live.price));
        if(!tap(820,515)) return false;
        long deadline=port.nowMillis()+30000;
        port.status("购买确认已发出，等待售罄和扣款（最多30秒）");
        for(int i=0;i<80 && alive() && port.nowMillis()<deadline;i++) {
            ShopVision.Frame f=waitFrame(400);
            if(f!=null && vision.sold(f,live) && vision.gold(f)==goldBefore-live.price) {
                port.verified();
                if(live.product==ShopVision.Product.COVENANT) result.covenant+=live.product.quantity;
                else result.mystic+=live.product.quantity;
                result.gold+=live.price;
                result.pendingPurchase=null;
                port.log("购买已核验售罄："+live.product.label+" +"+live.product.quantity);
                return true;
            }
        }
        return fail("确认后未检测到该行售罄");
    }
    public boolean refresh() throws Exception {
        if(result.refreshes>=maxRefresh || !alive()) return false;
        ShopVision.Frame before=capture();
        int[] button=before==null?null:vision.refreshButton(before);
        if(button==null) return fail("找不到商店刷新按钮");
        int skyBefore=vision.skystones(before);
        if(skyBefore<0) return fail("天空石余额无法识别，请确认画面清晰且没有遮挡");
        if(skyBefore<3) return fail("天空石不足：现有"+skyBefore+"，刷新需要3");
        if(!tap(button[0],button[1])) return false;
        boolean ready=false;
        for(int i=0;i<8 && alive();i++) {
            ShopVision.Frame f=waitFrame(350);
            if(f==null) return false;
            ShopVision.State state=vision.state(f);
            if(state==ShopVision.State.REFRESH_CONFIRM) {ready=true;break;}
            if(state==ShopVision.State.BUY_CONFIRM) return fail("刷新时出现购买弹窗");
        }
        if(!ready) return fail("未确认天空石刷新弹窗");
        result.pendingRefresh=true;
        port.pending(new PendingOperation(null,-1,skyBefore,skyBefore-3));
        if(!tap(748,460)) return false;
        long deadline=port.nowMillis()+30000;
        port.status("等待刷新换批和扣费（最多30秒），不重复确认");
        for(int i=0;i<80 && alive() && port.nowMillis()<deadline;i++) {
            ShopVision.Frame f=waitFrame(400);
            if(f!=null && vision.state(f)==ShopVision.State.SHOP && vision.skystones(f)==skyBefore-3) {
                port.verified();
                result.refreshes++;result.skystones+=3;
                result.pendingRefresh=false;
                port.log("刷新已返回商店："+result.refreshes+" / "+maxRefresh);
                return true;
            }
        }
        return fail("刷新确认后30秒内未核验成功，请核对游戏与天空石");
    }
    private ShopVision.Hit needed(ShopVision.Frame f) {
        scanProblem="";
        for(ShopVision.Product p:ShopVision.Product.values()) {
            if(p==ShopVision.Product.COVENANT?result.covenant>=targetCov:result.mystic>=targetMys)continue;
            ShopVision.Scan scan=vision.inspectCard(f,p);
            if(scan.hit!=null)return scan.hit;
            if(!scan.uncertainty.isEmpty()){scanProblem=scan.uncertainty;return null;}
        }
        return null;
    }
    private ShopVision.Hit checkedNeeded(ShopVision.Frame f)throws Exception {
        for(int i=0;i<8&&alive();i++) {
            if(f!=null&&vision.state(f)==ShopVision.State.SHOP) {
                ShopVision.Hit h=needed(f);
                if(h!=null||scanProblem.isEmpty())return h;
            }
            port.status("珍稀商品待确认，暂停刷新");
            if(i<7)f=waitFrame(350);
        }
        if(scanProblem.isEmpty())scanProblem="目标检查时画面未稳定";
        fail(scanProblem+"；未购买、未刷新该批商品");return null;
    }
    public void run() throws Exception {
        port.log("等待秘密商店；启动和未知画面不点击");
        boolean resetToTop=false;
        port.status("等待秘密商店（最多30秒），不点击");
        boolean ready=false;
        long startDeadline=port.nowMillis()+30000;
        for(int i=0;i<12&&alive()&&port.nowMillis()<startDeadline;i++) {
            ShopVision.Frame f=capture();
            if(f!=null && vision.state(f)==ShopVision.State.SHOP) {ready=true;break;}
            port.pause(2500);
        }
        if(!alive()) return;
        if(!ready) {fail("等待秘密商店超时，请把游戏保持在秘密商店顶部后重新开始");return;}
        while(alive()) {
            port.status("运行中：刷新"+result.refreshes+"/"+maxRefresh+"，誓约"+result.covenant+"，神秘"+result.mystic);
            if((targetCov>0||targetMys>0) && result.covenant>=targetCov && result.mystic>=targetMys) {result.completed=true;port.log("目标达成");return;}
            ShopVision.Frame f=shopFrame();
            if(f==null || vision.state(f)!=ShopVision.State.SHOP) {fail("扫描前不在商店");return;}
            // Start and refresh scan the current top directly. Purchases may leave the list scrolled.
            if(resetToTop) {
                if(!alive()) return;
                port.log("购买后复查顶部，向下拖动列表");
                port.swipe(985,200,985,620,500);
                f=waitFrame(900);
                if(f==null||vision.state(f)!=ShopVision.State.SHOP) {fail("列表复位后界面异常");return;}
            }
            ShopVision.Hit h=checkedNeeded(f);
            if(!scanProblem.isEmpty())return;
            if(h!=null) {if(!purchase(h)) return;resetToTop=true;continue;}
            if(!alive()) return;
            port.log("顶部检查完成，向上拖动查看底部商品");
            port.swipe(985,560,985,180,500);
            f=waitFrame(900);
            if(f==null||vision.state(f)!=ShopVision.State.SHOP) {fail("列表滚动后界面异常");return;}
            h=checkedNeeded(f);
            if(!scanProblem.isEmpty())return;
            if(h!=null) {if(!purchase(h)) return;resetToTop=true;continue;}
            if(result.refreshes>=maxRefresh) {result.completed=true;port.log("达到刷新上限，停止");return;}
            port.log("顶部和底部均无所需书签，准备刷新");
            if(!refresh()) return;
            resetToTop=false;
            port.log("刷新已复位顶部，直接扫描，不回顶滑动");
            port.pause(900);
        }
    }
}
