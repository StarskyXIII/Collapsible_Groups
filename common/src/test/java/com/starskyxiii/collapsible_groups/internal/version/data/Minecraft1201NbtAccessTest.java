package com.starskyxiii.collapsible_groups.internal.version.data;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Minecraft1201NbtAccessTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rootAndPathMatchersUseStrictNativeTagEquality() {
		ItemStack stack = new ItemStack(Items.STONE);
		CompoundTag tag = new CompoundTag();
		tag.putByte("number", (byte) 1);
		tag.put("bytes", new ByteArrayTag(new byte[] { 1, 2 }));
		tag.put("ints", new IntArrayTag(new int[] { 1, 2 }));
		tag.put("longs", new LongArrayTag(new long[] { 1L, 2L }));
		ListTag list = new ListTag();
		list.add(StringTag.valueOf("first"));
		list.add(StringTag.valueOf("second"));
		tag.put("list", list);
		stack.setTag(tag);

		assertTrue(Minecraft1201NbtAccess.compileRoot(tag.toString()).orElseThrow().matches(stack));
		CompoundTag unusual = new CompoundTag();
		unusual.putString("", "empty");
		unusual.putString("quote\"slash\\", "escaped");
		tag.put("a.b:c", unusual);
		stack.setTag(tag);

		assertFalse(Minecraft1201NbtAccess.compilePath("number", "1").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("number", "1b").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("bytes[1]", "2b").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("ints[1]", "2").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("longs[1]", "2L").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("list[0]", "\"first\"").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("[\"a.b:c\"][\"\"]", "\"empty\"").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("[\"a.b:c\"][\"quote\\\"slash\\\\\"]", "\"escaped\"").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("missing", "1b").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("list[2]", "\"missing\"").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("number", "\"1\"").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("ints", "[1,2]").orElseThrow().matches(stack));
		assertTrue(Minecraft1201NbtAccess.compilePath("ints", "[I;1,2]").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("list", "[\"second\",\"first\"]").orElseThrow().matches(stack));
		assertFalse(Minecraft1201NbtAccess.compilePath("list", "[\"first\"]").orElseThrow().matches(stack));
	}

	@Test
	void rootRuleIgnoresItemIdentityAndCountWhileExactStackDoesNot() {
		CompoundTag tag = new CompoundTag();
		tag.putString("mode", "same");
		ItemStack stone = new ItemStack(Items.STONE, 3);
		stone.setTag(tag.copy());
		ItemStack dirt = new ItemStack(Items.DIRT, 17);
		dirt.setTag(tag.copy());

		var matcher = Minecraft1201NbtAccess.compileRoot("{mode:\"same\"}").orElseThrow();
		assertTrue(matcher.matches(stone));
		assertTrue(matcher.matches(dirt));
		assertFalse(new Minecraft1201ItemDataAccess().exactStacks().equivalent(stone, dirt));
	}

	@Test
	void grammarIsStrictAsciiAndBounded() {
		for (String valid : List.of("safe", "safe-key_2", "safe[0][1]", "[\"\"]", "[\"a.b:c\"]", "a[0].b", "[\"\\u0061\"]")) {
			assertTrue(Minecraft1201NbtAccess.validPath(valid), valid);
		}
		for (String invalid : List.of("", ".a", "a.", "a[*]", "a[-1]", "a[١]", "[abc\"]", "[\"line\nbreak\"]", "[\"\\q\"]", "[\"\\uＦＦＦＦ\"]", "[\"unterminated]", "a[2147483648]", "a..b", "a[0].[\"b\"]")) {
			assertFalse(Minecraft1201NbtAccess.validPath(invalid), invalid);
		}
		String sixtyFiveSteps = String.join(".", java.util.Collections.nCopies(65, "a"));
		assertFalse(Minecraft1201NbtAccess.validPath(sixtyFiveSteps));
	}

	@Test
	void rootRequiresCompoundAndParserRequiresFullInputAndBoundedNesting() {
		assertTrue(Minecraft1201NbtAccess.canonicalRoot("{value:1b}").isPresent());
		assertTrue(Minecraft1201NbtAccess.canonicalRoot("1b").isEmpty());
		assertTrue(Minecraft1201NbtAccess.canonicalValue("1b trailing").isEmpty());
		assertTrue(Minecraft1201NbtAccess.canonicalValue("[I;1,2]").isPresent());
		assertEquals("[B;1B,2B]", Minecraft1201NbtAccess.canonicalValue("[B;1b,2b]").orElseThrow());
		String nested = "{" + "a:{".repeat(64) + "v:1" + "}".repeat(65);
		assertTrue(Minecraft1201NbtAccess.canonicalValue(nested).isEmpty());
	}

	@Test
	void snapshotsAreCopiedSortedBoundedAndTypePreserving() {
		ItemStack stack = new ItemStack(Items.STONE);
		CompoundTag tag = new CompoundTag();
		tag.putInt("z", 1);
		tag.putByte("a", (byte) 1);
		tag.put("ints", new IntArrayTag(new int[] { 3, 4 }));
		stack.setTag(tag);
		String nativeRoot = tag.toString();

		Minecraft1201NbtAccess.Snapshot snapshot = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		tag.putInt("later", 2);
		assertEquals(List.of("a", "ints", "ints[0]", "ints[1]", "z"),
			snapshot.paths().stream().map(Minecraft1201NbtAccess.PathValue::path).toList());
		assertEquals("1b", snapshot.paths().get(0).valueSnbt());
		assertEquals("3", snapshot.paths().get(2).valueSnbt());
		assertEquals(tag.get("ints").toString(), snapshot.paths().get(1).valueSnbt());
		assertEquals(nativeRoot, snapshot.rootSnbt());
		assertEquals(nativeRoot, Minecraft1201NbtAccess.canonicalRoot(snapshot.rootSnbt()).orElseThrow());
		assertFalse(snapshot.rootSnbt().contains("later"));
		assertFalse(snapshot.truncated());

		CompoundTag large = new CompoundTag();
		for (int i = 0; i < Minecraft1201NbtAccess.MAX_SNAPSHOT_PATHS + 10; i++) large.putInt("k" + i, i);
		stack.setTag(large);
		Minecraft1201NbtAccess.Snapshot truncated = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertTrue(truncated.truncated());
		assertTrue(truncated.paths().size() <= Minecraft1201NbtAccess.MAX_SNAPSHOT_PATHS);
	}

	@Test
	void snapshotOffersOnlyValuesAndPathsAcceptedByThePublicContracts() {
		ItemStack stack = new ItemStack(Items.STONE);
		CompoundTag accepted = nestedCompound(Minecraft1201NbtAccess.MAX_NESTING);
		stack.setTag(accepted);
		Minecraft1201NbtAccess.Snapshot acceptedSnapshot = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertTrue(Minecraft1201NbtAccess.canonicalRoot(acceptedSnapshot.rootSnbt()).isPresent());
		assertTrue(acceptedSnapshot.paths().stream().allMatch(pathValue ->
			Minecraft1201NbtAccess.validPath(pathValue.path())
				&& Minecraft1201NbtAccess.canonicalValue(pathValue.valueSnbt()).isPresent()));

		CompoundTag tooDeep = nestedCompound(Minecraft1201NbtAccess.MAX_NESTING + 1);
		stack.setTag(tooDeep);
		Minecraft1201NbtAccess.Snapshot deepSnapshot = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertEquals("", deepSnapshot.rootSnbt());
		assertTrue(deepSnapshot.truncated());
		assertTrue(deepSnapshot.paths().stream().allMatch(pathValue ->
			Minecraft1201NbtAccess.validPath(pathValue.path())
				&& Minecraft1201NbtAccess.canonicalValue(pathValue.valueSnbt()).isPresent()));

		CompoundTag longKey = new CompoundTag();
		longKey.putInt("x".repeat(Minecraft1201NbtAccess.MAX_EXPECTED_LENGTH + 1), 1);
		stack.setTag(longKey);
		Minecraft1201NbtAccess.Snapshot longPathSnapshot = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertTrue(longPathSnapshot.paths().isEmpty());
		assertTrue(longPathSnapshot.truncated());
	}

	@Test
	void snapshotSkipsUnrepresentableValuesButKeepsMatchableDescendantsWithinVisitBudget() {
		ItemStack stack = new ItemStack(Items.STONE);
		CompoundTag root = new CompoundTag();
		CompoundTag unusual = new CompoundTag();
		unusual.putInt("", 7);
		root.put("weird", unusual);
		root.putFloat("nan", Float.NaN);
		root.putDouble("infinity", Double.POSITIVE_INFINITY);
		stack.setTag(root);

		Minecraft1201NbtAccess.Snapshot snapshot = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertEquals("", snapshot.rootSnbt());
		assertTrue(snapshot.truncated());
		assertFalse(snapshot.paths().stream().anyMatch(pathValue ->
			pathValue.path().equals("nan") || pathValue.path().equals("infinity") || pathValue.path().equals("weird")));
		assertTrue(snapshot.paths().stream().anyMatch(pathValue ->
			pathValue.path().equals("weird[\"\"]") && pathValue.valueSnbt().equals("7")));
		assertTrue(snapshot.paths().stream().allMatch(pathValue ->
			Minecraft1201NbtAccess.compilePath(pathValue.path(), pathValue.valueSnbt())
				.orElseThrow().matches(stack)));

		CompoundTag overBudget = new CompoundTag();
		for (int i = 0; i < Minecraft1201NbtAccess.MAX_SNAPSHOT_VISITS + 10; i++) {
			overBudget.putFloat("nan" + i, Float.NaN);
		}
		stack.setTag(overBudget);
		Minecraft1201NbtAccess.Snapshot bounded = Minecraft1201NbtAccess.snapshot(stack).orElseThrow();
		assertTrue(bounded.truncated());
		assertTrue(bounded.paths().isEmpty());
	}

	private static CompoundTag nestedCompound(int containerCount) {
		CompoundTag root = new CompoundTag();
		CompoundTag current = root;
		for (int i = 1; i < containerCount; i++) {
			CompoundTag child = new CompoundTag();
			current.put("a", child);
			current = child;
		}
		current.putInt("value", 1);
		return root;
	}

	@Test
	void absenceIsDistinctFromPresentEmptyCompound() {
		ItemStack absent = new ItemStack(Items.STONE);
		ItemStack empty = new ItemStack(Items.STONE);
		empty.setTag(new CompoundTag());
		var matcher = Minecraft1201NbtAccess.compileRoot("{}").orElseThrow();
		assertFalse(matcher.matches(absent));
		assertTrue(matcher.matches(empty));
		assertTrue(Minecraft1201NbtAccess.snapshot(absent).isEmpty());
		assertEquals("{}", Minecraft1201NbtAccess.snapshot(empty).orElseThrow().rootSnbt());
	}
}
