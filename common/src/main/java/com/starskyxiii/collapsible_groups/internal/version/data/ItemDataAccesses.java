package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

public final class ItemDataAccesses {
	private static final Minecraft1201ItemDataAccess CURRENT = new Minecraft1201ItemDataAccess();

	private ItemDataAccesses() {}

	public static ItemDataAccess<ItemStack, JsonElement> current() {
		return CURRENT;
	}

	public static Minecraft1201ItemDataAccess minecraft1201() {
		return CURRENT;
	}
}
