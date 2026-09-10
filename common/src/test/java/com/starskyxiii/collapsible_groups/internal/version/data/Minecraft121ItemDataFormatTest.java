package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.starskyxiii.collapsible_groups.group.filter.*;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.*;
import static org.junit.jupiter.api.Assertions.*;

class Minecraft121ItemDataFormatTest {
    private static Minecraft121ItemDataAccess access;
    private static ExactStackCodec.DecodeSnapshot<ItemStack> snapshot;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        access = new Minecraft121ItemDataAccess();
        RegistryAccess registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        snapshot = access.decodeSnapshot(registry.createSerializationContext(JsonOps.INSTANCE), true, registry);
    }

    @Test void nativeExactEncodingPreservesComponentRemovalPatchAndNormalizesCount() {
        ItemStack stack = new ItemStack(Items.STONE, 32);
        stack.remove(DataComponents.RARITY);
        assertFalse(stack.getComponentsPatch().isEmpty());
        ItemDataPayload payload = access.exactStacks().encodePayload(stack).orElseThrow();
        assertEquals(ItemDataPayload.ITEM_COMPONENTS, payload.dataFormat());
        assertTrue(payload.data().getAsJsonObject().getAsJsonObject("components").has("!minecraft:rarity"));
        ItemStack decoded = snapshot.decode(payload.data().toString()).orElseThrow();
        assertEquals(1, decoded.getCount());
        assertFalse(decoded.has(DataComponents.RARITY));
        assertTrue(access.exactStacks().equivalent(stack, decoded));
        assertTrue(access.exactStacks().equivalent(stack.copyWithCount(9), decoded));
        assertFalse(access.exactStacks().equivalent(new ItemStack(Items.STONE), decoded));
        assertEquals(32, stack.getCount());
    }

    @Test void incompleteCodecResultsAndInvalidComponentPatchesAreUnavailable() {
        ItemStack partial = new ItemStack(Items.STONE);
        assertTrue(Minecraft121ItemDataAccess.successfulResult(DataResult.error(() -> "invalid component", partial)).isEmpty());
        assertEquals(partial, Minecraft121ItemDataAccess.successfulResult(DataResult.success(partial)).orElseThrow());
        for (String encoded : List.of("{}", "{\"id\":\"minecraft:stone\",\"components\":{\"unknown:component\":1}}",
            "{\"id\":\"minecraft:diamond_sword\",\"components\":{\"minecraft:damage\":\"bad\"}}")) {
            assertTrue(snapshot.decode(encoded).isEmpty(), encoded);
            GroupFilter invalid = new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, JsonParser.parseString(encoded)));
            ItemStackIngredientView view = new ItemStackIngredientView(partial);
            assertEquals(UNAVAILABLE, CompiledFilter.compile(invalid).evaluate(view));
            assertEquals(UNAVAILABLE, CompiledFilter.compile(Filters.not(invalid)).evaluate(view));
            assertEquals(MATCH, CompiledFilter.compile(Filters.any(invalid, Filters.itemId("minecraft:stone"))).evaluate(view));
            assertEquals(NO_MATCH, CompiledFilter.compile(Filters.all(invalid, Filters.itemId("minecraft:dirt"))).evaluate(view));
            assertEquals(MATCH, CompiledFilter.compile(Filters.not(Filters.all(invalid, Filters.itemId("minecraft:dirt")))).evaluate(view));
        }
    }

    @Test void fullComponentCodecErrorsAndUnknownTypesAreUnavailableButMissingComponentIsNoMatch() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.DAMAGE, 1);
        ItemDataPayload one = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive(1));
        assertEquals(MATCH, access.evaluateDataValue(sword, "minecraft:damage", "", one));
        assertEquals(NO_MATCH, access.evaluateDataValue(new ItemStack(Items.STONE), "minecraft:damage", "", one));
        assertEquals(UNAVAILABLE, access.evaluateDataValue(sword, "minecraft:damage", "",
            new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive("1"))));
        assertEquals(UNAVAILABLE, access.evaluateDataValue(sword, "unknown:type", "", one));
        assertEquals(UNAVAILABLE, access.evaluateDataValue(sword, "minecraft:damage", "", new ItemDataPayload(ItemDataPayload.NBT, new JsonPrimitive("1"))));
        assertEquals(MATCH, access.evaluateDataValue(sword, "minecraft:damage", "1", null));
        assertEquals(UNAVAILABLE, access.evaluateDataValue(sword, "minecraft:damage", "invalid", null));
        assertEquals(UNAVAILABLE, CompiledFilter.compile(Filters.not(Filters.itemComponentValue("unknown:type", new JsonPrimitive(1))))
            .evaluate(new ItemStackIngredientView(sword)));
        BuiltInRegistries.DATA_COMPONENT_TYPE.stream().filter(type -> type.codec() == null).forEach(type ->
            assertEquals(UNAVAILABLE, access.evaluateDataValue(sword, BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString(), "1", null)));
    }

    @Test void componentPathValuesAreSubvaluesAndPreserveLegacyVsTypedStringSemantics() {
        CompoundTag number = new CompoundTag();
        number.putInt("n", 1);
        CompoundTag text = new CompoundTag();
        text.putString("n", "1");
        ItemStack numberStack = new ItemStack(Items.STONE);
        numberStack.set(DataComponents.CUSTOM_DATA, CustomData.of(number));
        ItemStack stringStack = new ItemStack(Items.STONE);
        stringStack.set(DataComponents.CUSTOM_DATA, CustomData.of(text));
        ItemDataPayload typedString = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive("1"));
        ItemDataPayload typedNumber = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive(1));
        assertEquals(MATCH, access.evaluateDataPath(numberStack, "minecraft:custom_data", "n", "1", null));
        assertEquals(MATCH, access.evaluateDataPath(stringStack, "minecraft:custom_data", "n", "1", null));
        assertEquals(MATCH, access.evaluateDataPath(numberStack, "minecraft:custom_data", "n", "", typedNumber));
        assertEquals(NO_MATCH, access.evaluateDataPath(stringStack, "minecraft:custom_data", "n", "", typedNumber));
        assertEquals(NO_MATCH, access.evaluateDataPath(numberStack, "minecraft:custom_data", "n", "", typedString));
        assertEquals(MATCH, access.evaluateDataPath(stringStack, "minecraft:custom_data", "n", "", typedString));
        assertEquals(NO_MATCH, access.evaluateDataPath(numberStack, "minecraft:custom_data", "missing", "", typedNumber));
        assertEquals(UNAVAILABLE, access.evaluateDataPath(numberStack, "minecraft:custom_data", "bad[*]", "", typedNumber));
        assertEquals(UNAVAILABLE, access.evaluateDataValue(numberStack, "minecraft:custom_data", "", typedNumber));
    }
}
