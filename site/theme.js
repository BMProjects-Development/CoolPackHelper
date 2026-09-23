// Run in <head> before CSS so a saved dark theme never flashes light on navigation.
(() => {
  const key = 'cph-theme';
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const valid = (value) => value === 'light' || value === 'dark';
  let preference = null;
  try {
    const saved = localStorage.getItem(key);
    if (valid(saved)) preference = saved;
  } catch { /* The toggle also works when browser storage is unavailable. */ }

  function applyTheme() {
    const theme = preference || (system.matches ? 'dark' : 'light');
    document.documentElement.dataset.theme = theme;
    const button = document.querySelector('.theme-toggle');
    if (button) {
      button.hidden = false;
      button.setAttribute('aria-pressed', String(theme === 'dark'));
      button.title = theme === 'dark' ? button.dataset.lightAction : button.dataset.darkAction;
    }
  }

  applyTheme();
  document.addEventListener('DOMContentLoaded', () => {
    applyTheme();
    document.querySelector('.theme-toggle')?.addEventListener('click', () => {
      preference = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      try { localStorage.setItem(key, preference); } catch { /* Keep the in-memory choice. */ }
      applyTheme();
    });
  });
  system.addEventListener('change', () => { if (!preference) applyTheme(); });
  window.addEventListener('storage', (event) => {
    if (event.key === key || event.key === null) {
      preference = valid(event.newValue) ? event.newValue : null;
      applyTheme();
    }
  });
})();
