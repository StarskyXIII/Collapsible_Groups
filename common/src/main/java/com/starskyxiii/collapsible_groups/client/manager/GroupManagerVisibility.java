package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class GroupManagerVisibility {
    private GroupManagerVisibility() {}

    public record Result<T>(List<T> visible, int hiddenEmpty) {
        public Result { visible = List.copyOf(visible); }
    }

    public static <T> Result<T> filter(List<T> entries, boolean showEmpty, Function<T, GroupEvaluation> evaluation) {
        List<T> visible = new ArrayList<>();
        int hidden = 0;
        for (T entry : entries) {
            if (!showEmpty && evaluation.apply(entry).empty()) hidden++;
            else visible.add(entry);
        }
        return new Result<>(visible, hidden);
    }
}
