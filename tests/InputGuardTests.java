package com.e7.bmfarm;
public final class InputGuardTests {
 public static void main(String[]args)throws Exception{
  Thread issuing=Thread.currentThread();
  java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean();
  Thread main=new Thread(()->allowed.set(InputGuard.permitted(true,false,issuing,issuing)));main.start();main.join();
  if(!allowed.get())throw new AssertionError("dispatch on Android main thread must preserve issuing worker authorization");
  if(InputGuard.permitted(false,false,issuing,issuing))throw new AssertionError("stop blocks queued input");
  if(InputGuard.permitted(true,true,issuing,issuing))throw new AssertionError("destroy blocks input");
  if(InputGuard.permitted(true,false,new Thread(),issuing))throw new AssertionError("old worker cannot click after replacement");
  Thread interrupted=new Thread(()->{});interrupted.start();interrupted.join();
  issuing.interrupt();try{if(InputGuard.permitted(true,false,issuing,issuing))throw new AssertionError("interrupt blocks input");}finally{Thread.interrupted();}
  System.out.println("INPUT_GUARD_PASS main-thread dispatch, stopped, destroyed, replaced and interrupted; no real input");
 }
}
