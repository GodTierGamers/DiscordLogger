---
layout: default
title: "config.yml Generator — Build a Config Online"
description: Build a DiscordLogger config.yml and lang.yml in your browser — pick what gets logged, set your webhook, tune the filters, download the finished files.
---

# config.yml Generator

Pick your plugin version, test your webhook, then choose what to log, what to filter
out, and what every message says. You get both files the plugin uses —
`config.yml` and `lang.yml` — and the wizard itself runs entirely in your browser.

If you paste your webhook URL in yourself, it never leaves your browser. **Connect to
Discord** works slightly differently — [see below](#where-your-webhook-url-goes).

<div id="cfg-gen" class="markdown-body"></div>

<!-- Loader: reads /assets/configs/registry.json, then loads the version-specific
     generator bundle from /assets/configs/<schema>/generator.js -->
<script defer src="{{ '/assets/js/generator.js?v=4' | relative_url }}"></script>

---

## Where your webhook URL goes

<details>
<summary>The short version: pasting it keeps it in your browser, and "Connect to Discord" passes it through a Worker that stores nothing.</summary>

**Pasting it yourself** — it stays in the page. The only place it goes is Discord, when
you send the test message.

**Connect to Discord** cannot work that way. Discord hands your browser a one-time code,
and turning that code into a webhook URL needs the application's client secret, which
cannot be shipped to a browser without publishing it. So the code goes to a small
[Cloudflare Worker](https://github.com/GodTierGamers/DiscordLogger/tree/main/worker)
which exchanges it and returns the URL.

So the URL *passes through* that Worker, and there is no version of this flow where it
does not. What is avoidable is storing it, and the Worker has nowhere to: no database, no
key-value store, no bucket, no queue — and
[a CI check](https://github.com/GodTierGamers/DiscordLogger/blob/main/scripts/check-worker-stateless.py)
fails the build if anyone adds one. The tokens Discord returns alongside the webhook are
never read. [`/version`](https://oauth.godtiergamers.xyz/version) reports the commit it is
running, so you can check the deployment against the source.

It would be easy to write "we log nothing", and it would not be true — Cloudflare records
request metadata whatever the code does. The accurate claim is narrower: nothing is stored
by this project, and the paste field is always there if you would rather not authorize a
third-party app.

</details>
