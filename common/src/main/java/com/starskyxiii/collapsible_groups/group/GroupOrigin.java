package com.starskyxiii.collapsible_groups.group;

import java.nio.file.Path;
import java.util.Objects;

public record GroupOrigin(GroupSource source, String sourceId, String location, Path file) {
    public GroupOrigin {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(location, "location");
        if (file != null) file = file.toAbsolutePath().normalize();
    }

    public boolean locallyOwned() {
        return file != null && (source == GroupSource.USER || source == GroupSource.OVERRIDE);
    }
}
