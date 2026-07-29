#!/usr/bin/env python3
"""Cross-checks the website config-generator data for each schema version:

1. Every configKey/colorKey referenced in options.json has a matching
   {{TOKEN}} placeholder in that version's config.template.yml (and vice
   versa isn't required -- unused tokens in the template aren't an error).
2. Every "log.*" configKey referenced in options.json is actually read
   somewhere in the Java plugin source. This is the check that would have
   caught options.json advertising "log.moderation.whitelist" while the
   plugin only ever reads "log.moderation.whitelist_edit".

Exits non-zero (and prints one ERROR line per problem) if anything's wrong.
"""
import glob
import json
import os
import re
import subprocess
import sys

JAVA_SRC = "src/main/java"


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


def main() -> int:
    all_errors = []
    for options_path in sorted(glob.glob("docs/assets/configs/v*/options.json")):
        all_errors.extend(check_version(options_path))

    if all_errors:
        for e in all_errors:
            print(f"ERROR: {e}")
        print(f"\n{len(all_errors)} problem(s) found in config generator data.")
        return 1

    print("Config generator data OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
