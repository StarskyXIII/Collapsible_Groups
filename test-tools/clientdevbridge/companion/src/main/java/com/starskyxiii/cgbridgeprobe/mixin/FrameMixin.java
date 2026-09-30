package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Observation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
@Mixin(GameRenderer.class)
public abstract class FrameMixin {
 @Inject(method="extract",at=@At("HEAD")) private void cg$begin(DeltaTracker d,boolean a,CallbackInfo c) { Observation.begin(); }
 @Inject(method="render",at=@At("TAIL")) private void cg$end(DeltaTracker d,boolean a,CallbackInfo c) { Observation.end(); }
}
