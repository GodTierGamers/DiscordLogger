/* DiscordLogger – config.yml generator (options-driven, 1-step-at-a-time)
   - reads per-version files:
     /docs/assets/configs/<version>/config.template.yml
     /docs/assets/configs/<version>/options.json
   - window.DL_VERSIONS + window.DL_CONFIGS + window.DL_TEST_EMBED come from generator.config.js
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    /* ---------------------------------------- utils ---------------------------------------- */
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

    /* -------------------------------------- initial data ------------------------------------ */
    const VERSIONS = window.DL_VERSIONS || { "2.1.5": { configVersion: "v9" } };
    const CONFIGS  = window.DL_CONFIGS  || {}; // we rely on this – no fallback builder

    const DEFAULT_PLUGIN = Object.keys(VERSIONS)[0];
    const startConfigVersion = VERSIONS[DEFAULT_PLUGIN]?.configVersion || 'v9';

    const state = {
        currentPanel: '1',
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: startConfigVersion,

        webhookUrl: '',
        webhookConfirmed: false,

        embedsEnabled: true,
        embedAuthor: 'Server Logs',

        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',

        // these come from options.json
        options: null,     // full JSON (with categories)
        toggles: {},       // { "player.join": true, ... }   (NO "log." prefix)
        colors: {},        // { "player.join": "#hex", ... }
        template: '',      // raw template contents
    };

    /* ----------------------- loader: per-version assets (options + template) ---------------- */
    async function loadVersionAssets(version) {
        const cfg = CONFIGS[version];
        if (!cfg) {
            throw new Error(`No asset mapping for version ${version} – check generator.config.js`);
        }

        // fetch options.json
        const optRes = await fetch(cfg.optionsUrl, { cache: 'no-store' });
        if (!optRes.ok) {
            throw new Error(`Could not load options for ${version}`);
        }
        const optJson = await optRes.json();

        // fetch template
        const tplRes = await fetch(cfg.templateUrl, { cache: 'no-store' });
        if (!tplRes.ok) {
            throw new Error(`Could not load template for ${version}`);
        }
        const tplText = await tplRes.text();

        // normalise options into { toggles, colors }
        const { toggles, colors } = normaliseOptions(optJson);

        state.options  = optJson;
        state.toggles  = toggles;
        state.colors   = colors;
        state.template = tplText;
    }

    function normaliseOptions(opt) {
        // our current options.json shape:
        // { "categories": [ { id, label, description, items: [ {configKey, colorKey, default} ] } ] }
        const toggles = {};
        const colors  = {};

        if (!opt || !Array.isArray(opt.categories)) {
            return { toggles, colors };
        }

        for (const cat of opt.categories) {
            for (const item of (cat.items || [])) {
                // configKey: "log.player.join" -> store as "player.join"
                if (item.configKey) {
                    const key = item.configKey.replace(/^log\./, '');
                    toggles[key] = item.default !== false; // default to true unless explicit false
                }
                // colorKey: "player.join"
                if (item.colorKey) {
                    colors[item.colorKey] = item.defaultColor || guessColor(item.colorKey) || '#5865F2';
                }
            }
        }

        return { toggles, colors };
    }

    function guessColor(key) {
        // tiny helper: we can keep it very small
        if (key === 'player.join') return '#57F287';
        if (key === 'player.quit') return '#ED4245';
        if (key.startsWith('moderation.')) return '#FF3B30';
        return null;
    }

    /* -------------------------------------- layout shell ------------------------------------ */
    mount.innerHTML = '';
    const wrapper = el('div', { class: 'cfg-wrap' });
    mount.appendChild(wrapper);

    const makePanel = (id, title, body) => {
        return el('section', { class: 'cfg-panel', 'data-panel': id }, [
            el('h2', { class: 'cfg-title' }, title),
            ...(Array.isArray(body) ? body : [body]),
        ]);
    };

    const showPanel = (id) => {
        state.currentPanel = id;
        $$('.cfg-panel', wrapper).forEach(p => {
            p.style.display = (p.dataset.panel === id ? 'block' : 'none');
        });
    };

    /* -------------------------------------- panel 1: version -------------------------------- */
    const versionSelect = el('select', { class: 'cfg-input cfg-input--select' });
    Object.keys(VERSIONS)
        .sort()
        .forEach(v => {
            versionSelect.appendChild(el('option', {
                value: v, ...(v === state.pluginVersion ? { selected: '' } : {})
            }, v));
        });

    const versionNote = el('p', { class: 'cfg-note' });
    async function onVersionChange() {
        state.pluginVersion = versionSelect.value;
        state.configVersion = VERSIONS[state.pluginVersion]?.configVersion || 'v9';
        versionNote.textContent = `Config schema: ${state.configVersion}`;
        // load assets immediately for this version
        try {
            await loadVersionAssets(state.configVersion);
        } catch (e) {
            console.error(e);
            versionNote.textContent = `Config schema: ${state.configVersion} (assets missing)`;
        }
    }
    versionSelect.addEventListener('change', onVersionChange);

    const p1 = makePanel('1', '1) Plugin version', [
        el('p', { class: 'cfg-note' }, 'Pick the DiscordLogger version you installed.'),
        el('label', { class: 'cfg-label' }, 'Plugin version'),
        versionSelect,
        versionNote,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p1next' }, 'Next')
        ])
    ]);

    /* -------------------------------------- panel 2: webhook -------------------------------- */
    const webhookInput = el('input', { class: 'cfg-input', type: 'url', placeholder: 'https://discord.com/api/webhooks/…' });
    const webhookStatus = el('div', { class: 'cfg-status', style: 'display:none' });
    const webhookTestBtn = el('button', { class: 'cfg-btn', type: 'button' }, 'Send test');
    const confirmRow = el('div', { class: 'cfg-confirm', style: 'display:none' }, [
        el('p', { class: 'cfg-note' }, 'Did the test message appear in your Discord channel?'),
        el('div', { class: 'cfg-actions-inline' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p2yes' }, 'Yes'),
            el('button', { class: 'cfg-btn cfg-btn--ghost', type: 'button', id: 'p2no' }, 'No, try again')
        ])
    ]);
    const p2next = el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p2next', disabled: '' }, 'Next');

    const p2 = makePanel('2', '2) Discord webhook', [
        el('p', { class: 'cfg-note' }, 'Paste your Discord channel webhook here. We’ll send a test embed to it.'),
        el('label', { class: 'cfg-label' }, 'Webhook URL'),
        webhookInput,
        webhookTestBtn,
        webhookStatus,
        confirmRow,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p2back' }, 'Back'),
            p2next
        ])
    ]);

    /* ------------------------------------ panel 3: log style -------------------------------- */
    const TIME_PRESETS = [
        { label: 'Default (same as plugin)', value: '[HH:mm:ss, dd:MM:yyyy]' },
        { label: 'EU 24h (dd/MM/yyyy HH:mm)', value: '[dd/MM/yyyy HH:mm]' },
        { label: 'US 12h (MM/dd/yyyy HH:mm)', value: '[MM/dd/yyyy HH:mm]' },
        { label: 'Time only (HH:mm:ss)', value: '[HH:mm:ss]' },
        { label: 'Custom…', value: '' }
    ];

    const radioEmbeds = el('label', { class: 'cfg-radio' }, [
        el('input', { type: 'radio', name: 'logstyle', value: 'embeds', checked: '' }),
        el('span', {}, 'Use embeds (recommended)')
    ]);
    const embedsExtra = el('div', { class: 'cfg-inline' }, [
        el('label', { class: 'cfg-label', for: 'embedAuthor' }, 'Author name'),
        el('input', { id: 'embedAuthor', class: 'cfg-input', type: 'text', value: state.embedAuthor })
    ]);

    const radioPlain = el('label', { class: 'cfg-radio' }, [
        el('input', { type: 'radio', name: 'logstyle', value: 'plain' }),
        el('span', {}, 'Use plain text')
    ]);

    const dtPresetSel = el('select', { class: 'cfg-input cfg-input--select', id: 'dtPreset' });
    TIME_PRESETS.forEach(p => dtPresetSel.appendChild(el('option', { value: p.value }, p.label)));

    const dtCustomWrap = el('div', { class: 'cfg-inline', style: 'margin-top:.4rem;' }, [
        el('label', { class: 'cfg-label', for: 'plainFmt' }, 'Custom pattern'),
        el('input', { id: 'plainFmt', class: 'cfg-input', type: 'text', value: state.plainTimeFmt }),
        el('p', { class: 'cfg-note', id: 'fmtPrev' }, 'Preview: ')
    ]);

    const plainExtra = el('div', { class: 'cfg-plain', style: 'display:none' }, [
        el('label', { class: 'cfg-label' }, 'Server name (optional)'),
        el('input', { id: 'plainName', class: 'cfg-input', type: 'text', placeholder: 'e.g. Survival, Creative' }),
        el('p', { class: 'cfg-note' }, 'Useful if you run multiple servers behind a proxy.'),
        el('label', { class: 'cfg-label' }, 'Timestamp format'),
        dtPresetSel,
        dtCustomWrap
    ]);

    const p3 = makePanel('3', '3) Log Style', [
        el('p', { class: 'cfg-note' }, 'Choose how DiscordLogger should send messages to Discord.'),
        radioEmbeds,
        embedsExtra,
        radioPlain,
        plainExtra,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p3back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p3next' }, 'Next')
        ])
    ]);

    /* ------------------------------------ panel 4: what to log ------------------------------ */
    const p4content = el('div', { class: 'cfg-logwrap' });
    const p4 = makePanel('4', '4) What do you want to log?', [
        el('p', { class: 'cfg-note' }, 'Turn individual log types on or off.'),
        p4content,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p4back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p4next' }, 'Next')
        ])
    ]);

    function buildLogUIFromOptions() {
        p4content.innerHTML = '';

        // state.options comes from options.json
        const opts = state.options;
        if (!opts || !Array.isArray(opts.categories)) {
            // public site → no noisy dev message here
            const msg = el('p', { class: 'cfg-note' }, 'No logging options were found for this version.');
            p4content.appendChild(msg);
            return;
        }

        for (const cat of opts.categories) {
            const group = el('div', { class: 'cfg-loggroup' }, [
                el('h3', { class: 'cfg-sub' }, cat.label || human(cat.id || ''))
            ]);

            for (const item of (cat.items || [])) {
                // item.configKey: "log.player.join" → store/show "player.join"
                const keyNoPrefix = item.configKey ? item.configKey.replace(/^log\./, '') : '';
                const checked = state.toggles[keyNoPrefix] ?? item.default ?? true;

                const cb = el('input', {
                    type: 'checkbox',
                    'data-key': keyNoPrefix,
                    ...(checked ? { checked: '' } : {})
                });
                const row = el('label', { class: 'cfg-check' }, [
                    cb,
                    ' ',
                    item.label || human(item.id || keyNoPrefix)
                ]);

                cb.addEventListener('change', () => {
                    state.toggles[keyNoPrefix] = cb.checked;
                });

                group.appendChild(row);
            }

            p4content.appendChild(group);
        }
    }

    /* ----------------------------------- panel 5: embed colors ------------------------------- */
    const colorsWrap = el('div', { class: 'cfg-colwrap' });
    const p5 = makePanel('5', '5) Embed colors', [
        el('p', { class: 'cfg-note' }, 'Only used when “Use embeds” is selected.'),
        colorsWrap,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'p5back' }, 'Back'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'p5next' }, 'Generate')
        ])
    ]);

    function buildColorsUIFromOptions() {
        colorsWrap.innerHTML = '';
        const opts = state.options;
        if (!opts || !Array.isArray(opts.categories)) {
            return;
        }

        for (const cat of opts.categories) {
            const groupBox = el('div', { class: 'cfg-colgroup' }, [
                el('h3', { class: 'cfg-sub' }, cat.label || human(cat.id || ''))
            ]);

            for (const item of (cat.items || [])) {
                if (!item.colorKey) continue;
                const current = state.colors[item.colorKey] || '#5865F2';
                const row = el('div', { class: 'cfg-colrow' }, [
                    el('label', { class: 'cfg-label' }, item.label || human(item.id || item.colorKey)),
                    el('div', { class: 'cfg-colinputs' }, [
                        el('input', { type: 'color', value: current, 'data-key': item.colorKey }),
                        el('input', { type: 'text', class: 'cfg-input cfg-input--tiny', value: current, 'data-key': item.colorKey })
                    ])
                ]);
                groupBox.appendChild(row);
            }

            colorsWrap.appendChild(groupBox);
        }

        // wire inputs
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

    /* ------------------------------------ panel 6: result ------------------------------------ */
    const yamlOut = el('textarea', { class: 'cfg-yaml', readonly: '' });
    yamlOut.setAttribute('wrap', 'off'); // keep ASCII art
    const p6 = makePanel('6', 'Your config.yml', [
        yamlOut,
        el('div', { class: 'cfg-actions' }, [
            el('button', { class: 'cfg-btn', type: 'button', id: 'copyYaml' }, 'Copy'),
            el('button', { class: 'cfg-btn cfg-btn--primary', type: 'button', id: 'downloadYaml' }, 'Download')
        ])
    ]);

    /* ----------------------------------- assemble panels ------------------------------------ */
    wrapper.append(p1, p2, p3, p4, p5, p6);
    showPanel('1');

    /* ---------------------------------- panel navigation ------------------------------------ */
    $('#p1next').addEventListener('click', async () => {
        // ensure version assets loaded BEFORE we continue
        await onVersionChange();
        showPanel('2');
    });

    $('#p2back').addEventListener('click', () => showPanel('1'));
    $('#p3back').addEventListener('click', () => showPanel('2'));
    $('#p4back').addEventListener('click', () => showPanel('3'));
    $('#p5back').addEventListener('click', () => showPanel('4'));

    /* ---------------------------------- webhook logic --------------------------------------- */
    const setWebhookStatus = (txt, cls='') => {
        webhookStatus.textContent = txt;
        webhookStatus.className = `cfg-status ${cls}`;
        webhookStatus.style.display = txt ? '' : 'none';
    };

    async function postViaProxy(proxyUrl, webhookUrl, payload) {
        const res = await fetch(proxyUrl, {
            method: 'POST',
            headers: { 'Content-Type':'application/json' },
            body: JSON.stringify({ url: withWait(webhookUrl), payload })
        });
        return { ok: res.ok };
    }
    async function postDirect(webhookUrl, payload) {
        try {
            const res = await fetch(withWait(webhookUrl), {
                method:'POST',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify(payload)
            });
            return { ok: res.ok };
        } catch {
            return { ok:false };
        }
    }

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

    webhookTestBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setWebhookStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }

        setWebhookStatus('Sending test…', 'is-info');
        webhookTestBtn.disabled = true;
        confirmRow.style.display = 'none';

        // test payload is defined in generator.config.js
        const base = JSON.parse(JSON.stringify(window.DL_TEST_EMBED));
        if (base.embeds && base.embeds[0]) {
            base.embeds[0].timestamp = new Date().toISOString();
        }

        const proxy = (window.DL_PROXY_URL || '').trim();
        let res = { ok:false };
        if (proxy) {
            res = await postViaProxy(proxy, url, base);
        } else {
            res = await postDirect(url, base);
        }

        webhookTestBtn.disabled = false;

        if (res.ok) {
            setWebhookStatus('Test message sent. Check Discord.', 'is-ok');
        } else {
            setWebhookStatus("Couldn't send webhook, please check your webhook URL", 'is-error');
        }

        // user can always confirm manually
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

    $('#p2next').addEventListener('click', () => showPanel('3'));

    /* ---------------------------------- log style logic ------------------------------------- */
    const fmtInput = $('#plainFmt');
    const fmtPrev  = $('#fmtPrev');

    function updateLogStyleUI() {
        const useEmbeds = $('input[name="logstyle"]:checked').value === 'embeds';
        state.embedsEnabled = useEmbeds;
        embedsExtra.style.display = useEmbeds ? '' : 'none';
        plainExtra.style.display = useEmbeds ? 'none' : '';
    }

    function updateFmtPreview() {
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
    }

    $$('input[name="logstyle"]').forEach(r => r.addEventListener('change', updateLogStyleUI));
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

    $('#p3next').addEventListener('click', async () => {
        // ensure assets (template + options) are loaded before showing toggles
        await onVersionChange();
        buildLogUIFromOptions();
        showPanel('4');
    });

    // also init UI once
    updateLogStyleUI();
    updateFmtPreview();

    /* ---------------------------------- what to log → next ---------------------------------- */
    $('#p4next').addEventListener('click', () => {
        if (state.embedsEnabled) {
            // build colors UI from options
            buildColorsUIFromOptions();
            showPanel('5');
        } else {
            // build final YAML immediately
            yamlOut.value = buildYamlFromTemplate();
            showPanel('6');
        }
    });

    /* ---------------------------------- colors → result ------------------------------------- */
    $('#p5next').addEventListener('click', () => {
        yamlOut.value = buildYamlFromTemplate();
        showPanel('6');
    });

    /* ---------------------------------- result actions -------------------------------------- */
    $('#copyYaml').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(yamlOut.value); } catch {}
    });
    $('#downloadYaml').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([yamlOut.value], { type: 'text/yaml' }));
        document.body.appendChild(a);
        a.click();
        a.remove();
    });

    /* ---------------------------------- template → YAML ------------------------------------- */
    function buildYamlFromTemplate() {
        let tpl = state.template || '';
        const generatedAt = isoNice();

        // 1) If the template already has the placeholder, fill it
        // e.g. "# CONFIG VERSION V9, GENERATED ON WEBSITE ON {{GENERATED_AT}}"
        tpl = tpl.replace(/\{\{GENERATED_AT\}\}/g, generatedAt);

        // 2) If the template already has a hard-coded footer (like when someone forgets
        //    to remove it from config.template.yml), strip ALL of them so we can add
        //    exactly one at the end.
        //    This catches e.g.:
        //    "# CONFIG VERSION V9, GENERATED ON WEBSITE ON 2025-10-31 10:31:57"
        tpl = tpl.replace(/# CONFIG VERSION .*GENERATED ON WEBSITE ON.*\n?/gi, '');

        // 3) Replace normal tokens from options.json (logs + colors)
        const replacements = {};

        // plain/embeds
        replacements['{{TIME_FORMAT}}']   = state.plainTimeFmt;
        replacements['{{PLAIN_NAME}}']    = (state.plainServerName || '').replace(/"/g, '\\"');
        replacements['{{NICKNAMES}}']     = 'true';
        replacements['{{EMBEDS_AUTHOR}}'] = (state.embedAuthor || '').replace(/"/g, '\\"');

        const opts = state.options;
        if (opts && Array.isArray(opts.categories)) {
            for (const cat of opts.categories) {
                for (const item of (cat.items || [])) {
                    const logKey = item.configKey ? item.configKey.replace(/^log\./, '') : null;
                    const colorKey = item.colorKey || null;

                    if (logKey) {
                        const token = `{{LOG_${logKey}}}`;
                        const val   = state.toggles[logKey] ? 'true' : 'false';
                        replacements[token] = val;
                    }
                    if (colorKey) {
                        const token = `{{COLOR_${colorKey}}}`;
                        const val   = state.colors[colorKey] || '#5865F2';
                        replacements[token] = val;
                    }
                }
            }
        }

        for (const [token, val] of Object.entries(replacements)) {
            const re = new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
            tpl = tpl.replace(re, val);
        }

        // 4) embeds enabled/disabled
        tpl = tpl.replace(/embeds:\s*\n\s*enabled:\s*true/, `embeds:\n  enabled: ${state.embedsEnabled ? 'true' : 'false'}`);

        // 5) finally, add exactly one footer
        tpl += `\n# CONFIG VERSION ${state.configVersion.toUpperCase()}, GENERATED ON WEBSITE ON ${generatedAt}\n`;

        return tpl;
    }

    /* ---------------------------------- initial load ---------------------------------------- */
    // load default version immediately so step 2/3/4 works first time
    onVersionChange().catch(console.error);

    /* ---------------------------------- styles ---------------------------------------------- */
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
    #cfg-gen .cfg-loggroup { margin-bottom: .85rem; }
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
