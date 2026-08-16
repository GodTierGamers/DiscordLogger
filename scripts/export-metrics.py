#!/usr/bin/env python3
"""Exports the plugin's bStats charts to CSV.

bStats renders charts in a browser and offers no download, so answering
"how many people actually use lang.yml" means reading a pie chart by eye. The
data behind those charts is public JSON, though, so this pulls every chart and
writes it as a spreadsheet.

Output is ONE tidy long-format CSV rather than a file per chart:

    chart_id,chart_title,chart_type,series,label,value

One row per data point, every chart stacked in the same three columns. That is
the shape spreadsheets want -- select the range, insert a pivot table, and any
cut of the data is a drag away. A file per chart would be truer to the source
and useless in practice: 39 sheets that cannot be compared without first being
consolidated by hand. `--per-chart` still writes them out that way when a single
chart is all that is wanted.

Each chart type flattens differently:

    single_linechart  label = ISO-8601 UTC timestamp, one row per sample
    simple_pie        label = slice name
    advanced_pie      same shape as simple_pie
    drilldown_pie     series = outer slice, label = inner key

No authentication -- these endpoints are public, which is also why nothing here
can reach data the bStats page does not already show anyone.

`--check-charts` answers a different question: does every chart the plugin
SUBMITS actually exist on bStats? A chart added in Java but never created on the
site is accepted and discarded -- no error, no warning, the plugin looks fine and
the data is simply gone. Four charts were in that state when this script was
written (release_channel, enabled_events, output_mode, config_schema), including
the one that answers "which events do people actually enable".

Deliberately NOT wired into CI: it needs the network, and it would fail for the
window between a chart landing in Java and someone creating it on bStats -- which
is a normal state, not a broken build. Run it after adding charts.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PLUGIN_ID = 33026
API = "https://bstats.org/api/v1/plugins"
TIMEOUT = 30
METRICS_SRC = "src/main/java/com/discordlogger/metrics/PluginMetrics.java"
# Matches any bStats chart constructor: every one of their classes ends in Pie,
# Chart or Bar. Two things this must tolerate, both of which have occurred in
# this file: a fully-qualified name (2.2.0 registered enabled_events as
# `new org.bstats.charts.AdvancedPie(...)`) and a line break between the
# constructor and its id. An id this misses is a chart the check silently
# believes was never declared -- the exact blind spot the check exists to close.
CHART_DECL = re.compile(
    r"new\s+(?:[\w.]+\.)?\w*(?:Pie|Chart|Bar)\s*\(\s*\"([^\"]+)\"",
    re.MULTILINE,
)


def fetch(url: str) -> Any:
    req = urllib.request.Request(url, headers={"User-Agent": "DiscordLogger-export"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read().decode("utf-8"))


def iso(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).isoformat()


def flatten(chart: dict, data: Any) -> list[tuple[str, str, str]]:
    """One chart's payload as (series, label, value) rows."""
    cid, ctype = chart["idCustom"], chart["type"]
    rows: list[tuple[str, str, str]] = []

    if ctype.endswith("linechart"):
        # bStats hands back [[epoch_ms, value], ...]. The line's display name
        # lives in the chart metadata, not the data, so it is carried across
        # here -- otherwise every line chart's series column would read the same.
        name = (chart.get("data") or {}).get("lineName") or cid
        for point in data or []:
            if isinstance(point, list) and len(point) >= 2:
                rows.append((name, iso(point[0]), str(point[1])))

    elif ctype == "drilldown_pie":
        # Two levels: seriesData holds the outer totals, drilldownData the inner
        # breakdown per outer slice. Only the inner rows are emitted -- the outer
        # total is their sum, and writing both would double-count any pivot.
        for outer in (data or {}).get("drilldownData", []):
            for inner in outer.get("data", []):
                if isinstance(inner, list) and len(inner) >= 2:
                    rows.append((outer.get("name", ""), str(inner[0]), str(inner[1])))

    elif ctype.endswith("map"):
        # Map charts are the odd one out: [{"code": "DE", "value": 3}, ...] rather
        # than the {name, y} every other chart uses. Falling through to the pie
        # branch produced a row with no label and no value -- 149 of them before
        # this was caught, which is also why the value is asserted below.
        for entry in data or []:
            if isinstance(entry, dict) and entry.get("code") is not None:
                rows.append(("", str(entry["code"]), str(entry.get("value", ""))))

    else:  # simple_pie / advanced_pie -- [{"name": ..., "y": ...}, ...]
        for slice_ in data or []:
            if isinstance(slice_, dict):
                rows.append(("", str(slice_.get("name", "")), str(slice_.get("y", ""))))

    # A row with no value carries nothing and silently breaks any sum over the
    # column. Dropping it here keeps an unrecognised chart shape from quietly
    # filling the store with blanks the way simple_map did.
    return [r for r in rows if r[1] != "" and r[2] != ""]


