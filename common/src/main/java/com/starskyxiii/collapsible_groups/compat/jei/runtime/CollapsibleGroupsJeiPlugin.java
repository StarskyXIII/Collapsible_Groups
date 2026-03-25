package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.mojang.serialization.Codec;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIconHelper;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIconRenderer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;

/**
 * JEI plugin entry point for Collapsible Groups.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register the {@link GroupIcon} custom ingredient type
 *   <li>Capture {@link IJeiRuntime} for use by {@link GroupRegistry} and the generic ingredient system
 * </ul>
 */
@JeiPlugin
public class CollapsibleGroupsJeiPlugin implements IModPlugin {

	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.fromNamespaceAndPath("collapsible_groups", "jei_plugin");
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		registration.register(
			GroupIcon.TYPE,
			Collections.emptyList(),
			new GroupIconHelper(),
			new GroupIconRenderer(),
			Codec.unit(() -> new GroupIcon("", "", "", Collections.emptyList()))
		);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		JeiRuntimeHolder.set(jeiRuntime);
	}
}
