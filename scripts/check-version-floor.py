#!/usr/bin/env python3
"""Refuses a release version lower than one this repo has already proposed.

``Release-As:`` is absolute, not a floor. A footer saying 2.6.1 wins even when the
commits since the last release already justify 2.7.0 -- and that is not theoretical:
accumulated ``feat:`` commits had the Release PR at 2.7.0, then a ``fix:`` carrying
``Release-As: 2.6.1`` merged and dragged it back down. Nothing complained. The number
a release ships under is the one thing a user cannot be asked to reason about, and a
version that goes backwards breaks every update check that compares two of them.

The floor is the highest version this repository has ever put its name to:

* the released version in ``.release-please-manifest.json`` -- a new release must be
  strictly greater, and
* the base version of every tag, nightlies included. ``v2.7.0-BETA.1`` means 2.7.0 was
  built and published, so shipping 2.6.2 afterwards would renumber history. Nightly
  tags are never pruned, which is what makes them a durable high-water mark.

Run with ``--selftest`` to check the comparison itself, which is what CI does.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

VERSION = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)")


def parse(text: str) -> tuple[int, int, int] | None:
    """The numeric version at the start of a tag or title, ignoring any -BETA.n."""
    m = VERSION.match(text.strip())
    return (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None


def show(v: tuple[int, int, int]) -> str:
    return "%d.%d.%d" % v


def floor_of(released: str, tags: list[str]) -> tuple[tuple[int, int, int], str]:
    """The lowest version a new release may carry, and what set it."""
    best, why = (0, 0, 0), "nothing"

    rel = parse(released) if released else None
    if rel:
        # Strictly greater than what has shipped: bump the patch to express that.
        best, why = (rel[0], rel[1], rel[2] + 1), "the released version %s" % show(rel)

    for tag in tags:
        v = parse(tag)
        if v and v > best:
            best, why = v, "tag %s" % tag.strip()
    return best, why


def git_tags() -> list[str]:
    try:
        out = subprocess.run(["git", "tag", "--list", "v*"], check=True,
                             capture_output=True, text=True).stdout
    except (subprocess.CalledProcessError, OSError):
        return []
    return [t for t in out.splitlines() if t.strip()]


def released_version() -> str:
    try:
        with open(".release-please-manifest.json", encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        return ""
    return str(data.get(".", ""))


def selftest() -> int:
    cases = [
        # released, tags, proposed, ok?
        ("2.6.0", [], "2.6.1", True),
        ("2.6.0", [], "2.6.0", False),          # must move
        ("2.6.0", [], "2.5.9", False),          # backwards
        ("2.6.0", ["v2.6.1-BETA.7"], "2.7.0", True),
        # the real case: nightlies already published 2.7.0, then a footer says 2.6.1
        ("2.6.0", ["v2.7.0-BETA.1"], "2.6.1", False),
        ("2.6.0", ["v2.7.0-BETA.1"], "2.7.0", True),
        ("2.6.0", ["v2.6.0", "v2.6.1-BETA.2"], "2.6.1", True),
    ]
    bad = 0
    for released, tags, proposed, ok in cases:
        fl, _ = floor_of(released, tags)
        got = parse(proposed) >= fl
        if got != ok:
            print("SELFTEST FAIL: released=%s tags=%s proposed=%s -> %s, wanted %s"
                  % (released, tags, proposed, got, ok))
            bad += 1
    print("selftest: %d case(s), %d failure(s)" % (len(cases), bad))
    return 1 if bad else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--proposed", help="the version a release would carry, e.g. 2.7.0")
    ap.add_argument("--from-pr-json", action="store_true",
                    help="take it from release-please's PR output in $RP_PR")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    proposed_text = args.proposed or ""
    if args.from_pr_json:
        raw = os.environ.get("RP_PR", "").strip()
        if not raw:
            print("No Release PR this run; nothing to check.")
            return 0
        title = json.loads(raw).get("title", "")
        m = re.search(r"release\s+v?(\d+\.\d+\.\d+)", title, re.I)
        if not m:
            print("Could not read a version out of the Release PR title: %r" % title)
            return 1
        proposed_text = m.group(1)

    proposed = parse(proposed_text)
    if not proposed:
        print("Not a version: %r" % proposed_text)
        return 1

    fl, why = floor_of(released_version(), git_tags())
    if proposed >= fl:
        print("OK: %s is at or above the floor %s (set by %s)."
              % (show(proposed), show(fl), why))
        return 0

    print("::error::Release version %s is BELOW %s, which this repo has already "
          "proposed or shipped (floor set by %s). A version must never go backwards: "
          "update checks compare versions, so a lower number reads as 'no update' on "
          "every server already running the higher one. If this came from a "
          "'Release-As:' footer, remember it is absolute rather than a minimum -- add "
          "a newer commit with 'Release-As: %s' or higher."
          % (show(proposed), show(fl), why, show(fl)))
    return 1


if __name__ == "__main__":
    sys.exit(main())
