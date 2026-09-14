package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = mezz.jei.gui.bookmarks.BookmarkList.class, remap = false)
public class MixinBookmarkList {
	@Group(name = "cg$blockGroupHeaderBookmarks", min = 1, max = 1)
	@Inject(
		method = "onElementBookmarked(Lmezz/jei/gui/overlay/elements/IElement;)Z",
		at = @At("HEAD"),
		cancellable = true,
		require = 0,
		expect = 0
	)
	private <T> void cg$blockGroupHeaderBookmarksLegacy(
		IElement<T> element,
		CallbackInfoReturnable<Boolean> cir
	) {
		this.cg$applyBookmarkPolicy(element, cir);
	}

	@Group(name = "cg$blockGroupHeaderBookmarks", min = 1, max = 1)
	@Inject(
		method = "onElementBookmarked(Lmezz/jei/gui/overlay/elements/IElement;Lmezz/jei/gui/input/UserInput;Lmezz/jei/gui/overlay/bookmarks/BookmarkOverlay;)Z",
		at = @At("HEAD"),
		cancellable = true,
		require = 0,
		expect = 0
	)
	private <T> void cg$blockGroupHeaderBookmarksCurrent(
		IElement<T> element,
		UserInput userInput,
		BookmarkOverlay bookmarkOverlay,
		CallbackInfoReturnable<Boolean> cir
	) {
		this.cg$applyBookmarkPolicy(element, cir);
	}

	@Unique
	private <T> void cg$applyBookmarkPolicy(IElement<T> element, CallbackInfoReturnable<Boolean> cir) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		if (element.getTypedIngredient().getType() == GroupIcon.TYPE
			&& !JeiViewerAdapter.instance().bookmarkPolicy().canBookmarkGroupHeader()) {
			cir.setReturnValue(true);
		}
	}
}
