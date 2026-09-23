let language;
try { language = localStorage.getItem('cph-language'); } catch { /* Storage is optional. */ }
if (!['en', 'ru'].includes(language)) language = navigator.language?.toLowerCase().startsWith('ru') ? 'ru' : 'en';
location.replace(`${language}/index.html${location.hash}`);
