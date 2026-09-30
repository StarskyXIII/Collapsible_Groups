package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Observation;
import com.starskyxiii.cgbridgeprobe.Reflect;
import mezz.jei.gui.elements.IconButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@Mixin(targets="com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController",remap=false)
public abstract class JeiButtonMixin {
 @Inject(method="drawForegroundPhase",at=@At("RETURN")) private void cg$draw(GuiGraphicsExtractor g,int mx,int my,float t,CallbackInfo c) {
  if(!Boolean.TRUE.equals(Reflect.call(this,"shouldShowGroupsButton")))return;
  var r=((IconButton)Reflect.field(this,"groupsButton")).getArea();
  Observation.managerButton=Observation.rect(r.getX(),r.getY(),r.getWidth(),r.getHeight());
 }
}
