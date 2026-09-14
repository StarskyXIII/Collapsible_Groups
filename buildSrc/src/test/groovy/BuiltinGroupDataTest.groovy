import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import static org.junit.jupiter.api.Assertions.*

class BuiltinGroupDataTest {
    @TempDir Path temporary

    @Test void languageIsDeterministicAndNeverChangesUiInput() {
        File root = temporary.resolve('definitions').toFile()
        File ui = temporary.resolve('ui.json').toFile()
        File output = temporary.resolve('generated/en_us.json').toFile()
        write(ui, ['ui.title': 'Title'])
        File first = definition(root, 'first', 'First')
        definition(root, 'second', 'Second')
        byte[] originalUi = ui.bytes
        BuiltinGroupData.language(root, ui, output)
        byte[] generated = output.bytes
        BuiltinGroupData.language(root, ui, output)
        assertArrayEquals(generated, output.bytes)
        assertArrayEquals(originalUi, ui.bytes)
        assertFalse(object(output).containsKey('ui.title'))
        first.delete()
        BuiltinGroupData.language(root, ui, output)
        assertEquals(['collapsible_groups.group.second': 'Second'], object(output))
    }

    @Test void conflictingFallbackOrUiCollisionLeavesExistingOutputIntact() {
        File root = temporary.resolve('definitions').toFile()
        File ui = temporary.resolve('ui.json').toFile()
        File output = temporary.resolve('generated.json').toFile()
        write(ui, [:])
        definition(root, 'first', 'Shared', 'collapsible_groups.group.shared')
        File second = definition(root, 'second', 'Shared', 'collapsible_groups.group.shared')
        BuiltinGroupData.language(root, ui, output)
        assertEquals(1, object(output).size())
        byte[] before = output.bytes
        definition(root, 'second', 'Conflict', 'collapsible_groups.group.shared')
        assertThrows(GradleException) { BuiltinGroupData.language(root, ui, output) }
        assertArrayEquals(before, output.bytes)
        second.delete()
        write(ui, ['collapsible_groups.group.shared': 'UI collision'])
        assertThrows(GradleException) { BuiltinGroupData.language(root, ui, output) }
        assertArrayEquals(before, output.bytes)
    }

    @Test void catalogSortsPathsAndRemovesStaleGeneratedResources() {
        File root = temporary.resolve('definitions').toFile()
        File second = definition(root, 'second', 'Second')
        definition(root, 'first', 'First')
        File destination = temporary.resolve('generated').toFile()
        BuiltinGroupData.resources(root, destination)
        File catalog = new File(destination, 'assets/collapsible_groups/builtin_catalog.json')
        assertEquals(['first', 'second'], object(catalog).groups.collect { it.id })
        second.delete()
        BuiltinGroupData.resources(root, destination)
        assertEquals(['first'], object(catalog).groups.collect { it.id })
        assertFalse(new File(destination, 'assets/collapsible_groups/groups/sample/second.json').exists())
    }

    @Test void metadataSelectsLoadersAndRemovesResourcesWhenMembershipChanges() {
        File root = temporary.resolve('definitions').toFile()
        definition(root, 'shared', 'Shared')
        definition(root, 'restricted', 'Restricted', null, 'restricted', ['fabric', 'neoforge'])
        File destination = temporary.resolve('generated').toFile()
        File catalog = new File(destination, 'assets/collapsible_groups/builtin_catalog.json')
        BuiltinGroupData.resources(root, destination, 'fabric')
        assertEquals(['restricted', 'shared'], object(catalog).groups.collect { it.id })
        assertFalse(new File(destination, 'assets/collapsible_groups/groups/restricted/metadata.json').exists())
        BuiltinGroupData.resources(root, destination, 'forge')
        assertEquals(['shared'], object(catalog).groups.collect { it.id })
        assertFalse(new File(destination, 'assets/collapsible_groups/groups/restricted/restricted.json').exists())
        BuiltinGroupData.resources(root, destination, 'neoforge')
        write(new File(root, 'restricted/metadata.json'), [loaders: ['fabric']])
        BuiltinGroupData.resources(root, destination, 'neoforge')
        assertEquals(['shared'], object(catalog).groups.collect { it.id })
        assertFalse(new File(destination, 'assets/collapsible_groups/groups/restricted/restricted.json').exists())
        BuiltinGroupData.resources(root, destination)
        assertEquals(['restricted', 'shared'], object(catalog).groups.collect { it.id })
    }

