package com.e7.bmfarm;
import java.io.IOException;
import java.util.*;
/** Public text announcement, never an instruction to the automation engine. */
public final class AnnouncementInfo {
 public final long sequence;public final boolean active;public final String title,body;
 private AnnouncementInfo(long s,boolean a,String t,String b){sequence=s;active=a;title=t;body=b;}
 public static AnnouncementInfo parse(String text)throws IOException{
  if(text==null||text.length()>16384)throw new IOException("公告为空或过大");
  Set<String> allowed=new HashSet<>(Arrays.asList("schema","sequence","active","title","body"));
  Map<String,String> p=new HashMap<>();
  for(String line:text.split("\\r?\\n")){
   if(line.isEmpty())continue;
   for(int i=0;i<line.length();i++)if(Character.isISOControl(line.charAt(i)))throw new IOException("公告包含控制字符");
   int eq=line.indexOf('=');if(eq<1)throw new IOException("公告格式无效");
   String key=line.substring(0,eq);if(!allowed.contains(key)||p.put(key,line.substring(eq+1))!=null)throw new IOException("公告字段无效或重复");
  }
  if(!p.keySet().equals(allowed)||!"1".equals(p.get("schema")))throw new IOException("公告协议不支持");
  if(!p.get("sequence").matches("[0-9]+")||!Arrays.asList("true","false").contains(p.get("active")))throw new IOException("公告编号或状态无效");
  long s;try{s=Long.parseLong(p.get("sequence"));}catch(NumberFormatException e){throw new IOException("公告编号无效");}
  boolean active="true".equals(p.get("active"));String title=p.get("title"),body=p.get("body");
  if(title.length()>80||body.length()>3000||(active&&(s<1||title.trim().isEmpty()||body.trim().isEmpty())))throw new IOException("公告内容无效");
  return new AnnouncementInfo(s,active,title,body);
 }
 public boolean unreadAfter(long acknowledged){return active&&sequence>Math.max(0,acknowledged);}
}
