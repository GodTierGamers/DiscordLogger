# Architecture

A tour of how DiscordLogger is actually built — for contributors, curious users, and anyone who wants to understand the codebase before changing it. For the practical "how do I open a PR" mechanics, see [CONTRIBUTING.md](CONTRIBUTING.md); for install/usage, see the [README](README.md).

> If you're an AI coding agent working in this repo, read [AGENTS.md](AGENTS.md) instead — it's the same information in a denser, more exhaustive form, kept current for that purpose specifically.

## What DiscordLogger is

A Paper server plugin (Java 25) that watches server events and posts them to a Discord channel over a webhook, either as rich embeds or plain Markdown text. It ships two versioned files — `config.yml` (what to log, where, and how it looks) and `lang.yml` (every string it says) — both of which migrate themselves forward automatically. It checks for updates in a way that's aware of which release channel it's running on, and is accompanied by a Jekyll website (this `docs/` folder) with an interactive config generator.

## How a request flows through the plugin at startup

`DiscordLogger.onEnable()` runs through a fixed sequence, each step depending on the one before it:

1. **`BuildInfo.load(this)`** reads a small properties file baked into the JAR at build time, telling the plugin which *channel* it is — `stable`, `nightly`, or a local `dev` build. Everything channel-specific later in startup depends on this.
2. **`saveDefaultConfig()`** writes the bundled `config.yml` to disk if the server doesn't have one yet.
3. **`ConfigMigrator`** checks whether the user's existing config is from an older schema version and, if so, migrates it — transplanting their values into the new default file while preserving all the comments and ASCII art. It runs once per managed file, so `lang.yml` goes through the same path.
4. **`NightlyNotice`** activates (a no-op unless this is a nightly build).
5. **Runtime config is applied** — the webhook URL and time format are read, `Lang` loads the language file, `Filters` builds its immutable snapshot, and `Log.init(...)` is called. A missing or invalid webhook doesn't disable the plugin; it just runs in a "degraded," console-only mode until an admin fixes it and runs `/discordlogger reload`.
6. **Every listener registers**, unconditionally — whether an event actually gets logged is decided *inside* each listener by reading the config live, every time. This is what lets `/discordlogger reload` work without needing to re-register anything.
7. Commands are wired up, anonymous [bStats](https://bstats.org) metrics start (skipped entirely for local dev builds), an async update check fires, and the plugin announces server start.

## The pieces that do the actual work

**`Log`** is a static facade — it's the only class anything else calls to get a message to Discord. Its state is deliberately `volatile`: initialization happens on the main thread, but the actual sending happens on async scheduler threads, so the color map and other config-derived state are built up locally and published in a single atomic write. It handles category-to-color resolution (`"Player Join"` normalizes to `player_join` and looks itself up in a map that's overridable via `embeds.colors.*`), Markdown escaping, and player avatar URLs.

**`DiscordWebhook`** builds the JSON payload by hand with a `StringBuilder` rather than pulling in a JSON library — this is a deliberate zero-dependency choice that runs through the whole plugin. It performs a single POST and reports what happened, rather than deciding what to do about it.

**`WebhookQueue`** owns that decision. Every outgoing message goes through one worker thread, which serves two purposes: it respects Discord's rate limits, and it guarantees messages arrive in the order they happened — logs are a narrative, so delivering them out of order would be its own kind of bug. Discord tells you how many requests remain in the current window and when it resets, so the queue waits out a spent budget *before* sending rather than discovering the limit by being refused. If it does get refused anyway, it honours the retry delay and sends the same message again instead of discarding it. Network blips and server-side errors retry with a growing backoff; a webhook URL that no longer exists is reported plainly instead of being retried forever. The queue is bounded, so an unreachable Discord can't grow it until the server runs out of memory.

**`ConfigMigrator`** is the highest-risk code in the repo — it runs once on every existing install, and getting it wrong destroys settings people spent real time on. It works out which schema a file is by two independent means: the `config-version` key and trailer comment it *declares*, and the set of keys it actually *has*. A declaration can be edited or deleted; a shape cannot lie. **When they disagree, the shape wins** (`SchemaDetector`), so a hand-mangled marker degrades to a correct guess rather than a wrong migration.

