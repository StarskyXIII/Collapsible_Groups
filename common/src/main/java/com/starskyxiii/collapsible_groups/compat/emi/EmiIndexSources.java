package com.starskyxiii.collapsible_groups.compat.emi;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;

import java.util.List;

/** Keeps EMI's stable ownership order separate from its user-visible editor order. */
final class EmiIndexSources {
	private EmiIndexSources() {}

	static Sources<EmiStack> snapshot() {
		return from(EmiStackList.stacks, EmiStackList.filteredStacks);
	}

	static <T> Sources<T> from(List<T> stable, List<T> filtered) {
		return new Sources<>(stable, filtered);
	}

	record Sources<T>(List<T> ownership, List<T> editorDisplay) {}
}
