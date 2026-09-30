package com.starskyxiii.cgbridgeprobe;
import com.google.gson.*;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.viewer.*;
import com.starskyxiii.collapsible_groups.client.widget.*;
import com.starskyxiii.collapsible_groups.client.editor.*;
import com.starskyxiii.collapsible_groups.client.editor.model.*;
import com.starskyxiii.collapsible_groups.compat.jei.manager.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class Observation {
 public static long frame,tick;
 public static boolean inEmiSearch;
 public static JsonObject managerButton;
 public static JsonArray sourceCells=new JsonArray(),overlayCells=new JsonArray();
 public static JsonObject searchField;
 private static JsonObject completed=new JsonObject();
 private static Object drawnScreen;
 private static long at;
 public static JsonObject rect(int x,int y,int w,int h) { JsonObject o=new JsonObject();o.addProperty("x",x);o.addProperty("y",y);o.addProperty("w",w);o.addProperty("h",h);return o; }
 public static JsonObject rect(Object r) { return rect(((Number)Reflect.call(r,"x")).intValue(),((Number)Reflect.call(r,"y")).intValue(),((Number)Reflect.call(r,"width")).intValue(),((Number)Reflect.call(r,"height")).intValue()); }
 public static String id(Object o) { return o==null?"none":o.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(o)); }
 public static JsonObject box(EditBox e) { JsonObject o=rect(e.getX(),e.getY(),e.getWidth(),e.getHeight());o.addProperty("value",e.getValue());o.addProperty("visible",e.visible);o.addProperty("focused",e.isFocused());return o; }
 public static void search(EditBox e) { searchField=box(e); }
 public static String ingredient(Object e) {
  if(e instanceof ItemStack s)return "item:"+BuiltInRegistries.ITEM.getKey(s.getItem());
  if(e instanceof EditorFluidIngredientView f)return "fluid:"+f.resourceId();
  if(e instanceof EditorGenericIngredientView g)return g.typeId()+":"+g.resourceId();
  if(e.getClass().getName().equals("mekanism.api.chemical.ChemicalStack"))return "mekanism:chemical:"+Reflect.call(e,"getTypeRegistryName");
  return "other:"+e.getClass().getName();
 }
 public static void sourceCell(Object e,int x,int y) { JsonObject o=rect(x,y,17,17);o.addProperty("id",ingredient(e));sourceCells.add(o); }
 public static void begin() { frame++;sourceCells=new JsonArray();overlayCells=new JsonArray();managerButton=null;searchField=null;inEmiSearch=false; }
 public static void end() {
  var mc=Minecraft.getInstance();var s=mc.screen;
  JsonObject o=new JsonObject();o.addProperty("frame",frame);o.addProperty("tick",tick);o.addProperty("screen",s==null?"none":s.getClass().getSimpleName());o.addProperty("screenId",id(s));
  o.addProperty("inWorld",mc.level!=null);o.addProperty("inputCalls",NativeInput.calls);
  o.addProperty("language",mc.options.languageCode);if(s!=null)o.addProperty("screenTitle",s.getTitle().getString());
  o.addProperty("guiWidth",mc.getWindow().getGuiScaledWidth());o.addProperty("guiHeight",mc.getWindow().getGuiScaledHeight());
  try {
   var adapter=ViewerLifecycleCoordinator.global().activeAdapter().orElse(null);
   o.addProperty("viewer",adapter==null?"none":adapter.id());o.addProperty("indexReady",adapter!=null&&adapter.groupIndex().ready());
   if(adapter!=null)o.addProperty("generation",id(adapter.groupIndex().candidates().orElse(null)));
   JsonArray groups=new JsonArray();for(var g:GroupRepository.getAllIncludingScripted()) { JsonObject row=new JsonObject();row.addProperty("id",g.id());row.addProperty("name",g.displayName().fallback());row.addProperty("enabled",g.enabled());row.addProperty("expanded",GroupExpandState.isExpandedById(g.id()));groups.add(row); }o.add("groups",groups);
   PublicationObservation.capture(o);
   if(s instanceof GroupManagerScreen) manager(o,s);
   if(s!=null&&s.getClass().getSimpleName().equals("CategoryPickerScreen"))BetaObservation.picker(o,s);
   if(s instanceof GroupEditorScreen) editor(o,s);
   o.add("sourceCells",sourceCells);o.add("overlayCells",overlayCells);
   if(searchField!=null)o.add("viewerSearch",searchField);
   if(managerButton!=null)o.add("managerButton",managerButton);
  } catch(Throwable error) { o.addProperty("observerError",error.toString()); }
  completed=o;drawnScreen=s;at=System.nanoTime();
 }
 public static JsonObject snapshot() {
  JsonObject o=completed.deepCopy();var mc=Minecraft.getInstance();
  o.addProperty("fresh",drawnScreen==mc.screen&&at>0&&System.nanoTime()-at<2_000_000_000L&&mc.getOverlay()==null&&!o.has("observerError"));
  o.addProperty("modifiersReleased",!NativeInput.active&&NativeInput.modifiers==0&&NativeInput.heldKeys.isEmpty());
  return o;
 }
 private static void manager(JsonObject o,Object s) {
  o.add("search",box((EditBox)Reflect.field(s,"searchField")));
  o.addProperty("source",String.valueOf(Reflect.field(s,"sourceFilter")));o.addProperty("pending",Boolean.TRUE.equals(Reflect.field(s,"generationPending")));
  Object header=Reflect.field(s,"headerLayout"),content=Reflect.field(s,"contentLayout");
  JsonObject controls=new JsonObject();controls.add("new",rect(Reflect.call(header,"secondary")));controls.add("back",rect(Reflect.call(header,"back")));
  controls.add("batch",rect(Reflect.call(header,"primary")));controls.add("settings",rect(Reflect.call(content,"settings")));
  controls.add("category",rect(Reflect.call(content,"categoryToggle",Reflect.call(s,"sidebarVisible"))));o.add("controls",controls);
  o.addProperty("batchMode",(boolean)Reflect.field(s,"batchMode"));o.addProperty("sidebarOpen",(boolean)Reflect.field(s,"sidebarOpen"));
  List<?> cards=(List<?>)Reflect.field(s,"filteredCards");JsonArray rows=new JsonArray();
  int top=(int)Reflect.call(s,"headerHeight"),bottom=Minecraft.getInstance().screen.height-28;
  for(int i=0;i<cards.size();i++) { var c=(GroupManagerCard)cards.get(i);int[] xy=(int[])Reflect.call(s,"cardPos",i);int x=xy[0],y=xy[1];
   if(y<top||y+116>bottom)continue;
   JsonObject r=rect(x,y,196,116);r.addProperty("id",c.id());r.addProperty("name",c.displayName());r.addProperty("enabled",c.group().enabled());
   r.addProperty("items",c.itemCount());r.addProperty("fluids",c.fluidCount());r.addProperty("generic",c.genericCount());
   r.add("toggle",rect((int)Reflect.call(s,"switchControlX",x),(int)Reflect.call(s,"switchControlY",y),24,24));
   r.add("edit",rect((int)Reflect.call(s,"editButtonX",x),y+88,24,20));r.add("delete",rect((int)Reflect.call(s,"deleteButtonX",x),y+88,24,20));rows.add(r);
  }o.add("cards",rows);o.addProperty("filteredCount",cards.size());
  boolean dialog=Reflect.field(s,"pendingDelete")!=null||Reflect.field(s,"pendingBatchDelete")!=null;o.addProperty("dialog",dialog);if(dialog)dialog(o);
  BetaObservation.manager(o,s);
 }
 private static void editor(JsonObject o,Object s) {
  Object state=Reflect.field(s,"state"),right=Reflect.field(s,"rightPanel");var shell=(EditorShellLayout)Reflect.field(s,"shell");
  o.addProperty("loading",(boolean)Reflect.field(s,"editorDataLoading"));o.addProperty("dirty",(boolean)Reflect.field(s,"dirty"));
  o.addProperty("mode",String.valueOf(Reflect.field(s,"activeMode")));o.addProperty("contentType",String.valueOf(Reflect.field(s,"activeContentFilter")));
  o.add("nameField",box((EditBox)Reflect.field(s,"nameField")));o.add("search",box((EditBox)Reflect.field(s,"searchField")));
  JsonObject controls=new JsonObject();controls.add("save",rect(shell.saveButton()));controls.add("cancel",rect(shell.cancelButton()));
  for(var type:EditorContentFilter.values())controls.add(type.name(),rect(Reflect.call(s,"contentFilterRect",type)));
  for(var mode:EditorShellMode.values())controls.add(mode.name(),rect(Reflect.call(s,"modeSegmentRect",mode)));
  o.add("controls",controls);
  o.addProperty("hasGenericIngredients",(boolean)Reflect.field(s,"hasGenericIngredients"));
  RulesObservation.capture(o,s,state);
  JsonArray items=new JsonArray();for(var item:(List<?>)Reflect.field(right,"groupItems"))items.add(ingredient(item));o.add("previewItems",items);
  JsonArray fluids=new JsonArray();for(var fluid:(List<?>)Reflect.field(right,"groupFluids"))fluids.add(ingredient(fluid));o.add("previewFluids",fluids);
  JsonArray generic=new JsonArray();for(var value:(List<?>)Reflect.field(right,"groupGenericIngredients"))generic.add(ingredient(value));o.add("previewGeneric",generic);
  JsonArray selectors=new JsonArray();Object contents=Reflect.field(state,"contentsProjection");
  if(contents!=null)for(var v:(Set<?>)Reflect.call(contents,"explicitItemSelectors"))selectors.add(v.toString());o.add("selectors",selectors);
  o.addProperty("dialog",(boolean)Reflect.field(s,"discardDialogOpen"));if((boolean)Reflect.field(s,"discardDialogOpen"))dialog(o);
 }
 private static void dialog(JsonObject o) { var s=Minecraft.getInstance().screen;o.add("dialogPrimary",rect(ConfirmDialog.primaryButton(s.width,s.height)));o.add("dialogSecondary",rect(ConfirmDialog.secondaryButton(s.width,s.height))); }
}
