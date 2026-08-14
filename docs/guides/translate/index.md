---
layout: default
title: "Translate DiscordLogger's Messages"
description: Every string the plugin says lives in lang.yml. Translate it, reword it, or match your server's tone — no code, no rebuild.
---

# Translate and reword every message

Every string DiscordLogger says lives in `lang.yml` — 79 of them. Change any and
run `/discordlogger reload`. No rebuild, no code.

---

## Two sections that are not interchangeable

```yaml
chat:      # shown IN GAME  — MiniMessage formatting
discord:   # posted TO DISCORD — plain text, Discord Markdown
```

**A `<green>` tag in the `discord:` section is posted as the literal characters
`<green>`.** Discord renders Markdown, not MiniMessage. Getting this backwards is
the single most common mistake.

| Section | Formatting that works |
|---|---|
| `chat:` | `<red>`, `<bold>`, `<gradient:red:blue>`, `<#ff8800>` |
| `discord:` | `**bold**`, `*italic*`, `` `code` ``, `~~strike~~` |

---

## Placeholders

Written `{player}`, `{message}`, `{killer}`, and replaced before sending. Each
message lists the ones it accepts, and **they are not interchangeable** —
`{player}` only works where the plugin has a player to put there.

A misspelled placeholder is left in the message rather than blanked, so a typo
shows up as `{palyer} joined` instead of a sentence with a hole in it. That's
deliberate: a visible mistake is a fixable one.

You can also delete a placeholder entirely:

```yaml
player-join: "{player} joined"     ->  "Steve joined"
player-join: "Someone joined"      ->  "Someone joined"
```

---

## Translating

Work through `discord:` first — that's what your community sees. `chat:` is
staff-facing and matters less.

```yaml
discord:
  player-join: "{player} a rejoint le serveur"
  player-quit: "{player} a quitté le serveur"
  player-chat: "**{player}**: {message}"
  death:
    description: "{player} est mort"
    cause-field: "Cause de la mort"
    causes:
      fall: "Est tombé de haut"
      lava: "A essayé de nager dans la lave"
```

The death causes are the bulk of it — 33 of the 79 keys. They're also the most
rewarding, because they're the messages people actually read.

---

## If you break something

| What you did | What happens |
|---|---|
| Deleted a message | Falls back to the English inside the JAR |
| Deleted a whole key | You see the key name, e.g. `chat.reload-ok` |
| Deleted the file | Written again on next start |
| Broke the YAML | Parse error logged on start, falls back to English |

Nothing is unrecoverable. Check indentation first — it must be spaces, never
tabs.

---

## Console messages stay English

Deliberately. They're not in `lang.yml`, so that searching an error or pasting it
into a support thread still matches what everyone else sees.

---

## Your file survives updates

`lang.yml` carries a schema version and is migrated forward exactly like
`config.yml` — your wording is transplanted into the new file, comments and all,
and the old one is kept as `lang.old.yml`. New messages arrive in English for you
to translate; nothing you wrote is reset.

---

## Next

- **[Configuration reference](/config/)** — every key in both files
- **[Config generator](/generator/)** — edit all 79 messages in your browser
- **[What gets logged](/guides/what-gets-logged/)** — which message goes with which event
