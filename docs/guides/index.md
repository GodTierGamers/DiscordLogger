---
layout: default
title: "Guides — Minecraft Discord Logging"
description: Guides for logging a Minecraft server to Discord — running without a bot, DiscordLogger vs DiscordSRV, and fixing a webhook that isn't posting.
---

# Guides

Longer answers to the questions people actually arrive with.

<div class="dl-cards">
  <div class="dl-card">
    <h3><a href="/guides/without-a-bot/">Log to Discord without a bot</a></h3>
    <p>Most guides start by telling you to register a bot application. For logging you don't need one — and here's the honest trade-off of what a webhook can and can't do.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/discordsrv-alternative/">DiscordSRV vs DiscordLogger</a></h3>
    <p>They solve different problems and overlap on one feature. Which one fits what you're actually trying to do — and how to run both without double-posting.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/what-gets-logged/">What gets logged</a></h3>
    <p>All nineteen events, what each one posts, and which are filtered by default and why.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/webhook-not-posting/">Webhook not posting</a></h3>
    <p>Events reach console but never Discord. The five causes, in order of likelihood, and how console tells you which one you have.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/staff-channel/">Private staff channel</a></h3>
    <p>Route bans and kicks somewhere private while chat stays public. Any event can have its own webhook.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/reduce-spam/">Reduce logging spam</a></h3>
    <p>Fifteen filters, matched to the noise each one actually solves.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/translate/">Translate every message</a></h3>
    <p>All 79 strings live in <code>lang.yml</code>. Reword or translate without touching code.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/bedrock-geyser/">Geyser and Bedrock</a></h3>
    <p>Death messages that read the same for everyone, and Bedrock players flagged on join.</p>
  </div>
  <div class="dl-card">
    <h3><a href="/guides/upgrading/">Upgrading</a></h3>
    <p>What happens to your config when you drop in a new JAR — and what to do if it looks wrong.</p>
  </div>
</div>

## Also useful

- **[Setup guide](/setup/)** — install to first message in about five minutes
- **[Configuration reference](/config/)** — every key in `config.yml` and `lang.yml`
- **[Config generator](/generator/)** — build both files in your browser
