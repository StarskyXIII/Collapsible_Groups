# Collapsible Groups

Collapsible Groups adds collapsible ingredient groups to JEI and EMI. This branch targets Minecraft 1.20.1 on Forge and Fabric with Java 17. Players and modpack developers can define groups through the in-game editor, JSON configuration, KubeJS, or built-in providers.

## Overview

This version stores exact item selections using Minecraft 1.20.1 NBT. Minecraft 1.21 component rules and exact-item formats remain preserved as unavailable data; they are not converted automatically. A full NBT path editor is outside this branch's current scope.

KubeJS 6 integration uses the client-side `CGEvents.groups` event on both loaders. KubeJS is optional. The Minecraft 1.21.1 implementation remains on its separate version branch.

See [the 1.20.1 version notes](docs/minecraft-1.20.1.md) for the dependency baseline, item-data boundaries and a KubeJS example.