Migration is **forward-only and sequential** — v8 to v10 goes through v9, never straight across. Most of the work is generic: parse both files, transplant the user's values into the new structure, preserve every comment and inline spacing. But schemas that *moved* keys need real code, and there is a small `step()` chain for exactly those (`v6ToV7`, `v9ToV10`); every other step is the identity function. A config from a *newer* build is never downgraded — the plugin says so and changes nothing. Both managed files go through this, and each keeps its own backup (`config.old.yml`, `lang.old.yml`).

**`Filters`** sits between the listeners and `Log`. It holds an immutable `Snapshot` swapped atomically on reload, so a filter check can never observe a half-applied config. Fourteen rules cover commands, players, worlds, chat, advancements, teleports, deaths and explosions. The command filter normalises before matching — `/essentials:msg hi` becomes `msg` — because a deny-list that a plugin prefix defeats is worse than none: `/login` and `/msg` ship in it by default, and command logging posts the line exactly as typed.

**`Lang`** owns every user-facing string, split by destination rather than by topic: `chat.*` renders through Adventure MiniMessage for in-game text, `discord.*` is plain text for the channel. They are not interchangeable — a `<green>` tag posted to Discord arrives as the literal characters. The English shipped inside the JAR is the fallback for anything a user deletes, so a broken language file degrades to English instead of to blank messages.

**Listeners** follow one consistent shape: a config gate as the very first line of the handler (read live, never cached), then a `Filters` check, `MONITOR` priority in almost every case, and player-facing text always routed through `Log.mdEscape`. The moderation listeners (ban/kick/op/etc.) are a bit unusual — they don't hook a dedicated API event, because Bukkit doesn't reliably expose one for most of these. Instead they watch the raw command being run, then verify on the next tick that the state they expected actually changed, before logging anything.

**Routing** is per event, not per category. Any event can carry its own `webhook:` value, and every send site resolves its destination through one helper — including the ones that always want the main webhook, which pass `null` rather than reading the field directly. That uniformity is enforced by a test, because the first version of routing missed exactly one send site and the result was that quit routed correctly while death and gamemode silently did not.

**`ClientPlatform`** answers whether a player joined from Bedrock, using Floodgate's API when it's present and the UUID shape when it isn't. The two signals are OR'd, never chained: behind a Velocity proxy the backend's registry may not know a player the proxy handshook, so an API "no" is not evidence of Java when the UUID is plainly Floodgate's. It only ever flags Bedrock — it never asserts "Java", because absence of evidence isn't evidence here.

## The config files — and why there are four copies of each

The same config content exists in four places, and this is the single biggest source of confusion (and past bugs) in this repo:

1. **`src/main/resources/config.yml`** — the real one, bundled into the JAR. This is what every server actually gets.
2. **`docs/assets/configs/v10/config.yml`** — a static, byte-for-byte mirror served by the plain "Download" button on the docs site, for people who don't want to use the wizard.
3. **`docs/assets/configs/v10/config.template.yml`** — the same shape, but with `{{TOKEN}}` placeholders instead of real values. The generator wizard fills these in based on what a visitor chooses; this file itself is never downloaded.
4. **A code block embedded directly in `docs/config/v10/index.md`** — the full file shown inline for people reading the documentation who don't want to click away.

Files 1, 2, and 4 are supposed to be identical (bar one trailer line each). This has genuinely drifted apart twice already — once when a small text fix landed on two of the four copies and nobody noticed the others for a while. There's now a CI check (`scripts/validate-config-generator.py`) that fails the build if any of them disagree, so this class of bug can't merge silently again — but it's still worth knowing the four exist before you touch any of them.

**`lang.yml` has the same four**, in the same places (`lang.template.yml` for the generator, `docs/config/v10/lang.yml.txt` for the docs page), and is covered by the same check. Both files carry the **same** `config-version` — the schema number is global to the plugin's configuration, not per file, so there is no such thing as a "lang version" to reason about separately.

The invariant tying the generator to all of this: **generate, change nothing, and you get the shipped files byte for byte.** The validator enforces it from both ends — it re-renders every filter default and re-substitutes the whole language template, then diffs against what the JAR ships. Its filter renderer is a deliberate second implementation of the one in the generator bundle, so the two have to agree.

## The website and the config generator

The site is a fairly plain Jekyll project deployed straight from `main` via GitHub Pages — docs changes go live the moment they're merged, independent of when the plugin itself releases.

