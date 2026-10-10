/**
 * Stitch theme toggle — switches <html class="light"> / <html class="dark"> (darkMode: "class").
 */
(function () {
    const STORAGE_KEY = 'bre-dashboard-theme';

    function resolveTheme() {
        const stored = localStorage.getItem(STORAGE_KEY);
        return stored === 'dark' ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        const root = document.documentElement;
        root.classList.remove('light', 'dark');
        root.classList.add(theme);
        localStorage.setItem(STORAGE_KEY, theme);
        document.querySelectorAll('[data-stitch-theme-icon="light"]').forEach(function (el) {
            el.classList.toggle('hidden', theme !== 'light');
        });
        document.querySelectorAll('[data-stitch-theme-icon="dark"]').forEach(function (el) {
            el.classList.toggle('hidden', theme !== 'dark');
        });
    }

    function toggleTheme() {
        applyTheme(resolveTheme() === 'dark' ? 'light' : 'dark');
    }

    window.StitchTheme = { applyTheme: applyTheme, toggleTheme: toggleTheme };

    applyTheme(resolveTheme());

    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-stitch-theme-toggle]')) {
            toggleTheme();
        }
    });

    window.addEventListener('bre:page:navigated', function () {
        applyTheme(resolveTheme());
    });
})();
