---
layout: default
title: "Upgrading DiscordLogger Safely"
description: Drop in the new JAR and restart. Your settings are migrated forward one schema version at a time, comments preserved, and the old file is always kept.
---

# Upgrading

Drop in the new JAR and restart. That's the whole procedure.

This page exists because "will this reset my config" is a fair thing to worry
about, and the honest answer is longer than "no".

---

## What actually happens

On start, the plugin works out which schema version your files are, and if
they're older than the one it ships:

1. **Backs up** — `config.old.yml` and `lang.old.yml`, next to the originals
2. **Migrates forward one version at a time** — v8 to v10 goes through v9, never straight across
3. **Transplants your values** into the new file's structure, keeping every comment, blank line and piece of ASCII art
4. **Adds new keys at their defaults** — nothing you changed is reset
5. **Says so in console**, naming both versions

Only forward. A config from a **newer** build is never downgraded — it tells you
and changes nothing, because rewriting it against an older default would drop
keys the newer schema added, and that's data loss rather than a migration.

---

## How it knows which version you're on

Two independent ways:

- **What the file declares** — `config-version` and the trailer comment
- **What the file *is*** — the set of keys actually present

A declaration can be edited or deleted. A shape can't lie. **When they disagree,
the shape wins**, so a hand-mangled version marker degrades to a correct guess
rather than a wrong migration.

---

## Coming from 2.1.x

2.2.0 moved config schema v9 to v10, which is the largest change so far. Two
kinds of key moved, and both are handled automatically:

```yaml
# v9
log:
  player:
    join: true
embeds:
  colors:
    player:
      join: "#57F287"

# v10
log:
  player:
    join:
      enabled: true
      color: "#57F287"
      webhook: ""
```

Colours moved from `embeds.colors.*` into the event itself, and each event became
a section so it could carry its own webhook. One rename to be aware of: v9's
`embeds.colors.moderation.whitelist` becomes
`log.moderation.whitelist_edit.color`.

You also gain `lang.yml`, fourteen filters, per-event routing and the Bedrock
indicator — all at their defaults, so nothing changes until you want it to.

---

## If it goes wrong

**Compare against the backup.** `config.old.yml` is exactly what you had.

**Start clean** if you'd rather:

```
/discordlogger regen confirm
```

That rebuilds `config.yml` from the current default and backs up your existing
one first. Your settings are *not* carried over — it's the "start again" button,
not a repair.

**Check the console message.** It names the versions it migrated between, which
is the fastest way to tell whether migration even ran.

---

## Downgrading

Supported in the sense that nothing breaks: the older plugin sees a config newer
than it understands, tells you, and ignores keys it doesn't recognise rather than
overwriting them. Your file is left alone.

---

## Next

- **[Configuration reference](/config/)** — every key, per schema version
- **[Config generator](/generator/)** — rebuild both files from scratch
- **[Webhook not posting?](/guides/webhook-not-posting/)** — if logging stops after an upgrade
