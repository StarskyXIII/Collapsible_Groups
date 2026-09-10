package com.starskyxiii.collapsible_groups.client.editor;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleFieldRole;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class EditorValuePickerTransactionTest {
	@Test void namespaceManualInputUsesNamespaceValidationAndKeepsValidDraft() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.NAMESPACE);
		node.setIngredientType("emi:chemical");
		edit(node, true);
		modal("FORM");
		for (String invalid : List.of("test:oxygen", "test/path", "Invalid")) {
			setValue(invalid);
			assertFalse(core.currentValidationErrors().isEmpty(), invalid);
		}
		setValue("test_mod");
		assertTrue(core.currentValidationErrors().isEmpty());
		invoke("confirmEditor");
		assertFalse(core.hasRuleEditTransaction());
		assertEquals("test_mod", node.primaryValue());
	}

	private final EditorStateCore core = new EditorStateCore(null, () -> {});
	private final EditorRulesState state = (EditorRulesState) Proxy.newProxyInstance(EditorRulesState.class.getClassLoader(),
		new Class<?>[] {EditorRulesState.class}, (proxy, method, args) -> {
			var target = EditorStateCore.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
			target.setAccessible(true);
			return target.invoke(core, args);
		});
	private final EditorRulesPanel panel = new EditorRulesPanel(state, null, () -> {}, null, null);

	private void field(String name, Object value) throws Exception {
		field(panel, name, value);
	}

	private void invoke(String name) throws Exception {
		invoke(panel, name);
	}

	private static void field(EditorRulesPanel target, String name, Object value) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object field(EditorRulesPanel target, String name) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void invoke(EditorRulesPanel target, String name) throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private void modal(String name) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField("modal");
		field.setAccessible(true);
		field.set(panel, java.util.Arrays.stream(field.getType().getEnumConstants())
			.filter(kind -> kind.toString().equals(name)).findFirst().orElseThrow());
	}

	private void edit(GroupFilterRuleDraft.Node node, boolean isNew) throws Exception {
		if (!isNew) assertTrue(core.beginRuleEdit(node));
		field("editingNode", node);
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
		core.insertRuleRelative(kind);
		for (var leaf : List.of(GroupFilterRuleDraft.NodeKind.TAG, GroupFilterRuleDraft.NodeKind.ID, GroupFilterRuleDraft.NodeKind.NAMESPACE)) {
		core.selectRuleNode(core.flattenedRuleNodes().get(0).node());
		var node = core.beginInsertRule(leaf);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		invoke("cancelEditor");
		assertFalse(core.hasRuleEditTransaction());
		assertTrue(core.flattenedRuleNodes().get(0).node().children().isEmpty());
		core.selectRuleNode(core.flattenedRuleNodes().get(0).node());
		node = core.beginInsertRule(leaf);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		node.setPrimaryValue(leaf == GroupFilterRuleDraft.NodeKind.NAMESPACE ? "mekanism" : "mekanism:clean");
		invoke("confirmEditor");
		assertFalse(core.hasRuleEditTransaction());
		assertEquals(List.of(node), core.flattenedRuleNodes().get(0).node().children());
		assertEquals(leaf == GroupFilterRuleDraft.NodeKind.NAMESPACE ? "mekanism" : "mekanism:clean", node.primaryValue());
		core.cancelRuleEdit();
		assertEquals(List.of(node), core.flattenedRuleNodes().get(0).node().children());
		core.selectRuleNode(node);
		core.deleteSelectedRule();
		}
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID", "NAMESPACE"})
	void manualDraftDoesNotCommitPendingAndCancelStillDeletesIt(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.beginInsertRule(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setValue("c:manual");
		assertTrue(core.hasRuleEditTransaction());
		assertEquals("Mekanism.ChemicalStack", node.ingredientType());
		invoke("cancelEditor");
		assertFalse(core.hasRulesRoot());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID", "NAMESPACE"})
	void blankManualDraftCannotBeConfirmed(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.beginInsertRule(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		modal("FORM");
		setValue(" ");
		invoke("confirmEditor");
		assertTrue(core.hasRuleEditTransaction());
		assertTrue(panel.isModalOpen());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID", "NAMESPACE"})
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
		var restored = core.selectedRuleNode();
		assertEquals("missing:original", restored.ingredientType());
		assertEquals("c:unavailable", restored.primaryValue());
		assertEquals("exact secondary", restored.secondaryValue());
		assertEquals("exact tertiary", restored.tertiaryValue());
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"TAG", "ID", "NAMESPACE"})
	void deactivationAbortsPendingTransaction(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var node = core.beginInsertRule(kind);
		node.setIngredientType("Mekanism.ChemicalStack");
		edit(node, true);
		panel.onDeactivate();
		assertFalse(core.hasRuleEditTransaction());
		assertFalse(core.hasRulesRoot());
		assertFalse(panel.isModalOpen());
	}

	@Test
	void freshPanelInitializationCancelsTransactionOwnedByDiscardedPanel() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.ID);
		edit(node, true);
		node.setPrimaryValue("minecraft:stone");
		assertTrue(core.hasRuleEditTransaction());

		var replacement = new EditorRulesPanel(state, null, () -> {}, null, null);
		replacement.init(0, 0, 120, 100);

		assertFalse(core.hasRuleEditTransaction());
		assertFalse(core.hasRulesRoot());
		assertFalse(replacement.isModalOpen());
	}

	@Test
	void cancelRestoresCleanAndAlreadyDirtyScreenStates() throws Exception {
		for (boolean initiallyDirty : List.of(false, true)) {
			AtomicBoolean dirty = new AtomicBoolean(initiallyDirty);
			var candidate = new EditorRulesPanel(state, null, () -> dirty.set(true), null, null);
			candidate.setDirtyGate(dirty::get, dirty::set);
			var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.ID);
			field(candidate, "editingNode", node);
			field(candidate, "transactionDirtySnapshot", initiallyDirty);
			field(candidate, "modal", modalValue("FORM"));
			node.setPrimaryValue("minecraft:stone");
			dirty.set(true);

			invoke(candidate, "cancelEditor");

			assertEquals(initiallyDirty, dirty.get());
			assertFalse(core.hasRulesRoot());
		}
	}

	@Test
	void confirmingAnUnchangedExistingRuleKeepsACleanScreen() throws Exception {
		var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ID);
		node.setPrimaryValue("minecraft:stone");
		assertTrue(core.beginRuleEdit(node));
		AtomicBoolean dirty = new AtomicBoolean(false);
		var candidate = new EditorRulesPanel(state, null, () -> dirty.set(true), null, null);
		candidate.setDirtyGate(dirty::get, dirty::set);
		field(candidate, "editingNode", node);
		field(candidate, "transactionDirtySnapshot", false);
		field(candidate, "modal", modalValue("FORM"));

		invoke(candidate, "confirmEditor");

		assertFalse(dirty.get());
		assertFalse(core.hasRuleEditTransaction());
	}

	@Test
	void nbtFormsRejectInvalidNativeValuesAndAcceptValidOnes() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.NBT);
		edit(node, true);
		modal("FORM");
		node.setPrimaryValue("1b");

		invoke("confirmEditor");

		assertTrue(core.hasRuleEditTransaction());
		assertEquals(Set.of(RuleFieldRole.PRIMARY_VALUE), invalidRoles(panel));
		node.setPrimaryValue("{display:{Name:'Relic'}}");
		invoke("confirmEditor");
		assertFalse(core.hasRuleEditTransaction());
		assertEquals(com.starskyxiii.collapsible_groups.group.filter.Filters.nbt("{display:{Name:'Relic'}}"), core.buildCurrentFilter().orElseThrow());
	}

	@Test
	void nbtPathFormMarksPathAndValueFailuresIndependently() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.NBT_PATH);
		edit(node, true);
		modal("FORM");
		node.setPrimaryValue("display[");
		node.setSecondaryValue("1b");

		invoke("confirmEditor");

		assertEquals(Set.of(RuleFieldRole.PRIMARY_VALUE), invalidRoles(panel));
		node.setPrimaryValue("display.Name");
		node.setSecondaryValue("[");
		invoke("confirmEditor");
		assertEquals(Set.of(RuleFieldRole.SECONDARY_VALUE), invalidRoles(panel));
		node.setSecondaryValue("'Relic'");
		invoke("confirmEditor");
		assertFalse(core.hasRuleEditTransaction());
	}

	@Test
	void nbtFormFieldsUseTheNativeInputBound() {
		assertEquals(65_536, EditorRulesPanel.formFieldMaxLength(
			GroupFilterRuleDraft.NodeKind.NBT, RuleFieldRole.PRIMARY_VALUE));
		assertEquals(65_536, EditorRulesPanel.formFieldMaxLength(
			GroupFilterRuleDraft.NodeKind.NBT_PATH, RuleFieldRole.PRIMARY_VALUE));
		assertEquals(65_536, EditorRulesPanel.formFieldMaxLength(
			GroupFilterRuleDraft.NodeKind.NBT_PATH, RuleFieldRole.SECONDARY_VALUE));
		assertEquals(512, EditorRulesPanel.formFieldMaxLength(
			GroupFilterRuleDraft.NodeKind.ID, RuleFieldRole.PRIMARY_VALUE));
	}

	@Test
	void reopeningNbtFormsKeepsValuesLongerThanTheOrdinaryFieldLimit() throws Exception {
		String longSnbt = "{text:'" + "a".repeat(600) + "'}";
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.NBT);
		node.setPrimaryValue(longSnbt);
		Font font = testFont();
		var candidate = new EditorRulesPanel(state, font, () -> {}, null, null);
		field(candidate, "editingNode", node);

		invoke(candidate, "openForm");

		assertEquals(longSnbt, ((EditBox) field(candidate, "formPrimary")).getValue());
		invoke(candidate, "cancelEditor");

		String longPath = "a".repeat(600);
		node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.NBT_PATH);
		node.setPrimaryValue(longPath);
		node.setSecondaryValue(longSnbt);
		candidate = new EditorRulesPanel(state, font, () -> {}, null, null);
		field(candidate, "editingNode", node);
		invoke(candidate, "openForm");
		assertEquals(longPath, ((EditBox) field(candidate, "formPrimary")).getValue());
		assertEquals(longSnbt, ((EditBox) field(candidate, "formSecondary")).getValue());
		invoke(candidate, "cancelEditor");
	}

	private static Font testFont() {
		GlyphInfo glyph = new GlyphInfo() {
			@Override
			public float getAdvance() {
				return 6.0F;
			}

			@Override
			public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> glyphStitcher) {
				throw new AssertionError();
			}
		};
		FontSet set = new FontSet(null, new ResourceLocation("collapsible_groups", "test")) {
			@Override
			public GlyphInfo getGlyphInfo(int codepoint, boolean filterFishyGlyphs) {
				return glyph;
			}
		};
		return new Font(id -> set, false);
	}

	@Test
	void nbtReferenceSelectionsUseOnlyCompleteSnapshotValues() {
		var snapshot = new Minecraft1201NbtAccess.Snapshot("{display:{Name:'Relic'}}", List.of(
			new Minecraft1201NbtAccess.PathValue("display.Name", "'Relic'")), true);
		assertEquals(Map.of(RuleFieldRole.PRIMARY_VALUE, "{display:{Name:'Relic'}}"),
			EditorRulesPanel.nbtReferenceValues(GroupFilterRuleDraft.NodeKind.NBT, snapshot, 0));
		assertEquals(Map.of(
			RuleFieldRole.PRIMARY_VALUE, "display.Name",
			RuleFieldRole.SECONDARY_VALUE, "'Relic'"),
			EditorRulesPanel.nbtReferenceValues(GroupFilterRuleDraft.NodeKind.NBT_PATH, snapshot, 0));
		assertTrue(EditorRulesPanel.nbtReferenceValues(GroupFilterRuleDraft.NodeKind.NBT,
			new Minecraft1201NbtAccess.Snapshot("", List.of(), true), 0).isEmpty());
		assertTrue(EditorRulesPanel.nbtReferenceValues(GroupFilterRuleDraft.NodeKind.NBT_PATH,
			snapshot, 1).isEmpty());
	}

	@SuppressWarnings("unchecked")
	private static Set<RuleFieldRole> invalidRoles(EditorRulesPanel target) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField("formInvalidRoles");
		field.setAccessible(true);
		return Set.copyOf((Set<RuleFieldRole>) field.get(target));
	}

	@Test
	void formModalUsesViewportOnlyWhenReferenceFieldsOutgrowTheRulesBody() {
		EditorChrome.Rect body = new EditorChrome.Rect(40, 60, 300, 155);
		EditorChrome.Rect viewport = new EditorChrome.Rect(0, 0, 400, 240);
		int twoFieldHeight = EditorRulesPanel.formDesiredHeight(9, 2, true, false);
		int threeFieldHeight = EditorRulesPanel.formDesiredHeight(9, 3, true, false);

		EditorChrome.Rect twoFields = EditorRulesPanel.fitFormModalRect(
			body, viewport, 250, twoFieldHeight);
		EditorChrome.Rect threeFields = EditorRulesPanel.fitFormModalRect(
			body, viewport, 250, threeFieldHeight);
		EditorChrome.Rect viewportClamped = EditorRulesPanel.fitFormModalRect(
			body, viewport, 250, 260);

		assertEquals(new EditorChrome.Rect(65, 72, 250, twoFieldHeight), twoFields);
		assertEquals(new EditorChrome.Rect(75, 42, 250, threeFieldHeight), threeFields);
		assertEquals(new EditorChrome.Rect(75, 6, 250, 228), viewportClamped);
	}

	@Test
	void componentPathViewportLayoutKeepsTheLastFieldAboveTheActionRow() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);
		node.setPrimaryValue("minecraft:food");
		node.setSecondaryValue("saturation");
		node.setTertiaryValue("9.6");
		Font font = testFont();
		var candidate = new EditorRulesPanel(state, font, () -> {}, null, null);
		field(candidate, "editingNode", node);
		invoke(candidate, "openForm");

		assertNotNull(field(candidate, "formPrimary"));
		assertNotNull(field(candidate, "formSecondary"));
		assertNotNull(field(candidate, "formTertiary"));
		int desiredHeight = EditorRulesPanel.formDesiredHeight(font.lineHeight, 3, true, false);
		EditorChrome.Rect modal = EditorRulesPanel.fitFormModalRect(
			new EditorChrome.Rect(40, 60, 300, 155),
			new EditorChrome.Rect(0, 0, 400, 240), 250, desiredHeight);
		var fieldsYMethod = EditorRulesPanel.class.getDeclaredMethod("formFieldsY", EditorChrome.Rect.class);
		fieldsYMethod.setAccessible(true);
		var confirmMethod = EditorRulesPanel.class.getDeclaredMethod("formConfirmRect", EditorChrome.Rect.class);
		confirmMethod.setAccessible(true);
		int fieldsY = (int) fieldsYMethod.invoke(candidate, modal);
		EditorChrome.Rect confirm = (EditorChrome.Rect) confirmMethod.invoke(candidate, modal);
		var fieldHeight = EditorRulesPanel.class.getDeclaredField("FIELD_H");
		var fieldGap = EditorRulesPanel.class.getDeclaredField("FIELD_GAP");
		fieldHeight.setAccessible(true);
		fieldGap.setAccessible(true);
		int lastFieldBottom = fieldsY + 2 * (fieldHeight.getInt(null) + fieldGap.getInt(null))
			+ fieldHeight.getInt(null);
		assertTrue(lastFieldBottom <= confirm.y());
		invoke(candidate, "cancelEditor");
	}

	private static Object modalValue(String name) throws Exception {
		var field = EditorRulesPanel.class.getDeclaredField("modal");
		return java.util.Arrays.stream(field.getType().getEnumConstants())
			.filter(kind -> kind.toString().equals(name)).findFirst().orElseThrow();
	}

	@ParameterizedTest @EnumSource(value = GroupFilterRuleDraft.NodeKind.class, names = {"ID", "TAG", "NAMESPACE"})
	void genericValuesUseTheGenericPickerEligibilityWhileBuiltinsKeepTheirExistingFlow(GroupFilterRuleDraft.NodeKind kind) throws Exception {
		var method = EditorRulesPanel.class.getDeclaredMethod("canChangeType");
		method.setAccessible(true);
		var node = core.insertRuleRelative(kind);
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
		assertEquals(List.of(GroupFilterRuleDraft.NodeKind.ID, GroupFilterRuleDraft.NodeKind.TAG, GroupFilterRuleDraft.NodeKind.NAMESPACE), kinds);
	}

	@Test void invalidManualIdRemainsSubjectToRuleValidation() throws Exception {
		var node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.ID);
		node.setIngredientType("emi:mekanism_chemical");
		edit(node, true);
		modal("FORM");
		setValue("Invalid ID!");
		assertFalse(core.currentValidationErrors().isEmpty());
		invoke("cancelEditor");
		assertFalse(core.hasRuleEditTransaction());
	}
}
