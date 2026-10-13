package com.e7.bmfarm;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
public final class MascotTimelineTests {
 static int checks;
 static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static int frames(String meta,String state){Matcher m=Pattern.compile("\\\""+state+"\\\"\\s*:\\s*\\{\\s*\\\"frames\\\"\\s*:\\s*(\\d+)").matcher(meta);if(!m.find())throw new AssertionError("source metadata missing "+state);return Integer.parseInt(m.group(1));}
 public static void main(String[]args)throws Exception {
  String metadata=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
  int idle=frames(metadata,"idle"),run=frames(metadata,"move"),fall=frames(metadata,"down")+frames(metadata,"fail");
  need(idle==84&&run==40&&fall==58,"complete source animations at 60fps");
  MascotTimeline t=new MascotTimeline();
  MascotTimeline.Mode[] modes={MascotTimeline.Mode.WAIT,MascotTimeline.Mode.RUN,MascotTimeline.Mode.FALL};
  int[] counts={idle,run,fall},firsts={0,idle,idle+run};long origin=0;
  for(int m=0;m<3;m++){
   t.setMode(modes[m],origin);int n=counts[m],first=firsts[m];
   for(int i=0;i<n;i++){
    long begin=(i*1000L+59)/60,end=((i+1)*1000L+59)/60;
    need(t.frame(origin+begin)==first+i,"native frame order "+m+"/"+i);
    need(t.frame(origin+end-1)==first+i,"frame holds for 16 or 17ms "+m+"/"+i);
    need(t.delayUntilNext(origin+begin)==(m==2&&i==n-1?0:end-begin),"precise next frame schedule");
   }
   long duration=(n*1000L+59)/60;
   need(t.frame(origin+duration)==(m==2?first+n-1:first),"loop or terminal hold");
   long now=origin+300;t.setMode(modes[m],now);need(t.frame(now)==first+18,"same status keeps phase");
   origin+=10000;
  }
  need(t.frame(999999)==181&&!t.hasNext(999999),"fall remains down and stops callbacks");
  t.setMode(MascotTimeline.Mode.RUN,1000000);need(t.frame(1000000)==84,"next explicit run restarts");
  need(t.frame(999999)==84,"clock anomaly clamps elapsed");
  need(MascotTimeline.fromMarker("完")==MascotTimeline.Mode.FALL&&MascotTimeline.fromMarker("停")==MascotTimeline.Mode.FALL,"complete/error mapping");
  need(MascotTimeline.fromMarker("等")==MascotTimeline.Mode.WAIT&&MascotTimeline.fromMarker("签")==MascotTimeline.Mode.WAIT,"wait mapping");
  System.out.println("NATIVE_TIMELINE_PASS "+checks+" checks; 84 idle, 40 run, 58 fall; original metadata; no game input");
 }
}
