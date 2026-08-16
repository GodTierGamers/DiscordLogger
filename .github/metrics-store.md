# bStats snapshots

Written by [`poll-metrics.yml`](../../blob/main/.github/workflows/poll-metrics.yml).
Nothing here is edited by hand, and nothing here is part of the plugin.

**This branch is data, not code.** It shares no history with `main` — it was started
as an orphan branch precisely so 48 commits a day never appear in the plugin's log.

## What is stored, and what is not

Only **pie and drilldown charts**, appended to `data/YYYY-MM.csv`.

Line charts are deliberately absent. bStats keeps their full history —
`?maxElements=100000` on `servers` returns samples going back years — so they can
be pulled complete at any time and there is nothing here to protect. Pie charts
have no time dimension whatsoever: the endpoint answers "what is true right now",
and yesterday's answer is gone. This branch exists to keep the answers bStats
throws away.

## Columns

| Column | Meaning |
|---|---|
| `polled_at` | when the poll ran, UTC, ISO-8601 |
| `chart_id` | bStats chart id, e.g. `vanish_plugin` |
| `chart_type` | `simple_pie`, `advanced_pie` or `drilldown_pie` |
| `series` | outer slice for drilldowns, empty otherwise |
| `label` | slice name |
| `value` | servers reporting that slice |

## Reading it

**The series is irregular. Read `polled_at`; never count rows.** GitHub's scheduler
is best-effort — runs drift by minutes and are sometimes skipped entirely — so gaps
are expected and a "row count per day" means nothing.

A slice missing from a poll means **no server reported it**, not that it was zero
in some measurable sense. Pie charts only carry the slices that currently exist,
so absence and zero look identical here and cannot be told apart after the fact.

Values are server counts, so every slice of a `simple_pie` should sum to the number
of servers reporting that chart. That sum is the honest denominator for any
percentage — and it is smaller than the total server count for any chart added in a
release not everyone is running yet.
