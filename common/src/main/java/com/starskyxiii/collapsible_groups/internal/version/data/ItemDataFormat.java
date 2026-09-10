package com.starskyxiii.collapsible_groups.internal.version.data;

import java.util.Objects;

public record ItemDataFormat(String dataFormat) {
    public ItemDataFormat { Objects.requireNonNull(dataFormat, "dataFormat"); }
}
