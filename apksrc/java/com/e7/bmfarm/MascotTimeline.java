package com.e7.bmfarm;
/** Full original epicsubdash animations sampled at 60fps. */
public final class MascotTimeline {
 public enum Mode { RUN, WAIT, FALL }
 public static final int FRAME_COUNT=182,WIDTH=260,HEIGHT=304;
 private Mode mode=Mode.WAIT;private long started;
 public Mode mode(){return mode;}
 public void setMode(Mode next,long now){if(next==null)throw new IllegalArgumentException("animation mode");if(next!=mode){mode=next;started=now;}}
 public static Mode fromMarker(String marker){if("刷".equals(marker))return Mode.RUN;if("完".equals(marker)||"停".equals(marker))return Mode.FALL;return Mode.WAIT;}
 private int first(){return mode==Mode.WAIT?0:mode==Mode.RUN?84:124;}
 private int count(){return mode==Mode.WAIT?84:mode==Mode.RUN?40:58;}
 private long duration(){return (count()*1000L+59)/60;}
 private long elapsed(long now){long e=Math.max(0,now-started);return mode==Mode.FALL?Math.min(e,duration()):e%duration();}
 public int frame(long now){return first()+(int)Math.min(count()-1,elapsed(now)*60/1000);}
 public boolean hasNext(long now){return mode!=Mode.FALL||frame(now)<FRAME_COUNT-1;}
 public long delayUntilNext(long now){if(!hasNext(now))return 0;long e=elapsed(now),local=e*60/1000;return ((local+1)*1000+59)/60-e;}
}
