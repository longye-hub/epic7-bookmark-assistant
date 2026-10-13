package com.e7.bmfarm;
/** Consent may wait briefly for entry to the game; leaving after entry ends the session. */
public final class CapturePrivacyPolicy {
 private final long started;private boolean sawGame;
 public CapturePrivacyPolicy(long now){started=now;}
 public boolean shouldEnd(boolean game,boolean ownOverlay,boolean locked,long now){
  if(locked)return true;
  if(game){sawGame=true;return false;}
  if(sawGame)return !ownOverlay;
  return now-started>=60000;
 }
}
