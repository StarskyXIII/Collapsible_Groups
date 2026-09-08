package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScriptManager.class, remap = false)
public abstract class MixinKubeJSScriptManager {
	@Inject(method = "load", at = @At("RETURN"))
	private void cg$afterClientScriptsLoaded(CallbackInfo ci) {
		ScriptManager self = (ScriptManager) (Object) this;
		if (self.scriptType == ScriptType.CLIENT) ScriptedGroupStore.invalidateAndNotify();
	}
}
