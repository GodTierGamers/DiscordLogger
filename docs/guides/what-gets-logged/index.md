---
layout: default
title: "What DiscordLogger Logs — Every Event"
description: Every Minecraft event DiscordLogger posts to Discord — joins, chat, deaths, bans, kicks and explosions — what each looks like and how to turn it off.
---

# What gets logged

Nineteen events, each with its own toggle, its own embed colour, and its own
optional channel. This page is what each one actually posts.

Everything here is on by default except where noted, and every one can be turned
off in `config.yml` or with the [config generator](/generator/).

---

## Player events

### Join and quit

Posts when someone connects or disconnects, with their skin as the embed
thumbnail.

If the server runs Geyser with Floodgate, joins from **Bedrock** are flagged as
such. It never labels anyone "Java" — absence of evidence isn't evidence, and
saying so would be wrong on any server without Floodgate installed.

```yaml
log:
  player:
    join:
      enabled: true
      show_platform: true
```

### Chat

Every public chat message, with the player's name in bold. Messages are escaped
before sending, so nobody can inject Markdown into your channel by typing it.

Filterable by content and by minimum length — useful against `hi`, `?` and `.`
spam.

### Commands

Every command a player runs, exactly as typed.

**This is why `/login`, `/register` and `/msg` are filtered by default.** Command
logging posts the line verbatim, so without that deny-list you would publish
passwords and private messages to Discord. Matching ignores arguments and plugin
prefixes, so `/essentials:msg hi` is caught as `msg`.

### Deaths

The death message, plus a **Cause of Death** field built from server-side damage
context rather than the client's localised text — so it reads identically for
Java and Bedrock players, and in any language.

Coordinates are **off by default**, because anyone who can read the channel can
then find the body and its dropped items. Turn them on with `show_coords`.

Deaths can be filtered by cause, which matters on a void world or a parkour
course.

### Advancements

Advancement unlocks. Recipe unlocks and tab roots are skipped by default — they
fire constantly and mean nothing to a reader.

Filterable by key, and a trailing `*` matches a whole tab:
`minecraft:husbandry/*`.

### Teleports

Teleports, with distance. The noisiest event on most servers, so three causes are
excluded by default because they aren't really teleports at all:

| Cause | Why it's excluded |
|---|---|
| `EXIT_BED` | standing up from a bed |
| `DISMOUNT` | getting off a horse, boat or minecart |
| `SPECTATE` | a spectator jumping to a player |

**Add `PLUGIN` if you use Essentials** — `/home`, `/warp` and `/spawn` all arrive
that way and are usually the bulk of what's left.

### Gamemode changes

When someone switches between survival, creative, adventure or spectator. Often
the first thing you want in an audit channel.

---

## Server events

### Start and stop

The plugin announces server start, and logs a clean shutdown.

### Console commands

Commands run from the console or terminal, with the sender shown as `Server`.

### Explosions

Explosions with their source and how many blocks were destroyed — creepers, TNT,
end crystals, beds in the Nether, respawn anchors.

Filterable by source and by a minimum block count, so a creeper going off in mid
air over nothing doesn't earn a message.

---

## Moderation events

Bans, unbans, kicks, op, deop, whitelist on/off, and whitelist add/remove.

**Only logged when they actually succeeded.** A failed ban attempt from someone
without permission doesn't appear, so the channel is a record of what happened
rather than what was tried.

Each carries who did it and, where the command supplies one, why.

> **Known limitation:** punishment plugins such as LiteBans, LibertyBans and
> AdvancedBan keep their own database rather than the vanilla ban list, so their
> punishments may not be detected. If you use one of those, moderation logging
> may be quieter than you expect.

---

## Sending events to different channels

Any event can carry its own webhook, so moderation can go to a private staff
channel while chat stays public:

```yaml
log:
  moderation:
    ban:
      enabled: true
      webhook: "https://discord.com/api/webhooks/…"
```

Anything left blank uses the main webhook. Each destination gets its own queue,
so a busy chat channel never delays your moderation log.

---

## Turning things off

Three ways, depending on how much you want to change:

- **One event** — set its `enabled` to `false`
- **A pattern across events** — use one of the [fourteen filters](/config/): players, worlds, commands, chat content, advancements, teleport causes, death causes, explosion sources
- **Everything, visually** — the [config generator](/generator/) builds the whole file in your browser

---

## Next

- **[Setup guide](/setup/)** — from install to first message
- **[Configuration reference](/config/)** — every key explained
- **[Webhook not posting?](/guides/webhook-not-posting/)** — if an event isn't arriving
