import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Marked } from 'marked';

const root = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(root, 'dist');
const repo = 'https://github.com/BMProjects-Development/CoolPackHelper';
const origin = (process.env.SITE_URL || 'https://bmprojects-development.github.io/CoolPackHelper').replace(/\/$/, '');
const escape = (value) => String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const plain = (value) => value.replace(/<[^>]*>/g, '').replace(/[*`]/g, '');
const slug = (value) => plain(value).toLowerCase().replace(/[^\p{L}\p{N}_\-\s]/gu, '').replace(/ /g, '-');
const copy = {
  en: {
    home: 'Overview', roadmap: 'Roadmap', guide: 'Documentation', skip: 'Skip to content', nav: 'Main navigation', language: 'Language',
    darkTheme: 'Dark theme', lightAction: 'Switch to light theme', darkAction: 'Switch to dark theme',
    eyebrow: 'A companion for Minecraft modpacks', title: 'Better packs.\nClearer choices.',
    description: 'Help players understand missing mods. Give pack authors the tools to configure, explain, and maintain their requirements — right inside Minecraft.',
    releases: 'GitHub releases', readGuide: 'Read the guide', target: 'Current target', foundation: 'The 1.0 foundation',
    features: [['Explain what’s missing', 'Required and recommended mods, version checks, and localized descriptions.'], ['Keep changes in view', 'Reviewed downloads, file verification, installation history, and rollback.'], ['Build inside the game', 'An author workspace for configuration, metadata import, and translations.']],
    direction: 'Where we’re heading', planTitle: 'The road ahead', planIntro: 'First, a stable 1.0. Then, more tools for building and maintaining modpacks.',
    disclaimer: 'This is a direction, not a release schedule. Priorities can change; candidates and research are not confirmed features.',
    fullPlan: 'Explore the full roadmap', milestone: 'Milestone', source: 'View source on GitHub',
    contents: 'On this page', backTop: 'Back to top', footer: 'Built for pack authors. Explained for players.', issue: 'Report an issue',
    homeTitle: 'CoolPackHelper — Minecraft modpack companion', roadmapTitle: 'Development roadmap', guideTitle: 'Complete guide',
    roadmapDescription: 'The CoolPackHelper development roadmap: release readiness, content requirements, pack profiles, and future research.',
    guideDescription: 'Install and configure CoolPackHelper for Minecraft. Guides for players and pack authors, download verification, and troubleshooting.',
  },
  ru: {
    home: 'Обзор', roadmap: 'План развития', guide: 'Документация', skip: 'Перейти к содержимому', nav: 'Главная навигация', language: 'Язык',
    darkTheme: 'Тёмная тема', lightAction: 'Включить светлую тему', darkAction: 'Включить тёмную тему',
    eyebrow: 'Помощник для Minecraft-сборок', title: 'Продуманные сборки.\nПонятный выбор.',
    description: 'Помогайте игрокам разобраться с недостающими модами. Настраивайте требования, объясняйте изменения и поддерживайте сборку прямо в Minecraft.',
    releases: 'Релизы на GitHub', readGuide: 'Открыть руководство', target: 'Текущая платформа', foundation: 'Основа версии 1.0',
    features: [['Понятные требования', 'Обязательные и рекомендуемые моды, проверка версий и описания на языке игрока.'], ['Изменения под контролем', 'Подтверждение загрузок, проверка файлов, история установки и откат.'], ['Рабочая область в игре', 'Настройка сборки, импорт метаданных и переводы в редакторе для автора.']],
    direction: 'Куда движется проект', planTitle: 'План развития', planIntro: 'Сначала — стабильная 1.0. Затем — новые инструменты создания и поддержки сборок.',
    disclaimer: 'Это направление развития, а не расписание релизов. Приоритеты могут меняться; кандидаты и исследования не гарантируют появления функции.',
    fullPlan: 'Открыть подробный план', milestone: 'Этап', source: 'Исходный текст на GitHub',
    contents: 'На этой странице', backTop: 'Наверх', footer: 'Инструменты для авторов. Ясность для игроков.', issue: 'Сообщить об ошибке',
    homeTitle: 'CoolPackHelper — помощник для Minecraft-сборок', roadmapTitle: 'План развития', guideTitle: 'Полное руководство',
    roadmapDescription: 'План развития CoolPackHelper: подготовка релиза, требования содержимого, профили сборок и исследования будущих возможностей.',
    guideDescription: 'Установка и настройка CoolPackHelper для Minecraft. Руководства для игроков и авторов сборок, проверка загрузок и решение проблем.',
  },
};

function localizedLink(href, lang) {
  if (href === '../README.md') return 'index.html';
  return href.replace(/^(?:\.\/)?(GUIDE|ROADMAP)_(EN|RU)\.md(?=#|$)/, (_, kind, locale) => `${locale.toLowerCase() === lang ? '' : `../${locale.toLowerCase()}/`}${kind.toLowerCase()}.html`);
}

function renderDocument(source, lang) {
  const headings = [];
  const ids = new Map();
  let section = 0;
  const markdown = new Marked({ gfm: true });
  markdown.use({ renderer: {
    heading({ tokens, depth, text }) {
      const content = this.parser.parseInline(tokens);
      const base = slug(text);
      const count = ids.get(base) || 0;
      ids.set(base, count + 1);
      const id = base + (count ? `-${count}` : '');
      const milestone = text.match(/^(?:Milestone|Этап) (\d+) [-–—]/);
      const stable = depth === 2 ? (milestone ? `milestone-${milestone[1]}` : `section-${section}`) : '';
      if (depth === 2) {
        section++;
        headings.push({ id: stable, title: plain(text) });
      }
      return `${stable ? `<span class="anchor" id="${stable}"></span>` : ''}<h${depth} id="${escape(id)}"${stable ? ` data-section="${stable}"` : ''}>${content}</h${depth}>\n`;
    },
    link({ href, title, tokens }) {
      return `<a href="${escape(localizedLink(href, lang))}"${title ? ` title="${escape(title)}"` : ''}>${this.parser.parseInline(tokens)}</a>`;
    },
    table(token) {
      return `<div class="table-scroll" role="region" tabindex="0" aria-label="${lang === 'ru' ? 'Таблица' : 'Table'}">${this.constructor.prototype.table.call(this, token)}</div>`;
    },
  } });
  // Source documents already link back to one another; site navigation replaces that line.
  const body = source.replace(/^# .+\r?\n/, '').replace(/^\[(?:Back to README|Вернуться к README)\].+\r?\n/m, '');
  return { html: markdown.parse(body), headings };
}

function layout(lang, page, content, description, sourceFile) {
  const t = copy[lang];
  const title = page === 'index' ? t.homeTitle : `${t[`${page}Title`]} · CoolPackHelper`;
  return `<!doctype html>
<html lang="${lang}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <title>${escape(title)}</title>
  <meta name="description" content="${escape(description)}">
  <link rel="canonical" href="${origin}/${lang}/${page}.html">
  <link rel="alternate" hreflang="en" href="${origin}/en/${page}.html">
  <link rel="alternate" hreflang="ru" href="${origin}/ru/${page}.html">
  <link rel="alternate" hreflang="x-default" href="${origin}/en/${page}.html">
  <link rel="icon" type="image/png" href="../assets/cph_logo_new.png">
  <script src="../assets/theme.js"></script>
  <link rel="stylesheet" href="../assets/style.css">
  <script src="../assets/site.js" defer></script>
</head>
<body id="top">
  <a class="skip" href="#main">${t.skip}</a>
  <header class="header">
    <a class="brand" href="index.html"><img class="brand-logo" src="../assets/cph_logo_new.png" width="48" height="48" alt="">CoolPackHelper</a>
    <nav aria-label="${t.nav}">${[['index', 'home'], ['roadmap', 'roadmap'], ['guide', 'guide']].map(([file, label]) => `<a href="${file}.html"${page === file ? ' aria-current="page"' : ''}>${t[label]}</a>`).join('')}</nav>
    <div class="header-controls"><nav class="language" aria-label="${t.language}"><a lang="en" hreflang="en" href="${lang === 'en' ? '' : '../en/'}${page}.html"${lang === 'en' ? ' aria-current="true"' : ' data-language-switch'}>EN</a><a lang="ru" hreflang="ru" href="${lang === 'ru' ? '' : '../ru/'}${page}.html"${lang === 'ru' ? ' aria-current="true"' : ' data-language-switch'}>RU</a></nav>
    <button class="theme-toggle" type="button" hidden aria-label="${t.darkTheme}" aria-pressed="false" data-light-action="${t.lightAction}" data-dark-action="${t.darkAction}"><svg class="theme-moon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z"/></svg><svg class="theme-sun" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg></button></div>
  </header>
  <main id="main">${content}</main>
  <footer><div><a class="footer-brand" href="index.html">CoolPackHelper</a><p>${t.footer}</p></div><div class="footer-links"><a href="${repo}">GitHub ↗</a><a href="${repo}/issues">${t.issue} ↗</a><a href="${repo}/blob/main/${sourceFile}">${t.source} ↗</a></div></footer>
</body>
</html>`;
}

await mkdir(path.join(out, 'assets'), { recursive: true });
for (const file of ['style.css', 'site.js', 'theme.js', 'cph_logo_new.png']) await copyFile(path.join(root, file), path.join(out, 'assets', file));
for (const lang of ['en', 'ru']) {
  const t = copy[lang];
  const sources = {};
  await mkdir(path.join(out, lang), { recursive: true });
  for (const page of ['roadmap', 'guide']) {
    const sourceFile = `docs/${page.toUpperCase()}_${lang.toUpperCase()}.md`;
    sources[page] = (await readFile(path.join(root, '..', sourceFile), 'utf8')).replace(/\r\n/g, '\n');
    const rendered = renderDocument(sources[page], lang);
    const content = `<div class="document-layout"><aside><details class="toc" open><summary>${t.contents}</summary><nav>${rendered.headings.map((h) => `<a href="#${h.id}">${escape(h.title)}</a>`).join('')}</nav></details></aside><article class="document"><div class="eyebrow">CoolPackHelper / ${t[page]}</div><h1>${t[`${page}Title`]}</h1>${rendered.html}<a class="back-top" href="#top">↑ ${t.backTop}</a></article></div>`;
    await writeFile(path.join(out, lang, `${page}.html`), layout(lang, page, content, t[`${page}Description`], sourceFile));
  }
  const milestones = [...sources.roadmap.matchAll(/^## (?:Milestone|Этап) (\d+) [-–—] (.+)\r?\n+\*\*(?:Status|Статус): (.+?)\*\*/gm)];
  const headingCount = [...sources.roadmap.matchAll(/^## (?:Milestone|Этап) \d+ /gm)].length;
  if (!milestones.length || milestones.length !== headingCount) throw new Error(`Missing or malformed milestone status in ${lang} roadmap`);
  const roadmap = milestones.map(([, number, title, status]) => {
    const tone = /Research|исследование/i.test(status) ? 'research' : /Deferred|отложено/i.test(status) ? 'deferred' : /Planned|запланировано/i.test(status) ? 'planned' : /Release gate|условие релиза/i.test(status) ? 'current' : 'candidate';
    const label = status.split(/[,/]| until | до /)[0].trim();
    return `<a class="milestone ${tone}" href="roadmap.html#milestone-${number}"><span class="milestone-number"><span class="sr-only">${t.milestone} </span>${number.padStart(2, '0')}</span><h3>${escape(title)}</h3><span class="status" title="${escape(status)}">${escape(label)}</span><span class="row-arrow" aria-hidden="true">↗</span></a>`;
  }).join('');
  const content = `<section class="hero"><div><p class="eyebrow">${t.eyebrow}</p><h1>${t.title.split('\n').map(escape).join('<br>')}</h1><p class="intro">${t.description}</p><div class="actions"><a class="button primary" href="guide.html">${t.readGuide} <span aria-hidden="true">↗</span></a><a class="button secondary" href="${repo}/releases">${t.releases} <span aria-hidden="true">↗</span></a></div></div><aside class="platform"><span class="platform-label">${t.target}</span><strong>Minecraft <span>1.21.1</span></strong><div><span>NeoForge 21.1.x</span><span>Java 21</span></div><span class="platform-caption">CoolPackHelper / 1.0</span></aside></section>
  <section class="foundation" aria-labelledby="foundation-title"><h2 class="eyebrow" id="foundation-title">${t.foundation}</h2><div class="features">${t.features.map(([title, body], i) => `<div><span class="feature-number">0${i + 1}</span><h3>${title}</h3><p>${body}</p></div>`).join('')}</div></section>
  <section class="roadmap-section" id="roadmap" aria-labelledby="roadmap-title"><div class="section-heading"><div><p class="eyebrow">${t.direction}</p><h2 id="roadmap-title">${t.planTitle}</h2><p>${t.planIntro}</p></div><a class="text-link" href="roadmap.html">${t.fullPlan} <span aria-hidden="true">↗</span></a></div><p class="roadmap-note">${t.disclaimer}</p><div class="milestones">${roadmap}</div></section>`;
  await writeFile(path.join(out, lang, 'index.html'), layout(lang, 'index', content, t.description, 'site/build.mjs'));
}
await writeFile(path.join(out, 'index.html'), `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="color-scheme" content="light dark"><title>CoolPackHelper</title><link rel="icon" type="image/png" href="assets/cph_logo_new.png"><script src="assets/theme.js"></script><link rel="stylesheet" href="assets/style.css"><script src="assets/redirect.js" defer></script></head><body><main class="language-landing"><img class="brand-logo" src="assets/cph_logo_new.png" width="96" height="96" alt=""><h1>CoolPackHelper</h1><p><a class="button primary" href="en/index.html" lang="en">English ↗</a> <a class="button secondary" href="ru/index.html" lang="ru">Русский ↗</a></p></main></body></html>`);
await copyFile(path.join(root, 'redirect.js'), path.join(out, 'assets/redirect.js'));
await writeFile(path.join(out, '.nojekyll'), '');
console.log(`Built bilingual site: ${out}`);
