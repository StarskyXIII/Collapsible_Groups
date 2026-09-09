package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewEntry;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewFallbacks;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewLayout;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewRect;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewTooltip;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

interface EditorPresentationAccess {
	default void closeEditor() {}
	default Object previewGeneration() { return this; }
	void renderFluid(GuiGraphics graphics, EditorFluidIngredientView entry, int x, int y);
	void renderGeneric(GuiGraphics graphics, EditorGenericIngredientView entry, int x, int y);
	List<Component> fluidTooltip(EditorFluidIngredientView entry);
	List<Component> genericTooltip(EditorGenericIngredientView entry);
	List<PreviewEntry> resolveHeaderIcons(List<GroupIconDefinition> iconIds, List<PreviewEntry> fallbackEntries);
	PreviewLayout renderPreview(GuiGraphics graphics, PreviewRect area, boolean expanded, int page,
		AppearanceDraft appearance, List<PreviewEntry> headerIcons, List<PreviewEntry> entries, Font font,
		PreviewFallbacks fallbacks);
	PreviewLayout layoutPreview(PreviewRect area, boolean expanded, int itemCount, int page);
	PreviewTooltip previewTooltip(String displayName, int nameColorRgb, int itemCount, int fluidCount,
		int genericCount, boolean expanded, List<PreviewEntry> entries);
}
