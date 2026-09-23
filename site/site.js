// Switching language keeps the document and its stable section anchor.
for (const link of document.querySelectorAll('[data-language-switch]')) {
  link.addEventListener('click', () => {
    let hash = location.hash;
    try {
      const target = document.getElementById(decodeURIComponent(hash.slice(1)));
      if (target?.dataset.section) hash = `#${target.dataset.section}`;
      else if (hash && !/^#(?:milestone-\d+|section-\d+|roadmap|top|main)$/.test(hash)) hash = '';
    } catch { hash = ''; }
    link.hash = hash;
    try { localStorage.setItem('cph-language', link.lang); } catch { /* Navigation works without storage. */ }
  });
}
