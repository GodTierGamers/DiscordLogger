---
layout: default
title: Config Docs — v10
description: Full documentation for DiscordLogger's config.yml and lang.yml at schema v10 — every key explained, with downloadable files.
---

![DiscordLogger](/assets/DiscordLogger-Banner.webp "DiscordLogger")

# config.yml Docs — v10

**_Supported Plugin Versions:_ <span data-dl-schema-versions="v10">…</span>**

<div style="margin:1rem 0 .5rem;">
  <a class="btn" href="/assets/configs/v10/config.yml" download>
    Download v10 config.yml
  </a>
</div>

<div style="margin:0 0 1.25rem;">
  <a class="btn" href="/assets/configs/v10/lang.yml" download>
    Download v10 lang.yml
  </a>
</div>

> The docs below explain **every key** in v10.

---

## Top-level keys

### `webhook`
**Required.** Where logs are sent, unless an individual event overrides it.

```yaml
webhook:
  url: "https://discord.com/api/webhooks/1234567890/AbCdEf-gH1jK_lMnOpQrStUvWxYz"
```

**Getting the URL:** in Discord, right-click the channel → *Edit Channel* →
*Integrations* → *Webhooks* → *New Webhook* → *Copy Webhook URL*. You need
**Manage Webhooks** in that channel.

Or set it in game without touching the file at all:

```
/discordlogger webhook https://discord.com/api/webhooks/1234567890/AbCdEf…
```

That writes it here and reloads. The URL is never echoed back, and command logging
redacts it, so running it does not publish the URL to the channel you are moving away
from.

**If it is empty or malformed**, the plugin still starts and still logs to console — it
just says so on startup and posts nothing to Discord. It is not a crash, so check the
console if messages are not arriving.

> Treat the URL like a password. Anyone who has it can post to that channel as your
> server.

---

### `embeds`
Whether logs are sent as rich **embeds** or as plain text lines.

```yaml
embeds:
  enabled: true
  author: "Server Logs"     # small label at the top of every embed
```

With `enabled: true` a death arrives as a coloured embed titled **Player Death**, with
a **Cause of Death** field and the player's head as the thumbnail.

With `enabled: false` the same event is one line of text:

```
`[14:32:07, 31:07:2026]` - **Player Death**: Lachlan fell from a high place
```

Plain text is worth choosing if you pipe the channel somewhere else, or find embeds
noisy at volume. Everything still gets logged either way — only the presentation
changes.

`embeds.author` is the small label above the title. On a network it is worth setting
per server so you can tell them apart:

```yaml
embeds:
  author: "Survival"        # or "Creative", "Lobby", …
```

