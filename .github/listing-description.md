<!--
Shared listing copy for Modrinth, Hangar and CurseForge.

All three render Markdown, so this is written once and pasted into each. Keep it
that way: three descriptions that drifted apart is how a listing ends up claiming
a version range the plugin stopped supporting two releases ago.

NOT for SpigotMC. That listing is BBCode in a rich-text editor and needs its own
copy — and Spigot has no upload API, so it is a manual update either way.

No inline images: each platform has its own gallery, and uploading there beats
hotlinking this repo's assets into three descriptions that then depend on one domain
staying up.
-->

# DiscordLogger

Posts what happens on your Minecraft server to a Discord channel, as clean embeds
or plain text. One webhook URL, no bot to host, no OAuth, no dashboard.

**Runs on CraftBukkit, Spigot, Paper and forks, from Minecraft 1.8 through 26.2 — one JAR for all of them.**

---

## How it reads

Rich embeds with per-event colours, player heads and a configurable author line — or
plain Markdown text, if you would rather your channel stayed quiet.

Events with detail get their own fields. A death carries the cause, built from
server-side damage context so it reads the same for Java and Bedrock players, and
optionally where it happened. A ban carries who issued it, why, and for how long.

---

## What it logs

**Players** — join, quit, chat, commands, deaths, advancements, teleports, gamemode changes.

**Server** — console commands, start, stop, explosions.

**Moderation** — bans, unbans, kicks, op, deop, whitelist changes.

**Anything else** — build your own events from any command on the server. Essentials
homes, LuckPerms rank changes, shop purchases: if a command triggers it, you can log
it, with its own title, colour and webhook.

Every event above is an independent toggle with its own colour, and can be routed to
its own webhook — so moderation can go to a staff-only channel while chat goes to a
public one.

---

## Why you might pick this one

**It checks that things actually happened.** A ban is logged after the ban list
changes, not when someone types `/ban`. A command that failed, or was cancelled by
another plugin, does not produce a message claiming it worked.

**It knows what not to log.** `/login`, `/register` and `/msg` are filtered by
default, because logging them publishes passwords and private messages to your
channel. Fifteen filters cover the rest: ignore players, worlds, commands, death
causes, explosion sources, short chat, short teleports, and more.

**Every string is yours.** `lang.yml` holds every message the plugin sends. Reword
it, translate it, or strip it back — including all thirty death causes.

**It respects vanish.** Staff hidden by EssentialsX, SuperVanish, PremiumVanish or
CMI stay hidden. Moderation is deliberately exempt: hiding a ban because the person
was vanished would gut the audit trail exactly when it matters.

**Bedrock players are handled.** Works with Geyser and Floodgate, and joins from
Bedrock can be flagged as such.

---

## Setup

1. Drop the JAR in `plugins/` and start the server.
2. In Discord: **Channel Settings → Integrations → Webhooks → New Webhook**, then copy the URL.
3. Run `/discordlogger webhook <url>` in the console, or paste it into `config.yml`.

That is the whole setup. There is also a
[config generator](https://discordlogger.godtiergamers.xyz/generator/) that builds a
complete `config.yml` in your browser if you would rather click than edit YAML.

**Your webhook URL is a credential.** Anyone holding it can post to that channel as
your server. The plugin never echoes it back, redacts it from command logging, and
keeps it out of tab-completion.

---

## Requirements

| | |
|---|---|
| **Server** | CraftBukkit, Spigot, Paper or a fork — **1.8 to 26.2** |
| **Java** | 8 or newer |
| **Discord** | A webhook URL. No bot, no token, no hosting |

A few features need a version that has the underlying feature: advancements are
logged as achievements before 1.12, and block explosions need 1.8.3. The plugin
checks at startup and says which ones your server cannot provide, rather than
failing quietly.

---

## Privacy

Anonymous usage data is sent to [bStats](https://bstats.org/plugin/bukkit/DiscordLogger/33026):
server software, versions, which events are enabled, and whether sends are
succeeding. **Never** webhook URLs, player names, UUIDs, IP addresses, message
content, coordinates or world names. Counts are reported as ranges rather than exact
numbers, so a chart cannot become a fingerprint. It can be switched off in
`plugins/bStats/config.yml`.

---

## Links

- **[Documentation](https://discordlogger.godtiergamers.xyz/)** — setup, every config key, guides
- **[Config generator](https://discordlogger.godtiergamers.xyz/generator/)** — build a config in your browser
- **[GitHub](https://github.com/GodTierGamers/DiscordLogger)** — source, issues, releases
- **[Report a bug](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)**

Free and open source under the MIT licence.
