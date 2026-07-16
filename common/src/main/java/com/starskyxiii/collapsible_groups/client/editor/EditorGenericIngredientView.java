package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import net.minecraft.network.chat.Component;

import java.util.Set;

/** Viewer-neutral editor data for an opaque non-item, non-fluid ingredient. */
public record EditorGenericIngredientView(
	String typeId,
	Object ingredient,
	Object presentationData,
	Component displayName,
	String resourceId,
	String identityValueId,
	Set<String> tagIds,
	IngredientSearchDocument searchDocument
) {}
