package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.ItemFilterQueryCompiler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NbtQueryPlanTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void nativeNbtLeavesRequireFullScan() {
		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class,
			ItemFilterQueryCompiler.compile(new GroupFilter.Nbt("{value:1b}")));
		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class,
			ItemFilterQueryCompiler.compile(new GroupFilter.NbtPath("value", "1b")));
		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class,
			ItemFilterQueryCompiler.compile(new GroupFilter.Any(List.of(
				new GroupFilter.Nbt("{}"), new GroupFilter.NbtPath("value", "1b")))));
	}

	@Test
	void idCanNarrowAllButCannotNarrowAnyContainingNbt() {
		GroupFilter.Id stone = new GroupFilter.Id("item", "minecraft:stone");
		GroupFilter.Nbt nbt = new GroupFilter.Nbt("{value:1b}");
		assertInstanceOf(ItemFilterQueryCompiler.CandidatePlan.class,
			ItemFilterQueryCompiler.compile(new GroupFilter.All(List.of(stone, nbt))));
		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class,
			ItemFilterQueryCompiler.compile(new GroupFilter.Any(List.of(stone, nbt))));
	}
}
