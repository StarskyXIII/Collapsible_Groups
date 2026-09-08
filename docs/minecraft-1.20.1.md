# Minecraft 1.20.1 branch

This branch targets Java 17, Forge and Fabric. It is separate from the Minecraft 1.21.1 implementation. Historical 1.21.1 results in `emi-compatibility.md` do not establish compatibility for this branch.

## Dependency baseline

| Dependency | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.92.12+1.20.1 |
| EMI | 1.1.24+1.20.1 |
| JEI | 15.20.0.102 |
| KubeJS | 2001.6.5-build.26 |
| Rhino | 2001.2.3-build.10 |
| Architectury | 9.2.14 |

JEI, EMI and KubeJS integrations are optional. Use the artifact for the selected loader.

## Item data

Exact item selections use native Minecraft NBT, with item counts normalized to one. Matching compares the item and its complete NBT. Numeric NBT types, lists and typed arrays retain their native meaning.

Saved exact selections carry an explicit Minecraft 1.20.1 format envelope. Minecraft 1.21 component rules and foreign exact-selection data remain preserved but unavailable. Negating an unavailable rule does not turn it into a match. This branch does not provide a complete NBT path editor or automatic conversion of 1.21 item data.

## KubeJS 6

Put group definitions in a client script using `CGEvents.groups`:

```js
CGEvents.groups(event => {
  event.source('pack:materials', groups => {
    groups.item('pack:stone', 'Stone', 'minecraft:stone')
    groups.add('pack:building', 'Building materials', groups.any([
      groups.itemId('minecraft:cobblestone'),
      groups.itemTag('minecraft:planks')
    ]))
    groups.fluid('pack:water', 'Water', 'minecraft:water')
    groups.exact('pack:marked_stone', 'Marked stone',
      Item.of('minecraft:stone', '{mode:1b}'))
  })
})
```

Source identifiers must be unique within one event. Group identifiers use resource-location syntax. A successful event replaces all sources owned by this integration, including removing sources omitted from the new definitions.

`ingredient(id, name, value)` accepts supported item selectors. Arbitrary predicates, partial NBT conditions and count conditions are rejected for that group while legal sibling groups remain eligible. Fabric `strongNBT()` ingredients are accepted when they wrap one plain item ID. Forge `strongNBT()` ingredients are rejected because Forge compares share tags and ignores capabilities, which is different from this mod's complete-stack comparison. On Forge, pass the `ItemStack` directly or use `exact(...)` for complete NBT and capability-aware matching. Explicit `exact(...)` selects a complete stack independently of its count. A listener exception aborts the candidate publication rather than publishing a partial event.

This API is shared by Forge and Fabric. It does not add the KubeJS 7 `RecipeViewerEvents` interface to KubeJS 6.

## Verification status

The complete Forge and Fabric builds pass, including Java 17 class versions, Mixin compatibility, resource-pack format, optional JEI metadata, viewer-neutral compilation and EMI linkage checks. Automated tests pass with no skips: 834 common tests, eight Fabric tests and three Forge tests. The Fabric total includes native ingredient tests using Fabric Loader JUnit.

The repeatable client scripts in `tests/runtime/kubejs6` exercise native ingredients and real KubeJS event publication through a full client lifecycle. Both loaders passed the EMI + KubeJS sequence: exact matching, legal-peer publication after rejecting unsupported ingredients, direct post failure atomicity, resource-reload source removal/replacement/re-add, failed recollection without partial publication, and recovery.

Fabric also passed editor copy/save/reopen with a complete healing-potion NBT selection, plus discarding a dirty edit without changing the saved file. Forge passed basic editor copy/rename/save/reopen. These are distinct coverage levels; the full exact-selection editor sequence was performed on Fabric.

Fabric and Forge JEI-only clients initialized successfully and displayed their ingredient overlays; normal item bookmarking was exercised on both. Both loaders passed EMI + JEI coexistence with KubeJS and Rhino absent, showing one EMI overlay and the group manager control.

Verified artifacts:

| Loader | SHA-256 |
| --- | --- |
| Fabric | `2974539A22565CE6538FED8FACBCD8FF58554C2B9DEE96685300F9719FEA672A` |
| Forge | `01F09AE4AC5FE093370E2B0E9254E4664C5C6A55E6C4D063CFD4CDE8ADB266E2` |

JEI-only checks use these artifacts directly. Earlier EMI/KubeJS results cover unchanged classes: the final rebuild changed only the JEI bookmark callback class, verified by comparing every archive entry. The preceding Forge rebuild changed only optional JEI metadata. Historical failures are not counted as passing runtime evidence.
