/*  DiscordLogger — shared version awareness for the whole site
    ------------------------------------------------------------------
    One source of truth so version numbers are never hand-maintained across
    pages. Everything is derived at runtime from the GitHub releases API:

      newestStable   the latest non-prerelease release
      isBeta(v)      true when v is newer than newestStable — i.e. it exists
                     only in nightly builds and hasn't shipped stable yet
      showBeta       user opt-in (persisted); beta content stays hidden until
                     the visitor asks for it

    DECLARATIVE MARKUP (no per-page JS needed):

      <span data-dl-version="1.2.3">1.2.3</span>
          -> gets a BETA badge appended automatically while 1.2.3 is nightly-only

      <div data-dl-beta-only="1.2.3"> … </div>
          -> hidden entirely unless that version is beta AND the visitor
             enabled beta content (shown normally once it ships stable)

      <span data-dl-schema-versions="v10"></span>
                                            -> filled with the stable releases that
                                               actually ship that config schema
      <span data-dl-latest></span>          -> filled with the newest stable version
      <span data-dl-latest-nightly></span>  -> filled with the newest nightly version

      <div data-dl-beta-toggle></div>
          -> replaced with the "show beta/nightly content" checkbox

    Pages that need to react to the toggle can listen:
      document.addEventListener('dl-beta-change', e => e.detail.showBeta)
*/
(() => {
    'use strict';

    const RELEASES_API = 'https://api.github.com/repos/GodTierGamers/DiscordLogger/releases?per_page=100';
    const CACHE_KEY = 'dl-releases-cache-v1';
    const CACHE_TTL_MS = 10 * 60 * 1000;
    const BETA_KEY = 'dl-show-beta';

    /* ---- semver-ish parsing (understands -BETA.N) ---- */

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
    const cmpVer = (a, b) => {
        const c = cmpBase(a.base, b.base);
        if (c !== 0) return c;
        if ((a.beta === null) !== (b.beta === null)) return a.beta === null ? 1 : -1;
        return (a.beta || 0) - (b.beta || 0);
    };
    const isNightlyTag = tag => /-BETA\.\d+$/i.test(String(tag || ''));

    /* ---- release data (cached per session to stay well inside API limits) ---- */

    async function loadReleases() {
        try {
            const raw = sessionStorage.getItem(CACHE_KEY);
            if (raw) {
                const cached = JSON.parse(raw);
                if (cached && Date.now() - cached.at < CACHE_TTL_MS && Array.isArray(cached.list)) {
                    return cached.list;
                }
            }
        } catch (e) { /* ignore unusable cache */ }

        let list = [];
        try {
            const ctl = new AbortController();
            const timer = setTimeout(() => ctl.abort(), 8000);
            const res = await fetch(RELEASES_API, { signal: ctl.signal });
            clearTimeout(timer);
            if (res.ok) {
                const data = await res.json();
                list = (data || [])
                    .map(r => ({
                        version: String(r.tag_name || '').replace(/^v/i, ''),
                        prerelease: !!r.prerelease || isNightlyTag(r.tag_name),
                        url: r.html_url,
                    }))
                    .filter(r => parseVer(r.version));
            }
        } catch (e) { /* offline / rate-limited -> empty, handled by callers */ }

        try {
            sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), list }));
        } catch (e) { /* storage full or blocked */ }

        return list;
    }

    /* ---- beta opt-in state ---- */

    const readShowBeta = () => {
        try { return localStorage.getItem(BETA_KEY) === '1'; } catch (e) { return false; }
    };
    const writeShowBeta = on => {
        try { localStorage.setItem(BETA_KEY, on ? '1' : '0'); } catch (e) { /* ignore */ }
    };

    /* ---- public API ---- */

    const api = {
        releases: [],
        newestStable: null,
        newestNightly: null,
        showBeta: readShowBeta(),

        /** True when this version exists only in nightlies (newer than newest stable). */
        isBeta(version) {
            const v = parseVer(version);
            if (!v) return false;
            if (v.beta !== null) return true;                 // an explicit -BETA.N build
            if (!this.newestStable) return false;             // unknown -> don't cry beta
            return cmpBase(v.base, parseVer(this.newestStable).base) > 0;
        },

        setShowBeta(on) {
            this.showBeta = !!on;
            writeShowBeta(this.showBeta);
            applyAll();
            document.dispatchEvent(new CustomEvent('dl-beta-change', { detail: { showBeta: this.showBeta } }));
        },

        badge() {
            const b = document.createElement('span');
            b.className = 'dl-badge dl-badge--nightly';
            b.textContent = 'BETA';
            b.title = 'Only available in nightly builds — not in a stable release yet';
            return b;
        },

        /**
         * Build an on/off slider switch. Shared so every beta/nightly toggle on the
         * site looks and behaves identically.
         * @param {boolean} checked initial state
         * @param {(on: boolean) => void} onChange
         */
        switchEl(checked, onChange) {
            const wrap = document.createElement('span');
            wrap.className = 'dl-switch';

            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = !!checked;
            input.setAttribute('role', 'switch');
            input.setAttribute('aria-checked', String(!!checked));
            input.addEventListener('change', () => {
                input.setAttribute('aria-checked', String(input.checked));
                if (onChange) onChange(input.checked);
            });

            const track = document.createElement('span');
            track.className = 'dl-switch__track';
            const thumb = document.createElement('span');
            thumb.className = 'dl-switch__thumb';
            track.appendChild(thumb);

            wrap.append(input, track);
            return wrap;
        },

        /** Re-scan a subtree — call after injecting markup that uses the data-dl-* hooks. */
        apply(root) {
            applyAll(root || document);
        },
    };

    /* ---- declarative application ---- */

    function applyVersionBadges(root) {
        root.querySelectorAll('[data-dl-version]').forEach(el => {
            const v = el.getAttribute('data-dl-version');
            const existing = el.querySelector(':scope > .dl-badge--nightly');
            const beta = api.isBeta(v);
            if (beta && !existing) el.appendChild(api.badge());
            else if (!beta && existing) existing.remove();
        });
    }

    function applyBetaOnly(root) {
        root.querySelectorAll('[data-dl-beta-only]').forEach(el => {
            const v = el.getAttribute('data-dl-beta-only');
            // Once the version ships stable it's normal content and always visible.
            const gated = api.isBeta(v);
            el.hidden = gated && !api.showBeta;
        });
    }

    function applyFills(root) {
        if (api.newestStable) {
            root.querySelectorAll('[data-dl-latest]').forEach(el => {
                el.textContent = api.newestStable;
            });
        }
        if (api.newestNightly) {
            root.querySelectorAll('[data-dl-latest-nightly]').forEach(el => {
                el.textContent = api.newestNightly;
            });
        }
    }

    /**
     * Fills an element with the stable releases that actually ship a given config
     * schema, e.g. "v2.1.5, v2.1.6".
     *
     * <p>Deliberately never says "and newer": a schema is only known to be shipped
     * by the releases that have shipped it. The next release may open a new schema,
     * so claiming future coverage would eventually be a lie on a page nobody
     * revisits.
     *
     * <p>When nothing has shipped it yet, it names the schema's own `since` — the
     * one build the registry says will carry it. That is a single declared version
     * rather than an open-ended promise, so the rule above still holds, and it keeps
     * a finished schema's page readable before release day instead of leading with
     * an apology.
     */
    async function applySchemaCoverage(root) {
        const hosts = root.querySelectorAll('[data-dl-schema-versions]');
        if (!hosts.length) return;

        let schemas;
        try {
            const res = await fetch('/assets/configs/registry.json');
            if (!res.ok) return;
            schemas = (await res.json()).schemas || [];
        } catch { return; }

        schemas = schemas
            .filter(s => parseVer(s.since))
            .sort((a, b) => cmpVer(parseVer(a.since), parseVer(b.since)));

        hosts.forEach(el => {
            const want = el.getAttribute('data-dl-schema-versions');
            const idx = schemas.findIndex(s => s.config === want);
            if (idx < 0) return;

            const since = parseVer(schemas[idx].since);
            const nextSince = schemas[idx + 1] ? parseVer(schemas[idx + 1].since) : null;

            const hits = (api.releases || [])
                .filter(r => !r.prerelease)
                .filter(r => {
                    const v = parseVer(r.version);
                    if (!v) return false;
                    if (cmpVer(v, since) < 0) return false;
                    if (nextSince && cmpVer(v, nextSince) >= 0) return false;
                    return true;
                })
                .map(r => r.version);

            const uniq = [...new Set(hits)].sort((a, b) => cmpVer(parseVer(a), parseVer(b)));
            el.textContent = uniq.length
                ? uniq.map(v => 'v' + v).join(', ')
                : 'v' + String(schemas[idx].since).replace(/^v/i, '');
        });
    }

    function renderToggles(root) {
        root.querySelectorAll('[data-dl-beta-toggle]').forEach(host => {
            if (host.dataset.dlToggleReady === '1') {
                const cb = host.querySelector('input[type=checkbox]');
                if (cb) {
                    cb.checked = api.showBeta;
                    cb.setAttribute('aria-checked', String(api.showBeta));
                }
                return;
            }
            host.dataset.dlToggleReady = '1';
            host.innerHTML = '';
            host.classList.add('dl-nightly-controls');

            const label = document.createElement('label');
            label.className = 'dl-nightly-toggle';
            label.append(api.switchEl(api.showBeta, on => api.setShowBeta(on)));

            const text = document.createElement('span');
            text.append('Show beta content ');
            text.appendChild(api.badge());
            label.append(text);

            const note = document.createElement('p');
            note.className = 'dl-nightly-warning';
            note.textContent = host.getAttribute('data-dl-beta-toggle')
                || 'Beta content documents features that only exist in nightly builds and may change before release.';

            host.append(label, note);
        });
    }

    function applyAll(root = document) {
        applyVersionBadges(root);
        applyBetaOnly(root);
        applyFills(root);
        renderToggles(root);
        applySchemaCoverage(root);
    }

    /* ---- boot ---- */

    let resolveReady;
    const readyPromise = new Promise(r => { resolveReady = r; });
    api.ready = () => readyPromise;

    (async () => {
        const list = await loadReleases();
        api.releases = list;

        const stables = list.filter(r => !r.prerelease).sort((a, b) => cmpVer(parseVer(b.version), parseVer(a.version)));
        const nightlies = list.filter(r => r.prerelease).sort((a, b) => cmpVer(parseVer(b.version), parseVer(a.version)));
        api.newestStable = stables.length ? stables[0].version : null;
        api.newestNightly = nightlies.length ? nightlies[0].version : null;

        applyAll();
        resolveReady(api);
    })();

    window.DLVersions = api;
})();
