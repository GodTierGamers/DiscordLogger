# OAuth Worker

A Cloudflare Worker with one job: turn the one-time code Discord hands the config
generator into a webhook URL. It answers on `https://oauth.godtiergamers.xyz`.

It exists because `webhook.incoming` is an authorization-code grant. Exchanging the code
needs the Discord application's client secret, and a secret shipped to a browser is a
published secret — so the exchange has to happen somewhere else. This is that somewhere,
and it is as small as the job allows.

## What it does with the URL

The URL passes through on its way back to your browser, and there is no version of this
flow where it does not. What *is* avoidable is storing it:

- **There is nowhere to store it.** `wrangler.toml` declares no KV namespace, no D1
  database, no R2 bucket, no Durable Object, no queue and no service binding.
  [`scripts/check-worker-stateless.py`](../scripts/check-worker-stateless.py) fails CI if
  anyone adds one, because the generator page makes this promise in public.
- **Only `webhook.url` is read**, by name. The `access_token` and `refresh_token` arrive
  in the same response from Discord and are never touched — picking one field out, rather
  than forwarding the payload and trimming it, is what makes that true by construction.
- **Discord's error text is not forwarded.** It can echo parts of the request, and a code
  is single-use but still a credential while it lives.
- **The origin allowlist is checked before the body is parsed**, and `redirect_uri` is
  checked against the same list, so the Worker cannot be used to complete a flow aimed
  somewhere else.

It would be easy to write "we log nothing", and it would not be true: Cloudflare records
request metadata whatever the handler does. The accurate claim is narrower — nothing is
stored by this project. Pasting your webhook URL into the generator by hand skips the
Worker completely, and that option is always there.

## Checking the deployment against the source

`GET /version` reports the commit the running Worker was built from:

```bash
curl https://oauth.godtiergamers.xyz/version
```

Deploys only happen from a `worker-v*` tag, so that commit is immutable and public.

## Endpoints

| Route | Method | Purpose |
|---|---|---|
| `/version` | GET | The commit this deployment was built from |
| `/exchange` | POST | `{ code, redirect_uri }` → `{ url }` |

## Deploying

Needs `CLOUDFLARE_API_TOKEN` in repository secrets and `DISCORD_CLIENT_SECRET` set on the
Worker itself (`wrangler secret put DISCORD_CLIENT_SECRET`). The client **id** is public
and lives in `wrangler.toml`; the secret never enters the repository. Without the API
token the deploy job says so and skips rather than failing — a checkout with no Cloudflare
credentials is a valid state.
