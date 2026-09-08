package com.starskyxiii.collapsible_groups.client.editor;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExactItemPreviewIndexTest {
	@Test void nonItemNegationDoesNotScanItemCandidates() {
		var index = new ExactItemPreviewIndex(List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIRT)));
		assertEquals(List.of(), index.resolve(new GroupFilter.Not(new GroupFilter.Tag("fluid", "test:missing")), context));
		assertEquals(0, index.leafEvaluations());
	}
	@Test void invalidIdKeepsReferenceParseFailure() {
		var filter = new GroupFilter.Id("item", "invalid ID");
		var index = new ExactItemPreviewIndex(List.of(new ItemStack(Items.STONE)));
		var expected = assertThrows(RuntimeException.class, () -> CompiledFilter.compile(filter));
		var actual = assertThrows(RuntimeException.class, () -> index.resolve(filter, context));
		assertEquals(expected.getClass(), actual.getClass());
	}
	@ParameterizedTest @ValueSource(ints = {1, 10, 100, 252, 1000})
	void ordinaryIdDraftsUseOrdinalsWithoutPerSelectorUniverseScans(int size) {
		List<ItemStack> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
			.map(ItemStack::new).toList();
		List<GroupFilter> leaves = items.stream().limit(size).map(stack -> (GroupFilter)
			new GroupFilter.Id("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())).toList();
		GroupFilter filter = GroupFilterEditorDraft.decode(new GroupFilter.Any(leaves)).draft().toFilter().orElseThrow();
		CompiledFilter oracle = CompiledFilter.compile(filter);
		List<ItemStack> expected = items.stream().filter(stack -> oracle.matches(new ItemStackIngredientView(stack))).toList();
		var index = new ExactItemPreviewIndex(items);
		assertEquals(expected, index.resolve(filter, context));
		assertEquals(expected, index.resolve(filter, context));
		assertEquals(0, index.leafEvaluations());
		assertEquals(2L * size, index.idLookups());
		assertEquals(items.size(), index.candidateViews());
	}

	@Test
	void idOptimizationPreservesVariantsOrderAndThreeValuedNestedRules() {
		var items = List.of(named("second"), new ItemStack(Items.OAK_PLANKS), named("first"));
		var index = new ExactItemPreviewIndex(items);
		GroupFilter id = new GroupFilter.Id("item", "minecraft:stone");
		GroupFilter missing = new GroupFilter.Id("item", "test:missing");
		GroupFilter fluid = new GroupFilter.Id("fluid", "minecraft:stone");
		GroupFilter unknown = new GroupFilter.Unsupported(new JsonObject(), "future");
		for (GroupFilter filter : List.of(id, missing, fluid, new GroupFilter.Id("unknown:type", "minecraft:stone"),
			new GroupFilter.Any(List.of()), new GroupFilter.All(List.of()),
			new GroupFilter.Any(List.of(id, id, unknown)), new GroupFilter.Not(new GroupFilter.Any(List.of(id, unknown))),
			new GroupFilter.All(List.of(id, unknown)), new GroupFilter.Any(List.of(fluid, exact(items.get(0)), id)),
			new GroupFilter.Not(new GroupFilter.All(List.of(id, new GroupFilter.Not(exact(items.get(0)))))))) {
			CompiledFilter oracle = CompiledFilter.compile(filter);
			assertEquals(items.stream().filter(stack -> oracle.matches(new ItemStackIngredientView(stack))).toList(),
				index.resolve(filter, context));
		}
	}
	private static GroupItemSelector.ExactDecodeContext context;

	@BeforeAll static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		context = GroupItemSelector.exactDecodeContext();
	}

	@ParameterizedTest @ValueSource(ints = {1, 10, 100, 446, 6165})
	void coldLookupAndWarmDraftKeepLinearWork(int size) {
		List<ItemStack> items = new ArrayList<>();
		List<GroupFilter> selectors = new ArrayList<>();
		for (int i = 0; i <= size; i++) {
			ItemStack stack = named("variant-" + i);
			items.add(stack);
			if (i < size) selectors.add(exact(stack));
		}
		items.add(new ItemStack(Items.OAK_PLANKS));
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(items);
		assertEquals(items.subList(0, size), index.resolve(new GroupFilter.Any(selectors), context));
		assertEquals(size, index.decodes());
		assertTrue(index.comparisons() <= size * 2L);
		long coldComparisons = index.comparisons();
		selectors.add(new GroupFilter.Id("item", "minecraft:oak_planks"));
		List<ItemStack> expected = new ArrayList<>(items.subList(0, size));
		expected.add(items.get(items.size() - 1));
		assertEquals(expected, index.resolve(new GroupFilter.Any(selectors), context));
		assertEquals(size, index.cacheHits());
		assertEquals(size, index.decodes());
		assertEquals(coldComparisons, index.comparisons());
		assertEquals(size, index.cachedSelectors());
		assertTrue(index.retainedBytes() < ExactItemPreviewIndex.MAX_BYTES);
	}

	@Test void collisionsCountDamageAndNestedUnavailableSemanticsMatchReference() {
		ItemStack first = named("first");
		ItemStack copied = first.copy();
		copied.setCount(64);
		ItemStack different = named("second");
		ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
		damaged.setDamageValue(7);
		List<ItemStack> items = List.of(first, copied, different, damaged, new ItemStack(Items.DIAMOND_SWORD));
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(items, ignored -> 0);
		GroupFilter unknown = new GroupFilter.Unsupported(new JsonObject(), "future");
		for (GroupFilter filter : List.of(exact(first), exact(damaged),
			new GroupFilter.Not(exact(first)), new GroupFilter.Not(unknown),
			new GroupFilter.Any(List.of(unknown, exact(first))),
			new GroupFilter.All(List.of(unknown, exact(first))),
			new GroupFilter.Not(new GroupFilter.All(List.of(unknown, exact(first)))),
			new GroupFilter.All(List.of(new GroupFilter.Not(exact(different)), new GroupFilter.Id("item", "minecraft:stone"))))) {
			CompiledFilter reference = CompiledFilter.compile(filter);
			assertEquals(items.stream().filter(stack -> reference.matches(new ItemStackIngredientView(stack))).toList(),
				index.resolve(filter, context));
		}
		assertEquals(64, copied.getCount());
		assertEquals(7, damaged.getDamageValue());
	}

	@Test void registryChangesInvalidateResultsAndFallbackFailureIsRetried() {
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(List.of(named("first")));
		GroupFilter broken = new GroupFilter.ExactStack("{}");
		var fallback = context(false, new Object(), context);
		index.resolve(broken, fallback);
		index.resolve(broken, fallback);
		assertEquals(2, index.decodes());
		assertEquals(0, index.cachedSelectors());
		index.resolve(broken, context);
		index.resolve(broken, context);
		assertEquals(3, index.decodes());
		assertEquals(1, index.cacheHits());
		index.resolve(broken, context(true, new Object(), context));
		assertEquals(4, index.decodes());
	}

	@Test void cacheEvictsOldSelectorsAtEntryLimit() {
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(List.of(named("first")));
		for (int i = 0; i <= ExactItemPreviewIndex.MAX_ENTRIES; i++) index.resolve(exact(named("variant-" + i)), context);
		assertEquals(ExactItemPreviewIndex.MAX_ENTRIES, index.cachedSelectors());
		long decoded = index.decodes();
		index.resolve(exact(named("variant-0")), context);
		assertEquals(decoded + 1, index.decodes());
		assertTrue(index.retainedBytes() <= ExactItemPreviewIndex.MAX_BYTES);
	}

	@Test void byteBudgetEvictsAndOversizedSelectorsBypassCache() {
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(List.of(named("first")));
		String encoded = exact(named("first")).encodedStack();
		for (int i = 0; i < 70; i++) index.resolve(new GroupFilter.ExactStack(encoded + " ".repeat(262144 + i)), context);
		assertTrue(index.cachedSelectors() < 70);
		assertTrue(index.retainedBytes() <= ExactItemPreviewIndex.MAX_BYTES);
		long before = index.retainedBytes();
		GroupFilter huge = new GroupFilter.ExactStack(encoded + " ".repeat((int) (ExactItemPreviewIndex.MAX_BYTES / 2)));
		long decoded = index.decodes();
		index.resolve(huge, context);
		index.resolve(huge, context);
		assertEquals(decoded + 2, index.decodes());
		assertEquals(before, index.retainedBytes());
	}

	@Test void effectiveDefaultsAndRealDraftSelectorsMatchWithProductionHash() {
		ItemStack stone = new ItemStack(Items.STONE);
		ExactItemPreviewIndex index = new ExactItemPreviewIndex(List.of(stone));
		GroupFilter legacy121 = new GroupFilter.ExactStack("{\"id\":\"minecraft:stone\",\"components\":{}}");
		assertEquals(List.of(), index.resolve(legacy121, context));
		GroupFilter encoded = exact(stone);
		assertEquals(List.of(stone), index.resolve(encoded, context));
		var draft = GroupFilterEditorDraft.decode(encoded).draft();
		assertEquals(List.of(stone), index.resolve(draft.toFilter().orElseThrow(), context));
	}

	private static ItemStack named(String name) {
		ItemStack stack = new ItemStack(Items.STONE);
		stack.setHoverName(Component.literal(name));
		return stack;
	}

	private static GroupFilter.ExactStack exact(ItemStack stack) {
		String selector = GroupItemSelector.tryVersionedExactSelector(stack).orElseThrow();
		return new GroupFilter.ExactStack(selector.substring("stack:".length()));
	}

	private static GroupItemSelector.ExactDecodeContext context(boolean live, Object identity,
		GroupItemSelector.ExactDecodeContext delegate) {
		return new GroupItemSelector.ExactDecodeContext(new com.starskyxiii.collapsible_groups.internal.version.data.ExactStackCodec.DecodeSnapshot<>() {
			@Override public boolean liveRegistry() { return live; }
			@Override public Object registryIdentity() { return identity; }
			@Override public java.util.Optional<ItemStack> decode(String encoded) {
				return delegate.snapshot().decode(encoded);
			}
		});
	}
}
