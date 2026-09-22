package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorItemIndexTest {
	@BeforeAll static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test void overlapsKeepOriginalIdentitiesAndJeiOrder() {
		Map<TagKey<Item>, List<Holder<Item>>> original = new HashMap<>();
		BuiltInRegistries.ITEM.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
		var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse("test:preview_overlap"));
		var tags = new HashMap<>(original);
		tags.put(tag, List.of(Items.STONE.builtInRegistryHolder(), Items.DIRT.builtInRegistryHolder()));
		try {
			BuiltInRegistries.ITEM.bindTags(tags);
			ItemStack named = named("variant");
			ItemStack counted = named.copyWithCount(32);
			var items = List.of(new ItemStack(Items.DIRT), named, new ItemStack(Items.DIAMOND), counted,
				new ItemStack(Items.STONE));
			var draft = GroupFilterEditorDraft.empty();
			draft.itemTags().add(tag.location().toString());
			draft.explicitItemSelectors().add("minecraft:stone");
			draft.explicitItemSelectors().add(GroupItemSelector.exactSelector(named));
			var index = EditorItemIndex.build(items);
			assertReferences(reference(items, draft.toFilter().orElseThrow()), index.resolveDraft(draft));
			draft.itemTags().clear();
			draft.explicitItemSelectors().remove("minecraft:stone");
			assertReferences(List.of(named, counted), index.resolveDraft(draft));
			draft.explicitItemSelectors().add("stack:{broken");
			assertReferences(List.of(named, counted), index.resolveDraft(draft));
		} finally {
			BuiltInRegistries.ITEM.bindTags(original);
		}
	}

	@Test void hybridPreservedRulesStayCachedAcrossExactEdits() {
		var items = List.of(named("second"), new ItemStack(Items.DIAMOND), named("first"));
		var filter = new GroupFilter.Any(List.of(new GroupFilter.Namespace("item", "minecraft"),
			new GroupFilter.ExactStack(GroupItemSelector.exactSelector(items.getFirst()).substring(6))));
		var draft = GroupFilterEditorDraft.decode(filter).draft();
		var index = EditorItemIndex.build(items);
		var scans = new AtomicInteger();
		java.util.function.Function<List<GroupFilter>, List<ItemStack>> resolver = preserved -> {
			scans.incrementAndGet();
			return reference(items, new GroupFilter.Any(preserved));
		};
		assertReferences(reference(items, filter), index.resolveHybridDraft(draft, resolver));
		draft.explicitItemSelectors().clear();
		assertReferences(items, index.resolveHybridDraft(draft, resolver));
		assertEquals(1, scans.get());
	}

	@Test void removingOneOfThousandsOfExactSelectorsReusesLookups() {
		var items = new ArrayList<ItemStack>();
		var draft = GroupFilterEditorDraft.empty();
		for (int i = 0; i < 6322; i++) {
			ItemStack stack = named("variant " + i);
			items.add(stack);
			draft.explicitItemSelectors().add(GroupItemSelector.exactSelector(stack));
		}
		var index = EditorItemIndex.build(items);
		assertReferences(items, index.resolveDraft(draft));
		long decodes = index.exactDecodes();
		long comparisons = index.exactComparisons();
		assertEquals(items.size(), decodes);
		assertTrue(comparisons < items.size() * 2L);
		draft.explicitItemSelectors().remove(draft.explicitItemSelectors().iterator().next());
		assertReferences(items.subList(1, items.size()), index.resolveDraft(draft));
		assertEquals(decodes, index.exactDecodes());
		assertEquals(comparisons, index.exactComparisons());
	}

	private static ItemStack named(String name) {
		ItemStack stack = new ItemStack(Items.STONE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	private static List<ItemStack> reference(List<ItemStack> items, GroupFilter filter) {
		var compiled = CompiledFilter.compile(filter);
		return items.stream().filter(stack -> compiled.matches(new ItemStackIngredientView(stack))).toList();
	}

	private static void assertReferences(List<ItemStack> expected, List<ItemStack> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), actual.get(i));
	}
}
