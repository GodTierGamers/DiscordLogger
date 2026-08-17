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
6. Every website copy under docs/ carries a trailer saying it came from the
   website, and none carries the release-please marker. A marker in a file
   release-please does not track freezes at the version it was written with;
   docs/config/v10/lang.yml.txt claimed "SHIPPED WITH v2.1.6" for two releases
   because check 3 deliberately excludes the trailer from its comparison.
7. The newest registry.json entry is the schema the plugin actually ships. An
   entry ahead of the JAR captures newer releases and sends them to a bundle
   for a config they do not use.

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



def shipped_schema() -> str | None:
    """The schema dir name (e.g. "v10") the plugin currently ships."""
    try:
        with open(SHIPPED_CONFIG, encoding="utf-8") as f:
            trailer = f.read().splitlines()[-1]
    except (OSError, IndexError):
        return None
    m = VERSION_RE.search(trailer)
    return f"v{m.group(1)}" if m else None


def check_bundle_declares_its_own_version(version_dir: str) -> list[str]:
    """Each bundle's generator.js must declare the schema it *is*.

    CONFIG_VERSION drives three things: the key it registers under
    (window.DL_GENERATORS[...]), the directory it fetches options.json and the
    template from, and the version shown to the user. A copy-forward that leaves
    the previous value behind therefore produces a generator that silently emits
    the OLD schema's config -- valid YAML, wrong file, and no error anywhere.
    """
    schema = os.path.basename(version_dir)
    js_path = os.path.join(version_dir, "generator.js")
    if not os.path.exists(js_path):
        return [f"{js_path} missing"]

    with open(js_path, encoding="utf-8") as f:
        m = re.search(r"const\s+CONFIG_VERSION\s*=\s*['\"]([^'\"]+)['\"]", f.read())
    if not m:
        return [f"{js_path}: no CONFIG_VERSION declaration found"]
    if m.group(1) != schema:
        return [
            f"{js_path} declares CONFIG_VERSION '{m.group(1)}' but lives in {schema}/. "
            f"It would register as '{m.group(1)}' and load {m.group(1)}'s data, so the "
            f"{schema} generator would emit a {m.group(1)} config."
        ]
    return []


