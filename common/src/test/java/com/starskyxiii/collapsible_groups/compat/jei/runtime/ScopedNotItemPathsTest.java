package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.starskyxiii.collapsible_groups.client.editor.ExactItemPreviewIndex;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.*;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScopedNotItemPathsTest {
	private static GroupItemSelector.ExactDecodeContext context;

	@BeforeAll static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		context = new GroupItemSelector.ExactDecodeContext(registry.createSerializationContext(JsonOps.INSTANCE), true, registry);
	}

	@Test void previewPlannerAndDraftRoundTripsKeepScopedExactVariants() {
		ItemStack first = named("first");
		ItemStack second = named("second");
		ItemStack dirt = new ItemStack(Items.DIRT);
		List<ItemStack> items = List.of(first, dirt, second);
		GroupFilter water = new GroupFilter.Id("fluid", "minecraft:water");
		GroupFilter stone = new GroupFilter.Id("item", "minecraft:stone");
		GroupFilter exact = new GroupFilter.ExactStack(ItemStack.STRICT_SINGLE_ITEM_CODEC
			.encodeStart(context.ops(), first).getOrThrow().toString());
		GroupFilter unknown = new GroupFilter.Unsupported(new JsonObject(), "future");
		List<Case> cases = List.of(
			new Case(new GroupFilter.Not(water), List.of()),
			new Case(new GroupFilter.Not(new GroupFilter.Any(List.of(water, stone))), List.of(dirt)),
			new Case(new GroupFilter.Not(new GroupFilter.All(List.of(water, stone))), items),
			new Case(new GroupFilter.Not(exact), List.of(dirt, second)),
			new Case(new GroupFilter.Not(new GroupFilter.Not(exact)), List.of(first)),
			new Case(new GroupFilter.Not(new GroupFilter.Any(List.of(exact, water))), List.of(dirt, second)),
			new Case(new GroupFilter.All(List.of(stone, new GroupFilter.Not(exact))), List.of(second)),
			new Case(new GroupFilter.Not(new GroupFilter.All(List.of(stone, unknown))), List.of(dirt)),
			new Case(new GroupFilter.Not(new GroupFilter.Any(List.of(stone, unknown))), List.of()),
			new Case(new GroupFilter.Not(new GroupFilter.Not(unknown)), List.of()),
			new Case(new GroupFilter.Not(new GroupFilter.Not(new GroupFilter.Any(List.of(stone, unknown)))), List.of(first, second)));
		List<ITypedIngredient<?>> typed = items.stream().map(ScopedNotItemPathsTest::typed).toList();
		var jeiIndex = IngredientFilterItemIndex.build(typed);
		var preview = new ExactItemPreviewIndex(items);
		for (Case entry : cases) {
			GroupDefinition definition = new GroupDefinition("audit", "Audit", true, entry.filter());
			assertEquals(entry.expected(), items.stream().filter(definition::matchesIgnoringEnabled).toList(), entry.filter().toString());
			for (int warm = 0; warm < 2; warm++) assertEquals(entry.expected(), preview.resolve(entry.filter(), context), entry.filter().toString());
			var replacementContext = new GroupItemSelector.ExactDecodeContext(context.ops(), true, new Object());
			assertEquals(entry.expected(), preview.resolve(entry.filter(), replacementContext));
			var plan = ItemFilterQueryCompiler.compile(entry.filter());
			List<IngredientFilterItemIndex.ItemEntry> candidates = switch (plan) {
				case ItemFilterQueryCompiler.EmptyPlan ignored -> List.of();
				case ItemFilterQueryCompiler.CandidatePlan candidate -> candidate.collectCandidates(jeiIndex);
				default -> jeiIndex.orderedEntries();
			};
			assertEquals(entry.expected(), candidates.stream().map(IngredientFilterItemIndex.ItemEntry::stack)
				.filter(definition::matchesIgnoringEnabled).toList(), "JEI " + entry.filter());
			if (!FilterNodeCapabilities.containsUnavailable(entry.filter())) {
				GroupFilter rules = GroupFilterRuleDraft.decode(entry.filter()).toFilter().orElseThrow();
				GroupFilter contents = GroupFilterEditorDraft.decode(rules).draft().toFilter().orElseThrow();
				assertEquals(entry.expected(), preview.resolve(rules, context), "rules " + entry.filter());
				assertEquals(entry.expected(), preview.resolve(contents, context), "contents " + entry.filter());
			}
		}
	}

	@Test void emptyScopeStaysUnavailableThroughBitsetCompositionAndDiagnostics() {
		ItemStack stone = new ItemStack(Items.STONE);
		var preview = new ExactItemPreviewIndex(List.of(stone));
		for (GroupFilter empty : List.of(new GroupFilter.Any(List.of()), new GroupFilter.All(List.of()))) {
			GroupFilter nested = new GroupFilter.Not(new GroupFilter.Not(empty));
			assertEquals(List.of(), preview.resolve(nested, context));
			assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(nested).evaluate(new ItemStackIngredientView(stone)));
			assertFalse(GroupFilterValidator.validate(nested).isEmpty());
		}
		GroupFilter opaque = new GroupFilter.Not(new GroupFilter.Unsupported(new JsonObject(), "future"));
		assertTrue(FilterNodeCapabilities.containsUnavailable(opaque));
		assertEquals(List.of("future"), FilterNodeCapabilities.unavailableKinds(opaque));
	}

	@Test void outOfItemDomainHasNoPreviewOrPlannerCandidates() {
		var preview = new ExactItemPreviewIndex(List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIRT)));
		GroupFilter filter = new GroupFilter.Not(new GroupFilter.Tag("fluid", "test:unavailable"));
		assertEquals(List.of(), preview.resolve(filter, context));
		assertInstanceOf(ItemFilterQueryCompiler.EmptyPlan.class, ItemFilterQueryCompiler.compile(filter));
	}

	private static ITypedIngredient<?> typed(ItemStack stack) {
		return new ITypedIngredient<ItemStack>() {
			public IIngredientType<ItemStack> getType() { return VanillaTypes.ITEM_STACK; }
			public ItemStack getIngredient() { return stack; }
			public ITypedIngredient<ItemStack> normalize(IIngredientHelper<ItemStack> helper) { return this; }
		};
	}

	private static ItemStack named(String name) {
		ItemStack stack = new ItemStack(Items.STONE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	private record Case(GroupFilter filter, List<ItemStack> expected) {}
}
