---
layout: default
title: Setup / Install
description: Install DiscordLogger, set your webhook, and verify everything is working.
---

![DiscordLogger](/assets/DiscordLogger-Banner.webp "DiscordLogger")
# Setup / Install

This guide walks you through installing **DiscordLogger** and getting logs into Discord in minutes.

> **Latest plugin:** v<span data-dl-latest>…</span>  
> **Config schema:** v9

---

## 1) Requirements

- **Server:** Paper {{ site.data.versions.paper_display }}, or a Paper fork such as Purpur
- **Java:** {{ site.data.versions.java }} or newer (Minecraft {{ site.data.versions.paper_display }} requires it)
- **Discord:** A channel where you can create a webhook
- **Permissions:**
    - Discord:
        - Manage Webhooks
    - Minecraft:
        - Operator permissions
        - Access to console

---

## 2) Download

1. Grab the latest build from **[GitHub](https://github.com/GodTierGamers/DiscordLogger/releases)**.
2. Drop the JAR into your server’s `/plugins` folder:  
   `.../server/plugins/DiscordLogger-<version>.jar`

Start (or restart) the server once. This will generate `plugins/DiscordLogger/config.yml`.

---

## 3) Create a Discord Webhook

In your Discord server:

1. Right-click the target channel → **Edit Channel** → **Integrations** → **Webhooks**
2. **New Webhook** → name/icon → **Copy Webhook URL**

Keep that URL for the next step.

---

## 4) Configure `config.yml`

Open: `plugins/DiscordLogger/config.yml`

**Edit your webhook URL (required):**

```yaml
webhook:
  url: "https://discord.com/api/webhooks/XXXX/XXXXXXXXXXXXXXXX"
```

**Optional (embeds are enabled by default):**

```yaml
embeds:
  enabled: true
  author: "Server Logs"
```

> The last line of your file records which config schema it uses, e.g.  
> `# CONFIG VERSION {{ site.data.versions.schema }}, SHIPPED WITH v{{ site.data.versions.plugin }} BUILT DD-MM-YYYY`  
> The `{{ site.data.versions.schema }}` part is what matters — that's how the plugin knows whether your
> config needs migrating, and how the docs map versions. The version and build
> date after it are just there to tell you which build wrote the file.

Save the file.

---

## 5) Reload the Plugin

From console or as an OP:

```shell
/discordlogger reload
```

This reloads the config and verifies the webhook formatting.

---

## 6) Verify It Works

Trigger something simple:

- **Join/quit** the server (should post an embed with your head)
- Run a **server command** in console (e.g., `list`) to see a “Server Command” log

**If nothing posts:**

- Double-check the **webhook URL** (no extra spaces/quotes)
- Ensure outbound HTTPS to Discord is allowed by your host/firewall
- Check console for `[DiscordLogger] Webhook HTTP ...` messages

---

## 7) Tuning What Gets Logged

You can toggle categories (and colors) in `config.yml`. See:

- **[config.yml Generator](/generator/)** – build a config interactively
- **[config.yml Docs (versioned)](/config/)** – full explanations for every key

> Moderation actions (ban/unban/kick/op/deop/whitelist) only log when the action **actually succeeds** (permission-checked).

---

## 8) Upgrading Later

- Drop in the new JAR, restart once.
- Your settings are preserved; the config is versioned (v9).
- When a new config version is released, it will automatically update with your old config version, with new features being set to their default settings

> If after an update your config settings no longer work, [regenerate your config](/generator/) to match the latest version

---

### Next steps

- Want to generate the perfect config? See **[config.yml Generator](/generator/)**.
- Need details for every key? See **[Config Docs](/config/)**.
