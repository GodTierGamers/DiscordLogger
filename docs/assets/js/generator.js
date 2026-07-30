/*  DiscordLogger — config generator LOADER
    ------------------------------------------------------------------
    Small and stable. It:
      1. reads /assets/configs/registry.json   (which config schemas exist)
      2. asks DLVersions (versions.js) which of them are still beta
      3. renders the CONFIG VERSION picker — not a plugin-version picker;
         nightly builds do not create new generators, only new schemas do
      4. injects the chosen schema's SELF-CONTAINED bundle:
             /assets/configs/<vN>/generator.js

    A schema is beta when the first plugin version that ships it hasn't had a
    stable release yet (so it currently exists only in nightlies). That is
    derived automatically — nothing here is hand-flagged, and a schema stops
    being beta the moment its plugin version ships stable.

    THE BUNDLE CONTRACT (frozen — never change once a schema folder ships):
      - Each bundle registers itself on load:
            window.DL_GENERATORS = window.DL_GENERATORS || {};
            window.DL_GENERATORS['v9'] = function launch(ctx) { ... };
      - ctx = {
          mount:          DOM node to render into (bundle owns it entirely),
          configVersion:  e.g. "v9",
          pluginVersions: human string of covered plugin versions, e.g. "2.1.5 – 2.1.6",
          beta:           true when this schema is nightly-only so far,
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

    const parseBase = raw => {
        const m = String(raw || '').trim().match(/^v?(\d+)\.(\d+)\.(\d+)/);
        return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
    };
    const cmpBase = (a, b) => {
        for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
        return 0;
    };

    /* versions.js loads from <head> so it is normally ready first, but never
       depend on script ordering — wait briefly for it, then carry on without
       beta awareness rather than breaking the generator entirely. */
    async function waitForVersions(timeoutMs = 5000) {
        const started = Date.now();
        while (!window.DLVersions) {
            if (Date.now() - started > timeoutMs) return null;
            await new Promise(r => setTimeout(r, 50));
        }
        return window.DLVersions.ready();
    }

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

    /* Human-readable plugin coverage for a schema, derived from real releases:
       "2.1.5 – 2.1.6", "2.1.7 and newer", or just the since-version.
       Only STABLE releases are listed — naming an unreleased version here
       would imply it already shipped. */
    function coverageText(schema, nextSchema, releases) {
        const since = parseBase(schema.since);
        if (!since) return '';
        const nextSince = nextSchema ? parseBase(nextSchema.since) : null;

        const inRange = (releases || [])
            .filter(r => !r.prerelease)
            .filter(r => {
                const b = parseBase(r.version);
                if (!b) return false;
                if (cmpBase(b, since) < 0) return false;
                if (nextSince && cmpBase(b, nextSince) >= 0) return false;
                return true;
            })
            .map(r => r.version);

        const uniq = [...new Set(inRange)].sort((a, b) => cmpBase(parseBase(a), parseBase(b)));
        if (!uniq.length) return `plugin ${schema.since} and newer`;
        if (!nextSchema) return uniq.length === 1 ? `plugin ${uniq[0]} and newer` : `plugin ${uniq[0]} – ${uniq[uniq.length - 1]} and newer`;
        return uniq.length === 1 ? `plugin ${uniq[0]}` : `plugin ${uniq[0]} – ${uniq[uniq.length - 1]}`;
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

    function render(registry, entries) {
        const V = window.DLVersions;
        const showBeta = !!(V && V.showBeta);
        const visible = entries.filter(e => !e.beta || showBeta);

        mount.innerHTML = '';

        const select = h('select', { class: 'cfg-input cfg-input--select' });
        visible.forEach((e, i) => {
            const label = `${e.schema.config.toUpperCase()} — ${e.coverage}${e.beta ? '  (BETA)' : ''}`;
            select.appendChild(h('option', { value: String(i) }, label));
        });

        const detail = h('p', { class: 'cfg-note' });
        const betaWarn = h('p', { class: 'cfg-note cfg-note--beta' },
            '⚠️ This config version only exists in nightly builds so far. It may change before it ships in a stable release.');

        const sync = () => {
            const e = visible[Number(select.value)] || visible[0];
            if (!e) return;
            detail.textContent = `Config schema ${e.schema.config.toUpperCase()} · used by ${e.coverage}.`;
            betaWarn.style.display = e.beta ? '' : 'none';
        };
        select.addEventListener('change', sync);

        const goBtn = h('button', { class: 'cfg-btn cfg-btn--primary', type: 'button' }, 'Continue');
        goBtn.addEventListener('click', () => {
            const e = visible[Number(select.value)] || visible[0];
            if (!e) return;
            launchBundle({
                mount,
                configVersion: e.schema.config,
                pluginVersions: e.coverage,
                beta: e.beta,
                proxyUrl: (registry.proxyUrl || '').trim(),
                backToVersions: () => render(registry, entries),
            });
        });

        const hiddenBeta = entries.filter(e => e.beta).length && !showBeta;
        const body = [
            h('h2', { class: 'cfg-title' }, '1) Config version'),
            h('p', { class: 'cfg-note' }, 'Pick the config version your plugin build uses. Not sure? Check the last line of your existing config.yml, or match the plugin version shown below.'),
            h('label', { class: 'cfg-label' }, 'Config version'),
            select,
            detail,
            betaWarn,
        ];
        if (hiddenBeta) {
            body.push(h('p', { class: 'cfg-note' },
                'A newer config version exists in nightly builds. Enable beta content below to generate for it.'));
            body.push(h('div', { 'data-dl-beta-toggle': 'Beta config versions only exist in nightly builds and may change before release.' }));
        }
        body.push(h('div', { class: 'cfg-actions' }, [goBtn]));

        mount.appendChild(h('div', { class: 'cfg-wrap' }, [h('section', { class: 'cfg-panel' }, body)]));
        sync();
        // render the beta toggle if we just injected one
        if (V) V.apply(mount);
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
    #cfg-gen .cfg-nightly-note { margin-top: .5rem; }
    `;
    document.head.appendChild(style);

    (async () => {
        mount.innerHTML = '<p class="cfg-note">Loading config versions…</p>';

        const registry = await fetchJson(REGISTRY_URL);
        if (!registry || !Array.isArray(registry.schemas) || !registry.schemas.length) {
            mount.innerHTML = '<p class="cfg-note">Generator configuration failed to load. Please refresh.</p>';
            return;
        }

        // newest schema first
        const schemas = registry.schemas
            .filter(s => parseBase(s.since))
            .sort((a, b) => cmpBase(parseBase(b.since), parseBase(a.since)));

        const versionApi = await waitForVersions();
        const releases = versionApi ? versionApi.releases : [];

        const entries = schemas.map((schema, i) => {
            // schemas are newest-first, so the "next" (newer) schema is at i-1
            const newer = schemas[i - 1] || null;
            return {
                schema,
                coverage: coverageText(schema, newer, releases),
                beta: versionApi ? versionApi.isBeta(schema.since) : false,
            };
        });

        render(registry, entries);
        document.addEventListener('dl-beta-change', () => render(registry, entries));
    })();
})();
