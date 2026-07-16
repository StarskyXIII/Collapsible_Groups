package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiIngredientTypeDiscoveryTest {
	@Test
	void enumeratesFakeTypesAndExcludesBuiltinsByTypeIdentity() {
		IIngredientType<Object> automatic = type("example.discovery.AutomaticType");
		JeiIngredientTypeDiscovery.DiscoveryReport report = JeiIngredientTypeDiscovery.discover(
			manager(List.of(VanillaTypes.ITEM_STACK, GroupIcon.TYPE, automatic)));

		assertEquals(1, report.canonicalTypes());
		assertSame(automatic, JeiIngredientTypes.get("example.discovery.AutomaticType"));
		assertEquals(IngredientTypeIds.RegistrationOrigin.DISCOVERED,
			IngredientTypeIds.getCanonicalOrigin("example.discovery.AutomaticType"));
	}

	@Test
	void aNewManagerGenerationReplacesDiscoveryOwnedTypeObjects() {
		String uid = "example.discovery.ReloadedType";
		IIngredientType<Object> firstGeneration = type(uid);
		IIngredientType<Object> secondGeneration = type(uid);
		JeiIngredientTypeDiscovery.discover(manager(List.of(firstGeneration)));

		JeiIngredientTypeDiscovery.discover(manager(List.of(secondGeneration)));

		assertSame(secondGeneration, JeiIngredientTypes.get(uid));
		assertNull(JeiIngredientTypes.getCanonicalId(firstGeneration));
	}

	@Test
	void explicitCanonicalWinsAndDiscoveredUidBecomesItsAlias() {
		IIngredientType<Object> explicit = type("example.discovery.ExplicitJeiUid");
		CGApi.registerIngredientType("test:explicit_discovery_type", explicit);
		GroupDefinition parsedBeforeDiscovery = new GroupDefinition(
			"late_alias_group", "Late alias", true,
			Filters.genericId("example.discovery.ExplicitJeiUid", "test:oxygen"));

		JeiIngredientTypeDiscovery.DiscoveryReport report =
			JeiIngredientTypeDiscovery.discover(manager(List.of(explicit)));

		assertEquals(0, report.canonicalTypes());
		assertEquals(1, report.aliases());
		assertEquals("test:explicit_discovery_type",
			IngredientTypeIds.getCanonicalId("example.discovery.ExplicitJeiUid"));
		assertSame(explicit, JeiIngredientTypes.get("example.discovery.ExplicitJeiUid"));
		assertEquals(IngredientTypeIds.RegistrationOrigin.EXPLICIT,
			IngredientTypeIds.getCanonicalOrigin("test:explicit_discovery_type"));
		assertEquals(IngredientTypeIds.RegistrationOrigin.EXPLICIT,
			JeiIngredientTypes.getRegistrationOrigin(explicit));
		assertEquals(IngredientTypeIds.RegistrationOrigin.DISCOVERED,
			IngredientTypeIds.getAliasOrigin("example.discovery.ExplicitJeiUid"));
		assertFalse(JeiIngredientTypeDiscovery.unresolvedTypeIds(List.of(parsedBeforeDiscovery))
			.contains("example.discovery.ExplicitJeiUid"));
		assertTrue(parsedBeforeDiscovery.compiledFilter().matches(
			view("test:explicit_discovery_type", "test:oxygen")));
	}

	@Test
	void laterExplicitRegistrationPromotesOverDiscoveredCanonical() {
		IIngredientType<Object> type = type("example.discovery.PromotedUid");
		JeiIngredientTypeDiscovery.discover(manager(List.of(type)));

		CGApi.registerIngredientType("test:promoted_explicit_type", type);

		assertEquals("test:promoted_explicit_type", JeiIngredientTypes.getCanonicalId(type));
		assertEquals("test:promoted_explicit_type",
			IngredientTypeIds.getCanonicalId("example.discovery.PromotedUid"));
		assertSame(type, JeiIngredientTypes.get("example.discovery.PromotedUid"));
		assertEquals(IngredientTypeIds.RegistrationOrigin.EXPLICIT,
			JeiIngredientTypes.getRegistrationOrigin(type));
	}

	@Test
	void collisionBlankNullReservedAndDuplicateUidsNeverOverwrite() {
		IIngredientType<Object> winner = type("example.discovery.CollisionUid");
		IIngredientType<Object> collision = type("example.discovery.CollisionUid");
		IIngredientType<Object> blank = type(" ");
		IIngredientType<Object> nullUid = type(null);
		IIngredientType<Object> reserved = type("item");

		JeiIngredientTypeDiscovery.DiscoveryReport report = JeiIngredientTypeDiscovery.discover(
			manager(List.of(winner, winner, collision, blank, nullUid, reserved)));

		assertSame(winner, JeiIngredientTypes.get("example.discovery.CollisionUid"));
		assertNull(JeiIngredientTypes.getCanonicalId(collision));
		assertEquals(4, report.skipped());
	}

	@Test
	void aPreexistingStringRegistrationAlsoBlocksDiscoveryOverwrite() {
		String uid = "example.discovery.NeutralRegistryCollision";
		IngredientTypeIds.registerCanonical(uid);
		IIngredientType<Object> candidate = type(uid);

		JeiIngredientTypeDiscovery.DiscoveryReport report =
			JeiIngredientTypeDiscovery.discover(manager(List.of(candidate)));

		assertEquals(1, report.skipped());
		assertNull(JeiIngredientTypes.getCanonicalId(candidate));
		assertNull(JeiIngredientTypes.get(uid));
	}

	@Test
	void uidDriftLeavesConfigUntouchedAndDegradesToNoMatches() {
		String oldUid = "example.discovery.OldUid";
		IIngredientType<Object> replacement = type("example.discovery.NewUid");
		GroupDefinition group = new GroupDefinition("uid_drift_group", "UID drift", true,
			Filters.genericId(oldUid, "test:oxygen"));

		JeiIngredientTypeDiscovery.discover(manager(List.of(replacement)));

		assertEquals(Set.of(oldUid), JeiIngredientTypeDiscovery.unresolvedTypeIds(List.of(group)));
		assertEquals(1, JeiIngredientTypeDiscovery.warnUnresolvedTypesAfterBootstrap(List.of(group)));
		assertEquals(0, JeiIngredientTypeDiscovery.warnUnresolvedTypesAfterBootstrap(List.of(group)));
		assertEquals(oldUid, ((com.starskyxiii.collapsible_groups.group.filter.GroupFilter.Id) group.filter())
			.ingredientType());
		assertFalse(group.compiledFilter().matches(view("example.discovery.NewUid", "test:oxygen")));
	}

	private static IIngredientType<Object> type(String uid) {
		return new IIngredientType<>() {
			@Override public Class<? extends Object> getIngredientClass() { return Object.class; }
			@Override public String getUid() { return uid; }
		};
	}

	private static IIngredientManager manager(Collection<IIngredientType<?>> types) {
		return (IIngredientManager) Proxy.newProxyInstance(
			JeiIngredientTypeDiscoveryTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getRegisteredIngredientTypes")) return types;
				throw new UnsupportedOperationException(method.toString());
			}
		);
	}

	private static IngredientView view(String type, String id) {
		return new IngredientView() {
			@Override public String ingredientType() { return type; }
			@Override public Identifier resourceLocation() { return Identifier.parse(id); }
			@Override public boolean hasTag(Identifier tagId) { return false; }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
	}
}
