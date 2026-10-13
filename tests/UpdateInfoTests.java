package com.e7.bmfarm;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class UpdateInfoTests {
 static String fixture="schema=1\npackageName=com.e7.bmfarm.mobile\nversionCode=4\nversionName=1.0.3\nchannel=preview\nreleaseUrl=https://github.com/longye-hub/epic7-bookmark-assistant/releases/tag/2026.10.13.2\ndownloadUrl=https://github.com/longye-hub/epic7-bookmark-assistant/releases/download/2026.10.13.2/epic7-mobile-v1.0.3.apk\nsha256="+new String(new char[64]).replace('\0','a')+"\nnotes=隐私加固与更新提醒\n";
 static int checks;
 static void need(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 static void reject(String text)throws Exception{checks++;try{UpdateInfo.parse(text,"com.e7.bmfarm.mobile");throw new AssertionError("unsafe update metadata accepted");}catch(IOException expected){}}
 public static void main(String[]args)throws Exception{
  UpdateInfo i=UpdateInfo.parse(fixture,"com.e7.bmfarm.mobile");
  need(i.newerThan(3)&&!i.newerThan(4)&&!i.newerThan(5),"use integer versionCode, never version strings");
  need(i.notes.equals("隐私加固与更新提醒"),"preserve Unicode public notes");
  need(UpdateInfo.endpoint("com.e7.bmfarm.mobile").endsWith("/updates/mobile.properties"),"fixed mobile endpoint");
  need(UpdateInfo.endpoint("com.e7.bmfarm").endsWith("/updates/root.properties"),"fixed root endpoint");
  reject(fixture.replace("packageName=com.e7.bmfarm.mobile","packageName=com.e7.bmfarm"));
  reject(fixture.replace("schema=1","schema=2"));
  reject(fixture+"versionCode=99\n");
  reject(fixture+"deviceId=anything\n");
  reject(fixture.replace("versionCode=4","versionCode=-4"));
  reject(fixture.replace("versionCode=4","versionCode=2147483648"));
  reject(fixture.replace("https://github.com/","http://github.com/"));
  reject(fixture.replace("github.com/longye-hub/","evil.example/longye-hub/"));
  reject(fixture.replace("github.com/longye-hub/","github.com/other-owner/"));
  reject(fixture.replace("github.com/longye-hub/","github.com@evil.example/longye-hub/"));
  reject(fixture.replace("epic7-mobile-v1.0.3.apk","epic7-root-v1.0.3.apk"));
  reject(fixture.replace("epic7-mobile-v1.0.3.apk","epic7-mobile-v1.0.3.apk?secret=1"));
  reject(fixture.replace("notes=","notes=\u0000"));
  reject(fixture.replace("sha256=", "sha256=not-a-hash"));
  reject(fixture+new String(new char[17000]).replace('\0','x'));
  if(args.length>0){
   for(String variant:new String[]{"root","mobile"}){
    String pkg="com.e7.bmfarm"+(variant.equals("mobile")?".mobile":"");
    String live=new String(Files.readAllBytes(Paths.get(args[0],variant+".properties")),StandardCharsets.UTF_8);
    UpdateInfo released=UpdateInfo.parse(live,pkg);
    need(released.versionCode>0,"published manifest must pass the production parser: "+variant);
   }
  }
  System.out.println("UPDATE_INFO_PASS "+checks+" checks; fixed endpoints, strict metadata, origin, variant and version validation");
 }
}
