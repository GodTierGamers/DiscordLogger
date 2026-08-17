---
layout: default
title: "Reduce Discord Logging Spam from Minecraft"
description: Your Discord channel is drowning in teleports and chat. The fourteen DiscordLogger filters, matched to the noise they actually solve.
---

# Reduce logging spam

A logging channel nobody reads is the same as no logging channel. If yours has
become a wall of teleports, this is how to quieten it without turning things off
wholesale.

Fifteen filters, grouped by the complaint they solve.

---

## "Teleports never stop"

The most common one by a distance, and usually not really teleports at all —
Minecraft moves a player a block or two and reports it as one.

Three causes are **already excluded by default**: `EXIT_BED`, `DISMOUNT`,
`SPECTATE`.

**If you run Essentials, add `PLUGIN`.** `/home`, `/warp`, `/spawn` and `/tpa`
all arrive that way and are usually the entire remaining problem:

```yaml
filters:
  ignored_teleport_causes:
    - EXIT_BED
    - DISMOUNT
    - SPECTATE
    - PLUGIN
  minimum_teleport_distance: 50    # ignore short hops entirely
```

---

## "Chat is too busy"

```yaml
filters:
  minimum_chat_length: 3                # skips "hi", "?", "."
  ignored_chat_containing:
    - "buy gold"
```

`minimum_chat_length` counts characters, not words. Three is enough to kill
one-word spam without losing real conversation.

---

## "Command logging is unusable"

`/login`, `/register`, `/msg` and similar are filtered **by default** — command
logging posts lines exactly as typed, so without that you'd publish passwords and
private messages. Don't remove those; add to them.

If you only care about staff commands, invert it with the allow-list:

```yaml
filters:
  only_log_commands:
    - ban
    - kick
    - op
    - gamemode
```

With anything in `only_log_commands`, **only** those are logged and everything
else is ignored.

---

## "One player floods everything"

A bot account, a staff alt, or an AFK farm:

```yaml
filters:
  ignored_players:
    - AFKBot
    - 069a79f4-44e9-4726-a5be-fca90e38aaf5   # names or UUIDs
  exempt_permission: "discordlogger.exempt"   # or a permission node
```

The permission version is usually better for staff — it follows the rank rather
than a list you have to maintain.

---

## "A whole world is noise"

Creative plots, minigame worlds, a testing world:

```yaml
filters:
  ignored_worlds:
    - creative_plots
    - minigames
```

---

## "Advancements are constant"

Recipe unlocks and tab roots are already skipped. To cut more, a trailing `*`
matches a whole tab:

```yaml
filters:
  ignored_advancements:
    - "minecraft:husbandry/*"
```

---

## "Deaths in the void world"

A parkour course or a void map produces the same death over and over:

```yaml
filters:
  ignored_death_causes:
    - VOID
    - FALL
```

---

## "Every creeper gets a message"

```yaml
filters:
  minimum_explosion_blocks: 5
  ignored_explosion_sources:
    - CREEPER
```

A creeper going off in mid air destroys nothing and is rarely worth a line.

---

## The alternative: split rather than filter

If the problem is *volume* rather than *irrelevance*, don't filter — **route**.
Send chat to its own channel and the noise stops competing with what you actually
watch for. See [moderation logs in a private channel](/guides/staff-channel/).

---

## Next

- **[Configuration reference](/config/)** — every filter, with defaults
- **[Config generator](/generator/)** — set all fourteen with a form
- **[What gets logged](/guides/what-gets-logged/)** — what each event posts
