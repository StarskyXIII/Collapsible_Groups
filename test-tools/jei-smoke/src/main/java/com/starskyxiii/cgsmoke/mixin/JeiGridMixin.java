package com.starskyxiii.cgsmoke.mixin;
import com.starskyxiii.cgsmoke.JeiObservation;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation",remap=false)
public abstract class JeiGridMixin {
 // Observe after drawing without depending on the optimized grid method's local slot 0.
 // The caller retains its receiver; Forge 1.20.1's Mixin requires an instance handler.
 // Select by owner/name because Fabric remaps the Minecraft parameter types.
 @Redirect(method="drawForeground",at=@At(value="INVOKE",
  target="Lmezz/jei/gui/overlay/ingredients/IngredientGrid;draw"),require=1,allow=1)
 private void cg$draw(IngredientGrid grid,Minecraft minecraft,GuiGraphics graphics,
  int mouseX,int mouseY,boolean tooltip) {
  grid.draw(minecraft,graphics,mouseX,mouseY,tooltip);
  JeiObservation.draw(grid);
 }
}
