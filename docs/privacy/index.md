---
layout: default
title: "Privacy Policy"
description: What DiscordLogger and this website do with data — what is collected, what never leaves your own server, and what passes through the Discord webhook creator.
---

# Privacy Policy

**Last updated: 5 November 2026**

This policy covers the website at `discordlogger.godtiergamers.xyz`, the config
generator on it, the webhook creator that generator can use, and the DiscordLogger
plugin itself. The site and plugin are run by **God Tier Gamers**.

## The short version

Most of what this project does happens somewhere we never see. The plugin runs on
your server and posts to your Discord channel; neither of those passes through us.
The config generator builds your files in your browser. There are no accounts, and
nothing asks you who you are.

Three things are worth reading properly, because they are the exceptions: website
analytics, the webhook creator, and the anonymous statistics the plugin reports.

## The website

The site is static, hosted on **GitHub Pages**, with DNS through **Cloudflare**.
Both receive your IP address and request details simply because that is how a web
request works, and both keep their own logs under their own policies. We have no
access to those logs and no way to switch them off.

### Analytics

<div data-dl-consent class="dl-consent-ctl"></div>

We use **Google Analytics 4** to see which pages get read and which guides are
worth writing more of. It is **off until you allow it**. If you decline, or close
the banner without answering, no Google script is loaded and no analytics cookies
are set — the site behaves exactly as it did before analytics existed.

