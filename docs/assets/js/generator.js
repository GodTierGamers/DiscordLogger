/* DiscordLogger – config.yml generator (version-aware, NO fallback builder, NO inline test embed)
   Assumptions (part of your release process):
   - generator.config.js defines:
       window.DL_PROXY_URL
       window.DL_VERSIONS
       window.DL_CONFIGS
       window.DL_TEST_EMBED   <-- we ONLY use this, we do NOT build our own here
   - For every configVersion in window.DL_VERSIONS there is a matching entry in window.DL_CONFIGS:
       { templateUrl, optionsUrl, downloadUrl? }
   - config.template.yml contains the tokens we replace:
       {{WEBHOOK_URL}}, {{FORMAT_TIME}}, {{FORMAT_NAME}},
       {{EMBEDS_ENABLED}}, {{EMBED_AUTHOR}}, {{EMBED_COLORS}},
       {{LOG_SECTION}}, {{CONFIG_VERSION}}, {{GENERATED_AT}}
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    /* ----------------- tiny utils ----------------- */
    const $  = (s, el=document) => el.querySelector(s);
    const $$ = (s, el=document) => Array.from(el.querySelectorAll(s));
    const el = (tag, attrs={}, kids=[]) => {
        const n = document.createElement(tag);
        for (const [k,v] of Object.entries(attrs)) {
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

    const isDiscordWebhook = (u) =>
        /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+/i
            .test((u||'').trim());

    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;

    const isoNice = () => {
        const d = new Date();
        return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
    };

    const human = (s) => s.replace(/[_\-]+/g,' ').replace(/\b\w/g, m => m.toUpperCase());

    const replaceToken = (txt, name, value) => {
        if (!txt) return txt;
        const re1 = new RegExp(`{{\\s*${name}\\s*}}`, 'g');
        const re2 = new RegExp(`%%${name}%%`, 'g');
        return txt.replace(re1, value).replace(re2, value);
    };

    /* ----------------- initial data ----------------- */
    const allVersions = window.DL_VERSIONS || { '2.1.5': { configVersion: 'v9' } };
    const DEFAULT_PLUGIN = Object.keys(allVersions)[0];

    const allConfigs  = window.DL_CONFIGS || {
        'v9': {
            templateUrl: '/docs/assets/configs/v9/config.template.yml',
            downloadUrl: '/docs/assets/configs/v9/config.yml',
            optionsUrl:  '/docs/assets/configs/v9/options.json'
        }
    };

    const state = {
        currentPanel: '1',

        pluginVersion: DEFAULT_PLUGIN,
        configVersion: allVersions[DEFAULT_PLUGIN]?.configVersion || 'v9',

        // loaded from remote
        options: null,
        templateText: null,
        loadedFor: null,

        // step 2
        webhookUrl: '',
        webhookConfirmed: false,

        // step 3
        embedsEnabled: true,
        embedAuthor: 'Server Logs',
        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',

        // dynamic
        toggles: {},
        colors: {}
    };

    /* ----------------- normalize options.json ----------------- */
    const normalizeOptions = (raw) => {
        if (!raw) return null;

        // new format (recommended): { categories: {...}, categoryOrder: [...] }
        if (raw.categories) {
            const order = Array.isArray(raw.categoryOrder)
                ? raw.categoryOrder
                : Object.keys(raw.categories);
            return {
                order,
                categories: raw.categories
            };
        }

        // old / flat format: { logs: { "player.join": { ... } } }
        if (raw.logs) {
            const cats = {};
            Object.entries(raw.logs).forEach(([key, def]) => {
                const [cat, sub] = key.split('.', 2);
                if (!cats[cat]) {
                    cats[cat] = { label: human(cat), items: {} };
                }
                cats[cat].items[sub] = {
                    label: def.label || human(sub),
                    default: def.default !== undefined ? !!def.default : true,
                    color: def.color || ''
                };
            });
            return {
                order: Object.keys(cats),
                categories: cats
            };
        }

        return null;
    };

    const applyOptionsToState = (opt) => {
        state.toggles = {};
        state.colors  = {};

        if (!opt) return;

        opt.order.forEach(catKey => {
            const cat = opt.categories[catKey];
            if (!cat || !cat.items) return;
            Object.entries(cat.items).forEach(([logKey, def]) => {
                const fullKey = `${catKey}.${logKey}`;
                state.toggles[fullKey] = def.default !== undefined ? !!def.default : true;
                if (def.color) {
                    state.colors[fullKey] = def.color;
                }
            });
        });
    };

    /* ----------------- dynamic fetch ----------------- */
    const fetchText = async (url) => {
        const res = await fetch(url, {cache: 'no-store'});
        if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
        return res.text();
    };
    const fetchJSON = async (url) => {
        const res = await fetch(url, {cache: 'no-store'});
        if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
        return res.json();
    };

    const ensureAssetsLoaded = async (configVersion) => {
        const meta = allConfigs[configVersion];
        if (!meta) {
            throw new Error(`[cfg-gen] No DL_CONFIGS entry for ${configVersion}`);
        }

        if (state.loadedFor === configVersion && state.options && state.templateText) {
            return;
        }

        const [optJSON, tplText] = await Promise.all([
            meta.optionsUrl ? fetchJSON(meta.optionsUrl) : Promise.reject(new Error('Missing optionsUrl')),
            meta.templateUrl ? fetchText(meta.templateUrl) : Promise.reject(new Error('Missing templateUrl'))
        ]);

        state.options = normalizeOptions(optJSON);
        state.templateText = tplText;
        state.loadedFor = configVersion;

        applyOptionsToState(state.options);
    };

    /* ----------------- layout shell ----------------- */
    mount.innerHTML = '';
    const wrapper = el('div', {class:'cfg-wrap'});
    mount.appendChild(wrapper);

    const makePanel = (id, title, body) => {
        return el('section', {class:'cfg-panel', 'data-panel':id}, [
            el('h2', {class:'cfg-title'}, title),
            ...(Array.isArray(body) ? body : [body]),
        ]);
    };

    const showPanel = (id) => {
        state.currentPanel = id;
        $$('.cfg-panel', wrapper).forEach(p => {
            p.style.display = (p.dataset.panel === id ? 'block' : 'none');
        });
    };

    /* ----------------- Panel 1 ----------------- */
    const versionSelect = el('select', {class:'cfg-input cfg-input--select'});
    Object.keys(allVersions)
        .sort()
        .forEach(v => {
            versionSelect.appendChild(el('option', {value:v, ...(v===state.pluginVersion ? {selected:''} : {})}, v));
        });

    const versionNote = el('p', {class:'cfg-note'});
    const versionLoading = el('p', {class:'cfg-note', style:'display:none'}, 'Loading config assets…');

    const updateVersionNote = async () => {
        state.pluginVersion = versionSelect.value;
        state.configVersion = allVersions?.[state.pluginVersion]?.configVersion || 'v9';
        versionNote.textContent = `Config schema: ${state.configVersion}`;

        versionLoading.style.display = '';
        try {
            await ensureAssetsLoaded(state.configVersion);
            renderLogToggles();
            renderColors();
        } catch (e) {
            console.warn(e);
        } finally {
            versionLoading.style.display = 'none';
        }
    };
    updateVersionNote();

    const p1 = makePanel('1', '1) Plugin version', [
        el('p', {class:'cfg-note'}, 'Pick the DiscordLogger version you installed.'),
        el('label', {class:'cfg-label'}, 'Plugin version'),
        versionSelect,
        versionNote,
        versionLoading,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p1next'}, 'Next')
        ])
    ]);

    /* ----------------- Panel 2 ----------------- */
    const webhookInput = el('input', {class:'cfg-input', type:'url', placeholder:'https://discord.com/api/webhooks/...'});
    const webhookStatus = el('div', {class:'cfg-status', style:'display:none'});
    const webhookTestBtn = el('button', {class:'cfg-btn', type:'button'}, 'Send test');
    const confirmRow = el('div', {class:'cfg-confirm', style:'display:none'}, [
        el('p', {class:'cfg-note'}, 'Did the test message appear in your Discord channel?'),
        el('div', {class:'cfg-actions-inline'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p2yes'}, 'Yes'),
            el('button', {class:'cfg-btn cfg-btn--ghost', type:'button', id:'p2no'}, 'No, try again')
        ])
    ]);
    const p2next = el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p2next', disabled:''}, 'Next');

    const p2 = makePanel('2', '2) Discord webhook', [
        el('p', {class:'cfg-note'}, 'Paste your Discord channel webhook here. We’ll send a test embed to it.'),
        el('label', {class:'cfg-label'}, 'Webhook URL'),
        webhookInput,
        webhookTestBtn,
        webhookStatus,
        confirmRow,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p2back'}, 'Back'),
            p2next
        ])
    ]);

    /* ----------------- Panel 3 ----------------- */
    const TIME_PRESETS = [
        {label: 'Default (same as plugin)', value: '[HH:mm:ss, dd:MM:yyyy]'},
        {label: 'EU 24h (dd/MM/yyyy HH:mm)', value: '[dd/MM/yyyy HH:mm]'},
        {label: 'US 12h (MM/dd/yyyy HH:mm)', value: '[MM/dd/yyyy HH:mm]'},
        {label: 'Time only (HH:mm:ss)', value: '[HH:mm:ss]'},
        {label: 'Custom…', value: ''},
    ];

    const radioEmbeds = el('label', {class:'cfg-radio'}, [
        el('input', {type:'radio', name:'logstyle', value:'embeds', checked:''}),
        el('span', {}, 'Use embeds (recommended)')
    ]);
    const embedsExtra = el('div', {class:'cfg-inline'}, [
        el('label', {class:'cfg-label', for:'embedAuthor'}, 'Author name (shown in embed header)'),
        el('input', {id:'embedAuthor', class:'cfg-input', type:'text', value: state.embedAuthor})
    ]);

    const radioPlain = el('label', {class:'cfg-radio'}, [
        el('input', {type:'radio', name:'logstyle', value:'plain'}),
        el('span', {}, 'Use plain text')
    ]);

    const dtPresetSel = el('select', {class:'cfg-input cfg-input--select', id:'dtPreset'});
    TIME_PRESETS.forEach(p => dtPresetSel.appendChild(el('option', {value:p.value}, p.label)));

    const dtCustomWrap = el('div', {class:'cfg-inline', style:'margin-top:.4rem;'}, [
        el('label', {class:'cfg-label', for:'plainFmt'}, 'Custom pattern'),
        el('input', {id:'plainFmt', class:'cfg-input', type:'text', value: state.plainTimeFmt}),
        el('p', {class:'cfg-note', id:'fmtPrev'}, 'Preview: ')
    ]);

    const plainExtra = el('div', {class:'cfg-plain', style:'display:none'}, [
        el('label', {class:'cfg-label'}, 'Server name (optional)'),
        el('input', {id:'plainName', class:'cfg-input', type:'text', placeholder:'e.g. Survival, Creative', value:state.plainServerName}),
        el('p', {class:'cfg-note'}, 'Useful if you run multiple servers behind a proxy.'),
        el('label', {class:'cfg-label'}, 'Timestamp format'),
        el('p', {class:'cfg-note'}, 'Pick a pattern or choose “Custom…” and type your own Java DateTimeFormatter style.'),
        dtPresetSel,
        dtCustomWrap
    ]);

    const p3 = makePanel('3', '3) Log Style', [
        el('p', {class:'cfg-note'}, 'Choose how DiscordLogger should send messages to Discord.'),
        radioEmbeds,
        embedsExtra,
        radioPlain,
        plainExtra,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p3back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p3next'}, 'Next')
        ])
    ]);

    /* ----------------- Panel 4: what to log ----------------- */
    const p4list = el('div', {class:'cfg-loglist'});
    const p4 = makePanel('4', '4) What do you want to log?', [
        el('p', {class:'cfg-note'}, 'This list comes from assets/configs/<version>/options.json for the version you picked.'),
        p4list,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p4back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p4next'}, 'Next')
        ])
    ]);

    /* ----------------- Panel 5: colors ----------------- */
    const colorsWrap = el('div', {class:'cfg-colwrap'});
    const p5 = makePanel('5', '5) Embed colors', [
        el('p', {class:'cfg-note'}, 'Only used when “Use embeds” is selected.'),
        colorsWrap,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p5back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p5next'}, 'Generate')
        ])
    ]);

    /* ----------------- Panel 6: result ----------------- */
    const yamlOut = el('textarea', {class:'cfg-yaml', readonly:''});
    yamlOut.setAttribute('wrap', 'off');

    const p6 = makePanel('6', 'Your config.yml', [
        yamlOut,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'copyYaml'}, 'Copy'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'downloadYaml'}, 'Download')
        ])
    ]);

    wrapper.append(p1, p2, p3, p4, p5, p6);
    showPanel('1');

    /* ----------------- renderers ----------------- */
    function renderLogToggles() {
        p4list.innerHTML = '';
        const opt = state.options;
        if (!opt) {
            p4list.appendChild(el('p', {class:'cfg-note'}, 'Error: options.json not loaded for this version.'));
            return;
        }

        opt.order.forEach(catKey => {
            const cat = opt.categories[catKey];
            if (!cat) return;

            p4list.appendChild(el('h3', {class:'cfg-sub'}, cat.label || human(catKey)));

            Object.entries(cat.items || {}).forEach(([logKey, def]) => {
                const fullKey = `${catKey}.${logKey}`;
                const checked = state.toggles[fullKey] !== undefined ? state.toggles[fullKey] : (def.default !== undefined ? !!def.default : true);
                const cb = el('input', {type:'checkbox', 'data-key':fullKey, ...(checked ? {checked:''} : {})});
                const friendly = def.label || human(logKey);
                const row = el('label', {class:'cfg-check'}, [
                    cb, ' ', friendly
                ]);
                cb.addEventListener('change', () => {
                    state.toggles[fullKey] = cb.checked;
                });
                p4list.appendChild(row);
            });
        });
    }

    function renderColors() {
        colorsWrap.innerHTML = '';
        const opt = state.options;
        if (!opt) {
            colorsWrap.appendChild(el('p', {class:'cfg-note'}, 'Error: options.json not loaded for this version.'));
            return;
        }

        opt.order.forEach(catKey => {
            const cat = opt.categories[catKey];
            if (!cat) return;

            const groupBox = el('div', {class:'cfg-colgroup'}, [
                el('h3', {class:'cfg-sub'}, cat.label || human(catKey))
            ]);

            Object.entries(cat.items || {}).forEach(([logKey, def]) => {
                const fullKey = `${catKey}.${logKey}`;
                const existingColor = state.colors[fullKey] || def.color || '#5865F2';

                const row = el('div', {class:'cfg-colrow'}, [
                    el('label', {class:'cfg-label'}, def.label || human(logKey)),
                    el('div', {class:'cfg-colinputs'}, [
                        el('input', {type:'color', value: existingColor, 'data-key':fullKey}),
                        el('input', {type:'text', class:'cfg-input cfg-input--tiny', value: existingColor, 'data-key':fullKey})
                    ])
                ]);

                groupBox.appendChild(row);
            });

            colorsWrap.appendChild(groupBox);
        });

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
                if (!v.startsWith('#')) v = '#'+v;
                if (/^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(v)) {
                    const k = inp.dataset.key;
                    state.colors[k] = v;
                    const col = colorsWrap.querySelector(`input[type=color][data-key="${k}"]`);
                    if (col) col.value = v;
                }
            });
        });
    }

    /* ----------------- nav wiring ----------------- */
    $('#p1next').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('2');
    });
    $('#p2back').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('1');
    });
    $('#p3back').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('2');
    });
    $('#p4back').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('3');
    });
    $('#p5back').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('4');
    });

    /* ----------------- webhook logic ----------------- */
    const setWebhookStatus = (txt, cls='') => {
        webhookStatus.textContent = txt;
        webhookStatus.className = `cfg-status ${cls}`;
        webhookStatus.style.display = txt ? '' : 'none';
    };

    const postViaProxy = async (proxyUrl, webhookUrl, payload) => {
        const res = await fetch(proxyUrl, {
            method:'POST',
            headers:{'Content-Type':'application/json'},
            body: JSON.stringify({ url: withWait(webhookUrl), payload })
        });
        const text = await res.text().catch(()=> '');
        let data = null; try { data = text ? JSON.parse(text) : null; } catch {}
        return { ok: res.ok, status: res.status, data, text };
    };
    const postDirect = async (webhookUrl, payload) => {
        try {
            const res = await fetch(withWait(webhookUrl), {
                method:'POST',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify(payload)
            });
            let json = null; try { json = await res.json(); } catch {}
            return { ok: res.ok, status: res.status, data: json };
        } catch {
            return { ok:false, status:0 };
        }
    };

    const validateWebhookNext = () => {
        const ok = isDiscordWebhook(state.webhookUrl) && state.webhookConfirmed;
        $('#p2next').disabled = !ok;
    };

    webhookInput.addEventListener('input', () => {
        state.webhookUrl = webhookInput.value.trim();
        state.webhookConfirmed = false;
        confirmRow.style.display = 'none';
        setWebhookStatus('', '');
        validateWebhookNext();
    });

    webhookTestBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setWebhookStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }

        setWebhookStatus('Sending test...', 'is-info');
        webhookTestBtn.disabled = true;
        confirmRow.style.display = 'none';

        // IMPORTANT: test payload comes ONLY from generator.config.js
        // We assume window.DL_TEST_EMBED is present there.
        const base = (typeof structuredClone === 'function')
            ? structuredClone(window.DL_TEST_EMBED)
            : JSON.parse(JSON.stringify(window.DL_TEST_EMBED));

        if (base.embeds && base.embeds[0]) {
            base.embeds[0].timestamp = new Date().toISOString();
        }

        const proxy = (window.DL_PROXY_URL || '').trim();
        let res;
        try {
            res = proxy
                ? await postViaProxy(proxy, url, base)
                : await postDirect(url, base);
        } catch {
            res = { ok:false };
        }

        webhookTestBtn.disabled = false;

        if (res.ok) {
            setWebhookStatus('Test message sent. Check Discord.', 'is-ok');
        } else {
            setWebhookStatus("Couldn't send webhook, please check your webhook URL", 'is-error');
        }

        confirmRow.style.display = '';
    });

    $('#p2yes').addEventListener('click', (e) => {
        e.preventDefault();
        state.webhookConfirmed = true;
        setWebhookStatus('Webhook confirmed.', 'is-ok');
        validateWebhookNext();
    });
    $('#p2no').addEventListener('click', (e) => {
        e.preventDefault();
        state.webhookConfirmed = false;
        setWebhookStatus('Check your URL and try again.', 'is-info');
        validateWebhookNext();
    });

    $('#p2next').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('3');
    });

    /* ----------------- log style logic ----------------- */
    const updateLogStyleUI = () => {
        const useEmbeds = $('input[name="logstyle"]:checked').value === 'embeds';
        state.embedsEnabled = useEmbeds;
        embedsExtra.style.display = useEmbeds ? '' : 'none';
        plainExtra.style.display = useEmbeds ? 'none' : '';
    };

    $$('input[name="logstyle"]').forEach(r => r.addEventListener('change', updateLogStyleUI));

    const fmtInput = $('#plainFmt');
    const fmtPrev  = $('#fmtPrev');

    const updateFmtPreview = () => {
        const d = new Date();
        const map = {
            'yyyy': d.getFullYear(),
            'HH': pad2(d.getHours()),
            'mm': pad2(d.getMinutes()),
            'ss': pad2(d.getSeconds()),
            'dd': pad2(d.getDate()),
            'MM': pad2(d.getMonth()+1)
        };
        let s = fmtInput.value;
        s = s.replace(/yyyy|HH|MM|dd|mm|ss/g, m => map[m] ?? m);
        fmtPrev.textContent = 'Preview: ' + s;
        state.plainTimeFmt = fmtInput.value;
    };

    dtPresetSel.addEventListener('change', () => {
        const v = dtPresetSel.value;
        if (v) {
            fmtInput.value = v;
            fmtInput.disabled = true;
        } else {
            fmtInput.disabled = false;
            fmtInput.focus();
        }
        updateFmtPreview();
    });
    fmtInput.addEventListener('input', updateFmtPreview);

    $('#embedAuthor').addEventListener('input', (e) => {
        state.embedAuthor = e.target.value;
    });
    $('#plainName').addEventListener('input', (e) => {
        state.plainServerName = e.target.value;
    });

    $('#p3next').addEventListener('click', async (e) => {
        e.preventDefault();
        if (state.loadedFor !== state.configVersion) {
            await ensureAssetsLoaded(state.configVersion);
        }
        showPanel('4');
    });
    $('#p3back').addEventListener('click', (e) => {
        e.preventDefault();
        showPanel('2');
    });

    updateLogStyleUI();
    updateFmtPreview();

    /* ----------------- step 4 -> colors/result ----------------- */
    $('#p4next').addEventListener('click', (e) => {
        e.preventDefault();
        if (state.embedsEnabled) {
            showPanel('5');
        } else {
            yamlOut.value = buildYaml();
            showPanel('6');
        }
    });

    /* ----------------- colors -> result ----------------- */
    $('#p5next').addEventListener('click', (e) => {
        e.preventDefault();
        yamlOut.value = buildYaml();
        showPanel('6');
    });

    /* ----------------- result actions ----------------- */
    $('#copyYaml').addEventListener('click', async (e) => {
        e.preventDefault();
        try { await navigator.clipboard.writeText(yamlOut.value); } catch {}
    });
    $('#downloadYaml').addEventListener('click', (e) => {
        e.preventDefault();
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([yamlOut.value], {type:'text/yaml'}));
        document.body.appendChild(a); a.click(); a.remove();
    });

    /* ----------------- YAML build (NO fallback) ----------------- */
    function buildLogSectionFromState(indent = '') {
        const lines = [];
        const opt = state.options;
        if (!opt) {
            return '# ERROR: options.json not loaded for this config version\n';
        }

        lines.push(`${indent}log:`);
        opt.order.forEach(catKey => {
            const cat = opt.categories[catKey];
            lines.push(`${indent}  ${catKey}:`);
            Object.entries(cat.items || {}).forEach(([logKey, def]) => {
                const fullKey = `${catKey}.${logKey}`;
                const enabled = state.toggles[fullKey] !== undefined
                    ? state.toggles[fullKey]
                    : (def.default !== undefined ? !!def.default : true);
                const comment = def.label ? ` # ${def.label}` : '';
                lines.push(`${indent}    ${logKey}: ${enabled ? 'true' : 'false'}${comment}`);
            });
            lines.push('');
        });
        return lines.join('\n').trimEnd();
    }

    function buildEmbedColorsFromState(indent = '  ') {
        const opt = state.options;
        if (!opt) return '# ERROR: options.json not loaded';

        const lines = [];
        lines.push(`${indent}colors:`);
        opt.order.forEach(catKey => {
            const cat = opt.categories[catKey];
            lines.push(`${indent}  ${catKey}:`);
            Object.entries(cat.items || {}).forEach(([logKey, def]) => {
                const fullKey = `${catKey}.${logKey}`;
                const color = state.colors[fullKey] || def.color;
                if (color) {
                    lines.push(`${indent}    ${logKey}: "${color}"`);
                }
            });
        });
        return lines.join('\n');
    }

    function buildYaml() {
        if (!state.templateText) {
            return `# ERROR: Missing config.template.yml for ${state.configVersion}\n# Make sure /docs/assets/configs/${state.configVersion}/config.template.yml exists.`;
        }
        if (!state.options) {
            return `# ERROR: Missing options.json for ${state.configVersion}\n# Make sure /docs/assets/configs/${state.configVersion}/options.json exists.`;
        }

        const logSection  = buildLogSectionFromState('');
        const embedColors = buildEmbedColorsFromState('  ');

        let y = state.templateText;

        y = replaceToken(y, 'WEBHOOK_URL', state.webhookUrl);
        y = replaceToken(y, 'FORMAT_TIME', state.plainTimeFmt);
        y = replaceToken(y, 'FORMAT_NAME', (state.plainServerName || '').replace(/"/g, '\\"'));
        y = replaceToken(y, 'EMBEDS_ENABLED', state.embedsEnabled ? 'true' : 'false');
        y = replaceToken(y, 'EMBED_AUTHOR', (state.embedAuthor || '').replace(/"/g, '\\"'));
        y = replaceToken(y, 'EMBED_COLORS', embedColors);
        y = replaceToken(y, 'LOG_SECTION', logSection);
        y = replaceToken(y, 'CONFIG_VERSION', state.configVersion.toUpperCase());
        y = replaceToken(y, 'GENERATED_AT', isoNice());

        return y;
    }

    /* ----------------- styles ----------------- */
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
      font-family: ui-monospace, SFMono-Regular, SFMono-Medium, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      padding: .6rem .7rem;
      box-sizing: border-box;
      white-space: pre;
      overflow: auto;
      resize: vertical;
    }
  `;
    document.head.appendChild(style);
})();
