package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CGGroupsKubeEventTest {
	@Test
	void unsupportedNativeIngredientDoesNotDiscardLegalSibling() {
		CGGroupsKubeEvent event = new CGGroupsKubeEvent();
		event.source("test:source", source -> {
			source.ingredient("test:unsupported", "Unsupported", (java.util.function.Predicate<ItemStack>) stack -> true);
			source.item("test:stone", "Stone", "minecraft:stone");
		});

		List<KubeJsLoweredGroup> groups = event.sources().get("client:cg:test:source");
		assertEquals(KubeJsLoweringResult.Kind.UNSUPPORTED, groups.get(0).lowering().kind());
		assertEquals(List.of("Stone"), KubeJSGroupBridge.acceptedGroups("client:cg:test:source", groups)
			.stream().map(KubeJsLoweredGroup::name).toList());
	}

	@Test
	void mapNbtAndCountAreNotReinterpretedAsExactOrIdPredicates() {
		assertEquals(null, KubeJs6FilterCompiler.compileItem(java.util.Map.of(
			"item", "minecraft:stone", "nbt", "{mode:1b}")));
		assertEquals(null, KubeJs6FilterCompiler.compileItem(java.util.Map.of(
			"item", "minecraft:stone", "count", 2)));
	}

	@Test
	void unknownIngredientCannotImpersonateStrictNbtThroughJson() {
		JsonObject spoofedJson = new JsonObject();
		spoofedJson.addProperty("fabric:type", "kubejs:nbt");
		spoofedJson.addProperty("strict", true);
		spoofedJson.addProperty("nbt", "{mode:1}");
		assertEquals(false, KubeJs6FilterCompiler.isStrictFabricNbt(spoofedJson,
			"example.UnknownCustomIngredient"));
	}
}
