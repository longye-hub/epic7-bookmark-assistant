package android.os;
public class HandlerThread extends Thread {
 public HandlerThread(String name){super(name);}
 public boolean quitSafely(){return true;}
}
