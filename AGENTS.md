# AGENTS.md — DiscordLogger

**The contributor's guide to this repository — how the project is built, how it works internally, and how changes ship.** It's written for anyone picking up the codebase: human contributors and AI coding agents alike, with no prior context assumed. Everything below was verified against the actual source at the time of writing; when in doubt, the code wins — and if you find this file wrong, fix it in the same PR.

## What this project is

**DiscordLogger** is a Minecraft **Paper** server plugin (Java 21) that posts server events to a Discord channel via **webhooks** — either as rich embeds (per-event colors, player-head thumbnails, timestamps) or as plain Markdown text. It ships with a versioned `config.yml` that auto-migrates between schema versions, a channel-aware update checker, and a companion **Jekyll website** (in `docs/`) hosted on GitHub Pages at `https://discordlogger.godtiergamers.xyz` that includes an interactive config generator.

- **Current plugin version:** tracked by `pom.xml` / `.release-please-manifest.json` — never hand-edit either, see **Releases** below.
- **Current config schema:** **v9** (trailer comment in `src/main/resources/config.yml`, e.g. `# CONFIG VERSION V9, SHIPPED WITH v2.1.6 (x-release-please-version)`)
- **Paper API:** `1.21.11-R0.1-SNAPSHOT` (`provided` scope), `api-version: 1.21`
- **GitHub:** `GodTierGamers/DiscordLogger`

## Working agreement (the short version)

These rules apply to every contribution, whether written by a person or an AI agent:

1. **Trunk-based**: branch off `main` (`feat/<name>`, `fix/<name>`), PR into `main`. Never commit directly to `main`.
2. **Conventional Commit PR titles** (`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` / `ci:` / `test:` …) — `lint-pr.yml` rejects anything else. The title becomes the changelog entry verbatim. Squash-merge.
3. **Verify before PR**: `mvn -B -ntp clean package` must pass; for listener/config changes, exercise on a real Paper server when practical.
4. **Config changes travel in lockstep**: `config.yml` + listener + `EventRegistry` + `docs/assets/configs/v*/options.json` + `config.template.yml` **+ `docs/assets/configs/v*/config.yml`** (see "Config file dictionary" below — this one is easy to forget) in the same PR; run `python3 scripts/validate-config-generator.py` locally (CI runs it too).
5. **Merging is a maintainer's call** — AI agents open PRs and stop there unless a maintainer explicitly says to merge. This goes double for the **release-please Release PR**: merging it *is* the release. It exists to accumulate merged features (the batching role the old `dev` branch served) and stays open until the maintainer decides a feature compilation is ready to ship. Never merge it without an explicit, current instruction to release — general "go" energy on other work does not extend to it.
6. **Never hand-edit** `.release-please-manifest.json`, `CHANGELOG.md`, or `pom.xml`'s `<version>` — those belong to release-please.
7. Keep this file current: workflow or architecture changes update AGENTS.md in the same PR.
8. **AI attribution policy**: the README's *AI Disclosure* section is the single, project-level statement of AI involvement. Do **not** add per-commit or per-PR attribution — no `Co-Authored-By: Claude/AI` trailers, no "Generated with …" footers in commits or PR descriptions, no AI credits in code comments. The project is transparent about AI assistance without implying any individual change was unreviewed or machine-owned.

## Build & test

```bash
mvn -B -ntp clean package     # compile + shade → target/discordlogger-<version>.jar
mvn -B -ntp clean compile     # compile only (faster sanity check)
```

