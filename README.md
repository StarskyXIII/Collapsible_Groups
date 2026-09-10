# Collapsible Groups

Collapsible Groups is a JEI grouping mod for Minecraft 1.21.1. It lets players, modpack developers, and modders define collapsible item, fluid, and other ingredient type groups through an in-game editor, JSON config files, KubeJS, or built-in providers.

## Overview

This mod provides JEI ingredient grouping for Minecraft 1.21.1, with the richest feature set on NeoForge and lighter-weight builds for Forge and Fabric.

The rule editor supports item component and component-path conditions, including values selected from a sample item. Canceling a rule edit restores the prior rule tree; unconfirmed edits cannot be saved. Minecraft 1.20.1 NBT rules are preserved as unavailable data without automatic conversion.

New blank groups use versioned JSON with typed component values; existing groups keep their original format when edited or copied. See [group JSON format](docs/group-json-format.md), [editor behavior and verification](docs/editor-verification.md) and [EMI compatibility](docs/emi-compatibility.md) for the tested scope and remaining limitations.
