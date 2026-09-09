package com.starskyxiii.collapsible_groups.internal.version.data;

public final class MinecraftItemDataFormats {
	public static final ItemDataFormat EXACT_STACK_1_20_1 = new ItemDataFormat(
		"collapsible_groups:exact_stack", 1, "minecraft:item_stack_nbt_snbt", "1.20.1");
	public static final ItemDataFormat EXACT_STACK_1_21_1 = new ItemDataFormat(
		"collapsible_groups:exact_stack", 1, "minecraft:item_components", "1.21.1");
	public static final ItemDataFormat COMPONENT_VALUE_1_21_1 = new ItemDataFormat(
		"collapsible_groups:component_value", 1, "minecraft:data_component", "1.21.1");
	public static final ItemDataFormat NBT_VALUE_1_20_1 = new ItemDataFormat(
		"collapsible_groups:nbt_value", 1, "minecraft:nbt_snbt", "1.20.1");

	private MinecraftItemDataFormats() {}
}
