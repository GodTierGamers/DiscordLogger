---
layout: default
title: "lang.yml — Rewrite Every Message the Plugin Sends"
description: Change the wording, colours and language of every message DiscordLogger shows in game or posts to Discord, without touching code.
---

![DiscordLogger](/assets/DiscordLogger-Banner.webp "DiscordLogger")

# lang.yml

**_Lang format:_ {{ site.data.versions.lang }}**

Every message the plugin shows lives in `plugins/DiscordLogger/lang.yml`. Change the
wording, the colours, or the language entirely — no code, no rebuild.

The file is created on first start. Edit it, then:

```
/discordlogger reload
```

No restart needed.

---

## The two sections are not interchangeable

This is the one thing worth reading before you edit anything.

| Section | Where it goes | Format |
|---|---|---|
| `chat` | In game | **MiniMessage** — `<green>`, `<bold>`, gradients |
| `discord` | Your Discord channel | **Plain text** — Discord Markdown works, MiniMessage does not |

Discord has never heard of MiniMessage. A `<green>` tag in the `discord` section is
posted to your channel as the literal characters `<green>`:

```yaml
discord:
  player-join: "<green>{player} joined</green>"     # ❌ posts the tags as text
  player-join: "**{player}** joined"                # ✅ Discord bold
```

Discord's own Markdown does work there: `**bold**`, `*italic*`, `` `code` ``,
`~~strikethrough~~`.

---

## MiniMessage, for the `chat` section

Colours and formatting are tags that wrap the text they affect.

```yaml
chat:
  reload-ok: "<green>Reloaded in {ms} ms.</green>"
```

The common ones:

| Tag | Effect |
|---|---|
| `<red>` `<green>` `<blue>` `<yellow>` `<gold>` `<aqua>` `<gray>` `<white>` | Colour |
| `<#ff8800>` | Any hex colour |
| `<bold>` `<italic>` `<underlined>` `<strikethrough>` | Style |
| `<gradient:red:blue>` | Fade between colours |
| `<click:open_url:'https://…'>` | Clickable |
| `<hover:show_text:'Tooltip'>` | Tooltip on hover |

Full reference: [MiniMessage format](https://docs.advntr.dev/minimessage/format.html).

> **A tag you spell wrong is not an error.** It is shown to the player exactly as
> typed. If you see `<gren>` in game, that is your tag, not a bug.

**Example — make the reload message loud:**

```yaml
chat:
  reload-ok: "<gradient:#00ff88:#00aaff><bold>Reloaded</bold></gradient> <gray>({ms} ms)</gray>"
```

**Example — remove the prefix entirely:**

```yaml
chat:
  prefix: ""
```

---

## Placeholders

Placeholders look like `{player}` and are replaced before the message is sent. **Each
message accepts its own** — they are not interchangeable, because the plugin only has
certain values available at each point.

```yaml
discord:
  player-join: "{player} joined the server"      # {player} is available here
```

Every placeholder is documented in a comment above its message inside `lang.yml`.

**You can delete a placeholder** if you do not want that detail:

```yaml
player-join: "{player} joined"    # "Steve joined"
player-join: "Someone joined"     # "Someone joined"
```

**A placeholder you spell wrong is left visible**, not blanked:

```yaml
player-join: "{palyer} joined"    # posts: "{palyer} joined the server"
```

That is deliberate — a mistake you can see is a mistake you can fix, whereas a silently
empty sentence looks like a plugin bug.

---

## Worked example: translating to French

```yaml
chat:
  prefix: "<gold>[DiscordLogger]</gold> "
  reload-ok: "<green>Configuration rechargée ({ms} ms).</green>"
  no-permission: "<red>Vous n'avez pas la permission d'utiliser /{label} {command}</red>"

discord:
  player-join: "{player} a rejoint le serveur"
  player-quit: "{player} a quitté le serveur"
  player-chat: "**{player}** : {message}"
  death:
    description: "{player} est mort"
    cause-field: "Cause de la mort"
    coords-field: "Coordonnées"
    causes:
      fall: "Est tombé de haut"
      lava: "A essayé de nager dans la lave"
      drowning: "S'est noyé"
```

You only need to translate the lines you care about. Anything you leave out falls back
to the English shipped inside the plugin.

---

## Death causes

`discord.death.causes` has one entry per way Minecraft can kill someone — 33 of them.
The keys are Minecraft's own damage causes, lowercased with hyphens.

```yaml
discord:
  death:
    causes:
      fall: "Fell from a high place"
      lava: "Tried to swim in lava"
      fly-into-wall: "Flew into a wall"
      kill: "Killed by command"
```

> **Do not rename the keys.** The plugin looks each one up by that exact name. Change
> the text on the right, never the key on the left.

Every key has a comment in the file explaining when it fires — `fly-into-wall` is elytra
kinetic damage, `dryout` is an axolotl out of water, `custom` is another plugin dealing
damage.

If a death ever shows the `unknown` wording ("Died" by default), that means Minecraft
added a damage type the plugin has no wording for — worth
[reporting](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose).

---

## If you break something

| What you did | What happens |
|---|---|
| Deleted a message | Falls back to the English inside the jar. Nothing breaks. |
| Deleted a key entirely | You see the key name, e.g. `chat.reload-ok`. It names exactly what to fix. |
| Deleted the whole file | Written again on the next start. |
| Broke the YAML | The plugin logs the parse error and falls back to English. Check indentation — **spaces, never tabs**. |

Because unedited messages fall back to the shipped English, you can safely delete
everything you are not changing and keep a much shorter file.

---

## What is *not* in here

**Console messages.** Those stay in English on purpose: a translated error is one nobody
can search for, and support threads depend on everyone seeing the same text.

**Event names** like *Player Death* — those are the embed titles and are currently set
in code.

---

## Full lang.yml

> This is the exact file that ships with the plugin.

```yaml
{% include_relative lang.yml.txt %}
```
