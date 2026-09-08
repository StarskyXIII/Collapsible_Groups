package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CGGroupsKubeEventTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rhinoCallbackBuildsCompositionAndPreservesSourceOrder() {
		CGGroupsKubeEvent event = evaluate("""
			event.source('pack:first', function(source) {
			  source.add('pack:mixed', 'Mixed', source.any([
			    source.itemId('minecraft:stone'),
			    source.fluidId('minecraft:water')
			  ]))
			})
			event.source('pack:second', function(source) {
			  source.itemTag('pack:logs', 'Logs', 'minecraft:logs')
			})
			""");

		assertEquals(List.of("client:cg:pack:first", "client:cg:pack:second"),
			event.sources().keySet().stream().toList());
		KubeJsLoweredGroup mixed = event.sources().get("client:cg:pack:first").getFirst();
		assertTrue(mixed.filter() instanceof com.starskyxiii.collapsible_groups.group.filter.GroupFilter.Any);
	}

	@Test
	void reversibleEncodingAvoidsSourceAndGroupCollisions() {
		CGGroupsKubeEvent event = evaluate("""
			event.source('a:b_c', function(source) {
			  source.item('a:b/c', 'One', 'minecraft:stone')
			  source.item('a:b_c', 'Two', 'minecraft:dirt')
			})
			event.source('a_b:c', function(source) {
			  source.item('a:b/c', 'Three', 'minecraft:granite')
			})
			""");

		List<String> ids = event.sources().values().stream().flatMap(List::stream)
			.map(KubeJsLoweredGroup::id).toList();
		assertEquals(3, ids.stream().distinct().count());
		assertNotEquals(ids.get(0), ids.get(1));
		assertNotEquals(ids.get(0), ids.get(2));
		assertTrue(ids.stream().noneMatch(id -> id.equals(KubeJsGroupIds.item("cg_61_623a63"))));
	}

	@Test
	void plainExactItemDoesNotMatchNamedVariant() {
		CGGroupSourceBuilder source = new CGGroupSourceBuilder("pack:exact", "client:cg:pack:exact");
		GroupFilter exact = source.exactItem(new ItemStack(Items.STONE));
		assertTrue(exact instanceof GroupFilter.ExactStack);
		CompiledFilter compiled = CompiledFilter.compile(exact);
		assertTrue(compiled.matches(new ItemStackIngredientView(new ItemStack(Items.STONE))));
		ItemStack named = new ItemStack(Items.STONE);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		assertFalse(compiled.matches(new ItemStackIngredientView(named)));
	}

	private static CGGroupsKubeEvent evaluate(String script) {
		Context context = new ContextFactory().enter();
		Scriptable scope = context.initStandardObjects();
		CGGroupsKubeEvent event = new CGGroupsKubeEvent();
		context.addToScope(scope, "event", event);
		context.evaluateString(scope, script, "cg-groups-test.js", 1, null);
		return event;
	}
}
