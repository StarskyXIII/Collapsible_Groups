package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.core.IngredientSearchQuery;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class EditorGenericIngredientHelper {
	private EditorGenericIngredientHelper() {}

	static List<GenericIngredientView> buildViews(List<GenericIngredientRef> refs, String traceName) {
		long traceStart = PerformanceTrace.begin();
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null || refs.isEmpty()) return List.of();
		var manager = runtime.getIngredientManager();
		List<GenericIngredientView> result = new ArrayList<>(refs.size());
		for (GenericIngredientRef ref : refs) {
			IIngredientType<Object> type = ref.type();
			IIngredientHelper<Object> helper = manager.getIngredientHelper(type);
			IIngredientRenderer<Object> renderer = manager.getIngredientRenderer(type);
			var resourceLocation = helper.getIdentifier(ref.ingredient());
			String resourceId = resourceLocation != null
				? resourceLocation.toString()
				: helper.getUid(ref.ingredient(), UidContext.Ingredient).toString();
			List<Component> tooltipLines = renderer.getTooltip(ref.ingredient(), TooltipFlag.Default.NORMAL);
			Component displayName = tooltipLines.isEmpty() ? Component.literal(resourceId) : tooltipLines.getFirst();
			Set<String> tagIds = helper.getTagStream(ref.ingredient())
				.map(Object::toString)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			String namespace = resourceLocation != null ? resourceLocation.getNamespace()
				: resourceId.contains(":") ? resourceId.substring(0, resourceId.indexOf(':')) : resourceId;
			IngredientSearchDocument searchDocument = IngredientSearchDocument.of(
				List.of(displayName.getString(), resourceId, ref.typeId()), List.of(namespace), tagIds);
			result.add(new GenericIngredientView(ref.typeId(), type, ref.ingredient(), helper, renderer,
				displayName, resourceId, Set.copyOf(tagIds), searchDocument));
		}
		List<GenericIngredientView> copy = List.copyOf(result);
		if (traceName != null && !traceName.isBlank()) {
			PerformanceTrace.logIfSlow(traceName, traceStart, 5,
				"refs=" + refs.size() + " views=" + copy.size());
		}
		return copy;
	}

	static List<GenericIngredientView> filterViews(
		List<GenericIngredientView> entries,
		Map<GenericIngredientView, List<String>> ownership,
		boolean hideUsed,
		IngredientSearchQuery query
	) {
		return entries.stream().filter(entry -> {
			if (hideUsed && !ownership.getOrDefault(entry, List.of()).isEmpty()) return false;
			return query.matches(entry.searchDocument());
		}).toList();
	}

	static Map<GenericIngredientView, List<String>> buildOwnership(
		List<GenericIngredientView> entries,
		List<GroupDefinition> otherGroups
	) {
		Map<GenericIngredientView, List<String>> ownership = new IdentityHashMap<>();
		for (GenericIngredientView entry : entries) {
			List<String> names = matchingGroupNames(otherGroups, entry);
			if (!names.isEmpty()) ownership.put(entry, names);
		}
		return ownership;
	}

	static List<Component> tooltipLines(GenericIngredientView entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Component.literal(entry.resourceId()).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal(entry.typeId()).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	static void render(GuiGraphicsExtractor g, GenericIngredientView entry, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		entry.renderer().render(g, entry.ingredient());
		g.pose().popMatrix();
	}

	static String dragKey(GenericIngredientView entry) {
		return entry.typeId() + "|" + entry.resourceId();
	}

	// Winner semantics: groups are priority-ordered, so the first match is the JEI
	// winner; the returned single-element list names it for the overlap tab/tooltip.
	private static List<String> matchingGroupNames(List<GroupDefinition> groups, GenericIngredientView entry) {
		for (GroupDefinition group : groups) {
			if (GroupMatcher.matchesGeneric(group, entry.typeId(), entry.ingredient(), entry.helper())) {
				return List.of(EditorGroupOwnershipHelper.displayName(group));
			}
		}
		return List.of();
	}
}
