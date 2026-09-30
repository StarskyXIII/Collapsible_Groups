package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import java.util.*;
public final class PublicationObservation {
 private static final ArrayDeque<JsonObject> history=new ArrayDeque<>();
 public static void capture(JsonObject frame) {
  JsonObject pub=new JsonObject();JsonArray attempts=new JsonArray();
  synchronized(GroupRepository.class) {
   pub.addProperty("activity",((Number)Reflect.field(GroupRepository.class,"publicationActivity")).longValue());
   pub.addProperty("applied",GroupRepository.areScriptedGroupsApplied());
   for(var entry:((Map<?,?>)Reflect.field(GroupRepository.class,"SCRIPTED_PUBLICATIONS")).entrySet()) {
    JsonObject row=new JsonObject();row.addProperty("owner",entry.getKey().toString());
    row.addProperty("generation",((Number)Reflect.call(entry.getValue(),"generation")).longValue());
    row.addProperty("state",Reflect.call(entry.getValue(),"state").toString());attempts.add(row);
   }
  }
  pub.add("attempts",attempts);frame.add("publication",pub);
  JsonObject row=new JsonObject();row.add("frame",frame.get("frame"));row.add("tick",frame.get("tick"));row.add("publication",pub);
  JsonArray ids=new JsonArray();for(var group:frame.getAsJsonArray("groups")) {String id=group.getAsJsonObject().get("id").getAsString();if(id.startsWith("__kjs_"))ids.add(id);}
  row.add("ids",ids);history.addLast(row);while(history.size()>4096)history.removeFirst();
 }
 public static JsonObject since(long frame) {
  JsonObject out=new JsonObject();out.addProperty("oldestFrame",history.isEmpty()?-1:history.getFirst().get("frame").getAsLong());
  JsonArray rows=new JsonArray();for(var row:history)if(row.get("frame").getAsLong()>frame)rows.add(row.deepCopy());
  out.add("frames",rows);return out;
 }
}
