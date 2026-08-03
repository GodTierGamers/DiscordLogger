---
layout: default
title: DiscordLogger
description: Post Minecraft server events to Discord — joins, quits, chat, deaths and moderation actions, as rich embeds or plain text. Free, open source, and configurable per event.
---

<section class="dl-hero">
  <span class="dl-hero__eyebrow">Free &amp; open source</span>

  <h1>Minecraft server logging to&nbsp;Discord</h1>

  <p class="dl-hero__sub">
    A Paper plugin that posts what happens on your server to a Discord channel —
    joins, chat, deaths, and every moderation action — as rich embeds or plain text.
    Configure each event individually, or send them to different channels entirely.
  </p>

  <div class="dl-hero__actions">
    <a class="dl-cta dl-cta--primary" href="/downloads/">Download</a>
    <a class="dl-cta dl-cta--ghost" href="/setup/">Setup guide</a>
    <a class="dl-cta dl-cta--ghost" href="/generator/">Build a config</a>
  </div>

  <p class="dl-hero__meta">
    <span>Latest: <strong>v<span data-dl-latest>…</span></strong></span>
    <span>Config schema {{ site.data.versions.schema }}</span>
    <span>Paper {{ site.data.versions.paper_display }}</span>
    <span>Java {{ site.data.versions.java }}+</span>
  </p>
</section>

## What it logs

<div class="dl-cards">
  <div class="dl-card">
    <h3>Players</h3>
    <p>Joins, quits, chat, commands, deaths, advancements, teleports and gamemode changes.</p>
  </div>
  <div class="dl-card">
    <h3>Moderation</h3>
    <p>Bans, unbans, kicks, op and deop, and whitelist changes — only when they actually succeeded.</p>
  </div>
  <div class="dl-card">
    <h3>Server</h3>
    <p>Start, stop, console commands, and explosions with the source and blocks destroyed.</p>
  </div>
</div>

Every event has its own toggle, its own embed colour, and can be sent to its own
Discord channel.

## Why this one

<div class="dl-cards">
  <div class="dl-card">
    <h3>Nothing gets dropped</h3>
    <p>Sends are queued and paced against Discord's rate limits, per webhook, so a busy chat channel never delays your moderation log.</p>
  </div>
  <div class="dl-card">
    <h3>Passwords stay out</h3>
    <p><code>/login</code> and <code>/msg</code> are filtered by default. Command logging would otherwise post them to your channel verbatim.</p>
  </div>
  <div class="dl-card">
    <h3>Your config survives updates</h3>
    <p>Settings are migrated forward automatically, one schema version at a time, and the previous file is always kept.</p>
  </div>
  <div class="dl-card">
    <h3>Every message is yours</h3>
    <p><code>lang.yml</code> holds all of them. Reword or translate anything without touching code.</p>
  </div>
  <div class="dl-card">
    <h3>Cut the noise</h3>
    <p>Fourteen filters for players, worlds, commands, advancements, teleports, deaths and explosions.</p>
  </div>
  <div class="dl-card">
    <h3>No account needed</h3>
    <p>It posts over a plain Discord webhook. No bot to invite, no token to manage, no third-party service.</p>
  </div>
</div>

## Get started

1. **[Download](/downloads/)** the JAR and drop it into `plugins/`.
2. Start the server once. It writes `config.yml` and `lang.yml`.
3. Set your webhook, either in `config.yml` or in game:

   ```
   /discordlogger webhook https://discord.com/api/webhooks/…
   ```

4. That's it — logging starts immediately.

The **[setup guide](/setup/)** covers creating the webhook and verifying it works.
Prefer to configure everything up front? The **[config generator](/generator/)**
builds a complete `config.yml` in your browser.

## Requirements

| Requirement | Version |
|---|---|
| Server | **Paper {{ site.data.versions.paper_display }}** or a fork such as Purpur |
| Minimum API | `{{ site.data.versions.min_paper }}` |
| Java | **{{ site.data.versions.java }}** or newer |
| Discord | A webhook URL — no bot required |

Spigot and CraftBukkit are not supported: the plugin uses Paper's chat API, and
says so clearly on startup rather than failing with a stack trace.

## Links

- **[GitHub](https://github.com/GodTierGamers/DiscordLogger)** — source, issues, releases
- **[Modrinth](https://modrinth.com/plugin/discordlogger)** · **[Hangar](https://hangar.papermc.io/LVCHLANN/DiscordLogger)**
- **[Report a bug](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)**
