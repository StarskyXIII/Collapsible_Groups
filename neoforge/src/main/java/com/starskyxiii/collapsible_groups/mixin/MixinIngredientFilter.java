package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.element.FluidChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientFilterController;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
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

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$controller = new JeiIngredientFilterController(
			this.filterTextSource::getFilterText, this::cg$getIngredientListUncached,
			this::cg$notifyListenersOfChange, this.ingredientManager,
			() -> this.ingredientListCached, value -> this.ingredientListCached = value,
			new JeiIngredientFilterController.PlatformHooks() {
				@Override public Object fluidIngredient(ITypedIngredient<?> typed) {
					return typed.getIngredient(NeoForgeTypes.FLUID_STACK).orElse(null);
				}
				@Override public Object previewFluid(ITypedIngredient<?> typed) {
					return typed.getIngredient() instanceof FluidStack fluid ? fluid : null;
				}
				@Override public boolean hasFluidType() { return true; }
				@Override public String fluidId(Object fluid) {
					return fluid instanceof FluidStack stack
						? BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString() : null;
				}
				@Override @SuppressWarnings("unchecked")
				public IElement<?> createFluidChild(ITypedIngredient<?> typed, String groupId) {
					return new FluidChildElement((ITypedIngredient<FluidStack>) typed, groupId);
				}
				@Override public JeiIngredientFilterController.GenericProbe genericProbe() {
					return JeiIngredientFilterController.exactGenericProbe();
				}
				@Override public JeiIngredientFilterController.FluidCachePolicy fluidCachePolicy() {
					return JeiIngredientFilterController.FluidCachePolicy.WITH_ITEMS;
				}
				@Override public boolean canIndexGeneric(IIngredientManager manager) { return manager != null; }
				@Override public boolean probeFluidWithoutGroups() { return true; }
				@Override public boolean beforeIndex(List<ITypedIngredient<?>> all, IIngredientManager manager) {
					JeiViewerAdapter.instance().updateBootstrap(all, manager);
					if (GroupRegistry.isKubeJsApplied() || !ModList.get().isLoaded("kubejs")) return false;
					com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSGroupBridge.applyGroups(
						JeiViewerAdapter.instance().bootstrapContext());
					GroupRegistry.markKubeJsApplied();
					return true;
				}
				@Override public boolean traceBuilds() { return true; }
			});
		this.cg$controller.initialize();
	}

	@Inject(method = "getElements", at = @At("HEAD"), cancellable = true, require = 0)
	private void cg$onGetElements(CallbackInfoReturnable<List<IElement<?>>> cir) {
		cir.setReturnValue(this.cg$controller.getElements());
	}
}
