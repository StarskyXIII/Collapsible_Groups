package com.starskyxiii.collapsible_groups.client.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

/** Safely obtains item tooltips without allowing third-party tooltip failures to escape. */
final class EditorItemTooltipHelper {
	private EditorItemTooltipHelper() {}

	static List<Component> tooltipLines(ItemStack stack) {
		return tooltipLines(stack, () -> minimalTooltip(stack));
	}

	static List<Component> tooltipLines(ItemStack stack, List<Component> fallback) {
		return tooltipLines(stack, () -> fallback);
	}

	private static List<Component> tooltipLines(ItemStack stack, Supplier<List<Component>> fallback) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			Item.TooltipContext context = minecraft.level == null
				? Item.TooltipContext.EMPTY
				: Item.TooltipContext.of(minecraft.level);
			return stack.getTooltipLines(context, minecraft.player, TooltipFlag.Default.NORMAL);
		} catch (RuntimeException | LinkageError ignored) {
			return fallback.get();
		}
	}

	private static List<Component> minimalTooltip(ItemStack stack) {
		String registryId;
		try {
			registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		} catch (RuntimeException | LinkageError ignored) {
			registryId = "unknown";
		}

		Component name;
		try {
			name = stack.getHoverName();
		} catch (RuntimeException | LinkageError ignored) {
			name = Component.literal(registryId);
		}
		return List.of(name, Component.literal(registryId).withStyle(ChatFormatting.DARK_GRAY));
	}
}
