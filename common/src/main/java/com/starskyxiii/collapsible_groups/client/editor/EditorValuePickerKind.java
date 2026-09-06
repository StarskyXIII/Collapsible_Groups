package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;

import java.util.List;

enum EditorValuePickerKind {
	TAG(ModTranslationKeys.EDITOR_RULES_TAG_TITLE, ModTranslationKeys.EDITOR_RULES_TAG_MANUAL, ModTranslationKeys.EDITOR_RULES_TAG_EMPTY),
	ID(ModTranslationKeys.EDITOR_RULES_ID_TITLE, ModTranslationKeys.EDITOR_RULES_ID_MANUAL, ModTranslationKeys.EDITOR_RULES_ID_EMPTY);

	final String titleKey;
	final String manualKey;
	final String emptyKey;

	EditorValuePickerKind(String titleKey, String manualKey, String emptyKey) {
		this.titleKey = titleKey;
		this.manualKey = manualKey;
		this.emptyKey = emptyKey;
	}

	static EditorValuePickerKind forNode(GroupFilterRuleDraft.NodeKind kind) {
		return switch (kind) {
			case TAG -> TAG;
			case ID -> ID;
			default -> throw new IllegalArgumentException(kind.name());
		};
	}

	void update(EditorRuntimeAccess runtime, String type) {
		if (this == TAG) runtime.updateIngredientTags(type); else runtime.updateIngredientIds(type);
	}

	void cancel(EditorRuntimeAccess runtime) {
		if (this == TAG) runtime.cancelIngredientTags(); else runtime.cancelIngredientIds();
	}

	Snapshot snapshot(EditorRuntimeAccess runtime, String type) {
		return this == TAG ? fromTags(runtime == null ? EditorIngredientTags.UNAVAILABLE : runtime.ingredientTags(type))
			: fromIds(runtime == null ? EditorIngredientIds.UNAVAILABLE : runtime.ingredientIds(type));
	}

	static Snapshot fromTags(EditorIngredientTags catalog) {
		String key = switch (catalog.status()) {
			case PENDING -> ModTranslationKeys.EDITOR_RULES_TAG_PENDING;
			case UNAVAILABLE -> ModTranslationKeys.EDITOR_RULES_TAG_UNAVAILABLE;
			case TYPE_MISSING -> ModTranslationKeys.EDITOR_RULES_TYPE_MISSING;
			case READY -> catalog.partial() ? ModTranslationKeys.EDITOR_RULES_TAG_PARTIAL
				: catalog.coverage() == EditorIngredientTags.Coverage.OBSERVED_ONLY
					? ModTranslationKeys.EDITOR_RULES_TAG_OBSERVED : ModTranslationKeys.EDITOR_RULES_TAG_REGISTRY;
		};
		return new Snapshot(TAG, catalog.token(), catalog.status() == EditorIngredientTags.Status.READY, catalog.tags(), key);
	}

	static Snapshot fromIds(EditorIngredientIds catalog) {
		String key = switch (catalog.status()) {
			case PENDING -> ModTranslationKeys.EDITOR_RULES_ID_PENDING;
			case UNAVAILABLE -> ModTranslationKeys.EDITOR_RULES_ID_UNAVAILABLE;
			case TYPE_MISSING -> ModTranslationKeys.EDITOR_RULES_TYPE_MISSING;
			case READY -> catalog.partial() ? ModTranslationKeys.EDITOR_RULES_ID_PARTIAL : ModTranslationKeys.EDITOR_RULES_ID_AVAILABLE;
		};
		return new Snapshot(ID, catalog.token(), catalog.status() == EditorIngredientIds.Status.READY, catalog.ids(), key);
	}

	record Snapshot(EditorValuePickerKind kind, Object token, boolean ready, List<String> values, String statusKey) {
		Snapshot { values = List.copyOf(values); }
	}
}
