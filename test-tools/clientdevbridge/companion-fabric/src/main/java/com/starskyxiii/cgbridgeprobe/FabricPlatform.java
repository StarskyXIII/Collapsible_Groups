package com.starskyxiii.cgbridgeprobe;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
final class Platform {
 static final String LOADER="fabric";
 static JsonObject mods() {
  JsonObject mods=new JsonObject();
  for(var mod:FabricLoader.getInstance().getAllMods()) {
   JsonObject m=new JsonObject();m.addProperty("version",mod.getMetadata().getVersion().getFriendlyString());
   if(mod.getOrigin().getKind()==net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
    var paths=mod.getOrigin().getPaths();m.addProperty("path",paths.isEmpty()?"":paths.getFirst().toAbsolutePath().normalize().toString());
   } else m.addProperty("path","nested:"+mod.getOrigin().getParentModId());
   mods.add(mod.getMetadata().getId(),m);
  }return mods;
 }
 static String ingredient(Object i) {return i instanceof IJeiFluidIngredient f?"fluid:"+BuiltInRegistries.FLUID.getKey(f.getFluidVariant().getFluid()):Observation.ingredient(i);}
}
