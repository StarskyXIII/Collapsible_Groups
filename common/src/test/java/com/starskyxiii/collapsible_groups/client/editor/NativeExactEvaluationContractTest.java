package com.starskyxiii.collapsible_groups.client.editor;

import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeExactEvaluationContractTest {
	@BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

	@Test void malformedExactAndWrongFormatCannotBroadenNotInRuntimeOrPreview() {
		ItemStack stone = new ItemStack(Items.STONE);
		var view = new ItemStackIngredientView(stone);
		List<GroupFilter> invalid = List.of(
			new GroupFilter.ExactStack(ItemDataPayload.nbt("{id:'minecraft:stone',Count:1b,tag:3}")),
			new GroupFilter.ExactStack(ItemDataPayload.nbt("{Count:1b}")),
			new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS,
				new JsonPrimitive("{id:'minecraft:stone',Count:1b}"))));
		for (GroupFilter leaf : invalid) {
			assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(leaf).evaluate(view));
			for (GroupFilter filter : List.of(Filters.not(leaf), Filters.not(Filters.any(leaf, Filters.itemId("minecraft:dirt"))),
				Filters.not(Filters.all(leaf, Filters.itemId("minecraft:stone"))))) {
				assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(filter).evaluate(view));
				assertTrue(new ExactItemPreviewIndex(List.of(stone)).resolve(filter, GroupItemSelector.exactDecodeContext()).isEmpty());
			}
			assertTrue(CompiledFilter.compile(Filters.not(Filters.all(leaf, Filters.itemId("minecraft:dirt")))).matches(view));
			assertEquals(List.of(stone), new ExactItemPreviewIndex(List.of(stone)).resolve(
				Filters.not(Filters.all(leaf, Filters.itemId("minecraft:dirt"))), GroupItemSelector.exactDecodeContext()));
		}
	}

	@Test void nativeTypedArraysPassCaptureDefinitionSaveReloadAndMatching() throws Exception {
		ItemStack source = new ItemStack(Items.STONE, 37);
		source.setTag(TagParser.parseTag("{b:1b,i:1,l:1L,bytes:[B;1b,2b],ints:[I;1,2],longs:[L;1L,2L]}"));
		source.getOrCreateTag().putString("label", "quoted \" text and \\ slash");
		GroupDefinition group = GroupDefinition.of("arrays", "Arrays", Filters.exactStack(source));
		GroupDefinition loaded = GroupConfig.fromJson(GroupConfig.toJson(group));
		assertNotNull(loaded);
		assertEquals(group, loaded);
		assertTrue(loaded.matches(source));
		ItemStack differentCount = source.copy();
		differentCount.setCount(1);
		assertTrue(loaded.matches(differentCount));
		ItemStack differentType = source.copy();
		differentType.getOrCreateTag().putInt("b", 1);
		assertFalse(loaded.matches(differentType));
		assertEquals(List.of(source), new ExactItemPreviewIndex(List.of(source, differentType)).resolve(
			loaded.filter(), GroupItemSelector.exactDecodeContext()));
	}

	@Test void missingCandidateNbtIsOrdinaryNoMatch() {
		var view = new ItemStackIngredientView(new ItemStack(Items.STONE));
		for (GroupFilter filter : List.of(Filters.nbt("{value:1b}"), Filters.nbtPath("value", "1b"))) {
			assertEquals(CompiledFilter.Evaluation.NO_MATCH, CompiledFilter.compile(filter).evaluate(view));
			assertEquals(CompiledFilter.Evaluation.MATCH, CompiledFilter.compile(Filters.not(filter)).evaluate(view));
		}
	}
}
