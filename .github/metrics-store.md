# bStats history

Written by [`poll-metrics.yml`](../../blob/main/.github/workflows/poll-metrics.yml).
Nothing here is edited by hand, and nothing here is part of the plugin.

**This branch is data, not code.** It shares no history with `main` — it was started
as an orphan branch precisely so 48 commits a day never appear in the plugin's log.

## One row per check: `data/bstats.csv`

Every chart, every type, one running document. `polled_at` first, then one column per
slice.

Charting adoption is selecting a column against `polled_at` — no pivot, no reshaping,
no join. That is the whole reason for this shape: the store began one-row-per-slice,
which is what a pivot table wants and not what a person wants, and every question
needed the pivot built before it could be asked.

| Column | Example | Holds |
|---|---|---|
| `polled_at` | `2026-08-17T22:38:23+00:00` | when the poll ran, UTC |
| `<chart>.<label>` | `vanish_plugin.Essentials` | servers reporting that slice |
| `<chart>.<series>.<label>` | `mc_version_by_java.26.2.25` | drilldown, outer then inner |
| `<chart>.#servers` | `vanish_plugin.#servers` | servers reporting **that chart** |
| `<chart>` | `servers`, `players` | a line chart's value at that poll |
| `downloads.<source>` | `downloads.modrinth` | lifetime downloads from that source |

## Download counts do not partition

Three sources, and they overlap — which matters before reading them:

- **`downloads.modrinth`** — Modrinth hosts its own copy, so this is downloads of that copy.
- **`downloads.hangar`** — Hangar registers versions by `externalUrl` pointing at the GitHub asset, so a Hangar download *also* increments GitHub's counter. Hangar records its own too, so both are real and **the overlap is Hangar's figure**.
- **`downloads.github`** — direct downloads *plus* Hangar click-throughs. It cannot be separated further.

Pre-releases are excluded from the GitHub figure: nightlies are a different audience and counting them would inflate it against two sources that never see one.

The figure worth watching is not any single source but the **ratio against `servers`**. Downloads are lifetime and cumulative; `servers` is how many are running right now.

## ⚠️ Two things that will mislead you

**Divide by `#servers`, never by the server count.** A chart is only reported by
servers running the release that introduced it. Four charts shipped in **2.2.0** and
are reported by everyone — `config_schema`, `enabled_events`, `output_mode`,
`release_channel` — while everything else arrived in **2.3.0** and is reported only by
servers running it. A poll where `release_channel.#servers` is 10 and
`vanish_plugin.#servers` is 5 is telling you the second figure covers half as many
servers, not that adoption collapsed.

For an **advanced pie** (`enabled_events`, `enabled_options`) one server contributes to
many slices at once, so `#servers` holds the *largest slice* rather than the sum —
`enabled_events` summed to 201 across 13 servers, which is a total, not a population.

**An empty cell means the slice was absent, which is not zero.** Pie charts only return
slices that currently exist, so a country with no servers is simply not mentioned, and
absence cannot be told from a true zero afterwards.

## Line charts

They hold their latest value at each poll, under the bare chart id. bStats keeps their
full history — `?maxElements=100000` reaches back further than the plugin has existed —
so the fine-grained series is always recoverable and a poll row only has to say where
the line was. The pie snapshots are the part that is gone forever if not captured.

## Reading it

**The series is irregular. Read `polled_at`; never count rows.** GitHub's scheduler is
best-effort — observed firing minutes are spread across `:01`–`:59` with gaps of 16 to
51 minutes against a `:15`/`:45` schedule — so "rows per day" means nothing.

New slices add columns over time (a new country, a new Java version), so the file is
rewritten each poll. At one row per poll that is thousands of rows a year.
