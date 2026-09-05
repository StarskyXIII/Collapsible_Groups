package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleTagResolution;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

final class EditorTagDiagnostics {
	private EditorTagDiagnostics() {}

	static @Nullable String warning(GroupFilterRuleDraft.Node node, EditorRuntimeAccess runtime) {
		if (node.kind() == GroupFilterRuleDraft.NodeKind.TAG) {
			ResourceLocation tag = ResourceLocation.tryParse(node.primaryValue().trim());
			if (tag == null || node.ingredientType().isBlank()) return null;
			TagQueryDiagnostics diagnostic = runtime.tagDiagnostics(node.ingredientType(), tag);
			switch (diagnostic.availability()) {
				case PENDING: return ModTranslationKeys.EDITOR_TAG_PENDING;
				case PARTIAL: return ModTranslationKeys.EDITOR_TAG_PARTIAL;
				case UNAVAILABLE: return ModTranslationKeys.EDITOR_TAG_UNAVAILABLE;
				case SUPPORTED: break;
			}
			if (diagnostic.existence() == TagQueryDiagnostics.Existence.ABSENT)
				return ModTranslationKeys.EDITOR_RULES_UNRESOLVED_ROW;
		}
		return RuleTagResolution.isUnresolved(node, RuleTagResolution.RegistryLookup.INSTANCE)
			? ModTranslationKeys.EDITOR_RULES_UNRESOLVED_ROW : null;
	}
}
