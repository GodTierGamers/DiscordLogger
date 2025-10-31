// assets/js/scrollbar-overlay.js
// Draws two overlay scrollbars:
// 1) Page scrollbar (inset, fades in/out)
// 2) Sidebar scrollbar (mini, inside .dl-sidebar)

(function () {

    // ---------- Factory for an overlay scrollbar ----------
    function makeOverlayScrollbar(opts) {
        const {
            scroller,          // element that scrolls (document.scrollingElement or .dl-sidebar)
            mount,             // element to append overlay into (document.body or sidebar)
            clsBase,           // base class e.g. 'dl-scrollbar' or 'dl-sb-scrollbar'
            showWhenNearEdge,  // fn(mouseEvent) -> boolean to wake on hover/edge
            observeResizeEl,   // element to observe for size/content changes
            initialShow = true,
            hideDelay = 900
        } = opts;

        if (!scroller || !mount) return null;

        // Build DOM
        const wrap = document.createElement('div');
        wrap.className = clsBase;
        wrap.innerHTML = `
      <div class="${clsBase}__track"></div>
      <div class="${clsBase}__thumb"></div>
    `;
        mount.appendChild(wrap);

        const track = wrap.querySelector(`.${clsBase}__track`);
        const thumb = wrap.querySelector(`.${clsBase}__thumb`);

        // State
        let hideTimer = null;
        let dragging = false;
        let dragOffsetY = 0;

        function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }

        function dims() {
            return {
                scrollTop: scroller.scrollTop,
                scrollH:   scroller.scrollHeight,
                clientH:   scroller.clientHeight || window.innerHeight
            };
        }

        function updateThumb() {
            // Track rect relative sizes
            const trackRect = track.getBoundingClientRect();
            const trackH = trackRect.height;

            const { scrollTop, scrollH, clientH } = dims();
            const maxScroll = Math.max(0, scrollH - clientH);

            // Thumb size proportional to viewport height
            const ratio = Math.max(0.06, Math.min(1, clientH / scrollH));
            const thumbH = Math.max(36, Math.floor(trackH * ratio));
            thumb.style.height = thumbH + 'px';

            const maxThumbY = Math.max(0, trackH - thumbH);
            const p = maxScroll > 0 ? (scrollTop / maxScroll) : 0;
            const y = Math.round(p * maxThumbY);
            thumb.style.transform = `translateY(${y}px)`;
        }

        function show() {
            wrap.classList.add('is-visible');
            if (hideTimer) clearTimeout(hideTimer);
            hideTimer = setTimeout(() => wrap.classList.remove('is-visible'), hideDelay);
        }

        // Scroll/resize hooks
        const onScroll = () => { updateThumb(); show(); };
        const onResize = () => { updateThumb(); };

        scroller.addEventListener('scroll', onScroll, { passive: true });
        window.addEventListener('resize', onResize);

        // Mouse proximity wake
        if (typeof showWhenNearEdge === 'function') {
            window.addEventListener('mousemove', (e) => {
                if (showWhenNearEdge(e)) show();
            }, { passive: true });
        }

        // Dragging
        const startDrag = (clientY) => {
            dragging = true;
            const rect = thumb.getBoundingClientRect();
            dragOffsetY = clientY - rect.top;
            document.body.style.userSelect = 'none';
        };
        const duringDrag = (clientY) => {
            const trackRect = track.getBoundingClientRect();
            const thumbRect = thumb.getBoundingClientRect();
            const thumbH = thumbRect.height;

            const y = clamp(clientY - trackRect.top - dragOffsetY, 0, trackRect.height - thumbH);

            const { scrollH, clientH } = dims();
            const maxScroll = Math.max(0, scrollH - clientH);
            const maxThumbY = Math.max(0, trackRect.height - thumbH);
            const p = maxThumbY > 0 ? (y / maxThumbY) : 0;
            scroller.scrollTop = Math.round(p * maxScroll);
        };
        const endDrag = () => {
            dragging = false;
            document.body.style.userSelect = '';
        };

        thumb.addEventListener('mousedown', (e) => { e.preventDefault(); show(); startDrag(e.clientY); });
        document.addEventListener('mousemove', (e) => { if (dragging) duringDrag(e.clientY); }, { passive: false });
        document.addEventListener('mouseup', () => { if (dragging) endDrag(); });

        // Click track to jump
        track.addEventListener('mousedown', (e) => {
            if (e.target === thumb) return;
            const rect = track.getBoundingClientRect();
            const thumbRect = thumb.getBoundingClientRect();
            const clickY = e.clientY - rect.top;
            const targetY = clickY - thumbRect.height / 2;
            const y = clamp(targetY, 0, rect.height - thumbRect.height);

            const { scrollH, clientH } = dims();
            const maxScroll = Math.max(0, scrollH - clientH);
            const maxThumbY = Math.max(0, rect.height - thumbRect.height);
            const p = maxThumbY > 0 ? (y / maxThumbY) : 0;
            scroller.scrollTop = Math.round(p * maxScroll);
            show();
        });

        // Observe size/content changes for live thumb sizing
        if ('ResizeObserver' in window) {
            const ro = new ResizeObserver(() => updateThumb());
            ro.observe(observeResizeEl || scroller);
        }
        if ('MutationObserver' in window && observeResizeEl) {
            const mo = new MutationObserver(() => updateThumb());
            mo.observe(observeResizeEl, { childList: true, subtree: true });
        }

        // Init
        updateThumb();
        if (initialShow && (scroller.scrollHeight > (scroller.clientHeight || window.innerHeight))) {
            wrap.classList.add('is-visible');
            setTimeout(() => wrap.classList.remove('is-visible'), 600);
        }

        return { update: updateThumb, show };
    }

    // ---------- 1) PAGE overlay ----------
    const pageScroller = document.scrollingElement || document.documentElement;
    makeOverlayScrollbar({
        scroller: pageScroller,
        mount: document.body,
        clsBase: 'dl-scrollbar',
        observeResizeEl: document.body,
        showWhenNearEdge: (e) => (window.innerWidth - e.clientX) <= 90, // near right edge
        initialShow: true,
        hideDelay: 900
    });

    // ---------- 2) SIDEBAR overlay (mini) ----------
    const sidebar = document.querySelector('.dl-sidebar');
    if (sidebar) {
        // Wake when mouse moves inside the sidebar OR near its inner right edge
        const wakeSidebar = (e) => {
            const r = sidebar.getBoundingClientRect();
            const inside = (e.clientX >= r.left && e.clientX <= r.right &&
                e.clientY >= r.top  && e.clientY <= r.bottom);
            if (!inside) return false;
            // also wake more when near the right edge inside the sidebar
            return (r.right - e.clientX) <= 70 || inside;
        };

        makeOverlayScrollbar({
            scroller: sidebar,      // the sidebar itself scrolls
            mount: sidebar,         // mount overlay inside it
            clsBase: 'dl-sb-scrollbar',
            observeResizeEl: sidebar,
            showWhenNearEdge: wakeSidebar,
            initialShow: true,
            hideDelay: 800
        });
    }
})();
