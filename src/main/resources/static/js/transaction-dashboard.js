(() => {
    'use strict';

    const refreshButton = document.querySelector('[data-txn-refresh]');
    let refreshTimer;

    function refresh() {
        if (refreshButton) {
            refreshButton.classList.add('txn-refresh--loading');
            refreshButton.disabled = true;
        }
        window.location.reload();
    }

    if (refreshButton) {
        refreshButton.addEventListener('click', refresh);
    }

    const seconds = Number(window.opsconsoleTransactionRefreshSeconds) || 60;
    refreshTimer = window.setTimeout(refresh, Math.max(15, seconds) * 1000);

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            window.clearTimeout(refreshTimer);
            return;
        }
        refreshTimer = window.setTimeout(refresh, Math.max(15, seconds) * 1000);
    });
})();