The interesting part is the **config generator**, which is deliberately built so that old plugin versions keep generating exactly the config they always did, forever, even as new config schemas get added. It's split into a small, stable *loader* (`docs/assets/js/generator.js`) and fully self-contained *bundles*, one per config schema (`docs/assets/configs/v9/generator.js`, `v10/generator.js`, and so on). A visitor picks the plugin version they downloaded — not a config schema, since nobody knows that off the top of their head — and the loader silently resolves which schema that version uses and hands off to the matching bundle. The rule that keeps this maintainable: once a newer schema's folder exists, the older one is never edited again. Bug fixes only ever land in the newest schema; a folder gets copied forward, not refactored in place.

A bundle covers **every key in both files** — events, colours, per-event routing, all fourteen filters, all seventy-nine messages — and it is driven by data, not code. `options.json` describes the controls; adding a fifteenth filter is one entry plus one `{{TOKEN}}`, with no change to the bundle. This matters because anything the template hardcodes is a setting the user ends up with without knowing they have it, which is the worst possible way to ship a password deny-list.

A schema version is opened by the first change to the config's **keys** since the last published version — a key added, removed, renamed, or simply reordered. Changes to comments don't count; if the text could be deleted without changing behaviour, it isn't a schema change. Once opened, the version stays open and freely editable, accumulating everything that lands before the release that ships it — so several features arriving in one release all share a single version by construction, with nothing to coordinate. **Publication is what freezes it**, permanently, and the next key change after that opens the next version. Numbers are never skipped.

The asymmetry worth understanding: **the plugin ships exactly one config — the current one — but the website keeps every config version forever.** When v10 arrived, `src/main/resources/config.yml` was *replaced* outright (the JAR only ever carries the newest schema; existing users get migrated forward at runtime), while `docs/assets/configs/v9/` and the `docs/config/v9/` docs page are left completely untouched and stay online indefinitely, because people running older plugin versions still need them. The CI drift check understands this: it reads the shipped config's own version trailer to decide which mirror to compare against, so it automatically follows the schema forward and stops policing frozen ones — while still checking each old version against itself, so the archived docs stay internally consistent.

The site is also aware of which versions are "beta" — meaning they only exist as nightly builds and haven't shipped stable yet — entirely by asking the GitHub releases API at page-load time. Nothing on the site hand-flags a version as beta; the badge and the opt-in gate both derive from the same live data, so they can never go stale.

The generator and config docs apply a narrower rule than that badge. They refuse to offer a schema that has only ever existed in a *nightly*, because a config written against a format that can still move is wrong silently and later. That test is on the schema's declared `since` string, not on whether the build has shipped — a schema pinned to a stable version is finished and frozen even before release day, so it is listed and offered immediately rather than being hidden from the nightly users already running it.

## How code actually ships

Development is trunk-based: `main` is the only long-lived branch, and everything gets there through a squash-merged pull request with a [Conventional Commits](https://www.conventionalcommits.org/) title. That title becomes both the single commit on `main` and, eventually, a line in the changelog — which is why PR titles matter more here than in a lot of projects.

CI builds and runs the test suite on every PR. The tests are concentrated where a silent failure would be most expensive rather than spread for coverage's sake: five classes cover `ConfigMigrator` alone (the decision table, the step chain, path resolution, list splicing, and single-line rewrites), with the rest on filters, language loading, death causes, routing, webhook redaction and Bedrock detection. Several exist because the bug they describe actually shipped.

Two automated systems build on top of that:

- **release-please** watches `main` and maintains a single, ever-updating "release" pull request — its diff is the version bump and the changelog, computed from every conventional commit that's landed since the last release. It just sits there, accumulating, until a maintainer decides the accumulated set of changes is worth shipping and merges it. That merge *is* the release: a tag gets cut, a GitHub Release gets published, and the same workflow run builds and attaches the JAR.
- **Nightly builds** run on a schedule, packaging whatever's currently on `main` as a `vX.Y.Z-BETA.N` pre-release — a way to get unreleased work in front of testers without it ever counting as an official release. A nightly build knows it's a nightly (baked in at compile time, not guessed from its own version string) and behaves a little differently: it warns on every startup, and it checks for updates more assertively than a stable build does.

## Where to go from here

- **Contributing mechanics** (branch names, PR checklist, the AI-assistance policy) — [CONTRIBUTING.md](CONTRIBUTING.md)
- **Exhaustive technical reference** (exact file paths, every config key, every workflow's internals, known rough edges) — [AGENTS.md](AGENTS.md)
- **Installing and using the plugin** — [README.md](README.md)
