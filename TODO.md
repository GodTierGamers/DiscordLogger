# TODO

Planned work that hasn't been done yet.

This list is deliberately short. Items land here when work is actually committed to — it isn't a wishlist or an idea dump — and they're deleted the moment they're finished. So everything listed is genuinely outstanding, and an empty list means nothing is pending.

Got an idea that isn't here? Open a [feature request](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=feature-request.yml) — that's where proposals get discussed, and it keeps this file trustworthy as a picture of committed work.

---

## Plugin

- **Per-category webhook routing** — send different log categories to different Discord channels (e.g. moderation to a private staff channel, chat to a public one).
- **Deeper logging modes** — option to relay the full server console rather than only the specific events currently supported.
- **Log filtering (allow/deny lists)** — exclude specific entries within a category: e.g. don't log `/whisper` when command logging is on, or ignore a particular player by UUID.
- **Bedrock vs Java indicator** — show which platform a player connected from.
- **`lang.yml` with MiniMessage** — move every user-facing string into a language file so wording and formatting can be fully rewritten without touching code.

## Website & docs

- **Discord OAuth webhook creation** — let the config generator create the webhook via Discord's own channel picker instead of making people copy a URL by hand. Design settled, not yet built:

  - Uses the `webhook.incoming` scope. It is an authorization-code grant and cannot use implicit grant, so the code exchange needs the client secret and therefore a backend — a Cloudflare Worker. The exchange response contains `webhook.url` directly; the `access_token` and `refresh_token` it also returns are of no use here and should be discarded on arrival.
  - **The Worker is stateless.** Exchange the code, return the URL, keep nothing. Ship a `wrangler.toml` with no KV, D1, R2 or Durable Object bindings, so the Worker is *structurally* incapable of persisting anything rather than merely promising not to.
  - **Deploy from CI on a tag**, and expose a `/version` endpoint returning the commit SHA, so the running Worker is traceable to a public commit. Open source proves the code, not the deployment; this is what closes that gap.
  - **Rewrite the generator's privacy line honestly.** It currently says the webhook URL "is never sent anywhere except Discord", which is true today. Under OAuth the URL necessarily transits the Worker — the exchange needs the secret, and Discord returns the webhook in that same response, so there is no variant of the flow that avoids it. Transit is unavoidable; storage is avoidable. Say exactly that, and don't claim "we log nothing" — Cloudflare records request metadata regardless of what the handler does.
  - **Additive, never a replacement.** The manual paste field stays, for anyone who won't authorize a third-party app.
  - Needs a `state` parameter for CSRF and an origin allowlist.
  - Note: Discord creates a **new webhook on every authorization**, so running the flow repeatedly accumulates webhooks in the channel. The UI has to say so.
  - Requires a Discord application (client ID + secret) registered by the project owner, and a registered redirect URI.
- **Complete website redesign.**
- **Search visibility (ongoing)** — the technical groundwork is in place (sitemap, canonicals, structured data, per-page titles and descriptions). What's left is the slow part: content people actually search for (troubleshooting and comparison pages), listings on the sites Minecraft admins browse, and monitoring real queries in Search Console. This item stays open indefinitely; it isn't a task with a finish line.
- **Full docs quality pass** — review everything for accuracy and clarity.
