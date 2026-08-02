# AGENTS.md — DiscordLogger

**This file is written for AI coding agents working in this repository. It is not the human-facing docs.** If you're a person, read [ARCHITECTURE.md](ARCHITECTURE.md) (how the project works, narrative form) and [CONTRIBUTING.md](CONTRIBUTING.md) (how to submit a PR) instead — they cover the same ground with less density and no assumption you'll retain all of it in one pass. This file is optimized for the opposite: maximum accurate detail per token, safe to re-read in full every session, safe to grep.

Everything below was verified against the actual source at the time of writing; when in doubt, the code wins — and if you find this file wrong, fix it in the same PR.

## What this project is

**DiscordLogger** is a Minecraft **Paper** server plugin (Java 25) that posts server events to a Discord channel via **webhooks** — either as rich embeds (per-event colors, player-head thumbnails, timestamps) or as plain Markdown text. It ships with a versioned `config.yml` that auto-migrates between schema versions, a channel-aware update checker, and a companion **Jekyll website** (in `docs/`) hosted on GitHub Pages at `https://discordlogger.godtiergamers.xyz` that includes an interactive config generator.

- **Current plugin version:** tracked by `pom.xml` / `.release-please-manifest.json` — never hand-edit either, see **Releases** below.
- **Current config schema:** **v9** (trailer comment in `src/main/resources/config.yml`, e.g. `# CONFIG VERSION V9, SHIPPED WITH v2.1.6 (x-release-please-version)`)
- **Paper API:** `26.2.build.87-stable` (`provided` scope), `api-version: 26.1`, compiled for **Java 25**
- **GitHub:** `GodTierGamers/DiscordLogger`

## Working agreement (binding — not suggestions)

1. **Trunk-based**: branch off `main` (`feat/<name>`, `fix/<name>`), PR into `main`. Never commit directly to `main`.
2. **Conventional Commit PR titles** (`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` / `ci:` / `test:` …) — `lint-pr.yml` rejects anything else. The title becomes the changelog entry verbatim. Squash-merge.
3. **Verify before PR**: `mvn -B -ntp clean package` must pass; for listener/config changes, exercise on a real Paper server when practical.
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

**Never hand-type a Java version, Paper version or api-version anywhere except `pom.xml`.** Four properties there feed everything else:

| Property | Meaning |
|---|---|
| `<version>` | the plugin version — **release-please owns it**, never hand-edit |
| `<maven.compiler.release>` | Java the plugin is built for |
| `<dl.api.version>` | minimum Paper; becomes `plugin.yml`'s `api-version` |
| `<dl.paper.display>` | how Paper is written in prose, e.g. `26.x` |

The sync script also derives two values it doesn't own: **`plugin`** (the released version, from `<version>`) and **`schema`** (the config schema, read from `config.yml`'s trailer). Docs examples of the config trailer use these, so they can't go stale when a release ships or the schema moves.

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

**Tests exist and gate CI** (JUnit 5 + surefire; `ci.yml` runs `mvn clean package` with tests on). `mvn test` runs them in ~10s.

They cover `ConfigMigrator` and webhook redaction specifically, because those are the two places a silent bug destroys something the user cannot get back: their settings, or their webhook's secrecy. When touching either, **add the case to the test rather than only checking by hand** — all five suites are written against the genuinely shipped `config.yml` and read the schema number from its trailer, so they keep testing the real thing across schema bumps without edits.

Verified to actually fail: reintroducing bidirectional migration, dropping the v6→v7 step, letting the config writer strip comments, and disabling redaction each break the suite.


```bash
mvn -B -ntp clean package     # compile + shade → target/discordlogger-<version>.jar
mvn -B -ntp clean compile     # compile only (faster sanity check)
```

