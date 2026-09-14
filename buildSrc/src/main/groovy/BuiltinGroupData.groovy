import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

final class BuiltinGroupData {
    private static final Set<String> LOADERS = Set.of('fabric', 'forge', 'neoforge')

    static List<Map> read(File root) {
        if (!root.isDirectory()) throw new GradleException("Missing built-in group directory: ${root}")
        List<Map> entries = []
        Set<String> ids = new HashSet<>()
        Set<String> paths = new HashSet<>()
        root.listFiles().sort { it.name }.each { category ->
            if (!category.isDirectory()) {
                if (category.name.endsWith('.json')) throw new GradleException("Group JSON requires a category directory: ${category}")
                return
            }
            File metadataFile = new File(category, 'metadata.json')
            Map metadata = object(metadataFile)
            if (metadata.keySet() != Set.of('loaders') || !(metadata.loaders instanceof List)
                || metadata.loaders.isEmpty() || metadata.loaders.any { !(it instanceof String) || !LOADERS.contains(it) }
                || metadata.loaders.toSet().size() != metadata.loaders.size()) {
                throw new GradleException("Expected unique loaders from ${LOADERS} in ${metadataFile}")
            }
            category.eachFileRecurse { file ->
                if (file.isFile() && file.name.endsWith('.json') && file.parentFile != category) {
                    throw new GradleException("Nested built-in group JSON is unsupported: ${file}")
                }
            }
            List<File> files = category.listFiles().findAll { it.isFile() && it.name.endsWith('.json') && it.name != 'metadata.json' }
            if (files.isEmpty()) throw new GradleException("Empty built-in group category: ${category}")
            files.sort { it.name }.each { file ->
                String path = "assets/collapsible_groups/groups/${category.name}/${file.name}"
                if (!(path ==~ /assets\/collapsible_groups\/groups\/[a-z0-9_.\/-]+\.json/) || path.contains('..')) {
                    throw new GradleException("Invalid built-in resource path: ${path}")
                }
                Map group = object(file)
                if (!(group.id instanceof String) || group.id.isBlank() || !(group.filter instanceof Map)) {
                    throw new GradleException("Invalid built-in group: ${path}")
                }
                if (!(group.name instanceof Map) || !(group.name.translate instanceof String)
                    || group.name.translate.isBlank() || !(group.name.fallback instanceof String)
                    || group.name.fallback.isBlank()) {
                    throw new GradleException("Built-in group requires a translation key and English fallback: ${path}")
                }
                if (!group.name.translate.startsWith('collapsible_groups.group.')) {
                    throw new GradleException("Built-in group uses an external translation key: ${path}")
                }
                if (group.containsKey('schema_version') && (!(group.schema_version instanceof Number) || group.schema_version != 1)) {
                    throw new GradleException("Unsupported built-in group schema version: ${path}")
                }
                if (!ids.add(group.id)) throw new GradleException("Duplicate built-in group ID: ${group.id} (${path})")
                if (!paths.add(path)) throw new GradleException("Duplicate built-in resource path: ${path}")
                entries.add([path: path, file: file, definition: group, loaders: metadata.loaders])
            }
        }
        if (entries.isEmpty()) throw new GradleException("No built-in groups in ${root}")
        entries.sort { a, b -> a.path <=> b.path }
    }

    static void resources(File root, File destination, String loader = null) {
        if (loader != null && !LOADERS.contains(loader)) throw new GradleException("Unknown target loader: ${loader}")
        List<Map> entries = read(root).findAll { loader == null || it.loaders.contains(loader) }
        if (entries.isEmpty()) throw new GradleException("No built-in groups for ${loader} in ${root}")
        Map<String, String> output = new TreeMap<>()
        entries.each { entry -> output[entry.path] = entry.file.getText('UTF-8') }
        output['assets/collapsible_groups/builtin_catalog.json'] = json([
            version: 1,
            groups: entries.collect { [path: it.path, id: it.definition.id] }
        ])
        destination.mkdirs()
        destination.eachFileRecurse { file ->
            if (file.isFile()) {
                String relative = destination.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                if (!output.containsKey(relative)) Files.delete(file.toPath())
            }
        }
        output.each { path, text -> writeIfChanged(new File(destination, path).toPath(), text) }
    }

    static void language(File root, File uiFile, File destination) {
        Map ui = strings(object(uiFile), uiFile.name)
        Map<String, String> generated = new TreeMap<>()
        read(root).each { entry ->
            String key = entry.definition.name.translate
            String value = entry.definition.name.fallback
            if (generated.containsKey(key) && generated[key] != value) {
                throw new GradleException("Conflicting English fallback for ${key}: ${entry.path}")
            }
            if (ui.containsKey(key)) throw new GradleException("Generated group key collides with UI language entry: ${key}")
            generated[key] = value
        }
        writeIfChanged(destination.toPath(), json(generated))
    }

    private static Map object(File file) {
        if (!file.isFile()) throw new GradleException("Required JSON file is missing: ${file}")
        try {
            Object value = new JsonSlurper().parseText(file.getText('UTF-8'))
            if (!(value instanceof Map)) throw new GradleException("Expected a JSON object: ${file}")
            return value as Map
        } catch (Exception invalid) {
            throw new GradleException("Could not read ${file}: ${invalid.message}", invalid)
        }
    }

    private static Map strings(Map values, String source) {
        if (values.any { key, value -> !(key instanceof String) || !(value instanceof String) }) {
            throw new GradleException("Language entries must be strings: ${source}")
        }
        values
    }

    private static String json(Object value) {
        JsonOutput.prettyPrint(JsonOutput.toJson(value)) + '\n'
    }

    private static Path stage(Path target, String text) {
        Files.createDirectories(target.parent)
        Path temporary = Files.createTempFile(target.parent, target.fileName.toString(), '.tmp')
        Files.writeString(temporary, text, StandardCharsets.UTF_8)
        temporary
    }

    private static void writeIfChanged(Path target, String text) {
        Path temporary = stage(target, text)
        try {
            replaceIfChanged(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private static void replaceIfChanged(Path staged, Path target) {
        if (Files.exists(target) && Arrays.equals(Files.readAllBytes(staged), Files.readAllBytes(target))) return
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
