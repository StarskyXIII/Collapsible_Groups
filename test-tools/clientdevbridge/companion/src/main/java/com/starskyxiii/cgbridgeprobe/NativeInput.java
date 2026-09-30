package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import com.starskyxiii.cgbridgeprobe.mixin.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
public final class NativeInput {
 public static boolean active;
 public static int modifiers;
 public static long calls;
 public static final java.util.Set<Integer> heldKeys=new java.util.HashSet<>();
 private static Minecraft mc() { return Minecraft.getInstance(); }
 private static long window() { return mc().getWindow().handle(); }
 public static void move(double x,double y) {
  var w=mc().getWindow();
  if(x<0||y<0||x>=w.getGuiScaledWidth()||y>=w.getGuiScaledHeight()) throw new IllegalArgumentException("Off-screen input");
  ((MouseAccess)mc().mouseHandler).cg$move(window(),x*w.getScreenWidth()/w.getGuiScaledWidth(),y*w.getScreenHeight()/w.getGuiScaledHeight());
  if(mc().screen!=null)mc().mouseHandler.handleAccumulatedMovement();
 }
 public static void press(int button,int action) { ((MouseAccess)mc().mouseHandler).cg$press(window(),new net.minecraft.client.input.MouseButtonInfo(button,modifiers),action); }
 public static void key(int code,int action) { ((KeyboardAccess)mc().keyboardHandler).cg$key(window(),action,new net.minecraft.client.input.KeyEvent(code,GLFW.glfwGetKeyScancode(code),modifiers)); }
 public static void tap(int code) { try { key(code,GLFW.GLFW_PRESS); } finally { key(code,GLFW.GLFW_RELEASE); } }
 public static JsonObject execute(JsonObject p) {
  calls++;
  active=true; modifiers=p.has("modifiers")?p.get("modifiers").getAsInt():0;
  if((modifiers&~7)!=0) { active=false;modifiers=0;throw new IllegalArgumentException("Unsupported modifier"); }
  int held=-1;
  try {
   String action=p.get("action").getAsString();
   if(action.equals("drag")&&!mc().isWindowActive())throw new IllegalStateException("Drag requires the test game window to have focus");
   if(action.equals("click")||action.equals("text")||action.equals("drag")||action.equals("move")||action.equals("scroll")) {
    move(p.get("x").getAsDouble(),p.get("y").getAsDouble());
    if(!action.equals("move")&&!action.equals("scroll")) { held=p.has("button")?p.get("button").getAsInt():0; press(held,GLFW.GLFW_PRESS); }
    if(action.equals("drag")) for(var point:p.getAsJsonArray("points")) { var a=point.getAsJsonArray();move(a.get(0).getAsDouble(),a.get(1).getAsDouble()); }
    if(held!=-1) { press(held,GLFW.GLFW_RELEASE);held=-1; }
   }
   if(action.equals("text")) {
    String text=p.get("text").getAsString();
    if(text.length()>256||text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid text");
    int count=p.get("previousLength").getAsInt();
    if(count<0||count>512)throw new IllegalArgumentException("Invalid previous length");
    tap(GLFW.GLFW_KEY_END);for(int i=0;i<count;i++)tap(GLFW.GLFW_KEY_BACKSPACE);
    text.codePoints().forEach(c->((KeyboardAccess)mc().keyboardHandler).cg$character(window(),new net.minecraft.client.input.CharacterEvent(c)));
   } else if(action.equals("key"))tap(p.get("key").getAsInt());
   else if(action.equals("chord")) {
    for(var code:p.getAsJsonArray("keys")) {int k=code.getAsInt();heldKeys.add(k);key(k,GLFW.GLFW_PRESS);}
   }
   else if(action.equals("scroll"))((MouseAccess)mc().mouseHandler).cg$scroll(window(),0,p.get("dy").getAsDouble());
   else if(!java.util.Set.of("click","drag","move").contains(action))throw new IllegalArgumentException("Unknown action");
   JsonObject out=new JsonObject();out.addProperty("route","MouseHandler/KeyboardHandler");out.addProperty("calls",calls);return out;
  } finally { try { if(held!=-1)press(held,GLFW.GLFW_RELEASE);for(int k:new java.util.ArrayList<>(heldKeys)){heldKeys.remove(k);key(k,GLFW.GLFW_RELEASE);} } finally { heldKeys.clear();active=false;modifiers=0; } }
 }
}
