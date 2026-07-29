package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGroupOwnershipHelper;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientRenderBridge;
import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorFluidIngredientHelper {
	private EditorFluidIngredientHelper() {}

	static List<EditorFluidIngredientView> buildViews(List<?> fluids, String traceName) {
		long traceStart = PerformanceTrace.begin();
		if (fluids.isEmpty()) return List.of();
		List<EditorFluidIngredientView> result = new ArrayList<>(fluids.size());
		for (Object fluid : fluids) {
			String resourceId = Services.PLATFORM.getFluidId(fluid);
			Component displayName = Services.PLATFORM.getFluidDisplayName(fluid);
			String namespace = resourceId.contains(":") ? resourceId.substring(0, resourceId.indexOf(':')) : resourceId;
			IngredientSearchDocument searchDocument = IngredientSearchDocument.of(
				List.of(displayName.getString(), resourceId), List.of(namespace), Set.of());
			result.add(new EditorFluidIngredientView(
				fluid,
				displayName,
				resourceId,
				searchDocument,
				Services.PLATFORM.getFluidFallbackBucket(fluid)));
		}
		List<EditorFluidIngredientView> copy = List.copyOf(result);
		if (traceName != null && !traceName.isBlank()) {
			PerformanceTrace.logIfSlow(traceName, traceStart, 5,
				"fluids=" + fluids.size() + " views=" + copy.size());
		}
		return copy;
	}

	static List<EditorFluidIngredientView> filterViews(
		List<EditorFluidIngredientView> entries,
		Map<EditorFluidIngredientView, List<String>> ownership,
		boolean hideUsed,
		IngredientSearchQuery query
	) {
		return entries.stream().filter(entry -> {
			if (hideUsed && !ownership.getOrDefault(entry, List.of()).isEmpty()) return false;
			return query.matches(entry.searchDocument());
		}).toList();
	}

	static Map<EditorFluidIngredientView, List<String>> buildOwnership(
		List<EditorFluidIngredientView> entries,
		Map<String, String> groupNames,
		List<GroupDefinition> otherGroups,
		Map<String, Set<String>> fluidReverseIndex
	) {
		// Winner semantics via EditorGroupOwnershipHelper.buildOwnership: both
		// branches key to the single JEI winner (reverseIndex is deduped;
		// otherGroups is priority-ordered so first-match is the winner).
		return EditorGroupOwnershipHelper.buildOwnership(entries, groupNames, otherGroups, fluidReverseIndex,
			EditorFluidIngredientView::resourceId,
			(group, entry) -> GroupMatcher.matchesFluid(group, entry.ingredient()),
			EditorGroupOwnershipHelper::displayName);
	}

	static List<Component> tooltipLines(EditorFluidIngredientView entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Component.literal(entry.resourceId()).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	static void render(GuiGraphicsExtractor g, EditorFluidIngredientView entry, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		if (runtime != null && fluidType != null) {
			renderWithJei(g, fluidType, entry.ingredient(), x, y);
			return;
		}

		ItemStack fallback = entry.fallbackBucket();
		if (fallback != null && !fallback.isEmpty()) {
			g.item(fallback, x, y);
		}
	}

	static String dragKey(EditorFluidIngredientView entry) {
		return entry.resourceId();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void renderWithJei(GuiGraphicsExtractor g, IIngredientType fluidType, Object ingredient, int x, int y) {
		var manager = JeiRuntimeHolder.get().getIngredientManager();
		IIngredientRenderer renderer = manager.getIngredientRenderer(fluidType);
		JeiIngredientRenderBridge.render(g, renderer, ingredient, x, y);
	}
}
