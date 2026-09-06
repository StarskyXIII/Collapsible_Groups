package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleFieldRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorValuePickerTransactionTest {
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
		field.set(panel, java.util.Arrays.stream(field.getType().getEnumConstants())
			.filter(kind -> kind.toString().equals(name)).findFirst().orElseThrow());
	}

	private void edit(GroupFilterRuleDraft.Node node, boolean isNew) throws Exception {
		field("editingNode", node);
		field("editingIsNew", isNew);
		field("snapType", node.ingredientType());
		field("snapPrimary", node.primaryValue());
		field("snapSecondary", node.secondaryValue());
		field("snapTertiary", node.tertiaryValue());
		modal("VALUE_PICKER");
	}

	private void setValue(String value) throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod("setFormFieldValue", RuleFieldRole.class, String.class);
		method.setAccessible(true);
		method.invoke(panel, RuleFieldRole.PRIMARY_VALUE, value);
	}

	@ParameterizedTest
	@EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"ALL", "ANY", "NOT"})
	void pendingValueCancelAndConfirmRespectParent(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var parent = core.insertRuleRelative(kind);
		for (var leaf : List.of(GroupFilterRuleDraft.NodeKind.TAG, GroupFilterRuleDraft.NodeKind.ID)) {
		core.selectRuleNode(parent);
		var node = core.insertRuleRelativePending(leaf);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		invoke("cancelEditor");
		assertFalse(core.hasPendingRuleNode());
		assertTrue(parent.children().isEmpty());
		node = core.insertRuleRelativePending(leaf);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		node.setPrimaryValue("mekanism:clean");
		invoke("confirmEditor");
		assertFalse(core.hasPendingRuleNode());
		assertEquals(List.of(node), parent.children());
		assertEquals("mekanism:clean", node.primaryValue());
		core.cancelPendingRuleNode();
		assertEquals(List.of(node), parent.children());
		core.selectRuleNode(node);
		core.deleteSelectedRule();
		}
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID"})
	void manualDraftDoesNotCommitPendingAndCancelStillDeletesIt(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.insertRuleRelativePending(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setValue("c:manual");
		assertTrue(core.hasPendingRuleNode());
		assertEquals("Mekanism.ChemicalStack", node.ingredientType());
		invoke("cancelEditor");
		assertFalse(core.hasRulesRoot());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID"})
	void blankManualDraftCannotBeConfirmed(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.insertRuleRelativePending(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setValue(" ");
		invoke("confirmEditor");
		assertTrue(core.hasPendingRuleNode());
		assertTrue(panel.isModalOpen());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID"})
	void childSelectionDoesNotReplaceOriginalCancelSnapshot(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.insertRuleRelative(kind);
		node.setIngredientType("missing:original");
		node.setPrimaryValue("c:unavailable");
		node.setSecondaryValue("exact secondary");
		node.setTertiaryValue("exact tertiary");
		edit(node, false);
		node.setIngredientType("Mekanism.ChemicalStack");
		setValue("mekanism:clean");
		assertTrue(panel.isModalOpen());
		assertEquals("mekanism:clean", node.primaryValue());
		invoke("cancelEditor");
		assertEquals("missing:original", node.ingredientType());
		assertEquals("c:unavailable", node.primaryValue());
		assertEquals("exact secondary", node.secondaryValue());
		assertEquals("exact tertiary", node.tertiaryValue());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID"})
	void deactivationAbortsPendingTransaction(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.insertRuleRelativePending(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		panel.onDeactivate();
		assertFalse(core.hasPendingRuleNode());
		assertFalse(core.hasRulesRoot());
		assertFalse(panel.isModalOpen());
	}

	@Test void genericIdsUseTheGenericPickerEligibilityWhileBuiltinsKeepTheirExistingFlow() throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod("canChangeType");
		method.setAccessible(true);
		var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ID);
		edit(node, false);
		for (String type : List.of("Mekanism.ChemicalStack", "emi:mekanism_chemical", "missing:custom")) {
			node.setIngredientType(type);
			assertEquals(true, method.invoke(panel));
		}
		for (String type : List.of("item", "fluid")) {
			node.setIngredientType(type);
			assertEquals(false, method.invoke(panel));
		}
	}

	@Test void menuProvidesDistinctOtherIdAndOtherTagEntries() throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField("CONDITION_ENTRIES");
		field.setAccessible(true);
		java.util.ArrayList<Object> kinds = new java.util.ArrayList<>();
		for (Object entry : (List<?>) field.get(null)) {
			var other = entry.getClass().getDeclaredMethod("otherIngredient");
			var kind = entry.getClass().getDeclaredMethod("kind");
			other.setAccessible(true);
			kind.setAccessible(true);
			if (Boolean.TRUE.equals(other.invoke(entry))) kinds.add(kind.invoke(entry));
		}
		assertEquals(List.of(GroupFilterRuleDraft.NodeKind.ID, GroupFilterRuleDraft.NodeKind.TAG), kinds);
	}

	@Test void invalidManualIdRemainsSubjectToRuleValidation() throws Exception {
		var node = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.ID);
		node.setIngredientType("emi:mekanism_chemical");
		edit(node, true);
		modal("FORM");
		setValue("Invalid ID!");
		assertFalse(core.currentValidationErrors().isEmpty());
		invoke("cancelEditor");
		assertFalse(core.hasPendingRuleNode());
	}
}
