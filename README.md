![DiscordLogger](https://discordlogger.godtiergamers.xyz/assets/DiscordLogger-Banner.webp "DiscordLogger")

<!-- Badges (GodTierGamers/DiscordLogger) -->
![Build](https://img.shields.io/github/actions/workflow/status/GodTierGamers/DiscordLogger/ci.yml?branch=main&label=build)
![Release](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger)
![Nightly](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger?include_prereleases&label=nightly)
![Downloads](https://img.shields.io/github/downloads/GodTierGamers/DiscordLogger/total)
![Issues](https://img.shields.io/github/issues/GodTierGamers/DiscordLogger)
![License](https://img.shields.io/github/license/GodTierGamers/DiscordLogger)
<!-- dl:sync-block:badges -->
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Paper](https://img.shields.io/badge/Paper-1.19--26.2-blue)
<!-- /dl:sync-block -->
![Discord Webhooks](https://img.shields.io/badge/Discord-Webhooks-5865F2)

A minimal, reliable Minecraft server **logging plugin** that posts clean messages to a **Discord webhook** — in Markdown **or rich embeds**.
Built for **Paper <!-- dl:sync:paper_display -->1.19 – 26.2<!-- /dl:sync -->** (and Paper forks like Purpur) on **Java <!-- dl:sync:java -->17<!-- /dl:sync -->+**, tested with Geyser/Floodgate (Bedrock cross-play).

---

## ✨ Features

- **Discord webhook logging** — no bot to invite, no token to manage, no third-party service.
  - Plain text + Markdown format: `` `[HH:mm:ss, dd:MM:yyyy]` - **<Category>**: <message> ``
  - **Embeds (optional)** with per-event colors, configurable author, player avatar thumbnails, server icon, and timestamps.
- **Config-toggleable events** (all on by default):
  - **Player**: Join, Quit, Chat, Command, Death, Advancement, Teleport, Gamemode
  - **Server**: Start, Stop, Console Command, Explosion
  - **Moderation**: Ban, Unban, Kick, Op, Deop, Whitelist Toggle, Whitelist Add/Remove
- **Per-event channel routing**: give any single event its own webhook — moderation to a private staff channel, chat to a public one. Everything else keeps using the main webhook.
- **Fourteen filters**: ignore commands, players, worlds, chat content, advancements, teleport causes, death causes and explosion sources, with minimum-length and minimum-distance thresholds. `/login`, `/register` and `/msg` are filtered **by default** — command logging posts the line exactly as typed, which would otherwise publish passwords and private messages to your channel.
- **Every message is yours**: `lang.yml` holds all of them — in-game text with [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting, and the Discord-facing wording separately. Reword or translate anything without touching code.
- **Commands** (aliases `/dlogger`, `/dlog`):
  - `/discordlogger reload` — reload config and language files (`discordlogger.reload`)
  - `/discordlogger webhook <url>` — set the webhook and reload, without editing YAML (`discordlogger.webhook`). The URL is never echoed back and is redacted from command logging.
  - `/discordlogger regen [confirm]` — rebuild `config.yml` from this build's default, backing up the current one (`discordlogger.regen`)
- **Bedrock/Java indicator**: joins from Bedrock are flagged when the server runs Geyser with Floodgate.
- **Geyser-friendly death messages**: built from server-side damage context rather than client-localized text, with the cause of death as its own embed field and coordinates optionally alongside it.
- **Automatic config updater**: migrates `config.yml` and `lang.yml` forward one schema version at a time, preserving your settings and comments, and keeping the previous file. A config from a *newer* build is never silently downgraded — you're told, and nothing is overwritten.
- **Paced sends**: messages are queued and paced against Discord's per-webhook rate limits, so a busy chat channel never delays your moderation log.
- **Channel-aware update notifications**: stable servers are notified of new stable releases; nightly builds also warn when they fall behind.
- **Nickname support**: nicknames are recognized in server logs as `Nickname (RealName)`.
- **[Config generator](https://discordlogger.godtiergamers.xyz/generator/)**: build `config.yml` *and* `lang.yml` in your browser — every event, colour, filter and message, no hand-editing needed.

---

## 📦 Downloads

| Channel | What it is | Where |
|---|---|---|
| **Stable** | Tested releases. Use these on production servers. | [Latest release](https://github.com/GodTierGamers/DiscordLogger/releases/latest) |
| **Nightly** | Automated `vX.Y.Z-BETA.N` pre-release builds of unreleased work. May be unstable — the plugin itself will remind you. | [All releases](https://github.com/GodTierGamers/DiscordLogger/releases) (marked *Pre-release*) |

Every release includes a `.sha256` checksum for the JAR.

Stable releases are also published to [Modrinth](https://modrinth.com/plugin/discordlogger) and [Hangar](https://hangar.papermc.io/LVCHLANN/DiscordLogger), which is where most server hosts and plugin managers will find it. Nightly builds are GitHub-only.

---

## 🚀 Installation

1. Download the latest [release](https://github.com/GodTierGamers/DiscordLogger/releases/latest) and place the JAR in your server's `plugins/` folder.
2. Start the server once. It writes `plugins/DiscordLogger/config.yml` and `lang.yml`.
3. Set your **Discord webhook**, either way:
   - In game or from console: `/discordlogger webhook https://discord.com/api/webhooks/…`
   - Or set `webhook.url` in `config.yml` and run `/discordlogger reload`.
4. (Optional) Adjust per-event toggles under `log.*`, embed colors, filters, and the timestamp format — or generate both files up front with the [config generator](https://discordlogger.godtiergamers.xyz/generator/).

> **Note:** If `webhook.url` is empty/invalid, the plugin still runs and logs to console, but nothing posts to Discord until it's set.

Full instructions, including creating the webhook in Discord and checking that events arrive, are in the **[setup guide](https://discordlogger.godtiergamers.xyz/setup/)**. Every key is documented in the **[configuration reference](https://discordlogger.godtiergamers.xyz/config/)**.

---

## 🔌 Compatibility

- **Server:** Paper **<!-- dl:sync:paper_display -->1.19 – 26.2<!-- /dl:sync -->**, or a Paper fork such as Purpur. The plugin uses Paper-specific APIs, so Paper is required — it will tell you clearly on startup if the server doesn't provide them.
- **Java:** **<!-- dl:sync:java -->17<!-- /dl:sync -->+** — the plugin is compiled for Java <!-- dl:sync:java -->17<!-- /dl:sync -->, so it loads on that and anything newer. Your *Minecraft* version sets the real floor: 1.19.4 needs Java 17, 1.20.5 needs 21, and 26.x needs 25. Whatever your server already runs on is fine.
- **Cross-play:** Compatible with **Geyser/Floodgate** — death messages are server-generated for consistency across Java/Bedrock names/locales, and joins from Bedrock can be flagged as such.
- **Spigot and CraftBukkit are not supported.** The plugin uses Paper's chat API; on a server without it you get a plain explanation on startup rather than a stack trace.

---

## 🔒 Privacy

- **Your webhook URL is a credential.** Anyone holding it can post to that channel as your server. The plugin never echoes it back, redacts it from command logging, and keeps it out of tab-completion. The [config generator](https://discordlogger.godtiergamers.xyz/generator/) runs entirely in your browser — it sends your webhook to Discord and nowhere else.
- **Command logging posts commands verbatim**, which is why `/login`, `/register`, `/msg` and similar ship in `filters.ignored_commands` by default. Removing them publishes passwords and private messages to your channel.
- **Anonymous metrics** are reported to [bStats](https://bstats.org/plugin/bukkit/DiscordLogger/33026). They answer *"what do people do with this plugin"* and nothing else:
  - **Your setup** — server software, Minecraft version, Java version, online/offline mode, whether you're behind a proxy, and which of a short list of companion plugins are installed (Floodgate, PlaceholderAPI, CoreProtect, and the common punishment and vanish plugins).
  - **Your configuration** — which events are on, embeds or plain text, how many webhooks you route to, which of the fourteen filters differ from the defaults, whether `lang.yml` was edited and roughly how much, the config schema, and whether the file came from the website generator.
  - **Whether it's working** — how many sends failed, were rate-limited, or hit a deleted webhook, and roughly how busy the plugin is as a rate band rather than a message count.
  - **Never** — webhook URLs, player names, UUIDs, IP addresses, message content, coordinates, or world names. Counts are reported as ranges rather than exact numbers, so a chart cannot become a fingerprint.

  Builds compiled from source report too, tagged as such, so the totals aren't quietly understated. Opt out in `plugins/bStats/config.yml`, which switches it off for every bStats plugin on the server at once.

---

## 🧰 Development

Trunk-based: `main` is the only long-lived branch. Branch off `main`, open a PR with a [Conventional Commits](https://www.conventionalcommits.org/) title (`feat: ...`, `fix: ...`) — the title becomes the changelog entry. Versioning, changelogs, releases, and nightly builds are fully automated.

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — how to contribute, PR expectations, AI-assistance policy
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — how the project actually works: runtime architecture, the config system, the website, the release pipeline

Build locally:
```bash
git clone https://github.com/GodTierGamers/DiscordLogger.git
mvn -B -ntp clean package
```

---

## 📄 License

This project's license appears in the repository root.
![License](https://img.shields.io/github/license/GodTierGamers/DiscordLogger)

---

## 🤖 AI Disclosure

Parts of this project — including code, documentation, and the release automation — are developed with the assistance of AI tools. All AI-assisted changes go through the same pull-request review, CI checks, and testing as any other contribution, and a human maintainer reviews and approves everything that ships.

This repo also maintains [AGENTS.md](AGENTS.md), a technical reference written specifically for AI coding agents working in the codebase. It's not intended as human-facing documentation — see [ARCHITECTURE.md](ARCHITECTURE.md) and [CONTRIBUTING.md](CONTRIBUTING.md) for that.
