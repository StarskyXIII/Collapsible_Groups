package com.starskyxiii.collapsible_groups.internal.version.data;

public final class MinecraftItemDataFormats {
	public static final ItemDataFormat EXACT_STACK_1_20_1 = new ItemDataFormat(ItemDataPayload.NBT);
	public static final ItemDataFormat EXACT_STACK_1_21_1 = new ItemDataFormat(ItemDataPayload.ITEM_COMPONENTS);
	public static final ItemDataFormat COMPONENT_VALUE_1_21_1 = new ItemDataFormat(ItemDataPayload.DATA_COMPONENT);
	public static final ItemDataFormat NBT_VALUE_1_20_1 = new ItemDataFormat(ItemDataPayload.NBT);
	private MinecraftItemDataFormats() {}
}
