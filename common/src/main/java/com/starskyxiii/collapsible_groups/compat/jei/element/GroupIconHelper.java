package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * JEI ingredient helper for {@link GroupIcon}.
 * Provides identity, display name, and copy semantics required by JEI's
 * ingredient registration system.
 */
public final class GroupIconHelper implements IIngredientHelper<GroupIcon> {

	@Override
	public IIngredientType<GroupIcon> getIngredientType() {
		return GroupIcon.TYPE;
	}

	@Override
	public String getDisplayName(GroupIcon ingredient) {
		return ingredient.resolvedDisplayName();
	}

	@Override
	public Object getUid(GroupIcon ingredient, UidContext context) {
		return ingredient.groupId();
	}

	@Override
	public Identifier getIdentifier(GroupIcon ingredient) {
		return Identifier.fromNamespaceAndPath("collapsible_groups", sanitizePath(ingredient.groupId()));
	}

	@Override
	public GroupIcon copyIngredient(GroupIcon ingredient) {
		// GroupIcon is effectively immutable - safe to return the same instance.
		return ingredient;
	}

	@Override
	public String getErrorInfo(@Nullable GroupIcon ingredient) {
		return ingredient != null
			? "GroupIcon[" + ingredient.groupId() + "]"
			: "GroupIcon[null]";
	}

	@Override
	public boolean isValidIngredient(GroupIcon ingredient) {
		return true;
	}

	private static String sanitizePath(String groupId) {
		String normalized = groupId.toLowerCase(Locale.ROOT);
		StringBuilder path = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++) {
			char c = normalized.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
				path.append(c);
			} else {
				path.append('_');
			}
		}
		return path.isEmpty() ? "group" : path.toString();
	}
}
