package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

public final class ItemDataAccesses {
	private static final Minecraft121ItemDataAccess CURRENT = new Minecraft121ItemDataAccess();

	private ItemDataAccesses() {}

	public static ItemDataAccess<ItemStack, JsonElement> current() {
		return CURRENT;
	}

	public static Minecraft121ItemDataAccess minecraft121() {
		return CURRENT;
	}
}
