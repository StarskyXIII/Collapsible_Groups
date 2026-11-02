package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.element.FluidChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientFilterController;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientFilterHook;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class MixinIngredientFilter {
	@Shadow @Nullable private List<IElement<?>> ingredientListCached;
	@Shadow @Final private IFilterTextSource filterTextSource;
	@Shadow @Final private IIngredientManager ingredientManager;
	@Unique private JeiIngredientFilterController cg$controller;

	@org.spongepowered.asm.mixin.gen.Invoker("getIngredientListUncached")
	protected abstract Stream<ITypedIngredient<?>> cg$getIngredientListUncached(String filterText);
	@org.spongepowered.asm.mixin.gen.Invoker("notifyListenersOfChange")
	protected abstract void cg$notifyListenersOfChange();
	@org.spongepowered.asm.mixin.gen.Invoker("updateDirtyState")
	protected abstract void cg$updateDirtyState();

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void cg$onInit(CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		this.cg$controller = new JeiIngredientFilterController(
			this.filterTextSource::getFilterText, this::cg$getIngredientListUncached,
			this::cg$notifyListenersOfChange, this.ingredientManager,
			() -> this.ingredientListCached, value -> this.ingredientListCached = value,
			new JeiIngredientFilterController.PlatformHooks() {
				@Override public boolean hasFluidType() { return JeiIngredientTypes.getFluidType() != null; }
				@Override public IElement<?> createFluidChild(ITypedIngredient<?> typed, String groupId) {
					return new FluidChildElement(typed.cast(FabricTypes.FLUID_STACK), groupId);
				}
				@Override public JeiIngredientFilterController.GenericProbe genericProbe() {
					return JeiIngredientFilterController.castGenericProbe();
				}
				@Override public JeiIngredientFilterController.FluidCachePolicy fluidCachePolicy() {
					return JeiIngredientFilterController.FluidCachePolicy.INDEPENDENT;
				}
				@Override public boolean beforeIndex(JeiViewerAdapter.IngredientSourceSnapshot source, IIngredientManager manager) {
					return JeiViewerAdapter.instance().universeReady(source, manager);
				}
			});
		this.cg$controller.initialize();
	}

	// These alter the empty-query source. Search-text invalidateCache deliberately is not a source change.
	// Mark pending changes at entry: optimized JEI methods may reuse local slot 0 before returning.
	// The controller consumes the flag only when getElements runs after JEI updates its dirty state.
	@Inject(method = {"addIngredient", "onIngredientsAdded", "onIngredientsUpdated", "onIngredientsRemoved",
		"onIngredientVisibilityChanged", "onIngredientsVisibilityChanged", "updateHidden",
		"markSortIndexesDirty", "rebuildItemFilter"}, at = @At("HEAD"), require = 0)
	private void cg$onSourceChanged(CallbackInfo ci) {
		if (this.cg$controller != null) this.cg$controller.sourceChanged();
	}

	@Inject(method = "getElements", at = @At("HEAD"), cancellable = true, require = 0)
	private void cg$onGetElements(CallbackInfoReturnable<List<IElement<?>>> cir) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		cir.setReturnValue(JeiIngredientFilterHook.getElementsAfterDirtyStateUpdate(
			this::cg$updateDirtyState,
			this.cg$controller::getElements
		));
	}
}
