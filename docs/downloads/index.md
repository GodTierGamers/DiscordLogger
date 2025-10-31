---
layout: default
title: Downloads
description: Latest DiscordLogger builds from GitHub Releases.
---

![DiscordLogger](https://files.godtiergamers.xyz/DiscordLogger-Banner.png "DiscordLogger")
# Downloads

Latest builds from **GitHub Releases**.

<div id="dl-downloads-status" class="dl-downloads-status">
  Fetching releases from GitHub…
</div>

<div id="dl-releases" class="dl-releases-list" aria-live="polite"></div>

<script>
(function() {
  const OWNER = "GodTierGamers";
  const REPO = "DiscordLogger";
  const API = `https://api.github.com/repos/${OWNER}/${REPO}/releases`;

  const statusEl = document.getElementById('dl-downloads-status');
  const listEl = document.getElementById('dl-releases');

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
    const isPre = !!r.prerelease;
    const isDraft = !!r.draft;
    const tag = r.tag_name || 'untagged';
    const name = r.name || tag;
    const published = r.published_at ? formatDate(r.published_at) : 'Unpublished';
    const body = (r.body || '').trim();
    const assets = r.assets || [];

    // primary JAR (first .jar)
    const primaryJar = assets.find(a => a.name && a.name.endsWith('.jar'));

    return `
      <article class="dl-release ${isPre ? 'is-pre' : ''} ${isDraft ? 'is-draft' : ''}">
        <header class="dl-release__header">
          <div class="dl-release__meta-block">
            <div class="dl-release__titleline">
              <h2 class="dl-release__title">${name}</h2>
              <span class="dl-release__tag">${tag}</span>
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

  fetch(API)
    .then(r => {
      if (!r.ok) throw new Error('GitHub API error: ' + r.status);
      return r.json();
    })
    .then(data => {
      const releases = (data || []).slice().sort((a, b) => {
        return new Date(b.published_at || b.created_at) - new Date(a.published_at || a.created_at);
      });

      if (!releases.length) {
        statusEl.textContent = 'No releases found on GitHub.';
        return;
      }

      statusEl.textContent = '';
      listEl.innerHTML = releases.map(renderRelease).join('');
    })
    .catch(err => {
      console.error(err);
      statusEl.textContent = 'Could not fetch releases from GitHub (rate limit or network issue). Try again later.';
    });
})();
</script>
