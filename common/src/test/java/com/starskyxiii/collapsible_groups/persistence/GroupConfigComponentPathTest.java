package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-oriented tests for {@link GroupConfig} filter parsing and serialization,
 * specifically covering the {@code ComponentPath} / {@code HasComponent} discriminator
 * and round-trip behavior.
 *
 * <p>These tests call package-private {@code parseFilter} and {@code serializeFilter}
 * directly, which are pure-Java (Gson only) and do not require Minecraft bootstrap.
 */
class GroupConfigComponentPathTest {

	// -----------------------------------------------------------------------
	// parseFilter: discriminator behavior
	// -----------------------------------------------------------------------

	@Nested
	class ParseFilterDiscriminator {

		@Test
		void componentOnlyDeserializesAsHasComponent() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "apotheosis:gem",
					"value": "apotheosis:core/ballast"
				}
				""").getAsJsonObject();

			GroupFilter result = GroupConfig.parseFilter(node);

			assertInstanceOf(GroupFilter.HasComponent.class, result);
			GroupFilter.HasComponent hc = (GroupFilter.HasComponent) result;
			assertEquals("apotheosis:gem", hc.componentTypeId());
			assertEquals("apotheosis:core/ballast", hc.encodedValue());
		}

		@Test
		void componentWithPathDeserializesAsComponentPath() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "irons_spellbooks:spell_container",
					"path": "data[0].id",
					"value": "irons_spellbooks:blood_needles"
				}
				""").getAsJsonObject();

			GroupFilter result = GroupConfig.parseFilter(node);

			assertInstanceOf(GroupFilter.ComponentPath.class, result);
			GroupFilter.ComponentPath cp = (GroupFilter.ComponentPath) result;
			assertEquals("irons_spellbooks:spell_container", cp.componentTypeId());
			assertEquals("data[0].id", cp.path());
			assertEquals("irons_spellbooks:blood_needles", cp.expectedValue());
		}

		@Test
		void invalidPathFailsFastInsteadOfFallingBackToHasComponent() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "irons_spellbooks:spell_container",
					"path": "data[*].id",
					"value": "irons_spellbooks:blood_needles"
				}
				""").getAsJsonObject();

			IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> GroupConfig.parseFilter(node)
			);
			assertTrue(ex.getMessage().contains("invalid path grammar"),
				"Error message should mention invalid path grammar, got: " + ex.getMessage());
		}

		@Test
		void invalidPathEmptyStringFailsFast() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "comp:id",
					"path": "",
					"value": "val"
				}
				""").getAsJsonObject();

			assertThrows(IllegalArgumentException.class, () -> GroupConfig.parseFilter(node));
		}

		@Test
		void invalidPathNegativeIndexFailsFast() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "comp:id",
					"path": "data[-1]",
					"value": "val"
				}
				""").getAsJsonObject();

			assertThrows(IllegalArgumentException.class, () -> GroupConfig.parseFilter(node));
		}

		@Test
		void invalidPathRecursiveDescentFailsFast() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "comp:id",
					"path": "data..id",
					"value": "val"
				}
				""").getAsJsonObject();

			assertThrows(IllegalArgumentException.class, () -> GroupConfig.parseFilter(node));
		}

		@Test
		void missingTypeFieldThrows() {
			JsonObject node = JsonParser.parseString("""
				{
					"component": "comp:id",
					"value": "val"
				}
				""").getAsJsonObject();

			assertThrows(IllegalArgumentException.class, () -> GroupConfig.parseFilter(node));
		}

		@Test
		void missingValueFieldThrows() {
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "comp:id",
					"path": "data[0].id"
				}
				""").getAsJsonObject();

			assertThrows(IllegalArgumentException.class, () -> GroupConfig.parseFilter(node));
		}
	}

	// -----------------------------------------------------------------------
	// serializeFilter: ComponentPath output shape
	// -----------------------------------------------------------------------

	@Nested
	class SerializeFilter {

		@Test
		void componentPathSerializesAllFields() {
			GroupFilter.ComponentPath cp = new GroupFilter.ComponentPath(
				"irons_spellbooks:spell_container",
				"data[0].id",
				"irons_spellbooks:blood_needles"
			);

			JsonObject json = GroupConfig.serializeFilter(cp);

			assertEquals("item", json.get("type").getAsString());
			assertEquals("irons_spellbooks:spell_container", json.get("component").getAsString());
			assertEquals("data[0].id", json.get("path").getAsString());
			assertEquals("irons_spellbooks:blood_needles", json.get("value").getAsString());
		}

		@Test
		void hasComponentSerializesWithoutPathField() {
			GroupFilter.HasComponent hc = new GroupFilter.HasComponent(
				"apotheosis:gem", "apotheosis:core/ballast"
			);

			JsonObject json = GroupConfig.serializeFilter(hc);

			assertEquals("item", json.get("type").getAsString());
			assertEquals("apotheosis:gem", json.get("component").getAsString());
			assertEquals("apotheosis:core/ballast", json.get("value").getAsString());
			assertFalse(json.has("path"), "HasComponent serialization must not include 'path' field");
		}
	}

	// -----------------------------------------------------------------------
	// Round-trip: serialize -> parse preserves all fields
	// -----------------------------------------------------------------------

	@Nested
	class RoundTrip {

		@Test
		void componentPathRoundTripPreservesPathAndValue() {
			GroupFilter.ComponentPath original = new GroupFilter.ComponentPath(
				"irons_spellbooks:spell_container",
				"data[0].id",
				"irons_spellbooks:blood_needles"
			);

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.ComponentPath.class, parsed);
			GroupFilter.ComponentPath roundTripped = (GroupFilter.ComponentPath) parsed;
			assertEquals(original.componentTypeId(), roundTripped.componentTypeId());
			assertEquals(original.path(), roundTripped.path());
			assertEquals(original.expectedValue(), roundTripped.expectedValue());
		}

		@Test
		void hasComponentRoundTripPreservesFields() {
			GroupFilter.HasComponent original = new GroupFilter.HasComponent(
				"apotheosis:gem", "apotheosis:core/ballast"
			);

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.HasComponent.class, parsed);
			GroupFilter.HasComponent roundTripped = (GroupFilter.HasComponent) parsed;
			assertEquals(original.componentTypeId(), roundTripped.componentTypeId());
			assertEquals(original.encodedValue(), roundTripped.encodedValue());
		}

		@Test
		void componentPathDoesNotRoundTripAsHasComponent() {
			GroupFilter.ComponentPath original = new GroupFilter.ComponentPath(
				"comp:id", "field", "val"
			);

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.ComponentPath.class, parsed,
				"ComponentPath must not silently degrade to HasComponent through round-trip");
		}

		@Test
		void hasComponentDoesNotRoundTripAsComponentPath() {
			GroupFilter.HasComponent original = new GroupFilter.HasComponent(
				"comp:id", "val"
			);

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.HasComponent.class, parsed,
				"HasComponent must not incorrectly upgrade to ComponentPath through round-trip");
		}

		@Test
		void componentPathRoundTripWithComplexPath() {
			GroupFilter.ComponentPath original = new GroupFilter.ComponentPath(
				"mod:component_type",
				"nested[2].deep_field[0].value",
				"expected_match"
			);

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.ComponentPath.class, parsed);
			GroupFilter.ComponentPath roundTripped = (GroupFilter.ComponentPath) parsed;
			assertEquals("nested[2].deep_field[0].value", roundTripped.path());
			assertEquals("expected_match", roundTripped.expectedValue());
		}
	}

	// -----------------------------------------------------------------------
	// HasComponent backward compatibility through parseFilter
	// -----------------------------------------------------------------------

	@Nested
	class HasComponentBackwardCompat {

		@Test
		void legacyHasComponentJsonStillParsesCorrectly() {
			// This mimics the JSON shape that existed before ComponentPath was added.
			// Must continue to parse as HasComponent without errors.
			JsonObject node = JsonParser.parseString("""
				{
					"type": "item",
					"component": "apotheosis:gem",
					"value": "apotheosis:core/ballast"
				}
				""").getAsJsonObject();

			GroupFilter result = GroupConfig.parseFilter(node);

			assertInstanceOf(GroupFilter.HasComponent.class, result);
		}

		@Test
		void hasComponentWithDifferentComponentIds() {
			// Verify backward compat with multiple real-world component IDs
			String[] componentIds = {
				"apotheosis:gem",
				"minecraft:custom_data",
				"some_mod:fancy_component"
			};

			for (String compId : componentIds) {
				JsonObject node = new JsonObject();
				node.addProperty("type", "item");
				node.addProperty("component", compId);
				node.addProperty("value", "test_value");

				GroupFilter result = GroupConfig.parseFilter(node);
				assertInstanceOf(GroupFilter.HasComponent.class, result,
					"Component '" + compId + "' without path must parse as HasComponent");
			}
		}
	}

	// -----------------------------------------------------------------------
	// Composite filter parsing with ComponentPath
	// -----------------------------------------------------------------------

	@Nested
	class CompositeFilters {

		@Test
		void allWithIdAndComponentPathParsesCorrectly() {
			JsonObject node = JsonParser.parseString("""
				{
					"all": [
						{ "type": "item", "id": "irons_spellbooks:scroll" },
						{
							"type": "item",
							"component": "irons_spellbooks:spell_container",
							"path": "data[0].id",
							"value": "irons_spellbooks:blood_needles"
						}
					]
				}
				""").getAsJsonObject();

			GroupFilter result = GroupConfig.parseFilter(node);

			assertInstanceOf(GroupFilter.All.class, result);
			GroupFilter.All all = (GroupFilter.All) result;
			assertEquals(2, all.children().size());
			assertInstanceOf(GroupFilter.Id.class, all.children().get(0));
			assertInstanceOf(GroupFilter.ComponentPath.class, all.children().get(1));
		}

		@Test
		void allWithIdAndComponentPathRoundTrips() {
			GroupFilter original = new GroupFilter.All(java.util.List.of(
				new GroupFilter.Id("item", "irons_spellbooks:scroll"),
				new GroupFilter.ComponentPath(
					"irons_spellbooks:spell_container",
					"data[0].id",
					"irons_spellbooks:blood_needles"
				)
			));

			JsonObject serialized = GroupConfig.serializeFilter(original);
			GroupFilter parsed = GroupConfig.parseFilter(serialized);

			assertInstanceOf(GroupFilter.All.class, parsed);
			GroupFilter.All all = (GroupFilter.All) parsed;
			assertEquals(2, all.children().size());
			assertInstanceOf(GroupFilter.Id.class, all.children().get(0));
			assertInstanceOf(GroupFilter.ComponentPath.class, all.children().get(1));

			GroupFilter.ComponentPath cp = (GroupFilter.ComponentPath) all.children().get(1);
			assertEquals("data[0].id", cp.path());
			assertEquals("irons_spellbooks:blood_needles", cp.expectedValue());
		}
	}
}
