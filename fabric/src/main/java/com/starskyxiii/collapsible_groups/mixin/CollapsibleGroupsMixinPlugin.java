package com.starskyxiii.collapsible_groups.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CollapsibleGroupsMixinPlugin implements IMixinConfigPlugin {
	private static final Set<String> OPTIONAL_JEI_MIXINS = Set.of(
		"com.starskyxiii.collapsible_groups.mixin.MixinIngredientFilter",
		"com.starskyxiii.collapsible_groups.mixin.MixinBookmarkList"
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
		if (OPTIONAL_JEI_MIXINS.contains(mixinClassName)) {
			return isClassPresent(targetClassName);
		}
		return true;
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
