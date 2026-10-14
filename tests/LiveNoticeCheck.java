package com.e7.bmfarm;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Optional publisher-only read-only live check; not part of offline tests or APK. */
public final class LiveNoticeCheck {
 public static void main(String[]args)throws Exception{
  Path out=args.length>0?Paths.get(args[0]):null;if(out!=null)Files.createDirectories(out);
  for(String variant:new String[]{"root","mobile"}){
   String pkg="com.e7.bmfarm"+(variant.equals("mobile")?".mobile":"");
   String text=UpdateTransport.fetch(pkg);UpdateInfo info=UpdateInfo.parse(text,pkg);
   if(out!=null)Files.write(out.resolve(variant+".properties"),text.getBytes(StandardCharsets.UTF_8));
   System.out.println("LIVE_VERSION_PASS "+variant+" "+info.versionName+" code="+info.versionCode+" source="+UpdateInfo.endpoint(pkg));
  }
  String text=UpdateTransport.fetchAnnouncement();AnnouncementInfo info=AnnouncementInfo.parse(text);
  if(out!=null)Files.write(out.resolve("announcements.properties"),text.getBytes(StandardCharsets.UTF_8));
  System.out.println("LIVE_ANNOUNCEMENT_PASS sequence="+info.sequence+" active="+info.active+" source="+NoticeEndpoints.announcement());
 }
}
