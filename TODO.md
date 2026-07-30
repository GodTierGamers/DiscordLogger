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

## Config schema v10

- **Move embed colour options to sub-options under their event toggles** — requires introducing nested sub-options, including in the config generator. This is the change that starts config schema **v10**.

## Website & docs

- **Discord OAuth webhook creation** — let the config generator create the webhook via Discord OAuth instead of making people copy a URL by hand.
- **Complete website redesign.**
- **Improve SEO.**
- **Full docs quality pass** — review everything for accuracy and clarity.
- **Verify every plugin-version reference is correct** across the site and repo.
