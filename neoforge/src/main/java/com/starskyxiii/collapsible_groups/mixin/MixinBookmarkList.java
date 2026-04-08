package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = mezz.jei.gui.bookmarks.BookmarkList.class, remap = false)
public class MixinBookmarkList {
	@Inject(method = "onElementBookmarked", at = @At("HEAD"), cancellable = true)
	private <T> void cg$blockGroupHeaderBookmarks(
		IElement<T> element,
		UserInput input,
		BookmarkOverlay bookmarkOverlay,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (element.getTypedIngredient().getType() == GroupIcon.TYPE) {
			cir.setReturnValue(true);
		}
	}
}
