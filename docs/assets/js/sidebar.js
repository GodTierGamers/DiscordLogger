// assets/js/sidebar.js
(function () {
    const html = document.documentElement;
    const shell = document.querySelector('.dl-shell');
    const toggle = document.getElementById('sidebar-toggle');
    const sidebar = document.querySelector('.dl-sidebar');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Same-origin internal link?
    const isInternal = (href) => {
        try {
            const u = new URL(href, location.href);
            return u.origin === location.origin && !u.hash.startsWith('#');
        } catch { return false; }
    };

    // Page enter fade
    if (!reduced) {
        requestAnimationFrame(() => {
            html.classList.add('page-enter');
            requestAnimationFrame(() => {
                html.classList.add('page-enter-active');
                html.classList.remove('page-enter');
                setTimeout(() => html.classList.remove('page-enter-active'), 200);
            });
        });
    }

    // Exit fade on internal links (ignore new-tab, download, modifiers, middle-click)
    document.addEventListener('click', (e) => {
        const a = e.target.closest('a[href]');
        if (!a) return;
        if (e.defaultPrevented) return;
        if (a.target === '_blank' || a.hasAttribute('download')) return;
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

        const href = a.getAttribute('href');
        if (!isInternal(href)) return;

        e.preventDefault();
        if (!reduced) {
            html.classList.add('page-exit');
            requestAnimationFrame(() => {
                html.classList.add('page-exit-active');
                setTimeout(() => { window.location.href = a.href; }, 160);
            });
        } else {
            window.location.href = a.href;
        }
    });

    // Mobile sidebar open/close + accessibility
    if (toggle && shell && sidebar) {
        const updateAria = () => {
            toggle.setAttribute('aria-expanded', String(shell.classList.contains('sidebar-open')));
        };

        toggle.addEventListener('click', () => {
            shell.classList.toggle('sidebar-open');
            updateAria();
        });

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            if (!shell.classList.contains('sidebar-open')) return;
            const clickedToggle = e.target.closest('#sidebar-toggle');
            if (clickedToggle) return;
            if (!sidebar.contains(e.target)) {
                shell.classList.remove('sidebar-open');
                updateAria();
            }
        });

        // Close on Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && shell.classList.contains('sidebar-open')) {
                shell.classList.remove('sidebar-open');
                updateAria();
            }
        });

        // Auto-close after tapping a nav link (small screens)
        sidebar.addEventListener('click', (e) => {
            if (window.innerWidth <= 960 && e.target.closest('a[href]')) {
                shell.classList.remove('sidebar-open');
                updateAria();
            }
        });

        updateAria();
    }
})();
