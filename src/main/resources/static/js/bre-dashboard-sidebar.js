(function () {
  'use strict';

  const STORAGE_KEY = 'bre-dashboard-sidebar-collapsed';

  function isCollapsed() {
    return document.documentElement.classList.contains('sidebar-collapsed');
  }

  function updateToggle(btn) {
    if (!btn) return;
    const icon = btn.querySelector('.material-symbols-outlined');
    const collapsed = isCollapsed();
    if (icon) icon.textContent = collapsed ? 'chevron_right' : 'chevron_left';
    btn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
    btn.title = collapsed ? 'Expand sidebar' : 'Collapse sidebar';
    btn.setAttribute('aria-label', btn.title);
  }

  function setCollapsed(collapsed) {
    document.documentElement.classList.toggle('sidebar-collapsed', collapsed);
    localStorage.setItem(STORAGE_KEY, collapsed ? 'true' : 'false');
    updateToggle(document.getElementById('breSidebarToggle'));
  }

  function initializeSidebar() {
    updateToggle(document.getElementById('breSidebarToggle'));
  }

  document.addEventListener('click', function (event) {
    const toggle = event.target.closest('#breSidebarToggle');
    if (toggle) {
      setCollapsed(!isCollapsed());
      return;
    }

    const menuToggle = event.target.closest('#emiCardAuthToggle');
    const menuGroup = menuToggle?.closest('.bre-sidebar-menu-group');
    const submenu = document.getElementById('emiCardAuthSubmenu');
    if (!menuToggle || !menuGroup || !submenu) return;

    if (isCollapsed()) {
      setCollapsed(false);
      if (menuToggle.getAttribute('aria-expanded') !== 'true') {
        menuToggle.setAttribute('aria-expanded', 'true');
        submenu.setAttribute('aria-hidden', 'false');
        menuGroup.classList.add('is-open');
      }
      return;
    }
    const expanded = menuToggle.getAttribute('aria-expanded') === 'true';
    menuToggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
    submenu.setAttribute('aria-hidden', expanded ? 'true' : 'false');
    menuGroup.classList.toggle('is-open', !expanded);
  });

  initializeSidebar();
  window.addEventListener('bre:page:navigated', initializeSidebar);
})();
