package com.starskyxiii.collapsible_groups.viewer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered item, fluid, and generic ingredient universe supplied by a viewer. */
public final class ViewerIngredientUniverse<E> {
	private final List<ViewerIngredient<E>> ordered;
	private final Map<ViewerIngredientIdentity, ViewerIngredient<E>> byIdentity;
	private IconIndex<E> iconIndex;

	public ViewerIngredientUniverse(List<ViewerIngredient<E>> ordered) {
		Map<ViewerIngredientIdentity, ViewerIngredient<E>> indexed = new LinkedHashMap<>();
		for (ViewerIngredient<E> ingredient : ordered) indexed.putIfAbsent(ingredient.identity(), ingredient);
		this.ordered = List.copyOf(indexed.values());
		this.byIdentity = Map.copyOf(indexed);
	}

	private synchronized IconIndex<E> iconIndex() {
		if (iconIndex != null) return iconIndex;
		Map<IconKey, ViewerIngredient<E>> byValue = new LinkedHashMap<>();
		Map<IconKey, ViewerIngredient<E>> byResource = new LinkedHashMap<>();
		for (ViewerIngredient<E> ingredient : ordered) {
			String type = ingredient.identity().typeId();
			byValue.putIfAbsent(new IconKey(type, ingredient.identity().valueId()), ingredient);
			var resource = ingredient.view().resourceLocation();
			if (resource != null) byResource.putIfAbsent(new IconKey(type, resource.toString()), ingredient);
		}
		iconIndex = new IconIndex<>(Map.copyOf(byValue), Map.copyOf(byResource));
		return iconIndex;
	}

	ViewerIngredient<E> findIcon(String type, String value) {
		IconKey key = new IconKey(type, value);
		IconIndex<E> index = iconIndex();
		ViewerIngredient<E> exact = index.byValue().get(key);
		return exact != null ? exact : index.byResource().get(key);
	}

	private record IconKey(String type, String value) {}
	private record IconIndex<E>(Map<IconKey, ViewerIngredient<E>> byValue,
		Map<IconKey, ViewerIngredient<E>> byResource) {}

	public List<ViewerIngredient<E>> ordered() {
		return ordered;
	}

	public Map<ViewerIngredientIdentity, ViewerIngredient<E>> byIdentity() {
		return byIdentity;
	}

	public List<ViewerIngredient<E>> items() {
		return ingredientsOfKind(ViewerIngredient.Kind.ITEM);
	}

	public List<ViewerIngredient<E>> fluids() {
		return ingredientsOfKind(ViewerIngredient.Kind.FLUID);
	}

	public List<ViewerIngredient<E>> generic() {
		return ingredientsOfKind(ViewerIngredient.Kind.GENERIC);
	}

	private List<ViewerIngredient<E>> ingredientsOfKind(ViewerIngredient.Kind kind) {
		return ordered.stream().filter(ingredient -> ingredient.kind() == kind).toList();
	}
}
