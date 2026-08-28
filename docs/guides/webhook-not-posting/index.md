---
layout: default
title: "Discord Webhook Not Posting — How to Fix It"
description: Events reach your Minecraft console but never Discord. The five causes in order of likelihood — malformed URL, deleted webhook, blocked HTTPS, or a filter.
---

# Discord webhook not posting

Events show up in console but never reach Discord. Almost always one of five
things, and console tells you which.

**Start here:** does the event appear in your server console?

- **Yes, but not in Discord** → the webhook is the problem. Work down this page.
- **No, not even in console** → the event is switched off or filtered. Jump to
  [nothing in console](#nothing-appears-in-console-either).

That split matters because DiscordLogger logs every event to console whether or
not Discord accepted it. If console is silent, Discord was never the issue.

**Before reading further, run these two.** They answer most of what follows without
you having to go through logs at all:

```
/discordlogger status
/discordlogger doctor
```

`status` shows whether Discord is reachable, how deep the send queue is, and whether
you're being rate limited. `doctor` finds config contradictions that are valid YAML
but not what you meant — an allow-list quietly cancelling your deny-list, or every
event switched off.

To test a specific event's routing and colour without waiting for one to happen:

```
/discordlogger test player_death
```

---

## 1. The URL didn't save, or has stray characters

The most common cause by a distance. Check `plugins/DiscordLogger/config.yml`:

```yaml
webhook:
  url: "https://discord.com/api/webhooks/1234567890/AbCdEf…"
```

Things that break it:

- **Stray quotes** — a URL pasted inside quotes that are already there
- **A trailing space** before the closing quote
- **A line break** in the middle, from a terminal that wrapped it
- **The channel URL instead of the webhook URL.** `discord.com/channels/…` is the
  page you were looking at. A webhook URL contains `/api/webhooks/`.

Avoid the whole class of problem by setting it in game instead — nothing to edit
and it reloads for you:

```
/discordlogger webhook https://discord.com/api/webhooks/…
```

---

## 2. The webhook was deleted in Discord

Console shows a **404**.

Webhooks die when someone deletes them, or when the channel they belong to is
deleted. The URL keeps its shape, so nothing looks wrong until you check.

Recreate it: **Edit Channel → Integrations → Webhooks → New Webhook → Copy
Webhook URL**, then set it again.

You shouldn't have to notice this yourself. DiscordLogger checks every configured
webhook on startup, and warns staff **in game** when one stops working while the
server is running — a deleted webhook is by far the most common way logging quietly
stops, and console is exactly where that warning went unread.

---

## 3. Your host is blocking outbound HTTPS

Console shows a **timeout** rather than an error code.

Some hosts firewall outbound connections by default. Discord's API is on
`discord.com` over 443. If you can't reach it, nothing will help until that's
opened — ask your host to allow outbound HTTPS.

Quick check from the server box:

```bash
curl -I https://discord.com/api/webhooks
```

No response means it's the network, not the plugin.

---

## 4. The event is turned off

Every event has its own toggle:

```yaml
log:
  player:
    join:
      enabled: true
```

If `enabled` is `false`, nothing is sent and nothing is logged. The
[configuration reference](/config/) lists all of them.

---

## 5. A filter is catching it

This is the one people miss, because it looks identical to "broken".

Some commands are filtered **by default** — `/login`, `/register`, `/msg` and
similar — because command logging posts the line exactly as typed, which would
publish passwords and private messages to your channel. That's deliberate.

Other filters that silently suppress events:

| Filter | Suppresses |
|---|---|
| `filters.ignored_players` | everything from those players |
| `filters.exempt_permission` | everything from anyone holding it |
| `filters.ignored_worlds` | everything in those worlds |
| `filters.minimum_chat_length` | short chat messages |
| `filters.ignored_teleport_causes` | teleports, including bed and dismount by default |
| `filters.minimum_explosion_blocks` | small explosions |

---

## Nothing appears in console either

Then it isn't the webhook. In order:

1. **Is the plugin enabled?** `/plugins` should show DiscordLogger in green.
2. **Did it fail to load?** Look for `Unsupported API version` — that means the
   plugin is newer than your server. Check the
   [supported versions](/downloads/).
3. **Are you on Paper?** Spigot and CraftBukkit are not supported; the plugin
   says so plainly on startup rather than failing with a stack trace.
4. **Is the event filtered or disabled?** See above.

---

## Moderation events specifically

Bans, kicks and op changes only log **when they actually succeeded**. A failed
attempt from someone without permission doesn't appear — that's intended, so the
channel is a record of what happened rather than what was attempted.

Worth knowing: if you use a punishment plugin such as LiteBans or LibertyBans,
those keep their own database rather than the vanilla ban list, so moderation
logging may not detect them. That's a known gap rather than a misconfiguration.

---

## Still stuck

Console is the source of truth — it always says what happened. If you're stuck,
[open an issue](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)
with the console output.

**Redact your webhook URL first.** It's a credential, and an issue is public.

---

## Next

- **[Setup guide](/setup/)** — from scratch, including creating the webhook
- **[Configuration reference](/config/)** — every key explained
- **[Config generator](/generator/)** — rebuild your config cleanly
