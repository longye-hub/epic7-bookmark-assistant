package com.e7.bmfarm;
public final class NoticeResultTests {
 static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 public static void main(String[]args){
  NoticeResult onlyNotice=NoticeResult.parse(null,AnnouncementTests.FIXTURE,"com.e7.bmfarm.mobile");
  need(onlyNotice.update==null&&onlyNotice.announcement!=null,"version outage must not suppress valid ordinary announcement");
  NoticeResult onlyUpdate=NoticeResult.parse(UpdateInfoTests.fixture,"<html>login required</html>","com.e7.bmfarm.mobile");
  need(onlyUpdate.update!=null&&onlyUpdate.announcement==null,"announcement failure must not hide an update");
  NoticeResult valid=NoticeResult.parse(UpdateInfoTests.fixture,AnnouncementTests.FIXTURE,"com.e7.bmfarm.mobile");
  need(valid.update!=null&&valid.announcement!=null,"both independently validated sources survive");
  NoticeResult wrongVariant=NoticeResult.parse(UpdateInfoTests.fixture,AnnouncementTests.FIXTURE,"com.e7.bmfarm");
  need(wrongVariant.update==null&&wrongVariant.announcement!=null,"other variant APK must not be offered; common announcement remains");
  NoticeResult empty=NoticeResult.parse(null,null,"com.e7.bmfarm.mobile");
  need(empty.update==null&&empty.announcement==null,"offline sources produce no fabricated metadata");
  System.out.println("NOTICE_RESULT_PASS independent failures, common announcement and variant protection");
 }
}
