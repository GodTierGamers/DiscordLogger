(function () {
    const KEY = 'dl-theme';
    const root = document.documentElement;

    // Prevent transition on initial paint
    root.classList.add('theme-no-anim');
    requestAnimationFrame(() => root.classList.remove('theme-no-anim'));

    // Decide initial theme: saved → OS preference → light
    const saved = localStorage.getItem(KEY);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const initial = saved || (prefersDark ? 'dark' : 'light');
    root.setAttribute('data-theme', initial);

    // Build the toggle button
    const btn = document.createElement('button');
    btn.id = 'theme-toggle';
    btn.type = 'button';
    btn.ariaLabel = 'Toggle theme';
    const setIcon = (mode) => btn.textContent = (mode === 'dark' ? '☀️' : '🌙');
    setIcon(initial);

    // Fancy cross-fade if available (Chrome/Edge), graceful fallback elsewhere
    const canVT = typeof document.startViewTransition === 'function';

    const applyTheme = () => {
        const current = root.getAttribute('data-theme');
        const next = current === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-theme', next);
        localStorage.setItem(KEY, next);
        setIcon(next);
    };

    btn.addEventListener('click', () => {
        if (canVT) {
            document.startViewTransition(applyTheme);
        } else {
            applyTheme();
        }
    });

    // Mount after content so it doesn't shift layout
    window.addEventListener('DOMContentLoaded', () => {
        document.body.appendChild(btn);
    });
})();
