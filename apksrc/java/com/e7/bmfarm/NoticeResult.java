package com.e7.bmfarm;
/** Separately validated results: a broken version file must not hide an ordinary announcement. */
public final class NoticeResult {
 public final UpdateInfo update;public final AnnouncementInfo announcement;
 private NoticeResult(UpdateInfo u,AnnouncementInfo a){update=u;announcement=a;}
 public static NoticeResult parse(String version,String notice,String pkg){
  UpdateInfo update=null;AnnouncementInfo announcement=null;
  try{update=UpdateInfo.parse(version,pkg);}catch(java.io.IOException ignored){}
  try{announcement=AnnouncementInfo.parse(notice);}catch(java.io.IOException ignored){}
  return new NoticeResult(update,announcement);
 }
}
