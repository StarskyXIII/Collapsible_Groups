package com.starskyxiii.cgbridgeprobe;
import java.lang.reflect.*;
public final class Reflect {
 public static Object field(Object object, String name) {
  Class<?> type = object instanceof Class<?> c ? c : object.getClass();
  for (Class<?> c=type; c!=null; c=c.getSuperclass()) {
   try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(object instanceof Class<?> ? null : object); }
   catch(NoSuchFieldException ignored) {} catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
  }
  throw new IllegalStateException(type.getName()+" has no field "+name);
 }
 public static Object call(Object object, String name, Object... args) {
  Class<?> type=object instanceof Class<?> c ? c : object.getClass();
  for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Method m:c.getDeclaredMethods()) {
   if(!m.getName().equals(name)||m.getParameterCount()!=args.length) continue;
   try { m.setAccessible(true); return m.invoke(object instanceof Class<?> ? null : object,args); }
   catch(IllegalArgumentException ignored) {} catch(ReflectiveOperationException e) { throw new IllegalStateException(type.getName()+"."+name,e); }
  }
  throw new IllegalStateException(type.getName()+" has no method "+name);
 }
 public static Class<?> type(String name) { try { return Class.forName(name); } catch(ClassNotFoundException e) { throw new IllegalStateException(e); } }
 public static int integer(Object o,String n) { return ((Number)field(o,n)).intValue(); }
}
