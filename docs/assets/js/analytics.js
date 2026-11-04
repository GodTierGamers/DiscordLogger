/*
 * Google Analytics, loaded only after someone has said yes.
 *
 * The measurement id arrives from _config.yml as data-ga on <html>. An EMPTY id
 * disables all of this -- no banner, no script, no cookies -- which is what makes
 * it safe to ship before a property exists, and what makes turning it off again a
 * one-line edit rather than a revert.
 *
 * Nothing here runs before consent, and that is the whole design. This site set no
 * cookies whatsoever before analytics existed; a visitor who declines, or who never
 * answers, is left in exactly that state.
 */
(function () {
    'use strict';

    var ID = (document.documentElement.getAttribute('data-ga') || '').trim();
    var KEY = 'dl-analytics-consent';
    var GRANTED = 'granted';
    var DENIED = 'denied';

    function read() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
    function write(v) { try { localStorage.setItem(KEY, v); } catch (e) { /* private mode */ } }

    var started = false;

    function startGA() {
        if (started || !ID) return;
        started = true;
        window.dataLayer = window.dataLayer || [];
        window.gtag = function () { window.dataLayer.push(arguments); };
        window.gtag('js', new Date());
        window.gtag('config', ID);
        var s = document.createElement('script');
        s.async = true;
        s.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(ID);
        document.head.appendChild(s);
    }

    /* Withdrawing consent has to undo something or it is theatre. A tag already
       running cannot be unloaded from a live page, so what is on offer is honest
       and limited: the cookies it set are deleted, and the next page load never
       starts it. The privacy page says exactly that rather than implying more. */
    function clearGACookies() {
        var host = location.hostname;
        document.cookie.split(';').forEach(function (chunk) {
            var name = chunk.split('=')[0].trim();
            if (!/^_ga/.test(name)) return;
            var dead = '=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
            document.cookie = name + dead;
            document.cookie = name + dead + '; domain=' + host;
            document.cookie = name + dead + '; domain=.' + host;
        });
    }

    function setConsent(state) {
        write(state);
        if (state === GRANTED) startGA();
        else clearGACookies();
        document.dispatchEvent(new CustomEvent('dl-consent-change', { detail: { state: state } }));
        render();
    }

    /* ---- the banner ---- */

    function el(tag, props, kids) {
        var n = document.createElement(tag);
        Object.keys(props || {}).forEach(function (k) {
            if (k === 'class') n.className = props[k];
            else if (k === 'text') n.textContent = props[k];
            else n.setAttribute(k, props[k]);
        });
        (kids || []).forEach(function (c) {
            n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
        });
        return n;
    }

    var banner = null;

    function showBanner() {
        if (banner) return;
        var accept = el('button', { class: 'dl-consent__btn dl-consent__btn--yes', type: 'button', text: 'Allow' });
        var decline = el('button', { class: 'dl-consent__btn', type: 'button', text: 'No thanks' });
        accept.addEventListener('click', function () { setConsent(GRANTED); });
        decline.addEventListener('click', function () { setConsent(DENIED); });

        banner = el('div', {
            class: 'dl-consent', role: 'dialog', 'aria-live': 'polite',
            'aria-label': 'Analytics consent',
        }, [
            el('p', { class: 'dl-consent__text' }, [
                'Can we count this visit? Google Analytics tells us which pages are '
                + 'actually useful. It sets cookies, so it stays off unless you say yes. ',
                el('a', { href: '/privacy/', text: 'Privacy policy' }),
                '.',
            ]),
            el('div', { class: 'dl-consent__actions' }, [accept, decline]),
        ]);
        document.body.appendChild(banner);
    }

    function hideBanner() {
        if (!banner) return;
        banner.remove();
        banner = null;
    }

    /* ---- the control on the privacy page ---- */

    function renderControls() {
        var state = read();
        document.querySelectorAll('[data-dl-consent]').forEach(function (host) {
            host.textContent = '';
            if (!ID) {
                host.appendChild(el('p', { class: 'dl-consent-ctl__state', text:
                    'Analytics is not running on this site at all, so there is nothing to allow or decline.' }));
                return;
            }
            var on = state === GRANTED;
            host.appendChild(el('p', { class: 'dl-consent-ctl__state', text:
                on ? 'Analytics is on for this browser.'
                   : state === DENIED ? 'Analytics is off for this browser.'
                   : 'You have not been asked yet, so analytics is off.' }));
            var btn = el('button', {
                class: 'dl-consent__btn' + (on ? '' : ' dl-consent__btn--yes'), type: 'button',
                text: on ? 'Turn analytics off' : 'Turn analytics on',
            });
            btn.addEventListener('click', function () { setConsent(on ? DENIED : GRANTED); });
            host.appendChild(btn);
        });
    }

    function render() {
        var state = read();
        if (!ID) { hideBanner(); renderControls(); return; }
        if (state === GRANTED || state === DENIED) hideBanner();
        else showBanner();
        renderControls();
    }

    window.DLAnalytics = {
        get: read,
        set: setConsent,
        enabled: function () { return Boolean(ID); },
    };

    function init() {
        if (!ID) { renderControls(); return; }
        if (read() === GRANTED) startGA();
        render();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
