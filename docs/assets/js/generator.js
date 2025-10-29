/* DiscordLogger – config generator (steps 1→6)
   Uses window.DL_* from generator.config.js
*/
(() => {
    const mount = document.getElementById('cfg-gen');
    if (!mount) return;

    // ---------- Utilities ----------
    const $ = (sel, el = document) => el.querySelector(sel);
    const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));
    const create = (tag, attrs = {}, children = []) => {
        const el = document.createElement(tag);
        Object.entries(attrs).forEach(([k, v]) => {
            if (k === 'class') el.className = v;
            else if (k === 'html') el.innerHTML = v;
            else el.setAttribute(k, v);
        });
        (Array.isArray(children) ? children : [children]).forEach(ch => {
            if (ch == null) return;
            el.appendChild(typeof ch === 'string' ? document.createTextNode(ch) : ch);
        });
        return el;
    };
    const nowIso = () => new Date().toISOString();
    const isDiscordWebhook = (url) => /^https:\/\/(?:ptb\.|canary\.)?discord(?:app)?\.com\/api\/webhooks\//i.test((url||'').trim());
    const withWait = (url) => url.includes('?') ? `${url}&wait=true` : `${url}?wait=true`;
    const groupBy = (obj) => {
        const out = {};
        Object.entries(obj).forEach(([k, v]) => {
            const [p, rest] = k.split('.', 2);
            (out[p] ??= {})[rest] = v;
        });
        return out;
    };

    // ---------- State ----------
    const DEFAULT_PLUGIN = Object.keys(window.DL_VERSIONS)[0] || '2.1.5';
    const state = {
        pluginVersion: DEFAULT_PLUGIN,
        configVersion: window.DL_VERSIONS[DEFAULT_PLUGIN]?.configVersion || 'v9',
        webhookUrl: '',
        webhookConfirmed: false,
        embedsEnabled: true,
        embedAuthor: 'Server Logs',
        toggles: { ...(window.DL_DEFAULT_TOGGLES || {}) },
        colors:  { ...(window.DL_DEFAULT_COLORS || {}) },
    };

    // ---------- Base UI scaffold ----------
    mount.innerHTML = '';
    const header = create('div', { class: 'cfg-head', html: `
    <h2>Config Generator</h2>
    <p>Select your plugin version, verify your webhook, then choose logging & colors. Finally, download a ready-to-use <code>config.yml</code>.</p>
  `});

    const steps = create('ol', { class: 'cfg-steps' });

    const makeStep = (n, title, bodyEls) => {
        const li = create('li', { class: 'cfg-step', 'data-step': String(n) }, [
            create('h3', { class: 'cfg-step__title' }, [
                create('span', { class: 'cfg-badge' }, String(n)), ' ', title
            ]),
            ...(Array.isArray(bodyEls) ? bodyEls : [bodyEls])
        ]);
        return li;
    };

    // ---------- Step 1: plugin version ----------
    const versionSel = create('select', { class: 'cfg-input' });
    Object.keys(window.DL_VERSIONS).sort().forEach(v => {
        versionSel.appendChild(create('option', { value: v, ...(v === state.pluginVersion ? { selected: '' } : {}) }, v));
    });
    const cfgOut = create('div', { class: 'cfg-note' });
    const recomputeCfg = () => {
        state.pluginVersion = versionSel.value;
        state.configVersion = window.DL_VERSIONS[state.pluginVersion]?.configVersion || 'v9';
        cfgOut.textContent = `Config schema: ${state.configVersion}`;
    };
    versionSel.addEventListener('change', recomputeCfg);
    recomputeCfg();

    const step1 = makeStep(1, 'Select your plugin version', [
        create('label', { class: 'cfg-label' }, 'Plugin version'),
        versionSel,
        cfgOut,
        create('div', { class: 'cfg-nav' }, [
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 's1next', type: 'button' }, 'Next')
        ])
    ]);

    // ---------- Step 2: webhook + test ----------
    const webhookInput = create('input', {
        class: 'cfg-input', type: 'url',
        placeholder: 'https://discord.com/api/webhooks/…'
    });
    const helper = create('div', { class: 'cfg-hint' }, 'We’ll send your test embed to confirm this webhook works.');
    const testBtn = create('button', { class: 'cfg-btn', type: 'button' }, 'Send test');
    const statusBox = create('div', { class: 'cfg-status' });
    const confirmWrap = create('div', { class: 'cfg-confirm', style: 'display:none' }, [
        create('span', { class: 'cfg-note' }, 'Did you receive the test in Discord?'),
        create('div', { class: 'cfg-actions' }, [
            create('button', { class: 'cfg-btn', id: 's2yes', type: 'button' }, 'Yes, continue'),
            create('button', { class: 'cfg-btn cfg-btn--ghost', id: 's2no', type: 'button' }, 'No, try again')
        ])
    ]);
    const step2 = makeStep(2, 'Add and test your Discord webhook', [
        create('label', { class: 'cfg-label' }, 'Webhook URL'),
        webhookInput,
        helper,
        testBtn,
        statusBox,
        confirmWrap,
        create('details', { class: 'cfg-details' }, [
            create('summary', {}, 'Having CORS trouble?'),
            create('p', {}, 'Set a Cloudflare Worker proxy and assign:'),
            create('code', { class: 'cfg-code' }, 'window.DL_PROXY_URL = "https://your-worker.workers.dev/relay"')
        ]),
        create('div', { class: 'cfg-nav' }, [
            create('button', { class: 'cfg-btn', 'data-back': '', type: 'button' }, 'Back'),
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 's2next', type: 'button', disabled: '' }, 'Next')
        ])
    ]);

    // ---------- Step 3: embeds/plain ----------
    const radioEmbeds = create('label', { class: 'cfg-check' }, [
        create('input', { type: 'radio', name: 'embedMode', value: 'embeds', checked: '' }),
        create('span', {}, 'Use embeds (recommended)')
    ]);
    const labelAuthor = create('label', { class: 'cfg-label', for: 'embedAuthor' }, 'Embed author');
    const inputAuthor = create('input', { id: 'embedAuthor', class: 'cfg-input', type: 'text', value: state.embedAuthor });

    const radioPlain = create('label', { class: 'cfg-check' }, [
        create('input', { type: 'radio', name: 'embedMode', value: 'plain' }),
        create('span', {}, 'Use plain text')
    ]);

    const step3 = makeStep(3, 'Pick embed style', [
        radioEmbeds,
        labelAuthor,
        inputAuthor,
        radioPlain,
        create('div', { class: 'cfg-nav' }, [
            create('button', { class: 'cfg-btn', 'data-back': '', type: 'button' }, 'Back'),
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 's3next', type: 'button' }, 'Next')
        ])
    ]);

    // ---------- Step 4: toggles ----------
    const makeToggleBox = (legend, keys) => {
        const fs = create('fieldset', { class: 'cfg-fieldset' }, [
            create('legend', {}, legend)
        ]);
        keys.forEach(k => {
            const lbl = create('label', { class: 'cfg-check' });
            const cb = create('input', { type: 'checkbox', 'data-key': k, ...(state.toggles[k] ? { checked: '' } : {}) });
            lbl.appendChild(cb);
            lbl.append(' ', k.split('.').slice(-1)[0]);
            fs.appendChild(lbl);
        });
        return fs;
    };

    const step4 = makeStep(4, 'Choose what to log', [
        makeToggleBox('Player', [
            'log.player.join','log.player.quit','log.player.chat','log.player.command',
            'log.player.death','log.player.teleport','log.player.gamemode'
        ]),
        makeToggleBox('Server', [
            'log.server.start','log.server.stop','log.server.command','log.server.explosion'
        ]),
        makeToggleBox('Moderation', [
            'log.moderation.ban','log.moderation.unban','log.moderation.kick',
            'log.moderation.op','log.moderation.deop','log.moderation.whitelist_toggle','log.moderation.whitelist'
        ]),
        create('div', { class: 'cfg-nav' }, [
            create('button', { class: 'cfg-btn', 'data-back': '', type: 'button' }, 'Back'),
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 's4next', type: 'button' }, 'Next')
        ])
    ]);

    // ---------- Step 5: colors ----------
    const colorGrid = create('div', { class: 'cfg-colors' });
    const renderColorGrid = () => {
        colorGrid.innerHTML = '';
        Object.entries(state.colors).forEach(([k, hex]) => {
            const row = create('div', { class: 'cfg-color' });
            const label = create('label', { class: 'cfg-label' }, k);
            const pick = create('input', { type: 'color', value: hex, 'data-key': k });
            const hexBox = create('input', { class: 'cfg-input cfg-hex', type: 'text', value: hex, maxlength: '7', 'data-key': k });
            const wrap = create('div', { class: 'cfg-colorpick' }, [pick, hexBox]);
            row.append(label, wrap);
            colorGrid.appendChild(row);
        });

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
                if (!v.startsWith('#')) v = '#' + v;
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
        create('p', { class: 'cfg-hint' }, 'Keep the defaults or customize.'),
        colorGrid,
        create('div', { class: 'cfg-nav' }, [
            create('button', { class: 'cfg-btn', 'data-back': '', type: 'button' }, 'Back'),
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 's5next', type: 'button' }, 'Generate')
        ])
    ]);

    // ---------- Step 6: YAML output ----------
    const yamlOut = create('textarea', { class: 'cfg-yaml', id: 'yamlOut', spellcheck: 'false', readonly: '' });
    const footerLine = () => `# CONFIG VERSION ${state.configVersion.toUpperCase()}, SHIPPED WITH V${state.pluginVersion}`;

    const step6 = makeStep(6, 'Your config.yml', [
        yamlOut,
        create('div', { class: 'cfg-row' }, [
            create('button', { class: 'cfg-btn', id: 'btnCopy', type: 'button' }, 'Copy'),
            create('button', { class: 'cfg-btn cfg-btn--primary', id: 'btnDownload', type: 'button' }, 'Download')
        ]),
        create('p', { class: 'cfg-fine', id: 'verFooter' })
    ]);

    // ---------- Assemble & basic flow ----------
    [step1, step2, step3, step4, step5, step6].forEach(li => steps.appendChild(li));
    mount.append(header, steps);

    const showStep = (n) => {
        $$('.cfg-step', steps).forEach(li => li.classList.toggle('is-active', li.dataset.step === String(n)));
        window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'smooth' });
    };
    showStep(1);

    // Nav
    $('#s1next').addEventListener('click', () => showStep(2));
    $$('[data-back]').forEach(b => b.addEventListener('click', () => {
        const n = Number($('.cfg-step.is-active')?.dataset.step || '2');
        showStep(Math.max(1, n - 1));
    }));

    // Step 2 handlers (test webhook)
    const setStatus = (txt, cls) => { statusBox.textContent = txt; statusBox.className = `cfg-status ${cls||''}`; };
    const postViaProxy = async (proxyUrl, webhookUrl, payload) => {
        const res = await fetch(proxyUrl, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: withWait(webhookUrl), payload })
        });
        const text = await res.text().catch(() => '');
        let data = null; try { data = text ? JSON.parse(text) : null; } catch {}
        return { ok: res.ok, status: res.status, data, text };
    };
    const postDirect = async (webhookUrl, payload) => {
        try {
            const res = await fetch(withWait(webhookUrl), {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            let json = null; try { json = await res.json(); } catch {}
            return { ok: res.ok, status: res.status, data: json };
        } catch (e) {
            return { ok: false, status: 0, error: String(e) };
        }
    };

    webhookInput.addEventListener('input', () => {
        state.webhookUrl = webhookInput.value.trim();
        $('#s2next').disabled = !isDiscordWebhook(state.webhookUrl) || !state.webhookConfirmed;
    });

    testBtn.addEventListener('click', async () => {
        const url = webhookInput.value.trim();
        if (!isDiscordWebhook(url)) {
            setStatus('Please enter a valid Discord webhook URL.', 'is-error');
            return;
        }
        setStatus('Sending test…', 'is-info');
        testBtn.disabled = true;
        confirmWrap.style.display = 'none';

        const payload = { ...(window.DL_TEST_EMBED || {}), embeds: (window.DL_TEST_EMBED?.embeds || []).map(e => ({ ...e, timestamp: nowIso() })) };
        const proxy = (window.DL_PROXY_URL || '').trim();

        let res;
        try {
            res = proxy ? await postViaProxy(proxy, url, payload) : await postDirect(url, payload);
        } catch (e) {
            res = { ok: false, status: 0, error: String(e) };
        } finally {
            testBtn.disabled = false;
        }

        if (res.ok) {
            setStatus('We successfully posted the test embed. Check your channel.', 'is-ok');
            confirmWrap.style.display = '';
        } else {
            const msg = res?.data?.message || res?.text || res?.error || `HTTP ${res.status}`;
            setStatus(`We couldn’t verify from the browser (host/CORS). Check Discord manually. Details: ${msg}`, 'is-error');
            confirmWrap.style.display = '';
        }
    });

    $('#s2yes').addEventListener('click', () => {
        state.webhookConfirmed = true;
        setStatus('Great! Webhook confirmed.', 'is-ok');
        $('#s2next').disabled = !isDiscordWebhook(state.webhookUrl) || !state.webhookConfirmed;
    });
    $('#s2no').addEventListener('click', () => {
        state.webhookConfirmed = false;
        setStatus('Double-check the URL and try again.', 'is-info');
        $('#s2next').disabled = true;
    });
    $('#s2next').addEventListener('click', () => showStep(3));

    // Step 3 handlers
    $$('.cfg-check input[name="embedMode"]').forEach(r => {
        r.addEventListener('change', () => {
            state.embedsEnabled = r.value === 'embeds';
            $('#embedAuthor').disabled = !state.embedsEnabled;
        });
    });
    $('#embedAuthor').addEventListener('input', e => state.embedAuthor = e.target.value);
    $('#s3next').addEventListener('click', () => showStep(4));

    // Step 4 handlers
    $$('.cfg-fieldset input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => state.toggles[cb.dataset.key] = cb.checked);
    });
    $('#s4next').addEventListener('click', () => showStep(5));

    // Step 5 handlers
    $('#s5next').addEventListener('click', () => {
        $('#yamlOut').value = buildYaml();
        $('#verFooter').textContent = footerLine();
        showStep(6);
    });

    // Step 6 handlers
    $('#btnCopy').addEventListener('click', async () => {
        await navigator.clipboard.writeText($('#yamlOut').value);
        toast('Copied!');
    });
    $('#btnDownload').addEventListener('click', () => {
        const a = document.createElement('a');
        a.download = 'config.yml';
        a.href = URL.createObjectURL(new Blob([$('#yamlOut').value], { type: 'text/yaml' }));
        document.body.appendChild(a); a.click(); a.remove();
    });

    // ---------- YAML builder ----------
    function buildYaml() {
        const L = [];

        // webhook
        L.push('webhook:');
        L.push(`  url: "${state.webhookUrl}"`);
        L.push('');

        // embeds block (single block with enabled, author, and colors)
        L.push('embeds:');
        L.push(`  enabled: ${state.embedsEnabled ? 'true' : 'false'}`);
        L.push(`  author: "${state.embedAuthor.replace(/"/g, '\\"')}"`);
        L.push('  colors:');
        const colorGroups = groupBy(state.colors); // player/server/moderation
        Object.entries(colorGroups).forEach(([grp, obj]) => {
            L.push(`    ${grp}:`);
            Object.entries(obj).forEach(([k, hex]) => L.push(`      ${k}: "${hex}"`));
        });
        L.push('');

        // toggles
        L.push('log:');
        const toggleGroups = groupBy(state.toggles);
        Object.entries(toggleGroups).forEach(([grp, obj]) => {
            L.push(`  ${grp}:`);
            Object.entries(obj).forEach(([k, v]) => L.push(`    ${k}: ${v ? 'true' : 'false'}`));
        });
        L.push('');
        L.push(footerLine());
        L.push('');
        return L.join('\n');
    }

    // ---------- Tiny styles (scoped, uses your theme vars) ----------
    const style = document.createElement('style');
    style.textContent = `
    .cfg-steps{list-style:none;margin:0;padding:0}
    .cfg-step{display:none;margin:1.25rem 0 1.75rem}
    .cfg-step.is-active{display:block}
    .cfg-step__title{margin:0 0 .5rem}
    .cfg-badge{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:999px;background:color-mix(in oklab,var(--accent) 22%, transparent);color:var(--accent-fg);font-weight:700;margin-right:.5rem}

    .cfg-label{font-weight:600;display:block;margin:.25rem 0 .35rem}
    .cfg-input{width:100%;max-width:720px;padding:.6rem .75rem;border-radius:10px;border:1px solid var(--border);background:var(--bg);color:var(--fg);font:inherit}
    .cfg-hint,.cfg-note{color:var(--muted);margin:.35rem 0}
    .cfg-details{margin:.6rem 0}
    .cfg-code{display:inline-block;padding:.15rem .4rem;background:var(--code-bg);border:1px solid var(--border);border-radius:.4rem}

    .cfg-btn{padding:.55rem .9rem;border-radius:10px;border:1px solid var(--border);background:color-mix(in oklab,var(--fg) 6%, transparent);color:var(--fg);cursor:pointer}
    .cfg-btn--ghost{background:transparent}
    .cfg-btn--primary{border-color:color-mix(in oklab,var(--accent) 50%, var(--border));background:color-mix(in oklab,var(--accent) 16%, transparent);color:var(--accent-fg)}

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

    .cfg-colors{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:.75rem}
    .cfg-color{border:1px solid var(--border);border-radius:.75rem;padding:.6rem}
    .cfg-colorpick{display:grid;grid-template-columns:46px 1fr;gap:.5rem;align-items:center}
    .cfg-hex{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace}

    .cfg-yaml{width:100%;min-height:360px;border:1px solid var(--border);border-radius:.75rem;padding:.9rem;background:var(--code-bg);color:var(--fg);font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace}
  `;
    document.head.appendChild(style);

    // ---------- Tiny toast ----------
    function toast(msg){
        const el = create('div',{class:'gen__toast'},msg);
        const st=document.createElement('style');
        st.textContent=`
      .gen__toast{position:fixed;left:50%;bottom:18px;transform:translate(-50%,10px);background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:.75rem;padding:.5rem .8rem;opacity:0;transition:all .2s ease;z-index:9999}
      .gen__toast.live{opacity:1;transform:translate(-50%,0)}
    `;
        document.head.appendChild(st);
        document.body.appendChild(el);
        requestAnimationFrame(()=>el.classList.add('live'));
        setTimeout(()=>{el.classList.remove('live');setTimeout(()=>el.remove(),180);},1200);
    }
})();
