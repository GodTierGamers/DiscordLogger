---
layout: default
title: "config.yml Generator — Build a Config Online"
description: Build a DiscordLogger config.yml and lang.yml in your browser — pick what gets logged, set your webhook, tune the filters, download the finished files.
---

# config.yml Generator

Pick your plugin version, test your webhook, then choose what to log, what to filter
out, and what every message says. You get both files the plugin uses —
`config.yml` and `lang.yml` — and the wizard itself runs entirely in your browser.

### Where your webhook URL goes

**If you paste the URL yourself**, it never leaves your browser except to Discord, when
you send the test message.

**If you use "Connect to Discord"**, it cannot work that way, and it is worth being
precise rather than reassuring about why. Discord's flow hands your browser a one-time
code, and turning that code into a webhook URL requires the application's client secret
— which cannot be shipped to a browser without publishing it. So the code goes to a
small [Cloudflare Worker](https://github.com/GodTierGamers/DiscordLogger/tree/main/worker),
which exchanges it with Discord and returns the URL to the page.

That means the URL *passes through* that Worker. There is no version of this flow where
it does not. What is avoidable is storage, and the Worker has none: its configuration
declares no database, no key-value store, no bucket and no queue, so it has nowhere to
put a webhook URL even by mistake — and
[a CI check](https://github.com/GodTierGamers/DiscordLogger/blob/main/scripts/check-worker-stateless.py)
fails the build if anybody ever adds one. The access and refresh tokens Discord returns
alongside the webhook are not read at all. `/version` reports the commit the Worker is
running, so you can check the deployment against the public source.

It would be easy to write "we log nothing" here, and it would not be true: Cloudflare
records request metadata whatever the code does. The accurate claim is narrower —
nothing is stored by this project, and the manual paste field is always there if you
would rather not authorize a third-party app at all.

<div id="cfg-gen" class="markdown-body"></div>

<!-- Loader: reads /assets/configs/registry.json, then loads the version-specific
     generator bundle from /assets/configs/<schema>/generator.js -->
<script defer src="{{ '/assets/js/generator.js?v=4' | relative_url }}"></script>
