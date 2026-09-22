(function () {
    function closeMenu(menu) {
        var panel = menu.querySelector('[data-bre-account-panel]');
        var trigger = menu.querySelector('[data-bre-account-trigger]');
        if (!panel || !trigger) {
            return;
        }
        panel.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        menu.classList.remove('bre-account-menu--open');
    }

    function openMenu(menu) {
        document.querySelectorAll('[data-bre-account-menu].bre-account-menu--open').forEach(closeMenu);
        var panel = menu.querySelector('[data-bre-account-panel]');
        var trigger = menu.querySelector('[data-bre-account-trigger]');
        if (!panel || !trigger) {
            return;
        }
        panel.classList.remove('hidden');
        trigger.setAttribute('aria-expanded', 'true');
        menu.classList.add('bre-account-menu--open');
    }

    function toggleMenu(menu) {
        if (menu.classList.contains('bre-account-menu--open')) {
            closeMenu(menu);
        } else {
            openMenu(menu);
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-bre-account-menu]').forEach(function (menu) {
            var trigger = menu.querySelector('[data-bre-account-trigger]');
            if (!trigger) {
                return;
            }
            trigger.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleMenu(menu);
            });
        });

        document.addEventListener('click', function (event) {
            document.querySelectorAll('[data-bre-account-menu].bre-account-menu--open').forEach(function (menu) {
                if (!menu.contains(event.target)) {
                    closeMenu(menu);
                }
            });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                document.querySelectorAll('[data-bre-account-menu].bre-account-menu--open').forEach(closeMenu);
            }
        });
    });
})();
