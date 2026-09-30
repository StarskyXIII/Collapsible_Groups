package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Probe;
import org.cyclops.clientdevbridge.protocol.Dispatcher;
@Mixin(value=Dispatcher.class,remap=false)
public abstract class DispatcherMixin {
 @Inject(method="<init>",at=@At("RETURN")) private void cg$register(CallbackInfo c) { Probe.register((Dispatcher)(Object)this); }
}
