# Architecture

A tour of how DiscordLogger is actually built — for contributors, curious users, and anyone who wants to understand the codebase before changing it. For the practical "how do I open a PR" mechanics, see [CONTRIBUTING.md](CONTRIBUTING.md); for install/usage, see the [README](README.md).

> If you're an AI coding agent working in this repo, read [AGENTS.md](AGENTS.md) instead — it's the same information in a denser, more exhaustive form, kept current for that purpose specifically.

## What DiscordLogger is

A Paper server plugin (Java 21) that watches server events and posts them to a Discord channel over a webhook, either as rich embeds or plain Markdown text. It ships a versioned `config.yml` that migrates itself forward automatically, checks for updates in a way that's aware of which release channel it's running on, and is accompanied by a Jekyll website (this `docs/` folder) with an interactive config generator.

## How a request flows through the plugin at startup

`DiscordLogger.onEnable()` runs through a fixed sequence, each step depending on the one before it:

1. **`BuildInfo.load(this)`** reads a small properties file baked into the JAR at build time, telling the plugin which *channel* it is — `stable`, `nightly`, or a local `dev` build. Everything channel-specific later in startup depends on this.
2. **`saveDefaultConfig()`** writes the bundled `config.yml` to disk if the server doesn't have one yet.
3. **`ConfigMigrator`** checks whether the user's existing config is from an older schema version and, if so, migrates it — transplanting their values into the new default file while preserving all the comments and ASCII art.
4. **`NightlyNotice`** activates (a no-op unless this is a nightly build).
5. **Runtime config is applied** — the webhook URL and time format are read, and `Log.init(...)` is called. A missing or invalid webhook doesn't disable the plugin; it just runs in a "degraded," console-only mode until an admin fixes it and runs `/discordlogger reload`.
6. **Every listener registers**, unconditionally — whether an event actually gets logged is decided *inside* each listener by reading the config live, every time. This is what lets `/discordlogger reload` work without needing to re-register anything.
7. Commands are wired up, an async update check fires, and the plugin announces server start.

## The pieces that do the actual work

**`Log`** is a static facade — it's the only class anything else calls to get a message to Discord. Its state is deliberately `volatile`: initialization happens on the main thread, but the actual sending happens on async scheduler threads, so the color map and other config-derived state are built up locally and published in a single atomic write. It handles category-to-color resolution (`"Player Join"` normalizes to `player_join` and looks itself up in a map that's overridable via `embeds.colors.*`), Markdown escaping, and player avatar URLs.

**`DiscordWebhook`** builds the JSON payload by hand with a `StringBuilder` rather than pulling in a JSON library — this is a deliberate zero-dependency choice that runs through the whole plugin. It posts asynchronously via the Bukkit scheduler, but falls back to sending synchronously if the plugin is already disabled, so the "server stopped" message isn't dropped during shutdown.

**`ConfigMigrator`** finds the schema version by regex-matching a trailer comment (`CONFIG VERSION V9`) at the bottom of the config file — genuinely just a comment, but one both the plugin and the release automation treat as meaningful. When versions differ, it parses both the old and new YAML, and transplants the user's existing values into the new file's structure, preserving every comment along the way. There's no per-schema-version migration code to maintain — the algorithm is generic.

**Listeners** follow one consistent shape: a config gate as the very first line of the handler (read live, never cached), `MONITOR` priority in almost every case, and player-facing text always routed through `Log.mdEscape`. The moderation listeners (ban/kick/op/etc.) are a bit unusual — they don't hook a dedicated API event, because Bukkit doesn't reliably expose one for most of these. Instead they watch the raw command being run, then verify on the next tick that the state they expected actually changed, before logging anything.

## The config file — and why there are four copies of it

The same config content exists in four places, and this is the single biggest source of confusion (and past bugs) in this repo:

