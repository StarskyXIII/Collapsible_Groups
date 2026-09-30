package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.JeiObservation;
@Mixin(targets="mezz.jei.gui.overlay.ingredients.IngredientGrid",remap=false)
public abstract class JeiGridMixin {
 @Inject(method="draw",at=@At("RETURN")) private void cg$draw(CallbackInfo c) { JeiObservation.draw(this); }
}
