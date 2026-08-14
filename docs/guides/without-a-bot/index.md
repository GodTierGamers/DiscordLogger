---
layout: default
title: "Log Minecraft to Discord Without a Bot"
description: You don't need a Discord bot to get server logs into Discord. A webhook does it with no token, no hosting, and no permissions to manage — here's how, and when a bot is genuinely the better choice.
---

# Log Minecraft to Discord without a bot

Most guides for getting Minecraft server events into Discord start by telling you
to create a bot application, invite it to your server, and keep a token secret
forever. For **logging** — joins, chat, deaths, bans — none of that is necessary.

A **webhook** does the same job with no bot, no token to rotate, no application to
register, and no third-party service in the middle.

---

## Bot vs webhook, honestly

| | Webhook | Bot |
|---|---|---|
| Setup | Copy a URL from a channel | Register an app, invite it, manage a token |
| Posts to Discord | ✅ | ✅ |
| Reads from Discord | ❌ | ✅ |
| Two-way chat | ❌ | ✅ |
| Account linking, roles | ❌ | ✅ |
| Runs when your server is down | ❌ | ❌ |
| Something extra to keep online | No | Sometimes |

The line is simple: **a webhook can only send.** If everything you want is
"things that happen on my server should appear in a Discord channel", that's all
you need, and the bot is overhead you'll maintain forever for features you don't
use.

If you want players to chat *from* Discord *into* Minecraft, or link accounts to
Discord roles, you need a bot — and [DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/)
is the mature, well-supported choice for that. It's a different tool, not a worse
one.

---

## Why people assume they need a bot

Three reasons, all understandable and all wrong for this use case:

**Most tutorials are about chat bridges.** Two-way chat genuinely does need a bot,
so that's what the popular guides cover — and logging gets treated as a feature of
a bridge rather than a thing you can do on its own.

**Webhooks look limited.** They are limited, deliberately. A webhook URL can post
to exactly one channel and do nothing else. That constraint is the security
feature: there's no token that can read your messages or manage your server.

**"Bot" sounds more capable.** For sending messages it isn't. The same embed, the
same colours, the same avatars — Discord renders a webhook message identically.

---

## What this looks like in practice

[DiscordLogger](/) is a Paper plugin that does exactly this. One webhook URL and
you're done:

```
/discordlogger webhook https://discord.com/api/webhooks/…
```

That's the whole setup. No application, no token, no invite, no permissions
dialog. The plugin posts joins, quits, chat, commands, deaths, advancements,
teleports, gamemode changes, server start and stop, explosions, and every
moderation action — as rich embeds or plain text.

**Each event can go to its own channel.** Moderation to a private staff channel,
chat to a public one — because a webhook is per-channel, you just use more than
one.

The **[setup guide](/setup/)** walks through creating the webhook in Discord and
checking that events arrive. The **[config generator](/generator/)** builds your
whole configuration in the browser if you'd rather not edit YAML.

---

## What you give up

Worth being straight about, because a page that only lists advantages isn't
useful:

- **No two-way chat.** Discord messages will not appear in game.
- **No account linking or role sync.**
- **No commands from Discord.** You can't run `/ban` from your phone.
- **Nothing is logged while the server is down.** A webhook is pushed *by* your
  server, so if it's offline, there's nothing to push. That's true of bots hosted
  on the same machine too.

If none of those matter to you, the bot is doing nothing for you.

---

## Treat the URL like a password

The one thing to be careful about. Anyone holding a webhook URL can post to that
channel as your server — so don't paste it into a screenshot, a public issue, or
a stream.

DiscordLogger never echoes it back, redacts it from command logging, and keeps it
out of tab-completion, precisely because the obvious ways to leak it are the ones
people hit.

If a URL does leak, delete the webhook in Discord and make a new one. There's no
token to revoke and nothing else to clean up — another small advantage of not
having a bot.

---

## Next

- **[Setup guide](/setup/)** — create the webhook and verify it works
- **[Config generator](/generator/)** — build your config in the browser
- **[Configuration reference](/config/)** — every option explained
