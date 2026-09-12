import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

import static org.junit.jupiter.api.Assertions.*

class BuiltinGroupDataTest {
    @TempDir Path temporary
    File root
    File english
    File manifest
    File lock

    @BeforeEach void setUp() {
        root = temporary.resolve('definitions').toFile()
        root.mkdirs()
        english = temporary.resolve('en_us.json').toFile()
        manifest = temporary.resolve('ownership.json').toFile()
        lock = temporary.resolve('generation.lock').toFile()
        write(english, ['collapsible_groups.ui.manual': 'Manual'])
        write(manifest, [version: 1, keys: [], sha256: BuiltinGroupData.digest([:])])
    }

    @Test void deterministicGenerationPreservesManualEntriesAndOnlyRemovesOwnedKeys() {
        File first = definition('first', 'First')
        definition('second', 'Second')
        Map report = generate()
        assertEquals([definitions: 2, generatedKeys: 2, deduplicatedKeys: 0, conflicts: 0, missing: 0], report)
        byte[] previousLanguage = english.bytes
        byte[] previousManifest = manifest.bytes
        long modified = english.lastModified()
        generate()
        assertArrayEquals(previousLanguage, english.bytes)
        assertArrayEquals(previousManifest, manifest.bytes)
        assertEquals(modified, english.lastModified())
        assertFalse(english.text.contains(temporary.toString()))
        assertFalse(manifest.text.contains(temporary.toString()))
        Map manualEdit = object(english)
        manualEdit['collapsible_groups.ui.manual'] = 'Revised manual text'
        manualEdit['collapsible_groups.group.manual'] = 'Manually owned group'
        write(english, manualEdit)
        Files.delete(first.toPath())
        generate()
        assertEquals(['collapsible_groups.ui.manual': 'Revised manual text',
            'collapsible_groups.group.manual': 'Manually owned group',
            'collapsible_groups.group.second': 'Second'], object(english))
        assertEquals(['collapsible_groups.group.second'], object(manifest).keys)
    }

    @Test void sameValueCollisionCannotClaimManuallyOwnedKey() {
        write(english, ['collapsible_groups.group.first': 'First'])
        definition('first', 'First')
        byte[] before = english.bytes
        assertThrows(GradleException) { generate() }
        assertArrayEquals(before, english.bytes)
        assertEquals([], object(manifest).keys)
    }

    @Test void missingOrCorruptOwnershipStateNeverDeletesLanguageEntries() {
        definition('first', 'First')
        generate()
        byte[] language = english.bytes
        byte[] ownership = manifest.bytes
        Files.delete(manifest.toPath())
        assertThrows(GradleException) { generate() }
        assertArrayEquals(language, english.bytes)
        manifest.bytes = ownership
        Map corrupted = object(manifest)
        corrupted.sha256 = 'not-the-owned-projection'
        write(manifest, corrupted)
        assertThrows(GradleException) { generate() }
        assertArrayEquals(language, english.bytes)
        manifest.bytes = ownership
        Map changed = object(english)
        changed['collapsible_groups.group.first'] = 'Unexpected generated edit'
        write(english, changed)
        assertThrows(GradleException) { generate() }
        assertEquals(changed, object(english))
    }

    @Test void duplicateIdsMissingNamesExternalKeysAndConflictingFallbacksFail() {
        File first = definition('first', 'First')
        File second = definition('second', 'Second')
        Map value = object(second)
        value.id = 'first'
        write(second, value)
        assertThrows(GradleException) { generate() }
        value.id = 'second'
        value.name.translate = 'collapsible_groups.group.first'
        write(second, value)
        assertThrows(GradleException) { generate() }
        value.name.translate = 'third_party.group'
        write(second, value)
        assertThrows(GradleException) { generate() }
        value.name = [:]
        write(second, value)
        assertThrows(GradleException) { generate() }
        assertTrue(object(manifest).keys.isEmpty())
        assertTrue(first.isFile())
    }

    @Test void identicalSharedKeysDeduplicateAndConcurrentCallersKeepOneValidPair() {
        definition('first', 'Shared', 'collapsible_groups.group.shared')
        definition('second', 'Shared', 'collapsible_groups.group.shared')
        def executor = Executors.newFixedThreadPool(2)
        try {
            def futures = (1..2).collect { executor.submit({ generate() } as java.util.concurrent.Callable<Map>) }
            futures.each { assertEquals(1, it.get().deduplicatedKeys) }
        } finally { executor.shutdownNow() }
        assertEquals(['collapsible_groups.group.shared'], object(manifest).keys)
        assertEquals(BuiltinGroupData.digest(['collapsible_groups.group.shared': 'Shared']), object(manifest).sha256)
    }

    @Test void resourceCatalogUsesStablePathsAndRemovesOnlyStaleGeneratedResources() {
        File first = definition('first', 'First')
        definition('second', 'Second')
        File destination = temporary.resolve('generated').toFile()
        BuiltinGroupData.resources([root], destination)
        File catalog = new File(destination, 'assets/collapsible_groups/builtin_catalog.json')
        assertEquals(['first', 'second'], object(catalog).groups.collect { it.id })
        Files.delete(first.toPath())
        BuiltinGroupData.resources([root], destination)
        assertEquals(['second'], object(catalog).groups.collect { it.id })
        assertFalse(new File(destination, 'assets/collapsible_groups/collapsible_groups/groups/first.json').exists())
    }

    @Test void verificationReportsDriftWithoutUpdatingCommittedFiles() {
        File first = definition('first', 'First')
        generate()
        File workspace = temporary.resolve('verification').toFile()
        BuiltinGroupData.verifyLanguage([root], english, manifest, workspace)
        byte[] language = english.bytes
        byte[] ownership = manifest.bytes
        Map changed = object(first)
        changed.name.fallback = 'Changed'
        write(first, changed)
        assertThrows(GradleException) { BuiltinGroupData.verifyLanguage([root], english, manifest, workspace) }
        assertArrayEquals(language, english.bytes)
        assertArrayEquals(ownership, manifest.bytes)
        assertEquals(0, workspace.listFiles().length)
    }

    private Map generate() { BuiltinGroupData.language([root], english, manifest, lock) }

    private File definition(String id, String fallback, String key = null) {
        File file = new File(root, "assets/collapsible_groups/collapsible_groups/groups/${id}.json")
        file.parentFile.mkdirs()
        write(file, [schema_version: 1, id: id, name: [translate: key ?: "collapsible_groups.group.${id}".toString(), fallback: fallback],
            filter: [type: 'item', id: 'minecraft:stone']])
        file
    }

    private static Map object(File file) { new JsonSlurper().parseText(file.getText('UTF-8')) as Map }
    private static void write(File file, Map value) { file.setText(JsonOutput.toJson(value), 'UTF-8') }
}
