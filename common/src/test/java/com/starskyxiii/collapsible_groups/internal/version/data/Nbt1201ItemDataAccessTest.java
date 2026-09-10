package com.starskyxiii.collapsible_groups.internal.version.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Nbt1201ItemDataAccessTest {
	@Test
	void exactRoundTripPreservesNumericTypesListsTypedArraysAndMissingFields() {
		Nbt1201ItemDataAccess access = new Nbt1201ItemDataAccess();
		Map<String, Nbt1201ItemDataAccess.NbtNode> values = new LinkedHashMap<>();
		values.put("byte", new Nbt1201ItemDataAccess.NbtByte((byte) 1));
		values.put("short", new Nbt1201ItemDataAccess.NbtShort((short) 1));
		values.put("int", new Nbt1201ItemDataAccess.NbtInt(1));
		values.put("long", new Nbt1201ItemDataAccess.NbtLong(1L));
		values.put("float", new Nbt1201ItemDataAccess.NbtFloat(1.25F));
		values.put("double", new Nbt1201ItemDataAccess.NbtDouble(1.25D));
		values.put("list", new Nbt1201ItemDataAccess.NbtList((byte) 2, List.of(
			new Nbt1201ItemDataAccess.NbtShort((short) 2),
			new Nbt1201ItemDataAccess.NbtShort((short) 3))));
		values.put("bytes", new Nbt1201ItemDataAccess.NbtByteArray(List.of((byte) 1, (byte) -2)));
		values.put("ints", new Nbt1201ItemDataAccess.NbtIntArray(List.of(1, -2)));
		values.put("longs", new Nbt1201ItemDataAccess.NbtLongArray(List.of(1L, -2L)));
		Nbt1201ItemDataAccess.NbtStack stack = new Nbt1201ItemDataAccess.NbtStack(
			"minecraft:diamond_sword", new Nbt1201ItemDataAccess.NbtCompound(values));

		String legacy = access.exactStacks().encodeLegacy(stack).orElseThrow();
		ItemDataPayload payload = access.exactStacks().encodePayload(stack).orElseThrow();
		assertEquals(stack, access.exactStacks().beginDecode().decode(legacy).orElseThrow());
		assertEquals(stack, access.exactStacks().beginDecode().decode(payload.data().getAsString()).orElseThrow());
		assertEquals("test:typed_nbt", payload.dataFormat());

		Map<String, ItemDataAccess.DataReference<Nbt1201ItemDataAccess.NbtNode>> references =
			access.enumerateData(stack).stream().collect(java.util.stream.Collectors.toMap(
				ItemDataAccess.DataReference::dataTypeId, reference -> reference));
		assertInstanceOf(Nbt1201ItemDataAccess.NbtByte.class, references.get("byte").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtShort.class, references.get("short").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtInt.class, references.get("int").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtLong.class, references.get("long").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtList.class, references.get("list").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtByteArray.class, references.get("bytes").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtIntArray.class, references.get("ints").encodedValueNode());
		assertInstanceOf(Nbt1201ItemDataAccess.NbtLongArray.class, references.get("longs").encodedValueNode());
		assertFalse(references.containsKey("missing"));
		assertFalse(access.matchesDataValue(stack, "missing", "1"));
		assertEquals(List.of("byte", "bytes", "double", "float", "int", "ints", "list", "list[0]", "list[1]", "long", "longs", "short"),
			access.enumeratePaths(stack.tag()).stream().map(ItemDataAccess.DataPath::path).toList());
	}

	@Test
	void numericTagTypeAndListElementTypeArePartOfEquivalence() {
		Nbt1201ItemDataAccess access = new Nbt1201ItemDataAccess();
		Nbt1201ItemDataAccess.NbtStack intStack = stack(Map.of(
			"number", new Nbt1201ItemDataAccess.NbtInt(1),
			"list", new Nbt1201ItemDataAccess.NbtList((byte) 3, List.of(new Nbt1201ItemDataAccess.NbtInt(1)))));
		Nbt1201ItemDataAccess.NbtStack longStack = stack(Map.of(
			"number", new Nbt1201ItemDataAccess.NbtLong(1),
			"list", new Nbt1201ItemDataAccess.NbtList((byte) 4, List.of(new Nbt1201ItemDataAccess.NbtLong(1)))));

		assertNotEquals(intStack, longStack);
		assertFalse(access.exactStacks().equivalent(intStack, longStack));
		assertTrue(access.matchesDataPath(intStack, "list", "[0]", "1"));
		assertFalse(access.matchesDataPath(intStack, "list", "[0]", "1L"));
		assertNotEquals(new Nbt1201ItemDataAccess.NbtByteArray(List.of((byte) 1)),
			new Nbt1201ItemDataAccess.NbtIntArray(List.of(1)));
		assertThrows(IllegalArgumentException.class, () -> new Nbt1201ItemDataAccess.NbtList(
			(byte) 3, List.of(new Nbt1201ItemDataAccess.NbtLong(1L))));
	}

	private static Nbt1201ItemDataAccess.NbtStack stack(Map<String, Nbt1201ItemDataAccess.NbtNode> tag) {
		return new Nbt1201ItemDataAccess.NbtStack("minecraft:stone", new Nbt1201ItemDataAccess.NbtCompound(tag));
	}
}
