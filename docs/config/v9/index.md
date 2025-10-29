---
layout: default
title: Config Docs — v9
description: Full documentation for config.yml schema v9 — defaults, per-key explanations, and a downloadable config file.
---

![DiscordLogger](https://files.godtiergamers.xyz/DiscordLogger-Banner.png "DiscordLogger")
# config.yml Docs — v9

**_Supported Plugin Versions:_ v2.1.5**

<div style="margin:1rem 0 1.25rem;">
  <a class="btn" href="/assets/configs/v9/config.yml" download>
    Download v9 config.yml
  </a>
</div>

> The docs below explain **every key** in v9. Where examples are shown, you’ll see placeholders in comments — paste the actual code blocks on your site.

---

## Top-level keys

### `webhook`
**Required.** Discord webhook target for all logs.

- `webhook.url` — a Discord webhook URL
    - Must be a valid Discord endpoint (the plugin verifies formatting).
    - If empty/invalid, logs **won’t** post to Discord.

**Example:**
```yaml
webhook:
  url: "https://discord.com/api/webhooks/XXXX/XXXXXXXXXXXXXXXX"
```

---

### `embeds`
Control whether logs are sent as **Discord embeds** (recommended) or as plain text. Also defines embed **author** and **per-category colors**.

- `embeds.enabled` — `true` to send rich embeds (defaults to **true** in v9 shipped file).
- `embeds.author` — Small label at the top of embeds (default: **"Server Logs"**).
- `embeds.colors.*` — Hex colors for each category (nested by **player/server/moderation**).

**Color structure (v9):**
```yaml
embeds:
  enabled: true
  author: "Server Logs"
  colors:
    player:
      join: "#57F287"         # green
      quit: "#ED4245"         # red
      chat: "#5865F2"         # blurple
      command: "#FEE75C"      # yellow
      death: "#ED4245"        # red
    server:
      start: "#43B581"        # green
      stop: "#ED4245"         # red
      command: "#EB459E"      # pink
    moderation:
      ban: "#FF0000"               # red emphasis
      unban: "#FF0000"
      kick: "#FF0000"
      op: "#FF0000"
      deop: "#FF0000"
      whitelist_toggle: "#1ABC9C"  # teal
      whitelist: "#16A085"         # darker teal
```

> Notes
> • If embeds are disabled, messages are sent as formatted plain text (with an optional server name prefix; see `format.name`).

---

### `format`
Visual formatting for timestamps and an optional server label.

- `format.name` — Optional short label shown in plain text mode (e.g., proxy name).
    - Appears as ` [YourName]` after the timestamp in non-embed messages.
- `format.time` — Timestamp pattern used in both console echo and Discord text (embeds show an ISO timestamp field but still echo to console with this pattern).
    - Default used if invalid: `"[HH:mm:ss dd:MM:yyyy]"`
    - Must use the Java `DateTimeFormatter` pattern.
        - HH: Hours, mm: Minutes, ss: Seconds, dd: Day of month, MM: Month, yyyy: Year. (Case-sensitive)

**Example:**
```yaml
format:
  name: ""                          # e.g., "SMP-1" (optional)
  time: "[HH:mm:ss dd:MM:yyyy]"     # Java DateTimeFormatter pattern
```

---

### `log`
Enable/disable each event family. **v9** supports the following:

- `log.player.*`
    - `join`, `quit`, `chat`, `command`, `death`, `teleport`, `gamemode`
- `log.server.*`
    - `start`, `stop`, `command`, `explosion`
- `log.moderation.*`
    - `ban`, `unban`, `kick`, `op`, `deop`, `whitelist_toggle`, `whitelist`

**Example (structure):**
```yaml
log:
  player:
    join: true
    quit: true
    chat: true
    command: true
    death: true
    teleport: true
    gamemode: true
  server:
    start: true
    stop: true
    command: true
    explosion: true
  moderation:
    ban: true
    unban: true
    kick: true
    op: true
    deop: true
    whitelist_toggle: true
    whitelist: true
```

> Default values are defined in the **shipped file** for v9 (use the download above). Future releases may tweak defaults; the generator will always reflect the chosen version.

---

## Event details & behaviors

### Player events
- **Join / Quit** — Includes player name; color-coded (green/red).
- **Chat** — Player chat messages; uses the player color set under `embeds.colors.player.chat`.
- **Command** — Player-initiated commands (excludes commands blocked by other plugins if cancelled).
- **Death** — Player death message.
- **Teleport** — Logs teleporter & cause when available (e.g., plugin/command/end gateway).
- **Gamemode** — Logs previous → new mode, who changed it (self/other/console).

### Server events
- **Start / Stop** — Separated events with their own colors.
- **Command** — Console commands (with actor `CONSOLE`).
- **Explosion** — Logs cause (TNT, creeper, bed, respawn anchor, etc.) and a short list of **nearby players**.

### Moderation events
All moderation logs require the **action to succeed**:
- **Ban / Tempban / Unban / Kick** — Only logs when the ban/kick actually took effect (permission & result checked).
- **Op / Deop** — Only logs if permission changed.
- **Whitelist toggle / entries** — Toggling whitelist or adding/removing players.

> This prevents false-positive logs if a non-op attempts a command that fails.

---

## Colors (defaults recap)

These are the defaults shipped with v9 (customizable under `embeds.colors`):

- **Player**
    - `join` `#57F287` • `quit` `#ED4245` • `chat` `#5865F2` • `command` `#FEE75C` • `death` `#ED4245`
- **Server**
    - `start` `#43B581` • `stop` `#ED4245` • `command` `#EB459E`
- **Moderation**
    - `ban`/`unban`/`kick`/`op`/`deop` → `#FF0000`
    - `whitelist_toggle` → `#1ABC9C` • `whitelist` → `#16A085`

---

## Full config.yml (v9)

> This is the exact file that ships with v9. Download above, or paste the block below when editing this page.

```yaml
# DiscordLogger — config v9 (ships with v2.1.5)
webhook:
  url: ""   # REQUIRED: Discord webhook URL

embeds:
  enabled: true
  author: "Server Logs"
  colors:
    player:
      join: "#57F287"
      quit: "#ED4245"
      chat: "#5865F2"
      command: "#FEE75C"
      death: "#ED4245"
    server:
      start: "#43B581"
      stop: "#ED4245"
      command: "#EB459E"
    moderation:
      ban: "#FF0000"
      unban: "#FF0000"
      kick: "#FF0000"
      op: "#FF0000"
      deop: "#FF0000"
      whitelist_toggle: "#1ABC9C"
      whitelist: "#16A085"

format:
  name: ""                          # Optional server label for plain text
  time: "[HH:mm:ss dd:MM:yyyy]"     # Timestamp for console/plain text

log:
  player:
    join: true
    quit: true
    chat: true
    command: true
    death: true
    teleport: true
    gamemode: true
  server:
    start: true
    stop: true
    command: true
    explosion: true
  moderation:
    ban: true
    unban: true
    kick: true
    op: true
    deop: true
    whitelist_toggle: true
    whitelist: true

# CONFIG VERSION V9, DOWNLOADED FROM WEBSITE
```

---

## Changelog (v9)
- New **nested colors** under `embeds.colors.{player|server|moderation}`.
- Split **server start/stop** into separate events with distinct colors.
- Added **teleport**, **gamemode**, **explosion (nearby players)**.
- Moderation logs only fire when the action **succeeds**.
