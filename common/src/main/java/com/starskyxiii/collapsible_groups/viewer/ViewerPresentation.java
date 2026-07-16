package com.starskyxiii.collapsible_groups.viewer;

import java.util.List;

/** Rendering and tooltip delegation for opaque viewer entries and projected headers. */
public interface ViewerPresentation<E, T> {
	void renderIngredient(ViewerIngredient<E> ingredient, RenderContext context);

	void renderHeader(ViewerProjection.GroupHeader<E> header, RenderContext context);

	List<T> ingredientTooltip(ViewerIngredient<E> ingredient, TooltipContext context);

	List<T> headerTooltip(ViewerProjection.GroupHeader<E> header, TooltipContext context);

	record RenderContext(Object drawingContext, int x, int y, int width, int height, float partialTick) {}

	record TooltipContext(Object tooltipContext, int mouseX, int mouseY) {}
}
