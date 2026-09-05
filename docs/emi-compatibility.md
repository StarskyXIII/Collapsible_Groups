# EMI integration validation — Minecraft 1.21.1

Validated on 2026-09-05 using Prism Launcher 11.0.3, NeoForge 21.1.248 and Java 21.0.4.
These results cover the listed workflows, not every mod or ingredient type.

## Editor validation and preview follow-up

The editor now reuses validation results while the current filter remains value-equal,
including detecting direct draft-node changes. Invalid drafts still block saving and retain
the previous valid preview. EMI reuses the already validated preview definition instead of
constructing and validating it again. Optional JFR events record editor render and click
handler durations when recording is enabled.

This follows the header/preview allocation fixes in `0bba026` and ordinary item-ID indexing
in `0157f5d`. Independent source review, 681 common tests and the full Fabric, Forge and
NeoForge build passed, including neutral compilation, EMI-only linkage and mixin parity.

The final NeoForge test JAR SHA-256 was
`4F960B8F040C512776BE6C5EBF33927567B86DF7404B9318F218FD50910D8E93`.
On pure EMI with 15,848 source items, a 6,165-exact-rule draft recorded 4,251 focused
frames in a 40-second recording: render p50/p95/p99 were 5.264/8.599/13.027 ms.
The first whole-item-to-exact conversion still took 714 ms in the click handler and
919 ms to the following render end. A separate 252-ID draft measured three additions
at 35.590, 1.769 and 2.041 ms in the handler.

Render duration is CPU-side wall time, not GPU presentation time. Click measurements
exclude the OS input queue. Individual additions are not percentile distributions, and
recordings from different sessions are not controlled before/after FPS comparisons.
Cold preparation remains a known stall. A progress/preparation interface and incremental
warmup were deferred; this change does not implement them or claim cold-stall resolution.

Latest runtime checks covered mixed item/fluid editing and persistence with pure EMI,
EMI + TMRV, EMI + JEI and pure JEI. The final EMI + TMRV run verified reopening and
adding to a mixed group; disabled editing was additionally checked before the final
preview-definition reuse change. EMI + JEI reopened a large exact group and preserved
edits, but the JEMI ABI error described below still prevents full compatibility.

Pure JEI with AE2 19.2.17 exposed only one Facade variant, oak log, including after reload.
The EMI-created fixture excluded that variant, so its 6,165 exact Facade rules matched
none of JEI's available Facades. Local JAR inspection found AE2 EMI/REI integration but
no AE2 JEI integration; JEI deduplicates by item when no subtype data is supplied.
This supports a source/subtype limitation rather than a cross-viewer exact-decoding bug.
Collapsible Groups continues to use the active viewer's available ingredients.

The earlier results below remain historical observations. Neither this follow-up nor
those checks establish exhaustive integration coverage or long-duration heap stability.

## Changes

| Commit | Result | Validation before commit |
| --- | --- | --- |
| `7a2ccfa` | Merged main through `bf73289`, retaining the JEI public typed-ingredient factory and provider changes. EMI editor ownership now uses exact serialized identities from one ready generation. | 653 tests, common checks and NeoForge build; independent source review. |
| `d494874` | Added effective-component lookup and a generation-local exact-selector result cache, bounded to 8,192 entries and an estimated 32 MiB. | 663 tests, common checks, NeoForge JAR and Prism Facade stress test; independent source review. |
| `2f8aad7` | Disabled live EMI previews now clear items, fluids and generic entries. Initial prepared previews remain available for editing disabled groups. Shared labels use viewer-neutral wording. | 664 tests, common checks, NeoForge JAR and Prism disabled/save/reopen test; independent source review. |

The final full Gradle build passed for Fabric, Forge and NeoForge, including neutral compilation,
EMI-only linkage and mixin parity. Loader builds do not establish runtime coverage on Fabric or Forge.

## Runtime checks

| Installed viewers | Observed result |
| --- | --- |
| EMI 1.1.24 | Manager/editor, disabled live preview, save/reopen, water-fluid creation, and reload followed by a new 6,165-selector draft passed. |
| EMI 1.1.24 + TMRV 0.9.0 | Startup, manager/editor, reading a saved water group, adding an item, mixed-group save/reopen and disabled preview passed. |
| EMI 1.1.24 + JEI 19.52.0.423 | Manager/editor and 6,165-selector cold/warm edits passed after the performance change. Full compatibility fails because of the external JEMI recipe ABI mismatch below. This run preceded the disabled-preview/label change. |
| JEI 19.52.0.423 alone | Opening inventory no longer crashes. Reading the EMI-created mixed group, toggling enabled state, adding another item, saving and expanding the mixed header passed. |

The mixed-group persistence check retained ordinary item/fluid filter IDs across viewer changes.
Disabled groups display complete prepared contents when first opened; subsequent live disabled
rebuilds display no matches, consistently on EMI and JEI.

## Performance

For 6,166 AE2 Facades, removing one converts the whole-item rule into 6,165 exact selectors.
Measured `EditorRightPanel.rebuild` times on the same instance:

| Operation | Before | After |
| --- | ---: | ---: |
| First removal | 908 ms | 130 ms |
| Add oak log to the resulting draft | 733 ms | 33 ms |
| Add another item | Not measured | 21 ms |

A separate pure-EMI run after reload measured 95 ms for the first removal.
These are individual observations, not benchmark distributions or guarantees of stall-free frames.
Structural tests cover 1, 10, 100, 446 and 6,165 selectors: unchanged warm selectors cause no
additional decodes or component comparisons. Tests also cover hash collisions, count/damage,
effective default components, unavailable filter nodes, registry invalidation and both cache limits.

## External limitations

EMI 1.1.24's `JemiRecipeSlotBuilder` does not implement the six-argument
`IRecipeSlotBuilder.setPosition` expected by JEI 19.52.0.423. Chipped recipe bridging throws
`AbstractMethodError` in this combination. The Collapsible Groups fixes do not repair that ABI;
this version combination must not be advertised as fully compatible.

Pure EMI exposed 447 Facades on first load and 6,166 after `/reload` in the tested pack.
Local bytecode review shows that AE2 constructs Facades from other creative tabs' existing contents,
while EMI rebuilds tabs in sequence. This strongly supports an upstream initialization-order cause;
JEI may change the order by populating tabs earlier. It is not a fixed EMI-versus-JEI count difference.
Definitive attribution requires comparing raw EMI entries against group entries, or testing without
Collapsible Groups. The adapter has no Facade-specific cap and follows the active viewer universe.

Runtime coverage does not include KubeJS, Mekanism/Productive Bees generic ingredients,
Iron's Apothic fixtures, dedicated servers, exhaustive nested-filter/UI modifier combinations,
or long-duration heap profiling. Automated contracts cover additional logic, but do not replace
those integration tests. These remain release-validation gaps.