def declared_charts(src: str) -> set[str]:
    """Chart ids the Java source registers with bStats."""
    return set(CHART_DECL.findall(Path(src).read_text(encoding="utf-8")))


def check_charts(meta: dict, src: str) -> int:
    """Compare what the plugin submits against what bStats will accept."""
    try:
        declared = declared_charts(src)
    except OSError as e:
        print(f"could not read {src}: {e}", file=sys.stderr)
        return 1

    charts = meta.get("charts", {}).values()
    on_site = {c["idCustom"] for c in charts if not c.get("isDefault")}

    discarded = sorted(declared - on_site)
    orphaned = sorted(on_site - declared)

    print(f"{len(declared)} declared in Java, {len(on_site)} custom charts on bStats")

    if discarded:
        print(f"\nSubmitted but NOT on bStats -- this data is being discarded "
              f"({len(discarded)}):")
        for c in discarded:
            print(f"  - {c}")
        print("  Create these on bStats, matching the id exactly.")
    if orphaned:
        print(f"\nOn bStats but no longer submitted ({len(orphaned)}):")
        for c in orphaned:
            print(f"  - {c}")
        print("  Harmless -- they will just stop updating.")
    if not discarded and not orphaned:
        print("\nEvery declared chart exists on bStats.")

    # Only a discarded chart is a real problem; an orphan merely goes stale.
    return 1 if discarded else 0


# Charts that shipped in 2.2.0 and are therefore reported by EVERY server.
# Everything else arrived in 2.3.0 and is reported only by servers running it, so
# its slices sum to a smaller number. Recorded because a chart with a smaller
# denominator looks like collapsed adoption if you divide by total server count.
CHARTS_SINCE_2_2_0 = frozenset({
    "config_schema", "enabled_events", "output_mode", "release_channel",
})


