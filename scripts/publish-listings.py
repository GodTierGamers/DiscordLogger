#!/usr/bin/env python3
"""Publishes a stable release to the Modrinth and Hangar listings.

Those two listings are where most server owners actually find the plugin, so they
have to carry the same version GitHub does. Doing that by hand means remembering
two upload forms on every release; this does it from the release that was just
published.

Hangar versions are registered with an `externalUrl` pointing at the GitHub
Release asset rather than a re-hosted copy, so Hangar's download button
increments GitHub's counter. Modrinth has no equivalent — its API requires the
file to be uploaded to Modrinth — so that one JAR is mirrored, and Modrinth
keeps its own tally. `--badge` adds the two together and writes the
shields.io endpoint JSON the README badge reads, so one number covers both.

Run by .github/workflows/release-please.yml after the stable JAR is attached to
the GitHub Release. Nightly builds are deliberately never published here — they
stay GitHub-only, as documented on the downloads page.

Everything it needs comes from pom.xml and the GitHub Release:

    <project.version>      the version number to publish
    <dl.game.versions>     Minecraft versions to advertise
    the release body       becomes the changelog on both listings
    the release asset      the download URL the listings point at

Credentials come from the environment and are never logged:

    MODRINTH_TOKEN   a Modrinth PAT with "Create versions" scope
    HANGAR_API_KEY   a Hangar API key with the create_version permission

If a token is missing that platform is skipped with a notice rather than failing
the release — a listing that lags by one version is recoverable, a release job
that dies after tagging is messier. Genuine API failures do fail the job.

Usage:
    publish-listings.py --jar target/DiscordLogger-v2.2.0.jar --tag v2.2.0
    publish-listings.py --jar ... --tag ... --dry-run   # no network writes
    publish-listings.py --badge                         # refresh the badge only
"""
from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

POM = Path("pom.xml")
UA = "GodTierGamers/DiscordLogger (release automation)"

MODRINTH_API = "https://api.modrinth.com/v2"
MODRINTH_PROJECT = "discordlogger"

HANGAR_API = "https://hangar.papermc.io/api/v1"
HANGAR_SLUG = "DiscordLogger"


# --------------------------------------------------------------------------- io


def log(msg: str) -> None:
    print(msg, flush=True)


def fail(msg: str) -> "NoReturn":  # type: ignore[valid-type]
    print(f"::error::{msg}", flush=True)
    sys.exit(1)


def request(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    data: bytes | None = None,
    allow_404: bool = False,
) -> Any:
    """Single JSON request helper. Raises with the response body on failure —
    both APIs put the actual reason in there, and without it a 400 is unusable."""
    req = urllib.request.Request(url, method=method, data=data)
    req.add_header("User-Agent", UA)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read()
    except urllib.error.HTTPError as exc:
        if exc.code == 404 and allow_404:
            return None
        detail = exc.read().decode("utf-8", "replace").strip()
        raise RuntimeError(f"{method} {url} -> HTTP {exc.code}: {detail}") from None
    if not body:
        return None
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body.decode("utf-8", "replace")


def multipart(fields: dict[str, str], files: dict[str, Path]) -> tuple[bytes, str]:
    """Builds a multipart/form-data body. Both APIs take the metadata as a JSON
    form field alongside the JAR, and neither accepts a plain JSON POST."""
    boundary = "----DiscordLoggerRelease" + os.urandom(16).hex()
    parts: list[bytes] = []

    for name, value in fields.items():
        parts.append(
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{name}"\r\n'
            f"Content-Type: application/json\r\n\r\n"
            f"{value}\r\n".encode()
        )

    for name, path in files.items():
        ctype = mimetypes.guess_type(path.name)[0] or "application/java-archive"
        parts.append(
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{name}"; filename="{path.name}"\r\n'
            f"Content-Type: {ctype}\r\n\r\n".encode()
        )
        parts.append(path.read_bytes())
        parts.append(b"\r\n")

    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# ------------------------------------------------------------------- inputs


def pom_values() -> dict[str, str]:
    text = POM.read_text(encoding="utf-8")

    def prop(tag: str) -> str:
        match = re.search(rf"<{re.escape(tag)}>(.*?)</{re.escape(tag)}>", text, re.S)
        if not match:
            fail(f"pom.xml has no <{tag}>")
        return match.group(1).strip()

    # <version> under <project>, not a dependency's — it is the first one that
    # appears after </parent> is irrelevant here since there is no parent POM,
    # so take the first <version> at the top level of the file.
    version = re.search(r"</artifactId>\s*<version>(.*?)</version>", text, re.S)
    if not version:
        fail("pom.xml has no project <version>")

    return {
        "version": version.group(1).strip(),
        "game_versions": prop("dl.game.versions"),
        "api_version": prop("dl.api.version"),
    }


