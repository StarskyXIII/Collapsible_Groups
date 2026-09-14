package com.starskyxiii.collapsible_groups.persistence;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.CategoryPreferences;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public final class GroupCategoryStore {
    private static GroupCategoryStore current;
    private final Path file;
    private CategoryPreferences snapshot = CategoryPreferences.empty();
    private boolean writable;
    private String problem;
    private String persisted;

    public GroupCategoryStore(Path file) {
        this.file = file;
        reload();
    }

    public static synchronized GroupCategoryStore current() {
        Path file = Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/categories.json");
        if (current == null || !current.file.equals(file)) current = new GroupCategoryStore(file);
        return current;
    }

    public synchronized CategoryPreferences snapshot() { return snapshot; }
    public synchronized boolean writable() { return writable; }
    public synchronized String problem() { return problem; }

    public synchronized void reload() {
        try {
            String text = Files.notExists(file) ? null : Files.readString(file);
            CategoryPreferences loaded = text == null ? CategoryPreferences.empty() : CategoryPreferences.parse(text);
            snapshot = loaded;
            persisted = text;
            writable = true;
            problem = null;
        } catch (IOException | RuntimeException failure) {
            writable = false;
            problem = failure.getMessage();
            Constants.LOG.warn("Could not load category preferences at {}", file, failure);
        }
    }

    public synchronized boolean update(UnaryOperator<CategoryPreferences> change) {
        if (!writable) return false;
        try {
            String existing = Files.notExists(file) ? null : Files.readString(file);
            if (!java.util.Objects.equals(existing, persisted)) {
                writable = false;
                problem = "Category preferences changed on disk";
                return false;
            }
            CategoryPreferences next = change.apply(snapshot);
            if (next.equals(snapshot)) { problem = null; return true; }
            Files.createDirectories(file.getParent());
            String text = next.toJson();
            GroupConfig.writeAtomically(file, text);
            snapshot = next;
            persisted = text;
            problem = null;
            return true;
        } catch (IOException | RuntimeException failure) {
            problem = failure.getMessage();
            Constants.LOG.warn("Could not save category preferences at {}", file, failure);
            return false;
        }
    }
}
