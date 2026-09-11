package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MinecraftGroupRuleDiagnostics implements Function<GroupFilter, List<GroupEvaluation.Issue>> {
    private Minecraft121ItemDataAccess.RegistryContext context;
    private final Map<GroupFilter, List<GroupEvaluation.Issue>> checked = new HashMap<>();

    @Override public List<GroupEvaluation.Issue> apply(GroupFilter filter) {
        return checked.computeIfAbsent(filter, this::inspect);
    }

    private List<GroupEvaluation.Issue> inspect(GroupFilter filter) {
        try {
            if (context == null) context = ItemDataAccesses.minecraft121().registryContext();
            if (!context.liveRegistry()) return unavailable("World registry data is not ready");
            return switch (filter) {
                case GroupFilter.ExactStack exact -> exact(exact);
                case GroupFilter.HasComponent component -> component(component.componentTypeId(), component.encodedValue(), component.payload(), false);
                case GroupFilter.ComponentPath path -> component(path.componentTypeId(), path.expectedValue(), path.payload(), true);
                default -> List.of();
            };
        } catch (RuntimeException failure) {
            return unavailable(failure.toString());
        }
    }

    private List<GroupEvaluation.Issue> exact(GroupFilter.ExactStack exact) {
        JsonElement data;
        try {
            data = exact.payload() == null ? JsonParser.parseString(exact.encodedStack()) : exact.payload().data();
        } catch (RuntimeException invalid) {
            return error("Invalid exact item JSON: " + invalid.getMessage());
        }
        if (data.isJsonObject() && data.getAsJsonObject().has("id")) {
            String id = data.getAsJsonObject().get("id").getAsString();
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) return error("Invalid exact item ID: " + id);
            if (!BuiltInRegistries.ITEM.containsKey(location)) return unavailable("Item registry entry is unavailable: " + id);
        }
        var decoded = ItemStack.STRICT_SINGLE_ITEM_CODEC.parse(context.ops(), data);
        return decoded.error().map(failure -> error("Exact item data: " + failure.message())).orElseGet(List::of);
    }

    private List<GroupEvaluation.Issue> component(String id, String legacy, ItemDataPayload payload, boolean path) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return error("Invalid component ID: " + id);
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(location);
        if (type == null || type.codec() == null) return unavailable("Component codec is unavailable: " + id);
        if (payload != null && !ItemDataPayload.DATA_COMPONENT.equals(payload.dataFormat())) return unavailable("Unsupported component data format");
        if (path) return List.of();
        JsonElement value = payload == null ? new JsonPrimitive(legacy) : payload.data();
        var parsed = type.codec().parse(context.ops(), value);
        if (parsed.result().isPresent()) return List.of();
        if (payload == null) {
            try {
                if (type.codec().parse(context.ops(), JsonParser.parseString(legacy)).result().isPresent()) return List.of();
            } catch (RuntimeException ignored) {
            }
        }
        return error("Component data for " + id + ": " + parsed.error().map(failure -> failure.message()).orElse("decoding failed"));
    }

    private static List<GroupEvaluation.Issue> error(String reason) {
        return List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.ERROR, reason));
    }

    private static List<GroupEvaluation.Issue> unavailable(String reason) {
        return List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.UNAVAILABLE, reason));
    }
}
