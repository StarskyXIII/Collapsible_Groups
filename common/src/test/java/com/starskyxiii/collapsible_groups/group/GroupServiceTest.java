package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupServiceTest {
	@TempDir
	Path tempDir;

	@AfterEach
	void resetRepository() {
		GroupRepository.replaceForTesting(List.of());
		System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
	}

	@Test
	void replacingOneProducerDoesNotClearAnotherProducer() {
		GroupService service = new GroupService();
		GroupService.SourceKey first = new GroupService.SourceKey(GroupSource.KUBEJS, "first");
		GroupService.SourceKey second = new GroupService.SourceKey(GroupSource.KUBEJS, "second");
		service.replaceSource(first, List.of(group("first_old", 0)));
		service.replaceSource(second, List.of(group("second", 0)));

		service.replaceSource(first, List.of(group("first_new", 0)));

		assertEquals(List.of("first_new"), service.sourceGroups(first).stream().map(GroupDefinition::id).toList());
		assertEquals(List.of("second"), service.sourceGroups(second).stream().map(GroupDefinition::id).toList());
	}

	@Test
	void invalidReplacementLeavesThePublishedSnapshotUntouched() {
		GroupService service = new GroupService();
		GroupService.SourceKey source = new GroupService.SourceKey(GroupSource.KUBEJS, "script");
		GroupDefinition original = group("ordinary_script_id", 0);
		service.replaceSource(source, List.of(original));
		List<GroupDefinition> invalid = new ArrayList<>();
		invalid.add(group("replacement", 0));
		invalid.add(null);

		assertThrows(IllegalArgumentException.class, () -> service.replaceSource(source, invalid));

		assertEquals(List.of(original), service.sourceGroups(source));
		assertEquals(List.of(original), service.allPriorityOrder());
	}

	@Test
	void userSourceWinsAnOrdinaryIdCollisionWithoutChangingItsStableSlot() {
		GroupService service = new GroupService();
		GroupService.SourceKey script = new GroupService.SourceKey(GroupSource.KUBEJS, "script");
		GroupService.SourceKey user = new GroupService.SourceKey(GroupSource.USER, "persisted");
		GroupDefinition scripted = group("shared", 0);
		GroupDefinition scriptedTail = group("script_tail", 0);
		GroupDefinition persisted = group("shared", 0).withEnabled(false);
		service.replaceSource(script, List.of(scripted, scriptedTail));

		service.replaceSource(user, List.of(persisted));

		assertSame(persisted, service.findById("shared").orElseThrow());
		assertEquals(List.of("shared", "script_tail"),
			service.allPriorityOrder().stream().map(GroupDefinition::id).toList());
	}

	@Test
	void priorityOrderingIsDescendingAndStableAcrossSources() {
		GroupService service = new GroupService();
		service.replaceSource(new GroupService.SourceKey(GroupSource.USER, "persisted"),
			List.of(group("first_tie", 1), group("high", 5)));
		service.replaceSource(new GroupService.SourceKey(GroupSource.KUBEJS, "script"),
			List.of(group("second_tie", 1)));

		assertEquals(List.of("high", "first_tie", "second_tie"),
			service.allPriorityOrder().stream().map(GroupDefinition::id).toList());
	}

	@Test
	void managedCategoryOrderDoesNotDependOnSourcePublicationTiming() {
		GroupService service = new GroupService();
		service.replaceSource(new GroupService.SourceKey(GroupSource.KUBEJS, "script"),
			List.of(group("scripted", 0)));
		service.replaceSource(new GroupService.SourceKey(GroupSource.USER, "persisted"),
			List.of(group("user", 0)));
		service.replaceSource(new GroupService.SourceKey(GroupSource.BUILTIN, "providers"),
			List.of(group("builtin", 0)));

		assertEquals(List.of("builtin", "user", "scripted"),
			service.allPriorityOrder().stream().map(GroupDefinition::id).toList());
	}

	@Test
	void repositorySourceReplacePublishesExactlyOneEventAfterSuccess() {
		AtomicInteger events = new AtomicInteger();
		try (GroupChangeEvent.Subscription ignored = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.KUBEJS_REPLACE, events::incrementAndGet)) {
			GroupRepository.replaceScriptedSource("producer", List.of(group("ordinary_id", 0)), true);
		}

		assertEquals(1, events.get());
		assertTrue(GroupRepository.findById("ordinary_id").isPresent());
	}

	@Test
	void rejectedRepositoryReplacePublishesNoEventAndKeepsTheOldSource() {
		GroupDefinition original = group("original", 0);
		GroupRepository.replaceScriptedSource("producer", List.of(original), false);
		AtomicInteger events = new AtomicInteger();
		try (GroupChangeEvent.Subscription ignored = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.KUBEJS_REPLACE, events::incrementAndGet)) {
			assertThrows(IllegalArgumentException.class, () -> GroupRepository.replaceScriptedSource(
				"producer", List.of(group("duplicate", 0), group("duplicate", 1)), true));
		}

		assertEquals(0, events.get());
		assertEquals(List.of(original), GroupRepository.scriptedSourceGroups("producer"));
	}

	@Test
	void scriptedSourceReplacementKeepsEnabledOverrideForOrdinaryId() {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
		GroupDefinition incoming = group("ordinary_id", 0);
		GroupRepository.replaceScriptedSource("producer", List.of(incoming), false);

		assertTrue(GroupRepository.setEnabledQuietlyWithoutEvent(incoming.id(), false));
		GroupRepository.replaceScriptedSource("producer", List.of(incoming), false);

		assertFalse(GroupRepository.findById(incoming.id()).orElseThrow().enabled());
	}

	@Test
	void removingOneSourceClearsOnlyItsAppliedGeneration() {
		GroupService service = new GroupService();
		GroupService.SourceKey first = new GroupService.SourceKey(GroupSource.KUBEJS, "first");
		GroupService.SourceKey second = new GroupService.SourceKey(GroupSource.KUBEJS, "second");
		service.replaceSource(first, List.of(group("first", 0)));
		service.replaceSource(second, List.of(group("second", 0)));
		service.markApplied(first);
		service.markApplied(second);

		service.removeSource(first);

		assertTrue(service.sourceGroups(first).isEmpty());
		assertTrue(!service.isApplied(first));
		assertTrue(service.isApplied(second));
		assertEquals(List.of("second"), service.allPriorityOrder().stream().map(GroupDefinition::id).toList());
	}

	@Test
	void repositoryTeardownClearsEveryScriptProducerAndAppliedGeneration() {
		GroupRepository.replaceScriptedSource("first", List.of(group("first", 0)), false);
		GroupRepository.replaceScriptedSource("second", List.of(group("second", 0)), false);
		GroupRepository.markScriptedSourceApplied("first");
		GroupRepository.markScriptedSourceApplied("second");

		GroupRepository.clearScriptedGroups();

		assertTrue(GroupRepository.areScriptedGroupsEmpty());
		assertTrue(GroupRepository.scriptedSourceGroups("first").isEmpty());
		assertTrue(GroupRepository.scriptedSourceGroups("second").isEmpty());
		assertTrue(!GroupRepository.isScriptedSourceApplied("first"));
		assertTrue(!GroupRepository.isScriptedSourceApplied("second"));
	}

	@Test
	void failedPersistenceDoesNotReportACustomCopyOrPublishIt() throws Exception {
		GroupDefinition builtin = group("__default_source", 0);
		GroupRepository.replaceForTesting(List.of(builtin));
		Path unusableConfigRoot = tempDir.resolve("config-file");
		Files.writeString(unusableConfigRoot, "not a directory");
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, unusableConfigRoot.toString());

		GroupDefinition draft = GroupRepository.createCustomCopyDraft(builtin.id(), "Copy").orElseThrow();
		assertFalse(GroupRepository.saveQuietlyChecked(draft));

		assertEquals(List.of(builtin), GroupRepository.getAll());
	}

	@Test
	void invalidSaveIsRejectedBeforeWritingOrPublishing() {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
		GroupDefinition blankId = new GroupDefinition("", "Blank", true,
			Filters.itemId("minecraft:stone"));

		assertThrows(IllegalArgumentException.class, () -> GroupRepository.save(blankId));

		assertTrue(GroupRepository.getAll().isEmpty());
		assertTrue(Files.notExists(tempDir.resolve("collapsiblegroups/groups/.json")));
	}

	@Test
	void unrelatedMalformedConfigDoesNotBlockAValidDelete() throws Exception {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
		GroupDefinition target = group("delete_target", 0);
		GroupRepository.replaceForTesting(List.of(target));
		Path groupsDir = tempDir.resolve("collapsiblegroups/groups");
		Files.createDirectories(groupsDir);
		Path targetFile = groupsDir.resolve(target.id() + ".json");
		Path malformed = groupsDir.resolve("unrelated.json");
		Files.writeString(targetFile, GroupConfig.toJson(target));
		Files.writeString(malformed, "{broken");

		GroupRepository.delete(target.id());

		assertTrue(GroupRepository.findById(target.id()).isEmpty());
		assertTrue(Files.notExists(targetFile));
		assertTrue(Files.exists(malformed));
	}

    @Test void deletingTheLastDefinitionClearsItsCategoryAssignment() throws Exception {
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
        GroupDefinition target = group("categorized", 0);
        GroupRepository.replaceForTesting(List.of(target));
        var store = com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore.current();
        assertTrue(store.update(preferences -> preferences.assign(List.of(target.id()), null)));
        assertTrue(GroupRepository.deleteQuietlyChecked(target.id()));
        assertTrue(GroupRepository.findById(target.id()).isEmpty());
        assertFalse(store.snapshot().groupCategories().containsKey(target.id()));
    }

    @Test void deletingLocalDefinitionKeepsAssignmentWhenAScriptedDefinitionRemains() {
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
        GroupDefinition target = group("shared", 0);
        GroupRepository.replaceForTesting(List.of(target));
        GroupRepository.replaceScriptedSource("fallback", List.of(target), false);
        var store = com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore.current();
        assertTrue(store.update(preferences -> preferences.assign(List.of(target.id()), null)));
        assertTrue(GroupRepository.deleteQuietlyChecked(target.id()));
        assertTrue(GroupRepository.findById(target.id()).isPresent());
        assertTrue(store.snapshot().groupCategories().containsKey(target.id()));
    }

	private static GroupDefinition group(String id, int priority) {
		return new GroupDefinition(id, id, true, Filters.itemId("minecraft:stone")).withPriority(priority);
	}
}
