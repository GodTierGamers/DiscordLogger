---
layout: default
title: Config Docs — v10
description: Full documentation for config.yml schema v10 — defaults, per-key explanations, and a downloadable config file.
---

![DiscordLogger](/assets/DiscordLogger-Banner.webp "DiscordLogger")

# config.yml Docs — v10

**_Supported Plugin Versions:_ v2.2.0 and newer**

<div style="margin:1rem 0 1.25rem;">
  <a class="btn" href="/assets/configs/v10/config.yml" download>
    Download v10 config.yml
  </a>
</div>

> The docs below explain **every key** in v10.

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
Controls whether logs are sent as **Discord embeds** (recommended) or as plain text, and the embed **author** label.

- `embeds.enabled` — `true` to send rich embeds (defaults to **true** in the v10 shipped file).
- `embeds.author` — Small label at the top of embeds (default: **"Server Logs"**).

> **Changed in v10:** `embeds.colors` no longer exists. Each event's colour now sits
> directly under that event, beside its toggle — see [`log`](#log) below. Upgrading from
> v9 moves your existing colours across automatically.

```yaml
embeds:
  enabled: true
  author: "Server Logs"
```

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
Each event is a section with two keys: **`enabled`** (whether to log it) and
**`color`** (the embed colour for that event). v10 supports:

- `log.player.*` — `join`, `quit`, `chat`, `command`, `death`, `advancement`, `teleport`, `gamemode`
- `log.server.*` — `command`, `start`, `stop`, `explosion`
- `log.moderation.*` — `ban`, `unban`, `kick`, `op`, `deop`, `whitelist_toggle`, `whitelist_edit`

**Example (structure):**
```yaml
log:
  player:
    join:
      enabled: true
      color: "#57F287"
    quit:
      enabled: false      # this event won't be logged
      color: "#ED4245"
```

Some events carry extra sub-options alongside `enabled` and `color`:

| Key | Default | What it does |
|---|---|---|
| `log.player.death.show_coords` | `false` | Appends where the player died, as `x, y, z in world`. |

> **`show_coords` is off by default on purpose.** A death message with coordinates tells
> everyone who can read the channel exactly where the body — and the inventory it
> dropped — is. That is useful on a private server and a griefing tool on a public one,
> so it is opt-in rather than something you discover after the fact.

`color` is read whether or not the event is enabled, so turning something off and
back on keeps the colour you chose. Colours are hex, with or without the leading `#`;
an unreadable value falls back to the built-in default rather than failing to load.

> **Upgrading from v9:** every `log.<group>.<event>: true` becomes
> `log.<group>.<event>.enabled: true`, and each colour moves from `embeds.colors.*`
> to its event's `color`. The plugin does this automatically on first start and
> keeps your previous file as `config.old.yml`.

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

The defaults shipped with v10. Each is set under its own event's `color` key:

- **Player** — `join` `#57F287` • `quit` `#ED4245` • `chat` `#5865F2` • `command` `#FEE75C` • `death` `#ED4245` • `advancement` `#2ECC71` • `teleport` `#3498DB` • `gamemode` `#9B59B6`
- **Server** — `command` `#EB459E` • `start` `#43B581` • `stop` `#ED4245` • `explosion` `#E74C3C`
- **Moderation** — `ban`/`unban`/`kick`/`op`/`deop` `#FF0000` • `whitelist_toggle` `#1ABC9C` • `whitelist_edit` `#16A085`

---

## Full config.yml (v10)

> This is the exact file that ships with v10. Download above, or copy the block below.

```yaml
####################################################################################################################################
#                                                                                                                                  #
#    /$$$$$$$  /$$                                               /$$ /$$                                                           #
#   | $$__  $$|__/                                              | $$| $$                                                           #
#   | $$  \ $$ /$$  /$$$$$$$  /$$$$$$$  /$$$$$$   /$$$$$$   /$$$$$$$| $$        /$$$$$$   /$$$$$$   /$$$$$$   /$$$$$$   /$$$$$$    #
#   | $$  | $$| $$ /$$_____/ /$$_____/ /$$__  $$ /$$__  $$ /$$__  $$| $$       /$$__  $$ /$$__  $$ /$$__  $$ /$$__  $$ /$$__  $$   #
#   | $$  | $$| $$|  $$$$$$ | $$      | $$  \ $$| $$  \__/| $$  | $$| $$      | $$  \ $$| $$  \ $$| $$  \ $$| $$$$$$$$| $$  \__/   #
#   | $$  | $$| $$ \____  $$| $$      | $$  | $$| $$      | $$  | $$| $$      | $$  | $$| $$  | $$| $$  | $$| $$_____/| $$         #
#   | $$$$$$$/| $$ /$$$$$$$/|  $$$$$$$|  $$$$$$/| $$      |  $$$$$$$| $$$$$$$$|  $$$$$$/|  $$$$$$$|  $$$$$$$|  $$$$$$$| $$         #
#   |_______/ |__/|_______/  \_______/ \______/ |__/       \_______/|________/ \______/  \____  $$ \____  $$ \_______/|__/         #
#                                                                                     /$$  \ $$ /$$  \ $$                          #
#                                                                                    |  $$$$$$/|  $$$$$$/                          #
#                                                                                     \______/  \______/                           #
#                                                                                                                                  #
####################################################################################################################################

#######################################################################################
#                                                                                     #
#    _____              __ _         _____                           _                #
#   /  __ \            / _(_)       |  __ \                         | |               #
#   | /  \/ ___  _ __ | |_ _  __ _  | |  \/ ___ _ __   ___ _ __ __ _| |_ ___  _ __    #
#   | |    / _ \| '_ \|  _| |/ _` | | | __ / _ \ '_ \ / _ \ '__/ _` | __/ _ \| '__|   #
#   | \__/\ (_) | | | | | | | (_| | | |_\ \  __/ | | |  __/ | | (_| | || (_) | |      #
#    \____/\___/|_| |_|_| |_|\__, |  \____/\___|_| |_|\___|_|  \__,_|\__\___/|_|      #
#                             __/ |                                                   #
#                            |___/                                                    #
#                                                                                     #
#######################################################################################

# If you don't feel like configuring yourself, try out our config generator, simply select your plugin version and configure easily
# https://discordlogger.godtiergamers.xyz/generator/

#############################
# D O C U M E N T A T I O N #
#############################

# Documentation for this config can be found at https://discordlogger.godtiergamers.xyz/config/v10/

###################
# WEBHOOK OPTIONS #
###################

webhook:
  url: "" # Discord webhook URL goes here, plugin will not function until present

##################
# FORMAT OPTIONS #
##################

format:
  # ONLY USED FOR PLAIN TEXT MESSAGES (EMBEDS DISABLED)
  # Usage (case-sensitive): HH=hours, mm=minutes, ss=seconds, dd=day, MM=month, yyyy=year
  time: "[HH:mm:ss, dd:MM:yyyy]"
  # Only used for plain text, for embeds edit author name
  name: ""
  # Show nicknames (if set) as "Nickname (RealName)" in all player-related logs
  nicknames: true

#################
# EMBED OPTIONS #
#################

embeds:
  enabled: true
  author: "Server Logs" # Can be modified for proxy servers (e.g. Survival, Creative)

####################################################################################
#                                                                                  #
#     _                      _                ___         _    _                   #
#    | |    ___  __ _  __ _ (_) _ _   __ _   / _ \  _ __ | |_ (_) ___  _ _   ___   #
#    | |__ / _ \/ _` |/ _` || || ' \ / _` | | (_) || '_ \|  _|| |/ _ \| ' \ (_-<   #
#    |____|\___/\__, |\__, ||_||_||_|\__, |  \___/ | .__/ \__||_|\___/|_||_|/__/   #
#               |___/ |___/          |___/         |_|                             #
#                                                                                  #
####################################################################################

log:
  player:
    join: # Player Join
      enabled: true
      color: "#57F287" # green
    quit: # Player Quit
      enabled: true
      color: "#ED4245" # red
    chat: # Player Chat
      enabled: true
      color: "#5865F2" # blurple
    command: # Commands executed by a player in-game
      enabled: true
      color: "#FEE75C" # yellow
    death: # Player Death (with death message)
      enabled: true
      color: "#ED4245" # red
      show_coords: false # Adds where the player died. Anyone who can see the channel can find the body
    advancement: # Logs when a player gets an advancement
      enabled: true
      color: "#2ECC71" # green
    teleport: # Logs when a player teleports
      enabled: true
      color: "#3498DB" # blue
    gamemode: # Logs when a players gamemode changes
      enabled: true
      color: "#9B59B6" # purple

  server:
    command: # Commands executed via the server console/terminal
      enabled: true
      color: "#EB459E" # pink
    start: # Logged when the plugin/server starts
      enabled: true
      color: "#43B581" # green
    stop: # Logged on /stop / clean shutdown
      enabled: true
      color: "#ED4245" # red
    explosion: # Log when an explosion occurs
      enabled: true
      color: "#E74C3C" # red

  moderation:
    ban: # Logs when a player has been banned
      enabled: true
      color: "#FF0000" # red
    unban: # Logs when a player has been unbanned
      enabled: true
      color: "#FF0000" # red
    kick: # Logs when a player has been kicked
      enabled: true
      color: "#FF0000" # red
    op: # Logs when a player is granted op premissions
      enabled: true
      color: "#FF0000" # red
    deop: # Logs when a players op permissions are revoked
      enabled: true
      color: "#FF0000" # red
    whitelist_toggle: # Logs when the whitelist is enabled/disabled
      enabled: true
      color: "#1ABC9C" # teal
    whitelist_edit: # Logs when players are added/removed from the whitelist
      enabled: true
      color: "#16A085" # dark teal

# CONFIG VERSION V10, DOWNLOADED FROM WEBSITE
```
