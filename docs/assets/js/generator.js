/* DiscordLogger – config.yml generator (clean, 1-step-at-a-time) */
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

    /* ----------------- initial data ----------------- */
    const DEFAULT_PLUGIN = Object.keys(window.DL_VERSIONS || {'2.1.5':{configVersion:'v9'}})[0];

    const normalizeToggles = (t) => {
        const out = {};
        for (const [k,v] of Object.entries(t || {})) {
            const key = k.startsWith('log.') ? k.slice(4) : k;
            out[key] = !!v;
        }
        // always make sure advancement exists
        if (out['player.advancement'] === undefined) out['player.advancement'] = true;
        // rename old whitelist -> whitelist_edit
        if (out['moderation.whitelist'] !== undefined && out['moderation.whitelist_edit'] === undefined) {
            out['moderation.whitelist_edit'] = !!out['moderation.whitelist'];
            delete out['moderation.whitelist'];
        }
        return out;
    };

    const ensureColors = (c={}) => {
        const out = { ...c };
        out['player.advancement'] ??= '#2ECC71';
        return out;
    };

    const state = {
        currentPanel: '1',
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: (window.DL_VERSIONS?.[DEFAULT_PLUGIN]?.configVersion) || 'v9',
        webhookUrl: '',
        webhookConfirmed: false,
        embedsEnabled: true,
        embedAuthor: 'Server Logs',
        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',
        toggles: normalizeToggles(window.DL_DEFAULT_TOGGLES || {
            'player.join': true, 'player.quit': true, 'player.chat': true, 'player.command': true,
            'player.death': true, 'player.advancement': true, 'player.teleport': true, 'player.gamemode': true,
            'server.start': true, 'server.stop': true, 'server.command': true, 'server.explosion': true,
            'moderation.ban': true, 'moderation.unban': true, 'moderation.kick': true,
            'moderation.op': true, 'moderation.deop': true,
            'moderation.whitelist_toggle': true, 'moderation.whitelist_edit': true,
        }),
        colors: ensureColors(window.DL_DEFAULT_COLORS || {
            'player.join':'#57F287','player.quit':'#ED4245','player.chat':'#5865F2','player.command':'#FEE75C','player.death':'#ED4245','player.advancement':'#2ECC71','player.teleport':'#3498DB','player.gamemode':'#9B59B6',
            'server.start':'#43B581','server.stop':'#ED4245','server.command':'#EB459E','server.explosion':'#E74C3C',
            'moderation.ban':'#FF3B30','moderation.unban':'#FF3B30','moderation.kick':'#FF3B30','moderation.op':'#FF3B30','moderation.deop':'#FF3B30','moderation.whitelist_toggle':'#1ABC9C','moderation.whitelist':'#16A085'
        }),
    };

    /* ----------------- layout ----------------- */
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
        // if we land on colors, but embeds are off, skip straight to result
        if (id === '5' && !state.embedsEnabled) {
            // build immediately
            yamlOut.value = buildYaml();
            showPanel('6');
        }
    };

    /* ----------------- Panel 1: version ----------------- */
    const versionSelect = el('select', {class:'cfg-input cfg-input--select'});
    Object.keys(window.DL_VERSIONS || {[DEFAULT_PLUGIN]:{configVersion:'v9'}})
        .sort()
        .forEach(v => {
            versionSelect.appendChild(el('option', {value:v, ...(v===state.pluginVersion ? {selected:''} : {})}, v));
        });

    const versionNote = el('p', {class:'cfg-note'});
    const updateVersionNote = () => {
        state.pluginVersion = versionSelect.value;
        state.configVersion = window.DL_VERSIONS?.[state.pluginVersion]?.configVersion || 'v9';
        versionNote.textContent = `Config schema: ${state.configVersion}`;
    };
    updateVersionNote();
    versionSelect.addEventListener('change', updateVersionNote);

    const p1 = makePanel('1', '1) Plugin version', [
        el('p', {class:'cfg-note'}, 'Pick the DiscordLogger version you installed. Multiple plugin versions can share a config version.'),
        el('label', {class:'cfg-label'}, 'Plugin version'),
        versionSelect,
        versionNote,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p1next'}, 'Next')
        ])
    ]);

    /* ----------------- Panel 2: webhook ----------------- */
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

    /* ----------------- Panel 3: log style ----------------- */

    // presets for Java-like time formats, but human
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

    const embedsExtra = el('div', {class:'cfg-inline', style:''}, [
        el('label', {class:'cfg-label', for:'embedAuthor'}, 'Author name (shown in embed header)'),
        el('input', {id:'embedAuthor', class:'cfg-input', type:'text', value: state.embedAuthor})
    ]);

    const radioPlain = el('label', {class:'cfg-radio'}, [
        el('input', {type:'radio', name:'logstyle', value:'plain'}),
        el('span', {}, 'Use plain text')
    ]);

    // nice, guided date/time
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
    const makeCheckbox = (key, label) => {
        const cb = el('input', {type:'checkbox', 'data-key':key, ...(state.toggles[key] ? {checked:''}:{})});
        const w  = el('label', {class:'cfg-check'}, [cb, ' ', label]);
        return w;
    };
    const p4 = makePanel('4', '4) What do you want to log?', [
        el('p', {class:'cfg-note'}, 'Toggle categories on/off.'),
        el('h3', {class:'cfg-sub'}, 'Player'),
        makeCheckbox('player.join', 'Join'),
        makeCheckbox('player.quit', 'Quit'),
        makeCheckbox('player.chat', 'Chat'),
        makeCheckbox('player.command', 'Command'),
        makeCheckbox('player.death', 'Death'),
        makeCheckbox('player.advancement', 'Advancement'),
        makeCheckbox('player.teleport', 'Teleport'),
        makeCheckbox('player.gamemode', 'Gamemode'),
        el('h3', {class:'cfg-sub'}, 'Server'),
        makeCheckbox('server.start', 'Start'),
        makeCheckbox('server.stop', 'Stop'),
        makeCheckbox('server.command', 'Command (console)'),
        makeCheckbox('server.explosion', 'Explosion'),
        el('h3', {class:'cfg-sub'}, 'Moderation'),
        makeCheckbox('moderation.ban', 'Ban'),
        makeCheckbox('moderation.unban', 'Unban'),
        makeCheckbox('moderation.kick', 'Kick'),
        makeCheckbox('moderation.op', 'OP'),
        makeCheckbox('moderation.deop', 'De-OP'),
        makeCheckbox('moderation.whitelist_toggle', 'Whitelist Toggle'),
        makeCheckbox('moderation.whitelist_edit', 'Whitelist Edit'),
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'p4back'}, 'Back'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'p4next'}, 'Next')
        ])
    ]);

    /* ----------------- Panel 5: embed colors ----------------- */
    const colorsWrap = el('div', {class:'cfg-colwrap'});
    const renderColors = () => {
        colorsWrap.innerHTML = '';
        const groups = { player:[], server:[], moderation:[] };
        for (const [key,val] of Object.entries(state.colors)) {
            const [cat, sub] = key.split('.',2);
            if (!groups[cat]) groups[cat] = [];
            groups[cat].push({sub, val});
        }
        ['player','server','moderation'].forEach(cat => {
            if (!groups[cat] || !groups[cat].length) return;
            const box = el('div', {class:'cfg-colgroup'}, [
                el('h3', {class:'cfg-sub'}, human(cat))
            ]);
            groups[cat].forEach(({sub,val}) => {
                const row = el('div', {class:'cfg-colrow'}, [
                    el('label', {class:'cfg-label'}, `${human(cat)} ${human(sub)}`),
                    el('div', {class:'cfg-colinputs'}, [
                        el('input', {type:'color', value:val, 'data-key':`${cat}.${sub}`}),
                        el('input', {type:'text', class:'cfg-input cfg-input--tiny', value:val, 'data-key':`${cat}.${sub}`})
                    ])
                ]);
                box.appendChild(row);
            });
            colorsWrap.appendChild(box);
        });

        // wiring
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
    };
    renderColors();

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
    // allow horizontal scroll to keep ASCII art intact
    yamlOut.setAttribute('wrap', 'off');

    const p6 = makePanel('6', 'Your config.yml', [
        yamlOut,
        el('div', {class:'cfg-actions'}, [
            el('button', {class:'cfg-btn', type:'button', id:'copyYaml'}, 'Copy'),
            el('button', {class:'cfg-btn cfg-btn--primary', type:'button', id:'downloadYaml'}, 'Download')
        ])
    ]);

    // assemble
    wrapper.append(p1, p2, p3, p4, p5, p6);
    // show only #1 at start
    showPanel('1');

    /* ----------------- wiring: top level ----------------- */
    $('#p1next').addEventListener('click', () => showPanel('2'));
    $('#p2back').addEventListener('click', () => showPanel('1'));
    $('#p3back').addEventListener('click', () => showPanel('2'));
    $('#p4back').addEventListener('click', () => showPanel('3'));
    $('#p5back').addEventListener('click', () => showPanel('4'));

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

    webhookTestBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setWebhookStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }

        setWebhookStatus('Sending test...', 'is-info');
        webhookTestBtn.disabled = true;
        confirmRow.style.display = 'none';

        // build exact-ish test
        const base = window.DL_TEST_EMBED || {
            content: null,
            embeds: [
                {
                    title: 'DiscordLogger Webhook Test',
                    description: 'Hello, this is a test of your webhook to confirm if it works, if you are seeing this message, it worked.\n\nIf you did not request a webhook test, confirm with other members of your server.',
                    url: 'https://discordlogger.godtiergamers.xyz',
                    color: 5814783,
                    author: {
                        name: 'DiscordLogger Webhook Test',
                        url: 'https://discordlogger.godtiergamers.xyz',
                        icon_url: 'https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png'
                    },
                    footer: {
                        text: 'DiscordLogger Webhook Test',
                        icon_url: 'https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png'
                    },
                    thumbnail: {
                        url: 'https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png'
                    }
                }
            ],
            attachments: []
        };
        if (base.embeds && base.embeds[0]) {
            base.embeds[0] = { ...base.embeds[0], timestamp: new Date().toISOString() };
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

        // we always let user confirm manually
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

    /* ----------------- log style logic ----------------- */
    const updateLogStyleUI = () => {
        const useEmbeds = $('input[name="logstyle"]:checked').value === 'embeds';
        state.embedsEnabled = useEmbeds;
        embedsExtra.style.display = useEmbeds ? '' : 'none';
        plainExtra.style.display = useEmbeds ? 'none' : '';
        // DO NOT force-show p5 here – that was the bug.
    };

    $$('input[name="logstyle"]').forEach(r => r.addEventListener('change', updateLogStyleUI));
    // datetime presets
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
            // custom
            fmtInput.disabled = false;
            fmtInput.focus();
        }
        updateFmtPreview();
    });
    fmtInput.addEventListener('input', updateFmtPreview);

    // init UI for step 3 only after we get there
    $('#p3next').addEventListener('click', () => showPanel('4'));
    $('#p3back').addEventListener('click', () => showPanel('2'));
    // but we still need defaults
    updateLogStyleUI();
    updateFmtPreview();

    /* ----------------- what to log ----------------- */
    $$('.cfg-check input[type=checkbox]', p4).forEach(cb => {
        cb.addEventListener('change', () => {
            state.toggles[cb.dataset.key] = cb.checked;
        });
    });

    $('#p4next').addEventListener('click', () => {
        // if embeds, go to colors; else, straight to YAML
        if (state.embedsEnabled) {
            showPanel('5');
        } else {
            yamlOut.value = buildYaml();
            showPanel('6');
        }
    });

    /* ----------------- colors -> result ----------------- */
    $('#p5next').addEventListener('click', () => {
        yamlOut.value = buildYaml();
        showPanel('6');
    });

    /* ----------------- result actions ----------------- */
    $('#copyYaml').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(yamlOut.value); } catch {}
    });
    $('#downloadYaml').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([yamlOut.value], {type:'text/yaml'}));
        document.body.appendChild(a); a.click(); a.remove();
    });

    /* ----------------- YAML builder ----------------- */
    function buildYaml() {
        const L = [];
        L.push(
            `####################################################################################################################################
#                                                                                                                                  #
#    /$$$$$$$  /$$                                               /$$ /$$                                                           #
#   | $$__  $$|__/                                              | $$| $$                                                           #
#   | $$  \\ $$ /$$  /$$$$$$$  /$$$$$$$  /$$$$$$   /$$$$$$   /$$$$$$$| $$        /$$$$$$   /$$$$$$   /$$$$$$   /$$$$$$   /$$$$$$    #
#   | $$  | $$| $$ /$$_____/ /$$_____/ /$$__  $$ /$$__  $$ /$$__  $$| $$       /$$__  $$ /$$__  $$ /$$__  $$ /$$__  $$ /$$__  $$   #
#   | $$  | $$| $$|  $$$$$$ | $$      | $$  \\ $$| $$  \\__/| $$  | $$| $$      | $$  \\ $$| $$  \\ $$| $$  \\ $$| $$$$$$$$| $$  \\__/   #
#   | $$  | $$| $$ \\____  $$| $$      | $$  | $$| $$      | $$  | $$| $$      | $$  | $$| $$  | $$| $$  | $$| $$_____/| $$         #
#   | $$$$$$$/| $$ /$$$$$$$/|  $$$$$$$|  $$$$$$/| $$      |  $$$$$$$| $$$$$$$$|  $$$$$$/|  $$$$$$$|  $$$$$$$|  $$$$$$$| $$         #
#   |_______/ |__/|_______/  \\_______/ \\______/ |__/       \\_______/|________/ \\______/  \\____  $$ \\____  $$ \\_______/|__/         #
#                                                                                     /$$  \\ $$ /$$  \\ $$                          #
#                                                                                    |  $$$$$$/|  $$$$$$/                          #
#                                                                                     \\______/  \\______/                           #
#                                                                                                                                  #
####################################################################################################################################

#############################
# D O C U M E N T A T I O N #
#############################

# Documentation for this config can be found at https://discordlogger.godtiergamers.xyz/config/${state.configVersion}/

###################
# WEBHOOK OPTIONS #
###################
`);
        L.push('webhook:');
        L.push(`  url: "${state.webhookUrl}" # Discord webhook URL goes here, plugin will not function until present`);
        L.push('');
        L.push(
            `##################
# FORMAT OPTIONS #
##################

format:
  # ONLY USED FOR PLAIN TEXT MESSAGES (EMBEDS DISABLED)
  # Usage (case-sensitive): HH=hours, mm=minutes, ss=seconds, dd=day, MM=month, yyyy=year
  time: "${state.plainTimeFmt}"
  # Only used for plain text, for embeds edit author name
  name: "${(state.plainServerName||'').replace(/"/g,'\\"')}"
  # Show nicknames (if set) as "Nickname (RealName)" in all player-related logs
  nicknames: true

#################
# EMBED OPTIONS #
#################
`);
        L.push('embeds:');
        L.push(`  enabled: ${state.embedsEnabled ? 'true' : 'false'}`);
        L.push(`  author: "${(state.embedAuthor||'').replace(/"/g,'\\"')}" # Can be modified for proxy servers (e.g. Survival, Creative)`);
        L.push('');
        L.push('  # Per-category colors (hex). Keys are case-insensitive; spaces become underscores.');
        L.push('  colors:');
        const group = {};
        for (const [k,v] of Object.entries(state.colors)) {
            const [cat,sub] = k.split('.',2);
            (group[cat] ??= {})[sub] = v;
        }
        for (const cat of ['player','server','moderation']) {
            if (!group[cat]) continue;
            L.push(`    ${cat}:`);
            for (const [sub,hex] of Object.entries(group[cat])) {
                L.push(`      ${sub}: "${hex}"`);
            }
        }
        L.push('');
        L.push(
            `####################################################################################
#                                                                                  #
#     _                      _                ___         _    _                   #
#    | |    ___  __ _  __ _ (_) _ _   __ _   / _ \\  _ __ | |_ (_) ___  _ _   ___   #
#    | |__ / _ \\/ _\` |/ _\` || || ' \\ / _\` | | (_) || '_ \\|  _|| |/ _ \\| ' \\ (_-<   #
#    |____|\\___/\\__, |\\__, ||_||_||_|\\__, |  \\___/ | .__/ \\__||_|\\___/|_||_|/__/   #
#               |___/ |___/          |___/         |_|                             #
#                                                                                  #
####################################################################################
`);
        L.push('log:');

        // group toggles
        const tg = {};
        for (const [k,v] of Object.entries(state.toggles)) {
            const [cat, sub] = k.split('.',2);
            (tg[cat] ??= {})[sub] = v;
        }

        // player
        L.push('  player:');
        ['join','quit','chat','command','death','advancement','teleport','gamemode'].forEach(k => {
            if (tg.player && k in tg.player) {
                L.push(`    ${k}: ${tg.player[k] ? 'true' : 'false'}`);
            }
        });
        L.push('');
        // server
        L.push('  server:');
        ['command','start','stop','explosion'].forEach(k => {
            if (tg.server && k in tg.server) {
                L.push(`    ${k}: ${tg.server[k] ? 'true' : 'false'}`);
            }
        });
        L.push('');
        // moderation
        L.push('  moderation:');
        ['ban','unban','kick','op','deop','whitelist_toggle','whitelist_edit'].forEach(k => {
            if (tg.moderation && k in tg.moderation) {
                L.push(`    ${k}: ${tg.moderation[k] ? 'true' : 'false'}`);
            }
        });
        L.push('');
        L.push(`# CONFIG VERSION ${state.configVersion.toUpperCase()}, GENERATED ON WEBSITE ON ${isoNice()}`);
        L.push('');
        return L.join('\n');
    }

    /* ----------------- inject styles just for generator ----------------- */
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
      overflow: auto;        /* horizontal & vertical */
      resize: vertical;
    }
  `;
    document.head.appendChild(style);
})();
