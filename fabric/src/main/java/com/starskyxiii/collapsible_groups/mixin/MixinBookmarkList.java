package com.starskyxiii.collapsible_groups.mixin;

// Keep this annotation shell byte-identical across Fabric, Forge, and NeoForge.

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = mezz.jei.gui.bookmarks.BookmarkList.class, remap = false)
public class MixinBookmarkList {
	@Inject(
		method = {
			"onElementBookmarked(Lmezz/jei/gui/overlay/elements/IElement;Lmezz/jei/gui/input/UserInput;Lmezz/jei/gui/overlay/bookmarks/BookmarkOverlay;)Z",
			"onElementBookmarked(Lmezz/jei/gui/overlay/elements/IElement;Lmezz/jei/common/input/UserInput;Lmezz/jei/gui/overlay/bookmarks/BookmarkOverlay;)Z"
		},
		at = @At("HEAD"),
		cancellable = true,
		require = 1,
		allow = 1
	)
	private <T> void cg$blockGroupHeaderBookmarks(
		IElement<T> element,
		@Coerce IJeiUserInput input,
		BookmarkOverlay bookmarkOverlay,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		if (element.getTypedIngredient().getType() == GroupIcon.TYPE
			&& !JeiViewerAdapter.instance().bookmarkPolicy().canBookmarkGroupHeader()) {
			cir.setReturnValue(true);
		}
	}
}
