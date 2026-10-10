(() => {
    'use strict';

    const ANIMATION_MS = 700;
    const seconds = Math.max(15, Number(window.breDashboardTransactionRefreshSeconds) || 60);
    let refreshTimer;
    let loading = false;

    function parseDisplay(text) {
        const trimmed = (text || '').trim();
        const suffix = trimmed.endsWith('%') ? '%' : '';
        const numeric = trimmed.replace(/[,%]/g, '');
        const dot = numeric.indexOf('.');
        return {
            value: Number(numeric),
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
        const changeClass = target.value > from ? 'rt-changed--up' : 'rt-changed--down';
        const percent = element.closest('td')?.nextElementSibling?.querySelector('.rt-percent span');
        element.classList.add(changeClass);
        percent?.classList.add(changeClass);
        const start = performance.now();
        const step = now => {
            const progress = Math.min(1, (now - start) / ANIMATION_MS);
            const eased = 1 - Math.pow(1 - progress, 3);
            element.textContent = format(from + (target.value - from) * eased, target.decimals, target.suffix);
            if (progress < 1) {
                requestAnimationFrame(step);
            } else {
                window.setTimeout(() => {
                    element.classList.remove('rt-changed--up', 'rt-changed--down');
                    percent?.classList.remove('rt-changed--up', 'rt-changed--down');
                }, 900);
            }
        };
        element.textContent = format(from, target.decimals, target.suffix);
        requestAnimationFrame(step);
    }

    function snapshot(shell) {
        const numbers = new Map();
        const bars = new Map();
        shell.querySelectorAll('[data-rt]').forEach(el => numbers.set(el.dataset.rt, parseDisplay(el.textContent).value));
        shell.querySelectorAll('[data-rt-bar]').forEach(el => bars.set(el.dataset.rtBar, el.style.width));
        return { numbers, bars };
    }

    function applyUpdate(shell, fresh) {
        const previous = snapshot(shell);
        shell.innerHTML = fresh.innerHTML;

        shell.querySelectorAll('[data-rt-bar]').forEach(bar => {
            const oldWidth = previous.bars.get(bar.dataset.rtBar);
            if (!oldWidth || oldWidth === bar.style.width) {
                return;
            }
            const newWidth = bar.style.width;
            bar.style.width = oldWidth;
            requestAnimationFrame(() => requestAnimationFrame(() => {
                bar.style.width = newWidth;
            }));
        });
        shell.querySelectorAll('[data-rt]').forEach(el => {
            if (previous.numbers.has(el.dataset.rt)) {
                animateNumber(el, previous.numbers.get(el.dataset.rt));
            }
        });
    }

    async function refresh() {
        const shell = document.querySelector('[data-rt-shell]');
        if (!shell || loading) {
            return;
        }
        loading = true;
        const button = shell.querySelector('[data-txn-refresh]');
        if (button) {
            button.classList.add('txn-refresh--loading');
            button.disabled = true;
        }
        try {
            const response = await fetch(window.location.href, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            const fresh = response.ok
                ? new DOMParser().parseFromString(await response.text(), 'text/html').querySelector('[data-rt-shell]')
                : null;
            if (!fresh) {
                // Session expired or server error page: let the browser handle it normally.
                window.location.reload();
                return;
            }
            applyUpdate(shell, fresh);
        } catch (error) {
            console.warn('Response time refresh failed', error);
            if (button) {
                button.classList.remove('txn-refresh--loading');
                button.disabled = false;
            }
        } finally {
            loading = false;
            schedule();
        }
    }

    function schedule() {
        window.clearTimeout(refreshTimer);
        if (!document.hidden) {
            refreshTimer = window.setTimeout(refresh, seconds * 1000);
        }
    }

    document.addEventListener('click', event => {
        if (event.target.closest('[data-txn-refresh]')) {
            refresh();
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            window.clearTimeout(refreshTimer);
        } else {
            refresh();
        }
    });

    schedule();
})();
