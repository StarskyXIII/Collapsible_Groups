package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleFieldRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorTagPickerTransactionTest {
	private final EditorStateCore core = new EditorStateCore(null, () -> {});
	private final EditorRulesState state = (EditorRulesState) Proxy.newProxyInstance(EditorRulesState.class.getClassLoader(),
		new Class<?>[] {EditorRulesState.class}, (proxy, method, args) -> {
			var target = EditorStateCore.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
			target.setAccessible(true);
			return target.invoke(core, args);
		});
	private final EditorRulesPanel panel = new EditorRulesPanel(state, null, () -> {}, null, null);

	private void field(String name, Object value) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(panel, value);
	}

	private void invoke(String name) throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(panel);
	}

	private void modal(String name) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField("modal");
		field.setAccessible(true);
		for (var kind : field.getType().getEnumConstants()) if (kind.toString().equals(name)) field.set(panel, kind);
	}

	private void edit(GroupFilterRuleDraft.Node node, boolean isNew) throws Exception {
		field("editingNode", node);
		field("editingIsNew", isNew);
		field("snapType", node.ingredientType());
		field("snapPrimary", node.primaryValue());
		field("snapSecondary", node.secondaryValue());
		field("snapTertiary", node.tertiaryValue());
		modal("TAG_PICKER");
	}

	private void setTag(String value) throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod("setFormFieldValue", RuleFieldRole.class, String.class);
		method.setAccessible(true);
		method.invoke(panel, RuleFieldRole.PRIMARY_VALUE, value);
	}

	@ParameterizedTest
	@EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"ALL", "ANY", "NOT"})
	void pendingTagCancelAndConfirmRespectParent(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var parent = core.insertRuleRelative(kind);
		var node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		invoke("cancelEditor");
		assertFalse(core.hasPendingRuleNode());
		assertTrue(parent.children().isEmpty());
		node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		node.setPrimaryValue("mekanism:clean");
		invoke("confirmEditor");
		assertFalse(core.hasPendingRuleNode());
		assertEquals(List.of(node), parent.children());
		assertEquals("mekanism:clean", node.primaryValue());
		core.cancelPendingRuleNode();
		assertEquals(List.of(node), parent.children());
	}

	@Test void manualDraftDoesNotCommitPendingAndCancelStillDeletesIt() throws Exception {
		var node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setTag("c:manual");
		assertTrue(core.hasPendingRuleNode());
		assertEquals("Mekanism.ChemicalStack", node.ingredientType());
		invoke("cancelEditor");
		assertFalse(core.hasRulesRoot());
	}

	@Test void blankManualDraftCannotBeConfirmed() throws Exception {
		var node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setTag(" ");
		invoke("confirmEditor");
		assertTrue(core.hasPendingRuleNode());
		assertTrue(panel.isModalOpen());
	}

	@Test void childTagSelectionDoesNotReplaceOriginalCancelSnapshot() throws Exception {
		var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("missing:original");
		node.setPrimaryValue("c:unavailable");
		node.setSecondaryValue("exact secondary");
		node.setTertiaryValue("exact tertiary");
		edit(node, false);
		node.setIngredientType("Mekanism.ChemicalStack");
		setTag("mekanism:clean");
		assertTrue(panel.isModalOpen());
		assertEquals("mekanism:clean", node.primaryValue());
		invoke("cancelEditor");
		assertEquals("missing:original", node.ingredientType());
		assertEquals("c:unavailable", node.primaryValue());
		assertEquals("exact secondary", node.secondaryValue());
		assertEquals("exact tertiary", node.tertiaryValue());
	}

	@Test void deactivationAbortsTagPickerPendingTransaction() throws Exception {
		var node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		panel.onDeactivate();
		assertFalse(core.hasPendingRuleNode());
		assertFalse(core.hasRulesRoot());
		assertFalse(panel.isModalOpen());
	}
}
