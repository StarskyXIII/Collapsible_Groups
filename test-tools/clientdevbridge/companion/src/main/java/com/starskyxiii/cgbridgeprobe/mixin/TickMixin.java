package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Observation;
import net.minecraft.client.Minecraft;
@Mixin(Minecraft.class)
public abstract class TickMixin {
 @Inject(method="tick",at=@At("TAIL")) private void cg$tick(CallbackInfo c) { Observation.tick++; }
}
