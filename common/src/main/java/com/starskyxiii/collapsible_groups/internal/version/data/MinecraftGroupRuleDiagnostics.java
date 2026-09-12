package com.starskyxiii.collapsible_groups.internal.version.data;

import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.GroupFormatPolicy;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MinecraftGroupRuleDiagnostics implements Function<GroupFilter, List<GroupEvaluation.Issue>> {
    private final Map<GroupFilter, List<GroupEvaluation.Issue>> checked = new HashMap<>();

    @Override public List<GroupEvaluation.Issue> apply(GroupFilter filter) {
        return checked.computeIfAbsent(filter, this::inspect);
    }

    private List<GroupEvaluation.Issue> inspect(GroupFilter filter) {
        try {
            if (filter instanceof GroupFilter.ExactStack || filter instanceof GroupFilter.Nbt || filter instanceof GroupFilter.NbtPath) {
                ItemDataPayload payload = GroupFormatPolicy.payload(filter);
                if (payload != null && ItemDataPayload.NBT.equals(payload.dataFormat())
                    && (!payload.data().isJsonPrimitive() || !payload.data().getAsJsonPrimitive().isString())) {
                    return error("NBT payload must contain an SNBT string");
                }
            }
            if (filter instanceof GroupFilter.ExactStack exact) return exact(exact);
            if (filter instanceof GroupFilter.Nbt nbt) {
                if (!nativePayload(nbt.payload())) return unavailable("Unsupported NBT data format");
                return Minecraft1201NbtAccess.canonicalRoot(nbt.expectedSnbt()).isPresent()
                    ? List.of() : error("Invalid compound SNBT");
            }
            if (filter instanceof GroupFilter.NbtPath path) {
                if (!nativePayload(path.payload())) return unavailable("Unsupported NBT data format");
                return Minecraft1201NbtAccess.validPath(path.path())
                    && Minecraft1201NbtAccess.canonicalValue(path.expectedSnbt()).isPresent()
                    ? List.of() : error("Invalid NBT path or SNBT value");
            }
            if (filter instanceof GroupFilter.HasComponent || filter instanceof GroupFilter.ComponentPath) {
                return unavailable("Data components are unavailable in Minecraft 1.20.1");
            }
            return List.of();
        } catch (RuntimeException failure) {
            return error(failure.toString());
        }
    }

    private List<GroupEvaluation.Issue> exact(GroupFilter.ExactStack exact) {
        if (!nativePayload(exact.payload())) return unavailable("Unsupported exact item data format");
        CompoundTag data;
        try {
            data = TagParser.parseTag(exact.encodedStack());
        } catch (Exception invalid) {
            return error("Invalid exact item SNBT: " + invalid.getMessage());
        }
        if (!data.contains("id", Tag.TAG_STRING) || !data.contains("Count", Tag.TAG_ANY_NUMERIC)
            || (data.contains("tag") && !data.contains("tag", Tag.TAG_COMPOUND))) {
            return error("Invalid exact item NBT structure");
        }
        String id = data.getString("id");
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return error("Invalid exact item ID: " + id);
        if (!BuiltInRegistries.ITEM.containsKey(location)) return unavailable("Item registry entry is unavailable: " + id);
        return ItemDataAccesses.current().exactStacks().beginDecode().decode(exact.encodedStack()).isPresent()
            ? List.of() : error("Exact item data could not be decoded");
    }

    private static boolean nativePayload(ItemDataPayload payload) {
        return payload == null || ItemDataPayload.NBT.equals(payload.dataFormat());
    }

    private static List<GroupEvaluation.Issue> error(String reason) {
        return List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.ERROR, reason));
    }

    private static List<GroupEvaluation.Issue> unavailable(String reason) {
        return List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.UNAVAILABLE, reason));
    }
}
