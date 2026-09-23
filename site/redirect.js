let language;
try { language = localStorage.getItem('cph-language'); } catch { /* Storage is optional. */ }
if (!['en', 'ru'].includes(language)) language = navigator.language?.toLowerCase().startsWith('ru') ? 'ru' : 'en';
const build = document.querySelector('meta[name="cph-build"]')?.content;
const version = build ? `?v=${encodeURIComponent(build)}` : '';
location.replace(`${language}/index.html${version}${location.hash}`);
