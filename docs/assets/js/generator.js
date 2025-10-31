/* DiscordLogger – config.yml generator
   Source-of-truth files:
   - generator.config.js  → versions, proxy, webhook test payload
   - docs/assets/configs/<configVersion>/options.json
   - docs/assets/configs/<configVersion>/config.template.yml
   This file should not hard-code versions, toggles, or colors.
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    /* ------------------------------------------------------------
     * 1. REQUIRED GLOBALS (from generator.config.js)
     * ------------------------------------------------------------ */
    if (!window.DL_VERSIONS || !Object.keys(window.DL_VERSIONS).length) {
        mount.innerHTML = `<p style="color:red">DiscordLogger generator: window.DL_VERSIONS is missing. Make sure generator.config.js is loaded before generator.js.</p>`;
        return;
    }
    const VERS = window.DL_VERSIONS;
    const PROXY_URL = (window.DL_PROXY_URL || '').trim();
    const TEST_EMBED = window.DL_TEST_EMBED || null;

    /* ------------------------------------------------------------
     * 2. UTILS
     * ------------------------------------------------------------ */
    const $  = (s, el = document) => el.querySelector(s);
    const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));
    const el = (tag, attrs = {}, kids = []) => {
        const n = document.createElement(tag);
        for (const [k, v] of Object.entries(attrs)) {
            if (k === 'class') n.className = v;
            else if (k === 'html') n.innerHTML = v;
            else n.setAttribute(k, v);
        }
        (Array.isArray(kids) ? kids : [kids]).forEach(k => {
            if (k == null) return;
            n.appendChild(typeof k === 'string' ? document.createTextNode(k) : k);
        });
        return n;
    };
    const pad2 = (x) => String(x).padStart(2, '0');
    const isoNice = () => {
        const d = new Date();
        return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
    };
    const sortVersionsDesc = (a, b) => {
        const pa = a.split('.').map(n => parseInt(n, 10) || 0);
        const pb = b.split('.').map(n => parseInt(n, 10) || 0);
        const len = Math.max(pa.length, pb.length);
        for (let i = 0; i < len; i++) {
            const va = pa[i] || 0;
            const vb = pb[i] || 0;
            if (va !== vb) return vb - va;
        }
        return 0;
    };
    const isDiscordWebhook = (u) => /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+/i.test((u || '').trim());
    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;

    // derive "/docs/assets/configs/v9/" from e.g. "/docs/assets/configs/v9/config.yml"
    const baseFromDownloadUrl = (url) => {
        const idx = url.lastIndexOf('/');
        if (idx === -1) return url;
        return url.slice(0, idx + 1);
    };

    // tiny token replacer: {{FOO}}, {{ LOG_player.join }}
    const replaceTokens = (tpl, tokenMap) => {
        return tpl.replace(/{{\s*([A-Za-z0-9._-]+)\s*}}/g, (m, name) => {
            return Object.prototype.hasOwnProperty.call(tokenMap, name) ? tokenMap[name] : m;
        });
    };

    /* ------------------------------------------------------------
     * 3. STATE (no hardcoded versions here)
     * ------------------------------------------------------------ */
    const pluginOrder = Object.keys(VERS).sort(sortVersionsDesc);   // newest first
    const DEFAULT_PLUGIN = pluginOrder[0];

    // per-configVersion cache so if 2.1.5 and 2.1.6 both use v9, we only fetch once
    const cfgCache = new Map(); // key = configVersion, val = { options, template }

    const state = {
        currentPanel: '1',
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: VERS[DEFAULT_PLUGIN].configVersion,
        webhookUrl: '',
        webhookConfirmed: false,
        embedsEnabled: true,
        embedAuthor: 'Server Logs',
        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',
        nicknames: true,
        options: null,
        template: null,
        toggles: {},
        colors: {},
        loading: false
    };

    /* ------------------------------------------------------------
     * 4. LOADERS (from /docs/assets/configs/<ver>/...)
     * ------------------------------------------------------------ */
    async function ensureConfigLoadedFor(pluginVer) {
        const meta = VERS[pluginVer];
        const cfgVer = meta.configVersion;
        state.configVersion = cfgVer;

        if (cfgCache.has(cfgVer)) {
            const { options, template } = cfgCache.get(cfgVer);
            state.options = options;
            state.template = template;
            // derive toggles/colors from options
            hydrateFromOptions(options);
            return;
        }

        const base = baseFromDownloadUrl(meta.downloadUrl || `/assets/configs/${cfgVer}/config.yml`);
        const optUrl = `${base}options.json`;
        const tplUrl = `${base}config.template.yml`;

        state.loading = true;
        try {
            const [optRes, tplRes] = await Promise.all([
                fetch(optUrl),
                fetch(tplUrl),
            ]);
            const options = optRes.ok ? await optRes.json() : null;
            const template = tplRes.ok ? await tplRes.text() : null;
            cfgCache.set(cfgVer, { options, template });
            state.options = options;
            state.template = template;
            hydrateFromOptions(options);
        } finally {
            state.loading = false;
        }
    }

    function hydrateFromOptions(options) {
        // If options is missing, keep current toggles/colors (page will still generate the template)
        if (!options || !Array.isArray(options.categories)) return;
        const toggles = {};
        const colors = {};
        for (const cat of options.categories) {
            for (const item of (cat.items || [])) {
                const k = item.configKey ? item.configKey.replace(/^log\./, '') : null;
                if (k) toggles[k] = item.default !== undefined ? !!item.default : true;
                if (item.colorKey) {
                    colors[item.colorKey] = item.defaultColor || '';
                }
            }
        }
        state.toggles = toggles;
        state.colors = colors;
    }

    /* ------------------------------------------------------------
     * 5. LAYOUT
     * ------------------------------------------------------------ */
    mount.innerHTML = '';
    const wrapper = el('div', { class: 'cfg-wrap' });
    mount.appendChild(wrapper);

    const makePanel = (id, title, body) => el('section', { class: 'cfg-panel', 'data-panel': id }, [
        el('h2', { class: 'cfg-title' }, title),
        ...(Array.isArray(body) ? body : [body]),
    ]);

    const showPanel = (id) => {
        state.currentPanel = id;
        $$('.cfg-panel', wrapper).forEach(p => {
            p.style.display = (p.dataset.panel === id ? 'block' : 'none');
        });
    };

    /* --- Panel 1: version --- */
    const versionSelect = el('select', { class: 'cfg-input cfg-input--select' });
    pluginOrder.forEach(v => {
        versionSelect.appendChild(el('option', {
            value: v,
            ...(v === state.pluginVersion ? { selected: '' } : {})
        }, v));
    });
    const versionNote = el('p', { class: 'cfg-note' }, `Config schema: ${state.configVersion}`);

    const p1 = makePanel('1', '1) Plugin version', [
        el('p', { class: 'cfg-note' }, 'Pick the DiscordLogger version you installed. Newest at the top.'),
        el('label', { class: 'cfg-label' }, 'Plugin version'),
        versionSelect,
        versionNote,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p1next' }, 'Next')
        ])
    ]);

    /* --- Panel 2: webhook --- */
    const webhookInput = el('input', { class: 'cfg-input', type: 'url', placeholder: 'https://discord.com/api/webhooks/…' });
    const webhookStatus = el('div', { class: 'cfg-status', style: 'display:none' });
    const webhookTestBtn = el('button', { class: 'cfg-btn', type: 'button' }, 'Send test');
    const webhookConfirm = el('div', { class: 'cfg-confirm', style: 'display:none' }, [
        el('p', { class: 'cfg-note' }, 'Did the test message appear in your Discord channel?'),
        el('div', { class: 'cfg-actions-inline' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p2yes' }, 'Yes'),
            el('button', { class: 'cfg-btn cfg-btn--ghost', type: 'button', id: 'p2no' }, 'No, try again')
        ])
    ]);
    const p2next = el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p2next', disabled: '' }, 'Next');

    const p2 = makePanel('2', '2) Discord webhook', [
        el('p', { class: 'cfg-note' }, 'Paste your Discord channel webhook URL.'),
        el('label', { class: 'cfg-label' }, 'Webhook URL'),
        webhookInput,
        webhookTestBtn,
        webhookStatus,
        webhookConfirm,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p2back' }, 'Back'),
            p2next
        ])
    ]);

    /* --- Panel 3: log style (embeds/plain) + nicknames --- */
    const radioEmbeds = el('label', { class: 'cfg-radio' }, [
        el('input', { type: 'radio', name: 'logstyle', value: 'embeds', checked: '' }),
        el('span', {}, 'Use embeds (recommended)')
    ]);
    const embedsExtra = el('div', { class: 'cfg-inline' }, [
        el('label', { class: 'cfg-label', for: 'embedAuthor' }, 'Author name (shown in embed header)'),
        el('input', { id: 'embedAuthor', class: 'cfg-input', type: 'text', value: state.embedAuthor })
    ]);

    const radioPlain = el('label', { class: 'cfg-radio' }, [
        el('input', { type: 'radio', name: 'logstyle', value: 'plain' }),
        el('span', {}, 'Use plain text')
    ]);

    const TIME_PRESETS = [
        { label: 'Default (same as plugin)', value: '[HH:mm:ss, dd:MM:yyyy]' },
        { label: 'EU 24h (dd/MM/yyyy HH:mm)', value: '[dd/MM/yyyy HH:mm]' },
        { label: 'US 12h (MM/dd/yyyy HH:mm)', value: '[MM/dd/yyyy HH:mm]' },
        { label: 'Time only (HH:mm:ss)', value: '[HH:mm:ss]' },
        { label: 'Custom…', value: '' },
    ];
    const dtPresetSel = el('select', { class: 'cfg-input cfg-input--select', id: 'dtPreset' });
    TIME_PRESETS.forEach(p => dtPresetSel.appendChild(el('option', { value: p.value }, p.label)));
    const dtCustomWrap = el('div', { class: 'cfg-inline', style: 'margin-top:.4rem;' }, [
        el('label', { class: 'cfg-label', for: 'plainFmt' }, 'Custom pattern'),
        el('input', { id: 'plainFmt', class: 'cfg-input', type: 'text', value: state.plainTimeFmt }),
        el('p', { class: 'cfg-note', id: 'fmtPrev' }, 'Preview: ')
    ]);
    const plainExtra = el('div', { class: 'cfg-plain', style: 'display:none' }, [
        el('label', { class: 'cfg-label' }, 'Server name (optional)'),
        el('input', { id: 'plainName', class: 'cfg-input', type: 'text', value: state.plainServerName, placeholder: 'e.g. Survival, Creative' }),
        el('p', { class: 'cfg-note' }, 'Useful if you run multiple servers behind a proxy.'),
        el('label', { class: 'cfg-label' }, 'Timestamp format'),
        el('p', { class: 'cfg-note' }, 'Pick a preset or select “Custom…” to type your own.'),
        dtPresetSel,
        dtCustomWrap
    ]);

    const nicknamesRow = el('label', { class: 'cfg-check', style: 'margin-top:.7rem;' }, [
        el('input', { type: 'checkbox', id: 'nickToggle', checked: '' }),
        el('span', {}, 'Show player nicknames as "Nickname (RealName)"')
    ]);

    const p3 = makePanel('3', '3) Log Style', [
        el('p', { class: 'cfg-note' }, 'Choose how DiscordLogger should send logs.'),
        radioEmbeds,
        embedsExtra,
        radioPlain,
        plainExtra,
        nicknamesRow,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p3back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p3next' }, 'Next')
        ])
    ]);

    /* --- Panel 4: log options (from options.json) --- */
    const p4List = el('div', { class: 'cfg-loglist' });
    const p4 = makePanel('4', '4) What do you want to log?', [
        el('p', { class: 'cfg-note' }, 'Toggle the log types you want.'),
        p4List,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p4back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p4next' }, 'Next')
        ])
    ]);

    /* --- Panel 5: colors (from options.json) --- */
    const colorsWrap = el('div', { class: 'cfg-colwrap' });
    const p5 = makePanel('5', '5) Embed colors', [
        el('p', { class: 'cfg-note' }, 'Only used if you selected embeds.'),
        colorsWrap,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p5back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p5next' }, 'Generate')
        ])
    ]);

    /* --- Panel 6: result --- */
    const yamlOut = el('textarea', { class: 'cfg-yaml', readonly: '' });
    yamlOut.setAttribute('wrap', 'off');
    const p6 = makePanel('6', 'Your config.yml', [
        yamlOut,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'copyYaml' }, 'Copy'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'downloadYaml' }, 'Download')
        ])
    ]);

    wrapper.append(p1, p2, p3, p4, p5, p6);
    showPanel('1');

    /* ------------------------------------------------------------
     * 6. RENDERERS
     * ------------------------------------------------------------ */
    function renderLogOptions() {
        p4List.innerHTML = '';
        if (!state.options || !Array.isArray(state.options.categories)) {
            // public-facing message, no file paths
            p4List.appendChild(el('p', { class: 'cfg-note' }, 'This version has no configurable log entries.'));
            return;
        }
        for (const cat of state.options.categories) {
            p4List.appendChild(el('h3', { class: 'cfg-sub' }, cat.label || cat.id));
            if (cat.description) {
                p4List.appendChild(el('p', { class: 'cfg-note' }, cat.description));
            }
            for (const item of (cat.items || [])) {
                const k = item.configKey ? item.configKey.replace(/^log\./, '') : null;
                if (!k) continue;
                const cb = el('input', {
                    type: 'checkbox',
                    'data-key': k,
                    ...(state.toggles[k] ? { checked: '' } : {})
                });
                const wrap = el('label', { class: 'cfg-check' }, [
                    cb,
                    ' ',
                    item.label || k
                ]);
                p4List.appendChild(wrap);
                cb.addEventListener('change', () => {
                    state.toggles[k] = cb.checked;
                });
            }
        }
    }

    function renderColors() {
        colorsWrap.innerHTML = '';
        if (!state.options || !Array.isArray(state.options.categories)) return;

        const grouped = {};
        for (const cat of state.options.categories) {
            for (const item of (cat.items || [])) {
                if (!item.colorKey) continue;
                const [grp, sub] = item.colorKey.split('.', 2);
                if (!grouped[grp]) grouped[grp] = [];
                grouped[grp].push({
                    fullKey: item.colorKey,
                    label: item.label || sub,
                    value: state.colors[item.colorKey] || '#5865F2'
                });
            }
        }

        for (const group of Object.keys(grouped)) {
            const box = el('div', { class: 'cfg-colgroup' }, [
                el('h3', { class: 'cfg-sub' }, group.charAt(0).toUpperCase() + group.slice(1))
            ]);
            grouped[group].forEach(item => {
                const row = el('div', { class: 'cfg-colrow' }, [
                    el('label', { class: 'cfg-label' }, item.label),
                    el('div', { class: 'cfg-colinputs' }, [
                        el('input', { type: 'color', value: item.value, 'data-key': item.fullKey }),
                        el('input', { type: 'text', class: 'cfg-input cfg-input--tiny', value: item.value, 'data-key': item.fullKey })
                    ])
                ]);
                box.appendChild(row);
            });
            colorsWrap.appendChild(box);
        }

        $$('input[type=color]', colorsWrap).forEach(inp => {
            inp.addEventListener('input', () => {
                const k = inp.dataset.key;
                state.colors[k] = inp.value;
                const txt = colorsWrap.querySelector(`input[type=text][data-key="${k}"]`);
                if (txt) txt.value = inp.value;
            });
        });
        $$('input[type=text].cfg-input--tiny', colorsWrap).forEach(inp => {
            inp.addEventListener('input', () => {
                let v = inp.value.trim();
                if (!v.startsWith('#')) v = '#' + v;
                if (/^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(v)) {
                    const k = inp.dataset.key;
                    state.colors[k] = v;
                    const col = colorsWrap.querySelector(`input[type=color][data-key="${k}"]`);
                    if (col) col.value = v;
                }
            });
        });
    }

    /* ------------------------------------------------------------
     * 7. WEBHOOK HANDLERS
     * ------------------------------------------------------------ */
    const setWebhookStatus = (txt, cls = '') => {
        webhookStatus.textContent = txt;
        webhookStatus.className = `cfg-status ${cls}`;
        webhookStatus.style.display = txt ? '' : 'none';
    };
    const validateWebhookNext = () => {
        const ok = isDiscordWebhook(state.webhookUrl) && state.webhookConfirmed;
        p2next.disabled = !ok;
    };

    webhookInput.addEventListener('input', () => {
        state.webhookUrl = webhookInput.value.trim();
        state.webhookConfirmed = false;
        webhookConfirm.style.display = 'none';
        setWebhookStatus('', '');
        validateWebhookNext();
    });

    async function sendWebhookTest(url, payload) {
        if (PROXY_URL) {
            const res = await fetch(PROXY_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: withWait(url), payload })
            });
            return { ok: res.ok };
        }
        try {
            const res = await fetch(withWait(url), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            return { ok: res.ok };
        } catch {
            return { ok: false };
        }
    }

    webhookTestBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setWebhookStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }

        setWebhookStatus('Sending test…', 'is-info');
        webhookTestBtn.disabled = true;
        webhookConfirm.style.display = 'none';

        // test payload is defined in generator.config.js
        const payload = JSON.parse(JSON.stringify(TEST_EMBED || {}));
        if (payload && payload.embeds && payload.embeds[0]) {
            payload.embeds[0].timestamp = new Date().toISOString();
        }

        const res = await sendWebhookTest(url, payload);
        webhookTestBtn.disabled = false;

        if (res.ok) {
            setWebhookStatus('Test message sent. Check Discord.', 'is-ok');
        } else {
            setWebhookStatus("Couldn't send webhook, please check your webhook URL", 'is-error');
        }

        webhookConfirm.style.display = '';
    });

    $('#p2yes').addEventListener('click', () => {
        state.webhookConfirmed = true;
        setWebhookStatus('Webhook confirmed.', 'is-ok');
        validateWebhookNext();
    });
    $('#p2no').addEventListener('click', () => {
        state.webhookConfirmed = false;
        setWebhookStatus('Check your URL and try again.', 'is-info');
        validateWebhookNext();
    });

    /* ------------------------------------------------------------
     * 8. LOG STYLE HANDLERS
     * ------------------------------------------------------------ */
    const dtInput = $('#plainFmt');
    const dtPrev  = $('#fmtPrev');
    const plainNameInput = $('#plainName');
    const embedAuthorInput = $('#embedAuthor');

    function updateLogStyleUI() {
        const useEmbeds = $('input[name="logstyle"]:checked').value === 'embeds';
        state.embedsEnabled = useEmbeds;
        embedsExtra.style.display = useEmbeds ? '' : 'none';
        plainExtra.style.display = useEmbeds ? 'none' : '';
    }
    $$('input[name="logstyle"]').forEach(r => r.addEventListener('change', updateLogStyleUI));

    function updateFmtPreview() {
        const d = new Date();
        const map = {
            'yyyy': d.getFullYear(),
            'HH': pad2(d.getHours()),
            'mm': pad2(d.getMinutes()),
            'ss': pad2(d.getSeconds()),
            'dd': pad2(d.getDate()),
            'MM': pad2(d.getMonth() + 1),
        };
        let s = dtInput.value;
        s = s.replace(/yyyy|HH|MM|dd|mm|ss/g, m => map[m] ?? m);
        dtPrev.textContent = 'Preview: ' + s;
        state.plainTimeFmt = dtInput.value;
    }
    dtPresetSel.addEventListener('change', () => {
        const v = dtPresetSel.value;
        if (v) {
            dtInput.value = v;
            dtInput.disabled = true;
        } else {
            dtInput.disabled = false;
            dtInput.focus();
        }
        updateFmtPreview();
    });
    dtInput.addEventListener('input', updateFmtPreview);
    plainNameInput.addEventListener('input', () => {
        state.plainServerName = plainNameInput.value;
    });
    embedAuthorInput.addEventListener('input', () => {
        state.embedAuthor = embedAuthorInput.value;
    });
    $('#nickToggle').addEventListener('change', (e) => {
        state.nicknames = e.target.checked;
    });

    updateLogStyleUI();
    updateFmtPreview();

    /* ------------------------------------------------------------
     * 9. NAVIGATION
     * ------------------------------------------------------------ */
    $('#p1next').addEventListener('click', () => showPanel('2'));
    $('#p2back').addEventListener('click', () => showPanel('1'));
    $('#p2next').addEventListener('click', () => showPanel('3'));
    $('#p3back').addEventListener('click', () => showPanel('2'));
    $('#p3next').addEventListener('click', () => showPanel('4'));
    $('#p4back').addEventListener('click', () => showPanel('3'));
    $('#p4next').addEventListener('click', () => {
        if (state.embedsEnabled) {
            renderColors();
            showPanel('5');
        } else {
            yamlOut.value = buildYamlFromTemplate();
            showPanel('6');
        }
    });
    $('#p5back').addEventListener('click', () => showPanel('4'));
    $('#p5next').addEventListener('click', () => {
        yamlOut.value = buildYamlFromTemplate();
        showPanel('6');
    });

    $('#copyYaml').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(yamlOut.value); } catch {}
    });
    $('#downloadYaml').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([yamlOut.value], { type: 'text/yaml' }));
        document.body.appendChild(a); a.click(); a.remove();
    });

    /* ------------------------------------------------------------
     * 10. YAML BUILDER
     * ------------------------------------------------------------ */
    function buildYamlFromTemplate() {
        let tpl = state.template || '';
        const now = isoNice();

        // build token map
        const tokens = {
            WEBHOOK_URL: state.webhookUrl,
            EMBEDS_ENABLED: state.embedsEnabled ? 'true' : 'false',
            EMBEDS_AUTHOR: state.embedAuthor.replace(/"/g, '\\"'),
            TIME_FORMAT: state.plainTimeFmt,
            PLAIN_NAME: state.plainServerName.replace(/"/g, '\\"'),
            NICKNAMES: state.nicknames ? 'true' : 'false',
            GENERATED_AT: now,
            CONFIG_VERSION: state.configVersion.toUpperCase()
        };

        // add log + colors from options.json
        if (state.options && Array.isArray(state.options.categories)) {
            for (const cat of state.options.categories) {
                for (const item of (cat.items || [])) {
                    const logKey = item.configKey ? item.configKey.replace(/^log\./, '') : null;
                    const colorKey = item.colorKey || null;
                    if (logKey) {
                        tokens[`LOG_${logKey}`] = state.toggles[logKey] ? 'true' : 'false';
                    }
                    if (colorKey) {
                        tokens[`COLOR_${colorKey}`] = state.colors[colorKey] || '#5865F2';
                    }
                }
            }
        }

        // 1) replace tokens
        tpl = replaceTokens(tpl, tokens);

        // 2) ensure embeds.enabled is right even if template used literal true/false
        if (!tpl.includes(tokens.EMBEDS_ENABLED)) {
            // try a broad replace: "embeds:\n  enabled: true"
            tpl = tpl.replace(
                /(embeds:\s*\n(?:[ \t]+.*\n)*?[ \t]+enabled:\s*)(true|false)/,
                `$1${state.embedsEnabled ? 'true' : 'false'}`
            );
        }

        // 3) remove any existing footer in template
        tpl = tpl.replace(/# CONFIG VERSION .*GENERATED ON WEBSITE ON .*\n?/gi, '');

        // 4) append footer once
        tpl += `\n# CONFIG VERSION ${state.configVersion.toUpperCase()}, GENERATED ON WEBSITE ON ${now}\n`;

        return tpl;
    }

    /* ------------------------------------------------------------
     * 11. INITIAL LOAD (for default plugin)
     * ------------------------------------------------------------ */
    versionSelect.addEventListener('change', async () => {
        const pv = versionSelect.value;
        state.pluginVersion = pv;
        state.configVersion = VERS[pv].configVersion;
        versionNote.textContent = `Config schema: ${state.configVersion}`;
        await ensureConfigLoadedFor(pv);
        renderLogOptions();
        if (state.embedsEnabled) renderColors();
    });

    // initial load
    (async () => {
        await ensureConfigLoadedFor(state.pluginVersion);
        renderLogOptions();
        if (state.embedsEnabled) renderColors();
    })();

    /* ------------------------------------------------------------
     * 12. STYLES
     * ------------------------------------------------------------ */
    const style = document.createElement('style');
    style.textContent = `
    #cfg-gen .cfg-wrap { max-width: 740px; margin: 0 auto; }
    #cfg-gen .cfg-panel {
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 1.35rem 1.25rem 1.25rem;
      margin-bottom: 1.25rem;
    }
    #cfg-gen .cfg-title { margin: 0 0 .5rem; }
    #cfg-gen .cfg-note { color: var(--muted); }
    #cfg-gen .cfg-label { font-weight: 600; margin: .4rem 0 .25rem; display:block; }
    #cfg-gen .cfg-input {
      width: 100%;
      max-width: 100%;
      box-sizing: border-box;
      background: var(--bg);
      color: var(--fg);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: .55rem .6rem;
      font: inherit;
    }
    #cfg-gen select.cfg-input--select {
      appearance: none;
      background-image: linear-gradient(45deg, transparent 50%, var(--muted) 50%), linear-gradient(135deg, var(--muted) 50%, transparent 50%);
      background-position: calc(100% - 18px) calc(50% - 3px), calc(100% - 13px) calc(50% - 3px);
      background-size: 5px 5px, 5px 5px;
      background-repeat: no-repeat;
      padding-right: 2.2rem;
    }
    #cfg-gen .cfg-actions { display: flex; gap: .5rem; margin-top: 1rem; }
    #cfg-gen .cfg-actions-inline { display: flex; gap: .5rem; margin-top: .5rem; }
    #cfg-gen .cfg-btn {
      border: 1px solid var(--border);
      background: color-mix(in oklab, var(--fg) 4%, transparent);
      border-radius: 10px;
      padding: .5rem .85rem;
      cursor: pointer;
      color: var(--fg);
      font: inherit;
    }
    #cfg-gen .cfg-btn--ghost { background: transparent; }
    #cfg-gen .cfg-btn--primary {
      background: color-mix(in oklab, var(--accent) 14%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent) 30%, var(--border));
      color: var(--accent-fg);
    }
    #cfg-gen .cfg-btn--primary[disabled] {
      opacity: .5;
      cursor: not-allowed;
    }
    #cfg-gen .cfg-status {
      margin-top: .6rem;
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: .5rem .7rem;
    }
    #cfg-gen .cfg-status.is-ok { background: #16a34a14; border-color: #16a34a33; }
    #cfg-gen .cfg-status.is-error { background: #ef444414; border-color: #ef444433; }
    #cfg-gen .cfg-radio,
    #cfg-gen .cfg-check { display:flex; align-items:center; gap:.4rem; margin:.3rem 0; }
    #cfg-gen .cfg-sub { margin-top: .9rem; margin-bottom:.3rem; }
    #cfg-gen .cfg-colgroup {
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: .65rem .7rem;
      margin-bottom: .9rem;
    }
    #cfg-gen .cfg-colrow {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: .7rem;
      margin: .35rem 0;
    }
    #cfg-gen .cfg-colinputs {
      display: flex;
      gap: .35rem;
      align-items: center;
    }
    #cfg-gen .cfg-input--tiny { max-width: 110px; }
    #cfg-gen .cfg-yaml {
      width: 100%;
      min-height: 360px;
      border: 1px solid var(--border);
      border-radius: 12px;
      background: var(--code-bg);
      color: var(--fg);
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      padding: .6rem .7rem;
      box-sizing: border-box;
      white-space: pre;
      overflow: auto; /* horizontal + vertical */
      resize: vertical;
    }
    `;
    document.head.appendChild(style);
})();
