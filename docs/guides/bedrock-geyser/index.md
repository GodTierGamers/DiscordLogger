---
layout: default
title: "Discord Logging for Geyser Servers"
description: DiscordLogger flags Bedrock players on join and builds death messages server-side, so they read identically for Java and Bedrock players.
---

# Logging on a Geyser server

Cross-play servers create two problems for logging. DiscordLogger handles both.

---

## Death messages read the same for everyone

Most plugins relay the death message the *client* produced, which is localised —
so a Bedrock player or a non-English client produces different text for the same
death, and your channel becomes inconsistent.

DiscordLogger builds death messages **server-side from the damage cause**, then
looks the wording up in [`lang.yml`](/guides/translate/). Same death, same
sentence, every time, whatever the player is running.

The cause is its own embed field, so it stays readable even when the sentence is
short.

---

## Bedrock players are flagged on join

If the server runs Geyser with Floodgate, joins from Bedrock get a **Platform**
field:

```yaml
log:
  player:
    join:
      enabled: true
      show_platform: true      # default
```

**It never says "Java".** Only Bedrock is flagged, and only when there's positive
evidence. On a server without Floodgate every player would otherwise be labelled
Java, which would be a guess presented as a fact.

---

## How detection works, and why it's belt-and-braces

Two independent signals, **OR'd rather than chained**:

1. **Floodgate's API**, when Floodgate is installed
2. **The UUID shape** — Floodgate issues UUIDs whose most significant bits are zero

Either one is enough. That matters behind a proxy: a backend server's player
registry doesn't necessarily know a player the proxy handshook, so Floodgate's
API can answer "no" for someone whose UUID is plainly Floodgate's.

An earlier version returned the API's answer directly and **real Bedrock players
went unflagged on Velocity networks** because of exactly that. The signals are
OR'd now, and there's a test pinning it.

---

## Setup notes

**`softdepend`, not `depend`.** The plugin runs perfectly without Floodgate — the
Platform field simply never appears. Nothing to configure.

**Floodgate must be named in `plugin.yml`** for its API to be reachable, because
Paper gives each plugin its own classloader. That's already done; it's mentioned
only because a missing `softdepend` is what caused detection to silently fall
back to the UUID guess once before.

**Bedrock usernames often carry a prefix** — Floodgate's default is `.`. That
prefix appears in logs as part of the name, which is usually what you want.

---

## Next

- **[Setup guide](/setup/)** — from install to first message
- **[What gets logged](/guides/what-gets-logged/)** — all nineteen events
- **[Translate](/guides/translate/)** — reword the death causes
