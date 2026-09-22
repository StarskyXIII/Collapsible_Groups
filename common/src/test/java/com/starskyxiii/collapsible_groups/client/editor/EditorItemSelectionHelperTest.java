package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorItemSelectionHelperTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest @ValueSource(ints = {32, 256, 6322})
    void wholeItemExpansionDoesOneSelectionIndexPass(int size) {
        AtomicInteger hashes = new AtomicInteger();
        var helper = new EditorItemSelectionHelper(stack -> {
            hashes.incrementAndGet();
            return ItemStack.hashItemAndComponents(stack);
        });
        var selected = new CountingSet();
        selected.add("minecraft:stone");
        selected.add("minecraft:dirt");
        List<ItemStack> variants = new ArrayList<>();
        for (int i = 0; i < size; i++) variants.add(named("variant-" + i));
        helper.removeSingleSelection(variants.getFirst(), variants, selected);
        assertEquals(size, selected.size());
        assertTrue(selected.contains("minecraft:dirt"));
        assertFalse(selected.contains("minecraft:stone"));
        assertFalse(selected.contains(GroupItemSelector.exactSelector(variants.getFirst())));
        assertEquals(2, selected.iterations);
        assertTrue(hashes.get() <= size * 2);
        for (int i = 1; i < size; i++) assertTrue(selected.contains(GroupItemSelector.exactSelector(variants.get(i))));
    }

    @Test void expansionPreservesOriginalEquivalentSelectorsAndIgnoresCount() {
        var helper = new EditorItemSelectionHelper();
        var plain = new ItemStack(Items.STONE);
        var excluded = named("excluded");
        String original = "stack:{\"count\":1,\"id\":\"minecraft:stone\",\"components\":{\"minecraft:max_stack_size\":64}}";
        Set<String> selected = new LinkedHashSet<>(List.of("minecraft:stone", original, "minecraft:dirt"));
        helper.removeSingleSelection(excluded, List.of(excluded, excluded.copyWithCount(64), plain,
            plain.copyWithCount(32), named("other"), named("other")), selected);
        assertEquals(Set.of(original, "minecraft:dirt", GroupItemSelector.exactSelector(named("other"))), selected);
        assertTrue(helper.isExactSelected(plain, selected));
        assertFalse(helper.isExactSelected(excluded, selected));
    }

    @Test void collisionsStillCompareFullComponents() {
        var helper = new EditorItemSelectionHelper(ignored -> 0);
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack damaged = plain.copy();
        damaged.setDamageValue(7);
        ItemStack excluded = plain.copy();
        excluded.setDamageValue(9);
        Set<String> selected = new LinkedHashSet<>(List.of("minecraft:diamond_sword"));
        helper.removeSingleSelection(excluded, List.of(plain, damaged, excluded, damaged.copyWithCount(2)), selected);
        assertEquals(2, selected.size());
        assertTrue(helper.isExactSelected(plain, selected));
        assertTrue(helper.isExactSelected(damaged, selected));
        assertFalse(helper.isExactSelected(excluded, selected));
    }

    @Test void encodedCacheChecksMutableSourceAndIgnoresCount() {
        var helper = new EditorItemSelectionHelper();
        ItemStack stack = named("before");
        String before = helper.cachedExactSelector(stack).orElseThrow();
        stack.setCount(32);
        assertEquals(before, helper.cachedExactSelector(stack).orElseThrow());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("after"));
        String after = helper.cachedExactSelector(stack).orElseThrow();
        assertNotEquals(before, after);
        assertTrue(ItemStack.isSameItemSameComponents(stack, GroupItemSelector.decodeExactSelector(after).orElseThrow()));
        helper.clearCache();
        assertEquals(after, helper.cachedExactSelector(stack).orElseThrow());
    }

    @Test void failedEncodingCanBeRetriedAfterSourceRepair() {
        var helper = new EditorItemSelectionHelper();
        ItemStack invalid = named("invalid");
        invalid.set(DataComponents.MAX_STACK_SIZE, 0);
        assertTrue(helper.cachedExactSelector(invalid).isEmpty());
        Set<String> selected = new LinkedHashSet<>(List.of("minecraft:stone"));
        ItemStack excluded = named("excluded");
        helper.removeSingleSelection(excluded, List.of(excluded, invalid), selected);
        assertTrue(selected.isEmpty());
        invalid.set(DataComponents.MAX_STACK_SIZE, 64);
        assertTrue(helper.cachedExactSelector(invalid).isPresent());
    }

    @Test void sameSizeReplacementInvalidatesSemanticSelection() {
        var helper = new EditorItemSelectionHelper();
        Set<String> selected = new LinkedHashSet<>(List.of("stack:{\"id\":\"minecraft:stone\",\"count\":1}"));
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        assertTrue(helper.isExactSelected(stone, selected));
        selected.clear();
        selected.add("stack:{\"id\":\"minecraft:dirt\",\"count\":1}");
        helper.selectionChanged();
        assertFalse(helper.isExactSelected(stone, selected));
        assertTrue(helper.isExactSelected(dirt, selected));
        helper.removeAllSelectionsForItem(dirt, selected);
        assertFalse(helper.isExactSelected(dirt, selected));
    }

    @Test void togglesAndBulkRemovalInvalidatePreviouslyReadSelections() {
        var helper = new EditorItemSelectionHelper();
        Set<String> selected = new LinkedHashSet<>();
        ItemStack first = named("first");
        ItemStack second = named("second");
        assertFalse(helper.isExactSelected(first, selected));
        helper.toggleSingleSelection(first, selected);
        assertTrue(helper.isExactSelected(first, selected));
        assertTrue(helper.addSingleSelectionIfAbsent(second, selected));
        assertFalse(helper.addSingleSelectionIfAbsent(second.copyWithCount(5), selected));
        helper.toggleSingleSelection(first, selected);
        assertFalse(helper.isExactSelected(first, selected));
        assertTrue(helper.isExactSelected(second, selected));
        helper.toggleWholeItemSelection(second, selected);
        assertFalse(helper.isExactSelected(second, selected));
        assertTrue(helper.isWholeItemSelected(second, selected));
        helper.toggleWholeItemSelection(second, selected);
        assertFalse(helper.isWholeItemSelected(second, selected));
    }

    private static ItemStack named(String name) {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static final class CountingSet extends LinkedHashSet<String> {
        int iterations;
        @Override public Iterator<String> iterator() {
            iterations++;
            return super.iterator();
        }
    }
}
