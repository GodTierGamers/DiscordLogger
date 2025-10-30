/* DiscordLogger – config.yml generator (template-driven, 1-step-at-a-time)
   - pulls version → configVersion from window.DL_VERSIONS
   - pulls per-config assets (template + options) from window.DL_CONFIGS
   - fills placeholders like {{TIME_FORMAT}}, {{COLOR_player.join}}, {{LOG_player.join}}
   - NO fallback builder: if template/options are missing, we stop and tell the user
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    /* -------------------------------------------------------
       tiny utils
    ------------------------------------------------------- */
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

    const isoNice = () => {
        const d = new Date();
        return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
    };

    const isDiscordWebhook = (u) =>
        /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+/i
            .test((u||'').trim());

    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;

    const human = (s) => s.replace(/[_\-]+/g,' ').replace(/\b\w/g, m => m.toUpperCase());

    /* -------------------------------------------------------
       global config from generator.config.js
    ------------------------------------------------------- */
    const DL_BASE     = window.DL_BASE     || '';
    const DL_VERSIONS = window.DL_VERSIONS || {};
    const DL_CONFIGS  = window.DL_CONFIGS  || {};
    const DL_PROXY    = (window.DL_PROXY_URL || '').trim();
    const DL_TEST_EMBED = window.DL_TEST_EMBED || null;

    const VERSION_KEYS = Object.keys(DL_VERSIONS);
    const DEFAULT_PLUGIN = VERSION_KEYS.length ? VERSION_KEYS[0] : '2.1.5';

    /* -------------------------------------------------------
       runtime state
    ------------------------------------------------------- */
    const state = {
        currentPanel: '1',
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: DL_VERSIONS[DEFAULT_PLUGIN]?.configVersion || 'v9',

        template: '',    // raw text from /assets/configs/{v}/config.template.yml
        options: null,   // JSON from /assets/configs/{v}/options.json

        webhookUrl: '',
        webhookConfirmed: false,

        embedsEnabled: true,
        embedAuthor: 'Server Logs',

        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',

        // filled after options load
        toggles: {},   // e.g. { 'player.join': true }
        colors: {},    // e.g. { 'player.join': '#57F287' }
    };

    /* -------------------------------------------------------
       layout shell
    ------------------------------------------------------- */
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

    /* -------------------------------------------------------
       Panel 1 – plugin version
    ------------------------------------------------------- */
    const versionSelect = el('select', {class:'cfg-input cfg-input--select'});
    VERSION_KEYS.sort().forEach(v => {
        versionSelect.appendChild(el('option', {
            value: v,
            ...(v === state.pluginVersion ? {selected:''} : {})
        }, v));
    });

    const versionNote = el('p', {class:'cfg-note'});

    const p1 = makePanel('1', '1) Plugin version', [
        el('p', {class:'cfg-note'}, 'Pick the DiscordLogger version you installed. Multiple plugin versions can share a config version.'),
        el('label', {class:'cfg-label'}, 'Plugin version'),
        versionSelect,
        versionNote,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p1next'}, 'Next')
        ])
    ]);

    /* -------------------------------------------------------
       Panel 2 – webhook
    ------------------------------------------------------- */
    const webhookInput  = el('input', {
        class:'cfg-input',
        type:'url',
        placeholder:'https://discord.com/api/webhooks/...'
    });
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

    /* -------------------------------------------------------
       Panel 3 – log style (embeds vs plain)
    ------------------------------------------------------- */
    const radioEmbeds = el('label', {class:'cfg-radio'}, [
        el('input', {type:'radio', name:'logstyle', value:'embeds', checked:''}),
        el('span', {}, 'Use embeds (recommended)')
    ]);
    const embedsExtra = el('div', {class:'cfg-inline'}, [
        el('label', {class:'cfg-label', for:'embedAuthor'}, 'Author name'),
        el('input', {id:'embedAuthor', class:'cfg-input', type:'text', value: state.embedAuthor})
    ]);

    const radioPlain = el('label', {class:'cfg-radio'}, [
        el('input', {type:'radio', name:'logstyle', value:'plain'}),
        el('span', {}, 'Use plain text')
    ]);

    const TIME_PRESETS = [
        {label: 'Default (same as plugin)', value: '[HH:mm:ss, dd:MM:yyyy]'},
        {label: 'EU 24h (dd/MM/yyyy HH:mm)', value: '[dd/MM/yyyy HH:mm]'},
        {label: 'US 12h (MM/dd/yyyy HH:mm)', value: '[MM/dd/yyyy HH:mm]'},
        {label: 'Time only (HH:mm:ss)', value: '[HH:mm:ss]'},
        {label: 'Custom…', value: ''},
    ];
    const dtPresetSel = el('select', {class:'cfg-input cfg-input--select', id:'dtPreset'});
    TIME_PRESETS.forEach(p => dtPresetSel.appendChild(el('option', {value:p.value}, p.label)));

    const dtCustomWrap = el('div', {class:'cfg-inline', style:'margin-top:.4rem;'}, [
        el('label', {class:'cfg-label', for:'plainFmt'}, 'Custom pattern'),
        el('input', {id:'plainFmt', class:'cfg-input', type:'text', value: state.plainTimeFmt}),
        el('p', {class:'cfg-note', id:'fmtPrev'}, 'Preview:')
    ]);

    const plainExtra = el('div', {class:'cfg-plain', style:'display:none'}, [
        el('label', {class:'cfg-label'}, 'Server name (optional)'),
        el('input', {id:'plainName', class:'cfg-input', type:'text', placeholder:'e.g. Survival, Creative', value:state.plainServerName}),
        el('p', {class:'cfg-note'}, 'Useful if you run multiple servers behind a proxy.'),
        el('label', {class:'cfg-label'}, 'Timestamp format'),
        el('p', {class:'cfg-note'}, 'Pick a pattern or choose “Custom…” and type your own Java-like pattern.'),
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

    /* -------------------------------------------------------
       Panel 4 – what to log (built from options.json)
    ------------------------------------------------------- */
    const logListWrap = el('div', {class:'cfg-loglist'}, [
        el('p', {class:'cfg-note'}, 'Loading log options…')
    ]);

    const p4 = makePanel('4', '4) What do you want to log?', [
        logListWrap,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p4back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p4next'}, 'Next')
        ])
    ]);

    /* -------------------------------------------------------
       Panel 5 – colors (only if embeds)
    ------------------------------------------------------- */
    const colorsWrap = el('div', {class:'cfg-colwrap'}, [
        el('p', {class:'cfg-note'}, 'Loading colors…')
    ]);

    const p5 = makePanel('5', '5) Embed colors', [
        el('p', {class:'cfg-note'}, 'Only used when “Use embeds” is selected.'),
        colorsWrap,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p5back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p5next'}, 'Generate')
        ])
    ]);

    /* -------------------------------------------------------
       Panel 6 – result
    ------------------------------------------------------- */
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

    /* -------------------------------------------------------
       fetch helpers
    ------------------------------------------------------- */
    async function fetchText(url) {
        const res = await fetch(url, {cache:'no-cache'});
        if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
        return await res.text();
    }
    async function fetchJson(url) {
        const res = await fetch(url, {cache:'no-cache'});
        if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
        return await res.json();
    }

    async function loadAssetsForCurrentConfig() {
        const meta = DL_CONFIGS[state.configVersion];
        if (!meta) {
            // no template/options for this version
            state.template = '';
            state.options  = null;
            logListWrap.innerHTML = '';
            logListWrap.appendChild(el('p', {class:'cfg-status is-error'}, `This config version (${state.configVersion}) has no assets defined.`));
            colorsWrap.innerHTML = '';
            colorsWrap.appendChild(el('p', {class:'cfg-status is-error'}, `This config version (${state.configVersion}) has no colors defined.`));
            return;
        }
        try {
            const [tpl, opts] = await Promise.all([
                fetchText(meta.templateUrl),
                fetchJson(meta.optionsUrl)
            ]);
            state.template = tpl;
            state.options  = opts;
            buildLogUIFromOptions();
            buildColorUIFromOptions();
        } catch (e) {
            state.template = '';
            state.options  = null;
            logListWrap.innerHTML = '';
            logListWrap.appendChild(el('p', {class:'cfg-status is-error'}, `Could not load config assets for ${state.configVersion}.`));
            colorsWrap.innerHTML = '';
            colorsWrap.appendChild(el('p', {class:'cfg-status is-error'}, `Could not load config assets for ${state.configVersion}.`));
        }
    }

    /* -------------------------------------------------------
       options.json → step 4 UI
    ------------------------------------------------------- */
    function buildLogUIFromOptions() {
        const opts = state.options;
        if (!opts || !Array.isArray(opts.groups)) {
            logListWrap.innerHTML = '';
            logListWrap.appendChild(el('p', {class:'cfg-status is-error'}, 'No logging options were found for this version.'));
            return;
        }

        // rebuild state.toggles from options
        const toggles = {};
        logListWrap.innerHTML = '';
        opts.groups.forEach(group => {
            const h = el('h3', {class:'cfg-sub'}, group.label || human(group.id || ''));
            logListWrap.appendChild(h);
            (group.items || []).forEach(item => {
                const k = item.key;
                const d = (item.default !== undefined) ? !!item.default : true;
                toggles[k] = d;
                const cb = el('input', {
                    type:'checkbox',
                    'data-key':k,
                    ...(d ? {checked:''} : {})
                });
                const label = el('label', {class:'cfg-check'}, [
                    cb,
                    ' ',
                    item.label || human(k)
                ]);
                logListWrap.appendChild(label);
            });
        });
        state.toggles = toggles;

        // wire checkboxes → state
        $$('.cfg-check input[type=checkbox]', logListWrap).forEach(cb => {
            cb.addEventListener('change', () => {
                state.toggles[cb.dataset.key] = cb.checked;
            });
        });
    }

    /* -------------------------------------------------------
       options.json → step 5 UI (colors)
    ------------------------------------------------------- */
    function buildColorUIFromOptions() {
        const opts = state.options;
        colorsWrap.innerHTML = '';

        // prefer colors from options.json
        const colors = {};
        if (opts && opts.colors && typeof opts.colors === 'object') {
            Object.assign(colors, opts.colors);
        }

        // ensure we always keep them on state
        state.colors = colors;

        // group by prefix before the '.'
        const groups = {};
        Object.entries(colors).forEach(([key, val]) => {
            const [cat, sub] = key.split('.', 2);
            (groups[cat] ??= []).push({sub, val, key});
        });

        // draw
        Object.keys(groups).sort().forEach(cat => {
            const catBox = el('div', {class:'cfg-colgroup'}, [
                el('h3', {class:'cfg-sub'}, human(cat))
            ]);
            groups[cat].forEach(row => {
                const rowEl = el('div', {class:'cfg-colrow'}, [
                    el('label', {class:'cfg-label'}, `${human(cat)} ${human(row.sub)}`),
                    el('div', {class:'cfg-colinputs'}, [
                        el('input', {type:'color', value:row.val, 'data-key':row.key}),
                        el('input', {type:'text', class:'cfg-input cfg-input--tiny', value:row.val, 'data-key':row.key})
                    ])
                ]);
                catBox.appendChild(rowEl);
            });
            colorsWrap.appendChild(catBox);
        });

        // wire
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

    /* -------------------------------------------------------
       webhook step
    ------------------------------------------------------- */
    const setWebhookStatus = (txt, cls='') => {
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
        confirmRow.style.display = 'none';
        setWebhookStatus('', '');
        validateWebhookNext();
    });

    async function sendWebhookTest(url, payload) {
        if (DL_PROXY) {
            const res = await fetch(DL_PROXY, {
                method:'POST',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ url: withWait(url), payload })
            });
            return res.ok;
        } else {
            try {
                const res = await fetch(withWait(url), {
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body: JSON.stringify(payload)
                });
                return res.ok;
            } catch {
                return false;
            }
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
        confirmRow.style.display = 'none';

        const payload = DL_TEST_EMBED ? structuredClone(DL_TEST_EMBED) : {
            content: null,
            embeds: [
                {
                    title: 'DiscordLogger Webhook Test',
                    description: 'Test.',
                    timestamp: new Date().toISOString()
                }
            ]
        };
        if (payload.embeds && payload.embeds[0]) {
            payload.embeds[0].timestamp = new Date().toISOString();
        }

        const ok = await sendWebhookTest(url, payload);
        webhookTestBtn.disabled = false;

        if (ok) {
            setWebhookStatus('Test message sent. Check Discord.', 'is-ok');
        } else {
            setWebhookStatus("Couldn't send webhook, please check your webhook URL", 'is-error');
        }

        confirmRow.style.display = '';
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

    /* -------------------------------------------------------
       log style step
    ------------------------------------------------------- */
    function updateLogStyleUI() {
        const style = $('input[name="logstyle"]:checked', p3)?.value || 'embeds';
        state.embedsEnabled = (style === 'embeds');
        embedsExtra.style.display = state.embedsEnabled ? '' : 'none';
        plainExtra.style.display  = state.embedsEnabled ? 'none' : '';
    }

    $$('input[name="logstyle"]', p3).forEach(r => r.addEventListener('change', updateLogStyleUI));

    // plain text extras
    const plainFmtInput = $('#plainFmt');
    const fmtPrev       = $('#fmtPrev');
    const plainNameInp  = $('#plainName');
    const embedAuthorInp = $('#embedAuthor');

    plainNameInp.addEventListener('input', () => {
        state.plainServerName = plainNameInp.value;
    });
    embedAuthorInp.addEventListener('input', () => {
        state.embedAuthor = embedAuthorInp.value;
    });

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
        let s = plainFmtInput.value;
        s = s.replace(/yyyy|HH|MM|dd|mm|ss/g, m => map[m] ?? m);
        fmtPrev.textContent = 'Preview: ' + s;
        state.plainTimeFmt = plainFmtInput.value;
    };
    plainFmtInput.addEventListener('input', updateFmtPreview);

    dtPresetSel.addEventListener('change', () => {
        const v = dtPresetSel.value;
        if (v) {
            plainFmtInput.value = v;
            plainFmtInput.disabled = true;
        } else {
            plainFmtInput.disabled = false;
            plainFmtInput.focus();
        }
        updateFmtPreview();
    });

    updateLogStyleUI();
    updateFmtPreview();

    /* -------------------------------------------------------
       panel navigation
    ------------------------------------------------------- */
    $('#p1next').addEventListener('click', () => showPanel('2'));
    $('#p2back').addEventListener('click', () => showPanel('1'));
    $('#p2next').addEventListener('click', () => showPanel('3'));
    $('#p3back').addEventListener('click', () => showPanel('2'));
    $('#p3next').addEventListener('click', () => showPanel('4'));
    $('#p4back').addEventListener('click', () => showPanel('3'));
    $('#p5back').addEventListener('click', () => showPanel('4'));

    /* -------------------------------------------------------
       Step 4 → Step 5 / 6
    ------------------------------------------------------- */
    $('#p4next').addEventListener('click', () => {
        if (state.embedsEnabled) {
            showPanel('5');
        } else {
            // no colors step
            const finalYaml = renderFinalYamlFromTemplate();
            yamlOut.value = finalYaml;
            showPanel('6');
        }
    });

    $('#p5next').addEventListener('click', () => {
        const finalYaml = renderFinalYamlFromTemplate();
        yamlOut.value = finalYaml;
        showPanel('6');
    });

    /* -------------------------------------------------------
       final YAML builder (fills template)
    ------------------------------------------------------- */
    function renderFinalYamlFromTemplate() {
        if (!state.template) {
            return '# Could not build config: template for this version is missing.';
        }

        let out = state.template;

        // basic replacements
        const baseRepls = {
            '{{WEBHOOK_URL}}': state.webhookUrl,
            '{{TIME_FORMAT}}': state.plainTimeFmt,
            '{{PLAIN_NAME}}': (state.plainServerName || ''),
            '{{NICKNAMES}}': 'true',
            '{{EMBEDS_AUTHOR}}': (state.embedAuthor || '')
        };
        Object.entries(baseRepls).forEach(([ph,val]) => {
            out = out.split(ph).join(val);
        });

        // colors
        Object.entries(state.colors || {}).forEach(([key,val]) => {
            const ph = `{{COLOR_${key}}}`;
            out = out.split(ph).join(val);
        });

        // logs
        Object.entries(state.toggles || {}).forEach(([key,val]) => {
            const ph = `{{LOG_${key}}}`;
            out = out.split(ph).join(val ? 'true' : 'false');
        });

        // footer
        const stamp = `# CONFIG VERSION ${state.configVersion.toUpperCase()}, GENERATED ON WEBSITE ON ${isoNice()}`;
        if (!out.trimEnd().endsWith(stamp)) {
            out = out.replace(/\s*$/, '') + '\n\n' + stamp + '\n';
        }

        return out;
    }

    /* -------------------------------------------------------
       result actions
    ------------------------------------------------------- */
    $('#copyYaml').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(yamlOut.value); } catch {}
    });

    $('#downloadYaml').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([yamlOut.value], {type:'text/yaml'}));
        document.body.appendChild(a);
        a.click();
        a.remove();
    });

    /* -------------------------------------------------------
       version change → reload assets
    ------------------------------------------------------- */
    function updateVersionNote() {
        state.pluginVersion = versionSelect.value;
        state.configVersion = DL_VERSIONS[state.pluginVersion]?.configVersion || 'v9';
        versionNote.textContent = `Config schema: ${state.configVersion}`;
    }

    versionSelect.addEventListener('change', async () => {
        updateVersionNote();
        // show temporary loading states in p4/p5
        logListWrap.innerHTML = '';
        logListWrap.appendChild(el('p', {class:'cfg-note'}, 'Loading log options…'));
        colorsWrap.innerHTML = '';
        colorsWrap.appendChild(el('p', {class:'cfg-note'}, 'Loading colors…'));
        await loadAssetsForCurrentConfig();
    });

    // initial load
    updateVersionNote();
    loadAssetsForCurrentConfig().catch(() => { /* handled above */ });

    /* -------------------------------------------------------
       inline styles for the generator
    ------------------------------------------------------- */
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
