package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGroupOwnershipHelper;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
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

	static List<EditorGenericIngredientView> buildViews(List<GenericIngredientRef> refs, String traceName) {
		long traceStart = PerformanceTrace.begin();
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null || refs.isEmpty()) return List.of();
		var manager = runtime.getIngredientManager();
		List<EditorGenericIngredientView> result = new ArrayList<>(refs.size());
		for (GenericIngredientRef ref : refs) {
			IIngredientType<Object> type = ref.type();
			IIngredientHelper<Object> helper = manager.getIngredientHelper(type);
			IIngredientRenderer<Object> renderer = manager.getIngredientRenderer(type);
			var resourceLocation = helper.getIdentifier(ref.ingredient());
			Object uid = helper.getUid(ref.ingredient(), UidContext.Ingredient);
			String resourceId = resourceLocation != null
				? resourceLocation.toString()
				: uid != null ? uid.toString() : helper.getErrorInfo(ref.ingredient());
			String identityValueId = uid == null ? resourceId : uid.toString();
			List<Component> tooltipLines = renderer.getTooltip(ref.ingredient(), TooltipFlag.Default.NORMAL);
			Component displayName = tooltipLines.isEmpty() ? Component.literal(resourceId) : tooltipLines.getFirst();
			Set<String> tagIds = helper.getTagStream(ref.ingredient())
				.map(Object::toString)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			String namespace = resourceLocation != null ? resourceLocation.getNamespace()
				: resourceId.contains(":") ? resourceId.substring(0, resourceId.indexOf(':')) : resourceId;
			IngredientSearchDocument searchDocument = IngredientSearchDocument.of(
				List.of(displayName.getString(), resourceId, ref.typeId()), List.of(namespace), tagIds);
			result.add(new EditorGenericIngredientView(ref.typeId(), ref.ingredient(),
				new JeiData(type, helper, renderer), displayName, resourceId, identityValueId,
				Set.copyOf(tagIds), searchDocument));
		}
		List<EditorGenericIngredientView> copy = List.copyOf(result);
		if (traceName != null && !traceName.isBlank()) {
			PerformanceTrace.logIfSlow(traceName, traceStart, 5,
				"refs=" + refs.size() + " views=" + copy.size());
		}
		return copy;
	}

	static List<EditorGenericIngredientView> filterViews(
		List<EditorGenericIngredientView> entries,
		Map<EditorGenericIngredientView, List<String>> ownership,
		boolean hideUsed,
		IngredientSearchQuery query
	) {
		return entries.stream().filter(entry -> {
			if (hideUsed && !ownership.getOrDefault(entry, List.of()).isEmpty()) return false;
			return query.matches(entry.searchDocument());
		}).toList();
	}

	static Map<EditorGenericIngredientView, List<String>> buildOwnership(
		List<EditorGenericIngredientView> entries,
		List<GroupDefinition> otherGroups
	) {
		Map<EditorGenericIngredientView, List<String>> ownership = new IdentityHashMap<>();
		for (EditorGenericIngredientView entry : entries) {
			List<String> names = matchingGroupNames(otherGroups, entry);
			if (!names.isEmpty()) ownership.put(entry, names);
		}
		return ownership;
	}

	static List<Component> tooltipLines(EditorGenericIngredientView entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Component.literal(entry.resourceId()).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal(entry.typeId()).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	static void render(GuiGraphicsExtractor g, EditorGenericIngredientView entry, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		data(entry).renderer().render(g, entry.ingredient());
		g.pose().popMatrix();

	}

	static String dragKey(EditorGenericIngredientView entry) {
		return entry.typeId() + "|" + entry.resourceId();
	}

	static String identityValueId(EditorGenericIngredientView entry) {
		return entry.identityValueId();
	}

	// Winner semantics: groups are priority-ordered, so the first match is the JEI
	// winner; the returned single-element list names it for the overlap tab/tooltip.
	private static List<String> matchingGroupNames(List<GroupDefinition> groups, EditorGenericIngredientView entry) {
		for (GroupDefinition group : groups) {
			if (GroupMatcher.matchesGeneric(group, entry.typeId(), entry.ingredient(), data(entry).helper())) {
				return List.of(EditorGroupOwnershipHelper.displayName(group));
			}
		}
		return List.of();
	}

	static IIngredientType<Object> type(EditorGenericIngredientView entry) {
		return data(entry).type();
	}

	private static JeiData data(EditorGenericIngredientView entry) {
		return (JeiData) entry.presentationData();
	}

	private record JeiData(IIngredientType<Object> type, IIngredientHelper<Object> helper,
	                       IIngredientRenderer<Object> renderer) {}
}
