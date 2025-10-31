// assets/js/theme-toggle.js
(function () {
    const KEY = 'dl-theme';
    const root = document.documentElement;

    // Prevent transitions on first paint
    root.classList.add('theme-no-anim');
    requestAnimationFrame(() => root.classList.remove('theme-no-anim'));

    const prefersDarkMQ = window.matchMedia('(prefers-color-scheme: dark)');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Initial theme: saved → OS preference → light
    const saved = localStorage.getItem(KEY);
    const initial = saved || (prefersDarkMQ.matches ? 'dark' : 'light');
    root.setAttribute('data-theme', initial);

    // Reuse existing button if present; otherwise create one
    let btn = document.getElementById('theme-toggle');
    const ensureButton = () => {
        if (!btn) {
            btn = document.createElement('button');
            btn.id = 'theme-toggle';
            btn.type = 'button';
            document.body.appendChild(btn);
        }
        btn.classList.add('btn', 'btn--ghost');
        btn.setAttribute('aria-label', 'Toggle theme');
        btn.setAttribute('title', 'Toggle theme');
    };
    ensureButton();

    const setIcon = (mode) => { btn.textContent = (mode === 'dark' ? '☀️' : '🌙'); };
    setIcon(initial);
    btn.setAttribute('aria-pressed', String(initial === 'dark'));

    const applyTheme = (next) => {
        root.setAttribute('data-theme', next);
        localStorage.setItem(KEY, next);
        setIcon(next);
        btn.setAttribute('aria-pressed', String(next === 'dark'));
    };

    const toggleTheme = () => {
        const current = root.getAttribute('data-theme');
        const next = current === 'dark' ? 'light' : 'dark';
        applyTheme(next);
    };

    btn.addEventListener('click', () => {
        if (!reducedMotion && typeof document.startViewTransition === 'function') {
            document.startViewTransition(toggleTheme);
        } else {
            toggleTheme();
        }
    });

    // If user changes OS theme and no explicit preference saved, follow OS
    prefersDarkMQ.addEventListener?.('change', (e) => {
        const stillSaved = localStorage.getItem(KEY);
        if (!stillSaved) applyTheme(e.matches ? 'dark' : 'light');
    });
})();
