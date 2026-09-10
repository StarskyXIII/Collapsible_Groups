# Group JSON format

Groups created from a blank editor use `schema_version: 1`. Editing or copying an existing group keeps its source format. A group without `schema_version` remains a legacy group; there is no upgrade command.

The version belongs to the whole document, including conditions nested inside `any`, `all`, and `not`. Existing names, icons, themes, priority, extra metadata, and ordinary ID, tag, namespace, and item-path conditions keep their existing meaning.

```json
{
  "schema_version": 1,
  "id": "breach_gems",
  "name": {
    "translate": "collapsible_groups.group.breach_gems",
    "fallback": "Breach Gems"
  },
  "enabled": true,
  "filter": {
    "type": "item",
    "nbt_path": "gem",
    "value": {
      "data_format": "minecraft:nbt",
      "data": "\"apotheosis:core/breach\""
    }
  }
}
```

NBT data uses an object with exactly `data_format` and `data`. `data` is an SNBT string. The quotes inside the example represent an NBT string value; numeric suffixes and typed arrays also retain their NBT types.

The outer condition determines how the same `minecraft:nbt` payload is used:

| Condition | Payload field | Meaning |
| --- | --- | --- |
| `nbt` | `nbt` | The item's root tag, expressed as an SNBT compound |
| `nbt_path` | `value` | The NBT value selected by the path |
| `stack` | `stack` | A complete ItemStack, including `id`, `Count`, and optional `tag` |

For example, this exact condition can appear inside a version 1 group:

```json
{
  "type": "item",
  "stack": {
    "data_format": "minecraft:nbt",
    "data": "{id:\"minecraft:stone\",Count:1b,tag:{values:[I;1,2],marker:1b}}"
  }
}
```

Captured exact stacks use count 1. Matching and editor selection ignore stack count while comparing the item and its NBT; byte `1b`, integer `1`, and long `1L` remain different values. Reordering compound keys does not change which exact item is selected.

Minecraft 1.20.1 legacy groups cannot save NBT or exact-stack conditions. Their ordinary editing and copying remain available. The editor disables operations that would introduce these conditions, including reference capture and variant selection. Create a blank group to use them. Whole-item selection remains available in legacy groups; removing one variant is blocked when preserving its siblings would require exact-stack conditions.

The Java and KubeJS native-item helpers create version 1 data: `Filters.nbt(snbt)`, `Filters.nbtPath(path, snbt)`, and `Filters.exactStack(itemStack)`. The explicit `ItemDataPayload` overload retains its declared format. Existing String APIs do not infer a document version from a string's contents. A caller cannot use them to save an unrepresentable legacy document.

`minecraft:data_component` holds a single component or component-path JSON value; `minecraft:item_components` holds complete native ItemStack JSON. These formats belong to the component-based Minecraft branch. Minecraft 1.20.1 preserves such conditions without evaluating or converting them.

Unknown document versions, including string `"1"`, fractional values, and JSON null, remain unsupported. Their full original JSON is retained, and editor, copy, and save operations cannot replace them with a known format. Malformed or unsupported conditions retain their original subtree. An unavailable condition stays unavailable under `not`; normal missing item data is a non-match.

The earlier internal string-envelope format is no longer read or generated.
