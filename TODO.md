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
- **Velocity support, in the same JAR.** One download that works on the proxy and the backends, so installing it on a network means dropping the same file in one more place rather than learning a second plugin. Design settled, not yet built:

  - **One JAR, two entry points.** Paper reads `plugin.yml`; Velocity ignores that entirely and reads `velocity-plugin.json`, generated at build time by the `@Plugin` annotation processor. Each platform ignores the other's descriptor, and the class referencing the absent API is simply never loaded. Both APIs go in at `provided` scope. Velocity needs Java 21+, which we already exceed.
  - **Two config files, one schema number.** The proxy genuinely cannot do most of what a backend does, so a shared file would be mostly inert keys — settings that look like they work and don't. Two shapes, but both carry the same global `config-version`, exactly as `config.yml` and `lang.yml` already do. One migrator, one number.
  - **A backend toggle turns it on.** Without it, the backend keeps logging joins and quits itself. With it, the proxy owns them.
  - **Automatic server naming.** This is the point of the feature. `embeds.author` is currently hand-set per backend, so a six-server network is six files to keep in sync and a rename means editing all of them. The proxy already knows every server's name; it pushes each backend its own over a plugin-message channel we own (`discordlogger:main` — *not* the BungeeCord compatibility channel, which is toggleable in `velocity.toml` and would make us depend on someone else's setting).
  - **The naming gotcha:** plugin messaging rides a player connection, so a backend with nobody on it has no channel — which is exactly when `Server Start` fires. Cache the learned name to disk and fall back to the configured `embeds.author`, so only the very first boot after install is unlabelled.
  - **What the proxy can see:** network join/disconnect, server switches, chat, commands, kicked-from-server. **What it cannot:** deaths and damage causes, advancements, teleports, gamemode, explosions, world and coordinates, and every moderation action. Roughly five of the nineteen events. The value is the network-level view and the naming, not coverage.
  - **Double-logging is the failure mode to design against.** A backend with the toggle off behind a proxy produces *worse* noise than today — the exact problem this exists to fix. Have the proxy announce itself so a backend can warn when it sees one and is still logging joins itself.

- **Decouple `ConfigMigrator` from `JavaPlugin`** — *prerequisite for the Velocity work above; scope it before committing to that.* `migrateIfVersionChanged` takes a `JavaPlugin`, a type Velocity does not have, so the proxy cannot use the migrator at all as it stands. It needs a data folder, a resource-loading function and a logger instead. Mechanically straightforward and `migrateText` is already pure — but this is the file that runs once on every existing install and destroys real settings when it is wrong, so it is not a free afternoon. The five `ConfigMigrator` test classes are what make it safe to attempt.

- **Give `SchemaDetector` markers for the other managed files.** It infers a schema from config.yml-shaped keys, so `lang.yml` — and a future proxy config — fall back to their *declared* `config-version` with no shape check behind it. Shape-wins-over-declaration is what makes the version marker hard to break by accident; the other files do not currently have it. Cheapest to add alongside whichever file comes next rather than retrofitting later.

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
