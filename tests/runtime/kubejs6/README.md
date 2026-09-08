# KubeJS 6 client runtime checks

These client scripts exercise Collapsible Groups against a real Minecraft 1.20.1 loader and KubeJS 6 runtime. Run the same sequence on Fabric and Forge.

Copy `01-groups-v1.js` to `kubejs/client_scripts/cg_runtime_groups.js`, start the client, enter a world, and open the inventory once so the viewer performs its normal lazy bootstrap. Confirm the `phase=01-native-compile result=PASS` line during script loading, then the publication `state=ARMED` line followed by `phase=01-publication result=PASS`. The script directly checks native KubeJS values, exact matching, rejected custom ingredients, legal-peer publication, and a synchronous successful-post then failed-post transaction. Every phase's 400-client-tick deadline starts only after an inventory container screen has opened, and a timeout reports once rather than silently skipping or repeatedly logging the same failure.

While the client remains open, replace the active file in order, press `F3+T` after each replacement, and open the inventory again. For every phase, retain the `Reloading ResourceManager` log line as proof that the normal resource-reload hook ran before the phase PASS line:

1. `02-groups-reload-omit-replace.js`
2. `03-groups-readd.js`
3. `04-groups-intentional-throw.js`
4. `03-groups-readd.js` again to finish in a successful state

Every phase must emit its expected `[CG-P6-AUDIT] ... result=PASS` line. Any `assertion failed` error, missing PASS line, or timeout is a failure. Phase 04 intentionally produces `cg-p6-intentional-groups-post-failure`; it passes only when its separate audit line confirms that no partial or unreachable group was published and the failed publication was not marked applied.

The runtime matrix covers:

- direct tagged and untagged item stacks lowering to exact-stack semantics while a vanilla ingredient retains item-ID semantics;
- portable exact-stack group publication on both loaders;
- Fabric native strict-NBT lowering with a full tag and strict absence of NBT;
- Forge native strict-NBT rejection where its share-tag, damage, and capability semantics cannot be represented losslessly by CG's exact filter;
- weak NBT, arbitrary predicates, and a custom predicate wrapped around a strict-NBT base being rejected;
- unsupported native ingredients being rejected without discarding a legal peer in the same source;
- boolean item filters and vanilla fluid filters;
- direct post failure preserving the previous publication atomically;
- reload invalidation, omitted-source removal, source replacement, failed recollection, and source re-add recovery.

Fabric's loader-native automated test separately covers a strict-NBT wrapper whose nested base is a custom ingredient. KubeJS 6 does not expose a portable client-script constructor for that internal nesting shape on both loaders, so the scripts do not label the inverse custom-over-strict shape as that case. Forge rejects its native strict-NBT wrapper before serialization and directs scripts to pass an explicit `ItemStack` or use `source.exact(...)` when complete-stack exact semantics are intended.
