#!/usr/bin/env python3
"""Publishes a stable release to the Modrinth and Hangar listings.

Those two listings are where most server owners actually find the plugin, so they
have to carry the same version GitHub does. Doing that by hand means remembering
two upload forms on every release; this does it from the release that was just
published.

Hangar versions are registered with an `externalUrl` pointing at the GitHub
Release asset rather than a re-hosted copy, so Hangar's download button
increments GitHub's counter. Modrinth has no equivalent — its API requires the
file to be uploaded to Modrinth — so that one JAR is mirrored there and keeps
its own tally.

Run by .github/workflows/release-please.yml after the stable JAR is attached to
the GitHub Release. Nightly builds are deliberately never published here — they
stay GitHub-only, as documented on the downloads page.

Everything it needs comes from pom.xml and the GitHub Release:

    <project.version>      the version number to publish
    <dl.game.versions>     Minecraft versions to advertise
    MODRINTH_LOADERS       server platforms to tag on Modrinth (paper + purpur)
    the release body       becomes the changelog on both listings
    the release asset      the download URL the listings point at

Credentials come from the environment and are never logged:

    MODRINTH_TOKEN   a Modrinth PAT with the "Create versions" scope, plus
                     "Read user data" so --check-auth can verify it without
                     publishing anything. "Read analytics" used to serve that
                     purpose; Modrinth withdrew those routes in August 2026.
    HANGAR_API_KEY   a Hangar API key with the create_version permission

If a token is missing that platform is skipped with a notice rather than failing
the release — a listing that lags by one version is recoverable, a release job
that dies after tagging is messier. Genuine API failures do fail the job.

Usage:
    publish-listings.py --jar target/DiscordLogger-v2.2.0.jar --tag v2.2.0
    publish-listings.py --jar ... --tag ... --dry-run   # no network writes
    publish-listings.py --check-auth                    # verify both tokens
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
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

POM = Path("pom.xml")
UA = "GodTierGamers/DiscordLogger (release automation)"

MODRINTH_API = "https://api.modrinth.com/v2"
# Analytics only exists on v3; v2 returns 404 for it. Used solely by --check-auth.
MODRINTH_API_V3 = "https://api.modrinth.com/v3"
MODRINTH_PROJECT = "discordlogger"

# Modrinth treats each server fork as its own loader, and a version tagged only
# "paper" is filtered out for anyone browsing as Purpur -- even though Purpur is
# a Paper fork the plugin fully supports and the docs name explicitly. Both tags
# go on every version. Hangar has no equivalent: its platform list is
# PAPER/WATERFALL/VELOCITY, and PAPER already covers the forks.
# Bukkit and Spigot joined this list when the plugin stopped requiring Paper. Leaving
# them out would have kept the listing invisible to exactly the servers the port was
# for. Folia is deliberately absent: nothing here has been checked against region
# threading, and claiming a platform is a promise rather than a hope.
MODRINTH_LOADERS = ["bukkit", "spigot", "paper", "purpur"]

HANGAR_API = "https://hangar.papermc.io/api/v1"
HANGAR_SLUG = "DiscordLogger"

# CurseForge's upload API still lives on the old per-game host. It is not the same
# service as the public api.curseforge.com read API and does not share its keys: this
# one takes a project-scoped token from the author's own account settings.
CURSEFORGE_API = "https://minecraft.curseforge.com/api"

# The numeric project id, from the project's own page. Read from the environment
# rather than hardcoded, so the listing can be created and wired up without a code
# change -- and so this stays inert until it exists.
CURSEFORGE_PROJECT_ENV = "CURSEFORGE_PROJECT_ID"

# CurseForge keeps server software in the same list as Minecraft versions, under a
# different type. Included when the list offers them and skipped silently when it does
# not, because which of these exist has changed over the years and a hard requirement
# would break a release over a label.
CURSEFORGE_LOADERS = ["Bukkit", "Spigot", "Paper"]


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


def validate_loaders(loaders: list[str]) -> None:
    """Same reasoning as the game-version check: Modrinth answers an unknown
    loader with an opaque 400, so a typo would surface as a failed release
    rather than as a name it does not recognise."""
    known = {entry["name"] for entry in request(f"{MODRINTH_API}/tag/loader")}
    unknown = [l for l in loaders if l not in known]
    if unknown:
        fail(f"MODRINTH_LOADERS lists {unknown}, which Modrinth does not recognise.")
    log(f"Loaders validated against Modrinth: {', '.join(loaders)}")


# ------------------------------------------------------------------ modrinth


def modrinth_project_id() -> str:
    """The project's real base62 id, resolved from its slug.

    `project_id` in a version payload is NOT slug-or-id like the read endpoints
    are -- Modrinth base62-decodes it. The 13-character slug overflows that
    decode and comes back as a 400 that names neither the field nor the slug
    ("Base62 decoding overflowed"), which is why this went unnoticed until a
    real release tried to publish. Resolved at runtime rather than hardcoded so
    the two can never disagree.
    """
    project = request(f"{MODRINTH_API}/project/{MODRINTH_PROJECT}")
    ident = (project or {}).get("id", "")
    if not ident:
        fail(f"Modrinth returned no id for project '{MODRINTH_PROJECT}'")
    return ident


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
        "loaders": MODRINTH_LOADERS,
        "featured": True,
        "project_id": modrinth_project_id(),
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


# Paper's oldest build. Hangar's PAPER platform means "runs on Paper", and Paper does
# not exist below this -- so while the plugin genuinely supports 1.8.0, saying so on a
# Paper listing would be a claim about builds nobody can download. Modrinth and
# CurseForge take the full range, because their listings are not platform-scoped the
# same way.
#
# A constant rather than a lookup: Hangar's only platform-versions endpoint is an admin
# write endpoint, and there is no public read to ask.
HANGAR_OLDEST = (1, 8, 8)


def version_tuple(name: str) -> tuple[int, ...]:
    """"1.21.11" -> (1, 21, 11), so versions sort by number rather than by string."""
    parts = []
    for piece in name.split("."):
        try:
            parts.append(int(piece))
        except ValueError:
            # A name that is not purely numeric sorts last, which keeps anything
            # unexpected in the list rather than silently dropping it.
            return (9999,)
    return tuple(parts)


def hangar_versions(wanted: list[str]) -> list[str]:
    """The wanted versions that Paper actually has builds for."""
    accepted = [v for v in wanted if version_tuple(v) >= HANGAR_OLDEST]
    dropped = [v for v in wanted if version_tuple(v) < HANGAR_OLDEST]
    if dropped:
        log(
            f"Hangar: dropping {len(dropped)} version(s) older than Paper's first build "
            f"({'.'.join(str(n) for n in HANGAR_OLDEST)}): {', '.join(dropped)}"
        )
    if not accepted:
        raise RuntimeError(
            "no version in <dl.game.versions> is one Paper has ever built, so there is "
            "nothing to publish to Hangar"
        )
    return accepted


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


# ------------------------------------------------------------------ curseforge


def curseforge_version_ids(token: str, wanted: list[str]) -> list[int]:
    """Turns version names into the numeric ids the upload call requires.

    CurseForge will not take "1.21.4"; it wants the id that names it, and the ids are
    not stable across games, so they have to be looked up every time.

    Matching is by name rather than by version-type id. The type ids group Minecraft
    releases, Java versions and server software separately, and their meanings have
    moved; a name like "1.21.4" can only ever match a Minecraft version, so the lenient
    match is also the safe one.
    """
    catalogue = request(
        f"{CURSEFORGE_API}/game/versions", headers={"X-Api-Token": token}
    )
    by_name: dict[str, int] = {}
    for entry in catalogue or []:
        name = str(entry.get("name", "")).strip()
        if name and name not in by_name:
            by_name[name] = int(entry["id"])

    ids: list[int] = []
    missing: list[str] = []
    for name in wanted:
        if name in by_name:
            ids.append(by_name[name])
        else:
            missing.append(name)

    if missing:
        # Not fatal on its own: CurseForge adds a Minecraft version to this list on
        # its own schedule, and a release should not be blocked because the newest one
        # is not there yet. It IS fatal if nothing matched at all.
        log(f"::warning::CurseForge does not list these versions yet: {', '.join(missing)}")
    if not ids:
        raise RuntimeError(
            "none of the versions in <dl.game.versions> exist in CurseForge's list, so "
            "the upload would be rejected. Checked: " + ", ".join(wanted)
        )

    for loader in CURSEFORGE_LOADERS:
        if loader in by_name:
            ids.append(by_name[loader])
        else:
            log(f"CurseForge does not offer '{loader}' as a version; skipping it.")

    return ids


def publish_curseforge(
    token: str,
    project_id: str,
    jar: Path,
    version: str,
    game_versions: list[str],
    changelog: str,
    dry: bool,
) -> None:
    if dry and not token:
        log(f"[dry-run] CurseForge would publish {version} to project {project_id}")
        return

    # Resolved even on a dry run, when there is a token to resolve with. It is a read,
    # so it writes nothing -- and it is the only part of this that can be checked
    # before a release: it proves the token is accepted and shows how many of our
    # versions CurseForge actually knows. A dry run that skipped it would report
    # success without having spoken to CurseForge at all.
    ids = curseforge_version_ids(token, game_versions)

    if dry:
        log(f"[dry-run] CurseForge would publish {version} to project {project_id} "
            f"for {len(ids)} version ids")
        log("[dry-run]   the project id itself is only checked by a real upload; "
            "CurseForge offers no way to read a project back")
        return
    metadata = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": f"DiscordLogger {version}",
        "gameVersions": ids,
        # Nightlies never reach this script -- main() refuses them -- so anything
        # arriving here is a stable release.
        "releaseType": "release",
    }
    body, content_type = multipart(
        {"metadata": json.dumps(metadata)}, {"file": jar}
    )
    result = request(
        f"{CURSEFORGE_API}/projects/{project_id}/upload-file",
        method="POST",
        headers={"X-Api-Token": token, "Content-Type": content_type},
        data=body,
    )
    file_id = (result or {}).get("id") if isinstance(result, dict) else None
    log(f"CurseForge: uploaded {jar.name} for {len(ids)} versions (file {file_id})")


GITHUB_API = "https://api.github.com/repos/GodTierGamers/DiscordLogger"

# ------------------------------------------------------------------ auth check


def check_auth() -> int:
    """Proves both credentials still work, without publishing anything.

    Worth having as its own mode: --dry-run makes no authenticated call at all,
    so the first thing that ever exercises these tokens would otherwise be a
    release that has already tagged. Tokens also expire — Modrinth PATs carry an
    explicit expiry date — and an expired one is indistinguishable from a
    missing one until something tries to use it.
    """
    problems: list[str] = []

    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        problems.append("MODRINTH_TOKEN is not set")
    else:
        # VERSION_CREATE covers exactly one endpoint -- the publish call, a POST -- so
        # there is no request it can make that proves it works. Checking the token at
        # all depends on it holding some read scope as well.
        #
        # /v3/analytics/downloads was that read, and it worked: this check passed on it
        # weekly, last on 2026-08-24 with "token accepted (analytics scope)". Between
        # then and 2026-08-31 Modrinth removed the route. It now answers 404 to
        # everyone, authenticated or not, while the rest of v3 is healthy, and the
        # public API reference documents no analytics endpoints at all.
        #
        # A 404 and a 401 mean different things here and are no longer treated alike.
        # 404 is the route being gone, which says nothing about the token. 401 is the
        # token being refused -- but a PAT that simply lacks that scope is refused
        # identically to one that has been revoked, so it is not proof of a dead token
        # either. Only a success proves anything, which is why neither failure is
        # reported as one.
        probes = (
            ("user info", f"{MODRINTH_API}/user"),
            ("user info v3", f"{MODRINTH_API_V3}/user"),
        )
        errors: list[str] = []
        verified = False
        for name, url in probes:
            try:
                request(url, headers={"Authorization": token})
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{name}: {exc}")
                continue
            log(f"Modrinth: token accepted ({name})")
            verified = True
            break

        if not verified:
            # A notice, not an error. Failing here every week for a token that is
            # probably fine would train everyone to ignore this workflow, and its
            # remaining value -- Hangar and CurseForge, both genuinely checkable -- goes
            # with it. Saying "unverified" is honest; saying "rejected" is not.
            log(
                "::notice::Modrinth token could not be verified, which is not the same "
                "as it being broken. A PAT holding only 'Create versions' publishes "
                "perfectly well and still fails every probe, because no readable "
                "endpoint is gated by that scope. 'Read analytics' used to give one; "
                "Modrinth removed those routes in late August 2026. To make this "
                "checkable again, tick 'Read user data' at "
                "https://modrinth.com/settings/pats -- it grants nothing beyond reading "
                "your own account. Until then a revoked Modrinth token will not be "
                "noticed here, and will surface at the release that needs it. "
                + " | ".join(errors)
            )

    key = os.environ.get("HANGAR_API_KEY", "").strip()
    if not key:
        problems.append("HANGAR_API_KEY is not set")
    else:
        try:
            request(f"{HANGAR_API}/authenticate?apiKey={key}", method="POST")
            log("Hangar: API key accepted")
        except Exception as exc:  # noqa: BLE001
            problems.append(f"Hangar key rejected: {exc}")

    cf_token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    cf_project = os.environ.get(CURSEFORGE_PROJECT_ENV, "").strip()
    if not cf_token and not cf_project:
        # Not a problem: the listing may simply not exist yet. Said out loud so the
        # weekly check does not read as "CurseForge is fine" when it is absent.
        log("::notice::CurseForge is not configured — nothing to check.")
    elif not cf_token:
        problems.append("CURSEFORGE_TOKEN is not set, but a project id is")
    elif not cf_project:
        problems.append(f"{CURSEFORGE_PROJECT_ENV} is not set, but a token is")
    else:
        try:
            request(
                f"{CURSEFORGE_API}/game/versions", headers={"X-Api-Token": cf_token}
            )
            log("CurseForge: token accepted")
        except Exception as exc:  # noqa: BLE001
            problems.append(f"CurseForge token rejected: {exc}")

    for problem in problems:
        print(f"::error::{problem}", flush=True)
    return 1 if problems else 0


# ---------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="the built stable JAR")
    parser.add_argument("--tag", help="release tag, e.g. v2.2.0")
    parser.add_argument(
        "--check-auth",
        action="store_true",
        help="verify both API credentials still work, publish nothing, then exit",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="resolve and validate everything, but make no network writes",
    )
    args = parser.parse_args()

    if args.check_auth:
        return check_auth()

    if not args.jar or not args.tag:
        fail("--jar and --tag are required")

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
    validate_loaders(MODRINTH_LOADERS)
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
                hangar_versions(game_versions),
                changelog,
                args.dry_run,
            )
        except Exception as exc:  # noqa: BLE001 - reported, not swallowed
            failures.append(f"Hangar: {exc}")
    else:
        log("::notice::HANGAR_API_KEY is not set — skipping Hangar.")

    cf_token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    cf_project = os.environ.get(CURSEFORGE_PROJECT_ENV, "").strip()
    if (cf_token and cf_project) or args.dry_run:
        try:
            publish_curseforge(
                cf_token,
                cf_project or "(unset)",
                args.jar,
                tag_version,
                game_versions,
                changelog,
                args.dry_run,
            )
        except Exception as exc:  # noqa: BLE001 - reported, not swallowed
            failures.append(f"CurseForge: {exc}")
    else:
        # Skipping is the correct outcome until the listing exists, and a release must
        # not fail because it does not. Said out loud so it cannot be mistaken for a
        # publish that happened.
        log(
            "::notice::CurseForge is not configured "
            f"(needs CURSEFORGE_TOKEN and {CURSEFORGE_PROJECT_ENV}) — skipping it."
        )

    for failure in failures:
        print(f"::error::{failure}", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
