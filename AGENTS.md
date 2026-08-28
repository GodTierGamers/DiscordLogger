# AGENTS.md — DiscordLogger

**This file is written for AI coding agents working in this repository. It is not the human-facing docs.** If you're a person, read [ARCHITECTURE.md](ARCHITECTURE.md) (how the project works, narrative form) and [CONTRIBUTING.md](CONTRIBUTING.md) (how to submit a PR) instead — they cover the same ground with less density and no assumption you'll retain all of it in one pass. This file is optimized for the opposite: maximum accurate detail per token, safe to re-read in full every session, safe to grep.

Everything below was verified against the actual source at the time of writing; when in doubt, the code wins — and if you find this file wrong, fix it in the same PR.

## What this project is

**DiscordLogger** is a Minecraft **Paper** server plugin (Java 17 bytecode) that posts server events to a Discord channel via **webhooks** — either as rich embeds (per-event colors, player-head thumbnails, timestamps) or as plain Markdown text. It ships two versioned files, `config.yml` and `lang.yml`, both of which auto-migrate between schema versions, plus a channel-aware update checker and a companion **Jekyll website** (in `docs/`) hosted on GitHub Pages at `https://discordlogger.godtiergamers.xyz` that includes an interactive config generator.

- **Current plugin version:** tracked by `pom.xml` / `.release-please-manifest.json` — never hand-edit either, see **Releases** below.
- **Current config schema:** **v11**, open and unpublished (trailer comment in `src/main/resources/config.yml`, e.g. `# CONFIG VERSION V11, SHIPPED WITH v2.3.0 (x-release-please-version)`). v9 and v10 are published and frozen; v11 freezes when 2.3.1 ships.
- **Paper API:** three deliberately different numbers, all in `pom.xml` — see *Three floors* under Metrics. The short version: **compile against the oldest supported API, never the newest**, since compiling against the newest and declaring an older `api-version` is how a plugin loads and then dies on `NoSuchMethodError`.
- **GitHub:** `GodTierGamers/DiscordLogger`

## Working agreement (binding — not suggestions)

1. **Trunk-based**: branch off `main` (`feat/<name>`, `fix/<name>`), PR into `main`. Never commit directly to `main`.
2. **Conventional Commit PR titles** (`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` / `ci:` / `test:` …) — `lint-pr.yml` rejects anything else. The title becomes the changelog entry verbatim. Squash-merge.

   **Pick the type by what changes in the JAR, not by how much work it was.** Something under `scripts/`, `.github/` or `docs/` that never reaches a user's server is `chore:`, `ci:` or `docs:` however substantial it is — a new maintainer tool is not a feature of the plugin. The type no longer moves the version (see *Releasing* below), so this is now about the changelog reading honestly rather than about the number: a reader scanning "✨ Features" should find things they can actually use.
3. **Verify before PR**: `mvn -B -ntp clean package` must pass — that runs the test suite, which gates CI. For listener/config changes, exercise on a real Paper server when practical.
4. **Config changes travel in lockstep**: `config.yml` + listener + `EventRegistry` + `docs/assets/configs/v*/options.json` + `config.template.yml` + `docs/assets/configs/v*/config.yml` + the embedded block in `docs/config/v*/index.md` (see "Config file dictionary" below — four places, not two) in the same PR; run `python3 scripts/validate-config-generator.py` locally (CI runs it too).
5. **Merging is a maintainer's call — you open PRs and stop.** Do not merge without an explicit, current instruction. This goes double for the **release-please Release PR**: merging it *is* the release. It exists to accumulate merged features (the batching role the old `dev` branch served) and stays open until the maintainer decides a feature compilation is ready to ship. Never merge it on general "go" energy from other work — it requires its own fresh "ship it," every single time, no exceptions, even if you were just told to merge something else.
6. **Never hand-edit** `.release-please-manifest.json`, `CHANGELOG.md`, or `pom.xml`'s `<version>` — those belong to release-please.
7. Keep this file current: workflow or architecture changes update AGENTS.md in the same PR. Update [ARCHITECTURE.md](ARCHITECTURE.md) too if the change is the kind a human contributor would want to know about narratively, not just as a reference fact.
8. **AI attribution policy**: the README's *AI Disclosure* section is the single, project-level statement of AI involvement. Do **not** add per-commit or per-PR attribution — no `Co-Authored-By: Claude/AI` trailers, no "Generated with …" footers in commits or PR descriptions, no AI credits in code comments.
9. **TODO.md is maintainer-owned** — see the protocol immediately below. Never write to it uninvited.

### TODO.md protocol

[TODO.md](TODO.md) is a reminder list owned by the maintainer, not a general backlog, not a scratchpad, and not a place to park your own observations.

**Adding:** only when the maintainer *explicitly* asks — "remind me to X", "add X to TODO.md", or clearly equivalent. Noticing something worth doing is **not** grounds to add it; mention it in conversation instead and let them decide. If they don't say to write it down, it doesn't go in the file.

**Removing:** the moment an item is genuinely done, **delete the line entirely**. No strikethrough, no "Completed" section, no dated archive — the file must only ever show what is still outstanding. Do this in the same PR that completes the work, so the file can't drift out of sync with reality.

**Consequence to respect:** because entries are added only on request and deleted on completion, an empty TODO.md means "nothing outstanding" and can be trusted as such. Self-populating it — even with genuinely good ideas — destroys that guarantee and makes the file worthless. Don't.

## Version values — pom.xml is the single source of truth

**Never hand-type a Java version, Paper version or api-version anywhere except `pom.xml`.** These properties feed everything else:

| Property | Current | Meaning |
|---|---|---|
| `<version>` | — | the plugin version — **release-please owns it**, never hand-edit |
| `<maven.compiler.release>` | `17` | Java the plugin is built for |
| `<dl.api.version>` | `1.19` | minimum Paper; becomes `plugin.yml`'s `api-version` |
| `<dl.compat.floor>` | `1.19-R0.1-SNAPSHOT` | the oldest API the build promises to compile against; CI's `compat-floor` job enforces it |
| `<paper.api.version>` | `1.19.4-R0.1-SNAPSHOT` | what the normal build and the **test suite** compile against |
| `<dl.game.versions>` | 28 entries, `1.19`–`26.2` | the supported range; the listings advertise it, and the prose + badge in README/CONTRIBUTING are derived from its first and last entries |

