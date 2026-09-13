# Built-in group implementation and verification

This is a historical verification record. The unpublished local full-override feature has since been retired; see the [current implementation record](manager-feedback-implementation.md).

Implementation date: 2026-09-12. Minecraft branch: 1.21.1. Comparison baseline: `398f8f08eca7ff467cfd022cdc051747702ed7c2`.

## Implemented behavior

The Java built-in providers and their service registrations were replaced with 895 versioned JSON definitions. Packaging preserves the previous loader sets and registration order: Fabric 528, Forge 229, and NeoForge 895. The earlier mod-presence gates and per-provider configuration flags were removed. Definitions now remain available for evaluation against the current environment, with one global built-in switch and the existing individual preferences.

Client resource reloads resolve complete definitions by ID across built-ins, selected resource packs, ordinary custom files, and explicit local overrides. Managed sources retain precedence over KubeJS. Source history and owned paths support local override, restore, and custom-copy actions. Invalid new layers reject publication atomically; the last valid resource generation remains visible with stale/error diagnostics.

Viewer index generation now also produces complete item/fluid/generic counts and evaluation status. Rule preflight checks every logical branch, including branches excluded by a candidate optimization or short-circuited at match time. The manager reads cached summaries, hides only complete zero-count groups by default, and retains pending or uncertain groups. Replacing an index during card assembly discards the partial cards.

Built-in English names are generated from JSON into the standard English language file with a checked-in ownership manifest. Manual entries remain intact. Runtime config-language deployment was removed; explicit existing overlays remain supported. The new `group_key worklist` command reads a requested target locale directly, produces a new output directory, and reports empty, uncertain, and conflicting entries without altering the old dump/clean workflow.

Directory-based configuration inheritance and environment metadata are deferred. This implementation adds no inherited mod-loading conditions or new per-folder control UI. Minecraft 1.20.1 is outside this change.

Usage, paths, and authoring instructions are in [built-in groups and translations](builtin-groups-and-translations.md).

## Automated validation

All suites passed with zero failed or skipped tests:

| Suite | Tests |
| --- | ---: |
| Common | 1,876 |
| NeoForge | 16 |
| Build tooling | 7 |
| Total | 1,899 |

The common suite includes 895 independent definition comparisons against captured outputs of the removed Java providers. It normalizes typed JSON back to the original representation and compares the complete definitions, including names, priority, icons, theme, extras, and nested filters. Additional catalog checks cover loader membership, stable order, runtime format roundtrips, English coverage, and all 23 Iron's Apothic definitions. This verifies definition equivalence; it does not claim preservation of the intentionally removed mod-presence loading gates.

Resource loading tests cover whole-definition precedence, disabled replacements, duplicate IDs, last-valid ordinary files, preserved reserved-ID behavior, stale reload rejection, fresh failure, invalid known IDs, owned-file restoration, and preservation of unrelated files. Integration tests use Minecraft's `MultiPackResourceManager` to check actual pack stacks, two ID values at the same resource path, namespace-spanning duplicates, resource filters, bundled-catalog membership after filtering, nested local overrides, and a source path occupied by a regular file.

Evaluation tests cover single matches, disabled and fully shadowed groups, all three ingredient kinds, unknown rules below every logical operator, matching OR branches with uncertain siblings, unavailable types, partial tag support, codec errors, missing and mismatched generations, batch selection pruning, and zero candidate scans during cached visibility or enabled-state changes. A separate manager test replaces the generation while cards are being assembled and verifies that no partial counts survive.

Translation tests cover exact requested-locale lookup, resource stack and local-overlay priority, absence of an English fallback during missing-entry checks, malformed/unreadable files, changed overlays, stale or mixed evaluation rejection, disabled nonempty groups, one-match groups, fallback-language labels, equal-key deduplication, conflicting keys, preservation of existing target values, and unique output directories that do not overwrite manual work.

Build-tool tests cover deterministic unchanged output and timestamps, deletion of only previously owned keys, manual-key preservation, manual collisions including equal values, corrupt or missing manifest state, generated-pair drift, invalid definitions, conflicting keys, parallel writers, stale generated resources, and read-only verification without source mutation.

Validation commands:

```text
gradlew.bat verifyGeneratedGroupLang
gradlew.bat :common:check :fabric:build :forge:build :neoforge:build --parallel
gradlew.bat -p buildSrc test
```

The full build also passed the viewer-neutral compilation check, pure-EMI linkage guard, and Fabric/NeoForge EMI mixin parity check. The final common test run includes the subsequent generation-race test. Existing Javadoc and Gradle deprecation warnings remain; they are not new build failures.

Runtime and source jars were inspected directly: every catalog entry is present, each loader has exactly its expected number of group JSON files, and each jar has exactly one standard English language file. No old `group_lang` files or built-in provider classes remain in the runtime jars.

## Engine performance measurement

