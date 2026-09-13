# Built-in groups, resource packs, and translations

This guide covers Minecraft 1.21.1. Built-in definitions use the same JSON format as custom groups. The in-game manager identifies the effective source and creates editable custom copies without editing a mod jar or resource pack.

## Built-in definitions and the global switch

The repository contains 895 definitions. Loader coverage is preserved: Fabric packages 528, Forge 229, and NeoForge 895. Each loader retains the relative order of the definitions it previously provided. Definitions are loaded independently of whether the corresponding mod is installed; matching against the current viewer determines their content.

`defaultGroups.enabled` is the single built-in switch. Turning it off disables the grouping effect of every ID in the bundled catalog, including resource-pack or custom replacements for those IDs. Definitions, individual enabled preferences, and expansion state remain available. Turning the switch back on preserves the individual choices. A custom copy with a new ID is independent of this switch.

The previous Generic, Vanilla, integration master, and per-mod loading settings are retired and have no effect. There is no conversion of their old values into individual group preferences. Folder-based configuration inheritance or environment metadata is deferred; directory names do not act as mod-loading conditions.

## Resource-pack definitions

Place group JSON in a client resource pack at:

```text
pack.mcmeta
assets/collapsible_groups/groups/<path>.json
assets/collapsible_groups/group_lang/en_us.json
assets/collapsible_groups/group_lang/zh_tw.json
```

Only the `collapsible_groups` namespace is scanned for group definitions. The former double-directory path is no longer read. Subdirectories below `groups` are supported. A minimal resource pack for Minecraft 1.21.1 can use:

```json
{"pack":{"pack_format":34,"description":"My group definitions"}}
```

A complete group definition might be:

```json
{
  "schema_version": 1,
  "id": "my_stones",
  "name": {"translate": "my_pack.group.stones", "fallback": "Stones"},
  "enabled": true,
  "priority": 0,
  "filter": {
    "any": [
      {"type": "item", "id": "minecraft:stone"},
      {"type": "item", "id": "minecraft:cobblestone"}
    ]
  }
}
```

Use the existing ID to replace a group or a new ID to add an independent group. Names, filters, icons, theme, enabled value, format, and priority all come from the selected definition; fields are not merged with lower sources. The numeric `priority` still controls ingredient ownership between different group IDs.

Source precedence, highest first:

| Source | Location or selection |
| --- | --- |
| Ordinary custom group | `config/collapsiblegroups/groups/*.json` |
| Client resource pack | Minecraft's selected pack order, highest pack wins |
| Built-in definition | The current loader's bundled catalog |
| KubeJS group | Published script definition |

The loader examines every group resource layer before resolving IDs. Resource paths and group IDs are separate: two definitions at the same path can still have different IDs. Minecraft resource-pack filters suppress matching lower resource paths. An explicit disabled replacement does not expose a lower definition of the same ID.

A duplicate ID within one resource pack, the bundled set, or a resource set is an error. Ordinary custom files retain their earlier filename-sorted, last-valid-definition behavior. Existing ordinary files with IDs beginning `__default_` remain ignored; create a custom copy with a new ID instead.

Reload client resources after changing packs or files. A failed resource reload retains the last successfully published definition set and marks the sources stale. If no successful set exists, the failed set is not published. Source problems remain visible in the manager. Fix the reported file and reload; exports reject stale sources.

## Managing groups

The source badge distinguishes built-in, resource-pack, and KubeJS groups from ordinary custom groups. Provenance comes from the loaded source, rather than an ID prefix.

For a built-in, resource-pack, or KubeJS group, **Copy as custom** opens an editable draft with a new ID and preserves the original JSON format. Nothing is written until Save. The optional original-group disable runs only after the copy has been saved. If disabling fails, the saved copy remains available and the manager reports the incomplete action.

The unpublished local-definition override feature has been removed. Files under `config/collapsiblegroups/overrides` are ignored and left untouched. Individual enabled preferences in `enabled_overrides.json` remain supported.

Saving or removing a file checks its ownership, directory, and ID. Saving also rejects an unsupported document version. A file with a different ID or a path outside its source directory is left untouched; a failed removal is reported in the manager.

Use **Sort and filter** beside the search box for the existing sort modes and **Show empty groups**. Empty groups are hidden by default. This preference and the sort mode survive reopening the manager. The footer reports the number hidden by the current search and source selection; clicking the notice shows those groups. A newly saved empty group has a notice that can reveal it even if the current search would hide it.

