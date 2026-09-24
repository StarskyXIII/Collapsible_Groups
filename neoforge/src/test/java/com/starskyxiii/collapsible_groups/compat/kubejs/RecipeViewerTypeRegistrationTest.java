package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import dev.latvian.mods.kubejs.integration.emi.EMIAddInformationKubeEvent;
import dev.latvian.mods.kubejs.integration.emi.EMIRemoveEntriesKubeEvent;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.Lazy;
import dev.latvian.mods.rhino.BaseFunction;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RecipeViewerTypeRegistrationTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void registrationProducesReadableTypesWithoutInitializingLazyRegistry() {
		var previous = RecipeViewerEntryType.CUSTOM_TYPES;
		var identities = IngredientTypeIds.getAllIds();
		RecipeViewerEntryType.ALL_TYPES.forget();
		RecipeViewerEntryType.CUSTOM_TYPES = Lazy.of(() -> {
			throw new AssertionError("Registration must not expand the custom type registry");
		});
		try {
			var types = register(Set.of("mekanism", "productivebees"), List.of("other:type", "chemical"));
			assertEquals(List.of("mekanism:chemical", "chemical", "productivebees:bee", "bee", "other:type"),
				types.stream().map(type -> type.id).toList());
			for (var type : types) {
				assertComponent(type.entryType);
				assertComponent(type.predicateType);
				assertNull(type.baseClass);
			}
			assertNotNull(RecipeViewerEntryType.ITEM.entryType.type());
			assertNotNull(RecipeViewerEntryType.ITEM.predicateType.type());
			assertNotNull(RecipeViewerEntryType.FLUID.entryType.type());
			assertNotNull(RecipeViewerEntryType.FLUID.predicateType.type());
			assertEquals(identities, IngredientTypeIds.getAllIds());
		} finally {
			RecipeViewerEntryType.CUSTOM_TYPES = previous;
			RecipeViewerEntryType.ALL_TYPES.forget();
		}
	}

	@Test
	void registrationKeepsOptionalModGatingAndRuntimeDiscoveredTypes() {
		assertTrue(register(Set.of(), List.of()).isEmpty());
		assertEquals(List.of("other:type"), register(Set.of(), List.of("other:type"))
			.stream().map(type -> type.id).toList());
		assertEquals(List.of("mekanism:chemical", "chemical"), register(Set.of("mekanism"), List.of())
			.stream().map(type -> type.id).toList());
		assertEquals(List.of("productivebees:bee", "bee"), register(Set.of("productivebees"), List.of())
			.stream().map(type -> type.id).toList());
	}

	@Test
	void genericTypesRejectUnsupportedNativeConversionsAndSerialization() {
		for (var type : register(Set.of("mekanism", "productivebees"), List.of("other:type"))) {
			assertTrue(assertThrows(UnsupportedOperationException.class,
				() -> type.wrapEntry(null, "example:value")).getMessage().contains(type.id));
			assertTrue(assertThrows(UnsupportedOperationException.class,
				() -> type.wrapPredicate(null, "@example")).getMessage().contains(type.id));
			assertThrows(UnsupportedOperationException.class,
				() -> new EMIRemoveEntriesKubeEvent(type, null).remove(null, "@example"));
			assertThrows(UnsupportedOperationException.class,
				() -> new EMIAddInformationKubeEvent(type, null).add(null, "@example", List.of()));
			for (var component : List.of(type.entryType, type.predicateType)) {
				assertThrows(UnsupportedOperationException.class, () -> component.streamCodec().encode(null, null));
				assertThrows(UnsupportedOperationException.class, () -> component.streamCodec().decode(null));
			}
		}
	}

	@Test
	void registeredTargetsStillDispatchDeclarativeGenericGroups() {
		var handler = KubeJSCompatibility.groupEntries();
		for (var type : register(Set.of("mekanism", "productivebees"), List.of("other:type"))) {
			var previous = RecipeViewerEntryType.CUSTOM_TYPES;
			RecipeViewerEntryType.CUSTOM_TYPES = Lazy.of(() -> Map.of(type.id, type));
			handler.clear(ScriptType.CLIENT);
			try {
				handler.listenJava(ScriptType.CLIENT, type.id, event -> {
					var group = (JEIGenericGroupEntriesKubeEvent<?>) event;
					List<Object> filters = List.of("example:value", "@example", "#example:tag",
						List.of("example:first", "example:second"), Pattern.compile(".*"), new BaseFunction());
					for (int i = 0; i < filters.size(); i++) {
						group.group(null, filters.get(i), ResourceLocation.parse("test:group_" + i), Component.literal("Group"));
					}
					return null;
				});
				var event = new JEIGenericGroupEntriesKubeEvent<>(type.id, "client:generic:" + type.id);
				handler.post(ScriptType.CLIENT, type, event);
				var groups = event.collectedGroups();
				assertEquals(6, groups.size());
				assertEquals(List.of(new GroupFilter.Id(type.id, "example:value"),
					new GroupFilter.Namespace(type.id, "example"), new GroupFilter.Tag(type.id, "example:tag"),
					new GroupFilter.Any(List.of(new GroupFilter.Id(type.id, "example:first"),
						new GroupFilter.Id(type.id, "example:second")))),
					groups.subList(0, 4).stream().map(KubeJsLoweredGroup::filter).toList());
				assertTrue(groups.subList(4, 6).stream()
					.allMatch(group -> group.lowering().kind() == KubeJsLoweringResult.Kind.UNSUPPORTED));
			} finally {
				handler.clear(ScriptType.CLIENT);
				RecipeViewerEntryType.CUSTOM_TYPES = previous;
				RecipeViewerEntryType.ALL_TYPES.forget();
			}
		}
	}

	private static List<RecipeViewerEntryType> register(Set<String> mods, List<String> discoveredIds) {
		List<RecipeViewerEntryType> result = new ArrayList<>();
		CollapsibleGroupsKubeJSPlugin.registerRecipeViewerEntryTypes(result::add, mods::contains, discoveredIds);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static void assertComponent(RecipeViewerEntryType.Component<?> descriptor) {
		assertNotNull(descriptor);
		assertEquals(Object.class, descriptor.type().asClass());
		assertNotNull(descriptor.streamCodec());
		var empty = ((RecipeViewerEntryType.Component<Object>) descriptor).empty();
		assertTrue(empty.test(null));
		assertFalse(empty.test("example:value"));
	}
}
