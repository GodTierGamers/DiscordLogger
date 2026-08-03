---
layout: default
title: "config.yml Generator — Build a Config Online"
description: Build a DiscordLogger config.yml in your browser — pick which Minecraft events get logged, set your Discord webhook, and download the finished file.
---

# config.yml Generator

Pick your plugin version, test your webhook, then choose what to log. Everything runs in your browser — your webhook URL is never sent anywhere except Discord.

<div id="cfg-gen" class="markdown-body"></div>

<!-- Loader: reads /assets/configs/registry.json, then loads the version-specific
     generator bundle from /assets/configs/<schema>/generator.js -->
<script defer src="{{ '/assets/js/generator.js?v=2' | relative_url }}"></script>
