---
layout: default
title: "Moderation Logs in a Private Discord Channel"
description: Route bans, kicks and op changes to a private channel while chat and joins stay public — any DiscordLogger event can have its own webhook.
---

# Send moderation logs to a private channel

Chat and joins are fine in a public channel. Bans, kicks and op changes usually
aren't — they name players, carry reasons, and are nobody's business but staff's.

**Any event can have its own webhook**, so this is a per-event setting rather
than an all-or-nothing choice.

---

## The idea

A Discord webhook posts to exactly one channel. That's normally a limitation;
here it's the mechanism. Make a second webhook in your staff channel, put it on
the moderation events, and leave everything else pointing at the main one.

---

## Setup

**1. Create a webhook in your staff channel.** Right-click it → **Edit Channel**
→ **Integrations** → **Webhooks** → **New Webhook** → **Copy Webhook URL**.

**2. Put it on the moderation events** in `config.yml`:

```yaml
log:
  moderation:
    ban:
      enabled: true
      color: "#FF0000"
      webhook: "https://discord.com/api/webhooks/…staff…"
    kick:
      enabled: true
      color: "#FF0000"
      webhook: "https://discord.com/api/webhooks/…staff…"
    op:
      enabled: true
      color: "#FF0000"
      webhook: "https://discord.com/api/webhooks/…staff…"
```

**3. Leave everything else alone.** An empty `webhook:` means "use the main one":

```yaml
log:
  player:
    chat:
      enabled: true
      webhook: ""          # public channel
```

**4. Reload.**

```
/discordlogger reload
```

The [config generator](/generator/) does the same thing with a form, if you'd
rather not hand-edit — step 6 is per-event channels.

---

## A split that works well

| Channel | Events |
|---|---|
| **#server-log** (public) | join, quit, chat, advancement, server start/stop |
| **#staff-log** (private) | ban, unban, kick, op, deop, whitelist, console commands |
| **#grief-log** (private) | explosions, deaths with coordinates, teleports |

Console commands belong with staff, not in public — an admin running
`/give` or `/gamemode` isn't something a public channel needs, and console
commands can contain things you'd rather not broadcast.

---

## Why this doesn't slow anything down

Each destination gets **its own queue and its own worker**, so a busy chat
channel can't delay the staff log behind it. Discord rate-limits per webhook, and
using several means each has its own budget rather than sharing one.

If chat is heavy enough to hit a limit, moderation still lands immediately.

---

## Every URL is a credential

Each webhook URL can post to its channel as your server. A staff webhook leaking
is worse than a public one, because the channel it posts to is one people trust.

DiscordLogger never echoes a URL back, redacts it from command logging, and keeps
it out of tab-completion. If one leaks, delete that webhook in Discord and make a
new one — nothing else needs cleaning up.

---

## Next

- **[Configuration reference](/config/)** — every key
- **[What gets logged](/guides/what-gets-logged/)** — all nineteen events
- **[Reduce logging spam](/guides/reduce-spam/)** — the filters, if a channel is too noisy
