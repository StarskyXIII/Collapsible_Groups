package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.Wrapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class CGGroupSourceBuilder {
	private final String sourceId;
	private final String publicationSource;
	private final List<KubeJsLoweredGroup> groups = new ArrayList<>();

	CGGroupSourceBuilder(String sourceId, String publicationSource) {
		this.sourceId = sourceId;
		this.publicationSource = publicationSource;
	}

	public CGGroupSourceBuilder add(String id, String name, GroupFilter filter) {
		if (!KubeJsFilterComposition.supportsTree(filter)) {
			throw new IllegalArgumentException("group filter contains a node that CGEvents.groups cannot lower");
		}
		ResourceLocation groupLocation = requireId(id, "group");
		if (name == null || name.isBlank()) throw new IllegalArgumentException("group name must not be blank");
		String groupId = "__kjs_cg:" + encode(sourceId) + ':' + encode(groupLocation.toString());
		groups.add(new KubeJsLoweredGroup(groupId, name,
			KubeJsLoweringResult.exact(filter, publicationSource)));
		return this;
	}

	public CGGroupSourceBuilder ingredient(String id, String name, Object ingredient) {
		GroupFilter filter = KubeJs6FilterCompiler.compileItem(ingredient);
		if (filter == null) {
			ResourceLocation groupLocation = requireId(id, "group");
			if (name == null || name.isBlank()) throw new IllegalArgumentException("group name must not be blank");
			String groupId = "__kjs_cg:" + encode(sourceId) + ':' + encode(groupLocation.toString());
			groups.add(new KubeJsLoweredGroup(groupId, name, KubeJsLoweringResult.unsupported(
				KubeJs6FilterCompiler.unsupportedReason(ingredient),
				publicationSource)));
			return this;
		}
		return add(id, name, filter);
	}

	public CGGroupSourceBuilder item(String id, String name, String itemId) {
		return add(id, name, itemId(itemId));
	}

	public CGGroupSourceBuilder itemTag(String id, String name, String tagId) {
		return add(id, name, itemTag(tagId));
	}

	public CGGroupSourceBuilder exact(String id, String name, ItemStack stack) {
		return add(id, name, exactItem(stack));
	}

	public CGGroupSourceBuilder fluid(String id, String name, String fluidId) {
		return add(id, name, fluidId(fluidId));
	}

	public GroupFilter itemId(String id) { return Filters.itemId(requireId(id, "item").toString()); }
	public GroupFilter itemTag(String id) { return Filters.itemTag(requireId(id, "item tag").toString()); }

	public GroupFilter exactItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("exact item stack must not be empty");
		return Filters.exactStack(stack);
	}

	public GroupFilter fluidId(String id) { return Filters.fluidId(requireId(id, "fluid").toString()); }
	public GroupFilter fluidTag(String id) { return Filters.fluidTag(requireId(id, "fluid tag").toString()); }
	public GroupFilter nbt(String snbt) {
		return Filters.nbt(Minecraft1201NbtAccess.canonicalRoot(snbt)
			.orElseThrow(() -> new IllegalArgumentException("nbt() requires a valid compound SNBT value")));
	}
	public GroupFilter nbtPath(String path, String snbt) {
		if (!Minecraft1201NbtAccess.validPath(path)) {
			throw new IllegalArgumentException("nbtPath() requires a valid NBT path");
		}
		return Filters.nbtPath(path, Minecraft1201NbtAccess.canonicalValue(snbt)
			.orElseThrow(() -> new IllegalArgumentException("nbtPath() requires a valid SNBT value")));
	}
	public GroupFilter any(Object filters) { return composition(filters, true); }
	public GroupFilter all(Object filters) { return composition(filters, false); }

	public GroupFilter not(GroupFilter filter) {
		if (!KubeJsFilterComposition.supportsTree(filter)) throw new IllegalArgumentException("not() requires a supported CG filter");
		return Filters.not(filter);
	}

	List<KubeJsLoweredGroup> groups() { return List.copyOf(groups); }

	private GroupFilter composition(Object input, boolean any) {
		List<?> values = ListJS.of(input);
		if (values == null) throw new IllegalArgumentException("composition requires an array or list of CG filters");
		List<GroupFilter> filters = new ArrayList<>(values.size());
		for (Object value : values) {
			while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
			if (!(value instanceof GroupFilter filter) || !KubeJsFilterComposition.supportsTree(filter)) {
				throw new IllegalArgumentException("composition contains a value that is not a supported CG filter");
			}
			filters.add(filter);
		}
		GroupFilter result = any ? KubeJsFilterComposition.any(filters) : KubeJsFilterComposition.all(filters);
		if (result == null) throw new IllegalArgumentException("composition must contain at least one supported CG filter");
		return result;
	}

	private static String encode(String value) {
		return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
	}

	private static ResourceLocation requireId(String value, String label) {
		ResourceLocation id = ResourceLocation.tryParse(value);
		if (id == null) throw new IllegalArgumentException(label + " ID must be a valid resource location: " + value);
		return id;
	}
}
