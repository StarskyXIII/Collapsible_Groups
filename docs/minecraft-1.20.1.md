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

The rule editor also provides two item conditions:

- **NBT** compares the complete item tag against a compound SNBT value, such as `{Potion:"minecraft:healing"}`. Item ID and stack count are outside this comparison. Combine it with an item-ID condition when both must match.
- **NBT path** compares the value at one path, such as `Potion`, against an SNBT value, such as `"minecraft:healing"`. Other tag fields do not affect this condition.

Both conditions use strict native equality: `1b` differs from `1`, list order matters, and `[1,2]` differs from `[I;1,2]`. Compound key order does not matter. A missing tag differs from an existing empty compound. A missing path, wrong container or out-of-range index does not match. Negating a valid path condition matches both missing values and unequal values; it is not an absence-only test.

Use the sample-item picker to take a complete tag or a path/value pair, then edit the condition if needed. This changes the group rule, without modifying the sample item. Samples without a tag have no NBT values to offer. Incomplete sample listings show a truncation notice; only complete, supported values are offered.

Minecraft's native SNBT parser cannot reconstruct every possible NBT object. Compounds with empty keys and non-finite floating-point values cannot be offered as complete SNBT values. A supported descendant can still be selected by path, including `[""]` for an empty key. Unsupported parents are skipped rather than converted to another type.

Paths start with a key. Plain keys use `[A-Za-z_][A-Za-z0-9_-]*`; other keys use JSON-style double-quoted brackets. Examples: `display.Name`, `Enchantments[0].id`, `["mod:key"][0]`, and `[""]`. Repeated list or typed-array indexes are supported. Wildcards, recursive descent, negative indexes and filters are rejected. Paths support up to 64 steps; SNBT supports up to 65,536 characters and 64 nested containers. Sample browsing lists at most 4,096 paths with a combined limit of 1,048,576 path/value characters.

Saved exact and NBT selections carry explicit Minecraft 1.20.1 format envelopes. Minecraft 1.21 component rules and foreign exact-selection data remain preserved but unavailable. Negating an unavailable or invalid rule does not turn it into a match. This branch does not automatically convert 1.21 item data.

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
    groups.add('pack:healing_potions', 'Healing potions', groups.all([
      groups.itemId('minecraft:potion'),
      groups.nbtPath('Potion', '"minecraft:healing"')
    ]))
    groups.add('pack:marked_tag', 'Complete marked tag',
      groups.nbt('{mode:1b}'))
  })
})
```

Source identifiers must be unique within one event. Group identifiers use resource-location syntax. A successful event replaces all sources owned by this integration, including removing sources omitted from the new definitions.

`ingredient(id, name, value)` accepts supported item selectors. Arbitrary predicates, partial NBT conditions and count conditions are rejected for that group while legal sibling groups remain eligible. Fabric `strongNBT()` ingredients are accepted when they wrap one plain item ID. Forge `strongNBT()` ingredients are rejected because Forge compares share tags and ignores capabilities, which is different from this mod's complete-stack comparison. On Forge, pass the `ItemStack` directly or use `exact(...)` for complete NBT and capability-aware matching. Explicit `exact(...)` selects a complete stack independently of its count. A listener exception aborts the candidate publication rather than publishing a partial event.

This API is shared by Forge and Fabric. It does not add the KubeJS 7 `RecipeViewerEvents` interface to KubeJS 6.

`nbt(snbt)` and `nbtPath(path, snbt)` explicitly construct CG rules and can be combined with `any`, `all` and `not`. They validate their arguments when the script runs. These entry points do not change the restrictions on native KubeJS ingredient predicates or `strongNBT()`.

## Editor behavior

The editor keeps one authoritative rule tree. Item and fluid selection controls preserve advanced conditions when selections change; repeatedly adding an existing selection leaves the tree and preview cache unchanged.

Rule edits use a transaction. Confirm commits the edited rule; Cancel restores the previous tree and selection. Changing tabs or rebuilding the screen cancels an unfinished rule edit. Save is blocked while a rule edit remains unconfirmed. Taller forms use the available screen height when the Rules panel cannot contain their fields and action buttons.

Internally, `EditorStateCore` owns the canonical rule draft and its transaction snapshot; Contents controls use a derived projection. `RuleDescriptor` centralizes node capabilities, field requirements and picker roles while the existing capability/UI contracts remain compatibility entry points. Runtime consumers use separate group, ingredient and presentation interfaces, with `EditorRuntimeAccess` retained as the aggregate contract and viewer selection still owned by the existing lifecycle coordinator.

## Current automated verification

The September 9, 2026 Java 17 build passed 889 tests: 876 common tests, nine Fabric tests and four Forge tests, with no failures, errors or skips. Full Fabric and Forge builds passed, including version, Mixin, resource, optional integration and linkage checks.

New coverage includes native NBT equality and path parsing, sample extraction limits, persistence and unsupported-data preservation, compiled query planning, rule transaction rollback, descriptor projections and runtime service boundaries. Native client verification is recorded separately from these automated results.

## Current native client verification

On September 9, 2026, Fabric and Forge with EMI and KubeJS 6 passed the native NBT fixture sequence in `tests/runtime/kubejs6/s7`: strict root/path matching through Rhino and the published repository, exact builder rejection messages with a legal peer, omitted-source removal after resource reload, and restoration of the original source after another reload.

Both editors passed sample-root and path selection, invalid-path save blocking, saving a three-condition healing-potion group with one preview result, clean reopening and discarding a dirty edit without changing the saved configuration hash. Fabric additionally passed canceling a rule edit while preserving an earlier name change, and full-screen reinitialization that removed an unfinished NBT-path insertion and restored the clean state.

The final successful fixture segments are distinct from earlier fixture preparation failures. The initial script supplied an empty compound key to Minecraft's SNBT parser, then used an unavailable KubeJS method while correcting that sample; those failures are retained in the audit history. The corrected fixture uses native tag construction and the supported `Item.of` API. Exception assertions read Rhino's wrapped Java exception message without relaxing the expected text.

These runs used the following artifacts:

| Loader | SHA-256 |
| --- | --- |
| Fabric | `E2DD09AD40DFCD95B47AAFBC38C7BFE0B4AD6119E859EABFD46A06AF2B6C1D52` |
| Forge | `BC0CF795A4B50AC5F0AB2D6DCC3E823E94995FAF5694E76F57FCE37C570DB3D0` |

Fabric and Forge with JEI alone also passed native root/path selection, a three-condition healing-potion group with one preview result, save and clean reopen. These runs used the same respective artifacts and JEI 15.20.0.102 with EMI, KubeJS and Rhino absent. All four current native clients exited normally.

These focused checks cover the changed editor and NBT integration paths. They do not constitute an exhaustive modpack, dedicated-server or viewer-coexistence release certification; the coexistence results below remain historical.

## Previous verification baseline

The results below describe the build before the NBT rule editor changes. They do not validate the current working tree.

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
