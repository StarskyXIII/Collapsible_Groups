# Rule editor behavior and verification

The editor keeps one authoritative rule tree. Item and fluid selection controls derive their contents from that tree and preserve advanced conditions when selections change. Repeatedly adding an existing selection leaves the tree and its preview cache unchanged.

Rule edits use a transaction. Confirm commits the edited rule; Cancel restores the previous tree and selection. Changing tabs or rebuilding the screen cancels an unfinished rule edit. Save is blocked while a rule edit remains unconfirmed. Forms with three fields use the available screen height when the Rules panel is too short, keeping the fields clear of the action buttons.

Component conditions compare the complete encoded component value. Component-path conditions compare one encoded descendant. Numeric values retain JSON number semantics when serialized values are compared, so a codec-produced float such as food saturation `9.6` matches the value selected by the sample picker. Numbers and strings remain distinct, including nested values and non-finite codec output.

Minecraft 1.20.1 NBT nodes remain unavailable on this version. Their original saved representation is preserved for round trips; they are not interpreted as component conditions.

Internally, `EditorStateCore` owns the canonical rule draft and its transaction snapshot; Contents controls use a derived projection. `RuleDescriptor` centralizes node capabilities, field requirements and picker roles while the existing capability/UI contracts remain compatibility entry points. Runtime consumers use separate group, ingredient and presentation interfaces, with `EditorRuntimeAccess` retained as the aggregate contract and viewer selection still owned by the existing lifecycle coordinator.

## Automated verification

The September 9, 2026 build passed 878 tests: 862 common tests and 16 NeoForge tests, with no failures, errors or skips. The Fabric test task has no test sources. Full Fabric and NeoForge builds passed.

Coverage includes rule transaction rollback, no-op selection preservation, descriptor projections, runtime service boundaries, foreign NBT persistence, finite float component matching and rejection of number/string category changes during serialization fallback.

Verified build artifacts:

| Loader | SHA-256 |
| --- | --- |
| Fabric | `06B28C80F854153F9A17573FF1D5E748D10CF91DF1353C7D556CD6F19CB69114` |
| NeoForge | `D00579C51AFE8422BC6F71A11A7F9488CB9351A5311876F035199D02689F1F1D` |

## Native client verification

The September 9, 2026 follow-up used the Fabric artifact above with EMI 1.1.24 and Fabric API 0.116.17. Isolating the normal golden apple's complete food component produced one matching item, correcting the zero-result preview observed before the numeric comparison fix. The sample picker exposed `saturation = 9.6`, and the three-field component-path form kept all inputs and both action buttons inside the modal.

Both picker selection and manual entry of the component path and raw value `9.6` expanded the paired `Any` preview to four items. Saving and reopening preserved the two ordered conditions, their raw JSON values and the four-item preview, with the editor reporting no changes.

Settings verification retained priority 7 across resize, save and reopen. Invalid priority text reverted to the last valid value. A transient green name-color preview was canceled by full-screen reinitialization, returning to the original color; the final saved descriptor contained no theme override. The client exited normally. The transient green state was observed during the run; the retained native screenshot records its restored state.

These focused Fabric checks are separate from automated NeoForge coverage and the historical [EMI modpack validation](emi-compatibility.md).
