package com.e7.bmfarm;
import java.nio.file.*;
public final class PrivacyTests {
 static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("e7-private-cache-test");
  try{
   Files.write(dir.resolve("e7_f.png"),new byte[]{1,2,3});
   Files.write(dir.resolve("root-command.log"),new byte[]{4,5});
   Files.write(dir.resolve("journal.txt"),new byte[]{6});
   TransientFiles.clearKnown(dir.toFile());
   need(!Files.exists(dir.resolve("e7_f.png")),"screen contents must not remain on disk");
   need(!Files.exists(dir.resolve("root-command.log")),"command output must not remain on disk");
   need(Files.readAllBytes(dir.resolve("journal.txt"))[0]==6,"transaction records must remain untouched");
   TransientFiles.clearKnown(dir.toFile());
   CapturePrivacyPolicy p=new CapturePrivacyPolicy(100);
   need(!p.shouldEnd(false,false,false,500),"allow time to return to game before first capture");
   need(!p.shouldEnd(true,false,false,600),"game may keep consent");
   need(!p.shouldEnd(false,true,false,700),"own floating controls may keep consent while game remains behind");
   need(p.shouldEnd(false,false,false,800),"leaving game must end screen-sharing consent");
   need(new CapturePrivacyPolicy(100).shouldEnd(false,false,false,60100),"unused consent expires after one minute");
   need(new CapturePrivacyPolicy(100).shouldEnd(true,false,true,200),"lock immediately ends capture");
  }finally{try(java.util.stream.Stream<Path> files=Files.list(dir)){for(Path f:(Iterable<Path>)files::iterator)Files.delete(f);}Files.delete(dir);}
  System.out.println("PRIVACY_PASS scratch deletion, preserved journal, game exit, lock and unused consent");
 }
}
