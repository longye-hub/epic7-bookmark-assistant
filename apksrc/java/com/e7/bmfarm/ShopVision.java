package com.e7.bmfarm;

import java.util.*;

/** Recognition for the calibrated local 1280x720 game; no input or Android lifecycle. */
public final class ShopVision {
    public enum State { SHOP, BUY_CONFIRM, REFRESH_CONFIRM, UNKNOWN }
    public enum Product {
        COVENANT("covenant", "誓约书签", 5, 184000),
        MYSTIC("mystic", "神秘奖牌", 50, 280000);
        public final String key, label;
        public final int quantity, price;
        Product(String key,String label,int quantity,int price) {
            this.key=key; this.label=label; this.quantity=quantity; this.price=price;
        }
    }
    public static final class Frame {
        public final int width,height;
        public final int[] pixels;
        private final Map<Integer,boolean[]> masks=new HashMap<>();
        public Frame(int width,int height,int[] pixels) {
            if(width<=0||height<=0||pixels.length!=width*height) throw new IllegalArgumentException("frame dimensions");
            this.width=width; this.height=height; this.pixels=pixels;
        }
        boolean[] mask(int threshold) {
            boolean[] result=masks.get(threshold);
            if(result!=null) return result;
            result=new boolean[pixels.length];
            for(int i=0;i<pixels.length;i++) {
                int c=pixels[i];
                result[i]=threshold==1850 ? (c>>>16&255)>180&&(c>>>8&255)>180&&(c&255)>180
                        : ((c>>>16&255)*3+(c>>>8&255)*6+(c&255))>threshold;
            }
            masks.put(threshold,result);
            return result;
        }
        public boolean supported() { return width==1280 && (height==719 || height==720); }
    }
    public static final class Template {
        final int width,height,threshold,bright;
        final boolean[] mask;
        final int[] anchors;
        public Template(Frame frame,int threshold) {
            this.width=frame.width; this.height=frame.height; this.threshold=threshold;
            this.mask=frame.mask(threshold);
            List<Integer> foreground=new ArrayList<>();
            for(int i=0;i<mask.length;i++) if(mask[i]) foreground.add(i);
            bright=foreground.size();
            if(bright<5) throw new IllegalArgumentException("empty template");
            anchors=new int[Math.min(16,bright)];
            for(int i=0;i<anchors.length;i++) anchors[i]=foreground.get(i*(bright-1)/Math.max(1,anchors.length-1));
        }
    }
    public static final class Hit {
        public final Product product;
        public final int x,y,buttonY,price;
        Hit(Product product,int x,int y) { this.product=product; this.x=x; this.y=y; this.buttonY=y+40; this.price=product.price; }
    }
    public static final class Scan {
        public final Hit hit;
        public final String uncertainty;
        Scan(Hit hit,String uncertainty){this.hit=hit;this.uncertainty=uncertainty;}
    }
    private final Map<String,Template> templates;
    public ShopVision(Map<String,Template> templates) {
        this.templates=new HashMap<>(templates);
        for(String key:new String[]{"shop_title","buy_dialog","refresh_dialog","refresh_button","covenant","mystic","dialog_covenant","dialog_mystic","available","sold","gold_icon","sky_icon"})
            if(!templates.containsKey(key)) throw new IllegalArgumentException("缺少模板: "+key);
    }
    private boolean matches(Frame f,Template t,int x,int y,double agreement) {
        if(t==null||x<0||y<0||x+t.width>f.width||y+t.height>f.height) return false;
        boolean[] pixels=f.mask(t.threshold);
        int anchorMisses=0;
        for(int i:t.anchors) {
            if(!pixels[(y+i/t.width)*f.width+x+i%t.width] && ++anchorMisses>4) return false;
        }
        int mismatch=0,missingBright=0;
        int limit=(int)Math.floor(t.mask.length*(1-agreement));
        for(int ty=0,i=0;ty<t.height;ty++) {
            int offset=(y+ty)*f.width+x;
            for(int tx=0;tx<t.width;tx++,i++) {
                boolean a=pixels[offset+tx],b=t.mask[i];
                if(a!=b && ++mismatch>limit) return false;
                if(b&&!a) missingBright++;
            }
        }
        return missingBright<=t.bright*.18;
    }
    private int[] locate(Frame f,Template t,int left,int top,int right,int bottom,double agreement) {
        if(f==null||t==null) return null;
        int x1=Math.min(right-t.width,f.width-t.width),y1=Math.min(bottom-t.height,f.height-t.height);
        for(int y=Math.max(0,top);y<=y1;y++)
            for(int x=Math.max(0,left);x<=x1;x++)
                if(matches(f,t,x,y,agreement)) return new int[]{x,y};
        return null;
    }
    public State state(Frame f) {
        if(f==null||!f.supported()) return State.UNKNOWN;
        if(locate(f,templates.get("buy_dialog"),570,130,712,180,.95)!=null) return State.BUY_CONFIRM;
        if(locate(f,templates.get("refresh_dialog"),522,302,760,346,.95)!=null) return State.REFRESH_CONFIRM;
        if(locate(f,templates.get("shop_title"),62,14,169,52,.95)!=null) return State.SHOP;
        return State.UNKNOWN;
    }
    public Hit findCard(Frame f,Product product) {
        return inspectCard(f,product).hit;
    }
    public Scan inspectCard(Frame f,Product product) {
        if(state(f)!=State.SHOP) return new Scan(null,"");
        String uncertain="";
        Template t=templates.get(product.key);
        // Every pixel position is searched; scrolling has no fixed row grid.
        for(int y=65;y+t.height<=Math.min(705,f.height);y++) {
            for(int x=684;x<=710;x++) {
                if(!matches(f,t,x,y,.95)) continue;
                Hit h=new Hit(product,x,y);
                if(h.buttonY>f.height-15) continue;
                if(locate(f,templates.get("sold"),1080,y+18,1148,y+70,.95)!=null)continue;
                boolean available=locate(f,templates.get("available"),1080,y+18,1148,y+70,.95)!=null;
                int price=readPrice(f,1140,y-32,1235,y+1);
                if(available&&price==product.price)return new Scan(h,"");
                // Rows clipped by the header are checked in the other scroll position.
                if(y>=100)uncertain="已看到"+product.label+"，但价格或购买状态无法确认（预期金币"+product.price+"）";
            }
        }
        return new Scan(null,uncertain);
    }
    public boolean sold(Frame f,Hit h) {
        return state(f)==State.SHOP && locate(f,templates.get("sold"),1080,h.y+18,1148,h.y+70,.95)!=null;
    }
    public int[] refreshButton(Frame f) {
        if(state(f)!=State.SHOP) return null;
        int[] p=locate(f,templates.get("refresh_button"),215,620,330,696,.95);
        return p==null?null:new int[]{p[0]+43,p[1]+17};
    }
    public Product dialogProduct(Frame f) {
        if(state(f)!=State.BUY_CONFIRM) return null;
        int price=readPrice(f,646,497,729,524);
        for(Product p:Product.values()) {
            if(price!=p.price) continue;
            if(locate(f,templates.get("dialog_"+p.key),635,332,735,368,.93)!=null) return p;
        }
        return null;
    }
    public int readPrice(Frame f,int left,int top,int right,int bottom) {
        return readNumber(f,left,top,right,bottom,5,7,false,1350);
    }
    public int gold(Frame f) { return currency(f,"gold_icon",620,780,150); }
    public int skystones(Frame f) { return currency(f,"sky_icon",760,930,100); }
    private int currency(Frame f,String key,int left,int right,int numberWidth) {
        if(f==null || state(f)!=State.SHOP) return -1;
        // Locate the coloured currency icon, excluding white digits and the '+' badge.
        int min=f.width,max=-1,count=0;
        boolean gold=key.equals("gold_icon");
        for(int y=21;y<45;y++) for(int x=left;x<right;x++) {
            int c=f.pixels[y*f.width+x],r=c>>>16&255,g=c>>>8&255,b=c&255;
            boolean on=gold ? r>170&&g>100&&b<100&&r>g+12&&g>b+35
                    : r<120&&g>120&&b>180&&b>g+10;
            if(on) {min=Math.min(min,x);max=Math.max(max,x);count++;}
        }
        if(count<25||max-min>35) return -1;
        int start=(min+max)/2+15;
        return readNumber(f,start,25,Math.min(f.width,start+numberWidth),44,1,9,true,1850);
    }
    private int readNumber(Frame f,int left,int top,int right,int bottom,int minDigits,int maxDigits,boolean firstGroup,int threshold) {
        if(f==null||left<0||top<0||right>f.width||bottom>f.height) return -1;
        boolean[] mask=f.mask(threshold);
        List<int[]> components=new ArrayList<>();
        int start=-1;
        for(int x=left;x<=right;x++) {
            boolean on=false;
            if(x<right) for(int y=top;y<bottom;y++) if(mask[y*f.width+x]) {on=true;break;}
            if(on && start<0) start=x;
            if(!on && start>=0) {
                int y0=bottom,y1=top;
                for(int yy=top;yy<bottom;yy++) for(int xx=start;xx<x;xx++) if(mask[yy*f.width+xx]) {y0=Math.min(y0,yy);y1=Math.max(y1,yy);}
                if(y1-y0+1>=10) {
                    if(firstGroup&&!components.isEmpty()) {
                        int[] previous=components.get(components.size()-1);
                        if(start-(previous[0]+previous[2])>=12) break;
                    }
                    components.add(new int[]{start,y0,x-start,y1-y0+1});
                }
                start=-1;
            }
        }
        if(components.size()<minDigits||components.size()>maxDigits) return -1;
        int number=0;
        for(int[] box:components) {
            double best=1,runnerUp=1;
            int digit=-1;
            double[] scores=new double[10]; Arrays.fill(scores,1);
            for(Map.Entry<String,Template> e:templates.entrySet()) {
                String prefix=threshold==1850?"currency_digit_":"digit_";
                if(!e.getKey().startsWith(prefix)) continue;
                int d=e.getKey().charAt(prefix.length())-'0';
                scores[d]=Math.min(scores[d],digitError(f,box,e.getValue()));
            }
            for(int d=0;d<10;d++) {
                if(scores[d]<best) {runnerUp=best;best=scores[d];digit=d;}
                else runnerUp=Math.min(runnerUp,scores[d]);
            }
            if(digit<0||best>.20||runnerUp-best<.035) return -1;
            number=number*10+digit;
        }
        return number;
    }
    private double digitError(Frame f,int[] b,Template t) {
        boolean[] pixels=f.mask(t.threshold);
        int different=0;
        for(int y=0;y<20;y++) for(int x=0;x<12;x++) {
            boolean a=pixels[(b[1]+y*b[3]/20)*f.width+b[0]+x*b[2]/12];
            boolean c=t.mask[(y*t.height/20)*t.width+x*t.width/12];
            if(a!=c) different++;
        }
        return different/240.0;
    }
}
