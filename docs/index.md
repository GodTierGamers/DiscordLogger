---
layout: default
title: DiscordLogger
description: Post Minecraft server events to Discord — joins, chat, deaths and moderation actions, as rich embeds or plain text. Free, open source, no bot required.
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
  </div>

  <p class="dl-hero__meta">
    <span>Latest: <strong>v<span data-dl-latest>…</span></strong></span>
    <span>Config schema {{ site.data.versions.schema }}</span>
    <span>Minecraft {{ site.data.versions.mc_display }}</span>
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
    <p>Fifteen filters for players, worlds, commands, advancements, teleports, deaths and explosions.</p>
  </div>
  <div class="dl-card">
    <h3>No account needed</h3>
    <p>It posts over a plain Discord webhook. No bot to invite, no token to manage, no third-party service.</p>
  </div>
</div>

<div class="dl-promo">
  <div class="dl-promo__text">
    <p class="dl-promo__title">Build your config without writing YAML</p>
    <p class="dl-promo__sub">Pick your version, choose what to log and what to filter out, reword any message, download both files. Runs entirely in your browser.</p>
  </div>
  <a class="dl-cta dl-cta--primary" href="/generator/">Open the generator</a>
</div>

## Guides

<div class="dl-cards">
  <div class="dl-card">
    <h3><a href="/guides/without-a-bot/">Log to Discord without a bot</a></h3>
    <p>You don't need a bot application or a token. What a webhook does, and what it doesn't.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/discordsrv-alternative/">DiscordSRV vs DiscordLogger</a></h3>
    <p>A bridge and an audit log solve different problems. Which one you actually want.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/what-gets-logged/">What gets logged</a></h3>
    <p>All nineteen events, and which are filtered by default.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/webhook-not-posting/">Webhook not posting?</a></h3>
    <p>Console shows the event but Discord doesn't. Five causes, in order of likelihood.</p>
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
builds both `config.yml` and `lang.yml` in your browser.

## Requirements

| Requirement | Version |
|---|---|
| Server | **CraftBukkit, Spigot, Paper or a fork such as Purpur**, {{ site.data.versions.mc_display }} |
| Compiled against | Bukkit API `{{ site.data.versions.api_version }}` |
| Java | **{{ site.data.versions.java }}** or newer |
| Discord | A webhook URL — no bot required |

Bukkit, Spigot, Paper and forks all run the same JAR — the plugin calls nothing
Paper-only, so there is no separate build and no platform to choose. Releases
before 2.4.0 did need Paper, for its chat API; this one does not.

## Links

- **[GitHub](https://github.com/GodTierGamers/DiscordLogger)** — source, issues, releases
- **[Modrinth](https://modrinth.com/plugin/discordlogger)** · **[Hangar](https://hangar.papermc.io/LVCHLANN/DiscordLogger)** · **[CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/discordlogger)**
- **[Report a bug](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)**
