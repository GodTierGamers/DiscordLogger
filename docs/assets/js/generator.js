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

        // de-dupe identical tags, keeping the stabler entry
        const byVersion = new Map();
        list.forEach(e => {
            const prev = byVersion.get(e.version);
            if (!prev || (prev.beta && !e.beta)) byVersion.set(e.version, e);
        });

        return [...byVersion.values()].sort((a, b) => cmpVer(parseVer(b.version), parseVer(a.version)));
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
        const V = window.DLVersions;
        const showBeta = !!(V && V.showBeta);
        const visible = versions.filter(v => !v.beta || showBeta);

        mount.innerHTML = '';

        const panelBody = [
            h('h2', { class: 'cfg-title' }, '1) Plugin version'),
            h('p', { class: 'cfg-note' }, 'Pick the DiscordLogger version you downloaded — the matching config version is detected for you.'),
        ];

        if (!visible.length) {
            panelBody.push(h('p', { class: 'cfg-note' },
                'No supported versions found. Please refresh, or take a ready-made config from the config docs.'));
            mount.appendChild(h('div', { class: 'cfg-wrap' }, [h('section', { class: 'cfg-panel' }, panelBody)]));
            return;
        }

        const select = h('select', { class: 'cfg-input cfg-input--select' });
        visible.forEach((v, i) => {
            select.appendChild(h('option', { value: String(i) }, v.beta ? `${v.version}  (BETA)` : v.version));
        });
        // default to the newest stable build, never a nightly
        const firstStable = visible.findIndex(v => !v.beta);
        select.value = String(firstStable >= 0 ? firstStable : 0);

        const detail = h('p', { class: 'cfg-note' });
        const betaWarn = h('p', { class: 'cfg-note cfg-note--beta' },
            '⚠️ Nightly build — its config format may still change before it ships in a stable release.');
        const notReady = h('p', { class: 'cfg-note cfg-note--beta' });
        const goBtn = h('button', { class: 'cfg-btn cfg-btn--primary', type: 'button' }, 'Continue');

        const current = () => visible[Number(select.value)] || visible[0];

        const sync = () => {
            const v = current();
            const schema = schemaFor(v.version, schemas);
            const ready = schema && schema.generatorReady !== false;

            detail.textContent = schema
                ? `Uses config schema ${schema.config.toUpperCase()} — detected automatically.`
                : 'No generator is available for that version.';
            betaWarn.style.display = v.beta ? '' : 'none';

            if (schema && !ready) {
                notReady.textContent = `The generator for config ${schema.config.toUpperCase()} isn't available yet — this version is listed for reference only.`;
                notReady.style.display = '';
            } else {
                notReady.style.display = 'none';
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
            betaWarn,
            notReady,
        );

        // The toggle stays visible in BOTH states whenever nightlies exist —
        // otherwise turning it on would remove the only way to turn it back off.
        if (versions.some(v => v.beta)) {
            if (!showBeta) {
                panelBody.push(h('p', { class: 'cfg-note' },
                    'Running a nightly build? Enable beta versions below to generate a config for it.'));
            }
            panelBody.push(h('div', { 'data-dl-beta-toggle': 'Nightly builds are previews of unreleased work — their config format may change before release.' }));
        }
        panelBody.push(h('div', { class: 'cfg-actions' }, [goBtn]));

        mount.appendChild(h('div', { class: 'cfg-wrap' }, [h('section', { class: 'cfg-panel' }, panelBody)]));
        sync();
        if (V) V.apply(mount);   // renders the beta toggle if we just injected one
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
