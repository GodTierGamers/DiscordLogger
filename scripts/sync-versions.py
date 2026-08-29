#!/usr/bin/env python3
"""Propagates the version values out of pom.xml into every file that displays them.

pom.xml is the single source of truth. Nothing else in the repo should contain a
hand-typed Java version, Paper version, or api-version — this script writes them
all, and CI runs it automatically on every push to main (see sync-versions.yml),
committing the result. Change a value in pom.xml and it lands everywhere on its own.

Values, and where each comes from:

    <project.version>          the plugin version           (release-please owns it)
    <maven.compiler.release>   Java the plugin is built for
    <dl.api.version>           minimum Paper (plugin.yml's api-version)
    <dl.game.versions>         the supported range; prose + badge are derived from it

Targets:

    docs/_data/versions.yml    generated; Jekyll pages read {{ site.data.versions.* }}
                               (includes the plugin version + config schema, so docs
                               examples of the config trailer stay accurate)
    README.md                  badges + prose, between dl:sync markers
    CONTRIBUTING.md            prose, between dl:sync markers

Already self-updating, deliberately NOT touched here:
    plugin.yml, build-info.properties   Maven filtering resolves ${...} at build time
    the CI workflows                    read the Java version out of pom.xml at runtime
    anything showing the "latest release" on the site   reads the GitHub releases API live
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

POM = Path("pom.xml")
SHIPPED_CONFIG = Path("src/main/resources/config.yml")
DATA = Path("docs/_data/versions.yml")

# Prose/badges are rewritten between markers so the surrounding wording stays editable:
#     <!-- dl:sync:java -->25<!-- /dl:sync -->
MARKER = re.compile(r"(<!-- dl:sync:(\w+) -->)(.*?)(<!-- /dl:sync -->)", re.DOTALL)

# Some values sit inside Markdown syntax that can't contain HTML comments — a
# shields.io badge URL is the case in point: `![Java](...<!-- -->25<!-- -->...)`
# breaks the image entirely and GitHub renders the raw text. Those use a
# BLOCK marker on its own lines instead, and the whole block is regenerated.
BLOCK = re.compile(
    r"(<!-- dl:sync-block:(\w+) -->\n)(.*?)(<!-- /dl:sync-block -->)", re.DOTALL
)

BLOCK_TEMPLATES = {
    "badges": lambda v: (
        f"![Java](https://img.shields.io/badge/Java-{v['java']}%2B-orange)\n"
        f"![Paper](https://img.shields.io/badge/Paper-{v['paper_badge']}-blue)\n"
    ),
}


def paper_display(game_versions: str) -> str:
    """The supported range as (prose, badge) — e.g. ("1.19.4 – 26.2", "1.19.4--26.2").

    Derived from <dl.game.versions> rather than hand-set, because the two would
    otherwise drift the moment a new Minecraft release is added: the listings
    would advertise it while every badge and requirements table still named the
    old ceiling. A bare "1.19.4+" was worse still -- true, but silent about what
    has actually been tested at the top of the range.
    """
    parts = [v.strip() for v in game_versions.split(",") if v.strip()]
    if not parts:
        return "unknown", "unknown"
    if len(parts) == 1:
        return parts[0], parts[0]
    # Prose gets an en dash; the badge gets shields.io's escaping, where a literal
    # dash is "--" and a space breaks the URL outright (the image silently fails to
    # render and GitHub shows the raw markdown instead).
    return f"{parts[0]} \u2013 {parts[-1]}", f"{parts[0]}--{parts[-1]}"


def pom_values() -> dict[str, str]:
    text = POM.read_text(encoding="utf-8")

    def prop(name: str) -> str:
        m = re.search(rf"<{re.escape(name)}>([^<]+)</{re.escape(name)}>", text)
        if not m:
            sys.exit(f"pom.xml: missing <{name}>")
        return m.group(1).strip()

    # <version> sits at project level; take the first one before <properties>
    vm = re.search(r"<artifactId>discordlogger</artifactId>\s*<version>([^<]+)</version>", text)
    if not vm:
        sys.exit("pom.xml: could not read the project <version>")

    # The config schema the plugin currently ships, from config.yml's trailer.
    # Docs examples show it, and it moves on its own schedule (see AGENTS.md).
    schema = ""
    if SHIPPED_CONFIG.exists():
        trailer = SHIPPED_CONFIG.read_text(encoding="utf-8").rstrip().splitlines()[-1]
        sm = re.search(r"CONFIG\s+VERSION\s+(V\d+)", trailer, re.IGNORECASE)
        if sm:
            schema = sm.group(1).upper()

    display, badge = paper_display(prop("dl.game.versions"))

    return {
        "plugin": vm.group(1).strip(),
        "schema": schema,
        "java": prop("maven.compiler.release"),
        "paper_api": prop("spigot.api.version"),
        "min_paper": prop("dl.api.version"),
        "paper_display": display,
        "paper_badge": badge,
    }


def write_data_file(v: dict[str, str]) -> bool:
    """Jekyll data file — every docs page reads from this instead of hardcoding.

    The `plugin` line carries release-please's marker on purpose: it is listed in
    release-please-config.json's extra-files, so a version bump lands *inside* the
    release PR rather than needing a follow-up push to main. That matters because
    branch protection rejects bot pushes to main -- this workflow failed silently
    on the v2.2.0 release and left the docs advertising 2.1.6. Keep the marker, or
    the bump goes back to depending on a push that cannot land.
    """
    content = (
        "# GENERATED by scripts/sync-versions.py from pom.xml — do not edit by hand.\n"
        "# Docs pages reference these as {{ site.data.versions.<key> }}.\n"
        f"plugin: \"{v['plugin']}\" # x-release-please-version\n"
        f"schema: \"{v['schema']}\"\n"
        f"java: \"{v['java']}\"\n"
        f"min_paper: \"{v['min_paper']}\"\n"
        f"paper_display: \"{v['paper_display']}\"\n"
        f"paper_api: \"{v['paper_api']}\"\n"
    )
    DATA.parent.mkdir(parents=True, exist_ok=True)
    if DATA.exists() and DATA.read_text(encoding="utf-8") == content:
        return False
    DATA.write_text(content, encoding="utf-8")
    return True


def fill_markers(path: Path, v: dict[str, str]) -> bool:
    if not path.exists():
        return False
    original = path.read_text(encoding="utf-8")

    def repl(m: re.Match) -> str:
        open_tag, key, _old, close_tag = m.groups()
        if key not in v:
            sys.exit(f"{path}: unknown sync key '{key}' (known: {', '.join(sorted(v))})")
        return f"{open_tag}{v[key]}{close_tag}"

    def repl_block(m: re.Match) -> str:
        open_tag, name, _old, close_tag = m.groups()
        if name not in BLOCK_TEMPLATES:
            sys.exit(f"{path}: unknown sync block '{name}' "
                     f"(known: {', '.join(sorted(BLOCK_TEMPLATES))})")
        return f"{open_tag}{BLOCK_TEMPLATES[name](v)}{close_tag}"

    updated = BLOCK.sub(repl_block, original)
    updated = MARKER.sub(repl, updated)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        return True
    return False


def main() -> int:
    if not POM.exists():
        sys.exit("run this from the repository root (pom.xml not found)")

    v = pom_values()
    changed = []

    if write_data_file(v):
        changed.append(str(DATA))
    for p in (Path("README.md"), Path("CONTRIBUTING.md")):
        if fill_markers(p, v):
            changed.append(str(p))

    print("Version values from pom.xml:")
    for k, val in v.items():
        print(f"  {k:14} {val}")

    if changed:
        print("\nUpdated:")
        for c in changed:
            print(f"  {c}")
    else:
        print("\nEverything already in sync.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
