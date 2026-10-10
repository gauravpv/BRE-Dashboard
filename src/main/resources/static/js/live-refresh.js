(() => {
    'use strict';

    const ANIMATION_MS = 700;
    const seconds = Math.max(15, Number(window.breDashboardTransactionRefreshSeconds) || 30);
    let refreshTimer;
    let loading = false;
    let pendingView = null;
    // Cleared when soft navigation leaves the page, so an old instance can't keep refreshing the new one.
    let active = true;

    function parseDisplay(text) {
        const trimmed = (text || '').trim();
        const suffix = trimmed.endsWith('%') ? '%' : '';
        const numeric = trimmed.replace(/[,%]/g, '');
        const dot = numeric.indexOf('.');
        return {
            value: numeric === '' ? NaN : Number(numeric),
            decimals: dot >= 0 ? numeric.length - dot - 1 : 0,
            suffix
        };
    }

    function format(value, decimals, suffix) {
        return value.toLocaleString('en-US', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        }) + suffix;
    }

    function animateNumber(element, from) {
        const target = parseDisplay(element.textContent);
        if (Number.isNaN(from) || Number.isNaN(target.value) || from === target.value) {
            return;
        }
        element.classList.add(target.value > from ? 'rt-changed--up' : 'rt-changed--down');
        element.dataset.rtFinal = element.textContent;
        const start = performance.now();
        const step = now => {
            const progress = Math.min(1, (now - start) / ANIMATION_MS);
            const eased = 1 - Math.pow(1 - progress, 3);
            element.textContent = format(from + (target.value - from) * eased, target.decimals, target.suffix);
            if (progress < 1) {
                requestAnimationFrame(step);
            } else {
                delete element.dataset.rtFinal;
                window.setTimeout(() => element.classList.remove('rt-changed--up', 'rt-changed--down'), 900);
            }
        };
        element.textContent = format(from, target.decimals, target.suffix);
        requestAnimationFrame(step);
    }

    const HEIGHT_PANEL = '.hourly-trend-page .hourly-tables';

    function animatePanelHeight(panel, oldHeight) {
        if (!panel || !oldHeight) {
            return;
        }
        const newHeight = panel.offsetHeight;
        if (Math.abs(newHeight - oldHeight) < 1) {
            return;
        }
        panel.style.height = oldHeight + 'px';
        void panel.offsetHeight;
        panel.style.transition = 'height .35s cubic-bezier(.22, 1, .36, 1)';
        panel.style.height = newHeight + 'px';
        const done = event => {
            if (event.target !== panel || event.propertyName !== 'height') {
                return;
            }
            panel.removeEventListener('transitionend', done);
            panel.style.height = '';
            panel.style.transition = '';
        };
        panel.addEventListener('transitionend', done);
    }

    // Each animated kind: which attribute holds the key, how to read the value, how to write it.
    const TRANSITIONS = [
        {
            key: 'rtBar', selector: '[data-rt-bar]',
            read: el => el.style.width, write: (el, v) => { el.style.width = v; }
        },
        {
            key: 'rtHeight', selector: '[data-rt-height]',
            read: el => el.style.height, write: (el, v) => { el.style.height = v; }
        },
        {
            key: 'rtPos', selector: '[data-rt-pos]',
            read: el => el.style.left + '|' + el.style.top,
            write: (el, v) => {
                const [left, top] = v.split('|');
                el.style.left = left;
                el.style.top = top;
            }
        },
        {
            key: 'rtDash', selector: '[data-rt-dash]',
            read: el => el.getAttribute('stroke-dasharray'),
            write: (el, v) => el.setAttribute('stroke-dasharray', v)
        },
        {
            key: 'rtPath', selector: '[data-rt-path]',
            read: el => el.getAttribute('d'),
            write: (el, v) => { el.style.d = 'path("' + v + '")'; }
        }
    ];

    function snapshot(shell) {
        const numbers = new Map();
        shell.querySelectorAll('[data-rt]').forEach(el => numbers.set(el.dataset.rt, parseDisplay(el.textContent).value));
        const visuals = TRANSITIONS.map(kind => {
            const values = new Map();
            shell.querySelectorAll(kind.selector).forEach(el => values.set(el.dataset[kind.key], kind.read(el)));
            return values;
        });
        const checked = [...shell.querySelectorAll('input[type="radio"]:checked')].map(input => input.id).filter(Boolean);
        const scroll = [...shell.querySelectorAll('.txn-tabpane')].map(pane => pane.scrollTop);
        return { numbers, visuals, checked, scroll };
    }

    function applyUpdate(shell, fresh) {
        const previous = snapshot(shell);
        const oldHeight = shell.querySelector(HEIGHT_PANEL)?.offsetHeight;
        shell.dataset.reportUrl = fresh.dataset.reportUrl;
        shell.innerHTML = fresh.innerHTML;
        window.dispatchEvent(new CustomEvent('bre:report:updated'));
        animatePanelHeight(shell.querySelector(HEIGHT_PANEL), oldHeight);

        previous.checked.forEach(id => {
            const input = shell.querySelector('#' + CSS.escape(id));
            if (input) {
                input.checked = true;
            }
        });
        shell.querySelectorAll('.txn-tabpane').forEach((pane, index) => {
            pane.scrollTop = previous.scroll[index] || 0;
        });

        TRANSITIONS.forEach((kind, index) => {
            const oldValues = previous.visuals[index];
            shell.querySelectorAll(kind.selector).forEach(el => {
                const oldValue = oldValues.get(el.dataset[kind.key]);
                const newValue = kind.read(el);
                if (!oldValue || oldValue === newValue) {
                    return;
                }
                el.classList.add('rt-no-transition');
                kind.write(el, oldValue);
                requestAnimationFrame(() => requestAnimationFrame(() => {
                    el.classList.remove('rt-no-transition');
                    kind.write(el, newValue);
                }));
            });
        });

        shell.querySelectorAll('[data-rt]').forEach(el => {
            if (previous.numbers.has(el.dataset.rt)) {
                animateNumber(el, previous.numbers.get(el.dataset.rt));
            }
        });
    }

    async function refresh() {
        const shell = document.querySelector('[data-live-shell]');
        if (!active || !shell || loading) {
            return;
        }
        loading = true;
        const button = shell.querySelector('[data-txn-refresh]');
        if (button) {
            button.classList.add('txn-refresh--loading');
            button.disabled = true;
        }
        try {
            const response = await fetch(shell.dataset.reportUrl || window.location.href, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            const fresh = response.ok
                ? new DOMParser().parseFromString(await response.text(), 'text/html').querySelector('[data-live-shell]')
                : null;
            if (!active) {
                return;
            }
            if (!fresh) {
                // Session expired or server error page: let the browser handle it normally.
                window.location.reload();
                return;
            }
            applyUpdate(shell, fresh);
        } catch (error) {
            console.warn('Live refresh failed', error);
            if (button) {
                button.classList.remove('txn-refresh--loading');
                button.disabled = false;
            }
        } finally {
            loading = false;
            continueAfterRequest();
        }
    }

    async function changeHourlyView(url, pushHistory) {
        if (!active) {
            return;
        }
        if (loading) {
            pendingView = { url, pushHistory };
            return;
        }
        const shell = document.querySelector('[data-live-shell]');
        const currentPanel = shell?.querySelector('.hourly-tables, .txn-alert');
        if (!shell || !currentPanel) {
            return;
        }

        loading = true;
        window.clearTimeout(refreshTimer);
        try {
            const reportUrl = new URL(url, window.location.href);
            reportUrl.searchParams.set('fragment', 'true');
            const response = await fetch(reportUrl, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            const freshDocument = response.ok
                ? new DOMParser().parseFromString(await response.text(), 'text/html')
                : null;
            const fresh = freshDocument?.querySelector('[data-live-shell]');
            if (!active) {
                return;
            }
            if (!fresh) {
                throw new Error('Hourly breakdown was not returned');
            }
            applyUpdate(shell, fresh);
            if (pushHistory) {
                window.history.pushState({}, '', url);
            }
        } catch (error) {
            console.warn('Hourly view change failed', error);
        } finally {
            loading = false;
            continueAfterRequest();
        }
    }

    function continueAfterRequest() {
        if (pendingView) {
            const nextView = pendingView;
            pendingView = null;
            changeHourlyView(nextView.url, nextView.pushHistory);
        } else {
            schedule();
        }
    }

    // Align to wall-clock boundaries (e.g. :00/:30) so every device refreshes at the same moment.
    function nextAlignedDelay() {
        const periodMs = seconds * 1000;
        const delay = periodMs - (Date.now() % periodMs);
        return delay < 2000 ? delay + periodMs : delay;
    }

    function schedule(delayMs = nextAlignedDelay()) {
        window.clearTimeout(refreshTimer);
        if (active && !document.hidden) {
            refreshTimer = window.setTimeout(refresh, delayMs);
        }
    }

    document.addEventListener('click', event => {
        if (!active) {
            return;
        }
        if (event.target.closest('[data-txn-refresh]')) {
            refresh();
            return;
        }
        const hourlyTab = event.target.closest('[data-hourly-view]');
        if (hourlyTab) {
            event.preventDefault();
            changeHourlyView(hourlyTab.href, true);
        }
    });

    window.addEventListener('popstate', () => {
        if (active && document.querySelector('[data-hourly-view]')) {
            changeHourlyView(window.location.href, false);
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (!active) {
            return;
        }
        if (document.hidden) {
            window.clearTimeout(refreshTimer);
        } else {
            refresh();
        }
    });

    window.addEventListener('bre:page:before-navigation', () => {
        active = false;
        window.clearTimeout(refreshTimer);
    });

    // A restored page is only as old as its cache entry, so refresh when that data would have expired.
    window.addEventListener('bre:page:cache-restored', event => {
        const ageMs = Number(event.detail?.ageMs) || 0;
        schedule(Math.min(nextAlignedDelay(), Math.max(0, seconds * 1000 - ageMs)));
    });

    schedule();
})();
