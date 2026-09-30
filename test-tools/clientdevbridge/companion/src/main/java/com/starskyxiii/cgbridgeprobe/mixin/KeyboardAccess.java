package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(KeyboardHandler.class)
public interface KeyboardAccess {
 @Invoker("keyPress") void cg$key(long w,int action,net.minecraft.client.input.KeyEvent event);
 @Invoker("charTyped") void cg$character(long w,net.minecraft.client.input.CharacterEvent event);
}
