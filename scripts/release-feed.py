"""Write a GitHub-compatible fallback feed for an already signed stable APK."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    match = re.fullmatch(r"CoveRecipe-(\d+\.\d+\.\d+)\.apk", args.apk.name)
    if not match:
        parser.error("Expected CoveRecipe-X.Y.Z.apk")

    digest = hashlib.sha256()
    with args.apk.open("rb") as apk:
        for chunk in iter(lambda: apk.read(1024 * 1024), b""):
            digest.update(chunk)
    release = {
        "tag_name": "v" + match.group(1), "draft": False, "prerelease": False,
        "assets": [{"name": args.apk.name, "size": args.apk.stat().st_size,
                    "state": "uploaded", "digest": "sha256:" + digest.hexdigest()}],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps([release], indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
