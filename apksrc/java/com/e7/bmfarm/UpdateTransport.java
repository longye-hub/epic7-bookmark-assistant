package com.e7.bmfarm;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
/** GET of one fixed public metadata file. No request body, identifiers, cookies or local-file API. */
public final class UpdateTransport {
 private static volatile HttpsURLConnection current;
 public static void cancel(){HttpsURLConnection c=current;if(c!=null)c.disconnect();}
 public static String fetch(String ownPackage)throws IOException{
  HttpsURLConnection c=(HttpsURLConnection)new URL(UpdateInfo.endpoint(ownPackage)).openConnection();
  current=c;
  try{
   c.setRequestMethod("GET");c.setDoOutput(false);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
   c.setConnectTimeout(8000);c.setReadTimeout(8000);
   c.setRequestProperty("User-Agent","Epic7-UpdateCheck/1");c.setRequestProperty("Accept","text/plain");
   if(c.getResponseCode()!=200)throw new IOException("更新源暂时不可用");
   if(c.getContentLengthLong()>16384)throw new IOException("更新信息过大");
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[1024];int n;
    while((n=in.read(b))!=-1){if(out.size()+n>16384||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("更新读取已取消或超时");out.write(b,0,n);}
    String text=new String(out.toByteArray(),StandardCharsets.UTF_8);UpdateInfo.parse(text,ownPackage);return text;
   }
  }finally{c.disconnect();if(current==c)current=null;}
 }
 private UpdateTransport(){}
}
