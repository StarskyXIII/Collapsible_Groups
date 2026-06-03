package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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
			String searchKey = (displayName.getString() + "|" + resourceId).toLowerCase(Locale.ROOT);
			result.add(new EditorFluidIngredientView(
				fluid,
				displayName,
				resourceId,
				searchKey,
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
		String normalizedQuery
	) {
		return entries.stream().filter(entry -> {
			if (hideUsed && !ownership.getOrDefault(entry, List.of()).isEmpty()) return false;
			return normalizedQuery.isBlank() || entry.searchKey().contains(normalizedQuery);
		}).toList();
	}

	static Map<EditorFluidIngredientView, List<String>> buildOwnership(
		List<EditorFluidIngredientView> entries,
		Map<String, String> groupNames,
		List<GroupDefinition> otherGroups,
		Map<String, Set<String>> fluidReverseIndex
	) {
		Map<EditorFluidIngredientView, List<String>> ownership = new IdentityHashMap<>();
		if (fluidReverseIndex != null) {
			for (EditorFluidIngredientView entry : entries) {
				Set<String> groupIds = fluidReverseIndex.getOrDefault(entry.resourceId(), Set.of());
				List<String> names = new ArrayList<>();
				for (String groupId : groupIds) {
					String name = groupNames.get(groupId);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) ownership.put(entry, List.copyOf(names));
			}
			return ownership;
		}

		for (GroupDefinition other : otherGroups) {
			String name = EditorGroupOwnershipHelper.displayName(other);
			for (EditorFluidIngredientView entry : entries) {
				if (GroupMatcher.matchesFluid(other, entry.ingredient())) {
					ownership.computeIfAbsent(entry, k -> new ArrayList<>()).add(name);
				}
			}
		}
		return ownership;
	}

	static List<Component> tooltipLines(EditorFluidIngredientView entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Component.literal(entry.resourceId()).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	static void render(GuiGraphics g, EditorFluidIngredientView entry, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		IIngredientType<?> fluidType = Services.PLATFORM.getJeiFluidType();
		if (runtime != null && fluidType != null) {
			renderWithJei(g, fluidType, entry.ingredient(), x, y);
			return;
		}

		ItemStack fallback = entry.fallbackBucket();
		if (fallback != null && !fallback.isEmpty()) {
			g.renderItem(fallback, x, y);
		}
	}

	static String dragKey(EditorFluidIngredientView entry) {
		return entry.resourceId();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void renderWithJei(GuiGraphics g, IIngredientType fluidType, Object ingredient, int x, int y) {
		var manager = JeiRuntimeHolder.get().getIngredientManager();
		IIngredientRenderer renderer = manager.getIngredientRenderer(fluidType);
		g.enableScissor(x, y, x + 16, y + 16);
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		renderer.render(g, ingredient);
		g.pose().popPose();
		g.disableScissor();
	}
}
