# TODO

Planned work that hasn't been done yet.

This list is deliberately short. Items land here when work is actually committed to — it isn't a wishlist or an idea dump — and they're deleted the moment they're finished. So everything listed is genuinely outstanding, and an empty list means nothing is pending.

Got an idea that isn't here? Open a [feature request](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=feature-request.yml) — that's where proposals get discussed, and it keeps this file trustworthy as a picture of committed work.

---

## Release automation

- **Give release-please a GitHub App token instead of `GITHUB_TOKEN`.** *Blocks every future release; do this first.* A PR authored by `github-actions[bot]` never triggers `pull_request_target`, so the required `lint` context can never report on it and the release PR sits `BLOCKED` indefinitely — #102 needed an admin override to ship 2.2.0, and #151 already needs one. The same token change fixes `sync-versions.yml`, whose direct push to `main` branch protection rejects.

  - Create a GitHub App (Contents: read/write, Pull requests: read/write, no webhook), install it on this repo only, and add `RELEASE_APP_ID` + `RELEASE_APP_PRIVATE_KEY` as Actions secrets. **Requires the project owner's account.**
  - Then mint a token at runtime with `actions/create-github-app-token` and pass it to the `token:` input of `googleapis/release-please-action`.
  - A fine-grained PAT works too, but expires — and a silent expiry puts releases straight back to needing manual overrides.

## Plugin

- **PlaceholderAPI support** — the one feature a server owner comparing DiscordLogger against WebhookLogger side by side would pick the other for. MiniMessage formatting already exists in `lang.yml`; what's missing is placeholder *expansion*, so rank prefixes, nicknames and the like appear in relayed messages. Soft-depend and expand `lang.yml` values before sending, skipping silently when the plugin is absent — the same reflection pattern `ClientPlatform` uses for Floodgate. Opens config schema v11.
- **Deeper logging modes** — option to relay the full server console rather than only the specific events currently supported.

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

- **Verify the domain in Google Search Console.** *Do this before writing any of the pages below.* The site does not surface in search even for its own domain name, which is consistent with it not being indexed at all. If that is the case, no amount of content fixes it, and every page written first is wasted effort. Ten minutes, and it is diagnostic: it says whether the problem is indexation or authority. **Requires the project owner's account.**

- **Write the pages people actually search for.** Measured 2026-08-03: DiscordLogger ranks #1 and #2 for *"minecraft plugin log server events to discord webhook paper"* — as SpigotMC and GitHub. The docs site appears nowhere. For *"how to log minecraft chat to discord without a bot"* the plugin does not appear at all, despite that being precisely what it is. Three pages, in order of intent:

  - **Log Minecraft to Discord without a bot** — the differentiator the plugin owns and ranks nowhere for.
  - **DiscordSRV vs DiscordLogger** — comparison queries convert, and "I only want logging, not a chat bridge" is a real search. Be fair to DiscordSRV; it is a different product, not a worse one.
  - **Discord webhook not posting — troubleshooting** — the symptom→cause table already exists in the setup guide, buried where nobody searching will find it.

- **Search visibility (ongoing)** — the technical groundwork is in place (sitemap, canonicals, structured data, per-page titles and descriptions), and the Modrinth/Hangar listing bodies were rewritten for 2.2.0. What's left is the slow part: the pages above, listings on the sites Minecraft admins browse, and monitoring real queries once Search Console is connected. This item stays open indefinitely; it isn't a task with a finish line.

- **Let the config generator skip the webhook step.** It currently refuses to advance without a valid, confirmed URL, so there is no way to build a config before you have created the webhook — which is the order many people would rather work in.

- **Fix the primary button's contrast in light mode.** `#cfg-gen .cfg-btn--primary` is `color-mix(in oklab, var(--accent) 14%, transparent)`, which on a white panel reads as disabled. It lives in the shared loader stylesheet, so changing it also affects how the frozen v9 bundle renders — check both before committing.

---

## Deliberately not doing

Recorded so they don't get re-proposed. Each was considered and declined for a reason.

- **Competing with DiscordSRV.** A different product — a two-way bridge with a bot, and 480k Modrinth downloads. DiscordLogger's audience is specifically the people who don't want a bot, which DiscordSRV cannot serve by design.
- **Backporting to 1.21.x.** It is where most servers still are, but it means giving up the Paper APIs that make the current feature depth possible. Users on older versions still have 2.1.6.
- **Spigot/CraftBukkit support.** The plugin uses Paper's chat API; supporting Spigot means a second code path through the core of what it does.
- **Syncing listing descriptions from CI.** Would need `PROJECT_WRITE` on a Modrinth token living in a public repo — a scope that can also unpublish the project — against a recurring cost of one paste per release. Not worth the blast radius.
