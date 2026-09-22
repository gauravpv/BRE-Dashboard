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

  document.addEventListener('DOMContentLoaded', function () {
    const toggle = document.getElementById('breSidebarToggle');
    if (!toggle) return;
    updateToggle(toggle);
    toggle.addEventListener('click', function () {
      setCollapsed(!isCollapsed());
    });
  });
})();
