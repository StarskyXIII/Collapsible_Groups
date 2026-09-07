package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

	@Test
	void missingTypeSurvivesDraftSaveAndReloadThenRecoversWithoutMatchingOtherTypes(@TempDir Path directory) throws Exception {
		String originalProperty = System.getProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
		String originalType = "example.discovery.RecoverableUid";
		String otherType = "example.discovery.OtherViewerUid";
		List<GroupFilter> filters = List.of(
			Filters.genericId(originalType, "test:oxygen"),
			Filters.genericTag(originalType, "test:clean"),
			Filters.genericNamespace(originalType, "test"),
			new GroupFilter.Not(Filters.genericId(originalType, "test:oxygen")));
		try {
			System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, directory.toString());
			for (int i = 0; i < filters.size(); i++) {
				JeiIngredientTypeDiscovery.discover(manager(List.of(type(originalType))));
				GroupDefinition original = new GroupDefinition("recovery_" + i, "Recovery", true, filters.get(i));
				var available = List.of(taggedView(originalType, "test:oxygen"), taggedView(originalType, "test:hydrogen"));
				long expected = i == 2 ? 2 : 1;
				assertEquals(expected, available.stream().filter(original.compiledFilter()::matches).count());
				GroupConfig.save(original);
				Path file = directory.resolve("collapsiblegroups/groups/" + original.id() + ".json");
				byte[] saved = Files.readAllBytes(file);

				JeiIngredientTypeDiscovery.discover(manager(List.of(type(otherType))));
				GroupDefinition missing = GroupConfig.fromJson(Files.readString(file));
				assertEquals(Set.of(originalType), JeiIngredientTypeDiscovery.unresolvedTypeIds(List.of(missing)));
				GroupFilter draft = GroupFilterRuleDraft.decode(missing.filter()).toFilter().orElseThrow();
				assertEquals(original.filter(), draft);
				assertArrayEquals(saved, Files.readAllBytes(file), "opening and decoding the draft must not write");
				for (String unrelated : List.of(otherType, "item", "fluid")) {
					assertFalse(missing.compiledFilter().matches(taggedView(unrelated, "test:oxygen")));
					assertFalse(missing.compiledFilter().matches(taggedView(unrelated, "test:hydrogen")));
				}
				GroupConfig.save(new GroupDefinition(missing.id(), "Saved while missing", true, draft));
				GroupDefinition reloaded = GroupConfig.load().stream().filter(g -> g.id().equals(original.id())).findFirst().orElseThrow();
				assertEquals(original.filter(), reloaded.filter());
				assertEquals("Saved while missing", reloaded.displayName().fallback());

				JeiIngredientTypeDiscovery.discover(manager(List.of(type(originalType))));
				assertTrue(JeiIngredientTypeDiscovery.unresolvedTypeIds(List.of(reloaded)).isEmpty());
				assertEquals(expected, available.stream().filter(reloaded.compiledFilter()::matches).count());
			}
		} finally {
			JeiIngredientTypeDiscovery.clearRuntimeTypes();
			if (originalProperty == null) System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
			else System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, originalProperty);
		}
	}

	private static IngredientView taggedView(String type, String id) {
		return new IngredientView() {
			@Override public String ingredientType() { return type; }
			@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
			@Override public boolean hasTag(ResourceLocation tagId) { return id.equals("test:oxygen") && tagId.toString().equals("test:clean"); }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
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
			@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
			@Override public boolean hasTag(ResourceLocation tagId) { return false; }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
	}
}
