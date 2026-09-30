# ClientDevBridge tests for Minecraft 26.1.2

Run Collapsible Groups in isolated Prism Launcher instances and check its UI, saved groups, and behavior after restarting the game. ClientDevBridge and the companion probe drive the tests; neither is included in the mod's release JARs.

Start with the two core JEI profiles.

## Before you start

You need Windows, Node.js 22 or newer, JDK 25, and Prism Launcher with a signed-in account that can launch Minecraft. No npm packages need to be installed.

The setup uses these versions. Third-party mod downloads and hashes are pinned in [dependencies.json](dependencies.json):

| Component | Version |
| --- | --- |
| Minecraft / Collapsible Groups | 26.1.2 / 2.0.0-beta3 |
| JEI | 29.43.0.106 |
| NeoForge | 26.1.2.99 |
| Fabric Loader / Fabric API | 0.19.5 / 0.155.3+26.1.2 |
| ClientDevBridge | 1.0.0, build 172 |

## 1. Configure your local paths

Open PowerShell at the repository root, then enter this directory:

```powershell
Set-Location test-tools/clientdevbridge
$env:JAVA_HOME = 'C:/Path/To/jdk-25'
```

**Run all remaining commands from this directory.** Run the steps in order and stop if a command fails.

Copy [local.example.json](local.example.json) to `local.json` on first setup. If `local.json` already exists, edit it instead of replacing it. Set these four paths:

| Field | What to enter |
| --- | --- |
| `java` | The full path to JDK 25's `bin/javaw.exe`. |
| `prism` | The full path to `prismlauncher.exe`. |
| `prismData` | Your Prism data directory, which contains `instances`. |
| `packTemplate` | The full path to this directory's [pack-template.json](pack-template.json). |

## 2. Build the mod and test probes

```powershell
../../gradlew.bat -p ../.. :common:check :neoforge:build :fabric:build
node runner/fetch.mjs neo-jei fabric-jei
node runner/stage.mjs neo-jei fabric-jei
../../gradlew.bat -p companion build
../../gradlew.bat -p companion-fabric build
```

`fetch` downloads the pinned dependencies and checks their SHA-256 hashes. `stage` verifies them again and copies the mod JARs into the probe build directories. The last two commands build the NeoForge and Fabric probes.

To use existing dependency JARs, set `dependencyFiles` in `local.json` to a map of dependency IDs to file paths, then run `stage` instead of `fetch`. The files must match the hashes in `dependencies.json`.

## 3. Create the test instances

```powershell
node runner/prepare.mjs neo-jei fabric-jei
```

This creates separate Prism instances and saves their details under `profiles/`. It does not copy your personal worlds or settings. Preparation stops if an instance with the same name already exists or its port is in use.

| Profile | Loader | Port | Checks per run |
| --- | --- | --- | --- |
| `neo-jei` | NeoForge | 25931 | 46 game checks and 3 image comparisons |
| `fabric-jei` | Fabric | 25932 | 46 game checks and 3 image comparisons |

Use `prepare` once per profile. To update an existing instance, follow **Rebuild and rerun** below.

## 4. Run the core tests

```powershell
node --test runner/*.test.mjs
node runner/run.mjs neo-jei fabric-jei
node runner/audit-evidence.mjs neo-jei fabric-jei
```

The first command checks the runner without launching Minecraft. The second runs each game profile in sequence, including a cold restart. Keep the active game window in focus and avoid using the mouse or keyboard during the run.

The instances use 1280 × 800, GUI scale 2, a 60 FPS limit, and English (`en_us`). Keep these settings for image comparisons.

A successful run reports `passed`. The audit then checks the latest recorded run for each profile: all checks must pass, the deployed JARs must match, the clients must have exited, and the original configuration must have been restored. It writes a summary to `evidence/final-verification.json`.

Each run keeps its reports, screenshots, logs, and configuration backups in `evidence/suite-<timestamp>/`. Core checks cover group editing, item and fluid membership, search, drag input, batch actions, categories, invalid rules, and persistence. Image comparisons cover selected controls and text regions, not the whole screen; changes to the baseline images require manual review.

## Rebuild and rerun

When mod or probe code changes, close the test instances, repeat step 2, and deploy the rebuilt JARs to the profiles you want to test:

```powershell
node runner/deploy.mjs neo-jei fabric-jei
node runner/run.mjs neo-jei fabric-jei
node runner/audit-evidence.mjs neo-jei fabric-jei
```

`deploy` updates the mod and probe JARs and records their new hashes; it does not update loader or third-party dependency versions.

For a loader or dependency version change, finish any pending recovery, remove only the generated test instance in Prism, and repeat setup for that profile. If the mod version changes, also update the probe dependency metadata before rebuilding and preparing the replacement instance.

## If something fails

Read `result.json` and the logs in the failed profile's evidence directory first. An audit rejects a failed latest run even if an older run passed, and rejects evidence from different deployed JARs.

**A client is still running:** after the runner has stopped, use `node runner/quit.mjs <profile>` if the Bridge connection still works. The helper checks the instance identity and exits through the game's Quit button.

**Core configuration was not restored:** once the client has exited, run the following with the failed core profile's evidence directory:

```powershell
node runner/recover.mjs "evidence/<suite>/<profile>"
```

This helper requires a previously verified client identity, all recorded client processes to have exited, and a closed port.

**Startup failed before identity verification:** inspect the instance's process and port before restoring files manually. Keep the backups until recovery is complete.

**Gradle reports `Unable to establish loopback connection`:** set this workaround in the same PowerShell session and retry the failed build command:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/cg-bridge-no-socket'
```

The path must not exist; do not create the directory.

## Files and scope

Keep `local.json`, generated profiles, downloaded JARs, evidence, and internal test reports local. Their paths are ignored by Git. Test source, example configuration, dependency pins, and reviewed baseline images belong in the repository.

These profiles cover local single-player JEI testing on Fabric and NeoForge. They do not cover EMI, multiplayer, other resolutions, or performance measurements. ClientDevBridge source and artifact versions are recorded in [dependencies.json](dependencies.json).
