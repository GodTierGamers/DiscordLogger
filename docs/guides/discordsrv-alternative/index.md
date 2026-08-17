---
layout: default
title: "DiscordSRV vs DiscordLogger"
description: DiscordSRV bridges chat with a bot. DiscordLogger logs events over a webhook. An honest comparison of what each does and which one you actually need.
---

# DiscordSRV vs DiscordLogger

These get compared a lot, and they shouldn't really compete — they solve
different problems and overlap on one feature. This page is here because
"I only want the logging part" is a real thing people search for.

**Short version:** if you want players chatting between Discord and Minecraft,
use [DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/). If you
want a record of what happens on your server, in Discord, without running a bot,
that's what [DiscordLogger](/) is for.

---

## What each one is

**DiscordSRV** is a full Discord integration. It runs a bot, bridges chat in both
directions, links Minecraft accounts to Discord accounts, syncs roles, and can
run commands from Discord. It's mature, widely used, and the standard answer for
Minecraft ↔ Discord integration.

**DiscordLogger** posts server events to a Discord channel over a webhook. It
sends; it never reads. No bot, no token, no account linking.

---

## Side by side

| | DiscordLogger | DiscordSRV |
|---|---|---|
| Setup | One webhook URL | Bot application, invite, token |
| Discord → Minecraft chat | ❌ | ✅ |
| Minecraft → Discord chat | ✅ | ✅ |
| Account linking / role sync | ❌ | ✅ |
| Commands from Discord | ❌ | ✅ |
| Moderation logging | ✅ every action, with who and why | Partial |
| Per-event channel routing | ✅ any event to its own channel | Limited |
| Deaths with cause and coordinates | ✅ | Basic |
| Config migration between versions | ✅ automatic, comments preserved | Manual |
| Browser config generator | ✅ | ❌ |
| Every message customisable | ✅ `lang.yml` | Partial |

---

## Pick DiscordSRV if…

- You want players to talk to each other across Discord and Minecraft
- You want Discord roles tied to in-game ranks or purchases
- You want staff to run commands from Discord
- You want one plugin covering everything Discord-related

It does all of that well and DiscordLogger does none of it.

---

## Pick DiscordLogger if…

- You want an **audit trail**, not a chat room — who was banned, by whom, and why
- You don't want to run a bot, manage a token, or register an application
- You want **different events in different channels** — moderation somewhere
  private, chat somewhere public
- You want to filter aggressively: ignore specific commands, players, worlds,
  teleport causes, death causes
- You're on a **whitelisted, staff-run or managed server** where the question is
  usually "who did that" rather than "what's everyone saying"

---

## Can you run both?

Yes, and some people do — DiscordSRV for chat, DiscordLogger for the moderation
audit trail in a private channel.

The thing to watch is **double-posting chat**. If both are relaying player chat
to Discord, turn chat off in one of them. In DiscordLogger that's one line:

```yaml
log:
  player:
    chat:
      enabled: false
```

Everything else — moderation, deaths, explosions, server lifecycle — doesn't
overlap.

---

## The honest summary

DiscordSRV is a bigger, more capable plugin with far more users, and if you want
a bridge you should use it.

DiscordLogger is narrower on purpose. It does logging, and because that's all it
does, it does it in more depth: every moderation action verified before it's
logged, fifteen filters, per-event channels, and a config that migrates itself
forward when you update.

Not needing a bot isn't a limitation here — it's the point.

---

## Next

- **[Setup guide](/setup/)** — five minutes, one webhook
- **[Log to Discord without a bot](/guides/without-a-bot/)** — why a webhook is enough
- **[Config generator](/generator/)** — build your config in the browser
