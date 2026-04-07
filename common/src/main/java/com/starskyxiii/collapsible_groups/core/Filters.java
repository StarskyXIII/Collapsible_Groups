package com.starskyxiii.collapsible_groups.core;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public final class Filters {
	private static final String ITEM_TYPE = "item";
	private static final String FLUID_TYPE = "fluid";
	private static final String STACK_PREFIX = "stack:";

	private Filters() {}

	public static GroupFilter any(GroupFilter... children) {
		requireChildren("any", children);
		return GroupFilterNormalizer.normalize(new GroupFilter.Any(List.of(children)));
	}

	public static GroupFilter all(GroupFilter... children) {
		requireChildren("all", children);
		return GroupFilterNormalizer.normalize(new GroupFilter.All(List.of(children)));
	}

	public static GroupFilter not(GroupFilter child) {
		return GroupFilterNormalizer.normalize(new GroupFilter.Not(child));
	}

	public static GroupFilter id(String type, String id) {
		return new GroupFilter.Id(type, id);
	}

	public static GroupFilter tag(String type, String tag) {
		return new GroupFilter.Tag(type, tag);
	}

	public static GroupFilter namespace(String type, String namespace) {
		return new GroupFilter.Namespace(type, namespace);
	}

	public static GroupFilter exactStack(String encodedStack) {
		return new GroupFilter.ExactStack(encodedStack);
	}

	public static GroupFilter itemComponent(String componentTypeId, String encodedValue) {
		return new GroupFilter.HasComponent(componentTypeId, encodedValue);
	}

	public static GroupFilter itemComponentPath(String componentTypeId, String path, String expectedValue) {
		return new GroupFilter.ComponentPath(componentTypeId, path, expectedValue);
	}

	public static GroupFilter exactStack(ItemStack stack) {
		String selector = GroupItemSelector.exactSelector(stack);
		return exactStack(selector.substring(STACK_PREFIX.length()));
	}

	public static GroupFilter itemId(String id) {
		return id(ITEM_TYPE, id);
	}

	public static GroupFilter itemTag(String tag) {
		return tag(ITEM_TYPE, tag);
	}

	public static GroupFilter blockTag(String tag) {
		return new GroupFilter.BlockTag(tag);
	}

	public static GroupFilter itemPathStartsWith(String prefix) {
		return new GroupFilter.ItemPathStartsWith(prefix);
	}

	public static GroupFilter itemPathEndsWith(String suffix) {
		return new GroupFilter.ItemPathEndsWith(suffix);
	}

	public static GroupFilter itemNamespace(String namespace) {
		return namespace(ITEM_TYPE, namespace);
	}

	public static GroupFilter fluidId(String id) {
		return id(FLUID_TYPE, id);
	}

	public static GroupFilter fluidTag(String tag) {
		return tag(FLUID_TYPE, tag);
	}

	public static GroupFilter fluidNamespace(String namespace) {
		return namespace(FLUID_TYPE, namespace);
	}

	public static GroupFilter genericId(String type, String id) {
		return id(type, id);
	}

	public static GroupFilter genericTag(String type, String tag) {
		return tag(type, tag);
	}

	public static GroupFilter genericNamespace(String type, String namespace) {
		return namespace(type, namespace);
	}

	private static void requireChildren(String operation, GroupFilter[] children) {
		Objects.requireNonNull(children, operation + " children");
		if (children.length == 0) {
			throw new IllegalArgumentException("Filters." + operation + "(...) requires at least one child filter.");
		}
		for (GroupFilter child : children) {
			Objects.requireNonNull(child, "child filter");
		}
	}
}
