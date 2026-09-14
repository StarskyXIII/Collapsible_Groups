import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tomllib
from zipfile import ZipFile


LOADERS = {
    "fabric": "fabric.mod.json",
    "forge": "META-INF/mods.toml",
}
RELEASE_TYPES = ("release", "beta", "alpha")


def release_metadata(root, release_type):
    if release_type not in RELEASE_TYPES:
        raise ValueError("Release type must be release, beta, or alpha.")
    properties = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()

    version = properties["version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)[.-]?\d+)?", version):
        raise ValueError("Version must be X.Y.Z, X.Y.Z-alphaN, X.Y.Z-betaN, or X.Y.Z-rcN.")
    for key, pattern in {
        "minecraft_version": r"\d+\.\d+(?:\.\d+)?",
        "java_version": r"\d+",
        "mod_id": r"[a-z][a-z0-9_]*",
    }.items():
        if not re.fullmatch(pattern, properties[key]):
            raise ValueError(f"Invalid {key} in gradle.properties.")

    minecraft = properties["minecraft_version"]
    changelog = Path("changelogs") / minecraft / f"{version}.md"
    content = (root / changelog).read_text(encoding="utf-8")
    if not any(line.strip() and not line.lstrip().startswith("#") for line in content.splitlines()):
        raise ValueError(f"Add release notes to {changelog.as_posix()} before running the workflow.")
    return {
        "version": version,
        "minecraft": minecraft,
        "java": properties["java_version"],
        "mod-id": properties["mod_id"],
        "mod-name": properties["mod_name"],
        "release-type": release_type,
        "changelog": changelog.as_posix(),
    }


def stage_release(root, metadata):
    jars = []
    for loader, manifest_path in LOADERS.items():
        name = f"{metadata['mod-id']}-{loader}-{metadata['minecraft']}-{metadata['version']}.jar"
        jar = root / loader / "build" / "libs" / name
        with ZipFile(jar) as archive:
            manifest = archive.read(manifest_path).decode("utf-8")
            if loader == "fabric":
                mod = json.loads(manifest)
                mod_id, version = mod["id"], mod["version"]
            else:
                mods = tomllib.loads(manifest)["mods"]
                if len(mods) != 1:
                    raise ValueError(f"Expected one mod in {name}.")
                mod_id, version = mods[0]["modId"], mods[0]["version"]
            if mod_id != metadata["mod-id"] or version != metadata["version"]:
                raise ValueError(f"Packaged mod ID or version does not match gradle.properties: {name}")
        jars.append(jar)

    destination = root / "build" / "curseforge-release"
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / metadata["changelog"], destination / "changelog.md")
    for jar in jars:
        shutil.copyfile(jar, destination / jar.name)
    files = [destination / "changelog.md", *(destination / jar.name for jar in jars)]
    manifest = {
        **metadata,
        "commit": os.environ.get("GITHUB_SHA", "local"),
        "files": {file.name: hashlib.sha256(file.read_bytes()).hexdigest() for file in files},
    }
    (destination / "release.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"Version: {metadata['version']} ({metadata['release-type']})\n\n")
            stream.write(f"Minecraft: {metadata['minecraft']} · Commit: `{manifest['commit']}`\n\n")
            stream.write((destination / "changelog.md").read_text(encoding="utf-8") + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("metadata", "stage"))
    parser.add_argument("--release-type", choices=RELEASE_TYPES, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        metadata = release_metadata(root, args.release_type)
        if args.command == "stage":
            stage_release(root, metadata)
        else:
            print(json.dumps(metadata, indent=2))
            if output := os.environ.get("GITHUB_OUTPUT"):
                with open(output, "a", encoding="utf-8") as stream:
                    stream.writelines(f"{key}={value}\n" for key, value in metadata.items())
    except (OSError, KeyError, ValueError) as error:
        print(f"Release preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
