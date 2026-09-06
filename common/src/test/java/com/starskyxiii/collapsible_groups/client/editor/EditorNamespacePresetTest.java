package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodePresentation;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorNamespacePresetTest {
	@Test void builtinNamespaceSourcesAndTitlesFollowNormalizedTypeAndRejectGenericFallback() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var snapshot = EditorRulesPanel.class.getDeclaredMethod("snapshotFor", RuleNodePresentation.PickerKind.class, String.class);
		snapshot.setAccessible(true);
		assertEquals(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::getNamespace).distinct().sorted().toList(),
			snapshot.invoke(null, RuleNodePresentation.PickerKind.NAMESPACE, " ITEM "));
		assertEquals(BuiltInRegistries.FLUID.keySet().stream().map(ResourceLocation::getNamespace).distinct().sorted().toList(),
			snapshot.invoke(null, RuleNodePresentation.PickerKind.NAMESPACE, " FLUID "));
		assertEquals(List.of(), snapshot.invoke(null, RuleNodePresentation.PickerKind.NAMESPACE, "missing:type"));
		var panel = new EditorRulesPanel(null, null, () -> {}, null, null);
		var kind = EditorRulesPanel.class.getDeclaredField("pickerKind");
		kind.setAccessible(true);
		kind.set(panel, RuleNodePresentation.PickerKind.NAMESPACE);
		var editing = EditorRulesPanel.class.getDeclaredField("editingNode");
		editing.setAccessible(true);
		var node = GroupFilterRuleDraft.empty().createNode(GroupFilterRuleDraft.NodeKind.NAMESPACE);
		node.setIngredientType(" FLUID ");
		editing.set(panel, node);
		var title = EditorRulesPanel.class.getDeclaredMethod("pickerTitle");
		title.setAccessible(true);
		assertEquals(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_FLUID_NAMESPACE).getString(), title.invoke(panel));
		node.setIngredientType("item");
		assertEquals(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_ITEM_NAMESPACE).getString(), title.invoke(panel));
	}
}