def check_version(options_path: str, live_schema: str | None = None) -> list[str]:
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

            is_live = os.path.basename(version_dir) == live_schema

            config_key = item.get("configKey", "")
            if config_key.startswith("log."):
                token = "LOG_" + config_key[len("log."):]
                if token not in template_tokens:
                    errors.append(
                        f"{options_path}: item '{item_id}' expects "
                        f"{{{{{token}}}}} in {template_path}, not found"
                    )

                # Only the schema the plugin currently ships is checked against the
                # Java source. A frozen schema's keys describe the plugin that
                # shipped IT -- v9 read "log.player.join", today's plugin reads
                # "log.player.join.enabled", and neither is wrong. Checking a frozen
                # bundle against current Java would force an edit to a file that must
                # never change again. Same principle as the mirror comparison:
                # live copies are checked against each other, frozen versions only
                # against themselves.
                if not is_live:
                    continue

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

            # Per-event sub-options: same contract as configKey -- the template must
            # have a slot for it, and (for the live schema) some Java must read it.
            # Without this a sub-option silently generates nothing.
            for extra in item.get("extras", []):
                extra_token = "EXTRA_" + extra.get("key", "")
                if extra_token not in template_tokens:
                    errors.append(
                        f"{options_path}: item '{item_id}' sub-option expects "
                        f"{{{{{extra_token}}}}} in {template_path}, not found"
                    )
                extra_config_key = extra.get("configKey", "")
                if extra_config_key and is_live:
                    grep = subprocess.run(
                        ["grep", "-rl", "-F", f'"{extra_config_key}"', JAVA_SRC],
                        capture_output=True, text=True,
                    )
                    if not grep.stdout.strip():
                        errors.append(
                            f"{options_path}: item '{item_id}' sub-option references "
                            f"'{extra_config_key}', which no Java source reads"
                        )

            # Per-event webhook routing: the template must have a slot, or the key
            # silently never appears in a generated config.
            webhook_key = item.get("webhookKey")
            if webhook_key:
                token = "HOOK_" + webhook_key
                if token not in template_tokens:
                    errors.append(
                        f"{options_path}: item '{item_id}' expects "
                        f"{{{{{token}}}}} in {template_path}, not found"
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


def render_filter(entry: dict) -> str:
    """A deliberate reimplementation of renderFilter() in the v10 generator bundle.

    Two independent implementations that must agree is the whole point: this is
    what proves the generator's untouched output IS the file the plugin ships,
    rather than something that merely looks like it.
    """
    key, kind, value = entry["key"], entry.get("type", "list"), entry.get("default")
    if kind == "bool":
        return f"  {key}: {'true' if value else 'false'}"
    if kind == "number":
        return f"  {key}: {value if isinstance(value, (int, float)) and value >= 0 else 0}"
    if kind == "text":
        return f'  {key}: "{value or ""}"'

    items = [str(v).strip() for v in (value or []) if str(v).strip()]
    if not items:
        return f"  {key}: []"
    notes = {c["value"]: c.get("note", "") for c in entry.get("choices", [])}
    rendered = [
        v if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_./*-]*", v) else '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'
        for v in items
    ]
    width = max([12] + [len(r) for r in rendered])
    lines = []
    for raw, r in zip(items, rendered):
        body = f"  - {r}"
        note = notes.get(raw)
        lines.append(f"  {body}{' ' * (width - len(r) + 2)}# {note}" if note else f"  {body}")
    return f"  {key}:\n" + "\n".join(lines)


def check_filter_contract(options_path: str, template_path: str, is_live: bool) -> list[str]:
    """Every filter is reachable in the wizard, read by the plugin, and shipped as declared."""
    with open(options_path, encoding="utf-8") as f:
        data = json.load(f)
    filters = data.get("filters")
    if filters is None:
        return []          # schemas older than v10 don't expose filters at all

    with open(template_path, encoding="utf-8") as f:
        template = f.read()
    template_keys = set(re.findall(r"\{\{FILTER_([A-Za-z0-9_]+)\}\}", template))
    declared = {f["key"] for f in filters}

    errors = []
    for missing in sorted(declared - template_keys):
        errors.append(
            f"{options_path}: filter '{missing}' has no {{{{FILTER_{missing}}}}} slot in "
            f"{template_path}, so the wizard would collect it and then drop it"
        )
    for orphan in sorted(template_keys - declared):
        errors.append(
            f"{template_path}: {{{{FILTER_{orphan}}}}} has no entry in {options_path}. "
            f"Unmatched tokens are stripped, so that filter would vanish from the output"
        )

    if not is_live:
        return errors

    with open(SHIPPED_CONFIG, encoding="utf-8") as f:
        shipped = f.read()

    for entry in filters:
        key = entry["key"]
        grep = subprocess.run(
            ["grep", "-rl", "-F", f'"filters.{key}"', JAVA_SRC],
            capture_output=True, text=True,
        )
        if not grep.stdout.strip():
            errors.append(
                f"{options_path}: filter '{key}' is offered to users but no Java source "
                f"under {JAVA_SRC} reads 'filters.{key}'"
            )
        block = render_filter(entry)
        if block not in shipped:
            errors.append(
                f"{options_path}: filter '{key}' declares a default that does not match "
                f"{SHIPPED_CONFIG}. Generating a config and changing nothing must reproduce "
                f"the shipped file exactly. Expected to find:\n{block}"
            )
    return errors


def check_lang_contract(version_dir: str, is_live: bool) -> list[str]:
    """The lang template and its option list must cover each other exactly.

    And, for the live schema, substituting every declared default back into the
    template must rebuild the shipped lang.yml verbatim -- the same invariant the
    filters check enforces, for the other file the generator emits.
    """
    options_path = os.path.join(version_dir, "options.json")
    template_path = os.path.join(version_dir, "lang.template.yml")

    with open(options_path, encoding="utf-8") as f:
        data = json.load(f)
    lang = data.get("lang")
    if lang is None:
        return []          # a schema whose generator does not offer lang.yml

    if not os.path.exists(template_path):
        return [f"{template_path} missing, but {options_path} declares lang options"]

    with open(template_path, encoding="utf-8") as f:
        template = f.read()

    template_keys = set(re.findall(r"\{\{LANG_([A-Za-z0-9._-]+)\}\}", template))
    declared = {k["key"]: k.get("default", "") for g in lang.get("groups", []) for k in g.get("keys", [])}

    errors = []
    for missing in sorted(set(declared) - template_keys):
        errors.append(f"{options_path}: lang key '{missing}' has no {{{{LANG_{missing}}}}} slot in {template_path}")
    for orphan in sorted(template_keys - set(declared)):
        errors.append(
            f"{template_path}: {{{{LANG_{orphan}}}}} is in no group in {options_path}, so that "
            f"message would be unreachable in the wizard and stripped from the output"
        )

    if not is_live or errors:
        return errors

    shipped_path = "src/main/resources/lang.yml"
    if not os.path.exists(shipped_path):
        return errors

    rebuilt = template
    for key, default in declared.items():
        rebuilt = rebuilt.replace("{{LANG_" + key + "}}", default.replace("\\", "\\\\").replace('"', '\\"'))
    rebuilt = re.sub(r"\{\{GENERATED_AT\}\}", "", rebuilt)

    with open(shipped_path, encoding="utf-8") as f:
        shipped_lines = f.read().splitlines()

    # Trailers legitimately differ: the shipped file carries the release-please
    # marker, the generated one carries a timestamp.
    if shipped_lines[:-1] != rebuilt.splitlines()[:-1]:
        errors.append(
            f"{template_path} plus its declared defaults no longer rebuilds {shipped_path}. "
            f"Generating lang.yml and changing nothing must reproduce the shipped file exactly -- "
            f"regenerate the template from it."
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
        event = None
        for line in text.split("\nlog:", 1)[1].split("\n"):
            cat_m = re.match(r"^  (\w+):\s*(?:#.*)?$", line)
            if cat_m:
                category = cat_m.group(1)
                continue
            # Schema v9 shape: "    join: true"
            leaf_m = re.match(r"^    (\w+):\s*(true|false)\b", line)
            if leaf_m and category:
                shipped[f"log.{category}.{leaf_m.group(1)}"] = leaf_m.group(2)
                continue
            # Schema v10 shape: the event is a section whose boolean children are
            # its toggles -- "enabled", plus any sub-option such as "show_coords".
            # Capturing all of them (rather than "enabled" alone) is what lets a new
            # sub-option be checked against its Java fallback like any other key.
            event_m = re.match(r"^    (\w+):\s*(?:#.*)?$", line)
            if event_m and category:
                event = event_m.group(1)
                continue
            child_m = re.match(r"^      (\w+):\s*(true|false)\b", line)
            if child_m and category and event:
                shipped[f"log.{category}.{event}.{child_m.group(1)}"] = child_m.group(2)

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


def check_website_copies_are_labelled() -> list[str]:
    """Website copies must say so in their trailer, and must not carry the
    release-please marker.

    The two rules are the same rule. `(x-release-please-version)` only updates in
    files release-please is told about; in any other file it freezes at whatever
    version it was written with and then lies. docs/config/v10/lang.yml.txt sat at
    "SHIPPED WITH v2.1.6" through two releases for exactly that reason, and the
    existing copy check could not see it because that check compares everything
    EXCEPT the trailer -- the one line that differs on purpose.
    """
    errors = []
    for path in sorted(glob.glob("docs/config/v*/*.txt")
                       + glob.glob("docs/assets/configs/v*/*.yml")):
        with open(path, encoding="utf-8") as f:
            lines = f.read().rstrip().splitlines()
        if not lines:
            continue
        trailer = lines[-1]
        if "CONFIG VERSION" not in trailer.upper():
            continue

        if "x-release-please-version" in trailer:
            errors.append(
                f"{path}: trailer carries the release-please marker, but this file is "
                f"not in release-please-config.json's extra-files. It will freeze at "
                f"the version it was written with. Website copies say "
                f"'DOWNLOADED FROM WEBSITE' or 'GENERATED ON WEBSITE'."
            )
        elif not any(k in trailer.upper() for k in ("DOWNLOADED FROM WEBSITE",
                                                    "GENERATED ON WEBSITE")):
            errors.append(
                f"{path}: trailer is {trailer!r}. A copy served by the website should "
                f"say where it came from, not claim to have shipped with a build."
            )
    return errors


def check_lang_doc_copy() -> list[str]:
    """Every copy of lang.yml on the site must be the shipped file verbatim.

    There are two, and they drift independently: the one the docs page embeds, and
    the one people download. A docs page showing options that no longer exist, or a
    download that does not match what the plugin ships, is worse than neither.
    """
    shipped = "src/main/resources/lang.yml"
    if not os.path.exists(shipped):
        return []

    # Follows the live schema rather than naming a version, so this keeps working
    # when v11 arrives instead of silently checking a frozen page.
    schema = shipped_schema()
    if schema is None:
        return ["could not determine the shipped config schema, so lang.yml's copies cannot be checked"]

    copies = [
        f"docs/config/{schema}/lang.yml.txt",
        f"docs/assets/configs/{schema}/lang.yml",
    ]

    with open(shipped, encoding="utf-8") as f:
        shipped_lines = f.read().splitlines()

    errors = []
    for copy in copies:
        if not os.path.exists(copy):
            errors.append(f"{copy} is missing; the docs page and download both need it")
            continue
        with open(copy, encoding="utf-8") as f:
            copy_lines = f.read().splitlines()
        # Compare everything except each file's own trailer line, which legitimately
        # differs -- the shipped one carries the release-please marker.
        if shipped_lines[:-1] != copy_lines[:-1]:
            errors.append(
                f"{shipped} and {copy} have drifted apart. Copy the shipped file over: "
                f"cp {shipped} {copy}"
            )
    return errors


def check_doc_links_match_their_schema() -> list[str]:
    """Every config file's documentation URL must point at that file's own schema.

    The link is a comment, so the byte-for-byte copy checks are blind to it being
    stale: they compare a mirror against the shipped file, and all of them were
    wrong together. A v11 config shipped pointing users at the v11 docs' predecessor,
    where the key they were reading about does not appear.

    Checked against the version the file itself declares rather than the live schema,
    so a frozen bundle keeps pointing at its own page forever -- v9's config should
    link to /config/v9/, and always should.
    """
    link_re = re.compile(r"/config/(v\d+)/")
    errors = []
    paths = (["src/main/resources/config.yml", "src/main/resources/lang.yml"]
             + glob.glob("docs/assets/configs/v*/*.yml")
             + glob.glob("docs/config/v*/*.txt"))
    for path in sorted(set(paths)):
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except OSError:
            continue
        m = VERSION_RE.search(text)
        if not m:
            continue
        own = f"v{m.group(1)}"
        for linked in set(link_re.findall(text)):
            if linked != own:
                errors.append(
                    f"{path} declares {own.upper()} but links to /config/{linked}/. "
                    f"A config should document itself -- readers following that link "
                    f"land on a page that does not describe the file they have."
                )
    return errors


def check_registry_matches_shipped_schema() -> list[str]:
    """The newest registry entry must be the schema the plugin actually ships.

    The generator resolves a visitor's plugin version to a schema by walking this
    list, so an entry ahead of the JAR captures every release newer than its
    `since` and hands them a bundle for a config their build does not use. A
    speculative v10 entry was added once before v10 shipped and had to be pulled
    for exactly that reason -- it would have broken 2.2.0.

    Trailing the shipped config is the same bug pointed the other way: the
    schema in the JAR would be unreachable from the generator entirely.
    """
    live = shipped_schema()
    if live is None:
        return ["cannot read the shipped config's schema trailer"]

    path = "docs/assets/configs/registry.json"
    try:
        with open(path, encoding="utf-8") as f:
            schemas = json.load(f).get("schemas", [])
    except (OSError, json.JSONDecodeError) as e:
        return [f"{path}: unreadable ({e})"]
    if not schemas:
        return [f"{path}: no schemas listed"]

    newest = schemas[-1].get("config")
    if newest != live:
        return [
            f"{path}: newest entry is '{newest}' but the plugin ships '{live}' "
            f"({SHIPPED_CONFIG}'s trailer). The newest entry must be the shipped "
            f"schema -- ahead of it, the generator sends real releases to a bundle "
            f"for a config they do not use; behind it, the shipped schema is "
            f"unreachable."
        ]
    if not os.path.isdir(f"docs/assets/configs/{live}"):
        return [f"{path} lists '{live}' but docs/assets/configs/{live}/ does not exist"]
    return []


def main() -> int:
    all_errors = []
    live = shipped_schema()
    for options_path in sorted(glob.glob("docs/assets/configs/v*/options.json")):
        version_dir = os.path.dirname(options_path)
        is_live = os.path.basename(version_dir) == live
        all_errors.extend(check_version(options_path, live))
        all_errors.extend(check_bundle_declares_its_own_version(version_dir))
        all_errors.extend(check_filter_contract(
            options_path, os.path.join(version_dir, "config.template.yml"), is_live))
        all_errors.extend(check_lang_contract(version_dir, is_live))
    all_errors.extend(check_shipped_config_matches_mirror())
    all_errors.extend(check_doc_page_embedded_configs())
    all_errors.extend(check_java_fallbacks_match_shipped_config())
    all_errors.extend(check_lang_doc_copy())
    all_errors.extend(check_website_copies_are_labelled())
    all_errors.extend(check_registry_matches_shipped_schema())
    all_errors.extend(check_doc_links_match_their_schema())

    if all_errors:
        for e in all_errors:
            print(f"ERROR: {e}")
        print(f"\n{len(all_errors)} problem(s) found in config generator data.")
        return 1

    print("Config generator data OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
