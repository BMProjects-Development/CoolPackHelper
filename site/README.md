# CoolPackHelper website / Сайт CoolPackHelper

Static English and Russian project website for GitHub Pages. Node.js 24 is recommended. No client framework, external fonts, analytics, or runtime services.

## Local preview / Локальный просмотр

```powershell
cd site
npm ci --ignore-scripts
npm run build
npm run check
npm run preview
```

Open <http://127.0.0.1:4173/CoolPackHelper/>. The preview uses the same repository subpath as GitHub Pages. Rebuild after editing source files; the preview serves updated output without restarting.

Откройте <http://127.0.0.1:4173/CoolPackHelper/>. После изменений повторите `npm run build`; перезапуск просмотра не нужен.

## Content / Содержимое

- `docs/ROADMAP_EN.md` and `docs/ROADMAP_RU.md` are the source of truth for the roadmap. Overview rows are extracted from milestone headings and their status lines automatically. Keep the `## Milestone N — …` / `## Этап N — …` and `**Status: …**` / `**Статус: …**` structure.
- `docs/GUIDE_EN.md` and `docs/GUIDE_RU.md` produce the full documentation pages.
- `site/build.mjs` contains the shared template and translated overview/interface text.
- `site/style.css` contains the responsive design; `site/dist/` is generated and ignored by Git.

План и руководство берутся из существующих Markdown-файлов в `docs/`. Отдельную копию для сайта вести не нужно. Названия и статусы этапов на главной обновляются автоматически. Общие тексты интерфейса и главной на обоих языках находятся в `site/build.mjs`.

Both language routes work without JavaScript. At the root, JavaScript selects the saved language or the browser language (Russian for `ru`, English otherwise); without JavaScript, visitors see two language links. The language switch preserves the page and top-level section. Store only the explicit language preference in local storage; navigation still works when storage is blocked.

Обе языковые версии работают без JavaScript. Корневая страница выбирает сохранённый язык или язык браузера, а без JavaScript предлагает выбор. Переключатель сохраняет страницу и раздел верхнего уровня; выбор языка запоминается локально, если браузер разрешает хранение.

## GitHub Pages publication / Публикация

1. Push these changes to `main`.
2. In the repository, open **Settings → Pages → Build and deployment → Source → GitHub Actions**.
3. Open **Actions → Website → Run workflow** if the initial push happened before Pages was enabled.
4. After deployment succeeds, the site is available at <https://bmprojects-development.github.io/CoolPackHelper/>.

Отправьте изменения в `main`, выберите **Settings → Pages → Source → GitHub Actions** и при необходимости запустите workflow **Website** вручную. Дальнейшие изменения `site/` и `docs/` публикуются автоматически. Для pull request выполняются сборка и проверка ссылок без публикации.

The existing Java workflow ignores website/documentation-only changes so they do not rebuild the mod or recreate its release. A push changing Java/build files still runs the original mod workflow.

Изменения только сайта и документации не запускают пересоздание релиза мода. Изменения кода или сборки мода по-прежнему обрабатывает основной workflow.

For a custom domain or repository fork, set `SITE_URL` when building and update the repository link in `build.mjs`. Internal links are relative and do not require a domain-specific rebuild.
