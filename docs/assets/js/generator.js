/* DiscordLogger – config generator (step 1: versions + webhook test)
   - Uses window.DL_PROXY_URL if provided
   - Fallback direct POST (may be blocked by CORS)
*/

(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    // ---- Version map (more can be added later)
    const VERSION_MAP = [
        { plugin: '2.1.5', config: 'v9', default: true },
        // { plugin: '2.1.6', config: 'v9' }, // example of many plugin versions sharing config v9
    ];

    // ---- Utilities
    const $ = (sel, el = document) => el.querySelector(sel);
    const create = (tag, attrs = {}, children = []) => {
        const el = document.createElement(tag);
        Object.entries(attrs).forEach(([k, v]) => {
            if (k === 'class') el.className = v;
            else if (k === 'html') el.innerHTML = v;
            else el.setAttribute(k, v);
        });
        (Array.isArray(children) ? children : [children]).forEach(ch => {
            if (ch == null) return;
            el.appendChild(typeof ch === 'string' ? document.createTextNode(ch) : ch);
        });
        return el;
    };

    const isDiscordWebhook = (url) => {
        if (!url) return false;
        const re = /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+(?:\?wait=true)?$/i;
        return re.test(url.trim());
    };

    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;

    const nowIso = () => new Date().toISOString();

    // Build the exact embed the user provided
    const buildTestPayload = () => ({
        content: null,
        embeds: [
            {
                title: "DiscordLogger Webhook Test",
                description:
                    "Hello, this is a test of your webhook to confirm if it works, if you are seeing this message, it worked.\n\n" +
                    "If you did not request a webhook test, confirm with other members of your server if they created a webhook or used an already existing webhook URL, " +
                    "if nobody in your server requested this, reset/delete the webhook URL",
                url: "https://discordlogger.godtiergamers.xyz",
                color: 5814783,
                author: {
                    name: "DiscordLogger Webhook Test",
                    url: "https://discordlogger.godtiergamers.xyz",
                    icon_url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
                },
                footer: {
                    text: "DiscordLogger Webhook Test",
                    icon_url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
                },
                timestamp: nowIso(),
                thumbnail: {
                    url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
                }
            }
        ],
        attachments: []
    });

    // Proxy POST -> Cloudflare Worker
    const postViaProxy = async (proxyUrl, webhookUrl, payload) => {
        const res = await fetch(proxyUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: withWait(webhookUrl), payload })
        });
        const text = await res.text().catch(() => '');
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch {}
        return { ok: res.ok, status: res.status, data, text };
    };

    // Direct POST (likely CORS-blocked; we can still try)
    const postDirect = async (webhookUrl, payload) => {
        try {
            const res = await fetch(withWait(webhookUrl), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                // mode: 'cors' // default; CORS may block reading response
            });
            let json = null;
            try { json = await res.json(); } catch {}
            return { ok: res.ok, status: res.status, data: json };
        } catch (e) {
            return { ok: false, status: 0, error: String(e) };
        }
    };

    // ---- UI
    mount.innerHTML = '';

    const header = create('div', { class: 'cfg-head', html: `
    <h2>Config Generator</h2>
    <p>Start by selecting the plugin version and testing your Discord webhook.</p>
  `});

    // Step 1: select plugin version (shows mapped config)
    const versionSel = create('select', { class: 'cfg-input' },
        VERSION_MAP.map(v =>
            create('option', { value: v.plugin, ...(v.default ? { selected: '' } : {}) }, v.plugin)
        )
    );

    const configOut = create('div', { class: 'cfg-note' });

    const recomputeConfig = () => {
        const plugin = versionSel.value;
        const match = VERSION_MAP.find(v => v.plugin === plugin);
        configOut.textContent = match ? `Config schema: ${match.config}` : 'Config schema: (unknown)';
    };
    recomputeConfig();
    versionSel.addEventListener('change', recomputeConfig);

    const step1 = create('section', { class: 'cfg-step' }, [
        create('label', { class: 'cfg-label' }, 'Plugin Version'),
        versionSel,
        configOut
    ]);

    // Step 2: webhook entry + test
    const webhookInput = create('input', {
        class: 'cfg-input',
        type: 'url',
        placeholder: 'https://discord.com/api/webhooks/…'
    });

    const helper = create('div', { class: 'cfg-hint' },
        'Paste your Discord channel webhook URL. We’ll send the test embed you specified.'
    );

    const testBtn = create('button', { class: 'cfg-btn', type: 'button' }, 'Send Test');
    const statusBox = create('div', { class: 'cfg-status' });

    const step2 = create('section', { class: 'cfg-step' }, [
        create('label', { class: 'cfg-label' }, 'Webhook URL'),
        webhookInput,
        helper,
        testBtn,
        statusBox
    ]);

    // Step 3: confirm received?
    const confirmWrap = create('section', { class: 'cfg-step', style: 'display:none' }, [
        create('div', { class: 'cfg-note' }, 'Did you see the test message in Discord?'),
        create('div', { class: 'cfg-actions' }, [
            create('button', { class: 'cfg-btn', type: 'button', id: 'yes' }, 'Yes, continue'),
            create('button', { class: 'cfg-btn btn--ghost', type: 'button', id: 'no' }, 'No, try again')
        ])
    ]);

    // Wire “Send Test”
    testBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        statusBox.textContent = '';
        confirmWrap.style.display = 'none';

        if (!isDiscordWebhook(url)) {
            statusBox.textContent = 'Please enter a valid Discord webhook URL.';
            statusBox.className = 'cfg-status is-error';
            return;
        }

        testBtn.disabled = true;
        statusBox.className = 'cfg-status is-info';
        statusBox.textContent = 'Sending test…';

        const payload = buildTestPayload();
        const proxy = (window.DL_PROXY_URL || '').trim();

        let res;
        try {
            if (proxy) {
                res = await postViaProxy(proxy, url, payload);
            } else {
                res = await postDirect(url, payload);
            }
        } catch (e) {
            res = { ok: false, status: 0, error: String(e) };
        }

        testBtn.disabled = false;

        if (res.ok) {
            statusBox.className = 'cfg-status is-ok';
            statusBox.textContent = 'We successfully posted the test embed.';
            confirmWrap.style.display = '';
        } else {
            statusBox.className = 'cfg-status is-error';
            const msg = res?.data?.message || res?.text || res?.error || `HTTP ${res.status}`;
            statusBox.textContent = `We couldn’t verify the test (browser/host may block it). Check Discord. Details: ${msg}`;
            // Still allow user to confirm manually
            confirmWrap.style.display = '';
        }
    });

    // Confirm buttons
    confirmWrap.querySelector('#yes').addEventListener('click', () => {
        statusBox.className = 'cfg-status is-ok';
        statusBox.textContent = 'Great! Webhook confirmed.';
        // (Next steps: show embed/plain toggle, log toggles, colors…)
    });
    confirmWrap.querySelector('#no').addEventListener('click', () => {
        statusBox.className = 'cfg-status is-info';
        statusBox.textContent = 'Double-check the URL, channel permissions, or try the proxy method.';
    });

    // Assemble
    mount.appendChild(header);
    mount.appendChild(step1);
    mount.appendChild(step2);
    mount.appendChild(confirmWrap);

    // ---- lightweight styles (inherits your theme vars)
    const style = document.createElement('style');
    style.textContent = `
  #cfg-gen .cfg-step { margin: 1.25rem 0 1.75rem; }
  #cfg-gen .cfg-label { font-weight: 600; display:block; margin: .25rem 0 .35rem; }
  #cfg-gen .cfg-input {
    width: 100%; max-width: 720px;
    padding: .6rem .75rem; border-radius: 10px;
    border: 1px solid var(--border); background: var(--bg); color: var(--fg);
    font: inherit;
  }
  #cfg-gen .cfg-btn {
    margin-top: .75rem;
    padding: .55rem .9rem; border-radius: 10px; border: 1px solid var(--border);
    background: color-mix(in oklab, var(--accent) 14%, transparent);
    color: var(--fg); cursor: pointer;
  }
  #cfg-gen .btn--ghost { background: transparent; }
  #cfg-gen .cfg-note { color: var(--muted); margin-top: .35rem; }
  #cfg-gen .cfg-hint { color: var(--muted); margin: .4rem 0 .2rem; }
  #cfg-gen .cfg-status { margin-top: .6rem; padding: .6rem .75rem; border-radius: 10px; border: 1px solid var(--border); }
  #cfg-gen .cfg-status.is-ok { border-color: #16a34a33; background: #16a34a14; }
  #cfg-gen .cfg-status.is-error { border-color: #ef444433; background: #ef444414; }
  #cfg-gen .cfg-status.is-info { border-color: var(--border); background: color-mix(in oklab, var(--fg) 5%, transparent); }
  #cfg-gen .cfg-actions { display:flex; gap:.5rem; margin-top:.5rem; }
  `;
    document.head.appendChild(style);
})();
