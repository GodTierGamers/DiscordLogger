/*  DiscordLogger — config generator LOADER
    ------------------------------------------------------------------
    Small and stable. It:
      1. reads /assets/configs/registry.json   (which config schemas exist)
      2. reads DLVersions (versions.js) for the real release list + beta status
      3. renders the PLUGIN VERSION picker — users know which plugin build they
         downloaded, not which config schema it uses, so the schema is resolved
         for them and only shown as a confirmation note
      4. injects the resolved schema's SELF-CONTAINED bundle:
             /assets/configs/<vN>/generator.js

    Every published build is listed individually, nightlies included
    (1.2.3-BETA.1, 1.2.3-BETA.2, …) — successive nightlies can carry different
    features, so they are genuinely different targets. Nightlies are hidden
    until the visitor opts in via the beta toggle. "Beta" is derived from the
    releases API, never hand-flagged.

    A registry entry may set "generatorReady": false to appear in the list
    before its bundle exists — the picker then says so instead of failing to
    load a missing script.

    THE BUNDLE CONTRACT (frozen — never change once a schema folder ships):
      - Each bundle registers itself on load:
            window.DL_GENERATORS = window.DL_GENERATORS || {};
            window.DL_GENERATORS['v9'] = function launch(ctx) { ... };
      - ctx = {
          mount:          DOM node to render into (bundle owns it entirely),
          configVersion:  resolved schema, e.g. "v9",
          pluginVersion:  the build the user picked, e.g. "1.2.3" or "1.2.3-BETA.1",
          beta:           true when that build is a nightly / pre-release,
          proxyUrl:       optional CORS relay for webhook tests ("" = direct),
          backToVersions: fn — bundle calls this for its "Back" on step 1
        }
    Old schema folders are NEVER edited after a newer schema ships — that
    isolation is the whole point. Fix bugs only in the newest schema.
*/
(() => {
    'use strict';

    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    const REGISTRY_URL = '/assets/configs/registry.json';

    const h = (tag, props = {}, children = []) => {
        const n = document.createElement(tag);
        for (const [k, v] of Object.entries(props)) {
            if (k === 'class') n.className = v;
            else if (k === 'text') n.textContent = v;
            else if (k.startsWith('on')) n.addEventListener(k.slice(2).toLowerCase(), v);
            else if (v === true) n.setAttribute(k, '');
            else if (v !== false && v != null) n.setAttribute(k, v);
        }
        (Array.isArray(children) ? children : [children]).forEach(c => {
            if (c == null) return;
            n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
        });
        return n;
    };

    /* ---- version parsing: understands 1.2.3 and 1.2.3-BETA.4 ---- */

    const parseVer = raw => {
        const m = String(raw || '').trim().match(/^v?(\d+)\.(\d+)\.(\d+)(?:-BETA\.(\d+))?$/i);
        if (!m) return null;
        return {
            base: [Number(m[1]), Number(m[2]), Number(m[3])],
            beta: m[4] !== undefined ? Number(m[4]) : null,
        };
    };
    const cmpBase = (a, b) => {
        for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
        return 0;
    };
    /* Full ordering: base, then stable outranks any beta of that base, then beta number.
       So 1.2.3-BETA.1 < 1.2.3-BETA.2 < 1.2.3 */
    const cmpVer = (a, b) => {
        const c = cmpBase(a.base, b.base);
        if (c !== 0) return c;
        if ((a.beta === null) !== (b.beta === null)) return a.beta === null ? 1 : -1;
        return (a.beta || 0) - (b.beta || 0);
    };

    async function fetchJson(url, timeoutMs = 8000) {
        const ctl = new AbortController();
        const t = setTimeout(() => ctl.abort(), timeoutMs);
        try {
            const res = await fetch(url, { signal: ctl.signal });
            return res.ok ? await res.json() : null;
        } catch {
            return null;
        } finally {
            clearTimeout(t);
        }
    }

    /* versions.js loads from <head> so it is normally ready first, but never
       depend on script ordering — wait briefly, then carry on without beta
       awareness rather than breaking the generator entirely. */
    async function waitForVersions(timeoutMs = 5000) {
        const started = Date.now();
        while (!window.DLVersions) {
            if (Date.now() - started > timeoutMs) return null;
            await new Promise(r => setTimeout(r, 50));
        }
        return window.DLVersions.ready();
    }

    /* Resolve which config schema a build uses: the newest schema whose `since`
       is <= that build. `since` may itself be a nightly (e.g. "1.2.3-BETA.1")
       when a schema debuts in a nightly. Returns the schema entry, or null when
       the build predates every generator we ship. */
    function schemaFor(version, schemas) {
        const v = parseVer(version);
        if (!v) return null;
        for (const s of schemas) {           // newest-first, so first match wins
            const since = parseVer(s.since);
            if (since && cmpVer(v, since) >= 0) return s;
        }
        return null;
    }

    /* Every published build that has a generator, newest first. Nightlies are
       listed individually — consecutive nightlies can differ in features. */
    function buildVersionList(releases, schemas, versionApi) {
        const list = (releases || [])
            .filter(r => parseVer(r.version))
            .filter(r => schemaFor(r.version, schemas))
            .map(r => ({
                version: r.version,
                beta: !!r.prerelease || (versionApi ? versionApi.isBeta(r.version) : false),
            }));

        /* A schema is offered only once a release that ships it has actually been
           PUBLISHED. An unreleased schema is developed in the open -- its bundle,
           its mirrors and its docs page all exist and are testable by URL -- but it
           is not listed, because a version that cannot be downloaded yet has no
           business appearing in a picker of builds to configure.

           This costs the nightly users who already run it: they get the previous
           schema's generator until release day. That is the accepted trade, and it
           is the deliberate reverse of what this did before -- see AGENTS.md, which
           records both sides so it is not silently flipped back. */

        // de-dupe identical tags, keeping the stabler entry
        const byVersion = new Map();
        list.forEach(e => {
            const prev = byVersion.get(e.version);
            if (!prev || (prev.beta && !e.beta)) byVersion.set(e.version, e);
        });

        return [...byVersion.values()].sort((a, b) => cmpVer(parseVer(b.version), parseVer(a.version)));
    }

    /* ---------------------------------------------------------------- Discord OAuth --

       "Connect to Discord" fills the webhook field for people instead of making them
       copy a URL out of Discord's settings by hand.

       It lives in the LOADER, not in a version bundle, and that is the whole reason it
       can exist at all: bundles freeze when their schema ships, so v9, v10 and v11 can
       never be edited again. Driving the field from out here gives every live schema
       the feature without touching a frozen file -- and it works because the webhook
       input has had the same shape in every bundle to date, and because setting .value
       and dispatching 'input' is exactly what a person typing does, so the bundle's own
       listener updates its state and re-runs its own validation.

       Empty config disables it completely: no button, and the generator behaves exactly
       as it did before. That is the state until the Discord application exists.
    */
    const OAUTH = {
        clientId: '1384088628010942554',     // public; appears in every authorize URL
        worker: 'https://oauth.godtiergamers.xyz',
        redirect: `${location.origin}/generator/connected/`,
    };

    /* The one input the button belongs next to. Matched on its placeholder because the
       per-event routing fields share the class and the type -- they carry "Leave empty
       for the main webhook" instead, which is how the two are told apart in every
       bundle from v9 onward. */
    const WEBHOOK_FIELD = 'input.cfg-input[type=url][placeholder^="https://discord.com/api/webhooks/"]';

    const oauthConfigured = () => Boolean(OAUTH.clientId && OAUTH.worker);

    function randomState() {
        const a = new Uint8Array(16);
        crypto.getRandomValues(a);
        return Array.from(a, b => b.toString(16).padStart(2, '0')).join('');
    }

    /** Runs the whole popup round trip and resolves to a webhook URL, or throws. */
    function connectToDiscord() {
        return new Promise((resolve, reject) => {
            const state = randomState();
            const authorize = 'https://discord.com/oauth2/authorize'
                + `?client_id=${encodeURIComponent(OAUTH.clientId)}`
                + '&response_type=code'
                + '&scope=webhook.incoming'
                + `&redirect_uri=${encodeURIComponent(OAUTH.redirect)}`
                + `&state=${state}`;

            const popup = window.open(authorize, 'dl-discord-oauth', 'width=520,height=760');
            if (!popup) {
                reject(new Error('Your browser blocked the popup.'));
                return;
            }

            /* The relay page answers two ways and we listen for both. postMessage is the
               direct route; the channel is what works when the trip through Discord has
               severed the opener link, which leaves postMessage with nowhere to go and
               used to look exactly like the user closing the window. */
            let channel = null;
            try { channel = new BroadcastChannel('discordlogger-oauth'); } catch (e) { /* older Safari */ }

            let done = false;
            let answered = false;   // something came back, so a closed window is not the story
            let spending = false;   // a code is single-use; see handle()
            const finish = (fn, arg) => {
                if (done) return;
                done = true;
                window.removeEventListener('message', onMessage);
                if (channel) { try { channel.close(); } catch (e) { /* already gone */ } }
                clearInterval(watchClosed);
                clearTimeout(giveUp);
                fn(arg);
            };

            async function handle(d) {
                // Only our message, and only the state we just minted. Without the state
                // check, any page could hand us a code it obtained for a different
                // channel and have it written into the field.
                if (!d || d.type !== 'discordlogger-oauth') return;
                if (d.state !== state) {
                    finish(reject, new Error('That response did not match this request.'));
                    return;
                }
                answered = true;
                if (d.error) {
                    finish(reject, new Error('Discord refused the authorization.'));
                    return;
                }
                if (!d.code) {
                    finish(reject, new Error('Discord did not return a code.'));
                    return;
                }
                // The relay answers on both routes because it cannot tell which one
                // works, so when the opener survives the SAME code arrives twice. It is
                // single-use: exchanging it a second time fails, and the loser of that
                // race is as likely as the winner to be the one reporting back. Set
                // before the first await, which is what makes this a lock -- nothing can
                // interleave ahead of it.
                if (spending) return;
                spending = true;
                try {
                    const res = await fetch(`${OAUTH.worker}/exchange`, {
                        method: 'POST',
                        headers: { 'content-type': 'application/json' },
                        body: JSON.stringify({ code: d.code, redirect_uri: OAUTH.redirect }),
                    });
                    const body = await res.json().catch(() => null);
                    if (!res.ok || !body || !body.url) {
                        // The Worker passes Discord's status through; naming it here is
                        // the difference between "something went wrong" and knowing
                        // whether the code was stale (400) or the app is misconfigured
                        // (401). It never carries Discord's error text, which can echo
                        // the request back.
                        const said = body && body.status ? ` (Discord said ${body.status})` : '';
                        finish(reject, new Error(`Could not finish the exchange${said}.`));
                        return;
                    }
                    finish(resolve, body.url);
                } catch (e) {
                    finish(reject, new Error('Could not reach the exchange service.'));
                }
            }

            function onMessage(ev) {
                if (ev.origin !== location.origin) return;
                handle(ev.data);
            }
            window.addEventListener('message', onMessage);
            // A BroadcastChannel is scoped to this origin by the platform, so there is
            // no origin to check here -- there is no way for another one to reach it.
            if (channel) channel.onmessage = (ev) => handle(ev.data);

            // Someone closing the popup without authorizing should not leave the button
            // spinning forever. The grace period matters: the relay posts and then
            // closes itself, so a bare `closed` check can win the race against the
            // message it just sent and report a failure that did not happen.
            const watchClosed = setInterval(() => {
                if (!popup.closed || answered) return;
                clearInterval(watchClosed);
                setTimeout(() => {
                    finish(reject, new Error('Window closed before finishing.'));
                }, 800);
            }, 500);

            // Nothing at all came back: the window is still open and idle, or the relay
            // could not reach us by either route.
            const giveUp = setTimeout(() => {
                finish(reject, new Error('Discord did not send anything back. You can paste the URL instead.'));
            }, 300000);
        });
    }

    /* The webhook OAuth produced, remembered across re-renders of the step. The
       bundle has no idea a URL came from Discord, so on its own it keeps asking for
       a test message before it will let anyone past -- see attachOauth(). */
    let oauthUrl = '';

    /* The step's primary action is its Next button; the bundle builds the row the
       same way in every version. */
    const nextButton = (panel) => {
        const row = panel.querySelector('.cfg-actions');
        return row ? row.querySelector('.cfg-btn--primary') : null;
    };

    /* Discord's own mark, unmodified, white on Blurple -- the pairing their brand
       guidelines approve. Drawn in the SVG namespace because createElement('svg')
       makes an HTML element of that name, which renders nothing. */
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const DISCORD_MARK = 'M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z';

    function discordMark() {
        const svg = document.createElementNS(SVG_NS, 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('width', '20');
        svg.setAttribute('height', '20');
        svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('focusable', 'false');
        const path = document.createElementNS(SVG_NS, 'path');
        path.setAttribute('fill', 'currentColor');
        path.setAttribute('d', DISCORD_MARK);
        svg.appendChild(path);
        return svg;
    }

    const OAUTH_STYLE_ID = 'dl-oauth-styles';
    function injectOauthStyles() {
        if (document.getElementById(OAUTH_STYLE_ID)) return;
        document.head.appendChild(h('style', { id: OAUTH_STYLE_ID }, `
    #cfg-gen .dl-connect { margin: .2rem 0 1rem; }
    #cfg-gen .dl-discord-btn { display: inline-flex; align-items: center; gap: .55rem; background: #5865F2; color: #fff; border: 0; border-radius: 8px; padding: .62rem 1.1rem; font: inherit; font-weight: 600; line-height: 1.2; cursor: pointer; }
    #cfg-gen .dl-discord-btn:hover:not(:disabled) { background: #4752C4; }
    #cfg-gen .dl-discord-btn:active:not(:disabled) { background: #3C45A5; }
    #cfg-gen .dl-discord-btn:disabled { opacity: .6; cursor: default; }
    #cfg-gen .dl-discord-btn:focus-visible { outline: 2px solid #5865F2; outline-offset: 2px; }
    #cfg-gen .dl-discord-btn > svg { flex: none; }
    #cfg-gen .dl-manual { border: 1px solid var(--border); border-radius: 10px; padding: .5rem .7rem; margin-bottom: 1rem; }
    #cfg-gen .dl-manual > summary { cursor: pointer; color: var(--muted); }
    #cfg-gen .dl-manual[open] > summary { margin-bottom: .7rem; }
    `));
    }

    /** Rebuilds the webhook step around the button, whenever that step appears. */
    function attachOauth(root) {
        if (!oauthConfigured()) return;
        const field = root.querySelector(WEBHOOK_FIELD);
        if (!field) return;
        const panel = field.closest('.cfg-panel');
        if (!panel) return;

        /* Coming Back here re-renders the step, and the bundle re-disables Next
           because its own "confirmed" flag starts false. A URL Discord minted a
           moment ago does not need confirming twice, so put it back. */
        if (oauthUrl && field.value.trim() === oauthUrl) {
            const b = nextButton(panel);
            if (b) b.disabled = false;
        }

        if (field.dataset.dlOauth === 'on') return;

        const actionsRow = panel.querySelector('.cfg-actions');
        const intro = panel.querySelector('p.cfg-note');
        /* Not the shape we know. Leave the step exactly as the bundle built it
           rather than half-rearranging someone's only way to enter a webhook. */
        if (!actionsRow || !intro) return;

        field.dataset.dlOauth = 'on';
        injectOauthStyles();

        /* Everything the bundle put between the intro and the Back/Next row is the
           manual flow: the label, the field, the test button, and the test's status
           and confirm prompt. That shape is identical in every bundle from v9 on,
           which is what makes collecting it by position safe here. */
        const manual = [];
        for (let n = intro.nextElementSibling; n && n !== actionsRow; n = n.nextElementSibling) {
            manual.push(n);
        }

        const details = h('details', { class: 'dl-manual' }, [
            h('summary', {}, 'Insert Webhook URL (Manual)'),
        ]);
        manual.forEach(n => details.appendChild(n));
        /* Someone who already has one in the field should not have to go hunting
           for it behind a collapsed summary. */
        if (field.value.trim() && field.value.trim() !== oauthUrl) details.open = true;

        const note = h('p', { class: 'cfg-note', style: 'margin:.45rem 0 0' });
        /* Blurple is a fixed brand colour, so it is written out rather than taken
           from the site's palette, and it carries both themes unchanged. */
        const btn = h('button', { class: 'dl-discord-btn', type: 'button' });
        btn.appendChild(discordMark());
        btn.appendChild(h('span', {}, 'Connect to Discord'));

        btn.addEventListener('click', async () => {
            btn.disabled = true;
            note.textContent = 'Waiting for Discord…';
            try {
                const url = await connectToDiscord();
                oauthUrl = url;
                field.value = url;
                /* What a person typing does. The bundle listens for 'input', so this
                   is what makes it take the value. */
                field.dispatchEvent(new Event('input', { bubbles: true }));
                note.textContent = 'Webhook created.';
                /* The bundle gates Next on its test message, which is there to catch a
                   URL that was mistyped or has since been deleted. Discord just made
                   this one, against a channel the user picked in Discord's own UI, so
                   there is nothing left for a test to establish -- move them on rather
                   than asking them to prove it. */
                const b = nextButton(panel);
                if (b) { b.disabled = false; b.click(); }
            } catch (e) {
                note.textContent = e && e.message ? e.message : 'Could not connect.';
            } finally {
                btn.disabled = false;
            }
        });

        panel.insertBefore(h('div', { class: 'dl-connect' }, [
            btn,
            h('p', { class: 'cfg-note', style: 'margin:.45rem 0 0' },
                'Creates a new webhook in a channel you pick. Discord makes a new one each '
                + 'time you do this, so running it twice leaves two.'),
            note,
        ]), actionsRow);
        panel.insertBefore(details, actionsRow);

        /* The bundle's intro describes pasting, which is no longer the main path. */
        intro.textContent = 'Connect your Discord account and the webhook is made for you. '
            + 'If you already have one, you can paste it instead.';
    }

    /* The bundle renders its steps when it likes, so rather than guessing at timing,
       watch the mount and attach whenever the field turns up. */
    if (oauthConfigured()) {
        new MutationObserver(() => attachOauth(mount)).observe(mount, {
            childList: true, subtree: true,
        });
    }

    function launchBundle(ctx) {
        window.DL_GENERATORS = window.DL_GENERATORS || {};
        const ready = window.DL_GENERATORS[ctx.configVersion];
        if (typeof ready === 'function') { ready(ctx); return; }

        ctx.mount.innerHTML = '<p class="cfg-note">Loading generator…</p>';
        const tag = h('script', { src: `/assets/configs/${ctx.configVersion}/generator.js` });
        tag.addEventListener('load', () => {
            const fn = (window.DL_GENERATORS || {})[ctx.configVersion];
            if (typeof fn === 'function') fn(ctx);
            else ctx.mount.innerHTML = '<p class="cfg-note">Generator failed to initialise for this config version.</p>';
        });
        tag.addEventListener('error', () => {
            ctx.mount.innerHTML = '<p class="cfg-note">Could not load the generator for this config version. Please refresh and try again.</p>';
        });
        document.head.appendChild(tag);
    }

    function render(registry, schemas, versions) {
        // Stable releases only, always. A nightly's config schema can still change
        // before it ships, so a config generated against one can be wrong by the
        // time the stable release lands — and it would be wrong silently, which is
        // worse than not offering it. Deliberately not behind a toggle: the beta
        // opt-in elsewhere on the site is for downloading builds, not for configs.
        const visible = versions.filter(v => !v.beta);

        mount.innerHTML = '';

        const panelBody = [
            h('h2', { class: 'cfg-title' }, '1) Plugin version'),
            h('p', { class: 'cfg-note' }, 'Pick the DiscordLogger version you downloaded — the matching config version is detected for you.'),
        ];

        if (!visible.length) {
            panelBody.push(h('p', { class: 'cfg-note' },
                'No stable releases found. Please refresh, or take a ready-made config from the config docs.'));
            mount.appendChild(h('div', { class: 'cfg-wrap' }, [h('section', { class: 'cfg-panel' }, panelBody)]));
            return;
        }

        const select = h('select', { class: 'cfg-input cfg-input--select' });
        visible.forEach((v, i) => {
            select.appendChild(h('option', { value: String(i) }, v.beta ? `${v.version}  (BETA)` : v.version));
        });
        // Default to the newest version on offer. `visible` is sorted newest-first
        // and nightlies are already filtered out, so that is index 0.
        select.value = '0';

        const detail = h('p', { class: 'cfg-note' });
        const notReady = h('p', { class: 'cfg-note cfg-note--beta' });
        // Keyed on the PLUGIN version, not the config schema. Someone can pick an old
        // build whose schema is still current -- 2.2.0 and 2.3.0 are both v10 -- and a
        // schema-based check would say nothing while they configure a build two
        // releases behind. What they chose is a build, so that is what is checked.
        const legacy = h('p', { class: 'cfg-note dl-legacy-notice' });
        const nightlyFootnote = h('p', { class: 'cfg-note cfg-note--footnote' },
            'Nightly builds are not supported here. Their config format can still change '
            + 'before it ships, so only stable releases are listed.');
        const goBtn = h('button', { class: 'cfg-btn cfg-btn--primary', type: 'button' }, 'Continue');

        const current = () => visible[Number(select.value)] || visible[0];
        // visible is sorted newest-first and already excludes nightlies, so index 0 is
        // the newest published stable release.
        const newest = visible[0] ? visible[0].version : null;

        const sync = () => {
            const v = current();
            const schema = schemaFor(v.version, schemas);
            const ready = schema && schema.generatorReady !== false;

            detail.textContent = schema
                ? `Uses config schema ${schema.config.toUpperCase()} — detected automatically.`
                : 'No generator is available for that version.';

            if (schema && !ready) {
                notReady.textContent = `The generator for config ${schema.config.toUpperCase()} isn't available yet — this version is listed for reference only.`;
                notReady.style.display = '';
            } else {
                notReady.style.display = 'none';
            }
            if (newest && v.version !== newest) {
                legacy.innerHTML =
                    '<strong>That is not the latest version.</strong> DiscordLogger '
                  + newest + ' is out. Updating migrates your config for you and keeps '
                  + 'your settings, so there is rarely a reason to configure an older '
                  + 'build \u2014 carry on if you have one you cannot update yet.';
                legacy.style.display = '';
            } else {
                legacy.style.display = 'none';
            }

            goBtn.disabled = !ready;
        };
        select.addEventListener('change', sync);

        goBtn.addEventListener('click', () => {
            const v = current();
            const schema = schemaFor(v.version, schemas);
            if (!schema || schema.generatorReady === false) return;
            launchBundle({
                mount,
                configVersion: schema.config,
                pluginVersion: v.version,
                beta: v.beta,
                proxyUrl: (registry.proxyUrl || '').trim(),
                backToVersions: () => render(registry, schemas, versions),
            });
        });

        panelBody.push(
            h('label', { class: 'cfg-label' }, 'Plugin version'),
            select,
            detail,
            legacy,
            notReady,
        );

        // A plain footnote replaces what used to be an opt-in toggle. Someone on a
        // nightly needs to know why their build is absent; they do not need a way
        // to generate a config against a schema that may still change.
        if (versions.some(v => v.beta)) {
            panelBody.push(nightlyFootnote);
        }
        panelBody.push(h('div', { class: 'cfg-actions' }, [goBtn]));

        mount.appendChild(h('div', { class: 'cfg-wrap' }, [h('section', { class: 'cfg-panel' }, panelBody)]));
        sync();
        // There used to be a `V.apply(mount)` here to activate a beta toggle this picker
        // injected. The toggle was replaced by the nightly footnote above and `V` was
        // removed with it -- but the call stayed, referencing an identifier that is
        // declared nowhere. `if (V)` on an undeclared name is a ReferenceError, not
        // false, so every generator page load has thrown here and aborted render() at
        // its last statement. Nothing visible broke, which is exactly why it survived:
        // the picker had already been appended by the line above.
    }

    // minimal styles for the picker; each bundle ships its own full stylesheet
    const style = document.createElement('style');
    style.textContent = `
    #cfg-gen .cfg-wrap { max-width: 740px; margin: 0 auto; }
    #cfg-gen .cfg-panel { background: var(--bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.35rem 1.25rem 1.25rem; margin-bottom: 1.25rem; }
    #cfg-gen .cfg-title { margin: 0 0 .5rem; }
    #cfg-gen .cfg-note { color: var(--muted); }
    #cfg-gen .cfg-note--beta { color: #b45309; }
    html[data-theme="dark"] #cfg-gen .cfg-note--beta { color: #facc15; }
    #cfg-gen .cfg-label { font-weight: 600; margin: .4rem 0 .25rem; display: block; }
    #cfg-gen .cfg-input { width: 100%; max-width: 100%; box-sizing: border-box; background: var(--bg); color: var(--fg); border: 1px solid var(--border); border-radius: 10px; padding: .55rem .6rem; font: inherit; }
    #cfg-gen select.cfg-input--select { appearance: none; background-image: linear-gradient(45deg, transparent 50%, var(--muted) 50%), linear-gradient(135deg, var(--muted) 50%, transparent 50%); background-position: calc(100% - 18px) calc(50% - 3px), calc(100% - 13px) calc(50% - 3px); background-size: 5px 5px, 5px 5px; background-repeat: no-repeat; padding-right: 2.2rem; }
    #cfg-gen .cfg-actions { display: flex; gap: .5rem; margin-top: 1rem; flex-wrap: wrap; }
    #cfg-gen .cfg-btn { border: 1px solid var(--border); background: color-mix(in oklab, var(--fg) 4%, transparent); border-radius: 10px; padding: .5rem .85rem; cursor: pointer; color: var(--fg); font: inherit; }
    #cfg-gen .cfg-btn--primary { background: color-mix(in oklab, var(--accent) 14%, transparent); border: 1px solid color-mix(in oklab, var(--accent) 30%, var(--border)); color: var(--accent-fg); }
    #cfg-gen .cfg-btn[disabled] { opacity: .5; cursor: not-allowed; }
    `;
    document.head.appendChild(style);

    (async () => {
        mount.innerHTML = '<p class="cfg-note">Loading versions…</p>';

        const registry = await fetchJson(REGISTRY_URL);
        if (!registry || !Array.isArray(registry.schemas) || !registry.schemas.length) {
            mount.innerHTML = '<p class="cfg-note">Generator configuration failed to load. Please refresh.</p>';
            return;
        }

        // newest schema first, so schemaFor() can take the first match
        const schemas = registry.schemas
            .filter(s => parseVer(s.since))
            .sort((a, b) => cmpVer(parseVer(b.since), parseVer(a.since)));

        const versionApi = await waitForVersions();
        const releases = versionApi ? versionApi.releases : [];
        const versions = buildVersionList(releases, schemas, versionApi);

        render(registry, schemas, versions);
        document.addEventListener('dl-beta-change', () => render(registry, schemas, versions));
    })();
})();
