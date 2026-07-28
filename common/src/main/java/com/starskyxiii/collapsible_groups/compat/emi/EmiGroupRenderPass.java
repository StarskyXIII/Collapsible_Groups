package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import com.starskyxiii.collapsible_groups.client.preview.ConnectedSlotBorderRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.platform.Services;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stateless drawing helpers; each EMI ScreenSpace owns its current-frame positions. */
public final class EmiGroupRenderPass {
	private EmiGroupRenderPass() {}

	public static GroupBackgroundRenderer.BackgroundPosition positionFor(EmiIngredient ingredient, int x, int y) {
		if (ingredient instanceof GroupHeaderEmiStack header) {
			return new GroupBackgroundRenderer.BackgroundPosition(
				GroupBackgroundRenderer.Kind.HEADER, header.groupId(), x, y);
		}
		if (ingredient instanceof ProjectedChildEmiIngredient child) {
			return new GroupBackgroundRenderer.BackgroundPosition(
				GroupBackgroundRenderer.Kind.CHILD, child.parentGroupId(), x, y);
		}
		return null;
	}

	public static void drawTints(GuiGraphics graphics,
		List<GroupBackgroundRenderer.BackgroundPosition> positions) {
		if (!Services.CONFIG.showGroupBackgrounds()) return;
		for (var position : positions) {
			int color = switch (position.kind()) {
				case HEADER -> GroupThemeResolver.headerBackgroundColor(position.groupId(),
					GroupRepository.isExpandedById(position.groupId()));
				case CHILD -> GroupThemeResolver.expandedGroupBackgroundColor(position.groupId());
			};
			graphics.fill(position.x() - 1, position.y() - 1,
				position.x() + 17, position.y() + 17, color);
		}
	}

	public static void drawBorders(GuiGraphics graphics,
		List<GroupBackgroundRenderer.BackgroundPosition> positions) {
		Map<String, List<int[]>> childrenByGroup = new LinkedHashMap<>();
		for (var position : positions) {
			if (position.kind() == GroupBackgroundRenderer.Kind.CHILD) {
				childrenByGroup.computeIfAbsent(position.groupId(), ignored -> new ArrayList<>())
					.add(new int[]{position.x(), position.y()});
			}
		}
		if (childrenByGroup.isEmpty()) return;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		try {
			for (var entry : childrenByGroup.entrySet()) {
				ConnectedSlotBorderRenderer.drawBorder(graphics, entry.getValue(),
					GroupThemeResolver.expandedGroupBorderColor(entry.getKey()));
			}
		} finally {
			graphics.pose().popPose();
		}
	}
}
