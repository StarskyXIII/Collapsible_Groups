package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Screen-scoped item search corpus shared by the editor list and reference picker. */
final class EditorItemSearchSession {
	@FunctionalInterface
	interface TooltipProvider {
		List<Component> get(ItemStack stack);
	}

	private final Map<ItemStack, IngredientSearchDocument> documents = new IdentityHashMap<>();
	private final Map<ItemStack, List<String>> tooltips = new IdentityHashMap<>();
	private final TooltipProvider tooltipProvider;

	EditorItemSearchSession() {
		this(EditorItemSearchSession::minecraftTooltip);
	}

	EditorItemSearchSession(TooltipProvider tooltipProvider) {
		this.tooltipProvider = tooltipProvider;
	}

	IngredientSearchDocument document(ItemStack stack) {
		return documents.computeIfAbsent(stack, EditorItemSearchHelper::document);
	}

	boolean matches(ItemStack stack, IngredientSearchQuery query) {
		return query.matches(document(stack), () -> tooltipLines(stack));
	}

	private List<String> tooltipLines(ItemStack stack) {
		List<String> cached = tooltips.get(stack);
		if (cached != null) return cached;
		List<String> normalized;
		try {
			normalized = tooltipProvider.get(stack).stream()
				.map(Component::getString)
				.map(ChatFormatting::stripFormatting)
				.map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
				.filter(value -> !value.isEmpty())
				.toList();
		} catch (RuntimeException | LinkageError ignored) {
			normalized = List.of();
		}
		tooltips.put(stack, normalized);
		return normalized;
	}

	private static List<Component> minecraftTooltip(ItemStack stack) {
		return EditorItemTooltipHelper.tooltipLines(stack, List.of());
	}
}