The local headless benchmark uses Windows 11, Java 21.0.5, a 2 GiB maximum heap, 1,300 JSON groups, and 1,332 nonempty vanilla item stacks. Each of four categories has 325 groups: simple IDs, nested logical/item-path conditions, component conditions, and exact stacks. Some groups are disabled and priorities vary. The full candidate graph contains 8,450 matches; all 1,300 groups complete evaluation, and 975 have content.

The baseline candidate algorithm is taken from `398f8f0`; both algorithms use the same current matcher, definitions, item universe, and fallback registry context. This isolates the summary/index change. It is not a benchmark of the complete old mod, real-world mod registries, client startup, or rendered UI. The benchmark explicitly validates fixture codecs against fallback registries because there is no live world.

After four warmup pairs, twelve paired runs alternate execution order:

| Measurement | Baseline | Current |
| --- | ---: | ---: |
| Candidate build median | 148.20 ms | 150.76 ms |
| Candidate build range | 135.63–163.53 ms | 133.77–165.50 ms |
| Approximate retained bytes per index | 52,321 | 154,667 |

Candidate lists and resolved owners are equal between the two algorithms. Median build time differs by about 1.7%, within the overlapping sample ranges; no general speedup is claimed. The complete evaluation summaries add approximately 100 KiB for this fixture. Retained heap is estimated by retaining sixteen indexes and measuring after explicit garbage collection, so it is approximate.

Parsing all 1,300 JSON files has a median of 94.04 ms over eight warm-cache runs. Cached empty-group filtering has a median of 0.038 ms over 200 runs. Another 200 disabled-ownership recomputations also reuse the candidate graph. The combined cached operations perform zero ingredient-view calls. These timings describe the data operations, not screen rendering or input-to-frame latency.

## Runtime verification

Runtime validation uses isolated Prism instances and hashes each staged jar. Ordinary player and modpack instances are not used. The initial Fabric/EMI run entered the existing audit world with a low/high resource-pack pair and 533 effective definitions. The manager's Packs view showed the four added groups, A–Z sorting reordered them correctly, and turning Show empty groups on revealed the deliberately impossible `stone AND dirt` group.

The initial command outputs were checked directly:

| Check | Observed result |
| --- | --- |
| Highest selected resource-pack definition | `runtime_pack_order` used the high pack's complete definition. |
| `worklist zh_tw all` | 25 keys, one existing target-locale entry, 508 empty groups excluded. |
| `worklist zh_tw missing` | 24 keys; the existing alpha key was omitted. |
| Existing target value | `甲資源群組` was retained. |
| Uncertainty and conflicts | Both reports complete, with zero uncertain groups, conflicts, or source problems. |
| Language overlays | The log loaded an existing 895-entry English overlay from the audit instance; the requested `zh_tw` worklist still used the actual target-locale entries. |

This first run used Fabric jar SHA-256 `79cb57495657f68c4e869abff516c9d941fe6b4554258266b154abd0618ba82d`. It verified the checks above, not the complete override/copy/restore flow. Visual review of its screenshot found that source-tooltip newlines rendered as control glyphs. The tooltip now uses wrapped multiline rendering with a width limit, and source-action failures now show a footer message. Both corrections passed a subsequent full build and automated checks.

The first Forge 52.1.0 / JEI 19.42.0.379 launch exposed a pre-existing manifest defect: the optional JEI dependency omitted `versionRange`, and Forge rejected the installed viewer with an empty expected range. The Forge manifest now expands the existing `jei_version` build property to `[19.42.0.379,)`. A Forge rebuild passed, and the packaged manifest was checked directly before retrying the game. The dependency remains optional.

NeoForge 21.1.248 with EMI 1.1.24 passed its final-artifact smoke using SHA-256 `163f4ba4e20ccf680944ab07c2ab5bd5ada9f44ca801508d025921d73ac32e8a`. It entered the audit world, displayed all 895 built-ins among 908 effective definitions, sorted A–Z, and displayed a bounded multiline source tooltip. The screenshot was reviewed after capture and confirmed that the newline/control-glyph defect was fixed.

Its `zh_tw missing` worklist exported 339 keys, excluded 460 confirmed empty groups, and reported 109 unavailable groups with no key conflicts or source errors. Missing `enderio:soul_vial` and `irons_spellbooks:spell_container` entries were among the reported causes. The command correctly marked the worklist incomplete, preserving the distinction between an unavailable rule and a confirmed empty group. The game exited cleanly.

The final Forge jar, SHA-256 `ad08c522fb13c55f47c0aebde0ed83160347fd5fb2d4b700b544bdb83d1ccef2`, entered the audit world after the manifest correction. Its log confirmed Collapsible Groups initialization, 229 built-in definitions, and successful JEI runtime construction with 1,688 ingredients. The JEI panel was not visible in the audit inventory. Temporary inventory and overlay bindings did not recover it, so the Forge manager interaction checks remain blocked and are not counted as passed. The temporary bindings were restored and the game exited cleanly.

