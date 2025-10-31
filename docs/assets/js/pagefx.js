// pagefx.js – smooth fades between pages (internal links)
(function () {
    const html = document.documentElement;

    // Enter animation on load
    requestAnimationFrame(() => {
        html.classList.add('page-enter');
        requestAnimationFrame(() => {
            html.classList.add('page-enter-active');
            setTimeout(() => {
                html.classList.remove('page-enter', 'page-enter-active');
            }, 200);
        });
    });

    // Intercept internal nav to fade out
    function isInternal(href) {
        try {
            const u = new URL(href, location.href);
            return u.origin === location.origin && !u.hash.startsWith('#');
        } catch { return false; }
    }

    document.addEventListener('click', (e) => {
        const a = e.target.closest('a');
        if (!a) return;
        const href = a.getAttribute('href');
        const target = a.getAttribute('target');
        if (!href || target === '_blank' || !isInternal(href)) return;

        e.preventDefault();
        html.classList.add('page-exit');
        requestAnimationFrame(() => {
            html.classList.add('page-exit-active');
            setTimeout(() => { location.href = a.href; }, 160);
        });
    });
})();
