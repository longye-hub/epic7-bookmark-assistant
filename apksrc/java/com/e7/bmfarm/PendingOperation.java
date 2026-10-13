package com.e7.bmfarm;
/** Durable confirmation metadata. A record alone never authorizes retrying an input. */
public final class PendingOperation {
    public final ShopVision.Product product;
    public final int row,before,after;
    public PendingOperation(ShopVision.Product product,int row,int before,int after) {
        if(before<0||after<0||before-after!=(product==null?3:product.price))throw new IllegalArgumentException("invalid pending balance");
        if(product!=null&&(row<65||row>680))throw new IllegalArgumentException("invalid pending row");
        this.product=product;this.row=row;this.before=before;this.after=after;
    }
    public String encode(){return (product==null?"REFRESH":product.name())+","+row+","+before+","+after;}
    public static PendingOperation decode(String value){
        String[] p=value.split(",");if(p.length!=4)throw new IllegalArgumentException("invalid pending record");
        return new PendingOperation(p[0].equals("REFRESH")?null:ShopVision.Product.valueOf(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));
    }
    public int balance(ShopVision v,ShopVision.Frame f){return product==null?v.skystones(f):v.gold(f);}
    public boolean completed(ShopVision v,ShopVision.Frame f){
        return v.state(f)==ShopVision.State.SHOP&&balance(v,f)==after&&(product==null||v.sold(f,new ShopVision.Hit(product,690,row)));
    }
    public String label(){return product==null?"刷新":product.label+"购买";}
}
