# Manager feedback implementation

Implementation follows the approved five-step design. Each step is recorded and committed before the next starts. Automated tests and game sessions are not run in this implementation pass; compilation and packaging checks are recorded separately from runtime acceptance.

## 1. Custom copies and local override retirement

- Removed the unpublished full local override source, directory reader, create/restore actions, menu, and unused forwarding methods. Existing override files are ignored and left untouched.
- Built-in, resource-pack and scripted groups open an unsaved custom-copy draft directly. Saving writes a new custom group; cancelling does not write or change the source.
- The optional source-disable operation runs after successful copy saving. Failure or a missing source is reported in Manager without misreporting the saved copy as failed.
- Kept individual enabled preferences, resource-pack definition replacement, and custom file ownership, path, ID and format checks.
- Updated affected test sources and public documentation to match the removed feature.

Validation: common production/test sources and Fabric, Forge and NeoForge production sources compiled successfully. No tests or game sessions ran. Subagent static review passed with no blocking finding. Runtime acceptance still needs copy/cancel/save, partial success, and custom-group editing/deletion.

## 2. Manager interaction and loading presentation

- Restricted card hover, control hints, held switches and preview wheel handling to the visible card viewport.
- Removed blanket file/source-path tooltips. Evaluation details are available on the status text, and the built-in badge explains global disabling.
- Shared the editor's existing checkmark shape with the show-empty option at the same 14-pixel size.
- Centered loading text within the Contents source grid, Rules preview body, or Look preview area. The loading Look preview does not accept hidden preview clicks.

Validation: common production and test sources compiled successfully. Static review identified an invisible Look preview click path; it was gated while loading before commit. No tests or game sessions ran. Runtime acceptance covers header/footer clipping, preview scrolling, checkmark rendering and loading placement.


## 3. Save return and index display continuity

- Manager assembles cards from one captured repository source map and one captured viewer display generation. Unchanged filters and document formats retain full-match previews and evaluations while a local rebuild runs; changed/new groups wait independently.
- Saving records the result, publishes the change, and returns to Manager. Removed synchronous saved-preview population and the redundant pre-return card rebuild.
- Editor drafts no longer write into published viewer preview maps. Cache reads require the indexed filter and document format to match; changed drafts use editor resolution.
- Added SOURCE_RELOAD for resource/config reload and client tag updates on Fabric, Forge and NeoForge. JEI clears source caches before scheduling its rebuild; EMI invalidates its bootstrap epoch and re-enumerates its source. Script replacement and runtime reset also invalidate retained display results.
- Manager subscriptions exist only while its screen is active. Captured readiness completion, including failures, schedules the display refresh; closing the screen cancels queued refresh ownership.
- Closed the JEI publication/future completion gap and suppressed obsolete failed builds when a newer revision is requested. EMI bootstrap failures now settle readiness exceptionally instead of leaving an indefinite wait.
- Winning source categories are calculated once per repository publication, retaining constant-time snapshot reads on ownership hot paths.
- Removed the assembler's all-card fallback clearing and mutable-copy wrapper, and updated focused test sources for display retention, source capture, draft isolation and completion handling.

Validation: common production/test sources and all three loader production sources compiled successfully. Subagent static re-review confirmed that the four blocking findings were resolved. No tests or game sessions ran. Runtime acceptance covers edit/save/cancel, unchanged empty groups, view position, rapid consecutive saves, source reload and failed rebuild display.


## 4. Short resource paths and separate group translations

- Moved all 895 source definitions to `assets/collapsible_groups/groups/`. The loader scans only this namespace/path; the retired long path is ignored.
- Used the existing ownership manifest to identify exactly 895 generated group keys and checked every fallback before removing them from UI English. All 425 manual UI entries were preserved. Removed the manifest afterwards.
- Replaced source-language merging with a pure generator that emits `build/generated/group-language/assets/collapsible_groups/group_lang/en_us.json`. Removed the writer lock, hash/ownership bookkeeping, two-file rollback and drift-verification task. Manual group locales remain supported without generating empty locale files.
- Added one shared client language mixin and shared resource ordering. Pack identity determines priority across normal and group language files; within a pack, group language wins. Native locale fallback and resource filters remain in effect.
- Group language resources are fully validated before native parsing; malformed group files become per-resource IO failures. Translation worklists use the same ordering, read only the requested locale, and fail without output on malformed input.
- Connected generated group language to resource processing and source jars, excluding only retired group paths and empty directories. Updated the resource-pack guide and focused generator/language test sources.