- **There is no test suite.** `mvn package` compiling cleanly is the only automated check. Real verification means dropping the shaded JAR into a Paper server's `plugins/` folder.
- The shade plugin relocates SnakeYAML to `com.discordlogger.shaded.snakeyaml` and excludes its `META-INF`. `minimizeJar` is deliberately **off** (ASM/modern bytecode issues).
- **Maven resource filtering applies ONLY to `plugin.yml` and `build-info.properties`** (for `${project.version}` / `${dl.build.channel}` / `${dl.build.date}`). `config.yml` is copied **verbatim** — it contains `$` characters in ASCII art that must never be filtered. Don't add filtering to it; CI stamps its trailer via targeted regex replacement instead.
- A plain local `mvn package` produces a **`dev`-channel** build (`dl.build.channel` defaults to `dev` in `pom.xml`) — see `BuildInfo`.

## Repository layout

```
pom.xml                                Maven build
release-please-config.json             release-please: changelog sections, extra-files
.release-please-manifest.json          release-please: current released version (state)
scripts/validate-config-generator.py   CI check: options.json <-> template <-> Java source <-> shipped config <-> mirror <-> doc embed
scripts/sync-versions.py               Propagates pom.xml's version values into README/CONTRIBUTING/docs
scripts/publish-listings.py            Mirrors stable releases to Modrinth/Hangar; writes the combined downloads badge
src/main/resources/
  plugin.yml                           Plugin descriptor (Maven-filtered)
  build-info.properties                Build channel/version/date, baked in at package time
  config.yml                           Default config, schema v9 (NOT filtered) — file 1 of 4, see dictionary
src/main/java/com/discordlogger/
  DiscordLogger.java                   Plugin entry point (onEnable/onDisable)
  log/Log.java                         Static logging facade (the API everything calls)
  webhook/DiscordWebhook.java          Manual JSON building + one HTTP POST, reports outcome
  webhook/WebhookQueue.java            Single-threaded send queue: rate limiting, retries, ordering
  config/ConfigMigrator.java           Comment-preserving config version migration
  event/EventRegistry.java             Registers all listeners; fires start/stop
  event/ServerStart.java               Static handler (not a Listener)
  event/ServerStop.java                Static handler (not a Listener)
  command/Commands.java                Subcommand router (executor + tab completer)
  command/Subcommand.java              Interface: name/description/permission/execute/tabComplete
  command/Reload.java                  /discordlogger reload
  update/BuildInfo.java                Reads build-info.properties (channel/version/built)
  update/NightlyNotice.java            Nightly-channel warnings (console + first-boot op chat)
  update/UpdateChecker.java            Async, channel-aware GitHub release check on startup
  util/Names.java                      Nickname resolution + cache ("Nick (Real)")
  listener/player/                     PlayerJoin, PlayerQuit, PlayerChat, PlayerCommand,
                                        PlayerDeath, PlayerAdvancement, PlayerTeleport, PlayerGamemode
  listener/server/                     ServerCommand, Explosion
  listener/moderation/                 Ban, Unban, Kick, Op, Deop, Whitelist
docs/                                  Jekyll website (GitHub Pages, deploys from main)
  config/v9/index.md                    Config docs page — embeds file 4 of 4, see dictionary
  assets/js/versions.js                 Site-wide version awareness + BETA badges/gating
  assets/js/generator.js                Config generator loader (schema picker)
  assets/configs/registry.json          One entry per config schema
  assets/configs/v9/                    Self-contained v9 generator bundle + data (frozen once v10 ships)
.github/workflows/
  ci.yml                               Build + docs-validate on push/PR to main (path-filtered)
  lint-pr.yml                          Enforces Conventional Commit PR titles
  release-please.yml                   Rolling Release PR on main + builds/attaches the stable JAR
  nightly.yml                          Cron + manual nightly beta builds from main
.github/dependabot.yml                 Weekly maven/github-actions/bundler dependency PRs
```

## Branches, releases, and the nightly channel

