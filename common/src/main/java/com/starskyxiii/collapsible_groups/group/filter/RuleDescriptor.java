package com.starskyxiii.collapsible_groups.group.filter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RuleDescriptor(
	boolean available,
	boolean kubeJsLoweringSupported,
	TypePolicy typePolicy,
	boolean compound,
	int draftMinChildren,
	int validMinChildren,
	int maxChildren,
	List<FieldRole> fieldRoles,
	Set<FieldRole> requiredRoles,
	ReferencePickerSource referencePickerSource
) {
	public enum TypePolicy { NONE, INGREDIENT_TYPED, ITEM_ONLY }
	public enum FieldRole { INGREDIENT_TYPE, PRIMARY_VALUE, SECONDARY_VALUE, TERTIARY_VALUE }
	public enum ReferencePickerSource {
		NONE,
		INGREDIENT_IDS,
		INGREDIENT_TAGS,
		INGREDIENT_NAMESPACES,
		BLOCK_TAGS,
		ITEM_STACKS,
		ITEM_NBT,
		ITEM_NBT_PATH,
		ITEM_COMPONENTS,
		ITEM_COMPONENT_PATHS
	}

	private static final Map<FilterNodeKind, RuleDescriptor> TABLE = createTable();

	public RuleDescriptor {
		Objects.requireNonNull(typePolicy, "typePolicy");
		Objects.requireNonNull(referencePickerSource, "referencePickerSource");
		fieldRoles = List.copyOf(Objects.requireNonNull(fieldRoles, "fieldRoles"));
		requiredRoles = Set.copyOf(Objects.requireNonNull(requiredRoles, "requiredRoles"));
		if (draftMinChildren < 0 || validMinChildren < draftMinChildren || maxChildren < validMinChildren) {
			throw new IllegalArgumentException("child bounds must satisfy 0 <= draft minimum <= valid minimum <= maximum");
		}
		if (!compound && (draftMinChildren != 0 || validMinChildren != 0 || maxChildren != 0)) {
			throw new IllegalArgumentException("atomic rules cannot have child bounds");
		}
		if (!fieldRoles.containsAll(requiredRoles)) {
			throw new IllegalArgumentException("required roles must be exposed fields");
		}
		if ((typePolicy == TypePolicy.INGREDIENT_TYPED) != fieldRoles.contains(FieldRole.INGREDIENT_TYPE)) {
			throw new IllegalArgumentException("ingredient-typed rules must expose the ingredient type field");
		}
	}

	public static RuleDescriptor forKind(FilterNodeKind kind) {
		return TABLE.get(Objects.requireNonNull(kind, "kind"));
	}

	public static Map<FilterNodeKind, RuleDescriptor> all() {
		return TABLE;
	}

	private static Map<FilterNodeKind, RuleDescriptor> createTable() {
		EnumMap<FilterNodeKind, RuleDescriptor> table = new EnumMap<>(FilterNodeKind.class);
		table.put(FilterNodeKind.ANY, compound(1, 1, Integer.MAX_VALUE));
		table.put(FilterNodeKind.ALL, compound(1, 1, Integer.MAX_VALUE));
		table.put(FilterNodeKind.NOT, compound(0, 1, 1));
		table.put(FilterNodeKind.ID, atomic(true, TypePolicy.INGREDIENT_TYPED,
			List.of(FieldRole.INGREDIENT_TYPE, FieldRole.PRIMARY_VALUE), Set.of(FieldRole.PRIMARY_VALUE),
			ReferencePickerSource.INGREDIENT_IDS));
		table.put(FilterNodeKind.TAG, atomic(true, TypePolicy.INGREDIENT_TYPED,
			List.of(FieldRole.INGREDIENT_TYPE, FieldRole.PRIMARY_VALUE), Set.of(FieldRole.PRIMARY_VALUE),
			ReferencePickerSource.INGREDIENT_TAGS));
		table.put(FilterNodeKind.BLOCK_TAG, oneValue(true, ReferencePickerSource.BLOCK_TAGS));
		table.put(FilterNodeKind.ITEM_PATH_STARTS_WITH, oneValue(true, ReferencePickerSource.NONE));
		table.put(FilterNodeKind.ITEM_PATH_CONTAINS, oneValue(true, ReferencePickerSource.NONE));
		table.put(FilterNodeKind.ITEM_PATH_ENDS_WITH, oneValue(true, ReferencePickerSource.NONE));
		table.put(FilterNodeKind.NAMESPACE, atomic(true, TypePolicy.INGREDIENT_TYPED,
			List.of(FieldRole.INGREDIENT_TYPE, FieldRole.PRIMARY_VALUE), Set.of(FieldRole.PRIMARY_VALUE),
			ReferencePickerSource.INGREDIENT_NAMESPACES));
		table.put(FilterNodeKind.EXACT_STACK, oneValue(true, ReferencePickerSource.ITEM_STACKS));
		table.put(FilterNodeKind.NBT, oneValue(true, ReferencePickerSource.ITEM_NBT));
		table.put(FilterNodeKind.NBT_PATH, atomic(true, TypePolicy.ITEM_ONLY,
			List.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE),
			Set.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE), ReferencePickerSource.ITEM_NBT_PATH));
		table.put(FilterNodeKind.HAS_COMPONENT, atomic(false, TypePolicy.ITEM_ONLY,
			List.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE),
			Set.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE), ReferencePickerSource.ITEM_COMPONENTS));
		table.put(FilterNodeKind.COMPONENT_PATH, atomic(false, TypePolicy.ITEM_ONLY,
			List.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE, FieldRole.TERTIARY_VALUE),
			Set.of(FieldRole.PRIMARY_VALUE, FieldRole.SECONDARY_VALUE, FieldRole.TERTIARY_VALUE),
			ReferencePickerSource.ITEM_COMPONENT_PATHS));
		table.put(FilterNodeKind.UNKNOWN, atomic(false, TypePolicy.NONE, List.of(), Set.of(),
			ReferencePickerSource.NONE));
		if (table.size() != FilterNodeKind.values().length) {
			throw new IllegalStateException("missing rule descriptors");
		}
		return Collections.unmodifiableMap(table);
	}

	private static RuleDescriptor compound(int draftMinChildren, int validMinChildren, int maxChildren) {
		return new RuleDescriptor(true, true, TypePolicy.NONE, true, draftMinChildren, validMinChildren,
			maxChildren, List.of(), Set.of(), ReferencePickerSource.NONE);
	}

	private static RuleDescriptor oneValue(boolean available, ReferencePickerSource pickerSource) {
		return atomic(available, TypePolicy.ITEM_ONLY, List.of(FieldRole.PRIMARY_VALUE),
			Set.of(FieldRole.PRIMARY_VALUE), pickerSource);
	}

	private static RuleDescriptor atomic(boolean available, TypePolicy typePolicy, List<FieldRole> fieldRoles,
		Set<FieldRole> requiredRoles, ReferencePickerSource pickerSource) {
		return new RuleDescriptor(available, available, typePolicy, false, 0, 0, 0, fieldRoles,
			requiredRoles, pickerSource);
	}
}
