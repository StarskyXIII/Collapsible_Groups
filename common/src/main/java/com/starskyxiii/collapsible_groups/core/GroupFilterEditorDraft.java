package com.starskyxiii.collapsible_groups.core;

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

	public record GenericValue(String ingredientType, String value) {}

	public enum UnsupportedEditorNodeKind {
		ALL(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ALL_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_ALL_REASON),
		NOT(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NOT_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NOT_REASON),
		NAMESPACE(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NAMESPACE_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NAMESPACE_REASON),
		NESTED_STRUCTURE(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NESTED_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_NESTED_REASON),
		HAS_COMPONENT(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_REASON),
		COMPONENT_PATH(ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_LABEL, ModTranslationKeys.EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_REASON);

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
		Set<UnsupportedEditorNodeKind> unsupportedNodeKinds
	) {
		public DecodeResult {
			Objects.requireNonNull(draft, "draft");
			Objects.requireNonNull(unsupportedNodeKinds, "unsupportedNodeKinds");
			unsupportedNodeKinds = Set.copyOf(unsupportedNodeKinds);
		}

		public boolean hasUnsupportedNodeKinds() {
			return !unsupportedNodeKinds.isEmpty();
		}
	}

	private GroupFilterEditorDraft(Set<String> explicitItemSelectors,
	                               List<String> itemTags,
	                               List<String> fluidIds,
	                               List<String> fluidTags,
	                               List<GenericValue> genericIds,
	                               List<GenericValue> genericTags) {
		this.explicitItemSelectors = explicitItemSelectors;
		this.itemTags = itemTags;
		this.fluidIds = fluidIds;
		this.fluidTags = fluidTags;
		this.genericIds = genericIds;
		this.genericTags = genericTags;
	}

	public static GroupFilterEditorDraft empty() {
		return new GroupFilterEditorDraft(
			new LinkedHashSet<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>(),
			new ArrayList<>()
		);
	}

	public static DecodeResult decode(@Nullable GroupFilter filter) {
		if (filter == null) {
			return new DecodeResult(empty(), false, Set.of());
		}

		GroupFilter normalized = GroupFilterNormalizer.normalize(filter);
		GroupFilterEditorDraft draft = empty();
		EnumSet<UnsupportedEditorNodeKind> unsupportedNodeKinds = EnumSet.noneOf(UnsupportedEditorNodeKind.class);
		if (!collectSupportedNodes(normalized, draft, unsupportedNodeKinds, 0, false) || draft.isEmpty()) {
			return new DecodeResult(empty(), false, unsupportedNodeKinds);
		}
		return new DecodeResult(draft, true, unsupportedNodeKinds);
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

	public boolean isEmpty() {
		return explicitItemSelectors.isEmpty()
			&& itemTags.isEmpty()
			&& fluidIds.isEmpty()
			&& fluidTags.isEmpty()
			&& genericIds.isEmpty()
			&& genericTags.isEmpty();
	}

	public Optional<GroupFilter> toFilter() {
		List<GroupFilter> nodes = new ArrayList<>();

		for (String selector : explicitItemSelectors) {
			if (GroupItemSelector.isExactSelector(selector)) {
				nodes.add(Filters.exactStack(selector.substring(STACK_PREFIX.length())));
			} else {
				nodes.add(Filters.itemId(selector));
			}
		}
		itemTags.forEach(tag -> nodes.add(Filters.itemTag(tag)));
		fluidIds.forEach(id -> nodes.add(Filters.fluidId(id)));
		fluidTags.forEach(tag -> nodes.add(Filters.fluidTag(tag)));
		genericIds.forEach(entry -> nodes.add(Filters.genericId(entry.ingredientType(), entry.value())));
		genericTags.forEach(entry -> nodes.add(Filters.genericTag(entry.ingredientType(), entry.value())));

		if (nodes.isEmpty()) {
			return Optional.empty();
		}
		if (nodes.size() == 1) {
			return Optional.of(nodes.getFirst());
		}
		return Optional.of(Filters.any(nodes.toArray(GroupFilter[]::new)));
	}

	private static boolean collectSupportedNodes(GroupFilter filter,
	                                             GroupFilterEditorDraft draft,
	                                             Set<UnsupportedEditorNodeKind> unsupportedNodeKinds,
	                                             int depth,
	                                             boolean parentIsComposite) {
		if (isComposite(filter) && parentIsComposite) {
			unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
		}

		return switch (filter) {
			case GroupFilter.Any any -> {
				if (any.children().isEmpty()) {
					unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
					yield false;
				}
				if (depth > 0) {
					unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
				}
				for (GroupFilter child : any.children()) {
					if (!collectSupportedNodes(child, draft, unsupportedNodeKinds, depth + 1, true)) {
						yield false;
					}
				}
				yield true;
			}
			case GroupFilter.Id id -> addIdNode(id, draft);
			case GroupFilter.Tag tag -> addTagNode(tag, draft);
			case GroupFilter.ExactStack stack -> draft.explicitItemSelectors.add(STACK_PREFIX + stack.encodedStack());
			case GroupFilter.All all -> {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.ALL);
				if (all.children().stream().anyMatch(GroupFilterEditorDraft::isComposite)) {
					unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
				}
				for (GroupFilter child : all.children()) {
					collectSupportedNodes(child, draft, unsupportedNodeKinds, depth + 1, true);
				}
				yield false;
			}
			case GroupFilter.Not not -> {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NOT);
				if (isComposite(not.child())) {
					unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NESTED_STRUCTURE);
				}
				collectSupportedNodes(not.child(), draft, unsupportedNodeKinds, depth + 1, true);
				yield false;
			}
			case GroupFilter.Namespace ignored -> {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.NAMESPACE);
				yield false;
			}
			case GroupFilter.HasComponent ignored -> {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.HAS_COMPONENT);
				yield false;
			}
			case GroupFilter.ComponentPath ignored -> {
				unsupportedNodeKinds.add(UnsupportedEditorNodeKind.COMPONENT_PATH);
				yield false;
			}
		};
	}

	private static boolean isComposite(GroupFilter filter) {
		return filter instanceof GroupFilter.Any
			|| filter instanceof GroupFilter.All
			|| filter instanceof GroupFilter.Not;
	}

	private static boolean addIdNode(GroupFilter.Id id, GroupFilterEditorDraft draft) {
		return switch (id.ingredientType()) {
			case ITEM_TYPE -> draft.explicitItemSelectors.add(id.id());
			case FLUID_TYPE -> addUnique(draft.fluidIds, id.id());
			default -> addUnique(draft.genericIds, new GenericValue(id.ingredientType(), id.id()));
		};
	}

	private static boolean addTagNode(GroupFilter.Tag tag, GroupFilterEditorDraft draft) {
		return switch (tag.ingredientType()) {
			case ITEM_TYPE -> addUnique(draft.itemTags, tag.tag());
			case FLUID_TYPE -> addUnique(draft.fluidTags, tag.tag());
			default -> addUnique(draft.genericTags, new GenericValue(tag.ingredientType(), tag.tag()));
		};
	}

	private static <T> boolean addUnique(List<T> list, T value) {
		if (!list.contains(value)) {
			list.add(value);
		}
		return true;
	}
}
