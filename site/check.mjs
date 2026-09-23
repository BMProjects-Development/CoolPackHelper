import assert from 'node:assert/strict';
import { readFile, readdir, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dist');
const files = (await readdir(root, { recursive: true })).filter((file) => file.endsWith('.html'));
const pages = new Map();
for (const file of files) {
  const html = await readFile(path.join(root, file), 'utf8');
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(ids.length, new Set(ids).size, `Duplicate anchor in ${file}`);
  pages.set(path.resolve(root, file), { html, ids: new Set(ids) });
}
let links = 0;
for (const [file, { html }] of pages) {
  for (const [, value] of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
    if (/^(?:[a-z]+:|\/\/)/i.test(value)) continue;
    const [relative, hash] = value.split('#');
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
  }
}
// Stable top-level anchors must exist in both translations, including every roadmap milestone.
for (const page of ['roadmap', 'guide']) {
  const anchors = (lang) => [...pages.get(path.join(root, lang, `${page}.html`)).ids].filter((id) => /^(milestone|section)-\d+$/.test(id));
  assert.deepEqual(anchors('en'), anchors('ru'), `Translation sections differ: ${page}`);
}
console.log(`Checked ${pages.size} pages and ${links} local links/anchors; language routes and section parity passed.`);
