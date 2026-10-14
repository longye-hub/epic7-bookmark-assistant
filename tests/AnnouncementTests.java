package com.e7.bmfarm;
import java.io.IOException;
public final class AnnouncementTests {
 static final String FIXTURE="schema=1\nsequence=12\nactive=true\ntitle=维护公告\nbody=本次公告无需更新安装包。请在任务停止后检查版本。\n";
 static int checks;
 static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static void reject(String text)throws Exception{checks++;try{AnnouncementInfo.parse(text);throw new AssertionError("unsafe public announcement accepted");}catch(IOException expected){}}
 public static void main(String[]args)throws Exception{
  AnnouncementInfo info;
  try{info=AnnouncementInfo.parse(FIXTURE);}catch(IOException e){throw new AssertionError("ordinary announcement must parse independently of APK version",e);}
  need(info.title.equals("维护公告")&&info.body.contains("无需更新安装包"),"public Unicode text is preserved");
  need(info.unreadAfter(11)&&!info.unreadAfter(12)&&!info.unreadAfter(99),"monotonic acknowledgement suppresses repeats and older replay");
  need(!AnnouncementInfo.parse(FIXTURE.replace("active=true","active=false")).unreadAfter(0),"withdrawn notice never prompts");
  need(!AnnouncementInfo.parse("schema=1\nsequence=0\nactive=false\ntitle=\nbody=\n").unreadAfter(0),"empty inactive notice is valid");
  reject(FIXTURE.replace("sequence=12","sequence=-1"));
  reject(FIXTURE.replace("sequence=12","sequence=9223372036854775808"));
  reject(FIXTURE.replace("sequence=12","sequence=0"));
  reject(FIXTURE.replace("active=true","active=yes"));
  reject(FIXTURE.replace("schema=1","schema=2"));
  reject(FIXTURE+"sequence=13\n");
  reject(FIXTURE+"deviceId=private\n");
  reject(FIXTURE+"action=startFarm\n");
  reject(FIXTURE.replace("title=维护公告","title="));
  reject(FIXTURE.replace("body=","body=\u0000"));
  reject(FIXTURE.replace("title=维护公告","title="+new String(new char[81]).replace('\0','x')));
  reject(FIXTURE.replace("body=","body="+new String(new char[3001]).replace('\0','x')));
  reject(FIXTURE+new String(new char[17000]).replace('\0','x'));
  System.out.println("ANNOUNCEMENT_PASS "+checks+" checks; independent public text, bounded fields, acknowledgement and withdrawal");
 }
}
