package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.MATCH;
import static com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.NO_MATCH;
import static com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NbtCompiledFilterTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void compiledRulesMatchNativeNbtAndAbsenceIsNoMatch() {
		ItemStack tagged = new ItemStack(Items.STONE);
		CompoundTag tag = new CompoundTag();
		tag.putByte("value", (byte) 1);
		tagged.setTag(tag);
		ItemStack plain = new ItemStack(Items.STONE);

		assertEquals(MATCH, CompiledFilter.compile(new GroupFilter.Nbt("{value:1b}"))
			.evaluate(new ItemStackIngredientView(tagged)));
		assertEquals(NO_MATCH, CompiledFilter.compile(new GroupFilter.Nbt("{value:1b}"))
			.evaluate(new ItemStackIngredientView(plain)));
		assertEquals(MATCH, CompiledFilter.compile(new GroupFilter.NbtPath("value", "1b"))
			.evaluate(new ItemStackIngredientView(tagged)));
		assertEquals(NO_MATCH, CompiledFilter.compile(new GroupFilter.NbtPath("missing", "1b"))
			.evaluate(new ItemStackIngredientView(tagged)));
	}

	@Test
	void invalidProgrammaticRulesRemainUnavailableThroughNot() {
		ItemStackIngredientView view = new ItemStackIngredientView(new ItemStack(Items.STONE));
		for (GroupFilter invalid : java.util.List.of(
			new GroupFilter.Nbt("1b"),
			new GroupFilter.Nbt("{broken"),
			new GroupFilter.NbtPath("a[*]", "1b"),
			new GroupFilter.NbtPath("a", "broken trailing"))) {
			assertEquals(UNAVAILABLE, CompiledFilter.compile(invalid).evaluate(view));
			assertEquals(UNAVAILABLE, CompiledFilter.compile(new GroupFilter.Not(invalid)).evaluate(view));
		}
		for (GroupFilter foreign : java.util.List.of(
			new GroupFilter.HasComponent("minecraft:custom_data", "{value:1b}"),
			new GroupFilter.ComponentPath("minecraft:custom_data", "value", "1b"))) {
			assertEquals(UNAVAILABLE, CompiledFilter.compile(foreign).evaluate(view));
			assertEquals(UNAVAILABLE, CompiledFilter.compile(new GroupFilter.Not(foreign)).evaluate(view));
		}
	}
}
