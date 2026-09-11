# Group JSON format

A group created from the editor’s blank form uses `"schema_version": 1`. Editing an existing group keeps its source format, as do supported copy and local-override actions for built-in, resource-pack, and KubeJS groups. Unversioned groups stay unversioned; there is no automatic upgrade or upgrade tool.

The version belongs to the whole document, including every nested `any`, `all`, and `not` condition. The existing `id`, `name`, `enabled`, `icon`, `theme`, `priority`, and `extra` fields keep their meanings. Ordinary ingredient IDs, tags, namespaces, and item-path conditions do not use data payload objects.

```json
{
  "schema_version": 1,
  "id": "logs",
  "name": {"translate": "collapsible_groups.group.logs", "fallback": "Logs"},
  "enabled": true,
  "filter": {"type": "item", "tag": "minecraft:logs"}
}
```

## Component values

A component condition compares the entire encoded component. A component-path condition compares the JSON value at one path. Both use `minecraft:data_component`. A payload contains exactly `data_format` and `data`; the latter is a typed JSON value, not a second serialized JSON document.

```json
{
  "schema_version": 1,
  "id": "custom_string_value",
  "name": {"translate": "collapsible_groups.group.custom_string_value", "fallback": "Custom string value"},
  "enabled": true,
  "filter": {
    "type": "item",
    "component": "minecraft:custom_data",
    "path": "value",
    "value": {"data_format": "minecraft:data_component", "data": "1"}
  }
}
```

In the value field, enter `1` for a number, `"1"` for a string, `true` for a boolean, `null` for JSON null, or an object or array. An empty string is `""`; the string containing the word null is `"null"`. The reference picker supplies the complete literal with its type intact. Invalid or incomplete JSON leaves the form open and prevents Apply; Cancel restores the previous rule.

The path addresses a subvalue and is not decoded as a complete component. A missing component or path is a normal non-match. Unknown component types, unavailable codecs or registry data, malformed expected component values, and invalid exact stacks are unavailable. Negation keeps an unavailable result unavailable. `any` can still succeed through another matching condition; a definite failure in `all` remains a failure.

## Exact item stacks

An exact stack uses `minecraft:item_components` with Minecraft’s native ItemStack JSON. Component patches, including removal entries, are retained. Captured counts normalize to one, and matching compares the item and its components independently of count.

```json
{
  "schema_version": 1,
  "id": "plain_stone",
  "name": {"translate": "collapsible_groups.group.plain_stone", "fallback": "Plain stone"},
  "enabled": true,
  "filter": {
    "type": "item",
    "stack": {
      "data_format": "minecraft:item_components",
      "data": {"id": "minecraft:stone", "count": 1}
    }
  }
}
```

Selection, highlighting, removal, and preview compare decoded item equivalence rather than the spelling of the saved JSON. Native decoding accepts only complete successful codec results; a partial result is unavailable. Cached decode results retry when the registry identity changes.

## Existing groups and unsupported data

Released unversioned groups continue to use string payloads. A component `"value": "1"` keeps its original behavior and can match either the encoded string `"1"` or number `1`. It is not automatically parsed into a typed number during editing, copying, or saving. Existing exact `"stack"` JSON strings remain supported.

Only a numeric value equal to `1` (including `1.0` and `1e0`) is recognized as the current document version. Unknown or invalid versions, including `"1"`, `1.5`, and `null`, retain the complete original document and remain unavailable. They cannot be overwritten through ordinary save or copy actions. Unrecognized or malformed versioned data nodes preserve their complete JSON subtree. The unpublished experimental envelope format is no longer supported.

The `minecraft:nbt` payload is used by the Minecraft 1.20.1 branch, with SNBT strings for NBT values and exact stacks. It remains opaque on 1.21.1; there is no automatic NBT/component conversion.

## Java APIs

Existing `Filters.itemComponent(String, String)`, `Filters.itemComponentPath(String, String, String)`, and exact-stack String APIs retain their existing behavior. New typed entry points are `Filters.itemComponentValue(String, JsonElement)`, `Filters.itemComponentPathValue(String, String, JsonElement)`, and `Filters.exactStackValue(ItemStack)`; constructing a definition from typed conditions selects version 1. `Filters.exactStack(ItemDataPayload)` accepts an explicit payload.

A definition mixing a released component String condition with typed conditions cannot be saved by guessing a conversion. Serialization rejects that mixture and checked saving returns failure before changing a file. Existing KubeJS String-based groups stay on their original format. Bundled definitions now use JSON; the Java built-in provider extension has been removed. See [resource-pack loading and local overrides](builtin-groups-and-translations.md) for supported definition sources.
