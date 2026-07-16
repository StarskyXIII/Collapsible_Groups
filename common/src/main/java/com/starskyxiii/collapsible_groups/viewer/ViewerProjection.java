package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Structured group projection ready for translation into viewer-specific elements. */
public record ViewerProjection<E>(
	List<Entry<E>> entries,
	Map<ViewerIngredientIdentity, String> ownership
) {
	public ViewerProjection {
		entries = List.copyOf(entries);
		ownership = Map.copyOf(ownership);
	}

	public List<DisplayEntry<E>> displayEntries() {
		List<DisplayEntry<E>> result = new ArrayList<>();
		for (Entry<E> entry : entries) {
			switch (entry) {
				case IngredientEntry<E> ingredient -> result.add(
					new DisplayIngredient<>(ingredient.ingredient(), Optional.empty())
				);
				case GroupHeader<E> header -> {
					result.add(new DisplayHeader<>(header));
					if (header.expanded()) {
						for (ViewerIngredient<E> child : header.children()) {
							result.add(new DisplayIngredient<>(child, Optional.of(header.group().id())));
						}
					}
				}
			}
		}
		return List.copyOf(result);
	}

	public ViewerProjection<E> withExpansion(GroupExpansionState expansionState) {
		List<Entry<E>> updated = new ArrayList<>(entries.size());
		for (Entry<E> entry : entries) {
			updated.add(switch (entry) {
				case IngredientEntry<E> ingredient -> ingredient;
				case GroupHeader<E> header -> new GroupHeader<>(
					header.group(),
					header.children(),
					header.itemCount(),
					header.fluidCount(),
					header.genericCount(),
					expansionState.isExpanded(header.group().id()),
					header.iconIds(),
					header.fallbackIconIngredients()
				);
			});
		}
		return new ViewerProjection<>(updated, ownership);
	}

	public sealed interface Entry<E> permits IngredientEntry, GroupHeader {}

	public record IngredientEntry<E>(ViewerIngredient<E> ingredient) implements Entry<E> {
		public IngredientEntry {
			Objects.requireNonNull(ingredient, "ingredient");
		}
	}

	public record GroupHeader<E>(
		GroupDefinition group,
		List<ViewerIngredient<E>> children,
		int itemCount,
		int fluidCount,
		int genericCount,
		boolean expanded,
		List<String> iconIds,
		List<ViewerIngredient<E>> fallbackIconIngredients
	) implements Entry<E> {
		public GroupHeader {
			Objects.requireNonNull(group, "group");
			children = List.copyOf(children);
			iconIds = List.copyOf(iconIds);
			fallbackIconIngredients = List.copyOf(fallbackIconIngredients);
			if (itemCount + fluidCount + genericCount != children.size()) {
				throw new IllegalArgumentException("Ingredient counts must equal child count");
			}
		}
	}

	public sealed interface DisplayEntry<E> permits DisplayIngredient, DisplayHeader {}

	public record DisplayIngredient<E>(
		ViewerIngredient<E> ingredient,
		Optional<String> parentGroupId
	) implements DisplayEntry<E> {
		public DisplayIngredient {
			Objects.requireNonNull(ingredient, "ingredient");
			Objects.requireNonNull(parentGroupId, "parentGroupId");
		}
	}

	public record DisplayHeader<E>(GroupHeader<E> header) implements DisplayEntry<E> {
		public DisplayHeader {
			Objects.requireNonNull(header, "header");
		}
	}
}
