# DiscordLogger v2

<!-- Badges (GodTierGamers/DiscordLogger) -->
![Build](https://img.shields.io/github/actions/workflow/status/GodTierGamers/DiscordLogger/ci.yml?branch=main&label=build)
![Release](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger?include_prereleases)
![Downloads](https://img.shields.io/github/downloads/GodTierGamers/DiscordLogger/total)
![Issues](https://img.shields.io/github/issues/GodTierGamers/DiscordLogger)
![PRs](https://img.shields.io/github/issues-pr/GodTierGamers/DiscordLogger)
![Stars](https://img.shields.io/github/stars/GodTierGamers/DiscordLogger)
![Last Commit](https://img.shields.io/github/last-commit/GodTierGamers/DiscordLogger)
![Code Size](https://img.shields.io/github/languages/code-size/GodTierGamers/DiscordLogger)
![License](https://img.shields.io/github/license/GodTierGamers/DiscordLogger)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue)
![Discord Webhooks](https://img.shields.io/badge/Discord-Webhooks-5865F2)

A minimal, reliable Minecraft server **logging plugin** that posts clean messages to a **Discord webhook** — in Markdown **or rich embeds**.  
Built for Paper/Spigot 1.21+, tested with Geyser/Floodgate (Bedrock cross-play).

---

## ✨ Features

- **Discord webhook logging**
  - Plain text + Markdown format: `` `HH:mm:ss dd:MM:yyyy` - **<Category>**: <message> ``
  - **Embeds (optional)** with per-category colors, configurable author, hard-coded footer (**DiscordLogger**), player avatar thumbnails, server icon, and Discord-rendered timestamps.
- **Config-toggleable events** (all on by default):
  - **Server**: Start, Stop, Server Command
  - **Player**: Join, Quit, Chat, Command, Death
- **Live reload command**: `/discordlogger reload` (perm: `discordlogger.reload`)
- **Fail-fast config**: plugin disables if `webhook.url` is missing/invalid.
- **Geyser-friendly death messages**: built from server-side damage context (not client-localized text).

---

## 📦 Installation

1. Download the latest **release** and place the JAR in your server’s `plugins/` folder.  
2. Start the server once to generate `plugins/DiscordLogger/config.yml`.  
3. Edit `config.yml` and set a valid **Discord webhook** URL at `webhook.url`.  
4. (Optional) Adjust the timestamp format and per-event toggles under `log.*`, and set `embeds.enabled: true` to use embeds.  
5. Restart the server (or run `/discordlogger reload` after editing config).

> **Note:** If `webhook.url` is empty/invalid, the plugin will log a SEVERE message and **disable itself** on startup.

---

## ⚙️ Configuration Overview

- **Webhook**
  - `webhook.url` — *REQUIRED* Discord webhook URL.
- **Time format**
  - `format.time` — Java DateTimeFormatter (e.g., `[HH:mm:ss dd:MM:yyyy]`).
- **Embeds**
  - `embeds.enabled` — `true` to send embeds.
  - `embeds.author` — text shown as the embed author.
  - `embeds.colors.*` — per-category embed colors (e.g., `server`, `player_join`, `player_quit`, `player_chat`, `player_command`, `server_command`, `player_death`).
- **Event toggles**
  - `log.server.start`, `log.server.stop`, `log.server.command`
  - `log.player.join`, `log.player.quit`, `log.player.chat`, `log.player.command`, `log.player.death`

---

## 🧪 Example Output

```
`12:34:56 01:09:2025` - **Server**: Server Started
`12:35:10 01:09:2025` - **Player Join**: Steve joined the server
`12:35:42 01:09:2025` - **Player Chat**: Steve — hello world
`12:36:05 01:09:2025` - **Player Command**: Steve ran: /spawn
`12:36:30 01:09:2025` - **Server Command**: Server ran: /save-all
`12:37:12 01:09:2025` - **Player Death**: Steve was slain by a zombie
`12:40:00 01:09:2025` - **Server**: Server Stopped
```

(When `embeds.enabled: true`, the same events are sent as embeds with colors, author/footer, thumbnails, and timestamp.)

---

## 🔌 Compatibility

- **Server:** Paper/Spigot **1.21+** (Tested on 1.21.8 Paper)  
- **Java:** **21+**  
- **Cross-play:** Compatible with **Geyser/Floodgate** — death messages are server-generated for consistency across Java/Bedrock names/locales.

---

## 🧰 Development

- Build with Maven:
  ```bash
  git clone https://github.com/GodTierGamers/DiscordLogger.git
  mvn -B -DskipTests package
  ```

---

## 📄 License

This project’s license appears in the repository root.  
![License](https://img.shields.io/github/license/GodTierGamers/DiscordLogger)

---

## 📬 Support

Open an **Issue** with:
- A clear description of the problem
- Relevant `server.log` snippets
- (If config-related) your `config.yml` with the webhook URL redacted
