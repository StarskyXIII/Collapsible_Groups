package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.mojang.serialization.Codec;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

/**
 * Custom JEI ingredient type representing a collapsible group icon.
 * By registering this as a custom ingredient type, we gain full control
 * over the rendering pipeline ??no dependency on JEI's item/fluid renderers.
 *
 * <p>{@link GroupIconRenderer} displays up to 2 stacked ingredients.
 * Items are rendered via vanilla {@code GuiGraphicsExtractor.item()};
 * non-item types (fluids, generics) are delegated to JEI's own renderers.
 */
public final class GroupIcon {
	public static final IIngredientType<GroupIcon> TYPE = () -> GroupIcon.class;
	/** Serializes only the groupId; display name and ingredients are rebuilt at runtime. */
	public static final Codec<GroupIcon> CODEC = Codec.STRING.xmap(
		id -> new GroupIcon(id, "", id, Collections.emptyList()),
		GroupIcon::groupId
	);

	private final String groupId;
	private final String groupTranslationKey;
	private final String groupName;
	private final List<ITypedIngredient<?>> displayIngredients;

	public GroupIcon(String groupId, String groupTranslationKey, String groupName, List<ITypedIngredient<?>> displayIngredients) {
		this.groupId = groupId;
		this.groupTranslationKey = groupTranslationKey;
		this.groupName = groupName;
		this.displayIngredients = List.copyOf(displayIngredients);
	}

	public String groupId() { return groupId; }
	/** Returns the stable translation key for this group's name. */
	public String groupTranslationKey() { return groupTranslationKey; }
	/** Returns the fallback display name (plain text). */
	public String groupName() { return groupName; }
	public List<ITypedIngredient<?>> displayIngredients() { return displayIngredients; }
	public boolean isExpanded() { return GroupRegistry.isExpandedById(groupId); }

	/**
	 * Returns a {@link Component} for the group name using the full resolution chain:
	 * overlay ??Minecraft lang ??fallback.
	 */
	public Component displayNameComponent() {
		return new GroupDisplayName.Localized(groupTranslationKey, groupName).toComponent();
	}

	/**
	 * Returns the resolved display text for the current language:
	 * overlay ??Minecraft lang ??fallback.
	 */
	public String resolvedDisplayName() {
		return new GroupDisplayName.Localized(groupTranslationKey, groupName).resolveClientDisplayText();
	}
}
