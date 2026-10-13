package com.e7.bmfarm;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import java.security.cert.Certificate;
public final class UpdateTransportTests {
 static Fake last;static int code=200,length=-1,opens;static byte[] content=UpdateInfoTests.fixture.getBytes(StandardCharsets.UTF_8);
 static final class Fake extends HttpsURLConnection {
  Fake(URL url){super(url);}
  public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
  public int getResponseCode(){return code;}public long getContentLengthLong(){return length;}
  public InputStream getInputStream(){return new ByteArrayInputStream(content);}
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
  System.out.println("UPDATE_TRANSPORT_PASS fixed GET, fixed headers, no output stream, no redirect, bounded response and validated links");
 }
}