**Trunk-based development.** `main` is the only long-lived branch; short-lived `feat/*` / `fix/*` branches PR into it. (A `dev` branch existed historically — it has been retired; if you see references to it anywhere, they're stale.)

### Releasing
1. Conventional commits accumulate on `main` (squash-merged PR titles).
2. `release-please.yml` maintains a rolling **Release PR**: version bump computed from commit types (`fix:` → patch, `feat:` → minor, `!`/BREAKING → major), `CHANGELOG.md` from commit titles, `pom.xml` bumped natively, and `config.yml`'s annotated trailer line rewritten (the `(x-release-please-version)` marker — `ConfigMigrator` ignores everything after the `V<n>` number, verified).
3. **Merging the Release PR is the release.** The next `release-please.yml` run (triggered by that merge) tags `v<version>` and publishes the GitHub Release with the changelog as its body.
4. The same workflow run's `build-artifact` job then checks out the tag, stamps `BUILT <DD-MM-YYYY>` onto the trailer, builds with `-Ddl.build.channel=stable`, and attaches `DiscordLogger-v<version>.jar` + `.sha256`. (The build lives in the same run rather than a `release: published` listener because events created with the built-in `GITHUB_TOKEN` don't trigger other workflows — a separate listener would never fire. See Gotchas.)
5. **Config schema revisions (v9 → v10) stay manual and deliberate** — bump the `V<n>` trailer, add `docs/assets/configs/v<n>/`, wire the generator config. Never inferred from commits.
6. **Force a specific version:** a commit whose message has a `Release-As: X.Y.Z` footer retargets the Release PR (and `nightly.yml`'s version computation, which honors the same footer) to that exact version regardless of what the conventional-commit math would produce.

### Distribution — GitHub is the only host that serves a JAR (almost)

Stable releases are mirrored onto two listings, because that is where server owners look:

| Listing | Slug | How the version is registered |
|---|---|---|
| Modrinth | `discordlogger` | The JAR is **uploaded**. Modrinth's API has no external-URL field — checked against its OpenAPI spec — so this is the one place a second copy of the file exists. |
| Hangar | `LVCHLANN/DiscordLogger` | Registered with `externalUrl` pointing at the GitHub Release asset. No copy is hosted, and Hangar's download button increments **GitHub's** counter. |

`scripts/publish-listings.py`, run by `release-please.yml`'s `publish-listings` job, does both. It is idempotent (re-running skips versions that already exist), refuses to publish if `pom.xml`'s version doesn't match the release tag, and refuses nightly tags outright — nightlies are GitHub-only, as the downloads page states.

**Download counting.** The count spans two hosts, so the README badge is a shields.io *endpoint* badge reading `docs/assets/badges/downloads.json`, written by `publish-listings.py --badge`: GitHub asset downloads (excluding `.sha256` files) **+** Modrinth's total. Hangar is deliberately not added — its traffic is already inside the GitHub number, and adding it would double-count. `downloads-badge.yml` refreshes it daily; releases refresh it inline.

**Two secrets must exist** or the corresponding platform is skipped with a notice (a lagging listing is recoverable; a release job dying after tagging is not): `MODRINTH_TOKEN` (PAT, "Create versions" **plus one read scope** — either "Read analytics" or "Read user info") and `HANGAR_API_KEY` (create_version permission). Both are repository **Actions** secrets — the job declares no `environment:`, so environment secrets would not resolve.

Modrinth's `VERSION_CREATE` scope covers exactly one endpoint — the publish call — so a token holding only that scope cannot be verified without actually publishing. One read scope is therefore required in addition, purely to give `--check-auth` a harmless authenticated endpoint. It probes two and passes if **either** answers, so it doesn't care which was granted:

| Probe | Scope | Note |
|---|---|---|
| `GET /v3/analytics/downloads` | Read analytics | Analytics exists only on **v3**; v2 returns 404. `project_ids` must be percent-encoded — sent raw, it 400s *before* auth is checked, which would silently defeat the probe. |
| `GET /v2/user` | Read user info | |

Modrinth answers both "expired" and "wrong scopes" with a bare 401, so the failure message names both possibilities rather than implying the token is dead.

The downloads badge does **not** use analytics — Modrinth's public project endpoint already exposes the total with no auth at all. Analytics would only be needed for time-series or per-version breakdowns.

Modrinth PATs expire. `check-listing-credentials.yml` runs `publish-listings.py --check-auth` weekly so a dead token is caught by a failed scheduled run rather than by a release that has already tagged. Note that `--dry-run` makes no authenticated call at all and proves nothing about the tokens; `--check-auth` is the mode that does.

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
Path-filtered (`dorny/paths-filter`): `build` runs on `src/**`/`pom.xml` changes; `validate-generator-data` runs on `docs/assets/configs/**` changes; both on a mixed PR. Concurrency cancels superseded runs. PR builds upload the JAR as an artifact.

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
- **Color resolution:** category strings are normalized (lowercase; spaces/dots/dashes/slashes → `_`) then looked up, so `"Player Join"` → `player_join`. Defaults hard-coded in `Log.init`, overridable via `embeds.colors.*` (nested or flat keys). Unknown categories fall back to the `server` color.
- Valid webhook URL prefixes: `discord.com`, `discordapp.com`, `ptb.discord.com`, `canary.discord.com` (all `https://…/api/webhooks/`).

### `DiscordWebhook`
- JSON built **by hand with StringBuilder** (no JSON library); `escape()` handles quotes/backslashes/control chars — keep escaping every interpolated string.
- `dispatch()` hands off to `WebhookQueue` and returns immediately — callers may be on the main thread and must never block on HTTP.
- `post()` performs one request and **returns a `Response`** (status + rate-limit headers) rather than logging; the queue owns retry/wait/give-up decisions.

### Log filtering (`filters:`)

Applied in the **listener**, not in `Log` — that is where the player, world and raw command still exist; by the time a message reaches `Log` it is rendered prose. `Filters` holds an immutable snapshot swapped atomically on reload, and `applyRuntimeConfig` reloads it *before* `Log.init` so a reload cannot briefly log something the new config filters.

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
- Bounded at 1000 messages; beyond that it drops and warns once per outage rather than growing until the server dies.
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
- **Moderation listeners are command sniffers, not API hooks:** they watch `PlayerCommandPreprocessEvent` + `ServerCommandEvent`, parse the raw command, gate on vanilla/Bukkit/Essentials permission nodes (console always allowed), then **verify the state actually changed on the next tick** before logging. `Kick` is two-phase (intent map keyed by target UUID → confirmed by `PlayerKickEvent`, stale entries cleaned after 2 ticks).
- Notable: `PlayerJoin` delays 2 ticks for nickname plugins; `PlayerQuit` defers cache eviction 1 tick; `PlayerChat` uses Paper `AsyncChatEvent` + Adventure serializer (why Paper API is required); `PlayerAdvancement` skips `recipes/*` and `*/root`; `PlayerDeath` builds Geyser-friendly messages from damage context; `Explosion` handles entity+block explosions with CDN icons and a 20-block nearby-player list; `ServerStart`/`ServerStop` are static handlers called by `EventRegistry`, not listeners.

### Commands
- Root `/discordlogger` (aliases `/dlogger`, `/dlog`). The **command itself is deliberately ungated** — it used to require `discordlogger.reload`, which would have locked a `regen`-only admin out of the whole command once a second subcommand existed. `Commands` routes `Subcommand` implementations (LinkedHashMap) and filters both help and tab-complete by each subcommand's own permission, so an unprivileged sender sees nothing and can run nothing.
- Subcommands, all default op: `reload` (`discordlogger.reload`), `webhook <url>` (`discordlogger.webhook`), `regen confirm` (`discordlogger.regen`). `regen` requires the literal `confirm` — it is destructive and one keystroke from `reload`.
- **`webhook` never echoes the URL.** It is a bearer credential, so confirmation names the channel id only, tab-complete returns nothing, and `Log.redactWebhooks` masks the token in command logging — without which running the command would publish the new URL to the channel currently configured, i.e. the old webhook. Redacting rather than suppressing keeps the audit trail: you still see that someone changed it.
- **Never write config with `saveConfig()`.** Bukkit's YAML writer re-serialises the whole file and drops every comment, including the `CONFIG VERSION V<n>` trailer `ConfigMigrator` reads — a config saved that way looks like it has no schema at all. Use `ConfigMigrator.setScalar(file, path, value)`, which rewrites exactly one line (verified: one line changed, comment and line counts unchanged, trailer intact).
- Adding one: implement `Subcommand`, add to the `new Commands(...)` varargs in `onEnable`, register any new permission in `plugin.yml`.

### `UpdateChecker`
- Async on startup; skips for `dev` channel. Fetches the **releases list** (`/releases?per_page=50`) — not `/releases/latest` — because nightly builds need to see pre-releases. Parses `tag_name`/`prerelease` pairs by regex (no JSON library, intentional; see `parseReleases` for why it's safe) and ranks with a `SemVer` record where stable > any `-BETA.N` of the same version, higher N > lower.
- Stable channel: notify on any newer stable, pre-releases invisible. Nightly channel: notify on **every** newer stable, and on nightlies only when **more than 2** behind (`NIGHTLY_LAG_THRESHOLD`). Notifications = console banner + Discord webhook notice (embed or plain per config).

## Config reference (schema v10)

```yaml
webhook.url            ""            # plugin is console-only until valid
format.time            "[HH:mm:ss, dd:MM:yyyy]"  # Java DateTimeFormatter; plain-text mode only
format.name            ""            # plain-text server-name prefix (proxy setups)
format.nicknames       true          # "Nick (Real)" in player logs
embeds.enabled         true          # false → plain Markdown messages
embeds.author          "Server Logs"
embeds.colors.<cat>.<event>  "#RRGGBB"
log.player.{join,quit,chat,command,death,advancement,teleport,gamemode}
log.server.{command,start,stop,explosion}
log.moderation.{ban,unban,kick,op,deop,whitelist_toggle,whitelist_edit}
```
All `log.*` toggles ship as `true` in the default file.

### Config file dictionary — four places hold "the config," they are not the same thing

Four near-identical copies of the config content exist. Confusing them is the single most common way this repo drifts — it already happened twice: a hint-text reword landed on two of four copies and nobody noticed the other two for several sessions (one of them, the doc-page embed, had *already* been silently missing an entire banner block before anyone caught it).

**These files carry no in-file "which copy am I" markers, deliberately.** An earlier attempt put identity headers at the top of each one; every path a user obtains a config — fresh install, website download, and generator output — then handed them repo-internal notes referencing `AGENTS.md` and `scripts/`. User-facing artifacts must not leak repo internals, so the table below plus the CI check are the *only* mechanism keeping these in sync. Don't reintroduce in-file markers.

| # | Location | What it actually is | Consumed by |
|---|---|---|---|
| 1 | `src/main/resources/config.yml` | **The shipped config** — the real source of truth. Bundled inside the plugin JAR; every server gets this on first run. | `DiscordLogger.onEnable` / `ConfigMigrator` (Java, at runtime) |
| 2 | `docs/assets/configs/v9/config.yml` | **The download mirror** — a static copy served by the plain "Download" button on the config docs page. No wizard involved; just the file, verbatim. | A `<a download>` link in `docs/config/v9/index.md` |
| 3 | `docs/assets/configs/v9/config.template.yml` | **The generator template** — `{{TOKEN}}` placeholders, filled in by the wizard based on what the visitor chose. Never downloaded directly; its *output* is. | `docs/assets/configs/v9/generator.js` |
| 4 | The `## Full config.yml` fenced code block inside `docs/config/v<N>/index.md` | **The doc-page embed** — the full file shown inline in prose, for people reading the docs who don't want to click through. Easiest of the four to forget since it lives inside Markdown, not a config file. | Rendered directly on the config docs page |

**The rule:** 1, 2, and 4 must be **content-identical** — same real values, same comments, same banners — except each one's own trailer line (`SHIPPED WITH vX.Y.Z` vs `DOWNLOADED FROM WEBSITE`). `scripts/validate-config-generator.py` enforces this automatically in CI for both 1↔2 and 2↔4 — it will fail the build if any of them drift. File 3 isn't byte-compared (it has tokens instead of real values), but its `{{LOG_*}}`/`{{COLOR_*}}` tokens are cross-checked against `options.json` and the Java source by the same script.

**Practical consequence:** any edit to the real config content (webhook/format/embeds/log.\* structure or their comments, including the banners) touches file 1 first, then files 2 and 4 need the identical change, and file 3 needs the matching `{{TOKEN}}` version. Don't rely on memory for this — run `python3 scripts/validate-config-generator.py` locally before opening the PR; CI runs it too, but catching it before pushing is faster.

### ⚠️ Known inconsistency (still open — don't propagate it)
**Java fallback defaults don't all match config.yml.** `PlayerTeleport`, `PlayerGamemode`, and `Explosion` use `getBoolean(key, false)` while everything else uses `true` (and config.yml ships all `true`). The fallback only matters if the key is missing from a user's file, but the convention is *Java default == config.yml default* — fix toward `true` if you touch these. (Good first `fix:` PR.)

## Website (`docs/`)

Jekyll site (GitHub Pages gem stack) at `discordlogger.godtiergamers.xyz` (CNAME present). Pages use `_layouts/default.html` via `_config.yml` defaults; nav in `_data/nav.yml`. **Deploys from `main`** — docs changes go live on merge, independent of plugin releases.

- **Local dev:** `cd docs && bundle install && bundle exec jekyll serve --livereload --watch` (`docs/test.sh` does the same). `docs/_site/` is gitignored build output — never edit, never trust.
- The plugin hot-links icons from the site (`/assets/icons/…`) — the site being up is a runtime dependency of embeds. The banner is self-hosted at `/assets/DiscordLogger-Banner.webp` (compressed; no external image host).

### Version awareness — never hardcode a version number

**Never write "and newer" about schema coverage.** A schema is only known to be shipped by the releases that have actually shipped it — the next release may open a new one, so promising future coverage eventually becomes a lie on a page nobody revisits. `<span data-dl-schema-versions="v10">` fills itself from the releases API plus `registry.json`, listing only real releases, and says so plainly when none exists yet. The config-docs index uses the same rule.

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
  registry.json                    one entry per SCHEMA: { config: "v9", since: "2.1.5" }
  v9/generator.js                  SELF-CONTAINED bundle: steps, styles, webhook payload, YAML builder
  v9/options.json                  toggles/colors UI data (incl. defaultColor per event)
  v9/config.template.yml           {{TOKEN}} output template
  v9/config.yml                    reference copy of what shipped
```

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

**Adding a new config schema (worked example: v9 → v10).** The plugin ships exactly **one** config — the current one — so the shipped file is *replaced*, while every website artifact for the old schema is *kept forever*:

| Path | Action | Why |
|---|---|---|
| `src/main/resources/config.yml` | **REPLACE** with v10 content; trailer becomes `CONFIG VERSION V10` | The JAR only ever carries the current schema. `ConfigMigrator` migrates old user files forward at runtime. |
| `docs/assets/configs/v9/**` | **DO NOT TOUCH — ever again** | Bundle, options, template, mirror. Someone still running a v9 plugin must keep generating exactly the config they always did. |
| `docs/config/v9/index.md` | **KEEP FOREVER** | v9 users still need their docs. Old schema docs are never deleted, only superseded. |
| `docs/assets/configs/v10/**` | **CREATE** — copy v9's folder, then adapt it | Copy forward; never refactor an old schema in place. |
| `docs/config/v10/index.md` | **CREATE** | New docs page for the new schema. |
| `registry.json` | **ADD** one line: `{ "config": "v10", "since": "<first build shipping it>" }` | The v9 entry stays untouched. |

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
   - **Immediately change `const CONFIG_VERSION` at the top of the copied `generator.js`.** It drives three things at once: the key the bundle registers under (`window.DL_GENERATORS[...]`), the directory it fetches `options.json` and the template from, and the version shown to the user. Left at the old value, the new bundle registers as the *previous* schema and serves the previous schema's data — so the new generator silently emits the **old** config. Valid YAML, wrong file, no error anywhere. `validate-config-generator.py` now checks this (it was missed once and only surfaced by driving the UI).
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

12. **`since` must be a build that actually ships this schema.** It may be a nightly (`"2.3.0-BETA.1"`) when a schema debuts in one; comparison is BETA-aware. **The invariant:** the newest registry entry must match the trailer in `src/main/resources/config.yml`. Listing a schema the plugin isn't shipping yet captures newer releases and sends them to a generator that doesn't exist. This has bitten before — a speculative v10 entry was added and had to be removed because it would have broken 2.2.0.
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

A schema's docs page stays reachable by URL the whole time; it is simply not listed until a stable release carries it. This is what makes `registry.json`'s `since` pointing at a stable version the safe default.

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

- **Java 25, Paper API only**; Adventure preferred for anything new touching chat components (`ChatColor` lingers in command feedback).
- Final classes, private constructors on static utility classes, `LinkedHashMap` where iteration order matters.
- Config keys: lowercase snake_case, grouped `log.<category>.<event>`.
- Every new logged event, in lockstep: listener (with live config gate) + `EventRegistry` registration + `log.*` key in `config.yml` + default color in `Log.init` + generator `options.json` (including its `defaultColor`, which must match the plugin default) / template entries + docs mention. The validator catches the generator-side half in CI.
- **Never hardcode a version number in the website.** Use the `data-dl-*` hooks from `versions.js`; if something genuinely can't be derived, that's a signal the data model needs the fact once, not the page needing a literal.
- Escape user-visible strings via `Log.mdEscape`; prefer structured `Log.Field` embeds for multi-datum events.

## Things NOT in this repo (avoid confusion)

- **No test suite**, no linter/formatter config (Java).
- **No `generator.config.js`** — the old `DL_VERSIONS`/`DL_CONFIGS`/`DL_TEST_EMBED` globals are gone, replaced by `registry.json` plus per-bundle payloads.
- **No committed AI/editor tooling config.** `CLAUDE.md`, `.claude/`, `.cursor*`, `.aider*`, `.windsurfrules`, `*.local.md` and friends are gitignored — they're local preference, not project state. `AGENTS.md` (this file) is the one tracked, AI-agent-facing reference; `ARCHITECTURE.md` and `CONTRIBUTING.md` serve that role for humans.
- **No `dependency-reduced-pom.xml`.** It was committed historically; it's gitignored and its generation is disabled (`<createDependencyReducedPom>false</createDependencyReducedPom>` in the shade plugin config). It only matters when publishing to a Maven repo so consumers don't inherit shaded deps — this plugin ships as a JAR on GitHub Releases and is never consumed as a Maven artifact. Don't re-add it.
- **No `release-spec.md` / `release-changelog-builder-config.json` / `release-on-merge.yml`** — replaced by release-please. Any reference to them (old docs, old issues, old habits) is stale.
- **No `dev` branch** — retired in favor of trunk-based development on `main`.
- **Config v10 does not exist yet.** In July 2026 an unreleased effort (v2.1.7 + a website rewrite + a "config v10" with nested sub-option toggles) was deliberately discarded to start fresh; the maintainer keeps an archive of it outside the repo. References to config **v10** or nested sub-option toggles mean that discarded work, **not** the current codebase — v9 is the only schema that exists.
