---
layout: default
title: "How to Set Up Minecraft Discord Logging"
description: Step-by-step guide to installing DiscordLogger on a Bukkit, Spigot or Paper server, creating a Discord webhook, and checking that events are reaching your channel.
---

# Setup

Getting logs into Discord takes about five minutes. You need a Paper server and a
Discord channel you can create a webhook in.

> **Latest version:** v<span data-dl-latest>…</span> · **Config schema:** {{ site.data.versions.schema }}

---

## What you need

| Requirement | Version |
|---|---|
| Server | **Paper, Spigot or a fork such as Purpur**, {{ site.data.versions.paper_display }} |
| Java | **{{ site.data.versions.java }}** or newer |
| Discord | A channel where you have **Manage Webhooks** |
| Minecraft | Operator, or console access |

Bukkit, Spigot, Paper and forks all work, from the same JAR — there is no
platform to pick. Releases before 2.4.0 needed Paper for its chat API; this one
does not.

---

## 1. Install

1. [Download the latest release](/downloads/).
2. Put the JAR in your server's `plugins/` folder.
3. Start the server once.

That first start creates `plugins/DiscordLogger/` containing two files:

| File | What it is |
|---|---|
| `config.yml` | What gets logged, where, and how it looks |
| `lang.yml` | Every message the plugin sends, so you can reword or translate it |

The plugin runs happily without a webhook — it logs to console and tells you what's
missing. Nothing breaks while you finish setting up.

---

## 2. Create a Discord webhook

In Discord:

1. Right-click the channel you want logs in → **Edit Channel**
2. **Integrations** → **Webhooks** → **New Webhook**
3. Give it a name and icon if you like → **Copy Webhook URL**

> **Treat that URL like a password.** Anyone who has it can post to that channel as
> your server. Don't paste it into a screenshot, a public issue, or a stream.

---

## 3. Give the plugin your webhook

Either way works.

**In game or from console** — nothing to edit, and it reloads for you:

```
/discordlogger webhook https://discord.com/api/webhooks/…
```

The URL is never echoed back, and command logging redacts it, so running this
doesn't publish the URL to the channel you're setting up.

**Or edit `config.yml`** and reload:

```yaml
webhook:
  url: "https://discord.com/api/webhooks/1234567890/AbCdEf…"
```

```
/discordlogger reload
```

---

## 4. Check it works

Run `/discordlogger test` — it sends a real message through the same path a real
event uses, so if it arrives, your webhook works.

Then join and leave the server. You should get an embed in your channel with your
skin as the thumbnail.

If nothing arrives, in order of likelihood:

| Symptom | Cause |
|---|---|
| Console says the webhook URL is missing or invalid | The URL didn't save, or has stray quotes or spaces |
| Console shows a `404` | The webhook was deleted in Discord |
| Nothing in console at all | The event is turned off in `config.yml`, or filtered |
| Console shows a timeout | Your host is blocking outbound HTTPS to Discord |

Console always logs the event, whether or not Discord accepted it — so if you see it
in console but not in Discord, the problem is the webhook, not the plugin.

Two commands answer most of this without reading logs:

| Command | Tells you |
|---|---|
| `/discordlogger status` | whether Discord is reachable, how deep the send queue is, and whether you are rate limited |
| `/discordlogger doctor` | contradictions in your config that are valid YAML but not what you meant |

DiscordLogger also checks your webhooks on startup and warns staff **in game** if one
stops working, so a webhook deleted months later doesn't go unnoticed.

---

## 5. Decide what to log

Every event has its own toggle in `config.yml`:

```yaml
log:
  player:
    join:
      enabled: true
      color: "#57F287"
      webhook: ""          # empty = the main webhook above
```

Three ways to go further:

- **[Config generator](/generator/)** — pick what you want in your browser and download the finished `config.yml` and `lang.yml`.
- **[Configuration reference](/config/)** — every key, with examples.
- **Send events to different channels** — put a webhook URL on any individual event, so moderation can go somewhere private while chat stays public.

### Worth knowing before you go live

**Some commands are filtered by default.** `/login`, `/register`, `/msg` and similar
are never logged, because command logging posts the line exactly as typed — which
would publish passwords and private messages to your channel. You can change that in
`filters.ignored_commands`, but read why it's there in the
[configuration reference](/config/) first.

**Moderation events only log when they succeeded.** A failed ban attempt from someone
without permission doesn't appear.

**Teleports are noisy.** Getting out of bed and dismounting are excluded already; add
`PLUGIN` to `filters.ignored_teleport_causes` if you use Essentials, since `/home`,
`/warp` and `/spawn` all arrive that way.

**The plugin reports anonymous metrics** to [bStats](https://bstats.org/plugin/bukkit/DiscordLogger/33026).
They describe your *setup*, never your players: server and Java version, online or
offline mode, whether you run behind a proxy, which companion plugins are installed,
which events and filters you've changed from the defaults, whether you edited
`lang.yml`, and where your config came from.

Never collected: webhook URLs, player names, UUIDs, IP addresses, message content,
coordinates, or world names. Counts are reported as ranges rather than exact numbers.

Turn it off in `plugins/bStats/config.yml`, which covers every bStats plugin at once.

---

## 6. Updating later

Drop in the new JAR and restart. That's it.

Your settings are carried across automatically — the plugin reads the config version,
upgrades it one step at a time, and keeps your old file as `config.old.yml` (and
`lang.old.yml`) in case you want to compare.

New options arrive at their defaults. Nothing you changed is reset.

If something looks wrong after an update, `/discordlogger regen confirm` rebuilds
`config.yml` from scratch at the current version and backs up your existing one first.

---

## Next

- **[Configuration reference](/config/)** — every key in `config.yml` and `lang.yml`
- **[Config generator](/generator/)** — build both files without editing YAML
- **[Report a problem](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)**