The final Fabric jar, SHA-256 `e8bf853cfde386c1e6d7dc4f95c61cedcd5052b07f5a0f582144fc16e861be4c`, passed the resource-source editing flow with EMI 1.1.24. Creating and saving a local override changed the card's source to Local override. Copying the pack group produced `runtime_pack_order_copy` in the ordinary custom directory, retained `schema_version: 1`, and increased the effective-definition count from 533 to 534. Restoring the original group removed only its owned override file and returned the card to Packs. The override removal and custom-copy contents were also checked directly on disk.

Disabling the high resource pack and reloading changed the original group's name and source to Pack Order Low; its tooltip identified `file/cg-runtime-pack-low` and the expected group resource path. The deliberately empty group's disabled state survived reopening the manager and was then restored. These checks supplement the initial empty-visibility, sorting, and target-locale export checks above.

## Complete-client comparison

An additional comparison ran the actual previous and final Fabric jars in the same isolated Minecraft 1.21.1 / Fabric Loader 0.19.2 / EMI 1.1.24 instance. The previous jar's SHA-256 was `afeab0b7086771de976a847c464b0753500dcc7d085e8eeb4a5920b30a20e484`; the final jar was the `e8bf853c` artifact identified above. The host was Windows 11, an Intel Core i5-12400, and 32 GiB RAM. Both game processes used Java 21.0.4 with a 2 GiB initial and 4 GiB maximum heap; the headless benchmark above used a separate Java 21.0.5 runtime.

Both runs used the same 1,300 mixed JSON fixtures, two existing custom groups, other mods, world, and low resource pack. Debug timing was enabled in both. The Custom tab showed 1,302 groups with no search and A–Z sorting. Show empty groups was enabled in the final version and remained enabled after reopening. Total definitions were 1,323 before and 1,834 after: the new version retains all 528 Fabric built-ins and loads the four pack definitions. This is a comparison of the complete feature change, so the total definition sets intentionally differ.

There was one fresh process per version; the operating-system file cache was not cleared. Startup observations use process creation and log markers, excluding time spent waiting at menus. Both versions were measured in the Custom manager after the viewer finished loading. Each heap sample used one explicit garbage collection followed by `GC.heap_info`; process private bytes were sampled separately. The game remained live, so these are approximate observations rather than retained-size attribution to the mod.

| Initial observation | Previous | Final |
| --- | ---: | ---: |
| Fresh process to both group publication and final startup texture-atlas log | About 11 s | About 18 s |
| First full EMI reload reported by EMI | 713 ms | 721 ms |
| First manager card rebuild reported by the mod | 32 ms | 38 ms |
| Used Java heap after explicit GC | 442.6 MiB | 445.1 MiB |
| Process private memory | 3,070.1 MiB | 3,061.4 MiB |

Startup log timestamps have one-second precision. The longer startup observation is recorded as a cost of this run, not dismissed as a speedup or a passed latency budget. A single pair with different full definition counts cannot establish a general regression rate or memory saving. Manager timings cover card construction; they are not rendered-frame or input-to-frame latency.

Both versions completed scrolling through the middle and end of the Custom list, A–Z / Z–A sorting, and closing and reopening the manager with the same 1,302 groups. The final version also preserved Show empty groups across reopening. These were visual behavior checks, without an instrumented input-to-frame measurement. The baseline logged one additional 25 ms card rebuild; later baseline rebuilds and subsequent final rebuilds produced no entry at the existing 20 ms logging threshold.

F3+T did not reliably reach the game through the test input environment. Both versions therefore used the same resource-pack UI sequence: disable the low pack and apply, then re-enable it and apply. The following figures describe the second reload, with the low pack restored:

| After resource reload | Previous | Final |
| --- | ---: | ---: |
| Resource-manager start log to EMI completion log | About 1 s | About 4 s |
| EMI's own reload timer | 186 ms | 178 ms |
| Used Java heap after explicit GC | 446.4 MiB | 444.9 MiB |
| Process private memory | 3,107.4 MiB | 3,077.7 MiB |
| Custom groups after reopening | 1,302 | 1,302 |

The longer complete resource-reload interval remains visible even though EMI's own timer is similar: its timer does not include earlier resource loading and group publication. This measurement does not attribute that difference to a particular method. No general startup, reload, or memory improvement is claimed. The functional checks completed successfully; the small sample and coarse outer timings limit performance conclusions.

Both game processes exited. Debug settings, manager state, resource-pack selection, and the temporary inventory binding were restored, and the isolated instance retained the final jar. Cleanup verified all 1,300 fixture hashes, but automatic approval review blocked their deletion. Those fixtures therefore remain in the isolated audit instance alongside its two custom files. Duplicate screenshot/log evidence accidentally written to another workspace was also retained after a blocked cleanup attempt. These retained test artifacts are outside the committed implementation and do not affect ordinary play instances.
