#!/usr/bin/env python3
"""Merge the acceptance sweeps' results into the metrics-data store.

Each version runs on its own runner and writes its own results.csv. This gathers
them into one running document, the same shape bstats.csv already uses: one file,
every run appended, joined at analysis time rather than split across directories
that nobody ends up joining.

Screenshots are handled differently. The CSV is history and grows; the images are
the current sample and are replaced, so the branch holds one set rather than five
new pictures a run forever.
"""

import argparse
import csv
import pathlib
import re
import shutil
import sys
from datetime import datetime, timedelta, timezone

COLUMNS = [
    "run_at",        # when the sweep ran, UTC, ISO-8601
    "run_id",        # the GitHub Actions run, so a row can be traced back
    "plugin_version",
    "mc_version",
    "category",      # the group of settings swept, e.g. "filters.*"
    "key",           # the individual setting
    "verdict",       # PASS / PROBABLY_FINE / POTENTIAL_ERROR / WRONG
    "detail",        # why, in one line
]

# A verdict carries the server's last output when it is not a pass, which is the
# right thing to read in a failure and the wrong thing to put in a spreadsheet cell.
MAX_DETAIL = 300


# How long the running documents keep a row. The branch's git history keeps every
# version of these files, so a pruned row is still readable with
# `git show <sha>:data/<file>.csv` -- what this bounds is the working copy, which is
# the thing anyone actually opens, parses or downloads.
RETENTION_DAYS = 30


def within_retention(rows: list, column: str) -> list:
    """The rows stamped within RETENTION_DAYS, by the date in ``column``.

    Compared as date strings rather than parsed. The two stores stamp UTC
    differently -- ``2026-11-01T20:50:47+00:00`` here against
    ``2026-11-01T10:55:35.717518237Z`` in acceptance.csv -- and nine fractional
    digits are more than ``datetime.fromisoformat`` will take. The first ten
    characters are ``YYYY-MM-DD`` in both, and whole days are the right granularity
    for a policy measured in days.

    **A row whose stamp is too short to hold a date is kept.** Dropping what cannot
    be read would delete exactly the rows a writer bug produced, which are the ones
    worth still having.
    """
    cutoff = (datetime.now(timezone.utc).date() - timedelta(days=RETENTION_DAYS)).isoformat()
    return [r for r in rows
            if len(str(r.get(column, ""))) < 10 or str(r.get(column))[:10] >= cutoff]


def one_line(text):
    """Collapse a multi-line detail into something a CSV cell can hold."""
    flattened = re.sub(r"\s+", " ", (text or "")).strip()
    if len(flattened) > MAX_DETAIL:
        flattened = flattened[: MAX_DETAIL - 1] + "…"
    return flattened


def rows_from(results_csv, run_id, plugin_version):
    with results_csv.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            # A sweep that wrote no header wrote no rows either.
            if not row.get("key"):
                continue
            yield {
                "run_at": row.get("run_at", ""),
                "run_id": run_id,
                "plugin_version": plugin_version,
                "mc_version": row.get("version", ""),
                "category": row.get("category", ""),
                "key": row["key"],
                "verdict": row.get("verdict", ""),
                "detail": one_line(row.get("detail", "")),
            }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", required=True, type=pathlib.Path)
    ap.add_argument("--store", required=True, type=pathlib.Path)
    ap.add_argument("--run-id", default="")
    ap.add_argument("--plugin-version", default="")
    args = ap.parse_args()

    found = sorted(args.artifacts.rglob("results.csv"))
    if not found:
        # Every version failing to produce results is worth failing on: a silent
        # empty append would read as "the sweep ran and found nothing to say".
        print("no results.csv in any artifact", file=sys.stderr)
        return 1

    collected = []
    for results in found:
        collected.extend(rows_from(results, args.run_id, args.plugin_version))
    if not collected:
        print("results files were present but held no rows", file=sys.stderr)
        return 1

    out = args.store / "data" / "acceptance.csv"
    out.parent.mkdir(parents=True, exist_ok=True)

    # Rewritten rather than appended, because pruning has to read what is there.
    # `extrasaction="ignore"` and `restval` are what make an older file with a
    # different column set survive this instead of raising on it.
    kept = []
    if out.exists():
        with out.open(newline="", encoding="utf-8") as f:
            existing = list(csv.DictReader(f))
        kept = within_retention(existing, "run_at")
        if len(existing) > len(kept):
            print(f"dropped {len(existing) - len(kept)} row(s) older than "
                  f"{RETENTION_DAYS} days")
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, extrasaction="ignore", restval="")
        w.writeheader()
        w.writerows(kept + collected)

    # The sample, replaced rather than accumulated.
    shots = args.store / "acceptance" / "screenshots"
    if shots.exists():
        shutil.rmtree(shots)
    copied = 0
    for png in sorted(args.artifacts.rglob("sample-*.png")):
        version = png.parent.name
        target = shots / version
        target.mkdir(parents=True, exist_ok=True)
        shutil.copy2(png, target / png.name)
        copied += 1

    versions = sorted({r["mc_version"] for r in collected})
    verdicts = {}
    for r in collected:
        verdicts[r["verdict"]] = verdicts.get(r["verdict"], 0) + 1
    print(f"{len(collected)} rows from {len(versions)} versions: {', '.join(versions)}")
    print(f"verdicts: {verdicts}")
    print(f"{copied} screenshots")
    return 0


if __name__ == "__main__":
    sys.exit(main())
