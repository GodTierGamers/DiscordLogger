# bStats history

Written by [`poll-metrics.yml`](../../blob/main/.github/workflows/poll-metrics.yml).
Nothing here is edited by hand, and nothing here is part of the plugin.

**This branch is data, not code.** It shares no history with `main` — it was started
as an orphan branch precisely so 48 commits a day never appear in the plugin's log.

## One file: `data/bstats.csv`

Every chart, every type, one running document. Splitting pies from line charts, or
months from each other, only moves the joining work to analysis time — and the join
that never happens is the one left until "later".

| Column | Meaning |
|---|---|
| `polled_at` | when the poll ran, UTC, ISO-8601 |
| `chart_id` | bStats chart id, e.g. `vanish_plugin` |
| `chart_type` | `simple_pie`, `advanced_pie`, `drilldown_pie`, `single_linechart`, map |
| `series` | outer slice for drilldowns, line name for line charts, else empty |
| `label` | slice name — or, for line charts, **the sample's own bStats timestamp** |
| `value` | servers reporting that slice |
| `servers_reporting` | servers reporting **that chart** at that poll; empty for line charts. Sum of slices for simple/drilldown pies, largest slice for advanced pies — see below |

## ⚠️ `servers_reporting` is the denominator. Use it.

**A chart is only reported by servers running the release that introduced it.**
Dividing by the total server count understates every newer chart by however many
servers have not upgraded.

Four charts shipped in **2.2.0** and are reported by *every* server:

`config_schema` · `enabled_events` · `output_mode` · `release_channel`

**All 27 others arrived in 2.3.0** and are reported only by servers running 2.3.0 or
newer: `colors_customised`, `command_filter_state`, `commands_used`,
`config_ahead_of_build`, `config_origin`, `coreprotect`, `dead_webhooks`,
`enabled_options`, `filters_modified`, `floodgate`, `lang_customised`,
`lang_keys_changed`, `lang_sections_changed`, `mc_version_by_java`,
`mc_version_by_schema`, `migrated_from`, `placeholderapi`, `proxy_mode`,
`punishment_plugin`, `queue_drops`, `rate_limit_waits`, `routed_events`,
`routing_used`, `send_failures`, `send_rate`, `vanish_plugin`, `webhook_configured`.

You do not have to remember that list — `servers_reporting` carries it in the data.
A poll where `release_channel` sums to 11 while `vanish_plugin` sums to 5 is telling
you the second figure covers five servers, not eleven.

### Advanced pies count differently

A simple or drilldown pie puts each server in exactly one slice, so its slices sum
to the server count. An **advanced pie** lets one server contribute to many slices
at once — `enabled_events` summed to **201** across 13 servers — so a sum there is a
total, not a population. `servers_reporting` therefore holds the *largest slice* for
those, which is a lower bound on how many servers reported and in practice within
one of the truth.

## How each chart type is stored

**Pie, drilldown and map charts** have no time dimension — the endpoint answers "what
is true right now" and the previous answer is gone. Every poll is recorded as a new
snapshot. This is the irrecoverable data, and the reason this branch exists.

**Line charts** carry their own bStats timestamp per sample, and bStats keeps them for
years (`?maxElements=100000` reaches back further than the plugin has existed). Only
samples not already stored are appended, keyed on that timestamp, so the first poll
backfills the whole series and later polls add roughly one row per chart. Leading
zero-padding — bStats reports "no servers" for years before the plugin existed — is
trimmed rather than stored.

## Reading it

**The series is irregular. Read `polled_at`; never count rows.** GitHub's scheduler is
best-effort — runs drift by minutes and are sometimes skipped entirely — so gaps are
expected and "rows per day" means nothing.

**A slice missing from a poll means no server reported it**, which is indistinguishable
from a true zero after the fact. Pie charts only carry the slices that currently
exist, so absence cannot be recovered later.
