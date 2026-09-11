package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDocumentFormat;
import com.starskyxiii.collapsible_groups.group.GroupOrigin;
import com.starskyxiii.collapsible_groups.group.GroupSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public final class GroupFileStore {
    private GroupFileStore() {}

    public static Optional<GroupOrigin> create(GroupDefinition group, GroupSource source, Path configDirectory) {
        if (source != GroupSource.USER && source != GroupSource.OVERRIDE) return Optional.empty();
        Path root = directory(source, configDirectory);
        GroupOrigin origin = new GroupOrigin(source, source.name(), root.resolve(fileName(group.id())).toString(),
            root.resolve(fileName(group.id())));
        if (Files.exists(origin.file())) return Optional.empty();
        return save(group, origin, configDirectory) ? Optional.of(origin) : Optional.empty();
    }

    public static boolean save(GroupDefinition group, GroupOrigin origin, Path configDirectory) {
        if (group.documentFormat() == GroupDocumentFormat.UNSUPPORTED || origin == null || !origin.locallyOwned()) return false;
        try {
            Path target = checkedPath(origin, configDirectory);
            if (Files.exists(target)) {
                JsonObject raw = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!idMatches(raw, group.id()) || GroupDocumentFormat.inspect(raw) == GroupDocumentFormat.UNSUPPORTED) return false;
            }
            String text = GroupConfig.toJson(group);
            Files.createDirectories(target.getParent());
            Path staged = Files.createTempFile(target.getParent(), ".cg-group-", ".json.tmp");
            try {
                Files.writeString(staged, text, StandardCharsets.UTF_8);
                checkedPath(origin, configDirectory);
                try {
                    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staged);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            Constants.LOG.error("Could not save group '{}' at {}", group.id(), origin.location(), failure);
            return false;
        }
    }

    public static boolean delete(String id, GroupOrigin origin, Path configDirectory) {
        if (origin == null || !origin.locallyOwned()) return false;
        try {
            Path target = checkedPath(origin, configDirectory);
            if (!Files.exists(target)) return true;
            JsonObject raw = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!idMatches(raw, id)) return false;
            Files.delete(target);
            return true;
        } catch (IOException | RuntimeException failure) {
            Constants.LOG.error("Could not remove group '{}' at {}", id, origin.location(), failure);
            return false;
        }
    }

    private static boolean idMatches(JsonObject raw, String id) {
        return raw.has("id") && raw.get("id").isJsonPrimitive() && raw.get("id").getAsJsonPrimitive().isString()
            && id.equals(raw.get("id").getAsString());
    }

    private static Path checkedPath(GroupOrigin origin, Path configDirectory) throws IOException {
        Path root = directory(origin.source(), configDirectory).toAbsolutePath().normalize();
        Path target = origin.file().toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Group path is outside its owned directory");
        Files.createDirectories(root);
        Path ancestor = target;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        if (!ancestor.toRealPath().startsWith(root.toRealPath())) throw new IOException("Group path resolves outside its owned directory");
        return target;
    }

    private static Path directory(GroupSource source, Path configDirectory) {
        return configDirectory.resolve(source == GroupSource.OVERRIDE ? "collapsiblegroups/overrides" : "collapsiblegroups/groups");
    }

    private static String fileName(String id) {
        if (id.matches("[a-zA-Z0-9_-]{1,100}") && !id.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")) return id + ".json";
        try {
            return "group_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(StandardCharsets.UTF_8))) + ".json";
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
