(function () {
    const html = document.documentElement;
    const shell = document.querySelector('.dl-shell');
    const toggle = document.getElementById('sidebar-toggle');

    // Page enter fade
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        html.classList.add('page-enter');
        requestAnimationFrame(() => {
            html.classList.add('page-enter-active');
            html.classList.remove('page-enter');
            setTimeout(() => html.classList.remove('page-enter-active'), 220);
        });
    }

    // Exit fade on internal links
    document.addEventListener('click', (e) => {
        const a = e.target.closest('a[href]');
        if (!a) return;
        const href = a.getAttribute('href');
        if (/^https?:\/\//i.test(href) || href.startsWith('#')) return;
        if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            e.preventDefault();
            html.classList.add('page-exit');
            requestAnimationFrame(() => {
                html.classList.add('page-exit-active');
                setTimeout(() => { window.location.href = a.href; }, 140);
            });
        }
    });

    // Mobile sidebar open/close
    if (toggle && shell) {
        toggle.addEventListener('click', () => {
            shell.classList.toggle('sidebar-open');
        });

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            if (!shell.classList.contains('sidebar-open')) return;
            const aside = document.querySelector('.dl-sidebar');
            const btn = e.target.closest('#sidebar-toggle');
            if (btn) return;
            if (aside && !aside.contains(e.target)) {
                shell.classList.remove('sidebar-open');
            }
        });
    }
})();