    @Test void invalidMetadataAndNestedDefinitionsLeavePublishedResourcesIntact() {
        File root = temporary.resolve('definitions').toFile()
        File source = definition(root, 'group', 'Group')
        File metadata = new File(source.parentFile, 'metadata.json')
        File destination = temporary.resolve('generated').toFile()
        BuiltinGroupData.resources(root, destination, 'fabric')
        File catalog = new File(destination, 'assets/collapsible_groups/builtin_catalog.json')
        byte[] before = catalog.bytes
        [[:], [loaders: []], [loaders: 'fabric'], [loaders: ['common']], [loaders: [1]],
            [loaders: ['fabric', 'fabric']], [loaders: ['fabric'], unknown: true]].each { invalid ->
            write(metadata, invalid)
            assertThrows(GradleException) { BuiltinGroupData.resources(root, destination, 'fabric') }
            assertArrayEquals(before, catalog.bytes)
        }
        metadata.delete()
        assertThrows(GradleException) { BuiltinGroupData.resources(root, destination, 'fabric') }
        assertArrayEquals(before, catalog.bytes)
        write(metadata, [loaders: ['fabric']])
        File nested = new File(source.parentFile, 'nested/group.json')
        nested.parentFile.mkdirs()
        nested.bytes = source.bytes
        assertThrows(GradleException) { BuiltinGroupData.resources(root, destination, 'fabric') }
        assertArrayEquals(before, catalog.bytes)
    }

    @Test void emptyLoaderSelectionDoesNotReplacePublishedResources() {
        File root = temporary.resolve('definitions').toFile()
        File source = definition(root, 'group', 'Group')
        File destination = temporary.resolve('generated').toFile()
        BuiltinGroupData.resources(root, destination, 'forge')
        File catalog = new File(destination, 'assets/collapsible_groups/builtin_catalog.json')
        File packaged = new File(destination, 'assets/collapsible_groups/groups/sample/group.json')
        byte[] catalogBefore = catalog.bytes
        byte[] groupBefore = packaged.bytes
        write(new File(source.parentFile, 'metadata.json'), [loaders: ['fabric']])
        assertThrows(GradleException) { BuiltinGroupData.resources(root, destination, 'forge') }
        assertArrayEquals(catalogBefore, catalog.bytes)
        assertArrayEquals(groupBefore, packaged.bytes)
        File empty = temporary.resolve('empty').toFile()
        empty.mkdirs()
        assertThrows(GradleException) { BuiltinGroupData.resources(empty, destination, 'forge') }
        assertArrayEquals(catalogBefore, catalog.bytes)
        assertArrayEquals(groupBefore, packaged.bytes)
    }

    private static File definition(File root, String id, String fallback, String key = null,
        String category = 'sample', List<String> loaders = ['fabric', 'forge', 'neoforge']) {
        File file = new File(root, "${category}/${id}.json")
        file.parentFile.mkdirs()
        File metadata = new File(file.parentFile, 'metadata.json')
        if (!metadata.exists()) write(metadata, [loaders: loaders])
        write(file, [schema_version: 1, id: id, name: [translate: key ?: "collapsible_groups.group.${id}".toString(), fallback: fallback],
            filter: [type: 'item', id: 'minecraft:stone']])
        file
    }

    private static Map object(File file) { new JsonSlurper().parseText(file.getText('UTF-8')) as Map }
    private static void write(File file, Map value) { file.setText(JsonOutput.toJson(value), 'UTF-8') }
}
