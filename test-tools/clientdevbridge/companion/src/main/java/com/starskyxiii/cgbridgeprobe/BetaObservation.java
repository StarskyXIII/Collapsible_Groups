package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;

public final class BetaObservation {
 public static void manager(JsonObject out,Object screen) {
  out.addProperty("selectedCount",(int)Reflect.call(Reflect.field(screen,"batchSelection"),"selectedCount"));
  boolean open=(boolean)Reflect.field(screen,"batchMenuOpen");out.addProperty("batchMenuOpen",open);
  if(open) {
   Object layout=Reflect.call(screen,"batchMenuLayout");JsonObject actions=new JsonObject();
   String[] names={"SELECT_ALL_RESULTS","ENABLE","DISABLE","DELETE","MOVE"};
   for(int i=0;i<names.length;i++)actions.add(names[i],Observation.rect(Reflect.call(layout,"row",i)));
   out.add("batchActions",actions);
  }
  Object preferences=Reflect.call(Reflect.field(screen,"categories"),"snapshot");
  out.add("categories",JsonParser.parseString((String)Reflect.call(preferences,"toJson")));
 }
 public static void picker(JsonObject out,Object screen) {
  out.add("search",Observation.box((EditBox)Reflect.field(screen,"search")));
  Object state=Reflect.field(screen,"state"),bounds=Reflect.call(screen,"listRect");
  int first=Reflect.integer(screen,"first"),count=(int)Reflect.call(screen,"rows");
  List<?> visible=(List<?>)Reflect.call(state,"visible");JsonArray rows=new JsonArray();
  for(int i=first;i<visible.size()&&i<first+count;i++) {
   JsonObject row=Observation.rect((int)Reflect.call(bounds,"x"),(int)Reflect.call(bounds,"y")+(i-first)*20,(int)Reflect.call(bounds,"width"),20);
   row.addProperty("id",(String)Reflect.call(visible.get(i),"id"));rows.add(row);
  }
  out.add("categoryRows",rows);out.addProperty("selectedCategory",(String)Reflect.call(state,"selected"));
  JsonObject controls=new JsonObject();controls.add("confirm",Observation.rect(Reflect.call(screen,"buttonRect",true)));
  controls.add("cancel",Observation.rect(Reflect.call(screen,"buttonRect",false)));out.add("controls",controls);
 }
}
