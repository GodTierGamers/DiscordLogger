---
layout: default
title: "config.yml Generator — Build a Config Online"
description: Build a DiscordLogger config.yml and lang.yml in your browser — pick what gets logged, set your webhook, tune the filters, download the finished files.
---

# config.yml Generator

Pick your plugin version, test your webhook, then choose what to log, what to filter
out, and what every message says. You get both files the plugin uses —
`config.yml` and `lang.yml` — and the wizard itself runs in your browser.

<div id="cfg-gen" class="markdown-body"></div>

<!-- Loader: reads /assets/configs/registry.json, then loads the version-specific
     generator bundle from /assets/configs/<schema>/generator.js -->
<script defer src="{{ '/assets/js/generator.js?v=5' | relative_url }}"></script>