Validation: all production sources, common test sources and generator test sources compiled; Subagent static review passed. All six runtime/source jars contain the expected catalogs and short group paths (Fabric 528, Forge 229, NeoForge 895), exactly one 425-key UI English file and one 895-key group English file, and the common language mixin. No retired group path was present. No tests or game sessions ran. Native language reload and selected-language behavior remain for user acceptance.


## 5. Cleanup and final artifact checks

- Removed the former Java-provider migration fixture (about 400 KB) and its 895 fixed historical comparisons. Source contracts now check actual definition parsing, IDs, paths, loader root membership, catalog order and separate English fallback coverage.
- Removed unused JEI lazy-preview lookup records, fallback resolution, per-entry mutation and forwarding methods. Removed their exclusive tests while retaining generation immutability, event coalescing, invalidation, failure recovery and display capture coverage.
- Removed the obsolete full-match methods from the shared viewer interface and its implementations. Kept the published full-match maps and the editor's definition-checked cache entry reader.
- Moved copy tests beside the catalog/repository and removed redundant reset forwarding, stale comments and unused imports. Removed the unused source-path tooltip translation, leaving 424 UI keys per supported UI locale.
- Updated public documentation and retained the earlier verification document explicitly as historical evidence.

Validation: common production/test sources and all three loader production sources compiled successfully. Runtime and source jars were rebuilt; the static EMI-only linkage check passed. Final Subagent review passed with no blocking findings. The six-jar inventory below passed, including absence of retired paths and removed lazy-preview classes. No automated tests or game sessions ran. Compiled test sources are not reported as executed tests.

## User acceptance

1. Scroll Manager so cards are partly behind the header/footer. Hidden portions must produce no card tooltip, hover effect, switch interaction or preview-wheel capture. Visible controls and status diagnostics should work; hovering an ordinary card should not show file paths.
2. Check the show-empty checkmark and loading text in Contents, Rules and Look. Loading Look previews should not accept invisible preview clicks.
3. Copy a built-in/resource-pack/scripted group: cancel without saving, then copy and save with and without disabling its source. The draft must never create a local override. Existing custom groups remain editable/deletable.
4. Edit/save a group, change metadata only, edit rules, and save several groups in succession. Unaffected previews, empty filtering and view position should remain stable; only affected groups wait. Edit rules, resize the editor, then cancel to confirm that an unsaved preview does not leak into Manager.
5. Reload resources/tags and switch language. Group names should follow `group_lang`, normal pack priority, selected locale and English fallback. Confirm that worklists use only the requested locale plus its explicit config overlay.
6. Repeat the main Manager flow under the supported JEI versions and EMI configuration. Compilation does not establish runtime viewer or mixin compatibility.

## Commits

- `70dba7e` — custom copies and full local override retirement.
- `94ab87c` — visible interaction bounds, checkbox and loading placement.
- `9e41ce6` — display continuity, source invalidation and save return.
- `183846d` — short group paths and separate group translations.
- The final cleanup commit contains this record and the completed artifact results below.

## Final artifacts (2026-09-14)

All runtime and source jars contain matching catalogs and short group paths, one 424-key UI English file, one matching UI Traditional Chinese key set, and one separate 895-key group English file. UI/group keys do not overlap. Every catalog entry resolves to the expected group ID, and the common client language mixin is included. The retired long resource path and deleted lazy-preview classes are absent.

| Loader | Packaged groups | Runtime jar | Runtime SHA-256 |
| --- | ---: | --- | --- |
| Fabric | 528 | `fabric/build/libs/collapsible_groups-fabric-1.21.1-1.5.0.jar` | `ffabdaab662e1f6033339f5a6ac1edd0e67b02cc13443fb8562bbe35653e6776` |
| Forge | 229 | `forge/build/libs/collapsible_groups-forge-1.21.1-1.5.0.jar` | `63712a831eeffb7a11f340fca689dcfb2cc2cc6b53e652f6a398b1cb55953443` |
| NeoForge | 895 | `neoforge/build/libs/collapsible_groups-neoforge-1.21.1-1.5.0.jar` | `3705fd16670c8fa8aa4c8a774d174978678454c23f521736ed097b2781e13b85` |

Corresponding `-sources.jar` files passed the same resource inventory. Existing optional JEI mixin target and deprecated API compilation warnings remain; game acceptance is required to establish runtime behavior across supported viewer versions. These artifacts have not been installed into a launcher instance.
