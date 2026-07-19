package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable structural draft model for the current editor subset.
 *
 * <p>The editor supports authoring only a flat {@code Any(...)} structure composed of:
 * item IDs, exact item stacks, item tags, fluid IDs, fluid tags, generic IDs, and generic tags.
 *
 * <p>Unsupported structural nodes (e.g. All, Not, Namespace, nested structures) are recorded in
 * {@link DecodeResult#unsupportedNodeKinds()} and presented read-only in the editor.
 */
public final class GroupFilterEditorDraft {
	private static final String ITEM_TYPE = "item";
	private static final String FLUID_TYPE = "fluid";
	private static final String STACK_PREFIX = "stack:";

	private final Set<String> explicitItemSelectors;
	private final List<String> itemTags;
	private final List<String> fluidIds;
	private final List<String> fluidTags;
	private final List<GenericValue> genericIds;
	private final List<GenericValue> genericTags;
	// opaque advanced subtrees (rules the flat editor cannot author) kept
	// verbatim so a hybrid Any(rules…, ids…) filter survives a contents-only edit.
	private final List<GroupFilter> preservedSubtrees;

	public record GenericValue(String ingredientType, String value) {}

	public enum UnsupportedEditorNodeKind {
		ALL(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ALL_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ALL_REASON),
		NOT(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NOT_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NOT_REASON),
		BLOCK_TAG(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_BLOCK_TAG_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_BLOCK_TAG_REASON),
		ITEM_PATH_STARTS_WITH(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_STARTS_WITH_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_STARTS_WITH_REASON),
		ITEM_PATH_CONTAINS(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_CONTAINS_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_CONTAINS_REASON),
		ITEM_PATH_ENDS_WITH(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_ENDS_WITH_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ITEM_PATH_ENDS_WITH_REASON),
		NAMESPACE(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NAMESPACE_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NAMESPACE_REASON),
		NESTED_STRUCTURE(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NESTED_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NESTED_REASON),
		HAS_COMPONENT(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_REASON),
		COMPONENT_PATH(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_REASON),
		UNAVAILABLE(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_UNAVAILABLE_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_UNAVAILABLE_REASON);

		private final String labelKey;
		private final String reasonKey;

		UnsupportedEditorNodeKind(String labelKey, String reasonKey) {
			this.labelKey = labelKey;
			this.reasonKey = reasonKey;
		}

		public String labelKey() {
			return labelKey;
		}

		public String reasonKey() {
			return reasonKey;
		}
	}

	public record DecodeResult(
		GroupFilterEditorDraft draft,
		boolean structurallyEditable,
		List<GroupFilter> preservedSubtrees,
		Set<UnsupportedEditorNodeKind> unsupportedNodeKinds
	) {
		public DecodeResult {
			Objects.requireNonNull(draft, "draft");
			Objects.requireNonNull(preservedSubtrees, "preservedSubtrees");
			Objects.requireNonNull(unsupportedNodeKinds, "unsupportedNodeKinds");
			preservedSubtrees = List.copyOf(preservedSubtrees);
			unsupportedNodeKinds = Set.copyOf(unsupportedNodeKinds);
		}

		public boolean hasUnsupportedNodeKinds() {
			return !unsupportedNodeKinds.isEmpty();
		}

		/**
		 * flat-index-safe predicate — decoupled from {@link #structurallyEditable}.
		 *
		 * <p>The indexed item preview ({@code GroupRegistry.resolveEditorDraftItems})
		 * only understands the flat draft (explicit item selectors + item tags). It is a
		 * faithful preview <em>only</em> when the decoded draft carries no preserved
		 * subtree, because any preserved subtree is an advanced union member the flat
		 * index cannot resolve. A hybrid {@code Any(rules…, ids…)} draft therefore stays
		 * fully contents-editable ({@code structurallyEditable}) yet is <em>not</em>
		 * flat-index safe: callers must fall through to the full
		 * {@code GroupRegistry.resolveItems} pass so the preview and RULE_COVERED
		 * coverage include the preserved subtree's matches.
		 */
		public boolean flatIndexSafe() {
			return structurallyEditable && preservedSubtrees.isEmpty();
		}
	}

	private GroupFilterEditorDraft(Set<String> explicitItemSelectors,
	                               List<String> itemTags,
	                               List<String> fluidIds,
	                               List<String> fluidTags,
	                               List<GenericValue> genericIds,
	                               List<GenericValue> genericTags,
	                               List<GroupFilter> preservedSubtrees) {
		this.explicitItemSelectors = explicitItemSelectors;
		this.itemTags = itemTags;
		this.fluidIds = fluidIds;
		this.fluidTags = fluidTags;
		this.genericIds = genericIds;
		this.genericTags = genericTags;
		this.preservedSubtrees = preservedSubtrees;
	}

	public static GroupFilterEditorDraft empty() {
		return new GroupFilterEditorDraft(
			new LinkedHashSet<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>()
		);
	}

	/**
	 * decode with preserved subtrees. Branches:
	 * <ul>
	 * <li>{@code null} → empty draft, {@code editable=false}, as required by
	 * {@code refreshContentsDraftFromRules}.</li>
	 * <li>root {@code Any} → supported flat leaves flow into the draft, every other
	 * child subtree is preserved verbatim; {@code editable=true}.</li>
	 * <li>root single supported leaf → flat draft, no preserved; {@code editable=true}.</li>
	 * <li>root non-{@code Any}, non-supported-leaf → the whole tree is a single preserved
	 * subtree, draft empty, {@code editable=true} (the "auto-wrap on first edit" case:
	 * {@code toFilter} emits {@code Any(originalTree, newLeaves…)}).</li>
	 * </ul>
	 * {@code unsupportedNodeKinds} is now only a UI-text summary of the preserved kinds;
	 * it never decides editability.
	 */
	public static DecodeResult decode(@Nullable GroupFilter filter) {
		if (filter == null) {
			return new DecodeResult(empty(), false, List.of(), Set.of());
		}

		GroupFilter normalized = GroupFilterNormalizer.normalize(filter);
		GroupFilterEditorDraft draft = empty();
		EnumSet<UnsupportedEditorNodeKind> unsupportedNodeKinds = EnumSet.noneOf(UnsupportedEditorNodeKind.class);

		if (normalized instanceof GroupFilter.Any any) {
			if (any.children().isEmpty()) {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
				return new DecodeResult(empty(), false, List.of(), unsupportedNodeKinds);
			}
			for (GroupFilter child : any.children()) {
				if (!tryAddFlatLeaf(child, draft)) {
					draft.preservedSubtrees.add(child);
					recordPreservedKinds(child, unsupportedNodeKinds);
				}
			}
			return finishDecode(draft, unsupportedNodeKinds);
		}

		if (tryAddFlatLeaf(normalized, draft)) {
			return finishDecode(draft, unsupportedNodeKinds);
		}

		// Non-Any root that is not a single supported leaf: keep the whole tree opaque and
		// let the first contents edit wrap it in an Any (per the decision).
		draft.preservedSubtrees.add(normalized);
		recordPreservedKinds(normalized, unsupportedNodeKinds);
		return finishDecode(draft, unsupportedNodeKinds);
	}

	private static DecodeResult finishDecode(GroupFilterEditorDraft draft,
	                                         Set<UnsupportedEditorNodeKind> unsupportedNodeKinds) {
		boolean editable = !unsupportedNodeKinds.contains(UnsupportedEditorNodeKind.UNAVAILABLE);
		return new DecodeResult(draft, editable, List.copyOf(draft.preservedSubtrees), unsupportedNodeKinds);
	}

	/**
	 * Returns the live mutable selector set owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public Set<String> explicitItemSelectors() {
		return explicitItemSelectors;
	}

	/**
	 * Returns the live mutable item-tag list owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public List<String> itemTags() {
		return itemTags;
	}

	/**
	 * Returns the live mutable fluid-id list owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public List<String> fluidIds() {
		return fluidIds;
	}

	/**
	 * Returns the live mutable fluid-tag list owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public List<String> fluidTags() {
		return fluidTags;
	}

	/**
	 * Returns the live mutable generic-id list owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public List<GenericValue> genericIds() {
		return genericIds;
	}

	/**
	 * Returns the live mutable generic-tag list owned by this draft.
	 * Editor state objects intentionally mutate this collection in place.
	 */
	public List<GenericValue> genericTags() {
		return genericTags;
	}

	/**
	 * Returns the live mutable list of preserved advanced subtrees owned by this draft.
	 * Editor state objects intentionally mutate this collection in place so a hybrid
	 * filter round-trips through a contents edit with its rules intact.
	 */
	public List<GroupFilter> preservedSubtrees() {
		return preservedSubtrees;
	}

	public boolean hasPreservedSubtrees() {
		return !preservedSubtrees.isEmpty();
	}

	/** True when the flat (contents-authorable) portion of the draft is empty. Ignores preserved subtrees. */
	public boolean isEmpty() {
		return explicitItemSelectors.isEmpty()
			&& itemTags.isEmpty()
			&& fluidIds.isEmpty()
			&& fluidTags.isEmpty()
			&& genericIds.isEmpty()
			&& genericTags.isEmpty();
	}

	public Optional<GroupFilter> toFilter() {
		List<GroupFilter> flat = new ArrayList<>();

		for (String selector : explicitItemSelectors) {
			if (selector.startsWith(STACK_PREFIX)) {
				flat.add(Filters.exactStack(selector.substring(STACK_PREFIX.length())));
			} else {
				flat.add(Filters.itemId(selector));
			}
		}
		itemTags.forEach(tag -> flat.add(Filters.itemTag(tag)));
		fluidIds.forEach(id -> flat.add(Filters.fluidId(id)));
		fluidTags.forEach(tag -> flat.add(Filters.fluidTag(tag)));
		genericIds.forEach(entry -> flat.add(Filters.genericId(entry.ingredientType(), entry.value())));
		genericTags.forEach(entry -> flat.add(Filters.genericTag(entry.ingredientType(), entry.value())));

		if (flat.isEmpty() && preservedSubtrees.isEmpty()) {
			return Optional.empty();
		}
		// Untouched non-Any root: a lone preserved subtree round-trips as itself, with no
		// superfluous Any wrap, so a rules-only filter is not structurally deformed by a
		// no-op pass through the contents editor.
		if (flat.isEmpty() && preservedSubtrees.size() == 1) {
			return Optional.of(preservedSubtrees.get(0));
		}
		// Stable order: preserved advanced subtrees first, then flat contents leaves.
		List<GroupFilter> nodes = new ArrayList<>(preservedSubtrees.size() + flat.size());
		nodes.addAll(preservedSubtrees);
		nodes.addAll(flat);
		if (nodes.size() == 1) {
			return Optional.of(nodes.get(0));
		}
		return Optional.of(Filters.any(nodes.toArray(GroupFilter[]::new)));
	}

	/**
	 * Adds {@code filter} to the flat draft iff it is a supported contents leaf
	 * (item/fluid/generic id, tag, or an exact item stack). Returns {@code false} without
	 * mutating the draft for every composite or advanced node, so callers can preserve it.
	 */
	private static boolean tryAddFlatLeaf(GroupFilter filter, GroupFilterEditorDraft draft) {
		switch (filter) {
			case GroupFilter.Id id -> addIdNode(id, draft);
			case GroupFilter.Tag tag -> addTagNode(tag, draft);
			case GroupFilter.ExactStack stack -> draft.explicitItemSelectors.add(STACK_PREFIX + stack.encodedStack());
			default -> {
				return false;
			}
		}
		return true;
	}

	/**
	 * Walks a preserved subtree recording its {@link UnsupportedEditorNodeKind}s purely for
	 * the UI summary ("N advanced rules"). Never affects editability.
	 */
	private static void recordPreservedKinds(GroupFilter filter, Set<UnsupportedEditorNodeKind> kinds) {
		switch (filter) {
			case GroupFilter.Any any -> {
				kinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
				for (GroupFilter child : any.children()) {
					recordPreservedKinds(child, kinds);
				}
			}
			case GroupFilter.All all -> {
				kinds.add(UnsupportedEditorNodeKind.ALL);
				for (GroupFilter child : all.children()) {
					recordPreservedKinds(child, kinds);
				}
			}
			case GroupFilter.Not not -> {
				kinds.add(UnsupportedEditorNodeKind.NOT);
				recordPreservedKinds(not.child(), kinds);
			}
			case GroupFilter.BlockTag ignored -> kinds.add(UnsupportedEditorNodeKind.BLOCK_TAG);
			case GroupFilter.ItemPathStartsWith ignored -> kinds.add(UnsupportedEditorNodeKind.ITEM_PATH_STARTS_WITH);
			case GroupFilter.ItemPathContains ignored -> kinds.add(UnsupportedEditorNodeKind.ITEM_PATH_CONTAINS);
			case GroupFilter.ItemPathEndsWith ignored -> kinds.add(UnsupportedEditorNodeKind.ITEM_PATH_ENDS_WITH);
			case GroupFilter.Namespace ignored -> kinds.add(UnsupportedEditorNodeKind.NAMESPACE);
			case GroupFilter.HasComponent ignored -> kinds.add(UnsupportedEditorNodeKind.HAS_COMPONENT);
			case GroupFilter.ComponentPath ignored -> kinds.add(UnsupportedEditorNodeKind.COMPONENT_PATH);
			case GroupFilter.Unsupported ignored -> kinds.add(UnsupportedEditorNodeKind.UNAVAILABLE);
			// Supported leaves can appear inside a preserved composite; they contribute no kind.
			case GroupFilter.Id ignored -> { }
			case GroupFilter.Tag ignored -> { }
			case GroupFilter.ExactStack ignored -> { }
		}
	}

	private static void addIdNode(GroupFilter.Id id, GroupFilterEditorDraft draft) {
		switch (id.ingredientType()) {
			case ITEM_TYPE -> draft.explicitItemSelectors.add(id.id());
			case FLUID_TYPE -> addUnique(draft.fluidIds, id.id());
			default -> addUnique(draft.genericIds, new GenericValue(id.ingredientType(), id.id()));
		}
	}

	private static void addTagNode(GroupFilter.Tag tag, GroupFilterEditorDraft draft) {
		switch (tag.ingredientType()) {
			case ITEM_TYPE -> addUnique(draft.itemTags, tag.tag());
			case FLUID_TYPE -> addUnique(draft.fluidTags, tag.tag());
			default -> addUnique(draft.genericTags, new GenericValue(tag.ingredientType(), tag.tag()));
		}
	}

	private static <T> void addUnique(List<T> list, T value) {
		if (!list.contains(value)) {
			list.add(value);
		}
	}
}
