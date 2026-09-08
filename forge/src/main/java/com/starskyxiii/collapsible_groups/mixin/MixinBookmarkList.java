package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = mezz.jei.gui.bookmarks.BookmarkList.class, remap = false)
public class MixinBookmarkList {
	@Inject(method = "onElementBookmarked", at = @At("HEAD"), cancellable = true, require = 0)
	private <T> void cg$blockGroupHeaderBookmarks(
		IElement<T> element,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		if (element.getTypedIngredient().getType() == GroupIcon.TYPE
			&& !JeiViewerAdapter.instance().bookmarkPolicy().canBookmarkGroupHeader()) {
			cir.setReturnValue(true);
		}
	}
}
