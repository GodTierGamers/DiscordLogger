---
layout: default
title: "config.yml Reference — Every Option Explained"
description: Pick your config.yml version to view the correct documentation and download the exact file that shipped with the plugin.
---

# Configuration

<div class="dl-promo">
  <div class="dl-promo__text">
    <p class="dl-promo__title">Don't want to read all this?</p>
    <p class="dl-promo__sub">The generator builds both files in your browser — pick what to log, tune the filters, reword the messages, and download <code>config.yml</code> and <code>lang.yml</code>.</p>
  </div>
  <a class="dl-cta dl-cta--primary" href="/generator/">Open the generator</a>
</div>

Pick the **config schema version** your server is using.
If you're not sure, open your `config.yml` and check the **last line**; it will look like:

```text
# CONFIG VERSION {{ site.data.versions.schema }}, SHIPPED WITH v{{ site.data.versions.plugin }}
```

<div id="cfg-version-list" class="cfg-version-list">
  <p class="dl-nightly-warning">Loading config versions…</p>
</div>

<p id="cfg-version-nightly-note" class="dl-nightly-warning" hidden>
  Config versions that only exist in nightly builds are not listed. Their format can
  still change before it ships, so only schemas from stable releases appear here.
</p>

<script>
(function () {
  const listEl = document.getElementById('cfg-version-list');
  const nightlyNote = document.getElementById('cfg-version-nightly-note');

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
    // Never claim "and newer": the next release may open a new schema, so future
    // coverage is not ours to promise. With nothing released yet, `since` is still
    // the honest answer — it is the build that will carry this schema, named by the
    // registry, and the moment that release lands the API supplies the same string.
    if (!uniq.length) return 'v' + schema.since.replace(/^v/i, '');
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
      // Nightly-only schemas are excluded, with no opt-in: a format that has only
      // ever existed in a nightly can still move before release, so documenting it
      // as a choice invites someone to write a config against it and be wrong later.
      // That test is on the `since` STRING, not on whether the build has shipped —
      // a schema pinned to a stable version is final and frozen, and hiding it until
      // release day would only hide it from the nightly users already running it.
      let anyBeta = false;
      const rows = [];

      schemas.forEach((s, i) => {
        const newer = schemas[i - 1] || null;
        const beta = /-BETA\./i.test(String(s.since));
        if (beta) { anyBeta = true; return; }

        const label = s.config.toUpperCase();
        rows.push(
          `<li>
             <a href="/config/${s.config}/"><strong>${label}</strong></a>
             — ships with DiscordLogger ${coverage(s, newer, releases)}
           </li>`
        );
      });

      listEl.innerHTML = rows.length
        ? `<ul>${rows.join('')}</ul>`
        : '<p class="dl-nightly-warning">No stable config versions found.</p>';

      nightlyNote.hidden = !anyBeta;
    }

    render();
  }

  boot();
})();
</script>
