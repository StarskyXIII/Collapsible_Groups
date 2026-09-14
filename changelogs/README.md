# Release notes

Keep one Markdown file per Minecraft and mod version:

```text
changelogs/<minecraft_version>/<version>.md
```

Both values come from `gradle.properties`. For example, Minecraft `1.20.1` and
mod version `2.0.0-beta1` use `changelogs/1.20.1/2.0.0-beta1.md`.
Both loader uploads receive the same file, including any loader-specific
notes written inside it. Missing or empty notes stop the workflow.

Write player-facing changes, compatibility requirements, migration instructions,
and known issues. Internal plans and test records stay in the ignored local docs.
A file's presence does not mean that the version has been released.

See [publishing instructions](../.github/RELEASING.md).
