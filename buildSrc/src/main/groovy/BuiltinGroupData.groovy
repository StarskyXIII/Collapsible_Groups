import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

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
                if (!(path ==~ /assets\/[a-z0-9_.-]+\/collapsible_groups\/groups\/[a-z0-9_.\/-]+\.json/)) {
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

    static synchronized Map language(List<File> roots, File languageFile, File manifestFile, File lockFile) {
        lockFile.parentFile.mkdirs()
        FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable { channel ->
            channel.lock().withCloseable { ignored ->
                mergeLanguage(read(roots), languageFile, manifestFile)
            }
        }
    }

    static void verifyLanguage(List<File> roots, File languageFile, File manifestFile, File workingDirectory) {
        workingDirectory.mkdirs()
        Path temporary = Files.createTempDirectory(workingDirectory.toPath(), 'verify-language-')
        try {
            File languageCopy = temporary.resolve(languageFile.name).toFile()
            File manifestCopy = temporary.resolve(manifestFile.name).toFile()
            Files.copy(languageFile.toPath(), languageCopy.toPath())
            Files.copy(manifestFile.toPath(), manifestCopy.toPath())
            mergeLanguage(read(roots), languageCopy, manifestCopy)
            if (!Arrays.equals(languageCopy.bytes, languageFile.bytes) || !Arrays.equals(manifestCopy.bytes, manifestFile.bytes)) {
                throw new GradleException('Generated built-in English entries are out of date. Run a normal build and commit the updated language file and ownership manifest.')
            }
        } finally {
            temporary.toFile().eachFile { file -> Files.delete(file.toPath()) }
            Files.delete(temporary)
        }
    }

    private static Map mergeLanguage(List<Map> entries, File languageFile, File manifestFile) {
        Map language = strings(object(languageFile), languageFile.name)
        Map manifest = object(manifestFile)
        if (manifest.keySet() != ['version', 'keys', 'sha256'] as Set || manifest.version != 1
            || !(manifest.keys instanceof List) || !(manifest.sha256 instanceof String)
            || manifest.keys.any { !(it instanceof String) }
            || manifest.keys != manifest.keys.toSorted().unique()) {
            throw new GradleException('Invalid generated group language ownership manifest')
        }
        Map<String, String> owned = new TreeMap<>()
        manifest.keys.each { key ->
            if (!language.containsKey(key)) throw new GradleException("Missing owned group language key: ${key}")
            owned[key] = language[key]
        }
        if (digest(owned) != manifest.sha256) {
            throw new GradleException('Generated group language entries and ownership manifest do not match')
        }
        Map<String, String> generated = new TreeMap<>()
        entries.each { entry ->
            String key = entry.definition.name.translate
            String value = entry.definition.name.fallback
            if (generated.containsKey(key) && generated[key] != value) {
                throw new GradleException("Conflicting English fallback for ${key}: ${entry.path}")
            }
            if (language.containsKey(key) && !owned.containsKey(key)) {
                throw new GradleException("Generated group key collides with a manually owned language entry: ${key}")
            }
            generated[key] = value
        }
        Map<String, String> merged = new TreeMap<>(language)
        owned.keySet().each { merged.remove(it) }
        merged.putAll(generated)
        String nextLanguage = json(merged)
        String nextManifest = json([version: 1, keys: generated.keySet().toList(), sha256: digest(generated)])
        Map checkedLanguage = strings(new JsonSlurper().parseText(nextLanguage) as Map, languageFile.name)
        Map checkedManifest = new JsonSlurper().parseText(nextManifest) as Map
        if (digest(checkedLanguage.findAll { key, value -> generated.containsKey(key) }) != checkedManifest.sha256) {
            throw new GradleException('Generated group language validation failed')
        }
        Path stagedLanguage = stage(languageFile.toPath(), nextLanguage)
        Path stagedManifest = stage(manifestFile.toPath(), nextManifest)
        byte[] previousLanguage = Files.readAllBytes(languageFile.toPath())
        byte[] previousManifest = Files.readAllBytes(manifestFile.toPath())
        try {
            replaceIfChanged(stagedLanguage, languageFile.toPath())
            replaceIfChanged(stagedManifest, manifestFile.toPath())
        } catch (Exception failed) {
            writeIfChanged(languageFile.toPath(), new String(previousLanguage, StandardCharsets.UTF_8))
            writeIfChanged(manifestFile.toPath(), new String(previousManifest, StandardCharsets.UTF_8))
            throw failed
        } finally {
            Files.deleteIfExists(stagedLanguage)
            Files.deleteIfExists(stagedManifest)
        }
        [definitions: entries.size(), generatedKeys: generated.size(), deduplicatedKeys: entries.size() - generated.size(), conflicts: 0, missing: 0]
    }

    static String digest(Map values) {
        byte[] bytes = JsonOutput.toJson(new TreeMap(values)).getBytes(StandardCharsets.UTF_8)
        MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
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
