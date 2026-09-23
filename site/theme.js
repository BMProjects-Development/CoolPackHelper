// Run in <head> before CSS so the system or saved theme is applied before paint.
(() => {
  const key = 'cph-theme-mode';
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const valid = (value) => value === 'system' || value === 'light' || value === 'dark';
  let preference = 'system';
  try {
    const saved = localStorage.getItem(key);
    if (valid(saved)) preference = saved;
  } catch { /* The toggle also works when browser storage is unavailable. */ }

  function applyTheme() {
    const theme = preference === 'system' ? (system.matches ? 'dark' : 'light') : preference;
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.themeMode = preference;
    const picker = document.querySelector('.theme-picker');
    const select = document.querySelector('.theme-select');
    if (picker && select) {
      picker.hidden = false;
      select.value = preference;
    }
  }

  applyTheme();
  document.addEventListener('DOMContentLoaded', () => {
    applyTheme();
    document.querySelector('.theme-select')?.addEventListener('change', (event) => {
      preference = valid(event.target.value) ? event.target.value : 'system';
      try { localStorage.setItem(key, preference); } catch { /* Keep the in-memory choice. */ }
      applyTheme();
    });
  });
  system.addEventListener('change', () => { if (preference === 'system') applyTheme(); });
  window.addEventListener('storage', (event) => {
    if (event.key === key || event.key === null) {
      preference = valid(event.newValue) ? event.newValue : 'system';
      applyTheme();
    }
  });
})();
