package com.starskyxiii.collapsible_groups.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CollapsibleGroupsMixinPlugin implements IMixinConfigPlugin {
	private static final String JEI_MIXIN_CLASS = "com.starskyxiii.collapsible_groups.mixin.MixinIngredientFilter";
	private static final String JEI_FILTER_CLASS = "mezz.jei.gui.ingredients.IngredientFilter";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (JEI_MIXIN_CLASS.equals(mixinClassName)) {
			return isClassPresent(JEI_FILTER_CLASS);
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
