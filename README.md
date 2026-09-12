# Collapsible Groups

Collapsible Groups adds collapsible ingredient groups to JEI and EMI. This branch targets Minecraft 1.20.1 on Forge and Fabric with Java 17. Players and modpack developers can define groups through the in-game editor, JSON configuration, KubeJS, client resource packs, or bundled JSON definitions.

## Overview

This version supports exact item selections, complete NBT conditions and NBT path conditions using Minecraft 1.20.1's native data types. The rule editor can take values from a sample item or accept SNBT directly. Minecraft 1.21 component rules and exact-item formats remain preserved as unavailable data; they are not converted automatically.

KubeJS 6 integration uses the client-side `CGEvents.groups` event on both loaders. KubeJS is optional. The Minecraft 1.21.1 implementation remains on its separate version branch.

See [the 1.20.1 version notes](docs/minecraft-1.20.1.md) for the dependency baseline, item-data boundaries and a KubeJS example.

## Group JSON

See [the group JSON format](docs/group-json-format.md) for version 1 NBT payloads, exact items, and legacy editing behavior.

Built-in and resource-pack groups support local overrides. The manager hides confirmed empty groups by default, and `/cg group_key worklist <locale> [all|missing]` creates a translation worklist for the current environment. See [built-in groups and translations](docs/builtin-groups-and-translations.md) and [implementation and verification](docs/builtin-groups-verification.md).
