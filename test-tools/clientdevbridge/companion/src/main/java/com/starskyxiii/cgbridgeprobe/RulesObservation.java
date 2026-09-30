package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class RulesObservation {
 public static void capture(JsonObject out,Object screen,Object state) {
  Object core=Reflect.field(state,"core");
  JsonArray errors=new JsonArray();
  for(Object error:(List<?>)Reflect.field(core,"validationErrors"))errors.add(((Component)error).getString());
  out.add("ruleValidationErrors",errors);
  if(!"RULES".equals(out.get("mode").getAsString()))return;
  Object panel=Reflect.field(screen,"rulesPanel");
  String modal=String.valueOf(Reflect.field(panel,"modal"));out.addProperty("rulesModal",modal);
  JsonObject controls=out.getAsJsonObject("controls");
  controls.add("addCondition",Observation.rect(Reflect.call(panel,"addConditionRect")));
  controls.add("addRuleGroup",Observation.rect(Reflect.call(panel,"addGroupRect")));
  Object list=Reflect.call(panel,"listRect");
  int lx=(int)Reflect.call(list,"x"),ly=(int)Reflect.call(list,"y"),lw=(int)Reflect.call(list,"width"),lh=(int)Reflect.call(list,"height");
  int y=ly+4-Reflect.integer(panel,"scrollOffset");
  JsonArray rows=new JsonArray();
  for(Object row:(List<?>)Reflect.call(panel,"buildRows")) {
   Object node=Reflect.call(row,"node");
   if(y>=ly&&y+20<=ly+lh) {
    JsonObject r=Observation.rect(lx,y,lw,20);
    r.addProperty("path",String.valueOf(Reflect.call(row,"path")));
    r.addProperty("kind",String.valueOf(Reflect.call(node,"kind")));
    r.addProperty("value",String.valueOf(Reflect.call(node,"primaryValue")));
    r.addProperty("type",String.valueOf(Reflect.call(node,"ingredientType")));
    r.addProperty("selected",Reflect.field(core,"selectedRuleNode")==node);
    r.add("chip",Observation.rect((int)Reflect.call(panel,"chipX",row),y+4,(int)Reflect.call(panel,"chipWidth",node),12));
    r.add("edit",Observation.rect((int)Reflect.call(panel,"editButtonX",list),y,20,20));
    r.add("delete",Observation.rect((int)Reflect.call(panel,"deleteButtonX",list),y,20,20));
    rows.add(r);
   }
   y+=22;
  }
  out.add("ruleRows",rows);
  if(modal.equals("FORM")) {
   JsonObject form=new JsonObject();
   for(String name:List.of("formType","formPrimary","formSecondary","formTertiary")) {
    Object field=Reflect.field(panel,name);if(field!=null)form.add(name,Observation.box((EditBox)field));
   }
   Object box=Reflect.call(panel,"formModalRect");
   form.add("confirm",Observation.rect(Reflect.call(panel,"formConfirmRect",box)));
   form.add("cancel",Observation.rect(Reflect.call(panel,"formCancelRect",box)));
   JsonArray invalid=new JsonArray();for(Object role:(Set<?>)Reflect.field(panel,"formInvalidRoles"))invalid.add(role.toString());
   form.add("invalid",invalid);out.add("ruleForm",form);
  }
  if(modal.equals("MENU")) {
   Object box=Reflect.call(panel,"menuModalRect"),area=Reflect.call(panel,"menuListRect",box);
   int x=(int)Reflect.call(area,"x"),top=(int)Reflect.call(area,"y"),width=(int)Reflect.call(area,"width"),height=(int)Reflect.call(area,"height");
   int my=top-Reflect.integer(panel,"modalScrollOffset");JsonArray entries=new JsonArray();
   for(Object entry:(List<?>)Reflect.call(panel,"menuEntries")) {
    if(my>=top&&my+20<=top+height) {
     JsonObject e=Observation.rect(x,my,width,20);e.addProperty("kind",String.valueOf(Reflect.call(entry,"kind")));
     e.addProperty("wrap",(boolean)Reflect.call(entry,"wrap"));entries.add(e);
    }my+=22;
   }out.add("rulesMenu",entries);
  }
 }
}
