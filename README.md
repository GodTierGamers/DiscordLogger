![DiscordLogger](https://files.godtiergamers.xyz/DiscordLogger-Banner.png "DiscordLogger")

<!-- Badges (GodTierGamers/DiscordLogger) -->
![Build](https://img.shields.io/github/actions/workflow/status/GodTierGamers/DiscordLogger/ci.yml?branch=main&label=build)
![Release](https://img.shields.io/github/v/release/GodTierGamers/DiscordLogger)
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
  - **Embeds (optional)** with per-category colors, configurable author, player avatar thumbnails, server icon, and timestamps.
- **Config-toggleable events** (all on by default):
  - **Server**: Start, Stop, Server Command
  - **Player**: Join, Quit, Chat, Command, Death
  - **Moderation**: Ban, Unban, Kick, Op, Deop, Whitelist Toggle, Whitelist Add/Remove
- **Live reload command**: `/discordlogger reload` (perm: `discordlogger.reload`)
- **Geyser-friendly death messages**: built from server-side damage context (not client-localized text).
- **Automatic Config Updater**: Updates the config.yml file with new features
- **Automatic update prompts**: Plugin will prompt you when a new version is available
- **Nickname support**: Nicknames are recognized in server logs


---

## 📦 Installation

1. Download the latest [release](https://github.com/GodTierGamers/DiscordLogger/releases/latest) and place the JAR in your server’s `plugins/` folder.  
2. Start the server once to generate `plugins/DiscordLogger/config.yml`.  
3. Edit `config.yml` and set a valid **Discord webhook** URL at `webhook.url`.  
4. (Optional) Adjust the timestamp format and per-event toggles under `log.*`, and set `embeds.enabled: true` to use embeds.  
5. Restart the server (or run `/discordlogger reload` after editing config).


> **Note:** If `webhook.url` is empty/invalid, the plugin will not function until set.

---

## 🔌 Compatibility

- **Server:** Paper/Spigot **1.21+** (Tested on 1.21.8 Paper)  
- **Java:** **21+**  
- **Cross-play:** Compatible with **Geyser/Floodgate** — death messages are server-generated for consistency across Java/Bedrock names/locales.

---

## 🧰 Development

### Branch Usage:

- Main: Current latest version, not developed on, only updated via PR or for workflow updates
- Dev: May have new features implemented compared to main, is updated frequently and is fairly stable, create branches off dev
- All other branches (e.g. feat/banLogs): Active features in development, not expected to be functional yet, still in active development

To add a new feature:
- Create a branch based off dev
- Name it approprietly (e.g. feat/banLogs)
- Develop feature
- PR into dev
- When enough features are compiled, dev will be PRed into main

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
