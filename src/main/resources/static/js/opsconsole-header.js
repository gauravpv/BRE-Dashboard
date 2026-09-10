(function () {
    function closeMenu(menu) {
        var panel = menu.querySelector('[data-oc-account-panel]');
        var trigger = menu.querySelector('[data-oc-account-trigger]');
        if (!panel || !trigger) {
            return;
        }
        panel.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        menu.classList.remove('oc-account-menu--open');
    }

    function openMenu(menu) {
        document.querySelectorAll('[data-oc-account-menu].oc-account-menu--open').forEach(closeMenu);
        var panel = menu.querySelector('[data-oc-account-panel]');
        var trigger = menu.querySelector('[data-oc-account-trigger]');
        if (!panel || !trigger) {
            return;
        }
        panel.classList.remove('hidden');
        trigger.setAttribute('aria-expanded', 'true');
        menu.classList.add('oc-account-menu--open');
    }

    function toggleMenu(menu) {
        if (menu.classList.contains('oc-account-menu--open')) {
            closeMenu(menu);
        } else {
            openMenu(menu);
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-oc-account-menu]').forEach(function (menu) {
            var trigger = menu.querySelector('[data-oc-account-trigger]');
            if (!trigger) {
                return;
            }
            trigger.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleMenu(menu);
            });
        });

        document.addEventListener('click', function (event) {
            document.querySelectorAll('[data-oc-account-menu].oc-account-menu--open').forEach(function (menu) {
                if (!menu.contains(event.target)) {
                    closeMenu(menu);
                }
            });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                document.querySelectorAll('[data-oc-account-menu].oc-account-menu--open').forEach(closeMenu);
            }
        });
    });
})();
