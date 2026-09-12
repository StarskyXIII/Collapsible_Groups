# Built-in group implementation and verification

Implementation date: 2026-09-12. Minecraft branch: 1.20.1. Port baseline: `ecd974c`. Source implementation: `40aad28` on the separate 1.21.1 branch.

## Implemented behavior

The 1.20.1 Java built-in providers and service registrations are replaced by versioned JSON definitions. The shared catalog contains 528 definitions; Fabric packages all 528 and Forge packages its original 229. Loader membership, relative registration order, complete filters, names, icons, themes, and priorities are preserved. The previous per-provider configuration and mod-presence loading gates are intentionally removed. One global built-in switch controls their grouping effect while retaining individual preferences and expansion state.

Client resource reloads select complete definitions by ID across bundled groups, selected resource packs, ordinary custom files, and explicit local overrides. Managed definitions retain precedence over KubeJS. Source history and owned paths support local override, restore, and custom-copy actions. Invalid replacement layers reject publication atomically and retain the last valid generation with diagnostics.

Viewer indexes cache complete item, fluid, and generic ingredient counts and evaluation status. The manager hides only confirmed zero-count groups by default. Disabled groups, completely shadowed groups, and single matches still receive full counts. Pending, invalid, or unavailable rules remain visible. Every logical branch is inspected, including branches skipped by matching optimizations. The manager discards partially assembled cards if the index changes during assembly.

The port retains Minecraft 1.20.1's native NBT representation and Java 17 compatibility. Exact-stack diagnostics use the existing native item decoder; root and path diagnostics use the same SNBT and path parser as matching. Invalid native data is reported as an error, while missing items and foreign component payloads are unavailable. Neither condition is treated as a confirmed empty group. Existing legacy-document editing restrictions remain enforced when creating or saving owned files.

English group names are generated from JSON into the standard language file using an ownership manifest. Manual entries are preserved. Automatic deployment of bundled config-language files is removed; explicitly supplied local overlays remain supported. `/cg group_key worklist <locale> [all|missing]` reads the requested locale directly and creates a new translation work directory with uncertainty and conflict reports. Existing dump and clean commands retain their behavior.

Directory-based configuration inheritance and environment metadata remain deferred. The port adds no new per-folder settings. NeoForge is not included in this 1.20.1 build.

Usage and authoring instructions are in [built-in groups and translations](builtin-groups-and-translations.md).

## Automated validation

All suites passed with zero failed or skipped tests:

| Suite | Tests |
| --- | ---: |
| Common | 1,453 |
| Fabric | 12 |
| Forge | 8 |
| Build tooling | 7 |
| Total | 1,480 |

The common suite includes 528 independent comparisons against the captured Java-provider definitions. The corresponding provider sources in the 1.20.1 baseline are byte-identical to the provider sources used for the original capture; the fixture is restricted to the two active 1.20.1 loaders. Removing only the added schema version makes each JSON definition equal to its captured predecessor. Separate tests check loader membership and order, runtime format roundtrips, and English coverage. This verifies definition equivalence, excluding the intentionally retired loading gates.

Resource-loading tests cover whole-definition precedence, disabled replacements, duplicate IDs, last-valid ordinary files, reserved IDs, stale reload rejection, owned-file restoration, and unrelated-file preservation. Integration tests use Minecraft 1.20.1's actual `MultiPackResourceManager` for pack stacks, same-path resources with different IDs, namespace-spanning duplicates, resource filters, bundled membership after filtering, and nested local overrides.

Three Fabric Loader JUnit tests use the actual `FabricModResourcePack` and applied pack accessor. They check child-pack priority, exclusion of the mod's own bundled definitions from external sources, preservation of different IDs at one resource path, and overriding a built-in ID from another mod or selected resource pack. This covers the aggregate pack representation used by Fabric 1.20.1.

