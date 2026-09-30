package com.starskyxiii.cgbridgeprobe;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
final class Platform {
 static final String LOADER="neoforge";
 static JsonObject mods() {
  JsonObject mods=new JsonObject();
  for(var info:ModList.get().getMods()) {
   JsonObject m=new JsonObject();m.addProperty("version",info.getVersion().toString());
   m.addProperty("path",info.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize().toString());mods.add(info.getModId(),m);
  }return mods;
 }
 static String ingredient(Object i) {return i instanceof FluidStack f?"fluid:"+BuiltInRegistries.FLUID.getKey(f.getFluid()):Observation.ingredient(i);}
}
