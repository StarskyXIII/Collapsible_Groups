from contextlib import redirect_stdout
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

from curseforge_release import LOADERS, release_metadata, stage_release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write_release()

    def write_release(self, version="2.0.0-beta1", notes="- 修正 JEI 相容性。\n"):
        (self.root / "gradle.properties").write_text(
            f"version={version}\nminecraft_version=1.21.1\njava_version=21\n"
            "mod_id=collapsible_groups\nmod_name=Collapsible Groups\n",
            encoding="utf-8",
        )
        changelog = self.root / "changelogs" / "1.21.1" / f"{version}.md"
        changelog.parent.mkdir(parents=True, exist_ok=True)
        changelog.write_text(notes, encoding="utf-8")

    def write_jars(self, wrong_loader=None):
        names = []
        for loader, manifest_path in LOADERS.items():
            name = f"collapsible_groups-{loader}-1.21.1-2.0.0-beta1.jar"
            jar = self.root / loader / "build" / "libs" / name
            jar.parent.mkdir(parents=True, exist_ok=True)
            version = "1.4.4" if loader == wrong_loader else "2.0.0-beta1"
            manifest = (
                json.dumps({"id": "collapsible_groups", "version": version})
                if loader == "fabric"
                else f'[[mods]]\nmodId="collapsible_groups"\nversion="{version}"\n'
            )
            with ZipFile(jar, "w") as archive:
                archive.writestr(manifest_path, manifest)
            (jar.parent / name.replace(".jar", "-sources.jar")).write_bytes(b"sources")
            names.append(name)
        return names

    def test_selected_release_type_is_independent_of_version(self):
        for version in ("2.0.0", "2.0.0-alpha1", "2.0.0-beta1", "2.0.0-rc.1"):
            self.write_release(version)
            for release_type in ("release", "beta", "alpha"):
                with self.subTest(version=version, release_type=release_type):
                    metadata = release_metadata(self.root, release_type)
                    self.assertEqual(release_type, metadata["release-type"])
                    self.assertEqual(version, metadata["version"])
                    self.assertEqual(f"changelogs/1.21.1/{version}.md", metadata["changelog"])

    def test_invalid_release_type_is_rejected(self):
        for release_type in ("", "auto", "stable", None):
            with self.subTest(release_type=release_type), self.assertRaises(ValueError):
                release_metadata(self.root, release_type)

    def test_snapshot_cannot_be_published_as_a_release(self):
        self.write_release("2.0.0-SNAPSHOT")
        with self.assertRaises(ValueError):
            release_metadata(self.root, "release")

    def test_missing_or_empty_changelog_stops_preparation(self):
        self.write_release(notes="# Release\n\n")
        with self.assertRaises(ValueError):
            release_metadata(self.root, "beta")
        (self.root / "changelogs/1.21.1/2.0.0-beta1.md").unlink()
        with self.assertRaises(FileNotFoundError):
            release_metadata(self.root, "beta")

    def test_bundle_preserves_notes_and_contains_only_loader_jars(self):
        names = self.write_jars()
        for release_type in ("release", "beta", "alpha"):
            with self.subTest(release_type=release_type):
                metadata = release_metadata(self.root, release_type)
                with redirect_stdout(io.StringIO()):
                    stage_release(self.root, metadata)
                destination = self.root / "build/curseforge-release"
                self.assertEqual(set(names), {file.name for file in destination.glob("*.jar")})
                self.assertEqual((self.root / metadata["changelog"]).read_bytes(),
                                 (destination / "changelog.md").read_bytes())
                manifest = json.loads((destination / "release.json").read_text(encoding="utf-8"))
                self.assertEqual(set(names) | {"changelog.md"}, set(manifest["files"]))
                self.assertEqual(release_type, manifest["release-type"])

    def test_mislabeled_jar_stops_the_entire_bundle(self):
        for loader in LOADERS:
            with self.subTest(loader=loader):
                self.write_jars(wrong_loader=loader)
                with self.assertRaises(ValueError):
                    stage_release(self.root, release_metadata(self.root, "beta"))
                self.assertFalse((self.root / "build/curseforge-release").exists())


if __name__ == "__main__":
    unittest.main()
