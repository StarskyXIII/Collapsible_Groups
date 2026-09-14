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

Pending; starts after the first stage is committed.
