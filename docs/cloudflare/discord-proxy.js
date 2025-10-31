// Deploy as a Cloudflare Worker (e.g., route: https://discord-proxy.godtiergamers.xyz/relay)
// Set an environment variable: ALLOWED_ORIGINS = https://discordlogger.godtiergamers.xyz,https://godtiergamers.github.io

export default {
    async fetch(request, env) {
        // CORS preflight
        if (request.method === 'OPTIONS') {
            return cors(null, env);
        }

        if (new URL(request.url).pathname !== '/relay' || request.method !== 'POST') {
            return new Response('Not found', { status: 404 });
        }

        const origin = request.headers.get('Origin') || '';
        if (!isAllowedOrigin(origin, env.ALLOWED_ORIGINS)) {
            return cors(new Response(JSON.stringify({ error: 'Origin not allowed' }), { status: 403 }), env, origin);
        }

        let body;
        try {
            body = await request.json();
        } catch {
            return cors(new Response(JSON.stringify({ error: 'Invalid JSON' }), { status: 400 }), env, origin);
        }

        const url = (body?.url || '').toString();
        const payload = body?.payload || null;

        if (!/^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+/i.test(url)) {
            return cors(new Response(JSON.stringify({ error: 'Invalid webhook URL' }), { status: 400 }), env, origin);
        }

        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const text = await res.text();
        // Pass Discord’s status and body through
        return cors(new Response(text, { status: res.status, headers: { 'Content-Type': 'application/json' } }), env, origin);
    }
};

function isAllowedOrigin(origin, allowedCsv = '') {
    if (!allowedCsv) return false;
    return allowedCsv.split(',').map(s => s.trim()).some(a => a && a === origin);
}

function cors(response, env, origin) {
    const headers = {
        'Access-Control-Allow-Methods': 'POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Max-Age': '86400'
    };
    if (origin && isAllowedOrigin(origin, env.ALLOWED_ORIGINS)) {
        headers['Access-Control-Allow-Origin'] = origin;
        headers['Vary'] = 'Origin';
    } else {
        headers['Access-Control-Allow-Origin'] = 'https://example.invalid';
    }
    return response
        ? new Response(response.body, { status: response.status, headers: new Headers({ ...Object.fromEntries(response.headers), ...headers }) })
        : new Response(null, { status: 204, headers });
}
