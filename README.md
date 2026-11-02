# DiscordLogger data store

Written by [`poll-metrics.yml`](../../blob/main/.github/workflows/poll-metrics.yml)
and [`acceptance.yml`](../../blob/main/.github/workflows/acceptance.yml).
Nothing here is edited by hand, and nothing here is part of the plugin.

**This branch is data, not code.** It shares no history with `main` — it was started
as an orphan branch precisely so the data commits (roughly nine a day from the poll,
plus one per acceptance run) never appear in the plugin's log.

**Both CSVs keep 30 days.** See *Retention* at the end: the files are a window, and
this branch's git history is the archive.

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

Embeds rendered as Discord shows them, drawn from the payloads that run actually
produced. Named `sample-<verdict>-<key>.png`, so the ones worth opening stand out in a
listing.

Two different rules, on purpose:

| | How many |
|---|---|
| `PASS` | **five per version, at random** |
| everything else | **all of them** (capped at 40, which a run worth reading never reaches) |

A passing case is illustrative — five show what the plugin looks like when it works, and
the sixth says nothing the fifth did not. Anything that did not pass is evidence, and
evidence is not something to sample: the picture of the embed that came out wrong is the
quickest way to see what is wrong with it.

**A result with no picture had no payload.** Those are the cases where nothing arrived at
all, so there is nothing to draw; the CSV row is the whole record.

**Replaced every run, not accumulated.** The CSV is the history; these are the current
sample, and keeping every run's would add images a night forever.

# Retention — both CSVs hold the last 30 days

`RETENTION_DAYS = 30` in `scripts/export-metrics.py` and
`scripts/collect-acceptance.py`. Each writer already rewrites its whole file, so the
prune costs nothing extra and happens on the same commit as the append. Rows are
compared on the first ten characters of their stamp, so the cut lands on a whole day.

**Nothing is actually lost, and that is what makes 30 days safe.** Every version of
both files is still in this branch's history:

```bash
git log --oneline -- data/bstats.csv          # every poll that ever wrote
git show <sha>:data/acceptance.csv            # the file as it stood then
```

The window bounds the thing people interact with — the file they open, parse or
download — rather than the record. `acceptance.csv` was 8.3 MB and 57,000 rows across
70 runs before this existed, growing about 900 rows a day with no ceiling, and every
run rewrote all of it.

**Why 30 days is the right window for these two specifically.** The pie snapshots are
the only copy of what bStats discards, but the question they answer — what are servers
running now, and what changed this month — is answered by recent rows; anything older
is a historical question, which is a `git show` away. For the sweep, a two-month-old
`PASS` on a setting that has passed every night since says nothing, while a regression
shows up in the next run.

`acceptance/screenshots/` needs no retention: it is replaced each run rather than
appended, so it holds one sample set (six images per Minecraft version) and not five
new pictures a night forever.
