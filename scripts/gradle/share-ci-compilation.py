#!/usr/bin/env python3
"""Compile once and export new Gradle compilation entries for jobs in this run."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import tarfile


def cache_entries(directory):
    if not directory.exists():
        return set()
    return {
        path.name for path in directory.iterdir()
        if path.is_file() and re.fullmatch(r"[0-9a-f]{32}", path.name)
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("gradle_args", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    gradle_args = args.gradle_args
    if gradle_args[:1] == ["--"]:
        gradle_args = gradle_args[1:]
    root = Path(__file__).resolve().parents[2]
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "rspace-build-cache"
    before = cache_entries(cache)
    subprocess.run([
        str(root / "gradlew"), "compileJava", "compileTestJava",
        "--init-script", str(root / "scripts/gradle/compile-cache.init.gradle"),
        *gradle_args,
    ], cwd=root, check=True)
    added = cache_entries(cache) - before
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, "w") as archive:
        for name in sorted(added):
            archive.add(cache / name, arcname=name, recursive=False)
    print(f"Exported {len(added)} new compilation cache entries to {args.output}")


if __name__ == "__main__":
    main()