> **Changed in v10:** `embeds.colors` no longer exists. Each event's colour now sits
> under that event, beside its toggle — see [`log`](#log). Upgrading from v9 moves your
> existing colours across automatically.

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

### `config-version`
Identifies which config format the file uses, so the plugin can upgrade it correctly.

- **Do not change it.** Editing the number does not convert anything; it only misleads
  the upgrader into running the wrong migration, or none.
- **Deleting it is survivable.** If it is missing the plugin works the schema out from
  which keys the file contains, and says so in console. It replaces a comment on the
  last line, which was too easy to lose to a stray edit or an editor that strips
  comments.
- If the number and the file's actual keys disagree, the **keys win** — they are what
  the plugin reads — and the mismatch is logged. The usual cause is an older config
  pasted over a newer one.

---

### `filters`
Applied on top of the event toggles: an event that is **enabled** can still be skipped
if it matches a filter. This is how you exclude specific things without turning a whole
category off.

Each list is independent — use one and leave the rest empty if you like.

#### `filters.ignored_commands`
Commands that are never logged, whoever runs them.

The match is on the **command word only**, so arguments and any plugin prefix are
ignored. A single entry of `msg` blocks all three of these:

```
/msg Steve hello
/MSG Steve hello
/essentials:msg Steve hello
```

Write entries **without** the leading slash:

```yaml
filters:
  ignored_commands:
    - login
    - register
    - msg
    - vanish
    - co          # CoreProtect inspect spam
```

> **This list ships non-empty, on purpose.** Command logging posts the line exactly as
> typed, so `/login hunter2` would publish that password to Discord, and `/msg` would
> publish private messages. The defaults cover the usual auth and messaging commands.
> Removing them is a decision, not a tidy-up.

#### `filters.ignored_worlds`
Worlds whose events are never logged.

Use the world's **folder name**, which is what the server calls it internally — not a
display name from a plugin. On a default server:

| World | Name to put here |
|---|---|
| Overworld | `world` |
| Nether | `world_nether` |
| The End | `world_the_end` |

So to stop logging anything that happens in the Nether:

```yaml
filters:
  ignored_worlds:
    - world_nether
```

**If your server renames or adds worlds** — Multiverse, a custom `level-name`, minigame
worlds — those names will differ. Two ways to find the real one:

- Look in your server folder. Each world is a directory containing `level.dat`; the
  directory name is the world name.
- In game, type `/execute in ` and let it tab-complete — it lists every loaded world by
  its actual name.

Matching is case-insensitive, so `World_Nether` works as well as `world_nether`.

```yaml
# A creative plot world, a minigame world, and the End
filters:
  ignored_worlds:
    - creative
    - bedwars_arena
    - world_the_end
```

#### `filters.ignored_players`
Players whose **own activity** is never logged — their joins, quits, chat, commands and
deaths.

Accepts **names or UUIDs, mixed freely** in the same list. A UUID keeps working after a
name change, so prefer it for anything long-lived:

```yaml
filters:
  ignored_players:
    - AdminAlt                                  # by name
    - ShopBot
    - 069a79f4-44e9-4726-a5be-fca90e38aaf5      # by UUID
```

> **Moderation events are not filtered by player.** A ban or a kick is a record of
> *staff* action, not that player's own activity, so it is still logged even for an
> ignored account. Otherwise you could silence a bot and then never see it being
> banned — which is exactly the entry an audit trail exists for.

#### `filters.exempt_permission`
Any player holding this permission is never logged. Empty — the default — disables the
check entirely.

Useful when the set of people to exclude changes often, such as staff or anyone
currently vanished, because you then manage it in your permissions plugin rather than
by editing this file:

```yaml
filters:
  exempt_permission: "discordlogger.exempt"
```

Grant it however your permissions plugin does. With LuckPerms:

```
/lp group staff permission set discordlogger.exempt true
```

Leave it as `""` unless you want this behaviour — a node that something else happens to
grant would quietly suppress logging for those players.

#### `filters.ignored_chat_containing`
Chat messages containing any of these are skipped. Case-insensitive **substring** match
— not whole words, and not a regular expression:

```yaml
filters:
  ignored_chat_containing:
    - "[afk]"
    - discord.gg        # invite links
```

Because it matches inside words, keep entries distinctive: an entry of `ass` would also
skip "password" and "grass".

---

---

### `log`
Every event is a section with the same three keys, plus sub-options on a few:

| Key | What it does |
|---|---|
| `enabled` | Whether to log this event at all. |
| `color` | The embed's colour bar, as hex. |
| `webhook` | Send **just this event** to a different channel. Empty = the main `webhook.url`. |

The events available:

- `log.player.*` — `join`, `quit`, `chat`, `command`, `death`, `advancement`, `teleport`, `gamemode`
- `log.server.*` — `command`, `start`, `stop`, `explosion`
- `log.moderation.*` — `ban`, `unban`, `kick`, `op`, `deop`, `whitelist_toggle`, `whitelist_edit`

**A minimal example** — log joins, don't log quits:

```yaml
log:
  player:
    join:
      enabled: true
      color: "#57F287"
      webhook: ""
    quit:
      enabled: false           # this event is never sent
      color: "#ED4245"
      webhook: ""
```

#### `color`
Hex, with or without the leading `#`. Both of these are the same green:

```yaml
      color: "#57F287"
      color: "57F287"
```

An unreadable value falls back to the built-in default rather than failing to load, and
`color` is read whether or not the event is enabled — so turning something off and back
on keeps the colour you chose.

#### `webhook` — sending one event elsewhere
The common case is a private staff channel for moderation while everything else goes to
a public one:

```yaml
webhook:
  url: "https://discord.com/api/webhooks/111/PUBLIC"    # everything, by default

log:
  moderation:
    ban:
      enabled: true
      color: "#FF0000"
      webhook: "https://discord.com/api/webhooks/222/STAFF"   # …except this
    kick:
      enabled: true
      color: "#FF0000"
      webhook: "https://discord.com/api/webhooks/222/STAFF"   # …and this
```

Leave it as `""` and the event uses the main webhook, which is what almost every server
wants. A value that isn't a valid Discord webhook URL is **ignored with a warning in
console** and that event falls back to the main webhook, rather than silently going
nowhere.

Each destination is paced independently, because Discord's rate limits are per webhook
— a busy chat channel will not delay your moderation log.

#### Sub-options
A few events carry an extra key beside `enabled`, `color` and `webhook`:

| Key | Default | What it does |
|---|---|---|
| `log.player.death.show_coords` | `false` | Appends where the player died, as `x, y, z in world`. |
| `log.player.join.show_platform` | `true` | Adds a **Platform: Bedrock** field when the joining player came from Bedrock. |

```yaml
log:
  player:
    death:
      enabled: true
      color: "#ED4245"
      webhook: ""
      show_coords: true        # "Lachlan died … Coords: 128, 71, -344 in world"
    join:
      enabled: true
      color: "#57F287"
      webhook: ""
      show_platform: true
```

> **`show_coords` is off by default on purpose.** A death message with coordinates tells
> everyone who can read the channel exactly where the body — and the inventory it
> dropped — is. That is useful on a private server and a griefing tool on a public one,
> so it is opt-in rather than something you discover after the fact.

> **`show_platform` only ever flags Bedrock.** Nothing can prove a player *is* Java —
> with Geyser standalone, Bedrock players authenticate as ordinary Java accounts and are
> indistinguishable even to Floodgate. So the field appears only when something
> positively indicates Bedrock, and its absence means "no indication", not "definitely
> Java". On a server without Geyser it never appears at all.

> **Upgrading from v9:** every `log.<group>.<event>: true` becomes
> `log.<group>.<event>.enabled: true`, and each colour moves from `embeds.colors.*` to
> its event's `color`. The plugin does this automatically on first start and keeps your
> previous file as `config.old.yml`.

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

## lang.yml — every message the plugin sends

`lang.yml` sits beside `config.yml` and carries the **same config version**, so the two
are upgraded together. It holds every message shown in game or posted to Discord.

Every message the plugin shows lives in `plugins/DiscordLogger/lang.yml`. Change the
wording, the colours, or the language entirely — no code, no rebuild.

The file is created on first start. Edit it, then:

```
/discordlogger reload
```

No restart needed.

---

#### The two sections are not interchangeable

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

#### MiniMessage, for the `chat` section

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

#### Placeholders

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

#### Worked example: translating to French

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

#### Death causes

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

#### If you break something

| What you did | What happens |
|---|---|
| Deleted a message | Falls back to the English inside the jar. Nothing breaks. |
| Deleted a key entirely | You see the key name, e.g. `chat.reload-ok`. It names exactly what to fix. |
| Deleted the whole file | Written again on the next start. |
| Broke the YAML | The plugin logs the parse error and falls back to English. Check indentation — **spaces, never tabs**. |

Because unedited messages fall back to the shipped English, you can safely delete
everything you are not changing and keep a much shorter file.

---

#### What is *not* in here

**Console messages.** Those stay in English on purpose: a translated error is one nobody
can search for, and support threads depend on everyone seeing the same text.

**Event names** like *Player Death* — those are the embed titles and are currently set
in code.

---

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

#########################
# D O   N O T   E D I T #
#########################

# Set automatically
config-version: 10

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

###################################################
#  ______ _____ _   _______ ______ _____   _____  #
# |  ____|_   _| | |__   __|  ____|  __ \ / ____| #
# | |__    | | | |    | |  | |__  | |__) | (___   #
# |  __|   | | | |    | |  |  __| |  _  / \___ \  #
# | |     _| |_| |____| |  | |____| | \ \ ____) | #
# |_|    |_____|______|_|  |______|_|  \_\_____/  #
#                                                 #
###################################################

# Filters apply on top of the toggles below: an event that is enabled can still be
# skipped if it matches something here.

filters:
  # Never log these commands, whoever runs them. Matched on the command word, so
  # arguments and a plugin prefix are ignored -- "/essentials:msg hi" matches "msg".
  # The defaults exist because these leak: login commands carry passwords in plain
  # text, and private messages are private.
  ignored_commands:
    - login
    - register
    - changepassword
    - unregister
    - msg
    - tell
    - whisper
    - w
    - r
    - reply

  # Never log anything from these players. Accepts names or UUIDs, mixed freely.
  ignored_players: []

  # Players with this permission are never logged -- useful for staff alts, or a bot
  # account whose activity would drown everything else. Empty disables the check.
  exempt_permission: ""

  # Never log events that happen in these worlds.
  ignored_worlds: []

  # Skip chat messages containing any of these (case-insensitive).
  ignored_chat_containing: []

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
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above
      show_platform: true # Flags players who joined from Bedrock (needs Geyser + Floodgate)

    quit: # Player Quit
      enabled: true
      color: "#ED4245" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    chat: # Player Chat
      enabled: true
      color: "#5865F2" # blurple
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    command: # Commands executed by a player in-game
      enabled: true
      color: "#FEE75C" # yellow
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    death: # Player Death (with death message)
      enabled: true
      color: "#ED4245" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above
      show_coords: false # Adds where the player died. Anyone who can see the channel can find the body

    advancement: # Logs when a player gets an advancement
      enabled: true
      color: "#2ECC71" # green
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    teleport: # Logs when a player teleports
      enabled: true
      color: "#3498DB" # blue
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    gamemode: # Logs when a players gamemode changes
      enabled: true
      color: "#9B59B6" # purple
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

  server:
    command: # Commands executed via the server console/terminal
      enabled: true
      color: "#EB459E" # pink
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    start: # Logged when the plugin/server starts
      enabled: true
      color: "#43B581" # green
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    stop: # Logged on /stop / clean shutdown
      enabled: true
      color: "#ED4245" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    explosion: # Log when an explosion occurs
      enabled: true
      color: "#E74C3C" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

  moderation:
    ban: # Logs when a player has been banned
      enabled: true
      color: "#FF0000" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    unban: # Logs when a player has been unbanned
      enabled: true
      color: "#FF0000" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    kick: # Logs when a player has been kicked
      enabled: true
      color: "#FF0000" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    op: # Logs when a player is granted op premissions
      enabled: true
      color: "#FF0000" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    deop: # Logs when a players op permissions are revoked
      enabled: true
      color: "#FF0000" # red
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    whitelist_toggle: # Logs when the whitelist is enabled/disabled
      enabled: true
      color: "#1ABC9C" # teal
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

    whitelist_edit: # Logs when players are added/removed from the whitelist
      enabled: true
      color: "#16A085" # dark teal
      webhook: "" # Send just this event elsewhere. Empty = use webhook.url above

# CONFIG VERSION V10, DOWNLOADED FROM WEBSITE
```

---

## Full lang.yml (v10)

> The exact file that ships with v10.

```yaml
{% include_relative lang.yml.txt %}
```
