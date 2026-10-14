package com.e7.bmfarm;
import java.io.IOException;
/** Compile-time public origin. No account, device ID, remote redirect or editable URL. */
public final class NoticeEndpoints {
 public static final String BASE="https://cnb.cool/longye-hub/epic7-notices/-/git/raw/main/";
 public static String update(String pkg)throws IOException{
  if("com.e7.bmfarm".equals(pkg))return BASE+"root-v2.properties";
  if("com.e7.bmfarm.mobile".equals(pkg))return BASE+"mobile-v2.properties";
  throw new IOException("不支持的应用类型");
 }
 public static String announcement(){return BASE+"announcements.properties";}
 private NoticeEndpoints(){}
}
