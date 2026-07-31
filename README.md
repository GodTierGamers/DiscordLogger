![DiscordLogger](https://discordlogger.godtiergamers.xyz/assets/DiscordLogger-Banner.webp "DiscordLogger")

<!-- Badges (GodTierGamers/DiscordLogger) -->
![Build](https://img.shields.io/github/actions/workflow/status/GodTierGamers/DiscordLogger/ci.yml?branch=main&label=build)
![Release](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger)
![Nightly](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger?include_prereleases&label=nightly)
![Downloads](https://img.shields.io/github/downloads/GodTierGamers/DiscordLogger/total)
![Issues](https://img.shields.io/github/issues/GodTierGamers/DiscordLogger)
![License](https://img.shields.io/github/license/GodTierGamers/DiscordLogger)
<!-- dl:sync-block:badges -->
![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Paper](https://img.shields.io/badge/Paper-26.x-blue)
<!-- /dl:sync-block -->
![Discord Webhooks](https://img.shields.io/badge/Discord-Webhooks-5865F2)

A minimal, reliable Minecraft server **logging plugin** that posts clean messages to a **Discord webhook** — in Markdown **or rich embeds**.
Built for **Paper <!-- dl:sync:paper_display -->26.x<!-- /dl:sync -->** (and Paper forks like Purpur) on **Java <!-- dl:sync:java -->25<!-- /dl:sync -->+**, tested with Geyser/Floodgate (Bedrock cross-play).

---

## ✨ Features

- **Discord webhook logging**
  - Plain text + Markdown format: `` `HH:mm:ss dd:MM:yyyy` - **<Category>**: <message> ``
  - **Embeds (optional)** with per-category colors, configurable author, player avatar thumbnails, server icon, and timestamps.
- **Config-toggleable events** (all on by default):
  - **Player**: Join, Quit, Chat, Command, Death, Advancement, Teleport, Gamemode
  - **Server**: Start, Stop, Console Command, Explosion
  - **Moderation**: Ban, Unban, Kick, Op, Deop, Whitelist Toggle, Whitelist Add/Remove
- **Live reload command**: `/discordlogger reload` (perm: `discordlogger.reload`, aliases `/dlogger`, `/dlog`)
- **Geyser-friendly death messages**: built from server-side damage context (not client-localized text).
- **Automatic config updater**: migrates your `config.yml` between versions, preserving your settings and comments.
- **Channel-aware update notifications**: stable servers are notified of new stable releases; nightly builds also warn when they fall behind.
- **Nickname support**: nicknames are recognized in server logs as `Nickname (RealName)`.
- **[Config generator](https://discordlogger.godtiergamers.xyz/generator/)**: build your `config.yml` on the website, no hand-editing needed.

---

## 📦 Downloads

| Channel | What it is | Where |
|---|---|---|
| **Stable** | Tested releases. Use these on production servers. | [Latest release](https://github.com/GodTierGamers/DiscordLogger/releases/latest) |
| **Nightly** | Automated `vX.Y.Z-BETA.N` pre-release builds of unreleased work. May be unstable — the plugin itself will remind you. | [All releases](https://github.com/GodTierGamers/DiscordLogger/releases) (marked *Pre-release*) |

Every release includes a `.sha256` checksum for the JAR.

---

## 🚀 Installation

1. Download the latest [release](https://github.com/GodTierGamers/DiscordLogger/releases/latest) and place the JAR in your server's `plugins/` folder.
2. Start the server once to generate `plugins/DiscordLogger/config.yml`.
3. Edit `config.yml` and set a valid **Discord webhook** URL at `webhook.url` — or generate a complete config with the [config generator](https://discordlogger.godtiergamers.xyz/generator/).
4. (Optional) Adjust per-event toggles under `log.*`, embed colors, and the timestamp format.
5. Restart the server (or run `/discordlogger reload`).

> **Note:** If `webhook.url` is empty/invalid, the plugin still runs and logs to console, but nothing posts to Discord until it's set.

---

## 🔌 Compatibility

- **Server:** Paper **<!-- dl:sync:paper_display -->26.x<!-- /dl:sync -->**, or a Paper fork such as Purpur. The plugin uses Paper-specific APIs, so Paper is required — it will tell you clearly on startup if the server doesn't provide them.
- **Java:** **<!-- dl:sync:java -->25<!-- /dl:sync -->+** — Minecraft <!-- dl:sync:paper_display -->26.x<!-- /dl:sync --> requires it, and the plugin is compiled for it. It will not load on older Java runtimes.
- **Cross-play:** Compatible with **Geyser/Floodgate** — death messages are server-generated for consistency across Java/Bedrock names/locales.

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