- **There is no test suite.** `mvn package` compiling cleanly is the only automated check. Real verification means dropping the shaded JAR into a Paper server's `plugins/` folder.
- The shade plugin relocates SnakeYAML to `com.discordlogger.shaded.snakeyaml` and excludes its `META-INF`. `minimizeJar` is deliberately **off** (ASM/Java 21 bytecode issues).
- **Maven resource filtering applies ONLY to `plugin.yml` and `build-info.properties`** (for `${project.version}` / `${dl.build.channel}` / `${dl.build.date}`). `config.yml` is copied **verbatim** — it contains `$` characters in ASCII art that must never be filtered. Don't add filtering to it; CI stamps its trailer via targeted regex replacement instead.
- A plain local `mvn package` produces a **`dev`-channel** build (`dl.build.channel` defaults to `dev` in `pom.xml`) — see `BuildInfo`.

## Repository layout

```
pom.xml                                Maven build
release-please-config.json             release-please: changelog sections, extra-files
.release-please-manifest.json          release-please: current released version (state)
scripts/validate-config-generator.py   CI check: options.json <-> template <-> Java source <-> shipped config <-> mirror
src/main/resources/
  plugin.yml                           Plugin descriptor (Maven-filtered)
  build-info.properties                Build channel/version/date, baked in at package time
  config.yml                           Default config, schema v9 (NOT filtered)
src/main/java/com/discordlogger/
  DiscordLogger.java                   Plugin entry point (onEnable/onDisable)
  log/Log.java                         Static logging facade (the API everything calls)
  webhook/DiscordWebhook.java          Manual JSON building + HTTP POST to Discord
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
4. The same workflow run's `build-artifact` job then checks out the tag, stamps `BUILT <DD-MM-YYYY>` onto the trailer, builds with `-Ddl.build.channel=stable`, and attaches `DiscordLogger-v<version>.jar` + `.sha256`. (The build lives in the same run rather than a `release: published` listener because events created with the built-in `GITHUB_TOKEN` don't trigger other workflows — a separate listener would never fire.)
5. **Config schema revisions (v9 → v10) stay manual and deliberate** — bump the `V<n>` trailer, add `docs/assets/configs/v<n>/`, wire the generator config. Never inferred from commits.

### Build channels (`BuildInfo`, baked in at package time — never inferred from the version string)

| Channel | Set by | Version format | Behavior |
|---|---|---|---|
| `stable` | `release-please.yml` (`build-artifact` job) | `2.1.7` | Normal update checks; `NightlyNotice` inert. |
| `nightly` | `nightly.yml` | `2.1.7-BETA.3` | Console warning **every start**; ops get an in-game chat notice **once per nightly version** (marker file `.nightly-notice`); update checks notify on **every** new stable and when **more than 2** nightlies behind. |
| `dev` | default (local build) | whatever `pom.xml` says | Update checks skipped entirely. |

### Nightly builds (`nightly.yml`)
Cron (15:00 UTC — arbitrary, adjust freely) + `workflow_dispatch`, building from `main`:
- **Skips** if `main`'s HEAD matches the last nightly tag or the last stable tag — no identical rebuilds, which also naturally bounds how many nightlies exist (they're all kept forever, never pruned).
- Computes the **upcoming version** the same way release-please will (conventional commits since the last stable tag), so the beta's base version always matches the eventual stable release.
- Numbers builds `v<version>-BETA.1`, `.2`, … — derived from existing tags each run, self-resets when the base version moves (e.g. a `feat:` lands and `2.1.7-BETA.9` jumps to `2.2.0-BETA.1`).
- Version injected via `mvn versions:set` (CI-local, never committed); trailer stamped `SHIPPED WITH v<version>-BETA.<n> BUILT <DD-MM-YYYY>` — every version string in the built JAR matches what was built, while the repo never contains a beta version string.
- Publishes each nightly as its own pre-release (`prerelease: true`) with notes listing commits since the previous nightly, plus JAR + checksum.
- Stable servers never see nightlies: the stable-channel update check skips pre-releases entirely.

### CI (`ci.yml`)
Path-filtered (`dorny/paths-filter`): `build` runs on `src/**`/`pom.xml` changes; `validate-generator-data` runs on `docs/assets/configs/**` changes; both on a mixed PR. Concurrency cancels superseded runs. PR builds upload the JAR as an artifact.

### Repo settings that matter (configured, not in files)
- "Allow GitHub Actions to create and approve pull requests" **must stay enabled** — release-please cannot open its Release PR without it.
- Branch protection on `main` requires the CI + lint checks; merged branches auto-delete.

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
- `dispatch()` posts async via the Bukkit scheduler but **falls back to synchronous when the plugin is disabled** (so the Server Stop embed isn't dropped at shutdown). Don't "fix" that away.
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
- Root `/discordlogger` (aliases `/dlogger`, `/dlog`), permission `discordlogger.reload` (default op). `Commands` routes `Subcommand` implementations (LinkedHashMap, permission-filtered help/tab-complete). Only subcommand today: `reload`.
- Adding one: implement `Subcommand`, add to the `new Commands(...)` varargs in `onEnable`, register any new permission in `plugin.yml`.

### `UpdateChecker`
- Async on startup; skips for `dev` channel. Fetches the **releases list** (`/releases?per_page=50`) — not `/releases/latest` — because nightly builds need to see pre-releases. Parses `tag_name`/`prerelease` pairs by regex (no JSON library, intentional; see `parseReleases` for why it's safe) and ranks with a `SemVer` record where stable > any `-BETA.N` of the same version, higher N > lower.
- Stable channel: notify on any newer stable, pre-releases invisible. Nightly channel: notify on **every** newer stable, and on nightlies only when **more than 2** behind (`NIGHTLY_LAG_THRESHOLD`). Notifications = console banner + Discord webhook notice (embed or plain per config).

## Config reference (schema v9)

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

### Config file dictionary — four places hold "the config", they are not the same thing

This repo has four near-identical copies of the config content. Confusing them is the single most common way this repo drifts — it already happened twice: a hint-text reword landed on two of four copies and nobody noticed the other two for several sessions (one of them, the doc-page embed, had *already* been silently missing an entire banner block before anyone caught it). The first three carry an identity comment block at the top (`DL_FILE_IDENTITY_START`/`END`) stating which one they are — read it before editing any of them.

| # | Location | What it actually is | Consumed by |
|---|---|---|---|
| 1 | `src/main/resources/config.yml` | **The shipped config** — the real source of truth. Bundled inside the plugin JAR; every server gets this on first run. | `DiscordLogger.onEnable` / `ConfigMigrator` (Java, at runtime) |
| 2 | `docs/assets/configs/v9/config.yml` | **The download mirror** — a static copy served by the plain "Download" button on the config docs page. No wizard involved; just the file, verbatim. | A `<a download>` link in `docs/config/v9/index.md` |
| 3 | `docs/assets/configs/v9/config.template.yml` | **The generator template** — `{{TOKEN}}` placeholders, filled in by the wizard based on what the visitor chose. Never downloaded directly; its *output* is. | `docs/assets/configs/v9/generator.js` |
| 4 | The `## Full config.yml` fenced code block inside `docs/config/v9/index.md` | **The doc-page embed** — the full file shown inline in prose, for people reading the docs who don't want to click through. Easiest of the four to forget since it lives inside Markdown, not a config file. | Rendered directly on the config docs page |

**The rule:** 1, 2, and 4 must be **content-identical** — same real values, same comments, same banners — except each one's own trailer line (`SHIPPED WITH vX.Y.Z` vs `DOWNLOADED FROM WEBSITE`) and (for 1 and 2) identity header. `scripts/validate-config-generator.py` enforces this automatically in CI for both 1↔2 and 2↔4 — it will fail the build if any of them drift, which is exactly the class of bug that motivated adding the check. File 3 isn't byte-compared (it has tokens instead of real values), but its `{{LOG_*}}`/`{{COLOR_*}}` tokens are cross-checked against `options.json` and the Java source by the same script.

**Practical consequence:** any edit to the real config content (webhook/format/embeds/log.\* structure or their comments, including the banners) touches file 1 first, then files 2 and 4 need the identical change, and file 3 needs the matching `{{TOKEN}}` version. Don't rely on memory for this — run `python3 scripts/validate-config-generator.py` locally before opening the PR; CI runs it too, but catching it before pushing is faster.

### ⚠️ Known inconsistency (still open — don't propagate it)
**Java fallback defaults don't all match config.yml.** `PlayerTeleport`, `PlayerGamemode`, and `Explosion` use `getBoolean(key, false)` while everything else uses `true` (and config.yml ships all `true`). The fallback only matters if the key is missing from a user's file, but the convention is *Java default == config.yml default* — fix toward `true` if you touch these. (Good first `fix:` PR.)

## Website (`docs/`)

Jekyll site (GitHub Pages gem stack) at `discordlogger.godtiergamers.xyz` (CNAME present). Pages use `_layouts/default.html` via `_config.yml` defaults; nav in `_data/nav.yml`. **Deploys from `main`** — docs changes go live on merge, independent of plugin releases.

- **Local dev:** `cd docs && bundle install && bundle exec jekyll serve --livereload --watch` (`docs/test.sh` does the same). `docs/_site/` is gitignored build output — never edit, never trust.
- The plugin hot-links icons from the site (`/assets/icons/…`) — the site being up is a runtime dependency of embeds. The banner is self-hosted at `/assets/DiscordLogger-Banner.webp` (compressed; no external image host).

### Version awareness — never hardcode a version number

`docs/assets/js/versions.js` is loaded from `<head>` on every page and is the single source of truth. It reads the GitHub releases API once (cached per session), works out the newest stable and newest nightly, and exposes `window.DLVersions`.

**A version is "beta" when it is newer than the newest stable release** — i.e. it exists only in nightly builds. This is *derived, never hand-flagged*: while 2.1.7 is nightly-only its docs show a BETA badge, and the moment 2.1.7 ships stable every badge and gate flips itself off with no edits. Never add a manual "is beta" flag anywhere.

Use the declarative hooks rather than writing per-page JS or literal versions:

| Markup | Behaviour |
|---|---|
| `data-dl-version="2.1.7"` | appends a BETA badge while that version is nightly-only |
| `data-dl-beta-only="2.1.7"` | hides the element unless beta is enabled (normal content once it ships) |
| `data-dl-latest` / `data-dl-latest-nightly` | filled with the current version |
| `data-dl-beta-toggle="optional note"` | renders the beta opt-in checkbox |

Beta content is **opt-in and remembered** (`localStorage`), so visitors never trip over unreleased features. After injecting markup dynamically, call `DLVersions.apply(root)`; to react to the toggle, listen for the `dl-beta-change` event. `DLVersions.ready()` resolves once release data is in.

### Config generator — per-version frozen bundles

**Users pick their plugin version; the config schema is resolved for them.** Nobody knows offhand which schema their build uses, but everyone knows what they downloaded — so the picker lists plugin builds and shows the detected schema as a confirmation note.

Every published build is listed individually, **nightlies included** (`2.1.7-BETA.1`, `2.1.7-BETA.2`, …) — successive nightlies can carry different features, so they're genuinely different targets. Nightlies are hidden until the visitor enables beta, and the default selection is always the newest *stable* build.

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

**The isolation rule (the whole point): once a newer schema folder exists, never edit an older one again.** Old plugin versions must keep generating exactly the config they always did. Fix bugs only in the newest schema; copy the folder forward instead of refactoring in place. The loader↔bundle contract is documented at the top of both files and is frozen — a bundle registers `window.DL_GENERATORS['v9'] = launch` and receives `ctx` (`mount`, `configVersion`, `pluginVersions`, `beta`, `proxyUrl`, `backToVersions`).

`registry.json` entries take `{ "config", "since", "generatorReady"? }`. **`since` is the first build shipping that schema and may itself be a nightly** (e.g. `"2.1.7-BETA.1"`) when a schema debuts in one — version comparison is BETA-aware, so `2.1.7-BETA.1 < 2.1.7-BETA.2 < 2.1.7`. Setting `"generatorReady": false` lists a schema *before* its bundle exists: the picker names it, explains it isn't available yet, and disables Continue rather than failing to load a missing script. **v10 currently sits in the registry this way** — reachable from `2.1.7-BETA.1`, deliberately not yet implemented.

**Adding a new config schema:**
1. Copy `docs/assets/configs/v9/` → `v10/`, adapt the bundle, options, and template there.
2. Add one line to `registry.json`: `{ "config": "v10", "since": "<first build shipping it>" }` — and drop the `generatorReady: false` flag once the bundle works.
3. Add a docs page at `docs/config/v10/`.
4. Bump the `V<n>` trailer in `src/main/resources/config.yml`.

Nothing else. The generator picker, the config-docs index list, and BETA gating all derive from that one registry line plus the releases API. `scripts/validate-config-generator.py` cross-checks options ↔ template ↔ Java source (CI runs it; run it locally after touching any of those).

- **Webhook testing / CORS:** Discord webhooks allow simple browser POSTs; each bundle carries its own test payload. `docs/cloudflare/discord-proxy.js` is an optional Cloudflare Worker relay, used when `proxyUrl` is set in `registry.json` (currently `""`).

### Downloads page

Renders straight from the releases API. Nightly builds (tag matches `-BETA.N`) get a dedicated purple **"Nightly"** badge and a purple card edge; any *other* pre-release keeps the generic "Pre-release" badge. Nightlies are **hidden behind an opt-in toggle** (remembered per visitor) with a plain-language stability warning.

## Conventions

- **Java 21, Paper API only**; Adventure preferred for anything new touching chat components (`ChatColor` lingers in command feedback).
- Final classes, private constructors on static utility classes, `LinkedHashMap` where iteration order matters.
- Config keys: lowercase snake_case, grouped `log.<category>.<event>`.
- Every new logged event, in lockstep: listener (with live config gate) + `EventRegistry` registration + `log.*` key in `config.yml` + default color in `Log.init` + generator `options.json` (including its `defaultColor`, which must match the plugin default) / template entries + docs mention. The validator catches the generator-side half in CI.
- **Never hardcode a version number in the website.** Use the `data-dl-*` hooks from `versions.js`; if something genuinely can't be derived, that's a signal the data model needs the fact once, not the page needing a literal.
- Escape user-visible strings via `Log.mdEscape`; prefer structured `Log.Field` embeds for multi-datum events.

## Things NOT in this repo (avoid confusion)

- **No test suite**, no linter/formatter config (Java).
- **No `generator.config.js`** — the old `DL_VERSIONS`/`DL_CONFIGS`/`DL_TEST_EMBED` globals are gone, replaced by `registry.json` plus per-bundle payloads.
- **No committed AI/editor tooling config.** `CLAUDE.md`, `.claude/`, `.cursor*`, `.aider*`, `.windsurfrules`, `*.local.md` and friends are gitignored — they're local preference, not project state. `AGENTS.md` is the one tracked, tool-agnostic guide.
- `dependency-reduced-pom.xml` is shade-plugin output that happens to be committed — don't edit by hand.
- **No `release-spec.md` / `release-changelog-builder-config.json` / `release-on-merge.yml`** — replaced by release-please. Any reference to them (old docs, old issues, old habits) is stale.
- **No `dev` branch** — retired in favor of trunk-based development on `main`.
- **Config v10 does not exist yet.** In July 2026 an unreleased effort (v2.1.7 + a website rewrite + a "config v10" with nested sub-option toggles) was deliberately discarded to start fresh; the maintainer keeps an archive of it outside the repo. References to config **v10** or nested sub-option toggles mean that discarded work, **not** the current codebase — v9 is the only schema that exists.
