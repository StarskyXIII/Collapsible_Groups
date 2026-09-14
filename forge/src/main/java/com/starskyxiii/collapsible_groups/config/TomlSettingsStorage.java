package com.starskyxiii.collapsible_groups.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.starskyxiii.collapsible_groups.persistence.AtomicFileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TomlSettingsStorage implements SettingsController.Storage {
    private final Path path;

    public TomlSettingsStorage(Path path) { this.path = path; }

    private CommentedConfig document() throws Exception {
        CommentedConfig document = new TomlParser().parse(Files.exists(path) ? Files.readString(path) : "");
        for (String section : List.of("defaultGroups", "ui", "debug")) {
            if (document.contains(section) && !(document.get(section) instanceof UnmodifiableConfig))
                throw new IllegalArgumentException(section);
        }
        return document;
    }

    @Override public SettingsSnapshot read() throws Exception {
        if (!Files.exists(path)) write(SettingsSnapshot.DEFAULTS);
        return SettingsSnapshot.read(document()::get);
    }

    @Override public void write(SettingsSnapshot settings) throws Exception {
        CommentedConfig document = document();
        SettingsSnapshot.read(document::get);
        settings.write(document::set);
        AtomicFileWriter.write(path, new TomlWriter().writeToString(document));
    }
}
