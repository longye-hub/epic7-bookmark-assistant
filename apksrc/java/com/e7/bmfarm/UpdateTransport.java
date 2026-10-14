package com.e7.bmfarm;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
/** Fixed public GETs. No request body, identifiers, cookies or local-file API. */
public final class UpdateTransport {
 private static final AtomicReference<HttpsURLConnection> version=new AtomicReference<>(),announcement=new AtomicReference<>();
 public static void cancel(){close(version);close(announcement);}
 private static void close(AtomicReference<HttpsURLConnection> slot){HttpsURLConnection c=slot.getAndSet(null);if(c!=null)c.disconnect();}
 public static String fetch(String ownPackage)throws IOException{
  String text=read(UpdateInfo.endpoint(ownPackage),version);UpdateInfo.parse(text,ownPackage);return text;
 }
 public static String fetchAnnouncement()throws IOException{
  String text=read(NoticeEndpoints.announcement(),announcement);AnnouncementInfo.parse(text);return text;
 }
 private static String read(String fixedUrl,AtomicReference<HttpsURLConnection> slot)throws IOException{
  if(Thread.currentThread().isInterrupted())throw new IOException("读取已取消");
  HttpsURLConnection c=(HttpsURLConnection)new URL(fixedUrl).openConnection();
  if(!slot.compareAndSet(null,c)){c.disconnect();throw new IOException("已有检查请求");}
  try{
   c.setRequestMethod("GET");c.setDoOutput(false);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
   c.setConnectTimeout(8000);c.setReadTimeout(8000);
   c.setRequestProperty("User-Agent","Epic7-UpdateCheck/1");c.setRequestProperty("Accept","text/plain");
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   if(Thread.currentThread().isInterrupted()||slot.get()!=c)throw new IOException("读取已取消");
   if(c.getResponseCode()!=200)throw new IOException("通知源暂时不可用");
   if(c.getContentLengthLong()>16384)throw new IOException("更新信息过大");
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[1024];int n;
    while((n=in.read(b))!=-1){if(out.size()+n>16384||System.nanoTime()>deadline||Thread.currentThread().isInterrupted()||slot.get()!=c)throw new IOException("通知读取已取消或超时");out.write(b,0,n);}
    return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();
   }
  }finally{c.disconnect();slot.compareAndSet(c,null);}
 }
 private UpdateTransport(){}
}
