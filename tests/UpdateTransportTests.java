package com.e7.bmfarm;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class UpdateTransportTests {
 static Fake last;static int code=200,length=-1,opens;static byte[] content=UpdateInfoTests.fixture.getBytes(StandardCharsets.UTF_8);
 static volatile boolean block;static CountDownLatch entered;static final List<Fake> connections=Collections.synchronizedList(new ArrayList<Fake>());
 static final class Fake extends HttpsURLConnection {
  final CountDownLatch released=new CountDownLatch(1);volatile boolean disconnected;
  Fake(URL url){super(url);connections.add(this);}
  public void connect(){}public void disconnect(){disconnected=true;released.countDown();}public boolean usingProxy(){return false;}
  public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
  public int getResponseCode(){return code;}public long getContentLengthLong(){return length;}
  public InputStream getInputStream()throws IOException{
   if(block){entered.countDown();try{released.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new IOException("cancelled");}
   return new ByteArrayInputStream(content);
  }
  public OutputStream getOutputStream(){throw new AssertionError("update transport attempted to upload data");}
 }
 static void need(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
 static void rejects()throws Exception{try{UpdateTransport.fetch("com.e7.bmfarm.mobile");throw new AssertionError("unsafe response accepted");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){opens++;return last=new Fake(u);}}:null);
  need(UpdateTransport.fetch("com.e7.bmfarm.mobile").equals(UpdateInfoTests.fixture),"metadata passes through validated transport");
  need(last.getURL().toString().equals(UpdateInfo.endpoint("com.e7.bmfarm.mobile")),"only fixed public metadata URL");
  need(last.getRequestMethod().equals("GET")&&!last.getDoOutput(),"no body or upload method");
  need(!last.getInstanceFollowRedirects()&&!last.getUseCaches(),"no cross-origin redirects or disk cache");
  need(last.getRequestProperties().size()==2&&last.getRequestProperty("User-Agent").equals("Epic7-UpdateCheck/1")&&last.getRequestProperty("Accept").equals("text/plain"),"fixed headers, no cookie, authorization, identifier or version");
  code=302;int before=opens;rejects();need(opens==before+1,"redirect must not issue follow-up requests");
  code=200;length=17000;rejects();length=-1;content=new byte[17000];rejects();
  content=UpdateInfoTests.fixture.replace("github.com/longye-hub/","evil.example/longye-hub/").getBytes(StandardCharsets.UTF_8);rejects();
  try{UpdateTransport.fetch("com.e7.other");throw new AssertionError("unknown variant fetched");}catch(IOException expected){}
  content=AnnouncementTests.FIXTURE.getBytes(StandardCharsets.UTF_8);
  try{need(UpdateTransport.fetchAnnouncement().equals(AnnouncementTests.FIXTURE),"announcement fetch uses independent parser and fixed source");}
  catch(IOException e){throw new AssertionError("ordinary notices must be fetched independently",e);}
  need(last.getURL().toString().equals(NoticeEndpoints.announcement()),"fixed announcement URL; no user query parameters");
  need(last.getRequestMethod().equals("GET")&&!last.getDoOutput()&&last.getRequestProperties().size()==2,"announcement fetch must not upload or identify user");
  content=(AnnouncementTests.FIXTURE+"action=startFarm\n").getBytes(StandardCharsets.UTF_8);
  try{UpdateTransport.fetchAnnouncement();throw new AssertionError("announcement transport accepted action");}catch(IOException expected){}
  block=true;entered=new CountDownLatch(2);AtomicInteger cancelled=new AtomicInteger();
  Thread versions=new Thread(()->{try{UpdateTransport.fetch("com.e7.bmfarm.mobile");}catch(IOException e){cancelled.incrementAndGet();}});
  Thread notices=new Thread(()->{try{UpdateTransport.fetchAnnouncement();}catch(IOException e){cancelled.incrementAndGet();}});
  versions.start();notices.start();need(entered.await(3,TimeUnit.SECONDS),"both independent requests must be active");
  UpdateTransport.cancel();versions.join(3000);notices.join(3000);
  need(!versions.isAlive()&&!notices.isAlive()&&cancelled.get()==2,"cancel closes both in-flight requests");
  block=false;
  System.out.println("UPDATE_TRANSPORT_PASS fixed GET, fixed headers, no output stream, no redirect, bounded response and validated links");
 }
}
