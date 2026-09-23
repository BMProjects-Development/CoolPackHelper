import assert from 'node:assert/strict';
import { readFile, readdir, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dist');
const organizationLinks = [
  'https://github.com/BMProjects-Development',
  'https://t.me/BMProjects',
  'https://discord.gg/9GWKBVw3Ty',
  'https://www.reddit.com/r/BMProjects/',
  'https://vk.com/bmprojects',
  'https://www.youtube.com/@TheBMProjects',
  'https://www.tiktok.com/@bmprojectsoff',
  'https://www.curseforge.com/members/thebarmaxx/projects',
  'https://modrinth.com/user/BarMaxx',
  'https://www.patreon.com/c/BMProjectsMinecraft',
  'https://boosty.to/barmaxx',
  'mailto:bmpland.main@gmail.com',
  'mailto:bmpland.main@mail.ru',
];
const files = (await readdir(root, { recursive: true })).filter((file) => file.endsWith('.html'));
const pages = new Map();
for (const file of files) {
  const html = await readFile(path.join(root, file), 'utf8');
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(ids.length, new Set(ids).size, `Duplicate anchor in ${file}`);
  pages.set(path.resolve(root, file), { html, ids: new Set(ids) });
  assert.ok(html.includes('<meta name="cph-build" content="'), `Missing build version in ${file}`);
  for (const [, asset] of html.matchAll(/(?:href|src)="((?:\.\.\/)?assets\/(?:style\.css|theme\.js|site\.js|redirect\.js|cph_logo_new\.png)[^"]*)"/g)) {
    assert.ok(asset.includes('?v='), `Unversioned asset ${asset} in ${file}`);
  }
}
let links = 0;
for (const [file, { html }] of pages) {
  for (const [, value] of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
    if (/^(?:[a-z]+:|\/\/)/i.test(value)) continue;
    const [relativeWithQuery, hash] = value.split('#');
    const relative = relativeWithQuery.split('?')[0];
    const target = relative ? path.resolve(path.dirname(file), decodeURIComponent(relative)) : file;
    assert.ok(target.startsWith(root + path.sep), `Link leaves site: ${value}`);
    await access(target);
    if (hash) assert.ok(pages.get(target)?.ids.has(decodeURIComponent(hash)), `Missing anchor ${value} in ${file}`);
    links++;
  }
}
for (const lang of ['en', 'ru']) {
  for (const page of ['index', 'roadmap', 'guide']) {
    const { html } = pages.get(path.join(root, lang, `${page}.html`));
    assert.ok(html.includes(`<html lang="${lang}">`));
    assert.equal([...html.matchAll(/<h1[ >]/g)].length, 1);
    const other = lang === 'en' ? 'ru' : 'en';
    assert.ok(html.includes(`href="../${other}/${page}.html" data-language-switch`));
    for (const href of organizationLinks) assert.ok(html.includes(`href="${href}"`), `Missing organization link ${href} in ${lang}/${page}`);
  }
  const home = pages.get(path.join(root, lang, 'index.html')).html;
  assert.ok(home.includes('https://www.curseforge.com/minecraft/mc-mods/coolpackhelper'), `Missing CurseForge link in ${lang}`);
  assert.ok(home.includes('https://modrinth.com/mod/coolpackhelper'), `Missing Modrinth link in ${lang}`);
  assert.ok(home.includes('<option value="system">'), `Missing system theme option in ${lang}`);
}
// Stable top-level anchors must exist in both translations, including every roadmap milestone.
for (const page of ['roadmap', 'guide']) {
  const anchors = (lang) => [...pages.get(path.join(root, lang, `${page}.html`)).ids].filter((id) => /^(milestone|section)-\d+$/.test(id));
  assert.deepEqual(anchors('en'), anchors('ru'), `Translation sections differ: ${page}`);
}
console.log(`Checked ${pages.size} pages and ${links} local links/anchors; language routes and section parity passed.`);
