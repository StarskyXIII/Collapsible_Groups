package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonPrimitive;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Minecraft1201ItemDataAccessTest {
	private final Minecraft1201ItemDataAccess access = new Minecraft1201ItemDataAccess();

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void nativeNbtRoundTripsWithTypeFidelityAndNormalizedCount() {
		CompoundTag tag = new CompoundTag();
		tag.putByte("number", (byte) 4);
		tag.putInt("integer", 4);
		ListTag list = new ListTag();
		list.add(StringTag.valueOf("first"));
		list.add(StringTag.valueOf("second"));
		tag.put("list", list);
		tag.put("bytes", new ByteArrayTag(new byte[] { 1, 2 }));
		tag.put("ints", new IntArrayTag(new int[] { 3, 4 }));
		tag.put("longs", new LongArrayTag(new long[] { 5L, 6L }));
		ItemStack source = new ItemStack(Items.DIAMOND_SWORD, 17);
		source.setTag(tag);

		String encoded = access.exactStacks().encodeEnvelope(source).orElseThrow();
		assertEquals(17, source.getCount());
		assertEquals(tag, source.getTag());
		assertEquals(VersionedDataEnvelope.Support.CURRENT, access.exactStacks().support(encoded));
		ItemStack decoded = access.exactStacks().beginDecode().decode(encoded).orElseThrow();

		assertEquals(1, decoded.getCount());
		assertTrue(access.exactStacks().equivalent(source, decoded));
		assertEquals((byte) 4, decoded.getTag().getByte("number"));
		assertEquals(4, decoded.getTag().getInt("integer"));
		assertEquals(tag, decoded.getTag());
	}

	@Test
	void absentAndPresentNbtRemainDistinct() {
		ItemStack plain = new ItemStack(Items.STONE);
		ItemStack tagged = new ItemStack(Items.STONE);
		CompoundTag tag = new CompoundTag();
		tag.putString("marker", "present");
		tagged.setTag(tag);

		assertFalse(access.exactStacks().equivalent(plain, tagged));
		assertTrue(access.exactStacks().beginDecode()
			.decode(access.exactStacks().encodeEnvelope(plain).orElseThrow())
			.map(decoded -> access.exactStacks().equivalent(plain, decoded))
			.orElse(false));
	}

	@Test
	void exactComparisonPreservesNumericTypesAndIgnoresCompoundKeyOrder() {
		ItemStack byteStack = new ItemStack(Items.STONE);
		CompoundTag byteTag = new CompoundTag();
		byteTag.putByte("value", (byte) 1);
		byteTag.putString("name", "same");
		byteStack.setTag(byteTag);

		ItemStack reordered = new ItemStack(Items.STONE);
		CompoundTag reorderedTag = new CompoundTag();
		reorderedTag.putString("name", "same");
		reorderedTag.putByte("value", (byte) 1);
		reordered.setTag(reorderedTag);

		ItemStack intStack = new ItemStack(Items.STONE);
		CompoundTag intTag = new CompoundTag();
		intTag.putString("name", "same");
		intTag.putInt("value", 1);
		intStack.setTag(intTag);

		assertTrue(access.exactStacks().equivalent(byteStack, reordered));
		assertFalse(access.exactStacks().equivalent(byteStack, intStack));
		assertNotEquals(byteTag.get("value"), intTag.get("value"));
	}

	@Test
	void rejectsLegacyForeignAndStructurallyInvalidPayloads() {
		String malformedTag = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.EXACT_STACK_1_20_1,
			new JsonPrimitive("{id:\"minecraft:stone\",Count:1b,tag:3}"));
		String missingId = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.EXACT_STACK_1_20_1,
			new JsonPrimitive("{Count:1b}"));
		String foreign = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.EXACT_STACK_1_21_1,
			new JsonPrimitive("{}"));

		assertTrue(access.exactStacks().beginDecode().decode(malformedTag).isEmpty());
		assertTrue(access.exactStacks().beginDecode().decode(missingId).isEmpty());
		assertTrue(access.exactStacks().beginDecode().decode(foreign).isEmpty());
		assertTrue(access.exactStacks().beginDecode().decode("{\"id\":\"minecraft:stone\"}").isEmpty());
	}
}