Four Forge tests use actual `PathPackResources`, nested `DelegatingPackResources`, and Minecraft's resource manager. They verify exclusion by the registered mod's physical source, retention of an external pack using the same jar name, nested child priority matching native resource lookup, and preservation of all resources at a shared path. A regression test recreates the registered pack object for the same source, as integrated-server setup does, while retaining the original client pack. That test failed with object-only recognition and passes with source-path recognition.

Evaluation tests cover all ingredient kinds, unknown logical branches, incomplete tag support, unavailable types, invalid data, index-generation changes, batch-selection pruning, and repeated cached operations without candidate rescans. Seven additional native-data tests cover valid zero-match NBT, malformed SNBT and paths under AND/OR/NOT, missing item registrations, zero-count exact stacks, foreign component payloads, matching OR branches with an invalid sibling, numeric NBT type fidelity, and programmatic NBT payloads whose JSON data is not an SNBT string.

Translation tests check exact requested-locale lookup, resource and local-overlay precedence, missing-entry detection without English fallback, malformed files, stale or mixed evaluation generations, one-match and disabled groups, key deduplication and conflicts, existing values, and unique output directories. Build-tool tests cover deterministic output, owned-key deletion, manual-key preservation and collisions, manifest corruption, generated-pair drift, concurrent writers, stale packaged resources, and read-only verification.

Validation command:

```text
gradlew.bat :common:check :fabric:build :forge:build :buildSrc:test verifyGeneratedGroupLang --parallel
```

This also passes viewer-neutral compilation, pure-EMI linkage checks, Fabric/Forge EMI mixin parity, Java 17 bytecode checks, and the existing loader metadata checks. Existing Javadoc and dependency deprecation warnings remain.

Both runtime and source jars were inspected directly: the catalogs and group files contain exactly 528 Fabric or 229 Forge entries, every catalog entry resolves to its declared versioned definition, each jar contains one standard English language file, and no old provider classes or `group_lang` files remain. All runtime classes have Java class-file major version 61 or lower.

The original working directory had five unrelated unstaged files: the 1.20.1 notes and four JEI compatibility mixin files. Their original bytes were preserved and they are excluded from this port's commit. The working-directory build includes those existing compatibility edits.

A separate snapshot containing only the staged port and baseline versions of those five files also passed all 1,480 tests and both loader builds using Java 17.0.13. Read-only language verification passed in that snapshot before its first generation task. Its runtime and source jars passed the same catalog and bytecode inspections. The existing EMI input tests require three upstream source-reference files under `.reference/emi-1.20`; the snapshot used byte-identical copies of the established local reference inputs.

On the Windows test host, the JVM's temporary local-socket directory was set for the build process with `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<temporary-directory>` to resolve a Java loopback startup error.

## Runtime validation

Runtime validation uses isolated Minecraft 1.20.1 audit instances with JEI 15.20.0.102, Fabric Loader 0.16.14 / Fabric API 0.92.12, or Forge 47.4.10. Original custom files, translations, settings, and jars are backed up before staging. The 1.21.1 runtime and performance results are not treated as 1.20.1 results.

The initial Fabric run exposed an aggregate-pack compatibility defect: Fabric 1.20.1 publishes the mod packs through one `fabric` resource pack. Treating that aggregate as an external layer classified all 528 built-ins as Packs and left the Built-in tab empty. Fabric now exposes the underlying packs in their original priority order before group loading. This also preserves external definitions that share a resource path but use different IDs. The three Fabric-specific regression tests described above exercise this behavior with the actual loader classes and applied accessor.

The corrected Fabric jar has SHA-256 `0c2c0346dc103a529208ad45d811e192e962859ed77a5492e6f5700bb5c5be51`. It entered the existing audit world, initialized JEI with 1,558 ingredients, and loaded 530 effective definitions: 528 built-ins, one existing custom group, and one group shared by the low/high test packs. With empty groups shown, reviewed screenshots confirm Built-in `528/530` and Packs `1/530`. The built-in Potion tooltip identifies its bundled JSON path; the pack tooltip identifies the high pack and the lower pack it overrides.

