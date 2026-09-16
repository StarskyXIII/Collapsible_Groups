package com.starskyxiii.collapsible_groups.config;

import java.util.function.BiConsumer;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;

public record SettingsSnapshot(
    boolean loadDefaultGroups, boolean showManagerButton, boolean showGroupBackgrounds,
    boolean searchUngroupSmallGroups, int searchUngroupThreshold,
    int collapsedGroupBackgroundColor, int expandedGroupBackgroundColor,
    int groupNameColor, int expandedGroupBorderColor,
    boolean debugTimingEnabled, boolean debugStartupIndexVerificationEnabled,
    boolean debugEditorIndexVerificationEnabled, Set<String> disabledBuiltinCategories, boolean showCategorySidebar
) {
    public static final SettingsSnapshot DEFAULTS = new SettingsSnapshot(
        true, true, true, true, 5, 0x24FFFFFF, 0x24FFFFFF, 0xFFAA00, 0x66FFFFFF, false, false, false, Set.of(), true);

    public SettingsSnapshot {
        if (searchUngroupThreshold < 0) throw new IllegalArgumentException("searchUngroupThreshold");
        groupNameColor &= 0xFFFFFF;
        TreeSet<String> categories = new TreeSet<>(disabledBuiltinCategories);
        if (categories.stream().anyMatch(id -> !validCategoryId(id))) throw new IllegalArgumentException("disabledBuiltinCategories");
        disabledBuiltinCategories = Collections.unmodifiableSet(categories);
    }

    public static SettingsSnapshot read(Function<String, Object> read) {
        return new SettingsSnapshot(
            bool(read, "defaultGroups.enabled", true), bool(read, "ui.showManagerButton", true),
            bool(read, "ui.showGroupBackgrounds", true), bool(read, "ui.searchUngroupSmallGroups", true),
            integer(read, "ui.searchUngroupThreshold", 5),
            color(read, "ui.collapsedGroupBackgroundColor", DEFAULTS.collapsedGroupBackgroundColor, false),
            color(read, "ui.expandedGroupBackgroundColor", DEFAULTS.expandedGroupBackgroundColor, false),
            color(read, "ui.groupNameColor", DEFAULTS.groupNameColor, true),
            color(read, "ui.expandedGroupBorderColor", DEFAULTS.expandedGroupBorderColor, false),
            bool(read, "debug.enableTimingLogs", false), bool(read, "debug.verifyStartupIndex", false),
            bool(read, "debug.verifyEditorPreviewIndex", false), categories(read.apply("defaultGroups.disabledCategories")),
            bool(read, "ui.showCategorySidebar", true));
    }

    public void write(BiConsumer<String, Object> write) {
        write.accept("defaultGroups.enabled", loadDefaultGroups);
        write.accept("defaultGroups.disabledCategories", java.util.List.copyOf(disabledBuiltinCategories));
        write.accept("ui.showCategorySidebar", showCategorySidebar);
        write.accept("ui.showManagerButton", showManagerButton);
        write.accept("ui.showGroupBackgrounds", showGroupBackgrounds);
        write.accept("ui.searchUngroupSmallGroups", searchUngroupSmallGroups);
        write.accept("ui.searchUngroupThreshold", searchUngroupThreshold);
        write.accept("ui.collapsedGroupBackgroundColor", hex(collapsedGroupBackgroundColor, false));
        write.accept("ui.expandedGroupBackgroundColor", hex(expandedGroupBackgroundColor, false));
        write.accept("ui.groupNameColor", hex(groupNameColor, true));
        write.accept("ui.expandedGroupBorderColor", hex(expandedGroupBorderColor, false));
        write.accept("debug.enableTimingLogs", debugTimingEnabled);
        write.accept("debug.verifyStartupIndex", debugStartupIndexVerificationEnabled);
        write.accept("debug.verifyEditorPreviewIndex", debugEditorIndexVerificationEnabled);
    }

    public static boolean validCategoryId(Object value) {
        return value instanceof String id && !id.isBlank() && id.contains(":") && ResourceLocation.tryParse(id) != null;
    }

    private static Set<String> categories(Object value) {
        if (value == null) return Set.of();
        if (!(value instanceof java.util.List<?> values) || values.stream().anyMatch(id -> !validCategoryId(id)))
            throw new IllegalArgumentException("defaultGroups.disabledCategories");
        TreeSet<String> result = new TreeSet<>();
        values.forEach(id -> result.add((String) id));
        return result;
    }

    public static String hex(int color, boolean rgb) {
        return String.format(java.util.Locale.ROOT, rgb ? "#%06X" : "#%08X", rgb ? color & 0xFFFFFF : color);
    }

    private static boolean bool(Function<String, Object> read, String key, boolean fallback) {
        Object value = read.apply(key);
        if (value == null) return fallback;
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException(key);
    }

    private static int integer(Function<String, Object> read, String key, int fallback) {
        Object value = read.apply(key);
        if (value == null) return fallback;
        if (value instanceof Number number) {
            try {
                int result = new java.math.BigDecimal(number.toString()).intValueExact();
                if (result >= 0) return result;
            } catch (ArithmeticException | NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException(key);
    }

    private static int color(Function<String, Object> read, String key, int fallback, boolean rgb) {
        Object value = read.apply(key);
        if (value == null) return fallback;
        if (value instanceof String text && ColorConfigParser.isValidArgb(text))
            return rgb ? ColorConfigParser.parseRgb(text, fallback) : ColorConfigParser.parseArgb(text, fallback);
        throw new IllegalArgumentException(key);
    }
}

