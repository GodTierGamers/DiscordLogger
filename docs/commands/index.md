---
layout: default
title: "Commands and Permissions"
description: Every DiscordLogger command and the permission node that gates it — including who can look up player whereabouts in the explosion log.
---

# Commands and Permissions

Everything runs under `/discordlogger`, with the aliases `/dlogger` and `/dlog`.

Every subcommand has **its own permission node**, so you can hand out one without
handing out the rest — a moderator who should be able to check whether logging is
healthy does not also need to be able to rebuild your config.

All of them default to **op**.

| Command | What it does | Permission |
|---|---|---|
| `/discordlogger reload` | Reload `config.yml` and `lang.yml` | `discordlogger.reload` |
| `/discordlogger webhook <url>` | Set the webhook without editing YAML | `discordlogger.webhook` |
| `/discordlogger regen confirm` | Rebuild `config.yml` from this build's default | `discordlogger.regen` |
| `/discordlogger status` | Queue depth, webhook health, build channel | `discordlogger.status` |
| `/discordlogger test [event]` | Send a real message through an event's own webhook | `discordlogger.test` |
| `/discordlogger doctor` | Check the config for contradictions | `discordlogger.doctor` |
| `/discordlogger lookup …` | What exploded near a place | `discordlogger.lookup` |

## `lookup` is the one to think about

`/discordlogger lookup [x y z] [r:50] [t:7d] [type:tnt]` searches `explosions.csv`
and answers "was anything blown up here". It is the only command that reports on
**players**: each result lists who was nearby and how far away they were, resolved
from the stored UUIDs into current names.

That makes it a griefing tool and a whereabouts record at the same time. Anyone
holding `discordlogger.lookup` can ask where players were at a given time, near a
given place, for as long as `expiry_time` keeps the rows.

**Granting it is the decision.** It defaults to op, so on a normal server nobody has
it unless you have already trusted them with everything else. If you run with a
permissions plugin and hand out nodes individually, this is one to think about
rather than bundle.

To switch the record off entirely, set `log.server.explosion.history.enabled` to
`false` — no file is written and there is nothing to look up. The
[configuration reference](/config/) covers `expiry_time` and the rest.

## The root command is deliberately open

`/discordlogger` itself needs no permission. It used to require
`discordlogger.reload`, which locked anyone holding only `discordlogger.regen` out of
the command entirely — including the subcommand they *were* allowed to run.

Instead, the help listing and tab-completion filter themselves by what you hold. A
player with none of the permissions sees nothing, can complete nothing, and can run
nothing. The command existing is not a disclosure; its contents are, and those are
gated individually.

## One permission that is not a command

`filters.exempt_permission` names a node whose holders are **never logged** — useful
for staff whose own movements should stay out of the channel.

It ships **empty**, which means the feature is off and no node exists. You pick the
name yourself:

```yaml
filters:
  exempt_permission: "discordlogger.exempt"
```

Anything will do, as long as it is the node you actually grant. See the
[configuration reference](/config/) for the rest of the filters.
