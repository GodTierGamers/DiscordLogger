---
layout: default
title: "Terms of Use"
description: The terms covering the DiscordLogger website, the config generator, the webhook creator and the plugin — including what you are responsible for as a server operator.
---

# Terms of Use

**Last updated: 5 November 2026**

These terms cover the website at `discordlogger.godtiergamers.xyz`, the config
generator, the webhook creator, and the DiscordLogger plugin. The project is run by
**God Tier Gamers**. Using any of them means these terms apply.

They are deliberately short. This is a free, open-source hobby project with no
accounts and no money changing hands, and terms longer than the thing they govern
help nobody.

## The plugin is MIT licensed, and the licence wins

DiscordLogger is released under the
[MIT License](https://github.com/GodTierGamers/DiscordLogger/blob/main/LICENSE). You
may use, modify and redistribute it on those terms, including commercially. Where
anything on this page appears to conflict with the MIT licence as it applies to the
software, **the licence governs**.

What follows adds to that for the *services* we run — this website, the generator and
the webhook creator — which the licence does not cover.

## No warranty

The website, the generator, the webhook creator and the plugin are provided **as is**,
without warranty of any kind. We do not promise the site will be available, that the
generator will produce a config suited to your server, that the webhook creator will
work, or that the plugin will log everything you expect.

This matters more than it usually does, for a specific reason: **the plugin writes to
your server and posts to your Discord channel**. A misconfigured filter can publish
something you would rather it had not. Read what you are turning on, and test on a
server you can afford to get wrong.

## Limitation of liability

To the maximum extent the law allows, we are not liable for any loss or damage
arising from use of the project — including lost data, disclosed information, server
downtime, or anything that follows from a config you generated here.

Australian Consumer Law gives you guarantees that cannot be excluded. Nothing here
tries to exclude them. Where liability cannot be excluded, it is limited to
re-supplying the relevant service, which for a free one means making it work again.

## If you run a server, this part is yours

Running the plugin means recording things about the people on your server — their
joins, chat, commands and deaths. **You are responsible for that**, not us:

- telling your players what you log, if that is expected of you;
- complying with whatever privacy law applies where you and they are;
- keeping your webhook URL to yourself, since anyone holding it can post to your
  channel;
- what you do with `explosions.csv`, which records player UUIDs and coordinates.

The plugin ships filters so you can narrow what is logged, and some commands are
excluded by default because logging them verbatim would publish passwords and private
messages. Those defaults are a starting point, not a compliance strategy.

## Acceptable use

Don't use the site or the webhook creator to:

- break Discord's [Terms of Service](https://discord.com/terms) or
  [Community Guidelines](https://discord.com/guidelines);
- create webhooks for channels you have no right to post in;
- attack, overload or probe the site or the Worker, or use them to attack anything else;
- log people in ways that are unlawful where you are.

We may block access if any of that happens. There is no account to suspend, so in
practice this means what it says: don't.

## The webhook creator

"Connect to Discord" authorises a Discord application to create a webhook in a channel
you choose. Discord's terms govern that authorisation. Each use creates a **new**
webhook, so running it twice leaves two in the channel — tidying those up is yours to
do, in Discord.

What the Worker does with the URL in transit is described in the
[Privacy Policy](/privacy/), and the
[code is public](https://github.com/GodTierGamers/DiscordLogger/tree/main/worker).

## Not affiliated with Discord, Mojang or Microsoft

DiscordLogger is an independent project. It is **not** made, endorsed, sponsored by or
affiliated with Discord Inc., Mojang Studios, Microsoft, or the Paper or Spigot
projects.

DISCORD and the Discord logo are trademarks of Discord Inc., used here only to
identify Discord as the service this plugin connects to. MINECRAFT is a trademark of
Mojang Studios.

## Third-party services

The project depends on services we do not control — GitHub, Cloudflare, Discord,
bStats and mc-heads.net. Their terms govern them, and if one of them changes or
disappears, parts of the project may stop working. The
[Privacy Policy](/privacy/) lists what each is used for.

## Changes

These terms change when the project does. The date at the top is the last change, and
material changes will be noted in the release notes. Continuing to use the project
after a change means the new terms apply.

## Governing law

These terms are governed by the laws of **Australia**. Any dispute goes to the courts
of Australia, without prejudice to consumer rights you hold where you live.

## Contact

- **Email:** [contact@godtiergamers.xyz](mailto:contact@godtiergamers.xyz)
- **GitHub:** [open an issue](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)

---

See also the [Privacy Policy](/privacy/).
