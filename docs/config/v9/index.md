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

> The docs below explain **every key** in v9.

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

# If you don't feel like configuring yourself, try out our config generator, simply select the plugin version (2.1.5) and configure easily
# https://discordlogger.godtiergamers.xyz/generator/

#############################
# D O C U M E N T A T I O N #
#############################

# Documentation for this config can be found at https://discordlogger.godtiergamers.xyz/config/v9/

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

  # Per-category colors (hex). Keys are case-insensitive; spaces become underscores.
  colors:
    player:
      join:    "#57F287"  # green
      quit:    "#ED4245"  # red
      chat:    "#5865F2"  # blurple
      command: "#FEE75C"  # yellow
      death:   "#ED4245"  # red
      advancement: "#2ECC71" # green
      teleport: "#3498DB" # blue
      gamemode: "#9B59B6" # purple
    server:
      start:   "#43B581"  # green
      stop:    "#ED4245"  # red
      command: "#EB459E"  # pink
      explosion: "#E74C3C" # red
    moderation:
      ban:              "#FF0000"  # red
      unban:            "#FF0000"  # red
      kick:             "#FF0000"  # red
      op:               "#FF0000"  # red
      deop:             "#FF0000"  # red
      whitelist_toggle: "#1ABC9C"  # teal
      whitelist:        "#16A085"  # dark teal



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
    join: true # Player Join
    quit: true # Player Quit
    chat: true # Player Chat
    command: true # Commands executed by a player in-game
    death: true # Player Death (with death message)
    advancement: true # Logs when a player gets an advancement
    teleport: true # Logs when a player teleports
    gamemode: true # Logs when a players gamemode changes

  server:
    command: true # Commands executed via the server console/terminal
    start: true # Logged when the plugin/server starts
    stop: true # Logged on /stop / clean shutdown
    explosion: true # Log when an explosion occurs

  moderation:
    ban: true # Logs when a player has been banned
    unban: true # Logs when a player has been unbanned
    kick: true # Logs when a player has been kicked
    op: true # Logs when a player is granted op premissions
    deop: true # Logs when a players op permissions are revoked
    whitelist_toggle: true # Logs when the whitelist is enabled/disabled
    whitelist_edit: true # Logs when players are added/removed from the whitelist


# CONFIG VERSION V9, DOWNLOADED FROM WEBSITE
```

---

## Changelog (v9)
- New **nested colors** under `embeds.colors.{player|server|moderation}`.
- Split **server start/stop** into separate events with distinct colors.
- Added **teleport**, **gamemode**, **explosion (nearby players)**.
- Moderation logs only fire when the action **succeeds**.
