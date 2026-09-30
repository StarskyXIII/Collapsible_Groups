package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Observation;
import net.minecraft.client.gui.components.EditBox;
@Mixin(targets="mezz.jei.gui.input.GuiTextFieldFilter",remap=false)
public abstract class JeiSearchMixin {
 @Inject(method="extractForegroundRenderState",at=@At("RETURN")) private void cg$draw(CallbackInfo c) { Observation.search((EditBox)(Object)this); }
}
