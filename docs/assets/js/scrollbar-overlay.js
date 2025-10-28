// assets/js/scrollbar-overlay.js
(function () {
    const root = document.documentElement;
    const scroller = document.scrollingElement || document.documentElement;

    // Build overlay markup
    const wrap = document.createElement('div');
    wrap.className = 'dl-scrollbar';
    wrap.innerHTML = `
    <div class="dl-scrollbar__track"></div>
    <div class="dl-scrollbar__thumb"></div>
  `;
    document.body.appendChild(wrap);

    const thumb = wrap.querySelector('.dl-scrollbar__thumb');
    const track = wrap.querySelector('.dl-scrollbar__track');

    // Helpers
    let hideTimer = null;
    const HIDE_AFTER = 900;   // ms after last activity
    const EDGE_ZONE = 90;     // px from right edge to wake
    let dragging = false;
    let dragOffsetY = 0;      // pointer offset within the thumb

    const doc = () => ({
        scrollTop: scroller.scrollTop,
        scrollH: scroller.scrollHeight,
        clientH: window.innerHeight
    });

    function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }

    function updateThumb() {
        const { scrollTop, scrollH, clientH } = doc();
        const trackRect = track.getBoundingClientRect();
        const trackH = trackRect.height;

        // Thumb size proportional to viewport
        const ratio = clamp(clientH / scrollH, 0.06, 1); // min size ~6%
        const thumbH = Math.max(40, Math.floor(trackH * ratio));
        thumb.style.height = thumbH + 'px';

        // Thumb position
        const maxScroll = scrollH - clientH;
        const maxThumbY = trackH - thumbH;
        const p = maxScroll > 0 ? (scrollTop / maxScroll) : 0;
        const y = Math.round(p * maxThumbY);
        thumb.style.transform = `translateY(${y}px)`;
    }

    function show() {
        wrap.classList.add('is-visible');
        if (hideTimer) clearTimeout(hideTimer);
        hideTimer = setTimeout(() => wrap.classList.remove('is-visible'), HIDE_AFTER);
    }

    // Sync on scroll/resize
    const onScroll = () => { updateThumb(); show(); };
    const onResize = () => { updateThumb(); };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onResize);

    // Wake when mouse near right edge
    window.addEventListener('mousemove', (e) => {
        if ((window.innerWidth - e.clientX) <= EDGE_ZONE) show();
    }, { passive: true });

    // Drag to scroll
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
        // Convert thumb Y to scrollTop
        const { scrollH, clientH } = doc();
        const maxScroll = scrollH - clientH;
        const maxThumbY = trackRect.height - thumbH;
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

    // Click on track to jump
    track.addEventListener('mousedown', (e) => {
        // Ignore if clicking the thumb
        if (e.target === thumb) return;
        const rect = track.getBoundingClientRect();
        const thumbRect = thumb.getBoundingClientRect();
        const clickY = e.clientY - rect.top;
        const targetY = clickY - thumbRect.height / 2;
        const y = clamp(targetY, 0, rect.height - thumbRect.height);

        const { scrollH, clientH } = doc();
        const maxScroll = scrollH - clientH;
        const maxThumbY = rect.height - thumbRect.height;
        const p = maxThumbY > 0 ? (y / maxThumbY) : 0;
        scroller.scrollTop = Math.round(p * maxScroll);
        show();
    });

    // Init
    updateThumb();
    // Show briefly on first paint if content is scrollable
    if (scroller.scrollHeight > window.innerHeight) {
        wrap.classList.add('is-visible');
        setTimeout(() => wrap.classList.remove('is-visible'), 600);
    }
})();