1. **`src/main/resources/config.yml`** — the real one, bundled into the JAR. This is what every server actually gets.
2. **`docs/assets/configs/v9/config.yml`** — a static, byte-for-byte mirror served by the plain "Download" button on the docs site, for people who don't want to use the wizard.
3. **`docs/assets/configs/v9/config.template.yml`** — the same shape, but with `{{TOKEN}}` placeholders instead of real values. The generator wizard fills these in based on what a visitor chooses; this file itself is never downloaded.
4. **A code block embedded directly in `docs/config/v9/index.md`** — the full file shown inline for people reading the documentation who don't want to click away.

Files 1, 2, and 4 are supposed to be identical (bar one trailer line each). This has genuinely drifted apart twice already — once when a small text fix landed on two of the four copies and nobody noticed the others for a while. There's now a CI check (`scripts/validate-config-generator.py`) that fails the build if any of them disagree, so this class of bug can't merge silently again — but it's still worth knowing the four exist before you touch any of them.

## The website and the config generator

The site is a fairly plain Jekyll project deployed straight from `main` via GitHub Pages — docs changes go live the moment they're merged, independent of when the plugin itself releases.

The interesting part is the **config generator**, which is deliberately built so that old plugin versions keep generating exactly the config they always did, forever, even as new config schemas get added. It's split into a small, stable *loader* (`docs/assets/js/generator.js`) and fully self-contained *bundles*, one per config schema (`docs/assets/configs/v9/generator.js`, and eventually `v10/`, etc.). A visitor picks the plugin version they downloaded — not a config schema, since nobody knows that off the top of their head — and the loader silently resolves which schema that version uses and hands off to the matching bundle. The rule that keeps this maintainable: once a newer schema's folder exists, the older one is never edited again. Bug fixes only ever land in the newest schema; a folder gets copied forward, not refactored in place.

The asymmetry worth understanding: **the plugin ships exactly one config — the current one — but the website keeps every config version forever.** So when v10 arrives, `src/main/resources/config.yml` is *replaced* outright (the JAR only ever carries the newest schema; existing users get migrated forward at runtime), while `docs/assets/configs/v9/` and the `docs/config/v9/` docs page are left completely untouched and stay online indefinitely, because people running older plugin versions still need them. The CI drift check understands this: it reads the shipped config's own version trailer to decide which mirror to compare against, so it automatically follows the schema forward and stops policing frozen ones — while still checking each old version against itself, so the archived docs stay internally consistent.

The site is also aware of which versions are "beta" — meaning they only exist as nightly builds and haven't shipped stable yet — entirely by asking the GitHub releases API at page-load time. Nothing on the site hand-flags a version as beta; the badge and the opt-in gate both derive from the same live data, so they can never go stale.

## How code actually ships

Development is trunk-based: `main` is the only long-lived branch, and everything gets there through a squash-merged pull request with a [Conventional Commits](https://www.conventionalcommits.org/) title. That title becomes both the single commit on `main` and, eventually, a line in the changelog — which is why PR titles matter more here than in a lot of projects.

Two automated systems build on top of that:

- **release-please** watches `main` and maintains a single, ever-updating "release" pull request — its diff is the version bump and the changelog, computed from every conventional commit that's landed since the last release. It just sits there, accumulating, until a maintainer decides the accumulated set of changes is worth shipping and merges it. That merge *is* the release: a tag gets cut, a GitHub Release gets published, and the same workflow run builds and attaches the JAR.
- **Nightly builds** run on a schedule, packaging whatever's currently on `main` as a `vX.Y.Z-BETA.N` pre-release — a way to get unreleased work in front of testers without it ever counting as an official release. A nightly build knows it's a nightly (baked in at compile time, not guessed from its own version string) and behaves a little differently: it warns on every startup, and it checks for updates more assertively than a stable build does.

## Where to go from here

- **Contributing mechanics** (branch names, PR checklist, the AI-assistance policy) — [CONTRIBUTING.md](CONTRIBUTING.md)
- **Exhaustive technical reference** (exact file paths, every config key, every workflow's internals, known rough edges) — [AGENTS.md](AGENTS.md)
- **Installing and using the plugin** — [README.md](README.md)
