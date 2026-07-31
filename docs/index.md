---
layout: default
title: DiscordLogger
description: Post Minecraft server events to Discord — joins, quits, chat, deaths and moderation actions, as rich embeds or plain text. Free, open source, and configurable per event.
---

![DiscordLogger — Minecraft server logging to Discord](/assets/DiscordLogger-Banner.webp)

# Minecraft server logging to Discord

**DiscordLogger** is a free, open-source Paper plugin that sends your Minecraft server's
events straight to a Discord channel over a webhook — player joins and quits, chat,
deaths, advancements, and moderation actions like bans, kicks and whitelist changes.
Every event is individually configurable, and messages arrive as rich embeds or plain text.

> **Latest plugin:** v<span data-dl-latest>…</span>  
> **Latest config version:** v9

---

## What it does

- **Rich embeds** (or plain text fallback) for key events:
    - Player: join, quit, chat, command, death, teleport, gamemode
    - Server: start, stop, command, explosion (with nearby players)
    - Moderation: ban, unban, kick, op, deop, whitelist toggle/entries
- **Per-category colors** and a consistent, fields-first embed style
- **Smart safety**: only logs moderation actions if they actually succeeded
- **Versioned config**: automatic merging across updates (keeps your settings)

---

## Quick links

-  **[Setup / Install](setup/index.md)**
-  **[config.yml Generator](./generator/)**
-  **[config.yml Docs (versioned)](./config/)**

---

## Quick start

1. Drop the plugin JAR into `/plugins`.
2. Start once to generate `config.yml`.
3. Set your Discord **webhook URL** under `webhook.url`.
4. (Optional) Turn on/off embeds (on by default):
   ```yaml
   embeds:
     enabled: true
     author: "Server Logs"
   ```
5. Reload: /discordlogger reload.
> You can also use the [config.yml Generator](./generator/) to create a fully customized config.yml

## Useful Links
- Source: https://github.com/GodTierGamers/DiscordLogger
- Releases: https://github.com/GodTierGamers/DiscordLogger/releases