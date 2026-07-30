package com.starskyxiii.collapsible_groups.mixin;

// Keep this mixin plugin byte-identical across the active Fabric and NeoForge loaders.

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CollapsibleGroupsMixinPlugin implements IMixinConfigPlugin {
	private static final String JEI_MARKER = "mezz/jei/api/JeiPlugin.class";
	private static final Set<String> JEI_MIXINS = Set.of(
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientFilter",
		"com.starskyxiii.collapsible_groups.mixin.MixinBookmarkList",
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientListRenderer",
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientListOverlay",
		"com.starskyxiii.collapsible_groups.mixin.MixinGuiTextFieldFilterAccessor"
	);

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (JEI_MIXINS.contains(mixinClassName)) {
			return isJeiPresent();
		}
		return true;
	}

	private static boolean isJeiPresent() {
		return CollapsibleGroupsMixinPlugin.class.getClassLoader().getResource(JEI_MARKER) != null;
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
