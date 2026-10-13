package com.e7.bmfarm;
import java.io.IOException;
import java.net.URI;
import java.util.*;
/** Only public release metadata. Never a request model for local user data. */
public final class UpdateInfo {
 public static final String REPOSITORY="https://github.com/longye-hub/epic7-bookmark-assistant";
 public final int versionCode;public final String versionName,channel,releaseUrl,downloadUrl,sha256,notes;
 private UpdateInfo(int code,Map<String,String> p){versionCode=code;versionName=p.get("versionName");channel=p.get("channel");releaseUrl=p.get("releaseUrl");downloadUrl=p.get("downloadUrl");sha256=p.get("sha256");notes=p.get("notes");}
 public static String endpoint(String pkg)throws IOException{
  String variant=variant(pkg);
  return "https://raw.githubusercontent.com/longye-hub/epic7-bookmark-assistant/main/updates/"+variant+".properties";
 }
 private static String variant(String pkg)throws IOException{if("com.e7.bmfarm".equals(pkg))return "root";if("com.e7.bmfarm.mobile".equals(pkg))return "mobile";throw new IOException("不支持的应用类型");}
 public static UpdateInfo parse(String text,String pkg)throws IOException{
  String v=variant(pkg);if(text==null||text.length()>16384)throw new IOException("更新信息过大或为空");
  Set<String> allowed=new HashSet<>(Arrays.asList("schema","packageName","versionCode","versionName","channel","releaseUrl","downloadUrl","sha256","notes"));
  Map<String,String> p=new HashMap<>();
  for(String line:text.split("\\r?\\n")){
   if(line.isEmpty())continue;
   for(int x=0;x<line.length();x++)if(Character.isISOControl(line.charAt(x)))throw new IOException("更新信息包含控制字符");
   int eq=line.indexOf('=');if(eq<1)throw new IOException("更新信息格式无效");
   String key=line.substring(0,eq),value=line.substring(eq+1);
   if(!allowed.contains(key)||p.put(key,value)!=null)throw new IOException("更新信息字段无效或重复");
  }
  if(!p.keySet().equals(allowed)||!"1".equals(p.get("schema"))||!pkg.equals(p.get("packageName")))throw new IOException("更新信息与应用不匹配");
  int code;try{code=Integer.parseInt(p.get("versionCode"));}catch(NumberFormatException e){throw new IOException("更新版本号无效");}
  if(code<1||!p.get("versionName").matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!Arrays.asList("stable","preview").contains(p.get("channel"))||!p.get("sha256").matches("[a-fA-F0-9]{64}")||p.get("notes").length()>1500)throw new IOException("更新信息内容无效");
  String release=p.get("releaseUrl"),prefix=REPOSITORY+"/releases/tag/";
  if(!release.startsWith(prefix)||!release.substring(prefix.length()).matches("[A-Za-z0-9._-]{1,80}"))throw new IOException("更新页面不在作者仓库");
  String expected=REPOSITORY+"/releases/download/"+release.substring(prefix.length())+"/epic7-"+v+"-v"+p.get("versionName")+".apk";
  if(!expected.equals(p.get("downloadUrl")))throw new IOException("安装包地址与版本不匹配");
  return new UpdateInfo(code,p);
 }
 public boolean newerThan(int installed){return versionCode>installed;}
}
