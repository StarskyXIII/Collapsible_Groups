package com.starskyxiii.cgbridgeprobe.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import com.starskyxiii.cgbridgeprobe.NativeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(InputConstants.class)
public abstract class HeldKeyMixin {
 @Inject(method="isKeyDown",at=@At("HEAD"),cancellable=true)
 private static void cg$held(com.mojang.blaze3d.platform.Window window,int key,CallbackInfoReturnable<Boolean> cir) {
  if(NativeInput.active&&NativeInput.heldKeys.contains(key))cir.setReturnValue(true);
 }
}
