/**
 * Exchanges a Discord OAuth code for an incoming-webhook URL.
 *
 * The generator runs entirely in the browser, which is why this exists at all: the
 * `webhook.incoming` flow is an authorization-code grant, the exchange needs the
 * application's client secret, and a secret cannot be shipped to a browser. So the
 * page sends the one-time code here and gets back the webhook URL.
 *
 * What this deliberately does NOT do:
 *   - store anything (there are no storage bindings; see wrangler.toml)
 *   - log the code, the webhook URL, or anything derived from them
 *   - keep the access or refresh token Discord returns alongside the webhook
 *   - accept a request from an origin that is not on the allowlist
 *
 * It cannot promise that *nothing* is recorded: Cloudflare keeps request metadata
 * regardless of what this handler does. The generator's privacy copy says so.
 */

const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8' };

/** Discord's token endpoint. The only outbound call this Worker makes. */
const TOKEN_URL = 'https://discord.com/api/oauth2/token';

function allowedOrigins(env) {
  return String(env.ALLOWED_ORIGINS || '')
    .split(',')
    .map(o => o.trim())
    .filter(Boolean);
}

/** CORS headers for an allowed origin, or null when the origin is not allowed. */
function corsFor(request, env) {
  const origin = request.headers.get('Origin');
  if (!origin || !allowedOrigins(env).includes(origin)) return null;
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-methods': 'POST, OPTIONS',
    'access-control-allow-headers': 'content-type',
    'access-control-max-age': '86400',
    // The response differs per origin, so a shared cache must not reuse it.
    'vary': 'Origin',
  };
}

function json(body, status, extraHeaders) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...JSON_HEADERS, ...(extraHeaders || {}) },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Which commit is running. No secrets, no origin check -- the point is that
    // anybody can verify the deployment against a public commit.
    if (request.method === 'GET' && url.pathname === '/version') {
      return json({ sha: env.VERSION_SHA || 'unknown' }, 200, {
        'cache-control': 'no-store',
      });
    }

    if (url.pathname !== '/exchange') {
      return json({ error: 'not_found' }, 404);
    }

    const cors = corsFor(request, env);

    if (request.method === 'OPTIONS') {
      // A preflight from an origin we do not serve gets no CORS headers, which is
      // what makes the browser refuse the real request.
      return cors ? new Response(null, { status: 204, headers: cors })
                  : new Response(null, { status: 403 });
    }

    if (request.method !== 'POST') {
      return json({ error: 'method_not_allowed' }, 405, cors || {});
    }

    if (!cors) {
      // Deliberately before anything else is read: an unlisted origin never gets
      // as far as having its body parsed.
      return json({ error: 'origin_not_allowed' }, 403);
    }

    if (!env.DISCORD_CLIENT_ID || !env.DISCORD_CLIENT_SECRET) {
      // Says "not configured" rather than failing obscurely, because the generator
      // only shows the button when a client id is configured and a mismatch here
      // should be readable.
      return json({ error: 'not_configured' }, 503, cors);
    }

    let body;
    try {
      body = await request.json();
    } catch (notJson) {
      return json({ error: 'bad_request' }, 400, cors);
    }

    const code = typeof body.code === 'string' ? body.code : '';
    const redirectUri = typeof body.redirect_uri === 'string' ? body.redirect_uri : '';
    if (!code || !redirectUri) {
      return json({ error: 'bad_request' }, 400, cors);
    }

    // The redirect_uri has to match the one the authorize call used, and Discord
    // checks it against the application's registered list -- but check it here too,
    // so this Worker cannot be used to complete a flow aimed somewhere else.
    if (!allowedOrigins(env).some(o => redirectUri.startsWith(o + '/'))) {
      return json({ error: 'redirect_not_allowed' }, 400, cors);
    }

    const form = new URLSearchParams({
      client_id: env.DISCORD_CLIENT_ID,
      client_secret: env.DISCORD_CLIENT_SECRET,
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri,
    });

    let discord;
    try {
      discord = await fetch(TOKEN_URL, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: form,
      });
    } catch (unreachable) {
      return json({ error: 'discord_unreachable' }, 502, cors);
    }

    if (!discord.ok) {
      // Discord's own error text is not forwarded: it can echo parts of the request,
      // and a code is single-use but still a credential while it lives.
      return json({ error: 'exchange_failed', status: discord.status }, 502, cors);
    }

    let payload;
    try {
      payload = await discord.json();
    } catch (notJson) {
      return json({ error: 'exchange_failed' }, 502, cors);
    }

    const webhook = payload && payload.webhook;
    const webhookUrl = webhook && typeof webhook.url === 'string' ? webhook.url : '';
    if (!webhookUrl) {
      return json({ error: 'no_webhook' }, 502, cors);
    }

    // Everything else Discord just sent -- access_token, refresh_token, scope,
    // expiry -- is of no use here and is not read, returned or retained. Picking the
    // one field out by name, rather than forwarding the payload and trimming it, is
    // what makes that true by construction.
    return json({
      url: webhookUrl,
      channel_id: webhook.channel_id || null,
      guild_id: webhook.guild_id || null,
    }, 200, { ...cors, 'cache-control': 'no-store' });
  },
};
