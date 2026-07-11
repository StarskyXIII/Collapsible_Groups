package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Structural paths ("0.2.1" of child indexes from the root) used as weak,
 * session-local keys for collapse state in the rules builder. Paths shift when
 * siblings are inserted/removed and reset entirely after {@code replaceWith}/
 * {@code decode} rebuilds — both accepted behaviors for collapse memory.
 */
public final class RuleNodePaths {
	private RuleNodePaths() {}

	/** Root path is the empty string; unattached nodes fall back to the empty string as well. */
	public static String pathOf(@Nullable GroupFilterRuleDraft.Node node) {
		if (node == null) {
			return "";
		}
		Deque<Integer> indexes = new ArrayDeque<>();
		GroupFilterRuleDraft.Node current = node;
		while (current.parent() != null) {
			int index = current.parent().children().indexOf(current);
			if (index < 0) {
				return "";
			}
			indexes.addFirst(index);
			current = current.parent();
		}
		if (indexes.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int index : indexes) {
			if (sb.length() > 0) {
				sb.append('.');
			}
			sb.append(index);
		}
		return sb.toString();
	}
}