“Empty” means a complete evaluation found zero items, fluids, and other supported ingredient types. One match is nonempty even though the viewer needs at least two visible ingredients to draw a collapsible header. Counts include disabled groups and ingredients owned by higher-priority groups, and do not depend on the viewer's search text. Pending evaluations, invalid rules, unavailable types or codecs, and incomplete tag support remain visible with a status. Hiding empty groups removes them from the current batch selection.

## Translation worklists

UI translations remain in `assets/collapsible_groups/lang/<locale>.json`. Built-in English group names are generated into `assets/collapsible_groups/group_lang/en_us.json`; manual group translations use `group_lang/<locale>.json`. Empty locale files are not generated.

Both directories participate in Minecraft language loading. Pack priority applies across both files; within one pack, `group_lang` takes precedence over `lang`. A higher-priority pack's standard `lang` entry can replace a lower-priority pack's `group_lang` entry. Minecraft's English fallback and selected locale order remain in effect. Malformed group language files are skipped individually during client language reload.

Resource packs may use either normal language files or the dedicated group files. Explicit files in `config/collapsiblegroups/lang/<locale>.json` retain their existing highest precedence for group names.

The mod no longer copies bundled language files into the config directory. Existing local files are preserved, so a file copied by an earlier release may continue to mask resource-pack translations. Review those files manually when changing translation sources.

Enter a world and wait for the viewer's group index to finish, then run:

```text
/cg group_key worklist zh_tw
/cg group_key worklist zh_tw all
/cg group_key worklist zh_tw missing
```

The default is `all`. Every command creates a fresh directory below `config/collapsiblegroups/translation-work/` containing `<locale>.json` and `report.json`. It does not modify a live translation file or overwrite a previous worklist.

Both modes use all effective source groups with complete, nonzero content, including disabled groups. The manager's search, source filter, empty-group preference, ownership, and global built-in switch do not narrow the export.

| Mode | Output values |
| --- | --- |
| `all` | Preserve existing target-locale values; use the group's fallback where a target entry is absent. |
| `missing` | Include only keys absent from the requested locale, using each group's fallback. |

The command reads only the requested locale from both language directories in the same pack order, followed by its local overlay. It does not use the current language or English fallback to decide whether a target entry exists. An existing value identical to English is still an existing entry. A missing locale file is an empty input; an unreadable or malformed locale file is an error.

The report records the Minecraft version, viewer, evaluation generation, source locations, content counts, omitted empty groups, and uncertain groups. Built-in fallbacks are labeled `en_us`; custom, pack, override, and script fallbacks have an unspecified language. Equal fallbacks sharing a key are deduplicated. Different fallbacks sharing a key are omitted and reported as a conflict, even when a target translation already exists.

An incomplete report is explicitly identified in command feedback. Check its uncertain groups, source errors, and key conflicts before using it. Pending or mixed evaluations, stale sources, or a source change during export require a retry. “Missing” describes absent entries, not translation quality.

The existing `group_key` dump and `clean` commands retain their previous behavior. Use `worklist` for the separate nonempty, target-locale workflow above.

## Maintaining bundled definitions

Bundled source JSON lives in three packaging roots:

| Directory | Packaged by | Definitions |
| --- | --- | ---: |
| `builtin-groups/common` | Fabric, Forge, NeoForge | 229 |
| `builtin-groups/fabric-neoforge` | Fabric, NeoForge | 299 |
| `builtin-groups/neoforge` | NeoForge | 367 |

Keep each resource path unique across these roots. Filename prefixes preserve the original provider ordering; they do not alter a group's numeric priority. Built-in names must have a `collapsible_groups.group.*` translation key and a nonempty English fallback. No Java provider registration or per-group config option is required.

Normal resource processing and source-jar builds automatically generate the platform catalog and the separate group English file from JSON fallbacks. Generation reads JSON directly, without launching Minecraft, a datagen client, or installed integration mods. Commit the edited source definitions; generated output lives below `build/generated/` and is included in each loader's resources and source jar.

The language generator never rewrites manual UI files. Conflicting fallbacks, duplicate IDs or resource paths, missing names, and collisions with manual UI keys fail generation before replacing its output. Identical input produces identical output without timestamps. There is no ownership manifest, writer lock, two-file rollback, or checked-in generated language verification task.
