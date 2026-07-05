package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Detects "unresolved" tag rules: syntactically valid tag ids that resolve to
 * zero entries against the current registries.
 *
 * <p>This is orthogonal to {@code GroupFilterValidator}: syntax errors block
 * saving, unresolved tags stay saveable (serialized verbatim) and simply do not
 * match anything until the tag exists. Only TAG (item/fluid) and BLOCK_TAG
 * nodes participate; path/namespace/component kinds are scan-style matchers
 * with no unresolved concept.
 */
public final class RuleTagResolution {
	public enum TagRegistryKind {
		ITEM,
		FLUID,
		BLOCK
	}

	@FunctionalInterface
	public interface TagExistenceLookup {
		boolean tagExists(TagRegistryKind registry, ResourceLocation tagId);
	}

	private RuleTagResolution() {}

	/** Registry-backed production lookup. Not touched by unit tests. */
	public static final class RegistryLookup implements TagExistenceLookup {
		public static final RegistryLookup INSTANCE = new RegistryLookup();

		private RegistryLookup() {}

		@Override
		public boolean tagExists(TagRegistryKind registry, ResourceLocation tagId) {
			return switch (registry) {
				case ITEM -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId)).isPresent();
				case FLUID -> BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tagId)).isPresent();
				case BLOCK -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tagId)).isPresent();
			};
		}
	}

	/** The tag registry a node queries, or {@code null} when the node has no unresolved concept. */
	public static @Nullable TagRegistryKind tagRegistryFor(GroupFilterRuleDraft.Node node) {
		Objects.requireNonNull(node, "node");
		return switch (node.kind()) {
			case TAG -> switch (normalizeType(node.ingredientType())) {
				case "item" -> TagRegistryKind.ITEM;
				case "fluid" -> TagRegistryKind.FLUID;
				default -> null;
			};
			case BLOCK_TAG -> TagRegistryKind.BLOCK;
			default -> null;
		};
	}

	/**
	 * True when the node is a tag rule whose id parses cleanly but is absent from
	 * the current registry. Blank or unparseable values are the syntax-error axis
	 * and return {@code false} here.
	 */
	public static boolean isUnresolved(GroupFilterRuleDraft.Node node, TagExistenceLookup lookup) {
		Objects.requireNonNull(lookup, "lookup");
		TagRegistryKind registry = tagRegistryFor(node);
		if (registry == null) {
			return false;
		}
		String raw = node.primaryValue().trim();
		if (raw.isEmpty()) {
			return false;
		}
		ResourceLocation tagId = ResourceLocation.tryParse(raw);
		if (tagId == null) {
			return false;
		}
		return !lookup.tagExists(registry, tagId);
	}

	public static int countUnresolved(List<GroupFilterRuleDraft.FlatNode> nodes, TagExistenceLookup lookup) {
		Objects.requireNonNull(nodes, "nodes");
		int count = 0;
		for (GroupFilterRuleDraft.FlatNode flat : nodes) {
			if (isUnresolved(flat.node(), lookup)) {
				count++;
			}
		}
		return count;
	}

	private static String normalizeType(String ingredientType) {
		return ingredientType == null ? "" : ingredientType.trim().toLowerCase(Locale.ROOT);
	}
}