If you allow it, Google sets cookies (`_ga` and similar) to recognise your browser
across pages and visits, and receives your IP address, pages viewed, approximate
location, and browser and device details. Google is the controller of that data;
their [privacy policy](https://policies.google.com/privacy) governs it.

**How long Google keeps it.** Event-level records — individual page views — are
deleted **14 months** after they are collected. The identifier that ties a browser's
visits together is kept for 14 months too, but that clock restarts every time the
same browser comes back: for someone who reads these docs regularly it is 14 months
from their *last* visit, not their first. Fourteen months is the longest Google
offers without a paid contract.

You can change your mind at any time using the control above. Turning it off
deletes the analytics cookies this site can reach and stops the tag loading on
future visits. It cannot reach back and delete what Google already received — no
site can promise that, so we are not going to.

### What the site stores in your browser

Nothing here is sent anywhere. It all stays in your own browser and can be cleared
by clearing site data.

| Key | Kind | What it is for |
|---|---|---|
| `dl-analytics-consent` | localStorage | Whether you allowed analytics, so you are not asked twice |
| `dl-show-beta` | localStorage | Whether you opted into seeing nightly builds |
| `dl-theme` | localStorage | Light or dark, if you have chosen |
| `dl-releases-cache-v1` | sessionStorage | The release list, so every page does not re-fetch it |

Pages that show version numbers fetch the release list from the **GitHub API**,
which means GitHub sees a request from your browser. No identifiers are sent with it.

## The config generator

The generator runs in your browser. The `config.yml` and `lang.yml` it produces are
assembled locally and never uploaded.

**If you paste a webhook URL in**, it stays in the page. The only place it goes is
Discord, when you send the test message — straight from your browser to Discord,
not through us.

**If you use "Connect to Discord"**, it necessarily works differently, and this is
the part most worth understanding. Discord hands your browser a one-time code, and
turning that code into a webhook URL requires the application's client secret —
which cannot be shipped to a browser without publishing it to everyone. So the code
goes to a small **Cloudflare Worker** we run, which exchanges it and hands the
webhook URL back.

So your webhook URL passes through that Worker. There is no version of this flow
where it does not. What *is* avoidable is storing it, and the Worker has nowhere to:

- It declares **no database, key-value store, bucket, queue or any other storage**.
  A [CI check](https://github.com/GodTierGamers/DiscordLogger/blob/main/scripts/check-worker-stateless.py)
  fails the build if anyone ever adds one, because this page makes that promise in public.
- Only the `webhook.url` field of Discord's response is read, **by name**. The access
  and refresh tokens arrive in the same payload and are never touched.
- [`/version`](https://oauth.godtiergamers.xyz/version) reports the public commit the
  Worker is running, so you can check the deployment against
  [the source](https://github.com/GodTierGamers/DiscordLogger/tree/main/worker).

We are not going to write "we log nothing", because it would not be true: Cloudflare
records request metadata whatever our code does. The accurate claim is narrower —
**nothing is stored by this project** — and pasting the URL yourself skips the Worker
entirely.

## The plugin

The plugin runs on your server. Your logs go from your server to your Discord channel.
**We never receive them**, and there is no mechanism by which we could.

What the plugin does send, and to whom:

### Anonymous statistics (bStats)

The plugin reports anonymous statistics to [bStats](https://bstats.org/plugin/bukkit/DiscordLogger/33026).
They describe your *setup*, never your players: server and Java version, online or
offline mode, whether you run behind a proxy, which companion plugins are installed,
which events and filters you have changed from the defaults, whether you edited
`lang.yml`, and where your config came from. Counts are reported as ranges rather
than exact numbers.

**Never collected:** webhook URLs, player names, UUIDs, IP addresses, message
content, coordinates, or world names.

Turn it off in `plugins/bStats/config.yml`, which covers every bStats plugin at once.

### Update checks

On startup the plugin asks the **GitHub API** whether a newer release exists. GitHub
sees a request from your server's IP. Nothing about your server is sent.

### Player head images

Embeds show a player's head, by putting a URL containing that player's **UUID** into
the embed. Discord fetches it through its own image proxy from `mc-heads.net`. So a
player's UUID is visible to Discord and to mc-heads.net. UUIDs are public Minecraft
identifiers, but they do identify an account, which is why it is named here.

### Data the plugin writes on your server

Some features keep files on your own disk, which only you can read:

- **`explosions.csv`** records each explosion's time, world, coordinates and the
  **UUIDs of nearby players**, so `/discordlogger lookup` can answer "was anything
  blown up here". It is pruned on the schedule `expiry_time` sets.
- **`config.yml`** holds your webhook URL, which is a credential — anyone with it can
  post to your channel.

### If you run a server, this part is yours

Logging joins, chat, commands and deaths means recording things about the people on
your server. **For that data, you are the one responsible** — not us. Telling your
players what you log, and complying with whatever law applies to you, is your call to
make. The plugin gives you the tools to narrow it: `filters.ignored_players`,
`filters.ignored_commands`, `filters.log_chat` and the rest, documented in the
[configuration reference](/config/). Some commands are filtered by default precisely
because logging them verbatim would publish passwords and private messages.

## Who else is involved

| Who | Why | Their policy |
|---|---|---|
| GitHub | Hosts the site and the releases the plugin checks | [Policy](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) |
| Cloudflare | DNS, and runs the webhook-creation Worker | [Policy](https://www.cloudflare.com/privacypolicy/) |
| Discord | Where your logs go, and who authorises webhook creation | [Policy](https://discord.com/privacy) |
| Google | Analytics, only if you allow it | [Policy](https://policies.google.com/privacy) |
| bStats | Anonymous plugin statistics | [Policy](https://bstats.org/privacy-policy) |
| mc-heads.net | Renders player head images | — |

## Your rights

We are based in Australia and this policy is written to the **Australian Privacy
Principles**. If you are in the EU or UK, the GDPR gives you rights over personal
data — access, correction, erasure, and withdrawal of consent — and we will honour
those requests regardless of where you are.

In practice there is very little to request: we hold no accounts and no database. The
realistic subjects of a request are the analytics data held by Google if you allowed
it, and the provider logs described above. Ask us and we will tell you honestly what
exists and help you get at it.

## Changes

If what the project does changes, this page changes with it — that is the point of
describing mechanisms rather than making vague promises. The date at the top is the
last change. Material changes will be noted in the release notes.

## Contact

- **Email:** [contact@godtiergamers.xyz](mailto:contact@godtiergamers.xyz) — for
  privacy requests or anything you would rather not raise in public
- **GitHub:** [open an issue](https://github.com/GodTierGamers/DiscordLogger/issues/new/choose)
  for general questions

---

See also the [Terms of Use](/terms/).
