# CoolPackHelper 1.0 - Complete Guide

[Back to README](../README.md) · [Development roadmap](ROADMAP_EN.md) · [Русская версия](GUIDE_RU.md)

CoolPackHelper is a modpack companion for two audiences:

- **Players** receive a clear, localized explanation when configured mods are missing or incompatible, can review official project resources, and can install supported files through a guarded workflow.
- **Pack authors** receive an in-game workspace for building and validating those requirements without writing JSON manually.

This guide documents the Minecraft 1.21.1 / NeoForge 21.1.x version. Java 21 is required.

## Contents

1. [Scope and terminology](#1-scope-and-terminology)
2. [Installation](#2-installation)
3. [Player workflow](#3-player-workflow)
4. [Author quick start](#4-author-quick-start)
5. [The in-game workspace](#5-the-in-game-workspace)
6. [How requirement detection works](#6-how-requirement-detection-works)
7. [Player-facing requirements UI](#7-player-facing-requirements-ui)
8. [Configuration reference](#8-configuration-reference)
9. [Download source types](#9-download-source-types)
10. [Download security](#10-download-security)
11. [Metadata and project resources](#11-metadata-and-project-resources)
12. [Modrinth and CurseForge scanning](#12-modrinth-and-curseforge-scanning)
13. [Localization](#13-localization)
14. [Machine-translation drafts](#14-machine-translation-drafts)
15. [Backups, history, and rollback](#15-backups-history-and-rollback)
16. [Files and pack distribution](#16-files-and-pack-distribution)
17. [Validation, migration, and recovery](#17-validation-migration-and-recovery)
18. [Complete examples](#18-complete-examples)
19. [Troubleshooting](#19-troubleshooting)
20. [Known boundaries of 1.0](#20-known-boundaries-of-10)

## 1. Scope and terminology

A **requirement** is a mod entry configured by the pack author. It can be required or recommended. CoolPackHelper checks enabled entries after NeoForge reaches the title screen.

A **project resource** is a browser link such as a homepage, source repository, issue tracker, wiki, Discord community, or donation page. It is not automatically treated as a downloadable file.

A **download source** describes how CoolPackHelper can find or open a mod file. A source may resolve through an official platform API, point to a GitHub Release asset, identify a direct HTTPS JAR, or remain a browser-only page.

A **workspace draft** is an edit inside one editor window. Saving that window applies its draft to the in-memory working configuration. The configuration file itself is written only by **Save configuration** in the workspace top bar.

CoolPackHelper does not replace NeoForge dependency declarations. If NeoForge refuses to reach the title screen because a hard loader dependency is absent, no client-side requirements screen can be displayed. Use CoolPackHelper for pack-level requirements that allow the game to start far enough to present guidance.

## 2. Installation

### Players

1. Install Minecraft 1.21.1.
2. Install a compatible NeoForge 21.1.x build.
3. Use Java 21 for the instance.
4. Place the CoolPackHelper JAR in the instance's `mods` directory.
5. Start Minecraft.

CoolPackHelper is intended to be distributed with the client instance. It can load on a dedicated server, but all menus and author tools are disabled there; a server does not need it to enforce the client guidance described in this guide.

### Pack authors

Start a development instance once. CoolPackHelper creates:

- `config/coolpackhelper.json` — distributable pack configuration;
- `config/coolpackhelper.schema.json` — JSON Schema for external editors.

The generated example entries are disabled. No startup warning appears until at least one valid entry is enabled and unsatisfied.

## 3. Player workflow

When the title screen opens, CoolPackHelper validates the configuration, evaluates enabled requirements, and applies the configured display policy.

If attention is needed, the initial view is a compact window over the blurred title screen. The title-bar controls close or expand it, and double-clicking the title bar toggles compact/full mode. The full view provides:

- **All**, **Required**, and **Recommended** filters;
- a scrollable list of missing or incompatible mods;
- a clickable card for each mod;
- a compact download action when sources exist;
- actions to open the `mods` folder and check again;
- a bulk installation action for eligible platform files.

Opening a card displays its localized explanation, technical requirement, authors, license, icon, and all available resources. Homepage, GitHub, Discord, Patreon, wiki, and other web resources are clickable and use Minecraft's external-link confirmation. HTTP(S) URLs written directly into an author description are also extracted into the resource list. Download entries enter CoolPackHelper's review flow instead of opening silently.

After files are installed, restart Minecraft. NeoForge cannot load a newly downloaded mod into the already running process.

## 4. Author quick start

1. Open the NeoForge **Mods** screen.
2. Select **CoolPackHelper Editor**. The standard configuration action for CoolPackHelper opens the same workspace.
3. Open **Pack settings** and set a stable pack ID, display name, version, display policy, and language policy.
4. Open **Mods and downloads**.
5. Add entries manually, import local JAR metadata, or run a platform scan.
6. For every enabled entry, verify the detection rule, category, localized description, metadata, and download sources.
7. Open the requirements preview and test the player flow at both normal and small window sizes.
8. Resolve every validation error.
9. Save each open draft, then select **Save configuration** in the workspace top bar.
10. Include `config/coolpackhelper.json` in the exported instance.

Do not enable a generated or scanned draft before reviewing it. “Not found” from a platform scan means no exact match for that JAR, not a guarantee that the project is unavailable on the platform.

## 5. The in-game workspace

The editor behaves like a small desktop/IDE inside Minecraft:

- tools and mod documents open as independent windows;
- windows can be dragged by their title bars and resized from their edges;
- double-click a title bar to maximize or restore a window;
- windows can be minimized, restored, closed, and reordered from the taskbar;
- taskbar tabs can be pinned to protect their position;
- closing a pinned tab requires confirmation;
- multiple mod documents can be edited side by side;
- scrollable controls keep the editor usable at small resolutions and large GUI scales.

### Two save levels

This distinction is important:

1. **Save inside a window** applies that document to the workspace. After it succeeds, the window can close immediately. A warning appears only if the window has new unapplied edits.
2. **Save configuration** writes all applied workspace changes to `config/coolpackhelper.json` after validation.

The top status reports either unsaved window drafts, an applied configuration that has not been written, or a fully saved workspace. Exiting with unapplied or unwritten work opens the appropriate confirmation.

### Pack settings

Configure:

- pack ID, name, and version;
- startup display policy;
- game-language or fixed-language mode;
- fixed and fallback locale codes;
- the maximum number of installation backup batches.

### Mods and downloads

The list supports search and sorting. A mod document contains four tabs:

- **General** - name, mod ID, version range, filename pattern, enabled state, and category;
- **Descriptions** - one text per locale and optional machine-translation drafts;
- **Downloads** - any number of sources; basic fields are shown first and advanced integrity fields are opt-in;
- **Metadata** - icon, authors, license, homepage, source, issues, wiki, Discord, and donation links, plus platform import.

Adding a source creates a temporary draft. Canceling it removes the new source without affecting the rest of the mod document.

### Other tools

- **Menu translations** edits every pack-facing requirements string per locale.
- **Import mods folder** reads local NeoForge metadata and creates disabled entries.
- **Scan Modrinth** checks exact SHA-1 matches.
- **Scan CurseForge** checks normalized CurseForge fingerprints and requires an approved 3rd Party API key.
- **Installation history** reviews completed batches, rolls them back, and deletes records that have already been rolled back.
- **Requirements preview** opens the same experience players receive.
- **Validation** lists actionable errors; clicking an issue opens the relevant editor and section.

## 6. How requirement detection works

Only entries where `enabled` is not `false` are evaluated.

### Preferred: `modId`

If `modId` is present, CoolPackHelper asks NeoForge's loaded mod list for that exact ID. This is the authoritative method.

- Absent ID → `MISSING`.
- Present ID with no `versionRange` → satisfied.
- Present ID outside `versionRange` → `WRONG_VERSION`.

When `modId` is configured, a similarly named JAR does not satisfy the entry. This prevents a broken, disabled, or unrelated file from hiding the warning.

### Fallback: `filePattern`

`filePattern` is used as the detector only when `modId` is blank. It checks filenames directly inside `mods` and supports:

- `*` - any number of characters;
- `?` - one character;
- case-insensitive matching.

Example: `private-addon-1.21.1-*.jar`.

A filename match cannot verify that the JAR loaded or that its version is correct. Prefer `modId` whenever the mod contains valid NeoForge metadata.

### Version ranges

`versionRange` uses Maven range syntax:

| Value | Meaning |
|---|---|
| `[1.0]` | exactly 1.0 |
| `[1.0,2.0)` | at least 1.0, lower than 2.0 |
| `[1.5,)` | 1.5 or newer |
| `(,3.0]` | 3.0 or older |

Use the actual version reported by NeoForge. Platform marketing versions and filename fragments are not always identical to the loaded mod version.

## 7. Player-facing requirements UI

`category` controls presentation:

- `REQUIRED` - red accent and the required label;
- `RECOMMENDED` - amber accent and the recommended label.

Both categories are guidance: the player may close the screen. A pack should explain the consequence of skipping a recommended entry in its localized description.

The detailed view is responsive. On wide screens, description/technical data and resources appear in two columns. On narrow screens, they stack vertically. Both long information and long resource lists scroll independently.

Recognized resources receive compact branded marks for Modrinth, CurseForge, GitHub, GitLab, Discord, Patreon, Ko-fi, Boosty, Open Collective, PayPal, YouTube, Reddit, Buy Me a Coffee, and common wiki hosts. Unknown hosts use a neutral web/URL mark.

### Display policies

| `showPolicy` | Behaviour |
|---|---|
| `UNTIL_RESOLVED` | Show on every launch while at least one enabled requirement is unsatisfied. |
| `ONCE_PER_PACK_VERSION` | Show once for the current combination of `pack.id` and `pack.version`. |
| `ONCE_EVER` | Show once for this instance. |
| `NEVER` | Never open automatically; the manual requirements button still works. |

One-time policies are marked only after the player closes the displayed screen. Their state is stored outside `config`, so the author's development state is not meant to ship with the pack.

## 8. Configuration reference

The current schema version is `5`. The in-game editor is recommended, but the JSON remains intentionally readable.

### Root object

| Field | Type | Purpose |
|---|---|---|
| `$schema` | string | Usually `coolpackhelper.schema.json`; enables completion in compatible editors. |
| `schemaVersion` | integer | Configuration format version. Current value: `5`. |
| `pack` | object | Stable pack identity used by the UI and version-aware display policy. |
| `showPolicy` | enum | Startup display behaviour. |
| `downloads` | object | Installation-history retention settings. |
| `menu` | object | Language selection and pack-facing translations. |
| `mods` | array | Requirement entries. |

Unknown fields are validation errors. This is deliberate: a misspelled security or detection property must not be ignored silently.

### `pack`

| Field | Notes |
|---|---|
| `id` | Stable machine-readable identifier. Do not change it casually between updates. |
| `name` | Player-facing pack name. |
| `version` | Pack version. Increment it when `ONCE_PER_PACK_VERSION` should show again. |

### `downloads`

`maxBackupBatches` accepts `1` through `100` and defaults to `10`. Pruning occurs after a successful installation. Old batches are removed as complete units.

### Requirement entry

| Field | Type | Purpose |
|---|---|---|
| `enabled` | boolean | Disabled entries are ignored. |
| `category` | enum | `REQUIRED` or `RECOMMENDED`. |
| `name` | string | Display name. Falls back to mod ID or filename pattern. |
| `modId` | string | Preferred NeoForge detection key. |
| `versionRange` | string | Optional Maven range checked against the loaded version. |
| `filePattern` | string | Filename-only fallback when no mod ID is supplied. |
| `description` | string | Legacy/simple non-localized description fallback. |
| `descriptions` | object | Locale → player explanation. |
| `iconUrl` | string | Official Modrinth/CurseForge CDN image URL. |
| `projectLinks` | object | Browser resources for the detailed view. |
| `authors` | array | Display-only author list. |
| `license` | string | Display-only license snapshot. |
| `links` | array | Download/browser sources in priority order. |

At least one of `modId` or `filePattern` is needed for useful detection. An enabled entry does not need an automatic download, but it should normally provide a page where the player can learn how to obtain the file.

### `projectLinks`

- `homepage`
- `source`
- `issues`
- `wiki`
- `discord`
- `donations`: array of `{ "label": "…", "url": "https://…" }`

These are displayed as clickable resources. They do not grant automatic-install permission.

### Download entry

| Field | Purpose |
|---|---|
| `label` | Human-readable source name. |
| `type` | `MODRINTH`, `CURSEFORGE`, `GITHUB_RELEASE`, `DIRECT`, or `PAGE`. If omitted, inferred from the URL when possible. |
| `url` | Project/release/page URL. |
| `projectId` | Platform project ID or Modrinth slug. |
| `versionId` | Exact Modrinth version ID; also accepted as a legacy CurseForge file-ID fallback. |
| `fileId` | Exact CurseForge file ID. |
| `downloadUrl` | Exact HTTPS file URL. |
| `fileName` | Expected safe `.jar` filename. |
| `sizeBytes` | Optional expected byte length. |
| `sha512` | Preferred configured strong digest. |
| `sha256` | Strong digest required for GitHub/direct sources when an official one cannot be obtained. |
| `sha1` | Supported platform integrity digest; not sufficient for arbitrary direct downloads. |

A link entry must provide at least one of `url`, `downloadUrl`, or `projectId`.

## 9. Download source types

### Modrinth

Minimum recommended setup:

```json
{
  "label": "Modrinth",
  "type": "MODRINTH",
  "projectId": "ftb-quests",
  "url": "https://modrinth.com/mod/ftb-quests"
}
```

Without `versionId`, CoolPackHelper asks Modrinth for the newest file marked for NeoForge and Minecraft 1.21.1. It prefers the primary JAR and consumes the official size and hashes. With `versionId`, that exact version is resolved.

### CurseForge

Automatic resolution through the API requires `projectId`, `fileId`, and a locally stored approved CurseForge 3rd Party API key. API credentials are never read from the distributable config.

For player installations that must not require a local key, an author may preconfigure an official `*.forgecdn.net` `downloadUrl` together with a valid hash (and preferably filename and size). If the project author disables third-party downloads, CoolPackHelper respects that decision and leaves the entry page-only.

### GitHub Releases

Only URLs matching a GitHub Release asset are installable. A repository page, branch archive, Actions artifact, or arbitrary raw file remains page-only. SHA-256 is mandatory; CoolPackHelper may use the digest published by GitHub's release metadata when available, otherwise configure it explicitly.

GitHub sources are classified as **Repository**, never as platform-trusted. The player is asked to review the owner and repository.

### Direct HTTPS

Direct installation requires:

- HTTPS;
- a safe `.jar` filename;
- SHA-256 or SHA-512;
- a public destination that passes network checks.

It is classified as **Unverified**, excluded from bulk installation, and requires an individual player decision.

### Page

`PAGE` opens information in the browser and never installs a file. Use it whenever automatic installation is unavailable, unwanted, or not safely verifiable.

## 10. Download security

The installation pipeline is intentionally stricter than opening a browser link.

Before and during a download CoolPackHelper:

1. resolves the configured source type;
2. displays source, target, trust level, and any warning;
3. requires HTTPS for remote files;
4. rejects credentials embedded in URLs and non-standard HTTPS ports;
5. resolves DNS and rejects loopback, private, link-local, multicast, and reserved addresses;
6. verifies every redirect (maximum five) instead of blindly following it;
7. constrains platform sources to expected official hosts;
8. writes to a temporary `.part` file;
9. enforces a 512 MiB maximum and checks declared/expected size;
10. verifies the strongest available expected digest;
11. opens the JAR as a ZIP and rejects unsafe paths or unreasonable structure;
12. requires NeoForge mod metadata;
13. verifies the expected mod ID and configured version range;
14. moves the file into `mods` only after every check succeeds.

Bulk installation includes only resolved `PLATFORM` items. GitHub and direct files must be reviewed one at a time.

Security boundaries:

- a correct digest proves integrity, not harmlessness;
- platform trust reduces arbitrary-link risk but is not a malware guarantee;
- CoolPackHelper does not execute downloaded files in the current process;
- the player must restart before NeoForge loads them;
- authors should prefer official project identifiers and immutable versions for reproducible packs.

## 11. Metadata and project resources

The metadata importer is available from a mod document's **Metadata** tab and from relevant download-source editing paths.

### Modrinth import

Enter a project ID, slug, or project URL. No API key is required.

### CurseForge import

Enter the numeric project ID and an approved 3rd Party API key. An author-page token is not the same credential and commonly produces HTTP 401/403 errors.

### Import review

The preview can provide:

- project title;
- English summary/description;
- icon URL;
- authors;
- license;
- homepage, source, issue tracker, wiki, Discord, and donation links when exposed by the platform;
- identifiers useful for the matching source.

Choose **Fill empty** to preserve existing author edits or **Replace** to accept the imported snapshot. Always review text and links before saving.

Project icons are deliberately limited to official Modrinth and CurseForge image CDNs. Responses are capped at 4 MiB and decoded dimensions at 2048×2048. PNG, JPEG, GIF, and WebP are supported. Failed URLs are retried later rather than requested continuously.

## 12. Modrinth and CurseForge scanning

Scans are separate by design; they answer different distribution questions.

### Local inspection

CoolPackHelper enumerates regular `.jar` files in `mods`, skips its own JAR, reads up to 1 MiB of NeoForge/Forge metadata, and extracts the display name, mod ID, version, description, and homepage when present.

### Modrinth scan

- computes SHA-1 for each JAR;
- submits hashes in batches of at most 100 to Modrinth's version-file endpoint;
- requires no key.

### CurseForge scan

- computes CurseForge's normalized MurmurHash2 fingerprint (whitespace bytes ignored);
- submits fingerprints in batches of at most 100;
- requires an approved CurseForge 3rd Party API key.

Only hashes/fingerprints are sent; JAR contents are not uploaded.

### Result meanings

- **Found** — the platform returned an exact file match.
- **Not found** — the exact JAR hash/fingerprint was reported unmatched.
- **Unknown** — no reliable conclusion, usually because of a request, permission, or response problem.

Filters let authors isolate each status. Only reviewed **Not found** items are imported by the scan workflow, and they are created disabled. A rebuilt, modified, or locally patched JAR can be “Not found” even when its upstream project exists.

## 13. Localization

There are two localization layers.

### Pack-facing text

`menu.translations` belongs to the pack author and controls requirement-screen wording. Each locale can override fields independently. Missing fields fall back rather than becoming blank.

`menu.language.mode`:

- `GAME` — use Minecraft's selected language;
- `FIXED` — always use `fixedLanguage`.

Lookup order for each field:

1. selected full locale, for example `pt_br`;
2. base language, for example `pt`;
3. `fallbackLanguage`;
4. built-in English.

The same locale chain is used for `descriptions`. The legacy single `description` is the final simple fallback.

Useful placeholders include `{required}`, `{recommended}`, `{installed}`, `{current}`, `{total}`, `{count}`, `{mod}`, and `{value}`. Keep the placeholders required by a field; Russian and English resource tests check placeholder parity for the mod's own UI.

### CoolPackHelper system text

Editor controls, security warnings, errors, and hints are shipped in `assets/cph/lang/en_us.json` and `ru_ru.json`. These are mod resources, not pack configuration. Additional system translations require a resource pack or a contribution to the mod.

## 14. Machine-translation drafts

Automatic translation currently assists **mod descriptions**. It never silently overwrites text: the translated result is an editable preview and is saved only after author confirmation.

| Provider | Key | Notes |
|---|---|---|
| MyMemory | Not required | Default zero-setup option. Quality and public-service quotas vary. Text is split into chunks of at most 500 UTF-8 bytes. |
| LibreTranslate | Server-dependent | Supports a custom HTTPS endpoint and loopback HTTP for a local self-hosted service. Some public servers require a key. |
| DeepL | Required | `:fx` keys use the Free endpoint; other keys use the Pro endpoint. Account quotas apply. |
| Google Cloud Translation Basic | Required | Requires a Google Cloud project/API key and may require billing. |

The provider help action explains setup and opens the official provider page. **Test connection** validates endpoint/credentials without translating a description.

Privacy and credential rules:

- only the selected description is sent;
- keys are memory-only unless the author explicitly enables local storage;
- remembered keys live in `local/coolpackhelper/author-settings.json`;
- keys are not written to the pack config or logs;
- credential-bearing redirects to another origin are blocked;
- MyMemory places text in URL query parameters, so do not use it for sensitive material.

Machine translation is a draft, not publication-ready localization. Review names, terminology, formatting, and placeholders manually.

## 15. Backups, history, and rollback

Successful installations are grouped into batches. Before replacing an existing mod JAR, CoolPackHelper moves it into a batch-specific backup directory under `local/coolpackhelper/backups`.

The journal is `local/coolpackhelper/installations.json`. From the history screen an author/player can:

- review installed files and date;
- roll back an active batch;
- restore replaced files;
- delete a record after it has already been rolled back.

`downloads.maxBackupBatches` keeps the newest successful batches (default `10`, allowed `1..100`). Pruning removes old journal entries and their backup directories as complete units after a later successful installation.

Rollback is designed for files changed by CoolPackHelper. It is not a general snapshot of the Minecraft instance and does not revert arbitrary manual edits.

## 16. Files and pack distribution

| Path | Distribute? | Contents |
|---|---|---|
| `config/coolpackhelper.json` | **Yes** | Pack identity, requirements, text, metadata, and sources. |
| `config/coolpackhelper.schema.json` | Optional | Editor completion/validation schema; regenerated when missing. |
| `config/coolpackhelper.json.bak` | No | Previous editor-saved configuration. |
| `config/coolpackhelper.json.vN.bak` | No | Pre-migration backup. |
| `local/coolpackhelper/state.json` | **No** | Per-player “shown” policy state. |
| `local/coolpackhelper/author-settings.json` | **Never** | Local CurseForge/translation settings and optional API keys. |
| `local/coolpackhelper/installations.json` | No | This instance's installation history. |
| `local/coolpackhelper/backups/` | No | This instance's replaced JARs. |

Release checklist:

1. Give the pack a stable ID and real version.
2. Remove or keep disabled every example/test entry.
3. Verify mod IDs and version ranges against a clean instance.
4. Test every enabled entry in both satisfied and unsatisfied states.
5. Review every external URL and digest.
6. Ensure each required mod has a useful localized explanation.
7. Test the fallback language and at least one non-English Minecraft language.
8. Test normal, compact, and small-window UI layouts.
9. Test platform downloads on a disposable copy of the instance.
10. Test rollback.
11. Save, restart, and re-open the editor to confirm persistence.
12. Export only the distributable config, not local author/player state.

## 17. Validation, migration, and recovery

CoolPackHelper validates on load and before editor save. Errors identify the affected mod where possible. In the editor, issue cards are clickable and navigate to the relevant document/tab.

Validation covers, among other things:

- unknown JSON fields;
- missing detection data;
- duplicate mod IDs/patterns where applicable;
- invalid categories, policies, and language modes;
- invalid Maven version ranges;
- malformed URLs;
- unsupported/unsafe icon hosts;
- download-source requirements;
- filename, size, and digest format;
- required placeholders in localized strings.

If the configuration is invalid at startup, a dedicated error screen replaces the ordinary requirements list. Correct the relevant editor fields or JSON, then use **Check again**; a game restart is usually unnecessary.

Legacy fields are migrated to schema 5, including `showOnlyOnce`, `requiredMods`, `menu.defaultLanguage`, per-mod `projectUrl`, and the single `downloadUrl`. Before migration, the original is copied beside the config as `coolpackhelper.json.v<old-version>.bak`.

Editor saves are atomic. If a file already exists, the previous version is copied to `coolpackhelper.json.bak` before replacement.

## 18. Complete examples

### Typical Modrinth requirement

```json
{
  "$schema": "coolpackhelper.schema.json",
  "schemaVersion": 5,
  "pack": {
    "id": "example-adventure",
    "name": "Example Adventure",
    "version": "1.0.0"
  },
  "showPolicy": "UNTIL_RESOLVED",
  "downloads": {
    "maxBackupBatches": 10
  },
  "menu": {
    "language": {
      "mode": "GAME",
      "fixedLanguage": "en_us",
      "fallbackLanguage": "en_us"
    },
    "translations": {
      "en_us": {
        "title": "Example Adventure requirements",
        "description": "Install the missing components before joining a world.",
        "summary": "Required: {required} · Recommended: {recommended}"
      },
      "ru_ru": {
        "title": "Требования Example Adventure",
        "description": "Установите недостающие компоненты перед входом в мир.",
        "summary": "Обязательных: {required} · Рекомендуемых: {recommended}"
      }
    }
  },
  "mods": [
    {
      "enabled": true,
      "category": "REQUIRED",
      "name": "FTB Quests",
      "modId": "ftbquests",
      "versionRange": "[2101.1.0,)",
      "filePattern": "ftb-quests-*.jar",
      "descriptions": {
        "en_us": "Provides the quest book and progression used by this pack.",
        "ru_ru": "Добавляет книгу заданий и систему прогрессии этой сборки."
      },
      "projectLinks": {
        "homepage": "https://modrinth.com/mod/ftb-quests",
        "source": "https://github.com/FTBTeam/FTB-Quests",
        "issues": "https://github.com/FTBTeam/FTB-Quests/issues"
      },
      "authors": ["FTB Team"],
      "license": "All Rights Reserved",
      "links": [
        {
          "label": "Modrinth",
          "type": "MODRINTH",
          "projectId": "ftb-quests",
          "url": "https://modrinth.com/mod/ftb-quests"
        }
      ]
    }
  ]
}
```

`filePattern` is retained here as useful display/export information, but because `modId` exists, detection uses `modId`.

### Private or off-platform mod with a direct file

```json
{
  "enabled": true,
  "category": "REQUIRED",
  "name": "Studio Gameplay Addon",
  "modId": "studio_gameplay_addon",
  "versionRange": "[1.4.2]",
  "descriptions": {
    "en_us": "Adds the custom gameplay systems required by this pack.",
    "ru_ru": "Добавляет уникальные игровые механики, необходимые сборке."
  },
  "projectLinks": {
    "homepage": "https://example.org/studio-addon",
    "discord": "https://discord.gg/example"
  },
  "links": [
    {
      "label": "Official studio download",
      "type": "DIRECT",
      "url": "https://example.org/studio-addon",
      "downloadUrl": "https://downloads.example.org/studio-addon-1.4.2.jar",
      "fileName": "studio-addon-1.4.2.jar",
      "sizeBytes": 1234567,
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ]
}
```

Replace the demonstration digest with the exact digest of the published immutable file. If the bytes change, publish a new version and update the digest rather than silently replacing the file.

### Browser-only source

```json
{
  "label": "Author's installation page",
  "type": "PAGE",
  "url": "https://example.org/how-to-install"
}
```

## 19. Troubleshooting

### The requirements screen does not appear

- Confirm the entry is enabled.
- Confirm `showPolicy` is not `NEVER`.
- For one-time policies, increment `pack.version` or remove the local player state while testing.
- Ensure the entry is actually unsatisfied.
- Remember that a hard NeoForge dependency failure can stop the game before CoolPackHelper's UI exists.

### A mod is reported missing even though its JAR exists

- Verify the loaded NeoForge mod ID, not the filename or project slug.
- Check `latest.log` for a JAR that failed to load.
- If using `filePattern`, leave `modId` blank and verify the wildcard.

### A version is reported wrong

- Compare the installed version shown by CoolPackHelper with the Maven range.
- Use brackets/parentheses correctly.
- Avoid guessing the internal version from the public release name.

### CurseForge returns 401 or 403

- Use an approved CurseForge 3rd Party API key.
- An author dashboard token is not equivalent.
- Re-enter the key without copied whitespace.
- Check whether the key has access to the fingerprint/file endpoints.

### CurseForge can scan but cannot download a file

The project author may prohibit third-party downloads, or the configured source may lack an exact file ID. Keep a page source so the player can use the official site.

### A direct or GitHub source opens only as a page

- Direct sources need SHA-256 or SHA-512.
- GitHub sources must identify a Release asset and have SHA-256 from configuration or official release metadata.
- The target must be a safe JAR filename over HTTPS.

### An icon does not load

- Only official Modrinth/CurseForge icon CDN hosts are accepted.
- The image must be at most 4 MiB and 2048×2048.
- Wait at least one minute after a temporary failure before retrying.
- Re-import metadata to refresh a stale platform URL.

### Translation fails

- Run **Test connection**.
- Confirm the selected provider and endpoint.
- Confirm the key and quota for DeepL/Google/secured LibreTranslate.
- MyMemory is a public service and may throttle or reject requests temporarily.
- Provider language support may not cover every Minecraft locale.

### The editor says the configuration is still unsaved

Saving a document applies it to the workspace. Use the separate top-bar **Save configuration** action to write the JSON file.

### Recovery after a bad edit

- Inspect `config/coolpackhelper.json.bak` for the previous editor save.
- Inspect `coolpackhelper.json.vN.bak` after schema migration.
- Fix the current file and select **Check again**.
- Do not copy `local/coolpackhelper/state.json` into a clean release while testing display policies.

## 20. Known boundaries of 1.0

CoolPackHelper 1.0 manages mod requirements only. It does not yet install/order resource packs or shaders, capture controls/options presets, create server-pack variants, provide Discord Rich Presence, customize the game window, or install reusable pack presets. Those are possible future modules, but keeping them outside 1.0 makes the first release easier to audit and support.

The current release targets NeoForge 1.21.1. Do not assume compatibility with Forge, Fabric, Quilt, another Minecraft version, or another loader without a dedicated build.
