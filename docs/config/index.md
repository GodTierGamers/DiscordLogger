---
layout: default
title: Config Docs
description: Pick your config.yml version to view the correct documentation and download the exact file that shipped with the plugin.
---

![DiscordLogger](/assets/DiscordLogger-Banner.webp "DiscordLogger")
# Config Docs

Pick the **config schema version** your server is using.
If you're not sure, open your `config.yml` and check the **last line**; it will look like:

```text
# CONFIG VERSION V9, SHIPPED WITH v2.1.6
```

<div id="cfg-version-list" class="cfg-version-list">
  <p class="dl-nightly-warning">Loading config versions…</p>
</div>

<div id="cfg-version-beta-toggle" hidden data-dl-beta-toggle="Beta config versions only exist in nightly builds and may change before they ship in a stable release."></div>

<script>
(function () {
  const listEl = document.getElementById('cfg-version-list');
  const toggleHost = document.getElementById('cfg-version-beta-toggle');

  const parseBase = raw => {
    const m = String(raw || '').trim().match(/^v?(\d+)\.(\d+)\.(\d+)/);
    return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
  };
  const cmpBase = (a, b) => {
    for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
    return 0;
  };

  // Which released plugin versions fall under a given schema
  function coverage(schema, newerSchema, releases) {
    const since = parseBase(schema.since);
    const nextSince = newerSchema ? parseBase(newerSchema.since) : null;
    const hits = (releases || [])
      .filter(r => !r.prerelease)
      .filter(r => {
        const b = parseBase(r.version);
        if (!b) return false;
        if (cmpBase(b, since) < 0) return false;
        if (nextSince && cmpBase(b, nextSince) >= 0) return false;
        return true;
      })
      .map(r => r.version);
    const uniq = [...new Set(hits)].sort((a, b) => cmpBase(parseBase(a), parseBase(b)));
    if (!uniq.length) return `v${schema.since} and newer`;
    return uniq.map(v => 'v' + v).join(', ');
  }

  async function boot() {
    let registry = null;
    try {
      const res = await fetch('/assets/configs/registry.json');
      if (res.ok) registry = await res.json();
    } catch (e) { /* handled below */ }

    if (!registry || !Array.isArray(registry.schemas)) {
      listEl.innerHTML = '<p class="dl-nightly-warning">Could not load the config version list. Please refresh.</p>';
      return;
    }

    const V = window.DLVersions;
    const api = V ? await V.ready() : null;
    const releases = api ? api.releases : [];

    // newest schema first
    const schemas = registry.schemas
      .filter(s => parseBase(s.since))
      .sort((a, b) => cmpBase(parseBase(b.since), parseBase(a.since)));

    function render() {
      const showBeta = !!(api && api.showBeta);
      let anyBeta = false;
      const rows = [];

      schemas.forEach((s, i) => {
        const newer = schemas[i - 1] || null;
        const beta = api ? api.isBeta(s.since) : false;
        if (beta) anyBeta = true;
        if (beta && !showBeta) return;

        const label = s.config.toUpperCase();
        rows.push(
          `<li>
             <a href="/config/${s.config}/"><strong>${label}</strong></a>
             — ships with DiscordLogger ${coverage(s, newer, releases)}
             ${beta ? '<span class="dl-badge dl-badge--nightly" title="Only in nightly builds so far">BETA</span>' : ''}
           </li>`
        );
      });

      listEl.innerHTML = rows.length
        ? `<ul>${rows.join('')}</ul>`
        : '<p class="dl-nightly-warning">No stable config versions found.</p>';

      toggleHost.hidden = !anyBeta;
      if (V) V.apply(toggleHost);
    }

    render();
    document.addEventListener('dl-beta-change', render);
  }

  boot();
})();
</script>
