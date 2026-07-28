package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewTooltip;
import com.starskyxiii.collapsible_groups.client.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Non-serializable synthetic EMI stack representing one projected group header. */
public final class GroupHeaderEmiStack extends EmiStack {
	private final ViewerProjection.GroupHeader<EmiIngredient> header;
	private final GroupHeaderKey key;
	private final ResourceLocation id;

	public GroupHeaderEmiStack(ViewerProjection.GroupHeader<EmiIngredient> header) {
		this.header = header;
		this.key = new GroupHeaderKey(header.group().id());
		String hash = UUID.nameUUIDFromBytes(header.group().id().getBytes(StandardCharsets.UTF_8))
			.toString().replace("-", "");
		this.id = ResourceLocation.fromNamespaceAndPath("collapsible_groups", "group/" + hash);
	}

	public String groupId() { return header.group().id(); }
	public ViewerProjection.GroupHeader<EmiIngredient> header() { return header; }

	@Override public EmiStack copy() { return new GroupHeaderEmiStack(header); }
	@Override public boolean isEmpty() { return false; }
	@Override public DataComponentPatch getComponentChanges() { return DataComponentPatch.EMPTY; }
	@Override public Object getKey() { return key; }
	@Override public ResourceLocation getId() { return id; }
	@Override public Component getName() { return header.group().displayName().toComponent(); }

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
		List<ViewerIngredient<EmiIngredient>> previews = header.fallbackIconIngredients();
		int previewFlags = flags & ~EmiIngredient.RENDER_AMOUNT;
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(0.9f, 0.9f, 1f);
		if (previews.size() == 1) {
			previews.get(0).entry().render(graphics, 1, 1, delta, previewFlags);
		} else if (!previews.isEmpty()) {
			previews.get(1).entry().render(graphics, 2, 0, delta, previewFlags);
			graphics.pose().translate(0, 0, 10);
			previews.get(0).entry().render(graphics, 0, 2, delta, previewFlags);
		}
		graphics.pose().popPose();
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		String marker = header.expanded() ? "-" : "+";
		graphics.drawString(Minecraft.getInstance().font, marker, x + 10, y + 9, 0xffffffff, true);
		graphics.pose().popPose();
	}

	@Override
	public List<Component> getTooltipText() {
		return List.of(themedName(), GroupPreviewTooltip.buildCountLabel(
			header.itemCount(), header.fluidCount(), header.genericCount()), actionHint());
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> result = new ArrayList<>();
		result.add(ClientTooltipComponent.create(themedName().getVisualOrderText()));
		result.add(ClientTooltipComponent.create(GroupPreviewTooltip.buildCountLabel(
			header.itemCount(), header.fluidCount(), header.genericCount()).getVisualOrderText()));
		if (!header.expanded()) result.add(new PreviewTooltipComponent(previewEntries()));
		result.add(ClientTooltipComponent.create(actionHint().getVisualOrderText()));
		return List.copyOf(result);
	}

	private Component themedName() {
		return getName().copy().withStyle(style -> style.withColor(
			TextColor.fromRgb(GroupThemeResolver.groupNameColor(groupId()))));
	}

	private Component actionHint() {
		String key = header.expanded() ? ModTranslationKeys.TOOLTIP_COLLAPSE : ModTranslationKeys.TOOLTIP_EXPAND;
		return Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	}

	private List<GroupPreviewEntry> previewEntries() {
		return header.children().stream().map(child -> GroupPreviewEntry.ofRenderer((graphics, x, y) ->
			child.entry().render(graphics, x, y, 0, EmiIngredient.RENDER_ICON))).toList();
	}

	public record GroupHeaderKey(String groupId) {}
}
