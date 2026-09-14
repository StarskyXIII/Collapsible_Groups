# Built-in source layout and Manager menu implementation

This follows the approved two-stage plan. Each stage is recorded and committed before the next begins. No automated tests or game sessions are run in this implementation pass; compilation and artifact checks are reported separately.

## 1. Category metadata and alphabetical resource paths

- Moved 895 unchanged group JSON files into 12 direct category directories and removed numeric folder/filename prefixes.
- Added build-only category metadata with explicit loader membership. Removed the three source-root mapping and its positional loader selection.
- The generator validates the full source set, then packages each loader from metadata. Common resources and group English retain the complete union. Metadata does not enter group catalogs or jars.
- Retained fixed alphabetical resource-path order, the JSON priority field, group IDs and group definition contents. Paths referenced by resource-pack filters need the new names; equal-priority overlap can resolve differently under the new order.
- Updated the small generator fixtures and real-source contracts for metadata membership and outputs, removing the numbered-filename requirement.

Validation: common production/test sources, all three loader production sources and generator test sources compiled successfully. The six runtime/source jars passed an inventory comparing complete loader ID sets and definitions (Fabric 528, Forge 229, NeoForge 895), all 895 translation key/value pairs and unchanged UI locales. All 895 source files retained their exact bytes. Numeric paths and metadata are absent from jars. Subagent review passed after adding pre-publication rejection of empty source/loader selections, with a focused test-source case preserving existing outputs. No automated tests or game sessions ran.

## 2. Manager sort/filter menu

- Added a brighter opaque surface, visible border and padding to the local menu while keeping the existing segment button styles.
- Replaced the fixed 142-pixel width with translated font measurements, including the checkbox label, and bounded the menu position/width to the screen. Short windows reduce the outer vertical margin; the four-row popup requires 84 GUI pixels of height.
- Shared one private menu layout between rendering and hit detection. Panel padding and border consume input before underlying controls, including the footer; covered controls receive no hover coordinates.
- Retained same-option release behavior and existing wheel interception. Menu opening clears prior background holds; initialization, removal, closure and Escape clear menu press state. The opener also clears its hold when dragged into the popup and released.
- Shared the editor's existing 14-pixel checkbox drawing with Manager, including green checked fill, white mark and hover outline. The row uses a hover overlay distinct from its new background.

Validation: common production/test sources and all three loader production sources compiled successfully. Final runtime/source packaging and the static EMI-only linkage check passed. Subagent review passed after correcting opener release handling and short-window margins. All six jars retain the verified group catalogs, definition contents and separate translations, and contain the updated menu implementation. No automated tests or game sessions ran; runtime appearance and interaction remain for user acceptance.

## User acceptance

1. Open Sort and filter in Traditional Chinese and English. Confirm that the popup is distinct from Manager and its buttons fit their labels without the previous excess width.
2. Toggle Show empty groups and compare its green checkbox with the copy editor's original-group disable option. Confirm that checked, unchecked and hover states are clear.
3. Check sorting and show-empty persistence after reopening Manager. Confirm that changes still affect the same cards and ordering modes.
4. Click the popup border/padding, try other mouse buttons, drag off a pressed option and release. No underlying card or footer action should occur; only releasing the originally pressed option should execute it.
5. Resize or change GUI scale, including a narrower window. Check positioning, truncated labels, cleared press state and blocked background scrolling while the menu is open.
6. Reload a resource pack and switch language. Verify group names and existing group preferences; packs referencing the former numbered paths need those references updated.

## Commits

- `28f51fa` — category metadata, unchanged definition migration and alphabetical paths.
- The menu commit includes this completed implementation record.


## Final artifacts

| Loader | Groups | Runtime jar | SHA-256 |
| --- | ---: | --- | --- |
| fabric | 528 | `fabric/build/libs/collapsible_groups-fabric-1.21.1-1.5.0.jar` | `f9392e8fef8f7e4609162c43ce1c3c55e37514d5d8f1fcdd6cffd2866c3fce8c` |
| forge | 229 | `forge/build/libs/collapsible_groups-forge-1.21.1-1.5.0.jar` | `d8cef942730aa32ddde4d604f5c25e7571e81eae96d2b9708195ba0d3925b042` |
| neoforge | 895 | `neoforge/build/libs/collapsible_groups-neoforge-1.21.1-1.5.0.jar` | `9d46f304a66b488f8e763b863f1c623308cb14786bc0e9ebcfefcd6a32f48abb` |

Corresponding `-sources.jar` files passed the same resource inventory. Existing optional JEI mixin target and deprecated API compilation warnings remain. These jars have not been installed into a launcher instance; game acceptance is pending.
