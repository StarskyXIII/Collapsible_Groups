# Collapsible Groups

Collapsible Groups is an ingredient grouping mod for Minecraft 1.21.1. It lets players, modpack developers, and modders define collapsible item, fluid, and other ingredient type groups through an in-game editor, JSON config files, client resource packs, KubeJS, or bundled JSON definitions.

## Overview

This mod supports JEI, with EMI support on Fabric and NeoForge. Built-in group coverage remains specific to each loader.

The rule editor supports item component and component-path conditions, including values selected from a sample item. Canceling a rule edit restores the prior rule tree; unconfirmed edits cannot be saved. Minecraft 1.20.1 NBT rules are preserved as unavailable data without automatic conversion.

New blank groups use versioned JSON with typed component values; existing groups keep their original format when edited or copied. See [group JSON format](docs/group-json-format.md), [editor behavior and verification](docs/editor-verification.md) and [EMI compatibility](docs/emi-compatibility.md) for the tested scope and remaining limitations.

Built-in and resource-pack groups support local overrides. The manager can hide confirmed empty groups, and `/cg group_key worklist <locale> [all|missing]` creates a translation worklist for the current environment. See [built-in groups, resource packs, and translations](docs/builtin-groups-and-translations.md) and the [implementation and verification record](docs/builtin-groups-verification.md).
