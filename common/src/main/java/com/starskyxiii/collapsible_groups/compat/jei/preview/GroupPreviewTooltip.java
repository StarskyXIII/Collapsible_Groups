package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the group-header hover tooltip (name, count label, optional preview grid,
 * and expand/collapse action hint) shared by the live JEI element and the editor's
 * settings-mode preview.
 *
 * <p>Mirrors the composition of {@code GroupHeaderElement.getTooltip} and
 * {@code MixinIngredientFilter#cg$buildCountLabel} so the editor preview reads the
 * same as the real JEI element. Kept in {@code common} so the
 * three loader screens share one decision path and stay byte-identical.
 */
public final class GroupPreviewTooltip {

	/** Text lines plus an optional visual preview component (collapsed only). */
	public record Result(List<Component> lines, Optional<TooltipComponent> visual) {
		public Result {
			lines = List.copyOf(lines);
		}
	}

	private GroupPreviewTooltip() {}

	/**
	 * @param displayName group display name (already resolved to a fallback)
	 * @param nameColorRgb 0xRRGGBB name color (draft-resolved for the preview)
	 * @param expanded    whether the previewed header is currently expanded
	 * @param previewEntries entries for the collapsed preview grid (live-aligned)
	 */
	public static Result build(String displayName, int nameColorRgb, int itemCount, int fluidCount,
	                           int genericCount, boolean expanded, List<GroupPreviewEntry> previewEntries) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(displayName)
			.withStyle(style -> style.withColor(TextColor.fromRgb(nameColorRgb))));
		lines.add(buildCountLabel(itemCount, fluidCount, genericCount));
		String actionKey = expanded ? ModTranslationKeys.TOOLTIP_COLLAPSE : ModTranslationKeys.TOOLTIP_EXPAND;
		lines.add(Component.translatable(actionKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		Optional<TooltipComponent> visual = expanded
			? Optional.empty()
			: Optional.of(new PreviewTooltipComponent(previewEntries));
		return new Result(lines, visual);
	}

	/** Mirrors {@code MixinIngredientFilter#cg$buildCountLabel}. */
	public static Component buildCountLabel(int itemCount, int fluidCount, int genericCount) {
		MutableComponent result = null;
		if (itemCount > 0) {
			result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, itemCount);
		}
		if (fluidCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_FLUIDS, fluidCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		if (genericCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_ENTRIES, genericCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		return (result != null ? result : Component.empty()).withStyle(ChatFormatting.GRAY);
	}
}
