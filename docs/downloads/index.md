---
layout: default
title: "Download — Stable and Nightly Builds"
description: Download the latest DiscordLogger release for PaperMC, or opt in to nightly builds. Free and open source, straight from GitHub Releases.
---

# Downloads

Latest builds from **GitHub Releases**. Stable releases are also on
[Modrinth](https://modrinth.com/plugin/discordlogger),
[Hangar](https://hangar.papermc.io/LVCHLANN/DiscordLogger) and
[CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/discordlogger) — use whichever your host
or plugin manager installs from. Nightly builds are only published here.

<div class="dl-promo">
  <div class="dl-promo__text">
    <p class="dl-promo__title">Got the JAR? Build your config next</p>
    <p class="dl-promo__sub">Pick what to log in your browser and download ready-to-use <code>config.yml</code> and <code>lang.yml</code> files.</p>
  </div>
  <a class="dl-cta dl-cta--primary" href="/generator/">Open the generator</a>
</div>

<div id="dl-downloads-status" class="dl-downloads-status">
  Fetching releases from GitHub…
</div>

<div id="dl-nightly-controls" class="dl-nightly-controls" hidden>
  <label class="dl-nightly-toggle">
    <span class="dl-switch">
      <input type="checkbox" id="dl-show-nightly" role="switch" aria-checked="false">
      <span class="dl-switch__track"><span class="dl-switch__thumb"></span></span>
    </span>
    <span>Show nightly builds <span class="dl-badge dl-badge--nightly">Nightly</span></span>
  </label>
  <p class="dl-nightly-warning">
    Nightly builds are automated previews of unreleased work. They may be unstable — use a stable release on production servers.
  </p>
</div>

<div id="dl-releases" class="dl-releases-list" aria-live="polite"></div>

<script>
(function() {
  const OWNER = "GodTierGamers";
  const REPO = "DiscordLogger";
  const API = `https://api.github.com/repos/${OWNER}/${REPO}/releases`;

  const statusEl = document.getElementById('dl-downloads-status');
  const listEl = document.getElementById('dl-releases');
  const controlsEl = document.getElementById('dl-nightly-controls');
  const nightlyToggle = document.getElementById('dl-show-nightly');

  // Nightly builds are the automated vX.Y.Z-BETA.N pre-releases produced by
  // .github/workflows/nightly.yml. Any other pre-release is a manual one, so
  // it keeps the generic "Pre-release" badge instead.
  const isNightly = (r) => /-BETA\.\d+$/i.test(r.tag_name || '');

  function formatDate(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleString([], {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  // lightweight markdown → html, grouped lists, no weird UL-per-LI
  function mdLite(md) {
    if (!md) return "";
    // escape HTML first
    md = md.replace(/</g, "&lt;").replace(/>/g, "&gt;");
    const lines = md.replace(/\r\n/g, "\n").split("\n");

    let html = "";
    let inList = false;

    function closeList() {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
    }

    for (const line of lines) {
      const trimmed = line.trim();

      // headings
      const m = trimmed.match(/^(#{1,6})\s+(.*)$/);
      if (m) {
        closeList();
        const level = m[1].length;
        const text = m[2];
        const tag = level >= 4 ? "h4" : ("h" + (level + 1)); // h2/h3/h4 max
        html += `<${tag}>${text}</${tag}>`;
        continue;
      }

      // list item
      if (/^[-*]\s+/.test(trimmed)) {
        if (!inList) {
          html += "<ul>";
          inList = true;
        }
        const itemText = trimmed.replace(/^[-*]\s+/, "");
        html += `<li>${itemText}</li>`;
        continue;
      }

      // blank line
      if (trimmed === "") {
        closeList();
        continue;
      }

      // paragraph
      closeList();
      html += `<p>${trimmed}</p>`;
    }

    closeList();

    // bold / italics after structure
    html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    html = html.replace(/\*(.+?)\*/g, "<em>$1</em>");

    return html;
  }

  function renderRelease(r) {
    const nightly = isNightly(r);
    const isPre = !!r.prerelease && !nightly;
    const isDraft = !!r.draft;
    const tag = r.tag_name || 'untagged';
    const name = r.name || tag;
    const published = r.published_at ? formatDate(r.published_at) : 'Unpublished';
    const body = (r.body || '').trim();
    const assets = r.assets || [];

    // primary JAR (first .jar)
    const primaryJar = assets.find(a => a.name && a.name.endsWith('.jar'));

    return `
      <article class="dl-release ${isPre ? 'is-pre' : ''} ${nightly ? 'is-nightly' : ''} ${isDraft ? 'is-draft' : ''}">
        <header class="dl-release__header">
          <div class="dl-release__meta-block">
            <div class="dl-release__titleline">
              <h2 class="dl-release__title">${name}</h2>
              <span class="dl-release__tag">${tag}</span>
              ${nightly ? '<span class="dl-badge dl-badge--nightly">Nightly</span>' : ''}
              ${isPre ? '<span class="dl-badge dl-badge--pre">Pre-release</span>' : ''}
              ${isDraft ? '<span class="dl-badge dl-badge--draft">Draft</span>' : ''}
            </div>
            <p class="dl-release__meta">Published ${published}</p>
          </div>
          ${
            primaryJar
              ? `<a href="${primaryJar.browser_download_url}" class="dl-release__primary-download">
                   <span class="dl-release__primary-label">Download</span>
                   <span class="dl-release__primary-name">${primaryJar.name}</span>
                   <span class="dl-release__primary-size">${(primaryJar.size/1024/1024).toFixed(2)} MB • .jar</span>
                 </a>`
              : ''
          }
        </header>

        ${body ? `
          <details class="dl-release__notes">
            <summary>Release notes</summary>
            <div class="dl-release__notes-body markdown-body">
              ${mdLite(body)}
            </div>
          </details>
        ` : ''}
      </article>
    `;
  }

  const STORAGE_KEY = 'dl-show-nightly';
  let allReleases = [];

  function paint() {
    const showNightly = nightlyToggle.checked;
    const shown = allReleases.filter(r => showNightly || !isNightly(r));

    if (!shown.length) {
      listEl.innerHTML = '';
      statusEl.textContent = showNightly
        ? 'No releases found on GitHub.'
        : 'No stable releases yet — enable nightly builds above to see preview builds.';
      return;
    }

    statusEl.textContent = '';
    listEl.innerHTML = shown.map(renderRelease).join('');
  }

  nightlyToggle.addEventListener('change', () => {
    nightlyToggle.setAttribute('aria-checked', String(nightlyToggle.checked));
    try { localStorage.setItem(STORAGE_KEY, nightlyToggle.checked ? '1' : '0'); } catch (e) {}
    paint();
  });

  fetch(API)
    .then(r => {
      if (!r.ok) throw new Error('GitHub API error: ' + r.status);
      return r.json();
    })
    .then(data => {
      allReleases = (data || []).slice().sort((a, b) => {
        return new Date(b.published_at || b.created_at) - new Date(a.published_at || a.created_at);
      });

      if (!allReleases.length) {
        statusEl.textContent = 'No releases found on GitHub.';
        return;
      }

      const nightlyCount = allReleases.filter(isNightly).length;
      if (nightlyCount) {
        controlsEl.hidden = false;
        try {
          // Opt-in and remembered: nightlies stay hidden unless asked for.
          nightlyToggle.checked = localStorage.getItem(STORAGE_KEY) === '1';
        } catch (e) {
          nightlyToggle.checked = false;
        }
        nightlyToggle.setAttribute('aria-checked', String(nightlyToggle.checked));
      }

      paint();
    })
    .catch(err => {
      console.error(err);
      statusEl.textContent = 'Could not fetch releases from GitHub (rate limit or network issue). Try again later.';
    });
})();
</script>
