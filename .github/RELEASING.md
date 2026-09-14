# Publishing to CurseForge

The manual **Build and publish to CurseForge** workflow builds Fabric, Forge,
and NeoForge for the Minecraft version in `gradle.properties`. All builds and
checks must succeed before any upload starts. It does not create GitHub releases
or tags.

## One-time setup

1. Add the workflow and its scripts to the repository's default branch so that
   GitHub shows **Run workflow**. The selected release branch must also contain
   these files, the code to build, and its changelog.
2. Under **Settings → Secrets and variables → Actions**, add repository secret
   `CURSEFORGE_TOKEN` with your CurseForge **author API token**, and repository
   variable `CURSEFORGE_PROJECT_ID` with the project's numeric ID. Generate the
   token from the author account's API tokens page; this is the Upload API token,
   not a CurseForge for Studios API key.
3. Run the workflow once with `mode: build-only` and inspect the downloadable
   `curseforge-release` artifact. This mode needs no CurseForge credentials.

## Each release

1. Set `version` in `gradle.properties` to the intended release version.
2. Write and review `changelogs/<minecraft_version>/<version>.md`. Commit and push
   the version, notes, and release code to the branch you will select.
3. In **Actions → Build and publish to CurseForge → Run workflow**, choose that
   branch and `mode: publish`.

`X.Y.Z` becomes a CurseForge release; `-alphaN` becomes alpha; `-betaN` and `-rcN`
become beta. A dot or hyphen before the prerelease number is also accepted.
Snapshot versions are rejected. The workflow uses the version committed in
`gradle.properties` and its matching changelog.

The workflow builds the commit selected when the run starts, checks the packaged
mod IDs and versions, and saves exactly three release JARs with the shared notes
and a manifest containing the source commit and SHA-256 hashes. Artifacts remain
available for 14 days. Only the upload action receives the CurseForge token.

Uploads are separate files with their own loader and Minecraft labels. Fabric
declares Fabric API as required. JEI is optional on all loaders; EMI is optional
on Fabric and NeoForge, matching the mod metadata. This does not require players
to install both viewers. Forge 1.21.1 is not labeled as EMI compatible.

## Changelog updates and retries

The Markdown is copied to CurseForge when uploading. Editing it afterward does
not update files already published. Correct an existing file's notes in the
CurseForge author dashboard; do not rerun a successful upload just to change text.

If a loader fails, inspect its log and CurseForge first. Uploads are sequential;
automatic retries of the whole publication are disabled. A timeout can leave an
uploaded file without a successful response. If the failed loader has no uploaded file, use **Re-run
failed jobs**, keeping the successful loaders intact. Do not use **Re-run all
jobs** on a partially published release. The workflow does not deduplicate
separate manual runs or remove files that were already uploaded.

The run summary links to each successful file. CurseForge moderation may delay
public availability after the API accepts an upload.

## Pinned actions

- `actions/checkout`: v7.0.1
- `actions/setup-java`: v6.0.1
- `gradle/actions/setup-gradle`: v6.3.0
- `actions/upload-artifact`: v7.0.1
- `actions/download-artifact`: v8.0.1
- `Kira-NT/mc-publish`: v3.3.1

References: [GitHub manual workflows](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow),
[CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api),
[mc-publish](https://github.com/Kira-NT/mc-publish/tree/v3.3.1).
