/* DiscordLogger – config generator (steps 1→6)
   Uses window.DL_* from generator.config.js when present.
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    // -------- Utilities --------
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
    const human = (s) => s.replace(/[_\-]+/g,' ').replace(/\b\w/g, m => m.toUpperCase());
    const isDiscordWebhook = (u) => /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_\-]+/i.test((u||'').trim());
    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;
    const nowIso = () => new Date().toISOString();
    const isoNice = () => {
        const d = new Date();
        return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
    };

    // Normalize toggles: accept with/without "log." prefix, ensure advancement + whitelist_edit
    const normalizeToggles = (t) => {
        const out = {};
        for (const [k,v] of Object.entries(t || {})) {
            const key = k.startsWith('log.') ? k.slice(4) : k; // strip "log."
            out[key] = !!v;
        }
        if (out['player.advancement'] === undefined) out['player.advancement'] = true;
        // migrate whitelist → whitelist_edit if needed
        if (out['moderation.whitelist_edit'] === undefined && out['moderation.whitelist'] !== undefined) {
            out['moderation.whitelist_edit'] = !!out['moderation.whitelist'];
            delete out['moderation.whitelist'];
        }
        return out;
    };

    // Ensure color map has needed keys
    const ensureColors = (c) => {
        const out = { ...(c||{}) };
        out['player.advancement'] ??= '#2ECC71';
        return out;
    };

    // Group "a.b" -> {a: {b:val, ...}}
    const groupByDot = (obj) => {
        const g = {};
        for (const [k,v] of Object.entries(obj||{})) {
            const [a,b] = k.split('.',2);
            (g[a] ??= {})[b] = v;
        }
        return g;
    };

    // -------- State --------
    const DEFAULT_PLUGIN = Object.keys(window.DL_VERSIONS || {'2.1.5':{configVersion:'v9'}})[0];
    const initialToggles = normalizeToggles(window.DL_DEFAULT_TOGGLES || {
        'player.join': true, 'player.quit': true, 'player.chat': true, 'player.command': true,
        'player.death': true, 'player.teleport': true, 'player.gamemode': true, 'player.advancement': true,
        'server.start': true, 'server.stop': true, 'server.command': true, 'server.explosion': true,
        'moderation.ban': true, 'moderation.unban': true, 'moderation.kick': true,
        'moderation.op': true, 'moderation.deop': true, 'moderation.whitelist_toggle': true, 'moderation.whitelist_edit': true
    });
    const initialColors = ensureColors(window.DL_DEFAULT_COLORS || {
        'player.join':'#57F287','player.quit':'#ED4245','player.chat':'#5865F2','player.command':'#FEE75C','player.death':'#ED4245','player.advancement':'#2ECC71','player.teleport':'#3498DB','player.gamemode':'#9B59B6',
        'server.start':'#43B581','server.stop':'#ED4245','server.command':'#EB459E','server.explosion':'#E74C3C',
        'moderation.ban':'#FF3B30','moderation.unban':'#FF3B30','moderation.kick':'#FF3B30','moderation.op':'#FF3B30','moderation.deop':'#FF3B30','moderation.whitelist_toggle':'#1ABC9C','moderation.whitelist':'#16A085'
    });

    const state = {
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: (window.DL_VERSIONS?.[DEFAULT_PLUGIN]?.configVersion) || 'v9',
        webhookUrl: '',
        webhookConfirmed: false,
        embedsEnabled: true,
        embedAuthor: 'Server Logs',
        plainServerName: '',
        plainTimeFmt: '[HH:mm:ss, dd:MM:yyyy]',
        toggles: {...initialToggles},
        colors: {...initialColors},
    };

    // -------- UI Scaffold --------
    mount.innerHTML = '';
    const header = el('div', {class:'cfg-head', html: `
    <h2>Config Generator</h2>
    <p>Select your plugin version, verify your webhook, choose log style & options, then download <code>config.yml</code>.</p>
  `});
    const steps = el('ol', {class:'cfg-steps'});
    const makeStep = (n, title, body) => el('li', {class:'cfg-step', 'data-step':String(n)}, [
        el('h3', {class:'cfg-step__title'}, [ el('span',{class:'cfg-badge'}, String(n)), ' ', title ]),
        ...(Array.isArray(body)?body:[body]),
    ]);

    // --- Step 1: version ---
    const versionSel = el('select', {class:'cfg-input'});
    Object.keys(window.DL_VERSIONS || {[DEFAULT_PLUGIN]:{configVersion:'v9'}})
        .sort()
        .forEach(v => versionSel.appendChild(el('option', {value:v, ...(v===state.pluginVersion ? {selected:''}:{})}, v)));
    const cfgOut = el('div', {class:'cfg-note'});
    const recomputeCfg = () => {
        state.pluginVersion  = versionSel.value;
        state.configVersion  = window.DL_VERSIONS?.[state.pluginVersion]?.configVersion || 'v9';
        cfgOut.textContent   = `Config schema: ${state.configVersion}`;
    };
    versionSel.addEventListener('change', recomputeCfg);
    recomputeCfg();

    const step1 = makeStep(1, 'Select your plugin version', [
        el('label',{class:'cfg-label'},'Plugin version'),
        versionSel,
        cfgOut,
        el('div',{class:'cfg-nav'},[
            el('button',{class:'cfg-btn cfg-btn--primary', id:'s1next', type:'button'},'Next')
        ])
    ]);

    // --- Step 2: webhook test ---
    const webhookInput = el('input',{class:'cfg-input',type:'url',placeholder:'https://discord.com/api/webhooks/…'});
    const helper = el('div',{class:'cfg-hint'},'We’ll send your test embed to confirm this webhook works.');
    const testBtn = el('button',{class:'cfg-btn',type:'button'},'Send test');
    const statusBox = el('div',{class:'cfg-status', style:'display:none'}); // hidden until used
    const confirmWrap = el('div',{class:'cfg-confirm',style:'display:none'},[
        el('span',{class:'cfg-note'},'Did you receive the test in Discord?'),
        el('div',{class:'cfg-actions'},[
            el('button',{class:'cfg-btn', id:'s2yes', type:'button'},'Yes, continue'),
            el('button',{class:'cfg-btn cfg-btn--ghost', id:'s2no', type:'button'},'No, try again')
        ])
    ]);
    const step2 = makeStep(2, 'Add and test your Discord webhook', [
        el('label',{class:'cfg-label'},'Webhook URL'),
        webhookInput,
        helper,
        testBtn,
        statusBox,
        el('div',{class:'cfg-nav'},[
            el('button',{class:'cfg-btn','data-back':'',type:'button'},'Back'),
            el('button',{class:'cfg-btn cfg-btn--primary', id:'s2next', type:'button', disabled:''},'Next')
        ])
    ]);

    // --- Step 3: log style ---
    const radioEmbeds = el('label',{class:'cfg-check'},[
        el('input',{type:'radio',name:'logstyle',value:'embeds',checked:''}),
        el('span',{},'Use embeds (recommended)')
    ]);
    const authorWrap = el('div',{class:'cfg-inline',style:''},[
        el('label',{class:'cfg-label',for:'embedAuthor'},'Author name'),
        el('input',{id:'embedAuthor',class:'cfg-input',type:'text',value:state.embedAuthor})
    ]);

    const radioPlain = el('label',{class:'cfg-check'},[
        el('input',{type:'radio',name:'logstyle',value:'plain'}),
        el('span',{},'Use plain text')
    ]);
    const plainWrap = el('div',{class:'cfg-plain-wrap',style:'display:none'},[
        el('label',{class:'cfg-label'},'Server name (optional, useful for proxy servers)'),
        el('input',{id:'plainName',class:'cfg-input',type:'text',placeholder:'e.g. Survival', value:state.plainServerName}),
        el('div',{class:'cfg-label',style:'margin-top:.6rem'},'Timestamp format'),
        el('div',{class:'cfg-hint'},'Java DateTimeFormatter tokens. Click tokens to insert.'),
        el('div',{class:'cfg-chiprow'},[
            ...['[',']','HH','mm','ss','dd','MM','yyyy',':','-',' ',','].map(tok =>
                el('button',{type:'button',class:'cfg-chip','data-ins':tok},tok))
        ]),
        el('input',{id:'plainFmt',class:'cfg-input',type:'text',value:state.plainTimeFmt}),
        el('div',{class:'cfg-note', id:'fmtPrev'},'Preview: ')
    ]);

    const step3 = makeStep(3, 'Log Style', [
        radioEmbeds,
        authorWrap,
        radioPlain,
        plainWrap,
        el('div',{class:'cfg-nav'},[
            el('button',{class:'cfg-btn','data-back':'',type:'button'},'Back'),
            el('button',{class:'cfg-btn cfg-btn--primary', id:'s3next', type:'button'},'Next')
        ])
    ]);

    // --- Step 4: toggles ---
    const makeToggleBox = (legend, keys) => {
        const fs = el('fieldset',{class:'cfg-fieldset'},[ el('legend',{},legend) ]);
        keys.forEach(k => {
            const lbl = el('label',{class:'cfg-check'});
            const cb  = el('input',{type:'checkbox','data-key':k, ...(state.toggles[k] ? {checked:''}:{})});
            lbl.appendChild(cb);
            const nice = human(k.split('.').slice(-1)[0]);  // "join" -> "Join"
            lbl.append(' ', nice);
            fs.appendChild(lbl);
        });
        return fs;
    };
    const step4 = makeStep(4, 'Choose what to log', [
        makeToggleBox('Player', [
            'player.join','player.quit','player.chat','player.command','player.death','player.advancement','player.teleport','player.gamemode'
        ]),
        makeToggleBox('Server', [
            'server.start','server.stop','server.command','server.explosion'
        ]),
        makeToggleBox('Moderation', [
            'moderation.ban','moderation.unban','moderation.kick','moderation.op','moderation.deop','moderation.whitelist_toggle','moderation.whitelist_edit'
        ]),
        el('div',{class:'cfg-nav'},[
            el('button',{class:'cfg-btn','data-back':'',type:'button'},'Back'),
            el('button',{class:'cfg-btn cfg-btn--primary', id:'s4next', type:'button'},'Next')
        ])
    ]);

    // --- Step 5: colors (embeds only) ---
    const colorGrid = el('div',{class:'cfg-colors'});
    const renderColorGrid = () => {
        colorGrid.innerHTML = '';
        const grouped = groupByDot(state.colors); // { player:{join:#...}, server:{...}, moderation:{...} }
        for (const [cat, items] of Object.entries(grouped)) {
            const sec = el('section',{class:'cfg-colsec'},[
                el('h4',{class:'cfg-colsec__title'}, human(cat))
            ]);
            for (const [k,hex] of Object.entries(items)) {
                const full = `${cat}.${k}`;
                const row = el('div',{class:'cfg-color'});
                const label = el('label',{class:'cfg-label'}, `${human(cat)} ${human(k)}`); // "Player Join"
                const pick  = el('input',{type:'color', value:hex, 'data-key':full});
                const hexBox= el('input',{class:'cfg-input cfg-hex', type:'text', value:hex, maxlength:'7','data-key':full});
                const wrap  = el('div',{class:'cfg-colorpick'},[pick,hexBox]);
                row.append(label, wrap);
                sec.appendChild(row);
            }
            colorGrid.appendChild(sec);
        }

        // Wire pickers
        $$('.cfg-color input[type=color]', colorGrid).forEach(inp => {
            inp.addEventListener('input', () => {
                const key = inp.dataset.key; const v = inp.value;
                state.colors[key] = v;
                const buddy = colorGrid.querySelector(`.cfg-hex[data-key="${key}"]`);
                if (buddy) buddy.value = v;
            });
        });
        $$('.cfg-hex', colorGrid).forEach(inp => {
            inp.addEventListener('input', () => {
                let v = inp.value.trim();
                if (!v.startsWith('#')) v = '#'+v;
                if (/^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(v)) {
                    const key = inp.dataset.key;
                    state.colors[key] = v;
                    const buddy = colorGrid.querySelector(`input[type=color][data-key="${key}"]`);
                    if (buddy) buddy.value = v;
                }
            });
        });
    };
    renderColorGrid();

    const step5 = makeStep(5, 'Set embed colors', [
        el('p',{class:'cfg-hint'},'These apply only when embeds are enabled.'),
        colorGrid,
        el('div',{class:'cfg-nav'},[
            el('button',{class:'cfg-btn','data-back':'',type:'button'},'Back'),
            el('button',{class:'cfg-btn cfg-btn--primary', id:'s5next', type:'button'},'Generate')
        ])
    ]);

    // --- Step 6: YAML output ---
    const yamlOut = el('textarea',{class:'cfg-yaml', id:'yamlOut', spellcheck:'false', readonly:''});
    const step6 = makeStep(6, 'Your config.yml', [
        yamlOut,
        el('div',{class:'cfg-row'},[
            el('button',{class:'cfg-btn', id:'btnCopy', type:'button'},'Copy'),
            el('button',{class:'cfg-btn cfg-btn--primary', id:'btnDownload', type:'button'},'Download')
        ])
    ]);

    // Assemble
    [step1, step2, step3, step4, step5, step6].forEach(s => steps.appendChild(s));
    mount.append(header, steps);

    // Step visibility (no auto-scroll)
    const showStep = (n) => {
        $$('.cfg-step', steps).forEach(li => li.classList.toggle('is-active', li.dataset.step === String(n)));
    };
    showStep(1);

    // --- Nav
    $('#s1next').addEventListener('click', () => showStep(2));
    $$('[data-back]').forEach(b => b.addEventListener('click', () => {
        const n = Number($('.cfg-step.is-active')?.dataset.step || '2');
        showStep(Math.max(1, n-1));
    }));

    // --- Step 2 logic (webhook test)
    const setStatus = (txt, cls) => {
        statusBox.textContent = txt;
        statusBox.className = `cfg-status ${cls||''}`;
        statusBox.style.display = txt ? '' : 'none';
    };

    const postViaProxy = async (proxyUrl, webhookUrl, payload) => {
        const res = await fetch(proxyUrl, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({ url: withWait(webhookUrl), payload })
        });
        const text = await res.text().catch(()=> '');
        let data = null; try { data = text ? JSON.parse(text) : null; } catch {}
        return { ok: res.ok, status: res.status, data, text };
    };
    const postDirect = async (webhookUrl, payload) => {
        try {
            const res = await fetch(withWait(webhookUrl), {
                method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload)
            });
            let json = null; try { json = await res.json(); } catch {}
            return { ok: res.ok, status: res.status, data: json };
        } catch (e) {
            return { ok:false, status:0, error:String(e) };
        }
    };

    const validateNext2 = () => {
        $('#s2next').disabled = !(isDiscordWebhook(state.webhookUrl) && state.webhookConfirmed);
    };

    webhookInput.addEventListener('input', () => {
        state.webhookUrl = webhookInput.value.trim();
        validateNext2();
    });

    testBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }
        setStatus('Sending test…','is-info');
        testBtn.disabled = true;
        confirmWrap.style.display = 'none';

        // Build test payload (timestamp now)
        const base = window.DL_TEST_EMBED || {};
        const payload = {
            ...base,
            embeds: (base.embeds||[]).map(e => ({...e, timestamp: nowIso()}))
        };
        const proxy = (window.DL_PROXY_URL || '').trim();

        let res;
        try {
            res = proxy ? await postViaProxy(proxy, url, payload) : await postDirect(url, payload);
        } catch {
            res = { ok:false, status:0 };
        } finally {
            testBtn.disabled = false;
        }

        if (res.ok) {
            setStatus('We successfully posted the test embed. Check your channel.', 'is-ok');
            confirmWrap.style.display = '';
        } else {
            // Requested generic error message
            setStatus("Couldn't send webhook, please check your webhook URL", 'is-error');
            confirmWrap.style.display = '';
        }
    });

    $('#s2yes').addEventListener('click', () => {
        state.webhookConfirmed = true;
        setStatus('Great! Webhook confirmed.','is-ok');
        validateNext2();
    });
    $('#s2no').addEventListener('click', () => {
        state.webhookConfirmed = false;
        setStatus('Double-check the URL and try again.','is-info');
        validateNext2();
    });
    $('#s2next').addEventListener('click', () => showStep(3));

    // --- Step 3: log style
    const updateStyleUI = () => {
        const useEmbeds = $('input[name="logstyle"]:checked').value === 'embeds';
        state.embedsEnabled = useEmbeds;
        authorWrap.style.display = useEmbeds ? '' : 'none';
        plainWrap.style.display  = useEmbeds ? 'none' : '';
    };
    $$('.cfg-check input[name="logstyle"]').forEach(r => r.addEventListener('change', updateStyleUI));
    $('#embedAuthor').addEventListener('input', e => state.embedAuthor = e.target.value);
    $('#plainName').addEventListener('input', e => state.plainServerName = e.target.value);
    // token inserter + preview for DateTimeFormatter
    const fmtInput = $('#plainFmt');
    const fmtPrev  = $('#fmtPrev');
    const applyPreview = () => {
        let p = fmtInput.value;
        const d = new Date();
        const map = {
            'HH': pad2(d.getHours()),
            'mm': pad2(d.getMinutes()),
            'ss': pad2(d.getSeconds()),
            'dd': pad2(d.getDate()),
            'MM': pad2(d.getMonth()+1),
            'yyyy': String(d.getFullYear()),
        };
        // replace longest tokens first
        p = p.replace(/yyyy|HH|MM|dd|mm|ss/g, m => map[m] ?? m);
        fmtPrev.textContent = 'Preview: ' + p;
        state.plainTimeFmt = fmtInput.value;
    };
    fmtInput.addEventListener('input', applyPreview);
    $$('.cfg-chip').forEach(btn => btn.addEventListener('click', () => {
        const tok = btn.dataset.ins;
        const i = fmtInput.selectionStart ?? fmtInput.value.length;
        const j = fmtInput.selectionEnd ?? i;
        const v = fmtInput.value;
        fmtInput.value = v.slice(0,i) + tok + v.slice(j);
        fmtInput.focus();
        fmtInput.selectionStart = fmtInput.selectionEnd = i + tok.length;
        applyPreview();
    }));
    applyPreview();

    $('#s3next').addEventListener('click', () => showStep(4));

    // --- Step 4: toggles
    $$('.cfg-fieldset input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => state.toggles[cb.dataset.key] = cb.checked);
    });
    $('#s4next').addEventListener('click', () => {
        if (state.embedsEnabled) showStep(5);
        else {
            $('#yamlOut').value = buildYaml();
            showStep(6);
        }
    });

    // --- Step 5: colors
    $('#s5next').addEventListener('click', () => {
        $('#yamlOut').value = buildYaml();
        showStep(6);
    });

    // --- Step 6: actions
    $('#btnCopy').addEventListener('click', async () => {
        await navigator.clipboard.writeText($('#yamlOut').value);
        toast('Copied!');
    });
    $('#btnDownload').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([$('#yamlOut').value], {type:'text/yaml'}));
        document.body.appendChild(a); a.click(); a.remove();
    });

    // -------- YAML builder (includes ASCII art + sections) --------
    function buildYaml() {
        const L = [];

        // ASCII header
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
        // Webhook
        L.push('webhook:');
        L.push(`  url: "${state.webhookUrl}" # Discord webhook URL goes here, plugin will not function until present`);
        L.push('');
        // Format
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
        L.push(`  enabled: ${state.embedsEnabled ? 'true':'false'}`);
        L.push(`  author: "${(state.embedAuthor||'').replace(/"/g,'\\"')}" # Can be modified for proxy servers (e.g. Survival, Creative)`);
        // Colors (always include; ignored if embeds disabled)
        L.push('');
        L.push('  # Per-category colors (hex). Keys are case-insensitive; spaces become underscores.');
        L.push('  colors:');
        const colG = groupByDot(state.colors);
        for (const cat of ['player','server','moderation']) {
            const entries = Object.entries(colG[cat] || {});
            if (!entries.length) continue;
            L.push(`    ${cat}:`);
            for (const [k,hex] of entries) {
                const comment = (cat==='player' && k==='join') ? '  # green'
                    : (cat==='player' && k==='quit') ? '  # red'
                        : '';
                L.push(`      ${k}: "${hex}"${comment}`);
            }
        }
        L.push('');
        // LOGS banner
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
        // Toggles → YAML
        L.push('log:');
        const tg = groupByDot(state.toggles);
        const order = ['player','server','moderation'];
        for (const cat of order) {
            const entries = Object.entries(tg[cat] || {});
            if (!entries.length) continue;
            L.push(`  ${cat}:`);
            // keep a stable order
            const desired = cat==='player'
                ? ['join','quit','chat','command','death','advancement','teleport','gamemode']
                : cat==='server'
                    ? ['command','start','stop','explosion']
                    : ['ban','unban','kick','op','deop','whitelist_toggle','whitelist_edit'];
            for (const k of desired) {
                if (tg[cat] && Object.prototype.hasOwnProperty.call(tg[cat], k)) {
                    const desc = cat==='player' && k==='command' ? ' # Commands executed by a player in-game'
                        : cat==='server' && k==='command' ? ' # Commands executed via the server console/terminal'
                            : '';
                    L.push(`    ${k}: ${tg[cat][k] ? 'true':'false'}${desc}`);
                }
            }
            // include any extra keys (future-proof)
            for (const [k,v] of entries) {
                if (!desired.includes(k)) L.push(`    ${k}: ${v ? 'true':'false'}`);
            }
            L.push('');
        }

        L.push(`# CONFIG VERSION ${state.configVersion.toUpperCase()}, GENERATED ON WEBSITE ON ${isoNice()}`);
        L.push('');
        return L.join('\n');
    }

    // -------- Inline styles (scoped) --------
    const style = document.createElement('style');
    style.textContent = `
    .cfg-steps{list-style:none;margin:0;padding:0}
    .cfg-step{display:none;margin:1.25rem 0 1.75rem}
    .cfg-step.is-active{display:block}
    .cfg-step__title{margin:0 0 .5rem}
    .cfg-badge{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:999px;background:color-mix(in oklab,var(--accent) 22%, transparent);color:var(--accent-fg);font-weight:700;margin-right:.5rem}

    .cfg-label{font-weight:600;display:block;margin:.25rem 0 .35rem}
    .cfg-input{width:100%;max-width:720px;padding:.6rem .75rem;border-radius:10px;border:1px solid var(--border);background:var(--bg);color:var(--fg);font:inherit;box-sizing:border-box}
    .cfg-hint,.cfg-note{color:var(--muted);margin:.35rem 0}
    .cfg-inline{margin:.4rem 0}
    .cfg-chiprow{display:flex;flex-wrap:wrap;gap:.4rem;margin:.35rem 0 .5rem}
    .cfg-chip{border:1px solid var(--border);background:var(--bg);color:var(--fg);padding:.25rem .5rem;border-radius:.5rem;cursor:pointer}
    .cfg-chip:hover{background:color-mix(in oklab,var(--fg) 6%, transparent)}

    .cfg-btn{padding:.55rem .9rem;border-radius:10px;border:1px solid var(--border);background:color-mix(in oklab,var(--fg) 6%, transparent);color:var(--fg);cursor:pointer}
    .cfg-btn--ghost{background:transparent}
    .cfg-btn--primary{border-color:color-mix(in oklab,var(--accent) 50%, var(--border));background:color-mix(in oklab,var(--accent) 16%, transparent);color:var(--accent-fg)}
    .cfg-btn--primary[disabled]{opacity:.55;filter:grayscale(.35);cursor:not-allowed;border-color:var(--border);background:color-mix(in oklab,var(--fg) 4%, transparent);color:var(--muted)}

    .cfg-status{margin-top:.6rem;padding:.6rem .75rem;border-radius:10px;border:1px solid var(--border)}
    .cfg-status.is-ok{border-color:#16a34a33;background:#16a34a14}
    .cfg-status.is-error{border-color:#ef444433;background:#ef444414}
    .cfg-status.is-info{background:color-mix(in oklab,var(--fg) 5%, transparent)}

    .cfg-actions{display:flex;gap:.5rem;margin-top:.5rem}
    .cfg-nav{display:flex;gap:.6rem;margin-top:1rem}
    .cfg-row{display:flex;gap:.6rem;margin-top:.7rem}

    .cfg-check{display:inline-flex;align-items:center;gap:.5rem;margin:.25rem .8rem .25rem 0}
    .cfg-fieldset{border:1px solid var(--border);padding:.75rem .9rem;border-radius:.75rem;margin:.9rem 0}
    .cfg-fieldset legend{padding:0 .35rem;color:var(--muted)}

    .cfg-colors{display:grid;grid-template-columns:1fr;gap:1rem}
    .cfg-colsec{border:1px solid var(--border);border-radius:.9rem;padding:.8rem}
    .cfg-colsec__title{margin:.1rem 0 .6rem}
    .cfg-color{border:1px dashed var(--border);border-radius:.75rem;padding:.6rem}
    .cfg-colorpick{display:grid;grid-template-columns:46px minmax(0,1fr);gap:.5rem;align-items:center}
    .cfg-hex{width:100%}

    .cfg-yaml{width:100%;min-height:360px;border:1px solid var(--border);border-radius:.75rem;padding:.9rem;background:var(--code-bg);color:var(--fg);font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace}
  `;
    document.head.appendChild(style);

    // Tiny toast
    function toast(msg){
        const n = el('div',{class:'gen__toast'},msg);
        const st=document.createElement('style');
        st.textContent = `.gen__toast{position:fixed;left:50%;bottom:18px;transform:translate(-50%,10px);background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:.75rem;padding:.5rem .8rem;opacity:0;transition:all .2s ease;z-index:9999}
                      .gen__toast.live{opacity:1;transform:translate(-50%,0)}`;
        document.head.appendChild(st);
        document.body.appendChild(n);
        requestAnimationFrame(()=>n.classList.add('live'));
        setTimeout(()=>{n.classList.remove('live');setTimeout(()=>n.remove(),180);},1200);
    }
})();
