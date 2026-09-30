package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import org.cyclops.clientdevbridge.mcadapter.ClientThread;
import org.cyclops.clientdevbridge.protocol.Dispatcher;
public final class Probe {
 public static void register(Dispatcher d) {
  d.register("cgtest.identity",p->ClientThread.submit(Probe::identity));
  d.register("cgtest.observe",p->ClientThread.submit(Observation::snapshot));
  d.register("cgtest.history",p->ClientThread.submit(()->PublicationObservation.since(p.get("afterFrame").getAsLong())));
  d.register("cgtest.input",p->ClientThread.submit(()->NativeInput.execute(p)));
 }
 public static JsonObject identity() {
  JsonObject o=new JsonObject();o.addProperty("protocol",1);o.addProperty("probeVersion","0.1.0");
  o.addProperty("session",System.getProperty("cgbridgeprobe.session",""));o.addProperty("pid",ProcessHandle.current().pid());
  o.addProperty("gameDir",Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().toString());
  o.addProperty("loader",Platform.LOADER);o.addProperty("mcVersion",net.minecraft.SharedConstants.getCurrentVersion().name());
  o.add("mods",Platform.mods());return o;
 }
}