The Fabric `zh_tw all` and `zh_tw missing` commands created separate work directories. Both exported 15 keys, excluded 514 confirmed empty groups, and reported one uncertain existing custom group with no key conflicts or source problems. The preserved `potions_copy` fixture contains retired internal NBT envelopes and no current schema version; its NBT rules were reported as errors and the worklists correctly marked themselves incomplete. It was not silently included as a valid group or discarded as empty. The disabled built-in Potion group remained included with 42 item matches. Target-locale lookup reported zero existing target entries despite the instance's existing 528-entry English overlay, so the two scopes produced the same key set in this fixture.

Disabling the high pack and reloading revealed `Pack Order Low 1201` from `file/cg-runtime-1201-low`. A reviewed screenshot confirms the changed source tooltip and lower definition's item preview while the Packs tab remains `1/530`.

The initial Forge run likewise loaded all 229 built-ins but classified them as external resources. In Forge 1.20.1, `ResourcePackLoader.createPackForMod` names the pack after its jar file. Recognizing the exact registered pack object fixed initial loading, but an in-world resource reload exposed another issue: integrated-server setup replaces the shared pack registration with new objects while the client keeps its original packs. The final implementation also compares the registered pack's physical source path. Jar renaming does not affect recognition, an unrelated pack with the same name is retained, and recreated registrations still identify the original client pack. Nested Forge delegates remain flattened in the priority order used by native resource lookup.

The intermediate Forge jar `5d3322322b901b9e44f289c889041532c20377c4942103ada3e43577896d6596` entered the audit world and initialized JEI with 1,558 ingredients. Its initial Built-in `229/230` display was correct, but after selecting both packs its Packs count rose incorrectly to `230/231`. Its export is retained as diagnostic evidence and is not used as proof of corrected source attribution.

During that intermediate run, the existing hardcore audit character was killed by a spider after returning to the world. Subsequent checks use spectator mode. This is a persistent change to the isolated audit world; no world or player files were edited to reverse it.

The final Forge jar has SHA-256 `d484fff6136b6b2615699c4bb8c3d3abb6c200452808e35a9dce6d0d8df7e9d5`. An in-world resource reload completed at 13:37:36 local time with 231 effective definitions. Reviewed screenshots after that reload confirm Built-in `229/231` with the Potion group's bundled source, Packs `1/231`, `Pack Order High 1201`, and its high-pack source overriding the lower pack. The mod's 229 bundled definitions no longer appear as external resources after integrated-server setup.

The final Forge `zh_tw all` worklist, created after that reload, exported 15 keys, excluded 215 confirmed empty groups, and retained the same one uncertain legacy NBT fixture. It reports no key conflicts or source problems. All 14 included built-in groups have source `BUILTIN` and fallback language `en_us`; the external group has source `RESOURCE_PACK`, an unspecified fallback language, the high-pack location, and two item matches. The disabled Potion group still has 42 matches. The report is correctly incomplete because of the preserved invalid custom fixture. Forge's `missing` scope and removing the high pack were not separately exercised in this final run; those behaviors are covered by the Fabric runtime checks and automated tests described above.

Both test clients were closed. Independent byte comparisons confirmed restoration of each instance's original options, instance metadata, and all four original config/custom/language files. No generated UI-state or translation-work files remain in the instance configs, and both temporary resource packs are absent from the instances. The final Fabric and Forge jars remain installed and their hashes match the verified build outputs.

Automatic policy rejected direct recursive deletion of the generated test additions, providing only `blocked by policy` as the reason. The additions were instead moved out of the instances into the local audit evidence directory's `runtime/cleanup-retained` folder and remain there. They are not part of the commit. The Forge audit world's persistent spectator state described above remains unchanged by cleanup.