def release_body(tag: str) -> str:
    """The GitHub Release notes, reused verbatim as the listing changelog so the
    three never drift. Falls back to a link if gh is unavailable."""
    fallback = (
        f"See the full release notes: "
        f"https://github.com/GodTierGamers/DiscordLogger/releases/tag/{tag}"
    )
    try:
        out = subprocess.run(
            ["gh", "release", "view", tag, "--json", "body", "-q", ".body"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return fallback
    return f"{out}\n\n---\n{fallback}" if out else fallback


def validate_game_versions(versions: list[str]) -> None:
    """Modrinth rejects unknown versions with an opaque 400, and Hangar will
    happily accept a typo and display it. Check the list up front instead."""
    known = {
        entry["version"]
        for entry in request(f"{MODRINTH_API}/tag/game_version")
    }
    unknown = [v for v in versions if v not in known]
    if unknown:
        fail(
            f"pom.xml <dl.game.versions> lists {unknown}, which Minecraft version(s) "
            f"Modrinth does not recognise. Fix the property in pom.xml."
        )
    log(f"Game versions validated against Modrinth: {', '.join(versions)}")


# ------------------------------------------------------------------ modrinth


def publish_modrinth(
    token: str, jar: Path, version: str, game_versions: list[str], changelog: str, dry: bool
) -> None:
    existing = request(
        f"{MODRINTH_API}/project/{MODRINTH_PROJECT}/version/{version}", allow_404=True
    )
    if existing:
        log(f"Modrinth already has {version} — nothing to do.")
        return

    payload = {
        "name": f"v{version}",
        "version_number": version,
        "changelog": changelog,
        "dependencies": [],
        "game_versions": game_versions,
        "version_type": "release",
        "loaders": ["paper"],
        "featured": True,
        "project_id": MODRINTH_PROJECT,
        "file_parts": ["file"],
        "primary_file": "file",
    }

    if dry:
        log("[dry-run] Modrinth payload:\n" + json.dumps(payload, indent=2))
        return

    body, ctype = multipart({"data": json.dumps(payload)}, {"file": jar})
    result = request(
        f"{MODRINTH_API}/version",
        method="POST",
        headers={"Authorization": token, "Content-Type": ctype},
        data=body,
    )
    log(
        "Published to Modrinth: "
        f"https://modrinth.com/plugin/{MODRINTH_PROJECT}/version/{result['id']}"
    )


# -------------------------------------------------------------------- hangar


def publish_hangar(
    api_key: str,
    asset_url: str,
    version: str,
    game_versions: list[str],
    changelog: str,
    dry: bool,
) -> None:
    if dry:
        log(f"[dry-run] Hangar would publish {version} for PAPER {game_versions}")
        log(f"[dry-run]   download points at {asset_url}")
        return

    # Hangar trades the long-lived API key for a short-lived JWT; every other
    # call uses the JWT.
    auth = request(
        f"{HANGAR_API}/authenticate?apiKey={api_key}", method="POST"
    )
    jwt = auth["token"]
    headers = {"Authorization": f"Bearer {jwt}"}

    versions = request(
        f"{HANGAR_API}/projects/{HANGAR_SLUG}/versions?limit=25&offset=0",
        headers=headers,
    )
    if any(v["name"] == version for v in versions.get("result", [])):
        log(f"Hangar already has {version} — nothing to do.")
        return

    payload = {
        "version": version,
        "channel": "Release",
        "description": changelog,
        "pluginDependencies": {},
        "platformDependencies": {"PAPER": game_versions},
        # externalUrl instead of an attached JAR: Hangar then links straight to the
        # GitHub Release asset, so its download button increments GitHub's counter
        # rather than starting a second, separate tally on Hangar.
        "files": [{"platforms": ["PAPER"], "externalUrl": asset_url}],
    }

    body, ctype = multipart({"versionUpload": json.dumps(payload)}, {})
    request(
        f"{HANGAR_API}/projects/{HANGAR_SLUG}/upload",
        method="POST",
        headers={**headers, "Content-Type": ctype},
        data=body,
    )
    log(
        "Published to Hangar: "
        f"https://hangar.papermc.io/LVCHLANN/{HANGAR_SLUG}/versions/{version}"
    )


# --------------------------------------------------------------- download count


GITHUB_API = "https://api.github.com/repos/GodTierGamers/DiscordLogger"

# shields.io renders any JSON in this shape as a badge, which is the only way to
# show a total that spans two hosts — its built-in github/downloads badge can
# only ever see GitHub.
BADGE_PATH = Path("docs/assets/badges/downloads.json")


def download_counts() -> dict[str, int]:
    """GitHub + Modrinth downloads. Hangar is deliberately absent: its versions
    are external links to the GitHub asset, so Hangar traffic is already inside
    the GitHub number and adding it would double-count."""
    github = 0
    page = 1
    while True:
        releases = request(f"{GITHUB_API}/releases?per_page=100&page={page}")
        if not releases:
            break
        for release in releases:
            for asset in release.get("assets", []):
                # Checksums sit next to every JAR; they are not plugin downloads.
                if not asset["name"].endswith(".sha256"):
                    github += asset["download_count"]
        if len(releases) < 100:
            break
        page += 1

    modrinth = request(f"{MODRINTH_API}/project/{MODRINTH_PROJECT}")["downloads"]
    return {"github": github, "modrinth": modrinth, "total": github + modrinth}


def write_badge(counts: dict[str, int], path: Path) -> None:
    total = counts["total"]
    # Match shields' own abbreviation so the badge doesn't grow without bound.
    if total >= 1_000_000:
        label = f"{total / 1_000_000:.1f}M".replace(".0M", "M")
    elif total >= 1_000:
        label = f"{total / 1_000:.1f}k".replace(".0k", "k")
    else:
        label = str(total)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "label": "downloads",
                "message": label,
                "color": "brightgreen",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    log(
        f"GitHub {counts['github']} + Modrinth {counts['modrinth']} = {total} "
        f"-> {path} ({label})"
    )


# ---------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="the built stable JAR")
    parser.add_argument("--tag", help="release tag, e.g. v2.2.0")
    parser.add_argument(
        "--badge",
        action="store_true",
        help="only refresh the combined GitHub+Modrinth downloads badge, then exit",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="resolve and validate everything, but make no network writes",
    )
    args = parser.parse_args()

    if args.badge:
        write_badge(download_counts(), BADGE_PATH)
        return 0

    if not args.jar or not args.tag:
        fail("--jar and --tag are required unless --badge is given")

    if not args.jar.is_file():
        fail(f"JAR not found: {args.jar}")

    values = pom_values()
    tag_version = args.tag.lstrip("v")

    if "-BETA." in args.tag:
        fail(f"{args.tag} is a nightly build; those are never published to listings.")

    # A mismatch here means the JAR is not the version being announced. That is
    # worth stopping for: it would put the wrong file on both listings.
    if values["version"] != tag_version:
        fail(
            f"pom.xml is {values['version']} but the release tag is {args.tag}. "
            "Refusing to publish a mismatched artifact."
        )

    asset_url = (
        "https://github.com/GodTierGamers/DiscordLogger/releases/download/"
        f"{args.tag}/{args.jar.name}"
    )

    game_versions = [v.strip() for v in values["game_versions"].split(",") if v.strip()]
    if not game_versions:
        fail("pom.xml <dl.game.versions> is empty")

    validate_game_versions(game_versions)
    changelog = release_body(args.tag)

    modrinth_token = os.environ.get("MODRINTH_TOKEN", "").strip()
    hangar_key = os.environ.get("HANGAR_API_KEY", "").strip()

    failures: list[str] = []

    if modrinth_token or args.dry_run:
        try:
            publish_modrinth(
                modrinth_token, args.jar, tag_version, game_versions, changelog, args.dry_run
            )
        except Exception as exc:  # noqa: BLE001 - reported, not swallowed
            failures.append(f"Modrinth: {exc}")
    else:
        log("::notice::MODRINTH_TOKEN is not set — skipping Modrinth.")

    if hangar_key or args.dry_run:
        try:
            publish_hangar(
                hangar_key,
                asset_url,
                tag_version,
                game_versions,
                changelog,
                args.dry_run,
            )
        except Exception as exc:  # noqa: BLE001 - reported, not swallowed
            failures.append(f"Hangar: {exc}")
    else:
        log("::notice::HANGAR_API_KEY is not set — skipping Hangar.")

    if not args.dry_run and not failures:
        try:
            write_badge(download_counts(), BADGE_PATH)
        except Exception as exc:  # noqa: BLE001 - a stale badge must not fail a release
            log(f"::warning::Could not refresh the downloads badge: {exc}")

    for failure in failures:
        print(f"::error::{failure}", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
