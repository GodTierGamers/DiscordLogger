#!/usr/bin/env python3
"""Re-freeze the shipped user-facing strings as approved.

ApprovedStringsTest holds every string in lang.yml at the wording that was last
reviewed, because whether a sentence is correct is a human judgement and not
something a rule can decide. When that test fails it means a shipped string moved.

Run this only after reading the diff the test printed and agreeing with it. It
does not check anything: it records that someone did.

    python3 scripts/approve-strings.py

Commit the updated baseline together with the change that moved the wording, so
the two are never separated in history.
"""
import hashlib
import json
import pathlib
import sys

LANG = pathlib.Path("src/main/resources/lang.yml")
CONFIG = pathlib.Path("src/main/resources/config.yml")
BASELINE = pathlib.Path("src/test/resources/baseline/approved-strings.json")

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is needed: pip3 install pyyaml")


def flatten(node, path=""):
    out = {}
    if isinstance(node, dict):
        for key, value in node.items():
            out.update(flatten(value, f"{path}.{key}" if path else key))
    elif isinstance(node, str):
        out[path] = node
    return out


def main() -> int:
    if not LANG.is_file():
        return print(f"{LANG} not found -- run from the repository root") or 1

    lang = yaml.safe_load(LANG.read_text())
    config = yaml.safe_load(CONFIG.read_text())
    strings = flatten(lang)
    strings.pop("config-version", None)

    previous = {}
    if BASELINE.is_file():
        old = json.loads(BASELINE.read_text())
        previous = {**old.get("discord", {}), **old.get("chat", {})}

    changed = [k for k, v in strings.items() if k in previous and previous[k] != v]
    added = [k for k in strings if k not in previous]
    removed = [k for k in previous if k not in strings]

    discord = {k: v for k, v in sorted(strings.items()) if k.startswith("discord.")}
    chat = {k: v for k, v in sorted(strings.items()) if k.startswith("chat.")}

    BASELINE.parent.mkdir(parents=True, exist_ok=True)
    BASELINE.write_text(json.dumps({
        "_comment": [
            "Approved output strings, frozen at the moment they were reviewed.",
            "",
            "A SNAPSHOT, not a view of lang.yml. Reading expectations from lang.yml at",
            "test time would compare the plugin against its own source and pass whatever",
            "it happened to say, including a typo. Frozen here, any change to a shipped",
            "string shows up as drift and has to be approved again.",
            "",
            "Regenerate with scripts/approve-strings.py, only after reading the diff.",
        ],
        "approved_schema": config.get("config-version"),
        "lang_sha256": hashlib.sha256(LANG.read_bytes()).hexdigest(),
        "counts": {
            "discord_strings": len(discord),
            "chat_strings": len(chat),
            "death_causes": len(lang["discord"]["death"]["causes"]),
        },
        "discord": discord,
        "chat": chat,
    }, indent=2, ensure_ascii=False) + "\n")

    print(f"Approved {len(discord)} discord and {len(chat)} chat strings.")
    for label, keys in (("changed", changed), ("added", added), ("removed", removed)):
        if keys:
            print(f"  {label}: {len(keys)}")
            for k in keys[:10]:
                print(f"    {k}")
            if len(keys) > 10:
                print(f"    ... {len(keys) - 10} more")
    if not (changed or added or removed):
        print("  nothing moved; the baseline was already current.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
