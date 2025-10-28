// assets/js/scrollbars.js
(function () {
    const root = document.documentElement;
    const sidebar = document.querySelector('.dl-sidebar');
    const HIDE_AFTER = 900;          // ms after last activity
    const EDGE_ZONE = 88;            // px from right edge to “wake”
    let hideTimer = null;

    const show = () => {
        root.classList.add('scrollbars-visible');
        if (hideTimer) clearTimeout(hideTimer);
        hideTimer = setTimeout(() => root.classList.remove('scrollbars-visible'), HIDE_AFTER);
    };

    // Show on page/side scroll
    window.addEventListener('scroll', show, { passive: true });
    if (sidebar) sidebar.addEventListener('scroll', show, { passive: true });

    // Show when mouse is near right edge
    window.addEventListener('mousemove', (e) => {
        const nearRight = (window.innerWidth - e.clientX) <= EDGE_ZONE;
        if (nearRight) show();
    }, { passive: true });

    // First paint: hidden
    root.classList.remove('scrollbars-visible');
})();
