package com.e7.bmfarm;
/** Queue authorization belongs to the issuing worker, even on the UI callback thread. */
public final class InputGuard {
 private InputGuard(){}
 public static boolean permitted(boolean running,boolean destroyed,Thread active,Thread issuer){
  return running&&!destroyed&&issuer!=null&&active==issuer&&!issuer.isInterrupted();
 }
}