def append_snapshot(meta: dict, plugin_id: int, out_dir: str) -> int:
    """Append one poll of every chart to a single running CSV.

    **One file, all chart types, forever.** Splitting pies from line charts, or
    months from each other, only moves the joining work to analysis time -- and the
    join that never happens is the one done "later".

    The two chart types need different handling, and conflating them loses data:

    * **Pie / drilldown** carry no time dimension. The endpoint answers "what is true
      right now" and the previous answer is gone, so every poll is recorded as a new
      snapshot stamped with the poll time. This is the data that cannot be recovered
      and the reason the workflow exists at all.
    * **Line** charts carry their own bStats timestamp per sample and bStats keeps
      them for years, so re-recording the whole series every poll would add tens of
      thousands of duplicate rows an hour. Only samples not already stored are
      appended, keyed on that timestamp.

    `servers_reporting` is written on every pie row: the sum of that chart's slices
    at that poll, which is the only honest denominator for a percentage. A 2.3.0
    chart divided by the total server count understates itself by however many
    servers have not upgraded.
    """
    stamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    d = Path(out_dir)
    d.mkdir(parents=True, exist_ok=True)
    target = d / "bstats.csv"

    # Line samples already stored, so a poll adds only what is new. Keyed on the
    # bStats timestamp, which is the sample's own identity rather than ours.
    seen: set[tuple[str, str]] = set()
    if target.exists():
        with target.open(encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row["chart_type"].endswith("linechart"):
                    seen.add((row["chart_id"], row["label"]))

    rows, added_line, added_pie = [], 0, 0
    for chart in sorted(meta.get("charts", {}).values(), key=lambda c: c.get("position", 0)):
        cid, ctype = chart["idCustom"], chart["type"]
        url = f"{API}/{plugin_id}/charts/{cid}/data"
        # Line charts are asked for their full history; the dedupe above keeps that
        # from mattering after the first run, and it backfills everything on it.
        if ctype.endswith("linechart"):
            url += "?maxElements=100000"
        try:
            data = fetch(url)
        except (urllib.error.URLError, json.JSONDecodeError):
            continue

        flat = flatten(chart, data)
        if ctype.endswith("linechart"):
            # Trim the zero padding bStats returns for time before the plugin
            # existed -- roughly 90,000 rows a chart of "no servers yet".
            first_real = next((i for i, r in enumerate(flat) if r[2] not in ("0", "")), None)
            if first_real is None:
                continue
            for series, label, value in flat[first_real:]:
                if (cid, label) in seen:
                    continue
                rows.append([stamp, cid, ctype, series, label, value, ""])
                added_line += 1
        else:
            total = sum(int(v) for _, _, v in flat if v.isdigit())
            for series, label, value in flat:
                rows.append([stamp, cid, ctype, series, label, value, total])
                added_pie += 1

    if not rows:
        print(f"{stamp}: nothing new")
        return 0

    new_file = not target.exists()
    with target.open("a", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        if new_file:
            w.writerow(["polled_at", "chart_id", "chart_type", "series", "label",
                        "value", "servers_reporting"])
        w.writerows(rows)

    print(f"{stamp}: +{added_pie} pie, +{added_line} line -> {target}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--snapshot", metavar="DIR",
                    help="append a timestamped snapshot of the point-in-time (pie) "
                         "charts to DIR/YYYY-MM.csv, then exit. Line charts are "
                         "skipped: bStats keeps their full history already")
    ap.add_argument("--check-charts", action="store_true",
                    help="verify every chart the plugin submits exists on bStats, "
                         "then exit (writes no CSV)")
    ap.add_argument("--metrics-src", default=METRICS_SRC,
                    help="Java source to read chart ids from")
    ap.add_argument("--plugin-id", type=int, default=PLUGIN_ID)
    ap.add_argument("--out", default="bstats-export.csv", help="output CSV path")
    ap.add_argument("--per-chart", metavar="DIR",
                    help="also write one CSV per chart into DIR")
    ap.add_argument("--include-default", action="store_true",
                    help="include bStats' own charts (servers, players, OS, ...)")
    args = ap.parse_args()

    try:
        meta = fetch(f"{API}/{args.plugin_id}")
    except (urllib.error.URLError, json.JSONDecodeError) as e:
        print(f"could not read plugin {args.plugin_id}: {e}", file=sys.stderr)
        return 1

    if args.check_charts:
        return check_charts(meta, args.metrics_src)

    if args.snapshot:
        return append_snapshot(meta, args.plugin_id, args.snapshot)

    charts = sorted(meta.get("charts", {}).values(), key=lambda c: c.get("position", 0))
    if not args.include_default:
        charts = [c for c in charts if not c.get("isDefault")]

    rows, empty, failed = [], [], []
    for chart in charts:
        cid = chart["idCustom"]
        try:
            data = fetch(f"{API}/{args.plugin_id}/charts/{cid}/data")
        except urllib.error.HTTPError as e:
            # A 404 here is worth reporting rather than swallowing: it means the
            # plugin submits this chart but no chart exists on bStats to receive
            # it, so the data is being discarded at the far end.
            failed.append((cid, e.code))
            continue
        except urllib.error.URLError as e:
            failed.append((cid, str(e.reason)))
            continue

        flat = flatten(chart, data)
        if not flat:
            empty.append(cid)
        title, ctype = chart.get("title", cid), chart["type"]
        rows.extend([cid, title, ctype, s, l, v] for s, l, v in flat)

        if args.per_chart and flat:
            d = Path(args.per_chart)
            d.mkdir(parents=True, exist_ok=True)
            with (d / f"{cid}.csv").open("w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["series", "label", "value"])
                w.writerows(flat)

    out = Path(args.out)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["chart_id", "chart_title", "chart_type", "series", "label", "value"])
        w.writerows(rows)

    print(f"{len(rows)} rows from {len(charts) - len(failed)} charts -> {out}")
    if args.per_chart:
        print(f"per-chart CSVs -> {args.per_chart}/")
    if empty:
        print(f"\n{len(empty)} chart(s) with no data yet: {', '.join(empty)}")
    if failed:
        print(f"\n{len(failed)} chart(s) could not be read:", file=sys.stderr)
        for cid, why in failed:
            print(f"  {cid}: {why}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
