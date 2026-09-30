package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import net.minecraft.core.registries.BuiltInRegistries;
public final class JeiObservation {
 public static void draw(Object value) {
  IngredientGrid grid=(IngredientGrid)value;
  if(!Boolean.TRUE.equals(Reflect.field(grid,"searchable")))return;
  grid.getSlots().forEach(slot->{
   var element=slot.getOptionalElement().orElse(null);if(element==null||slot.isBlocked())return;
   var r=slot.getArea();var a=grid.getArea();if(r.getY()<a.getY()||r.getY()+r.getHeight()>a.getY()+a.getHeight())return;
   JsonObject o=Observation.rect(r.getX(),r.getY(),r.getWidth(),r.getHeight());Object ingredient=element.getTypedIngredient().getIngredient();
   if(ingredient instanceof GroupIcon icon) { o.addProperty("role","header");o.addProperty("groupId",icon.groupId());o.addProperty("expanded",icon.isExpanded()); }
   else { o.addProperty("role",element.getClass().getName().contains("Child")?"child":"ingredient");o.addProperty("id",Platform.ingredient(ingredient));if(o.get("role").getAsString().equals("child"))o.addProperty("groupId",(String)Reflect.field(element,"groupId")); }
   Observation.overlayCells.add(o);
  });
 }
}
