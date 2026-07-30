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
4. Any docs/config/v<N>/index.md page that embeds the full config in a
   "Full config.yml" code block matches that same mirror copy too. This is
   a 4th place the same content lives -- easy to forget, and it HAD already
   drifted (missing an entire banner block) before this check existed.
5. Every getConfig().getBoolean("log.*", <fallback>) in the Java source uses
   the SAME fallback as the value config.yml ships. These only diverge when a
   user's config is missing the key (hand-edited, partial copy, pre-dating the
   key), and a mismatch silently disables logging the docs promise is on --
   which is exactly what happened to teleport/gamemode/explosion.

Exits non-zero (and prints one ERROR line per problem) if anything's wrong.
"""
from __future__ import annotations

import glob
import json
import os
import re
import subprocess
import sys

JAVA_SRC = "src/main/java"
SHIPPED_CONFIG = "src/main/resources/config.yml"
DOC_PAGE_GLOB = "docs/config/v*/index.md"
VERSION_RE = re.compile(r"CONFIG\s+VERSION\s+V(\d+)", re.IGNORECASE)
FULL_CONFIG_HEADING_RE = re.compile(r"^#+\s*Full config\.yml", re.IGNORECASE | re.MULTILINE)



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

    # Compare everything except each file's own trailer line (last line) --
    # that's the one line that's SUPPOSED to differ between the two.
    shipped_body = shipped_lines[:-1]
    mirror_body = mirror_lines[:-1]

    if shipped_body != mirror_body:
        return [
            f"{SHIPPED_CONFIG} and {mirror_path} have drifted apart. "
            f"They must be identical except for their last line (the CONFIG VERSION trailer) -- "
            f"run: diff <(sed '$d' {SHIPPED_CONFIG}) <(sed '$d' {mirror_path})"
        ]
    return []


def extract_full_config_block(md_text: str) -> list[str] | None:
    """Finds the fenced ```yaml block that follows a '## Full config.yml' heading
    (any heading level). Returns None if the page doesn't have one -- not every
    doc page is required to embed a full copy."""
    heading = FULL_CONFIG_HEADING_RE.search(md_text)
    if not heading:
        return None
    fence = re.search(r"```yaml\r?\n(.*?)```", md_text[heading.end():], re.DOTALL)
    if not fence:
        return None
    lines = fence.group(1).splitlines()
    while lines and lines[-1].strip() == "":
        lines.pop()
    return lines


def check_doc_page_embedded_configs() -> list[str]:
    errors = []
    for doc_path in sorted(glob.glob(DOC_PAGE_GLOB)):
        schema = os.path.basename(os.path.dirname(doc_path))  # "docs/config/v9/index.md" -> "v9"
        mirror_path = f"docs/assets/configs/{schema}/config.yml"
        if not os.path.exists(mirror_path):
            continue  # no matching schema folder -- separate problem, not this check's job

        with open(doc_path, encoding="utf-8") as f:
            embedded = extract_full_config_block(f.read())
        if embedded is None:
            continue  # this page doesn't embed a full config -- nothing to check

        with open(mirror_path, encoding="utf-8") as f:
            mirror_lines = f.read().splitlines()

        # Drop each side's own trailer line before comparing -- same rule as
        # check_shipped_config_matches_mirror.
        mirror_body = mirror_lines[:-1]
        embedded_body = embedded[:-1]

        if mirror_body != embedded_body:
            errors.append(
                f"{doc_path}'s embedded \"Full config.yml\" block has drifted from "
                f"{mirror_path}. They must be identical except the trailer line."
            )
    return errors


def check_java_fallbacks_match_shipped_config() -> list[str]:
    """Java's getBoolean fallback must equal what config.yml ships for that key.

    The fallback only applies when a user's config is missing the key, so a
    mismatch is invisible in normal use and silently contradicts the docs --
    e.g. config.yml shipping `teleport: true` while the listener defaulted to
    false, so a partial config meant teleport logging never fired.
    """
    if not os.path.exists(SHIPPED_CONFIG):
        return [f"{SHIPPED_CONFIG} not found"]

    # what config.yml ships: log.<category>.<event> -> true/false
    shipped: dict[str, str] = {}
    with open(SHIPPED_CONFIG, encoding="utf-8") as f:
        text = f.read()
    if "\nlog:" in text:
        category = None
        for line in text.split("\nlog:", 1)[1].split("\n"):
            cat_m = re.match(r"^  (\w+):\s*(?:#.*)?$", line)
            if cat_m:
                category = cat_m.group(1)
                continue
            leaf_m = re.match(r"^    (\w+):\s*(true|false)\b", line)
            if leaf_m and category:
                shipped[f"log.{category}.{leaf_m.group(1)}"] = leaf_m.group(2)

    if not shipped:
        return [f"{SHIPPED_CONFIG}: could not parse any log.* toggles -- has the structure changed?"]

    errors = []
    pattern = re.compile(r'getBoolean\(\s*"(log\.[a-z._]+)"\s*,\s*(true|false)\s*\)')
    for java_file in glob.glob(f"{JAVA_SRC}/**/*.java", recursive=True):
        with open(java_file, encoding="utf-8") as f:
            for lineno, line in enumerate(f, 1):
                for key, fallback in pattern.findall(line):
                    want = shipped.get(key)
                    if want is None:
                        errors.append(
                            f"{java_file}:{lineno} reads '{key}', which {SHIPPED_CONFIG} doesn't ship"
                        )
                    elif want != fallback:
                        errors.append(
                            f"{java_file}:{lineno} defaults '{key}' to {fallback}, but "
                            f"{SHIPPED_CONFIG} ships {want} -- they must match, or a config "
                            f"missing this key silently behaves against the documented default"
                        )
    return errors


def main() -> int:
    all_errors = []
    for options_path in sorted(glob.glob("docs/assets/configs/v*/options.json")):
        all_errors.extend(check_version(options_path))
    all_errors.extend(check_shipped_config_matches_mirror())
    all_errors.extend(check_doc_page_embedded_configs())
    all_errors.extend(check_java_fallbacks_match_shipped_config())

    if all_errors:
        for e in all_errors:
            print(f"ERROR: {e}")
        print(f"\n{len(all_errors)} problem(s) found in config generator data.")
        return 1

    print("Config generator data OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
