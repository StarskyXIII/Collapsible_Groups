package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorRulesState {
	void ensureRuleSelection();

	@Nullable
	GroupFilterRuleDraft.Node selectedRuleNode();

	List<GroupFilterRuleDraft.FlatNode> flattenedRuleNodes();

	void selectRuleNode(GroupFilterRuleDraft.Node node);

	boolean canDeleteSelectedRule();

	void deleteSelectedRule();

	boolean canInsertRuleRelative();

	@Nullable
	GroupFilterRuleDraft.Node insertRuleRelative(GroupFilterRuleDraft.NodeKind kind);

	boolean canWrapSelectedRule(GroupFilterRuleDraft.NodeKind kind);

	@Nullable
	GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind);

	void markRulesChanged();

	List<Component> currentValidationErrors();

	String filterSummary();
}
