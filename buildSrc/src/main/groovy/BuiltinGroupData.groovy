import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

final class BuiltinGroupData {
    static List<Map> read(List<File> roots) {
        List<Map> entries = []
        Set<String> ids = new HashSet<>()
        Set<String> paths = new HashSet<>()
        roots.each { root ->
            if (!root.isDirectory()) throw new GradleException("Missing built-in group directory: ${root.name}")
            root.eachFileRecurse { file ->
                if (!file.isFile() || !file.name.endsWith('.json')) return
                String path = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                if (!(path ==~ /assets\/collapsible_groups\/groups\/[a-z0-9_.\/-]+\.json/)) {
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
                entries.add([path: path, file: file, definition: group])
            }
        }
        entries.sort { a, b -> a.path <=> b.path }
    }

    static void resources(List<File> roots, File destination) {
        List<Map> entries = read(roots)
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

    static void language(List<File> roots, File uiFile, File destination) {
        Map ui = strings(object(uiFile), uiFile.name)
        Map<String, String> generated = new TreeMap<>()
        read(roots).each { entry ->
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
        if (!file.isFile()) throw new GradleException("Required JSON file is missing: ${file.name}")
        try {
            Object value = new JsonSlurper().parseText(file.getText('UTF-8'))
            if (!(value instanceof Map)) throw new GradleException("Expected a JSON object: ${file.name}")
            return value as Map
        } catch (Exception invalid) {
            throw new GradleException("Could not read ${file.name}: ${invalid.message}", invalid)
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