The sync script derives three values no property owns: **`plugin`** (the released version, from `<version>`), **`schema`** (the config schema, read from `config.yml`'s trailer), and **`paper_display`** (how Paper is written in prose, e.g. `1.19 – 26.2`, built from the first and last of `<dl.game.versions>`). Docs examples of the config trailer use these, so they can't go stale when a release ships or the schema moves. `paper_display` is derived rather than hand-set because a standalone property would drift the moment a Minecraft version is added — the listings would advertise the new ceiling while every badge and requirements table still named the old one. (A `<dl.paper.display>` property did exist and has been removed; if you see it referenced, that's stale.)

How each destination gets its value — all automatic, none needs remembering:

- **`plugin.yml`, `build-info.properties`** — Maven resource filtering resolves `${project.version}` / `${dl.api.version}` at package time.
- **CI workflows** — each reads `<maven.compiler.release>` out of `pom.xml` at runtime into a step output, so the JDK installed always matches the compile target. No `java-version:` literal exists anywhere.
- **README.md, CONTRIBUTING.md** — values sit between `<!-- dl:sync:KEY -->…<!-- /dl:sync -->` markers, rewritten by `scripts/sync-versions.py`. Edit the surrounding prose freely; never the value between markers.
  - **Inline markers only work in prose.** They cannot go inside Markdown syntax — an HTML comment within a `![badge](url)` breaks the image and GitHub renders the raw `![Java](` as text. Anything of that shape uses a **block** marker instead: `<!-- dl:sync-block:NAME -->` on its own line, with the whole block regenerated from a template in the script (`BLOCK_TEMPLATES`). The shields.io badges use this.
- **Docs pages** — read `{{ site.data.versions.* }}` from `docs/_data/versions.yml`, which the same script generates. That file is generated: **do not edit it**. Liquid resolves inside fenced code blocks too, which is how the config-trailer examples stay current.
- **Anything showing the *latest release*** on the site (`data-dl-latest`, the downloads list, the generator's version picker) — reads the GitHub releases API live, so it covers nightlies and stable without any file to update.

`sync-versions.yml` runs the script on every push to `main` that touches `pom.xml` — including release-please's own release commits — and commits the result. So bumping a version in `pom.xml` is genuinely the only edit required.

To add a new synced location: wrap the value in `<!-- dl:sync:KEY -->` markers (Markdown) or reference `site.data.versions.KEY` (Jekyll). Unknown keys make the script fail loudly rather than silently skip.

## Build & test

**Tests exist and gate CI** (JUnit 5 + surefire; `ci.yml` runs `mvn clean package` with tests on). `mvn test` runs them in ~10s — currently 29 classes, 264 tests.

They are concentrated where a silent failure is most expensive rather than spread for coverage: five classes on `ConfigMigrator` alone (decision table, step chain, path resolution, list splicing, single-line rewrites), the rest on filters, lang loading, death causes, routing, webhook redaction, metrics bucketing and Bedrock detection. Several exist because the bug they describe actually shipped.

**Never assert the trailer's exact form.** `nightly.yml` and the release build both rewrite it before compiling — the `(x-release-please-version)` marker only exists in the source file. Two tests pinned that marker and made *every nightly fail for three days* while CI stayed green, because CI does not stamp. Assert `CONFIG VERSION V\d+` survives, not what follows it.

`ConfigMigrator` is the highest-risk code in the repo — it runs exactly once on every existing install, and a mistake destroys settings the user cannot get back. That is what most of the suite covers.


```bash
mvn -B -ntp clean package     # compile + shade → target/discordlogger-<version>.jar
mvn -B -ntp clean compile     # compile only (faster sanity check)
```

- A clean `mvn package` is not a functional test. Real verification of listener and config behaviour still means dropping the shaded JAR into a Paper server's `plugins/` folder.
- The shade plugin relocates SnakeYAML to `com.discordlogger.shaded.snakeyaml` and excludes its `META-INF`. `minimizeJar` is deliberately **off** (ASM/modern bytecode issues).
- **Maven resource filtering applies ONLY to `plugin.yml` and `build-info.properties`** (for `${project.version}` / `${dl.build.channel}` / `${dl.build.date}`). `config.yml` is copied **verbatim** — it contains `$` characters in ASCII art that must never be filtered. Don't add filtering to it; CI stamps its trailer via targeted regex replacement instead.
- A plain local `mvn package` produces a **`dev`-channel** build (`dl.build.channel` defaults to `dev` in `pom.xml`) — see `BuildInfo`.

## Repository layout

```
pom.xml                                Maven build
release-please-config.json             release-please: changelog sections, extra-files
.release-please-manifest.json          release-please: current released version (state)
scripts/validate-config-generator.py   CI check: options.json <-> template <-> Java source <-> shipped config
                                       <-> mirror <-> doc embed, plus trailers and the registry invariant
scripts/sync-versions.py               Propagates pom.xml's version values into README/CONTRIBUTING/docs
scripts/publish-listings.py            Mirrors stable releases to Modrinth/Hangar
scripts/export-metrics.py              Pulls the bStats charts to CSV; --check-charts finds discarded ones
src/main/resources/
  plugin.yml                           Plugin descriptor (Maven-filtered)
  build-info.properties                Build channel/version/date, baked in at package time
  config.yml                           Default config, schema v10 (NOT filtered) — file 1 of 4, see dictionary
  lang.yml                             Every player/Discord-facing string, same schema version
src/main/java/com/discordlogger/
  DiscordLogger.java                   Plugin entry point (onEnable/onDisable)
  log/Log.java                         Static logging facade (the API everything calls)
  lang/Lang.java                        Message lookup: chat() = MiniMessage, text() = plain
  filter/Filters.java                  Immutable filter snapshot, swapped atomically on reload
  alert/OpAlert.java                   Warns staff IN GAME when logging breaks; rate limited, never a URL
  custom/CustomLogs.java               Admin-defined command rules (log.custom.*), immutable snapshot
  custom/CustomTemplate.java           Fills {player}/{args}/{argN} in a rule's message; escapes + redacts
  webhook/DiscordWebhook.java          Manual JSON building + one HTTP POST, reports outcome
  webhook/WebhookQueue.java            Single-threaded send queue: rate limiting, retries, ordering
  config/ConfigMigrator.java           Comment-preserving config version migration
  config/SchemaDetector.java           Infers a config's schema from its KEYS — the arbiter
  config/ConfigVersionNotice.java      Warns ops when a config is AHEAD of the running build
  event/EventRegistry.java             Registers all listeners; fires start/stop
  event/ServerStart.java               Static handler (not a Listener)
  event/ServerStop.java                Static handler (not a Listener)
  command/Commands.java                Subcommand router (executor + tab completer)
  command/Subcommand.java              Interface: name/description/permission/execute/tabComplete
  command/Reload.java                  /discordlogger reload
  command/Webhook.java                 /discordlogger webhook <url> — never echoes the URL
  command/Regen.java                   /discordlogger regen confirm — destructive, hence the literal
  command/Status.java                  /discordlogger status — queue, webhook and build health
  command/Test.java                    /discordlogger test [event] — sends through the real path
  command/Doctor.java                  /discordlogger doctor — config contradictions a schema cannot catch
  command/CommandVisibility.java       Strips Bukkit's plugin:command tab-complete duplicates
  metrics/PluginMetrics.java           33 bStats charts; carries the privacy contract in its Javadoc
  metrics/Counters.java                Runtime tallies; reads are destructive (see Metrics)
  update/BuildInfo.java                Reads build-info.properties (channel/version/built)
  update/NightlyNotice.java            Nightly-channel warnings (console + first-boot op chat)
  update/UpdateChecker.java            Async, channel-aware GitHub release check on startup
  update/ServerCompat.java             Whether a release lists this server's Minecraft version (Modrinth)
  util/Names.java                      Nickname resolution + cache ("Nick (Real)")
  util/Platform.java                   Server-side capability probes
  util/ClientPlatform.java             Bedrock detection via Floodgate, never guesses "Java"
  util/Vanish.java                     Reads the `vanished` metadata every vanish plugin sets
  util/Placeholders.java               PlaceholderAPI expansion by reflection; inert when absent
  listener/player/                     PlayerJoin, PlayerQuit, PlayerChat, PlayerCommand,
                                        PlayerDeath, PlayerAdvancement, PlayerTeleport, PlayerGamemode,
                                        KillCommandTracker (pre-1.20 /kill vs VOID, see Listeners)
  listener/server/                     ServerCommand, Explosion
  listener/moderation/                 Ban, Unban, Kick, Op, Deop, Whitelist,
                                        PunishmentPlugins (LiteBans et al. bypass the ban list)
  listener/custom/CustomCommandLog.java Fires the log.custom.* rules; same filters as every event
src/test/java/com/discordlogger/       JUnit 5; 29 classes, 264 tests — see Build & test
docs/                                  Jekyll website (GitHub Pages, deploys from main)
  config/v10/index.md                   Config docs page — embeds file 4 of 4, see dictionary
  config/v10/lang.yml.txt               The lang.yml the page embeds and serves for download
  assets/js/versions.js                 Site-wide version awareness + BETA badges/gating
  assets/js/generator.js                Config generator loader (schema picker)
  assets/configs/registry.json          One entry per config schema
  assets/configs/v9/                    v9 generator bundle + data — PUBLISHED AND FROZEN, never edit
  assets/configs/v10/                   v10 bundle: generator.js, options.json, both templates, both mirrors
.github/workflows/
  ci.yml                               build + compat-floor + validate-generator-data (path-filtered)
  lint-pr.yml                          Enforces Conventional Commit PR titles
  release-please.yml                   Rolling Release PR on main + builds/attaches the stable JAR
  nightly.yml                          Cron + manual nightly beta builds from main
  sync-versions.yml                    Runs sync-versions.py on any push to main touching pom.xml
  check-listing-credentials.yml        Weekly --check-auth so a dead Modrinth PAT fails a cron, not a release
  poll-metrics.yml                     Twice-hourly bStats pie snapshots -> the metrics-data branch
.github/dependabot.yml                 Weekly maven/github-actions/bundler dependency PRs (paper-api ignored)
```

## Branches, releases, and the nightly channel

**Trunk-based development.** `main` is the only long-lived branch; short-lived `feat/*` / `fix/*` branches PR into it. (A `dev` branch existed historically — it has been retired; if you see references to it anywhere, they're stale.)

### Releasing
1. Conventional commits accumulate on `main` (squash-merged PR titles).
2. `release-please.yml` maintains a rolling **Release PR**: **every release is a patch bump** (`"versioning": "always-bump-patch"`), `CHANGELOG.md` from commit titles, `pom.xml` bumped natively, and `config.yml`'s annotated trailer line rewritten (the `(x-release-please-version)` marker — `ConfigMigrator` ignores everything after the `V<n>` number, verified).
3. **Merging the Release PR is the release.** The next `release-please.yml` run (triggered by that merge) tags `v<version>` and publishes the GitHub Release with the changelog as its body.
4. The same workflow run's `build-artifact` job then checks out the tag, stamps `BUILT <DD-MM-YYYY>` onto the trailer, builds with `-Ddl.build.channel=stable`, and attaches `DiscordLogger-v<version>.jar` + `.sha256`. (The build lives in the same run rather than a `release: published` listener because events created with the built-in `GITHUB_TOKEN` don't trigger other workflows — a separate listener would never fire. See Gotchas.)
5. **Config schema revisions (`v<N>` → `v<N+1>`) stay manual and deliberate** — bump the `V<n>` trailer, add `docs/assets/configs/v<n>/`, wire the generator config. Never inferred from commits. See the RUNBOOK below.
6. **Force a specific version:** a commit whose message has a `Release-As: X.Y.Z` footer retargets the Release PR (and `nightly.yml`'s version computation, which honors the same footer) to that exact version regardless of what the versioning strategy would produce.

7. **Minor and major bumps are manual, on purpose.** `"versioning": "always-bump-patch"` means commit types no longer move the version at all — a `feat:` and a `docs:` both produce a patch, and so does a `!`/BREAKING change. Semver is still the rule; it is just applied by a human at release time instead of inferred from a prefix.

   **The maintainer states patch vs minor every time. Never infer it, and never propose a bump that wasn't asked for.** When told a release is a minor, the mechanism is a `Release-As: X.Y.Z` footer in a commit body (`squash_merge_commit_message` is `PR_BODY`, so it survives the squash). Told nothing, it is a patch.

   **A major here means a ground-up rewrite, not a breaking change.** v1 was the first attempt at this plugin; v2 is this codebase, rebuilt from scratch. A config schema break, a dropped Minecraft version, a removed key — none of those are majors, and the migrator exists precisely so they don't have to be. Treat any impulse to suggest v3 as almost certainly wrong.

   The default strategy was dropped after a maintainer script (`scripts/export-metrics.py`, a file no server ever loads) went in as `feat:` and silently turned a patch cycle into 2.4.0. The prefix was answering "is this new?" when the version needs to answer "did the shipped artifact gain functionality?" — two different questions that only sometimes agree. The failure mode is now the opposite and quieter: **a real feature ships as a patch because nobody remembered the footer.** Check the accumulated changelog before merging a release, not just the version number.

### Distribution — GitHub is the only host that serves a JAR (almost)

Stable releases are mirrored onto two listings, because that is where server owners look:

| Listing | Slug | How the version is registered |
|---|---|---|
| Modrinth | `discordlogger` | The JAR is **uploaded**. Modrinth's API has no external-URL field — checked against its OpenAPI spec — so this is the one place a second copy of the file exists. |
| Hangar | `LVCHLANN/DiscordLogger` | Registered with `externalUrl` pointing at the GitHub Release asset. No copy is hosted, and Hangar's download button increments **GitHub's** counter. |

`scripts/publish-listings.py`, run by `release-please.yml`'s `publish-listings` job, does both. It is idempotent (re-running skips versions that already exist), refuses to publish if `pom.xml`'s version doesn't match the release tag, and refuses nightly tags outright — nightlies are GitHub-only, as the downloads page states.

**Modrinth `project_id` is the base62 id, not the slug.** The read endpoints accept either, so every check in this script (the duplicate lookup, `--check-auth`) works fine with the slug — but the version payload base62-decodes that field, and the 13-character slug overflows it. v2.2.0 published to Hangar and then 400'd with `Base62 decoding overflowed`, naming neither the field nor the slug. It is resolved from the slug at runtime now; do not hardcode it back.

**Both `paper` and `purpur` go on every Modrinth version.** Modrinth treats each fork as its own loader, so a version tagged only `paper` is filtered out for anyone browsing as Purpur — a server the plugin fully supports and the docs name explicitly. `MODRINTH_LOADERS` holds the list and is validated against `/tag/loader` the same way game versions are, because an unrecognised loader is another opaque 400. Hangar needs no equivalent: its platforms are PAPER/WATERFALL/VELOCITY and PAPER already covers the forks.

**The publish is re-runnable.** `release-please.yml` takes a `workflow_dispatch` with a `tag` input, so a listing that failed can be retried against an existing release without cutting a new one. Before that existed, `publish-listings` could only run as a side effect of the release commit — which is how v2.2.0 ended up published on Hangar, absent from Modrinth, and unrecoverable without faking a release.

### Metrics

33 bStats charts in `metrics/PluginMetrics.java`, with runtime tallies in `metrics/Counters.java`.

**Check bStats' defaults before adding a chart.** It already collects `bukkitName`, `onlineMode`, `bukkitVersion`, `javaVersion`, `playerAmount`, `coreCount` and the OS fields on its own. A `server_fork` and an `online_mode` chart were added here and removed again once it turned out `bukkitName` *is* `Bukkit.getName()` — the same value under a second name, on every server, forever.

**The line: configuration state, plugin presence and server software — never a player.** No names, UUIDs, IPs, message content, coordinates or world names. Counts are buckets, not exact numbers; `bucket()` has a test asserting it never returns the bare figure, so a chart cannot become a fingerprint by accident. `commands_used` is a `Set` for the same reason — fifty `/reload`s are indistinguishable from one.

**Counter reads are destructive.** bStats sums each submission across every server, so a running total would re-count everything already reported. `take*` returns the delta and resets.

**Source builds report**, tagged `dev` by `release_channel`. They were excluded once on the theory that a developer's machine skews every chart; bStats identifies a server by an id in `plugins/bStats/config.yml`, per data directory, so rebuild cycles against one test server are one server. Excluding them only understated the totals.

**`command_filter_state` is the one with teeth.** It reports `Reduced` when a command the plugin ships in `filters.ignored_commands` has been removed — `/login` and `/msg` are in there because command logging posts lines verbatim. Tested, including the trap where a *longer* list is still `Reduced`.

**Adding a chart means updating the disclosure.** The README and setup guide both enumerate what is collected. That list is a promise, not decoration.

**A chart must also be created on bStats, by hand, with the id matching exactly.** Registering it in Java is only half the job: bStats accepts a submission for a chart that does not exist on the site and silently discards it — no error, no warning, and the plugin looks perfectly healthy. Four charts sat in that state after the metrics expansion (`release_channel`, `enabled_events`, `output_mode`, `config_schema`), including the one answering "which events do people actually enable". Nothing surfaces this on its own, so after adding charts run:

```bash
python3 scripts/export-metrics.py --check-charts
```

It diffs the ids in `PluginMetrics.java` against bStats' own chart list and exits non-zero on any that would be discarded. **Not in CI on purpose** — it needs the network, and it would fail for the entirely normal window between a chart landing in Java and someone creating it on the site.

**bStats keeps line-chart history but not pie history.** `?maxElements=100000` on a line chart returns samples going back years; a pie endpoint answers only "what is true right now" and the previous answer is gone. `poll-metrics.yml` therefore polls twice an hour onto the orphan **`metrics-data`** branch, appending to a single `data/bstats.csv` as **one row per poll, one column per slice** — so charting adoption is selecting a column against `polled_at`, with no pivot first. Line charts hold their latest value under the bare chart id, since bStats keeps their full history anyway.

**Every chart carries a `<chart>.#servers` column**, the number of servers reporting *that chart* at that poll. It is the only honest denominator: only four charts (`config_schema`, `enabled_events`, `output_mode`, `release_channel`) shipped in 2.2.0 and are reported by every server — the other 27 arrived in 2.3.0 and are reported only by servers running it, so dividing them by the total server count understates them by however many have not upgraded.

That branch is data, never code: it shares no history with `main`, and it exists on its own branch because a workflow pushing to `main` is rejected by branch protection and fails every run (see *Gotchas*).

The same script exports every chart to CSV (`--include-default` for bStats' own, `--per-chart` for one file each). The bStats site has no download of any kind, so reading the data any way other than by eye means going through the public JSON API this wraps.

**Download counting.** The README badge is shields.io's stock `github/downloads/.../total`, which counts GitHub Release assets. Hangar traffic is already inside that number — its versions are `externalUrl` links to the GitHub asset — so only Modrinth's own tally is missing. There used to be a combined endpoint badge written by `publish-listings.py --badge` and committed by `downloads-badge.yml`; it was removed because the commit pushed straight to `main` and branch protection rejects that, so it failed every single day and the number froze. A badge that silently stops updating is worse than one that undercounts by a known amount.

**Two secrets must exist** or the corresponding platform is skipped with a notice (a lagging listing is recoverable; a release job dying after tagging is not): `MODRINTH_TOKEN` (PAT, "Create versions" **plus one read scope** — either "Read analytics" or "Read user info") and `HANGAR_API_KEY` (create_version permission). Both are repository **Actions** secrets — the job declares no `environment:`, so environment secrets would not resolve.

Modrinth's `VERSION_CREATE` scope covers exactly one endpoint — the publish call — so a token holding only that scope cannot be verified without actually publishing. One read scope is therefore required in addition, purely to give `--check-auth` a harmless authenticated endpoint. It probes two and passes if **either** answers, so it doesn't care which was granted:

| Probe | Scope | Note |
|---|---|---|
| `GET /v3/analytics/downloads` | Read analytics | Analytics exists only on **v3**; v2 returns 404. `project_ids` must be percent-encoded — sent raw, it 400s *before* auth is checked, which would silently defeat the probe. |
| `GET /v2/user` | Read user info | |

Modrinth answers both "expired" and "wrong scopes" with a bare 401, so the failure message names both possibilities rather than implying the token is dead.

The downloads badge does **not** use analytics — Modrinth's public project endpoint already exposes the total with no auth at all. Analytics would only be needed for time-series or per-version breakdowns.

Modrinth PATs expire. `check-listing-credentials.yml` runs `publish-listings.py --check-auth` weekly so a dead token is caught by a failed scheduled run rather than by a release that has already tagged. Note that `--dry-run` makes no authenticated call at all and proves nothing about the tokens; `--check-auth` is the mode that does.

**Three floors exist and are deliberately different numbers.** `api-version: 1.19` admits 1.19.0 onward, because api-version cannot express a patch before 1.20.5. `<dl.compat.floor>` is 1.19.0 and CI compiles against it on every Java change — without that job, using an API added in 1.19.1+ would compile, test, ship, and only then `NoSuchMethodError` on the servers the listings promise. `<paper.api.version>` is 1.19.4 because that is the oldest release the *test suite* can run against: below it, Bukkit's `YamlConfiguration` wants SnakeYAML 1.x and collides with ours on the unrelocated test classpath. That collision cannot happen in the shipped JAR, where SnakeYAML is shaded and relocated.

**Dependabot is told to ignore `paper-api`** in `.github/dependabot.yml`. It is not a dependency to keep current — it is the oldest server the plugin promises to run on, pinned low on purpose. A bot bumping it to the newest build silently undoes the whole arrangement.

`<dl.game.versions>` in `pom.xml` is the Minecraft version list both listings advertise. It's the one value that can't be derived — `api-version` is a floor, not a list — so it's validated against Modrinth's known-version API before publishing; a typo fails the release rather than mislabelling it.

### Build channels (`BuildInfo`, baked in at package time — never inferred from the version string)

| Channel | Set by | Version format | Behavior |
|---|---|---|---|
| `stable` | `release-please.yml` (`build-artifact` job) | `1.2.3` | Normal update checks; `NightlyNotice` inert. |
| `nightly` | `nightly.yml` | `1.2.3-BETA.3` | Console warning **every start**; ops get an in-game chat notice **once per nightly version** (marker file `.nightly-notice`); update checks notify on **every** new stable and when **more than 2** nightlies behind. |
| `dev` | default (local build) | whatever `pom.xml` says | Update checks skipped entirely. |

### Nightly builds (`nightly.yml`)
Cron (15:00 UTC — arbitrary, adjust freely) + `workflow_dispatch`, building from `main`:
- **Skips** if `main`'s HEAD matches the last nightly tag or the last stable tag — no identical rebuilds, which also naturally bounds how many nightlies exist (they're all kept forever, never pruned).
- Computes the **upcoming version** the same way release-please will (conventional commits since the last stable tag, honoring `Release-As:` footers the same way), so the beta's base version always matches the eventual stable release.
- Numbers builds `v<version>-BETA.1`, `.2`, … — derived from existing tags each run, self-resets when the base version moves (e.g. a `feat:` lands and `1.2.3-BETA.9` jumps to `2.2.0-BETA.1`).
- Version injected via `mvn versions:set` (CI-local, never committed); trailer stamped `SHIPPED WITH v<version>-BETA.<n> BUILT <DD-MM-YYYY>` — every version string in the built JAR matches what was built, while the repo never contains a beta version string.
- Publishes each nightly as its own pre-release (`prerelease: true`) with notes listing commits since the previous nightly, plus JAR + checksum.
- Stable servers never see nightlies: the stable-channel update check skips pre-releases entirely.

### CI (`ci.yml`)
Path-filtered (`dorny/paths-filter`), three jobs behind a `changes` gate:

| Job | Runs on | What it proves |
|---|---|---|
| `build` | `src/**`, `pom.xml` | compiles and the test suite passes; uploads the JAR as a PR artifact |
| `compat-floor` | same | recompiles with `-Dpaper.api.version=<dl.compat.floor>`, so using an API added after the advertised floor fails here instead of `NoSuchMethodError`-ing on a user's server |
| `validate-generator-data` | `docs/assets/configs/**` | `validate-config-generator.py` |

Concurrency cancels superseded runs. Branch protection requires `build`, `validate-generator-data` and `lint`; `compat-floor` is not (yet) in the required list.

### Repo settings that matter (configured, not in files)
- "Allow GitHub Actions to create and approve pull requests" **must stay enabled** — release-please cannot open its Release PR without it.
- Branch protection on `main` requires the `build`, `validate-generator-data`, and `lint` checks; merged branches auto-delete.
- **Squash-merge title source must be `PR_TITLE`, not the default `COMMIT_OR_PR_TITLE`.** With the default, a single-commit PR uses that commit's message instead of the (lint-checked) PR title when squashed — silently bypassing `lint-pr.yml`'s whole purpose. Verify with `gh api repos/OWNER/REPO --jq .squash_merge_commit_title`.

## Runtime architecture

### Startup flow (`DiscordLogger.onEnable`)
1. `BuildInfo.load(this)` — reads the baked-in channel first; later steps depend on it.
2. `saveDefaultConfig()` writes the bundled `config.yml` on first run.
3. `ConfigMigrator.migrateIfVersionChanged(...)` migrates the user's config if the schema version changed.
4. `new NightlyNotice(this).activate(this)` — no-op unless nightly channel.
5. `applyRuntimeConfig()` reads `webhook.url` + `format.time` and calls `Log.init(...)`. A missing/invalid webhook does **not** disable the plugin — it runs "degraded" (console-only) with a warning to set the URL and `/discordlogger reload`.
6. `EventRegistry.registerAll()` registers every listener **unconditionally** — per-event enable checks happen inside each handler by reading config live (this is what makes reload work without re-registering).
7. Commands wired up, `UpdateChecker.checkAsync(...)` fired, then `events.fireServerStart()`.

### `Log` (static facade — the only way anything sends to Discord)
- All state is `static volatile`; `init()` runs on the main thread, senders on async scheduler threads. The color map is built locally then assigned in **one volatile write** so async readers never see a half-built map. Preserve this pattern.
- `Log.isReady()` gates all Discord sends; console logging always happens.
- API: `plain(String)`; `event(category, message)` / `eventWithThumb(...)`; `eventFields(...)` / `eventFieldsWithThumb(category, title, author, List<Field>, thumbUrl)`; `sendUpdateEmbed(...)` (UpdateChecker only); `mdEscape(String)` (use on any player-controlled text); `playerAvatarUrl(UUID)` (mc-heads.net).
- **Color resolution:** category strings are normalized (lowercase; spaces/dots/dashes/slashes → `_`) then looked up, so `"Player Join"` → `player_join`. Defaults hard-coded in `Log.init`, overridden by `log.<group>.<event>.color` — the colour sits beside its toggle as of schema v10, not in the separate `embeds.colors` tree v9 used. Unknown categories fall back to the `server` color.
- Valid webhook URL prefixes: `discord.com`, `discordapp.com`, `ptb.discord.com`, `canary.discord.com` (all `https://…/api/webhooks/`).

### `DiscordWebhook`
- JSON built **by hand with StringBuilder** (no JSON library); `escape()` handles quotes/backslashes/control chars — keep escaping every interpolated string.
- `dispatch()` hands off to `WebhookQueue` and returns immediately — callers may be on the main thread and must never block on HTTP.
- `post()` performs one request and **returns a `Response`** (status + rate-limit headers) rather than logging; the queue owns retry/wait/give-up decisions.

### `lang.yml`

**Every config file must be stamped and bumped.** Three places know about the trailer, and all three take a list, not a single path: `release-please-config.json`'s `extra-files` (bumps `SHIPPED WITH` when a release is cut), `nightly.yml` (stamps the beta tag and build date), and `release-please.yml`'s `build-artifact` (appends the build date). Adding a config file and forgetting one leaves it claiming an old version with the `(x-release-please-version)` marker still in it — in a file users open. That happened to `lang.yml` and was caught only by inspecting a nightly artifact.

**The config version is global.** `lang.yml` carries the same `config-version` and the same `CONFIG VERSION V<n>` trailer as `config.yml` — every config file this plugin ships moves as one, so there is never a combination of versions to reason about. `Lang.reload` runs it through `ConfigMigrator.migrateIfVersionChanged`, which is file-agnostic.

That required un-hardcoding the rotation names: they were `config.new.yml` / `config.old.yml`, so a second migrated file would have overwritten the first one's backup. They are now derived from the file being migrated (`lang.yml` → `lang.old.yml`).

**Four copies to keep in step**, and `validate-config-generator.py` checks all of them:

| Copy | Purpose |
|---|---|
| `src/main/resources/lang.yml` | What the plugin ships. The source of truth. |
| `docs/config/v<N>/lang.yml.txt` | Embedded on the docs page. |
| `docs/assets/configs/v<N>/lang.yml` | The download button. Carries the `DOWNLOADED FROM WEBSITE` trailer, not the release-please marker — the same convention as `config.yml`'s mirror. |
| `LangTest` | Walks every `Lang.*("key")` in the source. |

The drift check follows the **live schema** rather than naming a version, so it keeps working at v11 instead of silently checking a frozen page. `lang.yml` is documented **inside the config docs page**, not on its own — it is part of the same reference, under the same version.


Every message players and Discord readers see. `Lang.chat(key, ...)` renders MiniMessage for in game; `Lang.text(key, ...)` returns plain text for Discord, which renders Markdown and would post a `<green>` tag literally. Two methods rather than one so mixing them up is hard.

**Console messages are deliberately NOT in lang.yml.** They are diagnostics, and a translated error is one nobody can search for — support threads and search results depend on the English text staying put.

The shipped English is loaded from the jar at class-load, not in `reload`, so messages still resolve before the first load and act as a fallback for a `lang.yml` predating a new key. A missing key returns the key itself: a blank message looks like the plugin failing silently, whereas `chat.reload-ok` appearing in game names the entry to fix.

Death causes are keyed by the enum name lowercased with hyphens (`FIRE_TICK` → `fire-tick`), so the switch that used to hold 33 strings is now a lookup. `LangTest` walks every `DamageCause` and every `Lang.*("key")` in the source: moving strings into a file removes the compiler as a safety net, so the test is what replaces it.

### Log filtering (`filters:`)

Applied in the **listener**, not in `Log` — that is where the player, world and raw command still exist; by the time a message reaches `Log` it is rendered prose. `Filters` holds an immutable snapshot swapped atomically on reload, and `applyRuntimeConfig` reloads it *before* `Log.init` so a reload cannot briefly log something the new config filters.

**15 filters**, in three groups: *who* (`ignored_players`, `exempt_permission`, `respect_vanish`), *where* (`ignored_worlds`), and *what* (commands, chat, advancements, teleports, deaths, explosions). Each listener checks the ones relevant to it — `Filters` holds no event knowledge, and no listener holds filter logic.

`only_log_commands` is an allow-list that wins outright when set, with the deny-list applying inside it. `log_recipe_advancements` was a hardcoded skip in `PlayerAdvancement` and is now a setting, defaulting to the old behaviour.

`filters.ignored_commands` **ships non-empty**, which is unusual for this project and deliberate: command logging posts the line as typed, so `/login` published passwords in plain text and `/msg` published private messages. Matching is on the command word after stripping the slash, arguments and any plugin qualifier — without that last step `/essentials:msg` would bypass an entry of `msg`, which would make a security-flavoured filter worthless.

**Moderation events are deliberately not filtered by player.** `ignored_players` means "this account's own activity", not "everything mentioning this account"; a ban is a record of staff action and hiding it guts the audit trail.

**Lists in config are a migration hazard.** `ConfigMigrator` replaced scalars only, and every value was a scalar until `filters:` existed. A list hitting that path wrote `key:"[a, b]"` — invalid YAML, original items orphaned. It now splices list blocks bottom-up (top-down would invalidate later indices) and quotes an item only when YAML would otherwise read it as a boolean or number. `ConfigMigratorListTest` pins this.

### Per-event webhook routing

Every event takes an optional `log.<group>.<event>.webhook`. Empty means the main `webhook.url`, which is the overwhelmingly common case. `Log.webhookFor(category)` resolves it from a map built alongside `colorMap` — same walk, same key normalisation, same atomic swap. An invalid URL is rejected at load with a console warning and that event falls back, rather than posting into the void.

`Log.plain` and the update notices deliberately stay on the main webhook: they belong to no event.

### `WebhookQueue` (rate limiting)
- **One worker per destination**, which is also the ordering guarantee — logs are a narrative, so out-of-order delivery is its own bug.
- **Proactive pacing:** reads `X-RateLimit-Remaining` / `X-RateLimit-Reset-After` from each response and waits out a spent budget *before* sending, so 429s are usually avoided rather than handled. A 429 is still handled (honours `Retry-After`, retries the same payload without consuming an attempt).
- Transient failures (5xx, network — status `0`) retry with exponential backoff, max 4 attempts. Other 4xx aren't retried; a 404 says the webhook URL is gone.
- **Not a Bukkit scheduler task** — it must keep draining during `onDisable`, and the scheduler refuses tasks once disabled.
- Bounded at 1000 messages; beyond that it drops and warns once per outage rather than growing until the server dies. **Both that and a 404 also alert staff in game** through `OpAlert` — eleven days of metrics showed 7,957 of 8,721 failures were dead webhooks across 130 separate windows, with the console warning reaching nobody. Alerts are capped at one per problem per 30 minutes, because a dead webhook fails on every event and an unthrottled alert gets muted immediately.
- `onDisable` order matters: `fireServerStop()` queues the stop message, *then* `WebhookQueue.shutdown()` drains (5s budget). Reversing that loses the message.
- HTTP 200/204 = success; 10-second timeouts; footer icon hard-coded to the website-hosted logo.

### `ConfigMigrator`
- Schema version = regex-matched trailer comment `CONFIG VERSION V<n>` (case-insensitive; trailing text after the number is ignored, which is why the release-please annotation and `BUILT` stamps are safe).
- Migrates only when both versions are detectable and differ: parses both YAMLs (SnakeYAML), flattens to dotted paths, transplants user values into the **new default text** in-place (preserving all comments/ASCII art, respecting inline comments), then rotates: user file → `config.old.yml`, new → `config.yml`.
- Removed/renamed keys silently keep new defaults. Generic — no per-version migration code.

### Listeners — common patterns (follow when adding events)
- One final class per event; constructor takes `JavaPlugin` (or `Plugin`).
- First line of every handler: live config gate, e.g. `if (!plugin.getConfig().getBoolean("log.player.join", true)) return;` — never cached.
- `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)` on almost everything (exceptions: `PlayerChat`, `PlayerCommand` use default priority).
- Player text → `Names.display(player, plugin)` + `Log.mdEscape(...)`; player embeds get `Log.playerAvatarUrl(uuid)` thumbnails; server events use the hosted `server.png`.
- **Moderation listeners are command sniffers, not API hooks:** they watch `PlayerCommandPreprocessEvent` + `ServerCommandEvent`, parse the raw command, gate on vanilla/Bukkit/Essentials permission nodes (console always allowed), then **verify the state actually changed on the next tick** before logging — *except where a punishment plugin owns bans*. LiteBans, LibertyBans, AdvancedBan, BanManager and CMI keep their own databases and never write to Bukkit's ban list, so that check could only ever be false there and 4 of 25 reporting servers were getting no moderation logging at all. `PunishmentPlugins.installed()` skips the verification on those servers and logs on the command; the permission gate still applies. The trade is an occasional over-report instead of silent total omission, and `PunishmentPlugins.isBanned` now consults `BanList.Type.IP` too, since `/ban-ip` was unlogged everywhere. `Kick` is two-phase (intent map keyed by target UUID → confirmed by `PlayerKickEvent`, stale entries cleaned after 2 ticks).
- Notable: `PlayerJoin` delays 2 ticks for nickname plugins; `PlayerQuit` defers cache eviction 1 tick; `PlayerChat` uses Paper `AsyncChatEvent` + Adventure serializer (why Paper API is required); `PlayerAdvancement` skips `recipes/*` and `*/root`; `PlayerDeath` builds Geyser-friendly messages from damage context; `Explosion` handles entity+block explosions with CDN icons and a 20-block nearby-player list; `ServerStart`/`ServerStop` are static handlers called by `EventRegistry`, not listeners.
- **`KillCommandTracker` is a version-gated workaround, not a feature.** `DamageCause.KILL` arrived in 1.20; before it, the server reports `/kill` as `VOID`, so a command death read as "Fell into the void". It watches the command and lets `PlayerDeath` correlate a `VOID` death against it within 250ms. `ACTIVE` is `false` wherever `KILL` exists — running correlation where the real information is available could only turn a correct answer into a guess. Keep it that way; the parsing (`targetOf`) is unit-tested precisely because the awkward cases are textual (`/minecraft:kill`, `/killall`, selectors, leading whitespace).

### Commands
- Root `/discordlogger` (aliases `/dlogger`, `/dlog`). The **command itself is deliberately ungated** — it used to require `discordlogger.reload`, which would have locked a `regen`-only admin out of the whole command once a second subcommand existed. `Commands` routes `Subcommand` implementations (LinkedHashMap) and filters both help and tab-complete by each subcommand's own permission, so an unprivileged sender sees nothing and can run nothing.
- Subcommands, all default op: `reload`, `webhook <url>`, `regen confirm`, `status`, `test [event]`, `doctor` — each gated by `discordlogger.<name>`.
- **Tab-completion is filtered twice, in two different places.** `Commands.onTabComplete` filters *arguments* by permission. `CommandVisibility` filters the *command list itself* via `PlayerCommandSendEvent` — that is the only hook that can remove Bukkit's automatic `plugin:command` duplicates, because command-name completion never consults the plugin. It also hides the command from players holding none of the subcommand permissions. It touches only this plugin's entries: editing another plugin's makes that plugin's behaviour unexplainable from its own source. `regen` requires the literal `confirm` — it is destructive and one keystroke from `reload`.
- **`webhook` never echoes the URL.** It is a bearer credential, so confirmation names the channel id only, tab-complete returns nothing, and `Log.redactWebhooks` masks the token in command logging — without which running the command would publish the new URL to the channel currently configured, i.e. the old webhook. Redacting rather than suppressing keeps the audit trail: you still see that someone changed it.
- **Never write config with `saveConfig()`.** Bukkit's YAML writer re-serialises the whole file and drops every comment, including the `CONFIG VERSION V<n>` trailer `ConfigMigrator` reads — a config saved that way looks like it has no schema at all. Use `ConfigMigrator.setScalar(file, path, value)`, which rewrites exactly one line (verified: one line changed, comment and line counts unchanged, trailer intact).
- Adding one: implement `Subcommand`, add to the `new Commands(...)` varargs in `onEnable`, register any new permission in `plugin.yml`.

### `UpdateChecker`
- Async on startup; skips for `dev` channel. Fetches the **releases list** (`/releases?per_page=50`) — not `/releases/latest` — because nightly builds need to see pre-releases. Parses `tag_name`/`prerelease` pairs by regex (no JSON library, intentional; see `parseReleases` for why it's safe) and ranks with a `SemVer` record where stable > any `-BETA.N` of the same version, higher N > lower.
- Stable channel: notify on any newer stable, pre-releases invisible. Nightly channel: notify on **every** newer stable, and on nightlies only when **more than 2** behind (`NIGHTLY_LAG_THRESHOLD`). Notifications = console banner + Discord webhook notice (embed or plain per config).

## Config reference (schema v10)

```yaml
config-version         10            # declared schema; SchemaDetector still overrules it
webhook.url            ""            # plugin is console-only until valid
format.time            "[HH:mm:ss, dd:MM:yyyy]"  # Java DateTimeFormatter; plain-text mode only
format.name            ""            # plain-text server-name prefix (proxy setups)
format.nicknames       true          # "Nick (Real)" in player logs
embeds.enabled         true          # false → plain Markdown messages
embeds.author          "Server Logs"

# v10 shape: every event is a SECTION, not a boolean. Colour lives beside its
# toggle rather than in embeds.colors.*, which is what v9→v10 migrated.
log.<group>.<event>.enabled   true
log.<group>.<event>.color     "#RRGGBB"
log.<group>.<event>.webhook   ""      # empty = the main webhook.url
log.<group>.<event>.<extra>           # per-event sub-options, see `extras`

log.player.{join,quit,chat,command,death,advancement,teleport,gamemode}
log.server.{command,start,stop,explosion}
log.moderation.{ban,unban,kick,op,deop,whitelist_toggle,whitelist_edit}
filters.<14 keys>                     # see Log filtering
```

All `log.*.enabled` toggles ship as `true`. Sub-options currently shipped: `log.player.join.show_platform` (`true`) and `log.player.death.show_coords` (**`false`** — it reveals where the body and its drops are, so it is the one thing here that ships off).

**`embeds.colors.*` no longer exists.** It was the v9 location; v10 moved every colour to `log.<group>.<event>.color`. `Log` still normalises category strings the same way, so the resolution rules below are unchanged — only the source keys moved.

### Config file dictionary — four places hold "the config," they are not the same thing

Four near-identical copies of the config content exist. Confusing them is the single most common way this repo drifts — it already happened twice: a hint-text reword landed on two of four copies and nobody noticed the other two for several sessions (one of them, the doc-page embed, had *already* been silently missing an entire banner block before anyone caught it).

**These files carry no in-file "which copy am I" markers, deliberately.** An earlier attempt put identity headers at the top of each one; every path a user obtains a config — fresh install, website download, and generator output — then handed them repo-internal notes referencing `AGENTS.md` and `scripts/`. User-facing artifacts must not leak repo internals, so the table below plus the CI check are the *only* mechanism keeping these in sync. Don't reintroduce in-file markers.

| # | Location | What it actually is | Consumed by |
|---|---|---|---|
| 1 | `src/main/resources/config.yml` | **The shipped config** — the real source of truth. Bundled inside the plugin JAR; every server gets this on first run. | `DiscordLogger.onEnable` / `ConfigMigrator` (Java, at runtime) |
| 2 | `docs/assets/configs/v<N>/config.yml` | **The download mirror** — a static copy served by the plain "Download" button on the config docs page. No wizard involved; just the file, verbatim. | A `<a download>` link in `docs/config/v<N>/index.md` |
| 3 | `docs/assets/configs/v<N>/config.template.yml` | **The generator template** — `{{TOKEN}}` placeholders, filled in by the wizard based on what the visitor chose. Never downloaded directly; its *output* is. | `docs/assets/configs/v<N>/generator.js` |
| 4 | The `## Full config.yml` fenced code block inside `docs/config/v<N>/index.md` | **The doc-page embed** — the full file shown inline in prose, for people reading the docs who don't want to click through. Easiest of the four to forget since it lives inside Markdown, not a config file. | Rendered directly on the config docs page |

**The rule:** 1, 2, and 4 must be **content-identical** — same real values, same comments, same banners — except each one's own trailer line (`SHIPPED WITH vX.Y.Z` vs `DOWNLOADED FROM WEBSITE`). `scripts/validate-config-generator.py` enforces this automatically in CI for both 1↔2 and 2↔4 — it will fail the build if any of them drift. File 3 isn't byte-compared (it has tokens instead of real values), but its `{{LOG_*}}`/`{{COLOR_*}}` tokens are cross-checked against `options.json` and the Java source by the same script.

**The trailer itself is checked separately, and had to be.** Because the copy comparison deliberately excludes the trailer — the one line that is *meant* to differ — nothing looked at its contents for a long time, and `docs/config/v10/lang.yml.txt` sat claiming `SHIPPED WITH v2.1.6 (x-release-please-version)` through two releases. **A release-please marker only updates in files listed in `release-please-config.json`'s `extra-files`; anywhere else it freezes at whatever version it was written with and then lies.** `check_website_copies_are_labelled()` now asserts both halves of that rule for every copy under `docs/`: no marker in an untracked file, and a trailer that says where the file came from (`DOWNLOADED FROM WEBSITE` / `GENERATED ON WEBSITE`).

**Practical consequence:** any edit to the real config content (webhook/format/embeds/log.\* structure or their comments, including the banners) touches file 1 first, then files 2 and 4 need the identical change, and file 3 needs the matching `{{TOKEN}}` version. Don't rely on memory for this — run `python3 scripts/validate-config-generator.py` locally before opening the PR; CI runs it too, but catching it before pushing is faster.

### Java fallbacks must equal the shipped defaults

`getBoolean("log.x.y.enabled", <fallback>)` in Java must use the same value `config.yml` ships. The fallback only applies when the key is missing from a user's file — hand-edited, partial copy, or predating the key — and a mismatch silently disables logging the docs promise is on. `validate-config-generator.py` fails the build on any disagreement.

The one deliberate `false` is `log.player.death.show_coords`, which ships `false` in both places. (`PlayerTeleport`, `PlayerGamemode` and `Explosion` were once inconsistent here; that has been fixed — if you find a doc still listing it as open, it's stale.)

## Website (`docs/`)

Jekyll site (GitHub Pages gem stack) at `discordlogger.godtiergamers.xyz` (CNAME present). Pages use `_layouts/default.html` via `_config.yml` defaults; nav in `_data/nav.yml`. **Deploys from `main`** — docs changes go live on merge, independent of plugin releases.

- **Local dev:** `cd docs && bundle install && bundle exec jekyll serve --livereload --watch` (`docs/test.sh` does the same). `docs/_site/` is gitignored build output — never edit, never trust.
- The plugin hot-links icons from the site (`/assets/icons/…`) — the site being up is a runtime dependency of embeds. The banner is self-hosted at `/assets/DiscordLogger-Banner.webp` (compressed; no external image host).

### Version awareness — never hardcode a version number

**Never write "and newer" about schema coverage.** A schema is only known to be shipped by the releases that have actually shipped it — the next release may open a new one, so promising future coverage eventually becomes a lie on a page nobody revisits. `<span data-dl-schema-versions="v10">` fills itself from the releases API plus `registry.json`, listing only real releases, and says so plainly when none exists yet. Naming the schema's own `since` when nothing has shipped it is not the same thing and is fine — that is one declared build, not an open-ended promise.

`docs/assets/js/versions.js` is loaded from `<head>` on every page and is the single source of truth. It reads the GitHub releases API once (cached per session), works out the newest stable and newest nightly, and exposes `window.DLVersions`.

**A version is "beta" when it is newer than the newest stable release** — i.e. it exists only in nightly builds. This is *derived, never hand-flagged*: while 1.2.3 is nightly-only its docs show a BETA badge, and the moment 1.2.3 ships stable every badge and gate flips itself off with no edits. Never add a manual "is beta" flag anywhere.

Use the declarative hooks rather than writing per-page JS or literal versions:

| Markup | Behaviour |
|---|---|
| `data-dl-version="1.2.3"` | appends a BETA badge while that version is nightly-only |
| `data-dl-beta-only="1.2.3"` | hides the element unless beta is enabled (normal content once it ships) |
| `data-dl-latest` / `data-dl-latest-nightly` | filled with the current version |
| `data-dl-beta-toggle="optional note"` | renders the beta opt-in slider (shared `DLVersions.switchEl()` — used by every beta toggle on the site, never a raw checkbox) |

Beta content is **opt-in and remembered** (`localStorage`), so visitors never trip over unreleased features. After injecting markup dynamically, call `DLVersions.apply(root)`; to react to the toggle, listen for the `dl-beta-change` event. `DLVersions.ready()` resolves once release data is in. **A beta toggle must render in both its on and off states whenever beta content exists** — gating its own visibility on `!showBeta` removes the only control that could turn it back off (this shipped once, was reported, and was fixed — don't reintroduce it).

### Config generator — per-version frozen bundles

**Users pick their plugin version; the config schema is resolved for them.** Nobody knows offhand which schema their build uses, but everyone knows what they downloaded — so the picker lists plugin builds and shows the detected schema as a confirmation note.

Every published build is listed individually, **nightlies included** (`1.2.3-BETA.1`, `1.2.3-BETA.2`, …) — successive nightlies can carry different features, so they're genuinely different targets. Nightlies are hidden until the visitor enables beta, and the default selection is always the newest *stable* build.

Internally the generator is still keyed on **config schema versions** (v9, v10…): a new schema means a new bundle, a new plugin release does not.

```
docs/assets/js/generator.js        LOADER — small, stable, shared
docs/assets/configs/
  registry.json                    one entry per SCHEMA: { config: "v10", since: "2.2.0" }
  v9/**                            PUBLISHED AND FROZEN — never edit (no lang files; lang.yml is v10+)
  v10/generator.js                 SELF-CONTAINED bundle: steps, styles, webhook payload, YAML builders
  v10/options.json                 UI data: events+colours, `filters`, `lang` groups
  v10/config.template.yml          {{TOKEN}} output template for config.yml
  v10/lang.template.yml            {{LANG_*}} output template for lang.yml
  v10/config.yml                   reference copy of what shipped
  v10/lang.yml                     reference copy of what shipped
```

Live schemas: **v9** (`since: 2.1.5`, frozen) and **v10** (`since: 2.2.0`, current).

**The generator emits every key in both files, and nothing is hardcoded in a template that the wizard cannot reach.** A setting the generator silently bakes in is one the user does not know they have — which is exactly what the filters were until they were wired up. Concretely: `options.json` carries `filters` (one entry per `filters.*` key, typed `list` / `choices` / `text` / `number` / `bool`) and `lang` (all messages, grouped, each with its shipped default). Adding a sixteenth filter or an eightieth message is a **data** change — one entry plus one `{{TOKEN}}` — never a code change to the bundle.

**The invariant that makes this safe: generating and changing nothing must reproduce the shipped files byte for byte.** `validate-config-generator.py` enforces it in both directions — `render_filter()` there is a deliberate reimplementation of the bundle's renderer, so the two must agree, and the lang template is re-substituted with its declared defaults and diffed against `src/main/resources/lang.yml`. It also fails on a filter with no template slot, a template token with no entry, and a filter no Java source reads. Templates therefore may not carry text the shipped file lacks: a stray explanatory comment above `config-version` is enough to break the invariant, and did.

**The isolation rule (the whole point): once a schema version has been *published*, never edit it again.** Old plugin versions must keep generating exactly the config they always did. Fix bugs only in the current unpublished schema; copy the folder forward instead of refactoring in place. The loader↔bundle contract is documented at the top of both files and is frozen — a bundle registers `window.DL_GENERATORS['v9'] = launch` and receives `ctx` (`mount`, `configVersion`, `pluginVersion`, `beta`, `backToVersions`; `proxyUrl` is still passed for the frozen v9 bundle but is always `""`).

### Schema version lifecycle — what opens a version, and when it freezes

**Publication is what freezes a schema, not creation.** Get this wrong and you'll either freeze a version far too early (creating vN+1 for every small change) or edit one that real users already depend on.

**A new version opens on the first change to the config's *keys* since the last published version.** Specifically, any of:

- a key added
- a key removed
- a key renamed
- **the order of keys changed** — reordering alone is enough

**Comment-only changes never open a version.** If the text could be deleted without changing behaviour — the ASCII banners, explanatory comments, the generator hint, the trailer's `SHIPPED WITH …` suffix — it isn't a schema change. Rewording those against a published schema is fine and expected.

**While a version is open, edit it freely.** Between opening and publication, `docs/assets/configs/v<N>/` (bundle, `options.json`, template, mirror), its docs page, and the shipped `config.yml` are all normal editable files. Multiple features landing in the same release all accumulate into that one open version — there is nothing to coordinate or batch deliberately, it happens by construction.

**The release that ships it freezes it, permanently.** From then on the isolation rule above applies and the next key change opens vN+1.

**Versions are never skipped.** v9 → v10 → v11. Don't jump numbers to "reserve" one.

`registry.json` entries take `{ "config", "since", "generatorReady"? }`. **`since` is the first build shipping that schema and may itself be a nightly** (e.g. `"1.2.3-BETA.1"`) when a schema debuts in one — version comparison is BETA-aware, so `1.2.3-BETA.1 < 1.2.3-BETA.2 < 1.2.3`. Setting `"generatorReady": false` lists a schema *before* its bundle exists: the picker names it, explains it isn't available yet, and disables Continue rather than failing to load a missing script.

**Adding a new config schema (`v<N>` = the new one, `v<N-1>` = the frozen one).** The plugin ships exactly **one** config — the current one — so the shipped file is *replaced*, while every website artifact for the old schema is *kept forever*. This has happened once so far, v9 → v10 in 2.2.0; the next one is v10 → v11.

| Path | Action | Why |
|---|---|---|
| `src/main/resources/config.yml` | **REPLACE** with v\<N\> content; trailer becomes `CONFIG VERSION V<N>` | The JAR only ever carries the current schema. `ConfigMigrator` migrates old user files forward at runtime. |
| `docs/assets/configs/v<N-1>/**` | **DO NOT TOUCH — ever again** | Bundle, options, templates, mirrors. Someone still running the older plugin must keep generating exactly the config they always did. |
| `docs/config/v<N-1>/index.md` | **KEEP FOREVER** | Those users still need their docs. Old schema docs are never deleted, only superseded. |
| `docs/assets/configs/v<N>/**` | **CREATE** — copy `v<N-1>`'s folder, then adapt it | Copy forward; never refactor an old schema in place. All six files: bundle, `options.json`, both templates, both mirrors. |
| `docs/config/v<N>/index.md` | **CREATE** | New docs page for the new schema. Bring `lang.yml.txt` with it. |
| `registry.json` | **ADD** one line: `{ "config": "v<N>", "since": "<first build shipping it>" }` | Every older entry stays untouched. |

Nothing else. The generator picker, the config-docs index list, and BETA gating all derive from that one registry line plus the releases API.

**Only the live schema is checked against Java.** `check_version` takes the shipped schema and skips the Java-source cross-check for every frozen bundle. A frozen schema's `configKey`s describe the plugin that shipped *it* — v9 reads `log.player.join`, v10 reads `log.player.join.enabled`, and neither is wrong. Checking a frozen bundle against current Java would demand an edit to a file that must never change again. This was found the first time two schemas coexisted; before that the distinction could not surface.

**How the drift guard follows the schema forward** (verified by simulating the whole v10 transition): `scripts/validate-config-generator.py` reads the *shipped* config's own trailer to decide which mirror to compare against. The moment that trailer says `V10`, it compares against `docs/assets/configs/v10/config.yml` and stops comparing v9 entirely — because v9 is frozen history, not a live copy. v9 doesn't go unchecked though: each docs page is validated against **its own** schema's mirror, so v9 stays internally consistent forever without ever being measured against a newer plugin config. In short: **live copies are checked against each other; frozen versions are checked only against themselves.**

### 🚨 RUNBOOK — launching a new config schema version

Follow this in order. It is the complete list; anything missed here shows up either as a broken generator for real users or as a config the plugin silently misreads. Read the lifecycle rules above first — this is the *how*, those are the *when*.

**Terminology.** `V<N>` (the trailer, uppercase) and `v<N>` (paths and registry, lowercase) are the same number in different casings. Both appear below exactly as they must be typed.

#### Phase 0 — confirm a new version is actually warranted

Do not start until the change genuinely opens a version: a key **added, removed, renamed, or reordered** since the last *published* schema. Comment-only edits do not count. If the current schema is still **open** (opened since the last release and not yet shipped), there is no new version to launch — just edit the open one and stop here.

```bash
grep -n "CONFIG VERSION" src/main/resources/config.yml   # what the JAR currently ships
```

Compare that against the newest entry in `docs/assets/configs/registry.json`. If the trailer is **ahead** of the newest registry entry, the schema is already open and unpublished.

#### Phase 1 — the plugin side (what the JAR ships)

1. **Edit `src/main/resources/config.yml`** — the new keys, in their final order. This file is *replaced*, not copied; the JAR only ever carries the current schema.
2. **Update its trailer** to `# CONFIG VERSION V<N>, SHIPPED WITH v<x.y.z> (x-release-please-version)`. Leave the `SHIPPED WITH` version alone — release-please rewrites it, and `build-artifact` appends `BUILT <date>`. Only the `V<N>` is yours to change.
3. **Add matching `getBoolean`/`getString` defaults in the Java** for every new key. The fallback in code must equal the default in `config.yml`; `validate-config-generator.py` fails the build if they disagree, because a mismatch means the documented default is a lie for anyone who deletes the line.
4. **Nothing else in the plugin needs the number.** `ConfigMigrator` reads the trailer at runtime, and `ConfigVersionNotice` derives everything from it — see *Config version enforcement* below. Never hardcode a schema number in Java.

#### Phase 2 — the website generator (the part with the isolation rule)

5. **`cp -r docs/assets/configs/v<N-1> docs/assets/configs/v<N>`** — copy forward, then adapt the copy. Never refactor the old folder in place.
   - **Immediately change `const CONFIG_VERSION` at the top of the copied `generator.js`.** It drives three things at once: the key the bundle registers under (`window.DL_GENERATORS[...]`), the directory it fetches `options.json` and the template from, and the version shown to the user. Left at the old value, the new bundle registers as the *previous* schema and serves the previous schema's data — so the new generator silently emits the **old** config. Valid YAML, wrong file, no error anywhere. `validate-config-generator.py` now checks this (it was missed once and only surfaced by driving the UI). The same copy-forward trap bit the **style element's id**, which is built from `CONFIG_VERSION` for exactly this reason: hardcoded, it made `injectStyles()` a no-op for anyone who opened the older generator first in the same session, silently dropping every rule the new bundle added.
6. **Adapt the new bundle**: `generator.js` (registers `window.DL_GENERATORS['v<N>']`), `options.json`, the template, and the `config.yml` mirror. The mirror must match `src/main/resources/config.yml` byte for byte apart from the trailer's `BUILT` suffix.
7. **DO NOT TOUCH `docs/assets/configs/v<N-1>/**` ever again.** Someone still running the older plugin must keep generating exactly the config they always did.

#### Phase 3 — the docs page

8. **Create `docs/config/v<N>/index.md`** by copying the previous version's page, then updating: the heading, the option tables, and the `## Full config.yml` fenced block (which must match the shipped file — the validator checks this too).
9. **Keep `docs/config/v<N-1>/index.md` forever.** Old schema docs are superseded, never deleted.
10. **The config-docs index needs no edit** — it renders from `registry.json` at runtime.

#### Phase 4 — the registry (one line, and the invariant that bites)

11. **Add one entry** to `docs/assets/configs/registry.json`:

    ```json
    { "config": "v<N>", "since": "<first build that ships it>" }
    ```

    Leave every older entry untouched.

12. **`since` must be a build that actually ships this schema.** It may be a nightly (`"2.3.0-BETA.1"`) when a schema debuts in one; comparison is BETA-aware. **The invariant:** the newest registry entry must match the trailer in `src/main/resources/config.yml`. Listing a schema the plugin isn't shipping yet captures newer releases and sends them to a generator that doesn't exist. This has bitten before — a speculative v10 entry was added and had to be removed because it would have broken 2.2.0. `check_registry_matches_shipped_schema()` now fails the build on it in both directions, so **add the registry entry in the same PR that bumps the shipped trailer**, not before.
13. If the bundle isn't finished yet, set `"generatorReady": false` — the picker names the schema, says it isn't available, and disables Continue instead of failing on a missing script.

#### Phase 5 — verify before opening the PR

```bash
python3 scripts/validate-config-generator.py
```

That cross-checks `options.json` ↔ template ↔ Java source ↔ shipped config ↔ mirror ↔ doc embed, and the Java fallbacks against the config defaults. It follows the *shipped* trailer, so the moment it says `V<N>` it compares against `v<N>` and stops checking `v<N-1>` — frozen versions are only ever checked against themselves.

```bash
python3 scripts/sync-versions.py && git diff docs/_data/versions.yml
```

`versions.yml`'s `schema` key is derived from the shipped trailer, so it should flip to `V<N>` on its own. If it doesn't, the trailer is malformed. Never hand-edit that file.

Then build and confirm the JAR carries the right schema:

```bash
mvn -B -ntp -DskipTests clean package && unzip -p target/*.jar config.yml | tail -1
```

#### Phase 6 — publication freezes it

14. Merging the Release PR ships the schema and **freezes it permanently**. From that moment `docs/assets/configs/v<N>/` and the shipped keys are history: the next key change opens `v<N+1>`.
15. **Versions are never skipped**, and a schema that was opened but never published is not "used up" — it stays open and keeps accumulating changes until a release ships it.

#### Per-event sub-options (`extras`)

An event can carry extra keys beside `enabled` and `color` — `log.player.death.show_coords` is the first. The generator handles these generically, so **adding another needs no JavaScript**: declare it in `options.json` under the item's `extras`, add a matching `{{EXTRA_<key>}}` slot to the template, ship the key in `config.yml`, and read it in Java.

```json
"extras": [
  { "key": "player.death.show_coords",
    "configKey": "log.player.death.show_coords",
    "label": "Include coordinates in death messages",
    "note": "Anyone who can read the channel can find the body and its dropped items.",
    "default": false }
]
```

Two behaviours worth preserving: **"Select all"/"Select none" deliberately skip sub-options** (the selector excludes `.cfg-check--sub`), because turning every event on should never silently start broadcasting death locations; and the CSS must stay scoped `#cfg-gen .cfg-check--sub` *after* the base rule, since `#cfg-gen .cfg-check` uses the `margin` shorthand and would otherwise reset the indent.

The validator checks each `extras` entry the same way as `configKey`: the template must have the slot, and for the live schema some Java must read it.

#### What each of the four config copies is for

Phases 1–3 touch three different files that all look like "the config". They are not interchangeable — see *Config file dictionary* above for the full breakdown, and check all four before claiming a schema change is done.

### How a config's schema is identified

Three sources, in decreasing durability, resolved by `ConfigMigrator.detectVersion`:

1. **Its shape** — which keys exist (`SchemaDetector.infer`). The arbiter. A file's schema is not a claim it makes, it is what the file *is*; a config declaring v10 while lacking every v10 key is a v9 file with a bad label.
2. **The `config-version` key** at the top of the file. Replaced a comment on the *last* line, which was the least durable thing in a config — editors strip comments, formatters move them, and tidying the end of a file removes one without anyone noticing. When it vanished, migration was skipped and every option silently fell back to its default.
3. **The trailer comment**. Retained because every v9-and-earlier config in existence has one and no key — the files that most need upgrading are exactly the ones with only this.

On disagreement the **shape wins** and the mismatch is logged. `config-version` is excluded from the transplant in `resolvePath`: the newly written file already declares its own schema, and copying the old number forward would relabel a v10 file as v9 and make the next start migrate it again.

Each version's marker is the first key that appeared in it, so deleting an unrelated option cannot drop a file a version. **v4 and v5 have identical key sets** and are genuinely indistinguishable; reporting the newer is safe because nothing changed between them.

### Config version enforcement (plugin side)

`ConfigMigrator` compares the trailer in the user's `config.yml` against the one baked into the running JAR and returns a `Result(status, installed, shipped)`. The decision is `ConfigMigrator.decide(installed, shipped)`, deliberately split out as a pure function so it can be tested without a running server:

| On disk vs JAR | Status | Behaviour |
|---|---|---|
| no config yet | `FRESH_INSTALL` | Shipped default written out. Silent. |
| same | `UP_TO_DATE` | Nothing. Silent. |
| **older** | `UPGRADED` | Migrated forward automatically, user values transplanted, previous file kept as `config.old.yml`. |
| **newer** | `AHEAD` | **File left untouched.** Console warning every start; ops warned on join; `/discordlogger reload` warns too. |
| trailer missing either side | `UNKNOWN` | Warns that the trailer can't be read. No migration. |

**Migration runs one schema at a time.** `resolvePath(path, defMap, from, to)` walks `from`→`to` applying each step's renames in turn, rather than jumping straight to the target. This matters because renames compose: colours were flat (`embeds.colors.player_join`) until v7 nested them, and moved beside their toggle in v10. A v6 config jumping straight to v10 would match only the v9→v10 renames, so every colour the user had set would match nothing and be silently replaced by a default. Stepping 6→7→8→9→10 renames the key at each hop so it arrives in a shape the target recognises.

Schema history, recovered from the shipped config's git history — **update this when adding a step**:

| Step | What moved |
|---|---|
| v2→v3, v3→v4, v4→v5, v5→v6 | nothing; pure additions |
| **v6→v7** | colours flat → nested (`embeds.colors.player_join` → `embeds.colors.player.join`); moderation colours gained their group; `embeds.colors.server` (a scalar fallback) became a section and has no successor |
| v7→v8, v8→v9 | nothing; pure additions |
| **v9→v10** | colours moved to `log.<group>.<event>.color`; toggles became `log.<group>.<event>.enabled` |

**A step that only adds keys needs no entry** — existing paths carry over untouched, which is why `step()` defaults to identity. **A step that moves or renames a key MUST get a `case` in `step()`**, or every upgrade from before it silently loses those settings. That is the single easiest way to break this quietly.

**Migration only ever runs forward.** This is the important invariant: before, migration fired whenever the two numbers *differed*, so a config from a newer install — a rollback, or a file copied between servers — was silently rewritten against the older shipped default, deleting every key the newer schema had added. It now refuses, because the plugin cannot know what those keys mean.

`AHEAD` is the only state needing a human, so it is the only one that registers a join listener (`ConfigVersionNotice`). The escape hatch is **`/discordlogger regen confirm`** (`discordlogger.regen`, op by default): it replaces `config.yml` with this build's default and renames the old file to `config.backup-<timestamp>.yml`. It requires the literal `confirm` argument because `regen` and `reload` are one keystroke apart and one of them is destructive. It deliberately does **not** merge or preserve settings — the entire point is to land on a file this build fully understands.

- **Webhook testing / CORS:** Discord webhooks allow simple browser POSTs; each bundle carries its own test payload. Tests go browser → Discord directly. A Cloudflare Worker relay used to exist for this and was deleted — it was never deployed and never needed. The **frozen** v9 bundle still contains the dormant `proxyUrl` branch in its `sendTest`; it is inert (the key no longer exists in `registry.json`, so the value is `""`) and must not be edited, because publication froze that schema. Newer bundles should simply omit it.

### Config generator and config docs — stable schemas only

Neither offers a config version that has only ever shipped in a nightly, and there is **no opt-in**. A nightly's schema can still change before release, so a config generated against one can be silently wrong by the time the stable build lands — worse than not offering it at all. Both carry a footnote explaining the absence instead of a toggle.

The beta opt-in in `versions.js` still exists and still drives the **downloads page**, which is a different question: choosing which *build* to install is the user's call, choosing a config format that may still move is not. `window.DLVersions.showBeta` is deliberately ignored by both the generator and the config-docs picker — setting it true does not surface beta schemas.

**A schema is offered only once a release shipping it has been PUBLISHED.** Two independent reasons to hide one, and both apply: its `since` carries `-BETA.`, so the format itself can still move; or no published release carries it yet, so it documents a file nobody can have. `coverage()` in `docs/config/index.md` returns null in that second case and the row is dropped; the generator no longer injects a schema's `since` as a pickable build.

**This reverses the earlier rule, deliberately, and the cost is real.** It used to list any schema whose `since` was a stable string on the reasoning that the schema was final and hiding it only hid it from the nightly users already running it. That reasoning still holds — those users now get the previous schema's generator until release day. It was overridden because a picker of *builds to configure* should not offer a build nobody can download. Unreleased schemas are still developed in the open: the bundle, the mirrors and the docs page all exist and are reachable by URL from the moment they are created. **Do not flip this back without the same deliberation** — both sides are recorded here precisely so it is a decision rather than a drift.

**Superseded schemas get a banner automatically, keyed on the PLUGIN version.** `applyLegacyNotice` in `versions.js` reads the page's schema from the URL (`/config/v10/`) and asks a plugin-version question, not a schema one: *does the newest published release fall under this page's schema?* Schema numbers cannot answer what a reader actually wants to know — 2.2.0 and 2.3.0 are both v10, so a page covering only 2.2.0 is behind while its schema is current. The generator does the same check on the build the visitor picked, so choosing an old release warns even when its schema is still the live one.

Three outcomes, and the third is the one that is easy to get wrong: the covering schema gets no banner; an older one gets *"this page is for an older version"*; and a schema **ahead** of the current release gets *"this is an upcoming version"*. Labelling an unreleased schema "outdated" is the exact opposite of the truth, and those pages are reachable precisely because unreleased work is developed in the open.

Derived from the URL rather than written into each page for two reasons: publication freezes a schema's files, so a banner in `docs/config/v9/index.md` would mean editing what must not change; and deriving it means every future version gains the notice on release day with nothing to remember.

A schema's docs page is reachable by URL regardless. `registry.json`'s `since` pointing at a stable version remains the default — it is now what makes a finished schema public rather than what delays it.

### Downloads page

Renders straight from the releases API. Nightly builds (tag matches `-BETA.N`) get a dedicated purple **"Nightly"** badge and a purple card edge; any *other* pre-release keeps the generic "Pre-release" badge. Nightlies are **hidden behind an opt-in toggle** (remembered per visitor, same shared slider component) with a plain-language stability warning.

## Gotchas — learned the hard way, don't relearn these

- **`X | None` union-type syntax in `scripts/*.py` needs `from __future__ import annotations`** at the top of the file, or it crashes at import time on Python < 3.10 (the local dev machine runs 3.9; don't assume CI's Python version is what you're testing against).
- **Every new GitHub Actions workflow needs an explicit, minimal `permissions:` block.** A workflow without one trips CodeQL's "Workflow does not contain permissions" finding. Default to `contents: read` and add only what the workflow actually needs (`pull-requests: read`, `contents: write`, etc.).
- **GITHUB_TOKEN-created events don't trigger other workflows.** A separate workflow listening for `release: published` will never fire for a release that release-please itself created — this is why the JAR-build step lives inside `release-please.yml`'s own run instead of a standalone listener.
- **A required branch-protection check that can't yet report will block every PR forever.** If you add a new required status check (e.g. `lint-pr.yml`), it can only start reporting once its own workflow file exists on `main` — don't mark it required until after that first merge, or remove it from the required list temporarily while the workflow itself is mid-rollout.
- **`squash_merge_commit_title` must be `PR_TITLE`, not the GitHub default `COMMIT_OR_PR_TITLE`.** Otherwise a single-commit PR's original (unvetted) commit message silently wins over the lint-checked PR title when squashed onto `main` — quietly defeating the whole point of `lint-pr.yml`.
- **Bot-authored PRs (release-please's Release PR) will show no CI checks at all**, and that's expected, not broken — `pull_request_target` workflows don't execute against them the normal way. Merge with the admin/override option.
- **`schedule`-triggered workflows only run using the copy of the workflow file on the repo's default branch.** If you edit `nightly.yml` on a feature branch, the cron won't pick up the change until that branch merges — use `workflow_dispatch` to test it in the meantime.
- **A nightly's channel is a compiled-in fact (`BuildInfo`), never parsed from its own version string.** A version string is trivially renameable; don't add any logic anywhere that infers `stable`/`nightly`/`dev` by pattern-matching a version number instead of reading `BuildInfo`.

## Conventions

- **Java 17 bytecode, Paper API only.** 17 is the compile target because 1.19 servers run on it; do not reach for a newer language feature without moving `<maven.compiler.release>`, which raises the floor for every server on the list. Adventure preferred for anything new touching chat components (`ChatColor` lingers in command feedback).
- Final classes, private constructors on static utility classes, `LinkedHashMap` where iteration order matters.
- Config keys: lowercase snake_case, grouped `log.<category>.<event>`.
- Every new logged event, in lockstep: listener (with live config gate) + `EventRegistry` registration + `log.*` key in `config.yml` + default color in `Log.init` + generator `options.json` (including its `defaultColor`, which must match the plugin default) / template entries + docs mention. The validator catches the generator-side half in CI.
- **Never hardcode a version number in the website.** Use the `data-dl-*` hooks from `versions.js`; if something genuinely can't be derived, that's a signal the data model needs the fact once, not the page needing a literal.
- Escape user-visible strings via `Log.mdEscape`; prefer structured `Log.Field` embeds for multi-datum events.

## Things NOT in this repo (avoid confusion)

- **No linter/formatter config (Java).** There *is* a test suite — JUnit 5 under `src/test/`, gating CI. Any doc claiming otherwise is stale.
- **No `generator.config.js`** — the old `DL_VERSIONS`/`DL_CONFIGS`/`DL_TEST_EMBED` globals are gone, replaced by `registry.json` plus per-bundle payloads.
- **No committed AI/editor tooling config.** `CLAUDE.md`, `.claude/`, `.cursor*`, `.aider*`, `.windsurfrules`, `*.local.md` and friends are gitignored — they're local preference, not project state. `AGENTS.md` (this file) is the one tracked, AI-agent-facing reference; `ARCHITECTURE.md` and `CONTRIBUTING.md` serve that role for humans.
- **No `dependency-reduced-pom.xml`.** It was committed historically; it's gitignored and its generation is disabled (`<createDependencyReducedPom>false</createDependencyReducedPom>` in the shade plugin config). It only matters when publishing to a Maven repo so consumers don't inherit shaded deps — this plugin ships as a JAR on GitHub Releases and is never consumed as a Maven artifact. Don't re-add it.
- **No `release-spec.md` / `release-changelog-builder-config.json` / `release-on-merge.yml`** — replaced by release-please. Any reference to them (old docs, old issues, old habits) is stale.
- **No `dev` branch** — retired in favor of trunk-based development on `main`.
- **The July 2026 discarded work is not the current v10.** An unreleased effort (v2.1.7 + a website rewrite + a "config v10") was deliberately abandoned then; the maintainer keeps an archive outside the repo. The v10 that exists today was built fresh afterwards and shipped in **2.2.0** — it is real, current, and in `docs/assets/configs/v10/`. Old notes calling v10 speculative or nonexistent describe the abandoned attempt, not this one.
