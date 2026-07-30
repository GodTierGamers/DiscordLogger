#!/usr/bin/env python3
"""Cross-checks the website config-generator data for each schema version:

1. Every configKey/colorKey referenced in options.json has a matching
   {{TOKEN}} placeholder in that version's config.template.yml (and vice
   versa isn't required -- unused tokens in the template aren't an error).
2. Every "log.*" configKey referenced in options.json is actually read
   somewhere in the Java plugin source. This is the check that would have
   caught options.json advertising "log.moderation.whitelist" while the
   plugin only ever reads "log.moderation.whitelist_edit".
3. The plugin's shipped default (src/main/resources/config.yml) matches
   its website download-mirror copy (docs/assets/configs/v<N>/config.yml,
   for whichever schema version is currently shipping) line-for-line,
   except the trailer line (which legitimately differs -- one says "SHIPPED
   WITH vX.Y.Z", the other says "DOWNLOADED FROM WEBSITE"). This is the
   check that would have caught the shipped config still saying "(2.1.5)"
   in its generator hint after the website copy had already been reworded
   -- see the "Config file dictionary" in AGENTS.md for what each file is.

Exits non-zero (and prints one ERROR line per problem) if anything's wrong.
"""
import glob
import json
import os
import re
import subprocess
import sys

JAVA_SRC = "src/main/java"
SHIPPED_CONFIG = "src/main/resources/config.yml"
VERSION_RE = re.compile(r"CONFIG\s+VERSION\s+V(\d+)", re.IGNORECASE)
IDENTITY_START = "# ---DL_FILE_IDENTITY_START---"
IDENTITY_END = "# ---DL_FILE_IDENTITY_END---"


def strip_identity_block(lines: list[str]) -> list[str]:
    """Removes the per-file '[N of 3 ...]' header block (each file's identity
    comment legitimately differs -- only the content below it must match)."""
    if IDENTITY_START not in lines:
        return lines
    start = lines.index(IDENTITY_START)
    if IDENTITY_END not in lines:
        return lines
    end = lines.index(IDENTITY_END, start)
    return lines[:start] + lines[end + 1:]


def check_version(options_path: str) -> list[str]:
    errors = []
    version_dir = os.path.dirname(options_path)
    template_path = os.path.join(version_dir, "config.template.yml")

    if not os.path.exists(template_path):
        return [f"{template_path} missing (required by {options_path})"]

    with open(options_path, encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError as e:
            return [f"{options_path} is not valid JSON: {e}"]

    with open(template_path, encoding="utf-8") as f:
        template_tokens = set(re.findall(r"\{\{([^}]+)\}\}", f.read()))

    for cat in data.get("categories", []):
        for item in cat.get("items", []):
            item_id = item.get("id", "?")

            config_key = item.get("configKey", "")
            if config_key.startswith("log."):
                token = "LOG_" + config_key[len("log."):]
                if token not in template_tokens:
                    errors.append(
                        f"{options_path}: item '{item_id}' expects "
                        f"{{{{{token}}}}} in {template_path}, not found"
                    )

                # Match the fully-quoted literal ("log.moderation.whitelist") so
                # e.g. "...whitelist" doesn't false-positive against the Java
                # source actually reading "...whitelist_edit" (substring match).
                grep = subprocess.run(
                    ["grep", "-rl", "-F", f'"{config_key}"', JAVA_SRC],
                    capture_output=True, text=True,
                )
                if not grep.stdout.strip():
                    errors.append(
                        f"{options_path}: item '{item_id}' references config key "
                        f"'{config_key}', which no Java source under {JAVA_SRC} reads"
                    )

            color_key = item.get("colorKey")
            if color_key:
                token = "COLOR_" + color_key
                if token not in template_tokens:
                    errors.append(
                        f"{options_path}: item '{item_id}' expects "
                        f"{{{{{token}}}}} in {template_path}, not found"
                    )

    return errors


def check_shipped_config_matches_mirror() -> list[str]:
    if not os.path.exists(SHIPPED_CONFIG):
        return [f"{SHIPPED_CONFIG} not found"]

    with open(SHIPPED_CONFIG, encoding="utf-8") as f:
        shipped_lines = f.read().splitlines()

    trailer = shipped_lines[-1] if shipped_lines else ""
    m = VERSION_RE.search(trailer)
    if not m:
        return [f"{SHIPPED_CONFIG}: last line doesn't match 'CONFIG VERSION V<n>' -- can't find its website mirror"]

    schema = f"v{m.group(1)}"
    mirror_path = f"docs/assets/configs/{schema}/config.yml"
    if not os.path.exists(mirror_path):
        return [f"{SHIPPED_CONFIG} is schema {schema} but {mirror_path} doesn't exist"]

    with open(mirror_path, encoding="utf-8") as f:
        mirror_lines = f.read().splitlines()

    # Compare everything except each file's own trailer line (last line) and its
    # per-file identity header -- those are the two things SUPPOSED to differ.
    shipped_body = strip_identity_block(shipped_lines[:-1])
    mirror_body = strip_identity_block(mirror_lines[:-1])

    if shipped_body != mirror_body:
        return [
            f"{SHIPPED_CONFIG} and {mirror_path} have drifted apart. "
            f"They must be identical except for their last line (the CONFIG VERSION trailer) -- "
            f"run: diff <(sed '$d' {SHIPPED_CONFIG}) <(sed '$d' {mirror_path})"
        ]
    return []


def main() -> int:
    all_errors = []
    for options_path in sorted(glob.glob("docs/assets/configs/v*/options.json")):
        all_errors.extend(check_version(options_path))
    all_errors.extend(check_shipped_config_matches_mirror())

    if all_errors:
        for e in all_errors:
            print(f"ERROR: {e}")
        print(f"\n{len(all_errors)} problem(s) found in config generator data.")
        return 1

    print("Config generator data OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
