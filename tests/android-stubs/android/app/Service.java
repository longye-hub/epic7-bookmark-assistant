package android.app;
public class Service extends android.content.Context {
 public int foregroundStops;
 public void onConfigurationChanged(android.content.res.Configuration c){}
 public void stopForeground(boolean remove){foregroundStops++;}
}
