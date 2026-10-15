package com.e7.bmfarm;
import java.lang.reflect.*;
/** Runs the actual Android service handler with small JVM boundary substitutes.
 * No screenshot token, input gesture, device, or game is used by these tests. */
public final class CaptureRotationTests {
 static int checks;
 static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static void put(Object object,String field,Object value)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
 static Object get(Object object,String field)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
 static Object unconstructed(Class<?> type)throws Exception{
  Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
  return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
 }
 static PhoneCapture consent()throws Exception{
  PhoneCapture capture=(PhoneCapture)unconstructed(PhoneCapture.class);
  put(capture,"handler",new android.os.Handler());put(capture,"thread",new android.os.HandlerThread("test"));return capture;
 }
 static FloatService service(PhoneCapture capture)throws Exception{FloatService s=new FloatService();put(s,"phoneCapture",capture);return s;}
 public static void main(String[]args)throws Exception{
  PhoneCapture idle=consent();FloatService s=service(idle);
  s.onConfigurationChanged(new android.content.res.Configuration());
  need(get(s,"phoneCapture")==idle,"First portrait-to-landscape transition must preserve unused consent");
  need(!((Boolean)get(idle,"closed")),"Unused consent must remain open without capturing a screen");
  need(get(idle,"display")==null&&get(idle,"reader")==null,"Initial rotation must not create screen capture surfaces");
  s.onConfigurationChanged(new android.content.res.Configuration());
  need(get(s,"phoneCapture")==idle,"Repeated pre-task configuration changes must not force reauthorization");
  PhoneCapture active=consent();FloatService task=service(active);put(task,"running",true);
  task.onConfigurationChanged(new android.content.res.Configuration());
  need(get(task,"phoneCapture")==null&&!((Boolean)get(task,"running")),"Rotation during a task must stop input and release capture");
  need((Boolean)get(active,"closed"),"Active task consent must actually be closed");
  PhoneCapture used=consent();put(used,"captureStarted",true);FloatService after=service(used);
  after.onConfigurationChanged(new android.content.res.Configuration());
  need(get(after,"phoneCapture")==null&&(Boolean)get(used,"closed"),"A previously used capture session must not be treated as unused consent");
  FloatService empty=service(null);empty.onConfigurationChanged(new android.content.res.Configuration());
  need(get(empty,"phoneCapture")==null,"Configuration changes must never manufacture consent");
  System.out.println("CAPTURE_ROTATION_PASS "+checks+" assertions against the real FloatService; no screen or game input");
 }
}
