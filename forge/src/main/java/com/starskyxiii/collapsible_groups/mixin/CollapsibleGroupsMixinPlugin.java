package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.viewer.LoaderViewerEnvironment;
import com.starskyxiii.collapsible_groups.viewer.ViewerCompatibilityEnvironment;
import com.starskyxiii.collapsible_groups.viewer.ViewerSelectionPolicy;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CollapsibleGroupsMixinPlugin implements IMixinConfigPlugin {
	private final ViewerCompatibilityEnvironment environment = LoaderViewerEnvironment.detectEarly();
	private static final Set<String> JEI_INTERNAL_MIXINS = Set.of(
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientFilter",
		"com.starskyxiii.collapsible_groups.mixin.MixinBookmarkList",
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientListOverlay",
		"com.starskyxiii.collapsible_groups.mixin.MixinGuiTextFieldFilterAccessor",
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientListRenderer"
	);
	private static final Set<String> EMI_INTERNAL_MIXINS = Set.of(
		"com.starskyxiii.collapsible_groups.mixin.MixinEmiScreenSpace",
		"com.starskyxiii.collapsible_groups.mixin.MixinEmiScreenManager"
	);
	private static boolean warnedMissingEmiTarget;

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (JEI_INTERNAL_MIXINS.contains(mixinClassName)) {
			return environment.mayApplyJeiInternals() && isClassPresent(targetClassName);
		}
		if (EMI_INTERNAL_MIXINS.contains(mixinClassName)) {
			return environment.selectedViewer() == ViewerSelectionPolicy.Viewer.EMI
				&& shouldApplyEmiTarget(targetClassName, mixinClassName);
		}
		return true;
	}

	private static boolean shouldApplyEmiTarget(String targetClassName, String mixinClassName) {
		boolean present = isClassPresent(targetClassName);
		if (!present && isClassPresent("dev.emi.emi.EmiPort") && !warnedMissingEmiTarget) {
			warnedMissingEmiTarget = true;
			System.err.println("[CollapsibleGroups] EMI is present but the pinned integration target is missing; "
				+ "EMI grouping is disabled (first failed mixin: " + mixinClassName + ").");
		}
		return present;
	}

	private static boolean isClassPresent(String className) {
		String resourcePath = className.replace('.', '/') + ".class";
		return CollapsibleGroupsMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
