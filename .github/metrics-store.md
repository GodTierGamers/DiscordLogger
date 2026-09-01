# DiscordLogger data store

Written by [`poll-metrics.yml`](../../blob/main/.github/workflows/poll-metrics.yml)
and [`acceptance.yml`](../../blob/main/.github/workflows/acceptance.yml).
Nothing here is edited by hand, and nothing here is part of the plugin.

**This branch is data, not code.** It shares no history with `main` — it was started
as an orphan branch precisely so 48 commits a day never appear in the plugin's log.

## `data/bstats.csv` — one row per check

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


# `data/acceptance.csv` — what the plugin does on a real server

Written by the `report` job in `acceptance.yml`, once per run. Where `bstats.csv`
records what other people's servers are doing, this records what the shipped JAR did
when the suite drove every setting in `config.yml`, and every line in `lang.yml`, on
five Minecraft versions.

One row per setting per version per run.

| Column | Meaning |
|---|---|
| `run_at` | when that sweep ran, UTC, ISO-8601 |
| `run_id` | the Actions run, so a row can be traced back to its logs |
| `plugin_version` | the version of the JAR under test |
| `mc_version` | the Minecraft version it ran against |
| `category` | the group swept, e.g. `filters.*` |
| `key` | the individual setting, e.g. `filters.respect_vanish` |
| `verdict` | see below |
| `detail` | why, flattened to one line and capped at 300 characters |

## The four verdicts

A binary pass/fail is the wrong shape for output made of sentences, so there are four.

| Verdict | Meaning |
|---|---|
| `PASS` | did exactly what the setting says |
| `PROBABLY_FINE` | differs only in whitespace or case, or in something that legitimately varies by version |
| `POTENTIAL_ERROR` | needs a person: wording drifted, a stray tag or placeholder reached Discord, **or the harness could not set the scene** |
| `WRONG` | contradicts the specification. Only this fails a run |

## ⚠️ `POTENTIAL_ERROR` is not always the plugin's fault

It is also what a case reports when it could not put the server in the state it needed
— a ban that never landed, an event whose control did not post. That is deliberate.
The alternative is a suite that reads "no message arrived" as a defect, which it did
six times while being built, every time naming the plugin for something the harness
had failed to do.

**Read the `detail` before treating a `POTENTIAL_ERROR` as a bug.** It says which of
the two happened.

Two settings can never reach `PASS` here and say so in their detail: a kick needs a
genuinely connected client, and the Bedrock indicator needs Floodgate installed.

## `acceptance/screenshots/`

Five embeds per version, drawn from the payloads that run actually produced and chosen
at random, rendered as Discord shows them.

**Replaced every run, not accumulated.** The CSV is the history; these are the current
sample, and keeping every run's would add twenty-five images a night forever.
