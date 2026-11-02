package com.starskyxiii.cgsmoke.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgsmoke.JeiObservation;
@Mixin(targets="mezz.jei.gui.overlay.ingredients.IngredientGrid",remap=false)
public abstract class JeiGridMixin {
 // Draw runs on the render thread and does not recursively draw another grid.
 // Preserve the receiver before optimized JEI bytecode reuses local slot 0.
 @Unique private static Object cg$renderingGrid;
 @Inject(method="draw",at=@At("HEAD")) private void cg$beforeDraw(CallbackInfo c) { cg$renderingGrid=this; }
 @Inject(method="draw",at=@At("RETURN")) private static void cg$afterDraw(CallbackInfo c) {
  Object grid=cg$renderingGrid;cg$renderingGrid=null;
  if(grid!=null)JeiObservation.draw(grid);
 }
}
