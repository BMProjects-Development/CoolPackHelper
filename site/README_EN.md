# CoolPackHelper website

Static English and Russian project website for GitHub Pages. It uses Node.js without a client framework, external fonts, analytics, or runtime services. Node.js 24 is recommended.

## Local preview

```powershell
cd site
npm ci --ignore-scripts
npm run build
npm run check
npm run preview
```

Open <http://127.0.0.1:4173/CoolPackHelper/>. Run `npm run build` again after editing source files. The preview server sends `Cache-Control: no-store` and reads the new output without a restart.

The generated `site/dist/` directory is ignored by Git. Commit the source files instead; the GitHub Actions workflow creates a fresh deployment artifact.

## Content and source files

- `docs/ROADMAP_EN.md` and `docs/ROADMAP_RU.md` are the roadmap sources. Overview rows are extracted from milestone headings and status lines. Keep the `## Milestone N — …` / `## Этап N — …` and `**Status: …**` / `**Статус: …**` structure.
- `docs/GUIDE_EN.md` and `docs/GUIDE_RU.md` generate the full documentation pages.
- `site/build.mjs` contains the shared template, translated overview text, download links, and organization links.
- Set `distribution.modrinthPending` in `site/build.mjs` to `false` after the Modrinth page passes moderation.
- `site/style.css` contains the responsive design.
- `site/cph_logo_new.png` is the mod logo used in the header, language page, and browser tab.
- `site/theme.js` applies System, Light, or Dark mode before the page is painted. System mode is the default and follows browser or operating-system changes.

Both language routes work without JavaScript. The root page uses the saved language or the browser language when JavaScript is available and otherwise shows explicit English and Russian links.

## GitHub Pages publication

Push the source changes to `main`. The **Website** workflow builds, checks, and deploys `site/dist/`. GitHub Pages must use **Settings → Pages → Build and deployment → Source → GitHub Actions**.

Publishing is asynchronous. Wait until both the `build` and `deploy` jobs in the workflow are successful. GitHub Pages currently serves HTML with a cache lifetime of up to 10 minutes, so a browser can briefly show the previous deployment after the workflow finishes. The generated site adds the commit version to CSS, JavaScript, and image URLs to prevent assets from different deployments from being mixed.

The production site is <https://bmprojects-development.github.io/CoolPackHelper/>. If a browser still shows an earlier page after the successful deployment and cache window, reload while bypassing the browser cache.

Website and documentation-only changes do not run the mod build workflow. For a custom domain or repository fork, set `SITE_URL` during the build and update the repository link in `build.mjs`.
