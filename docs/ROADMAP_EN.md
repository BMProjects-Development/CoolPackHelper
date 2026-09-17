# CoolPackHelper - Development Roadmap

[Back to README](../README.md) · [Complete guide](GUIDE_EN.md) · [Русская версия](ROADMAP_RU.md)

This document describes the intended direction of CoolPackHelper after the first public release. It is a product and architecture roadmap, not a promise of dates or a guarantee that every experiment will ship unchanged.

## Status labels

- **Release gate** - required before the current release can be considered ready.
- **Planned** - fits the product direction and has a reasonably clear design.
- **Candidate** - useful, but its final scope or priority still needs validation.
- **Research** - technically or legally uncertain; prototype before committing.
- **Deferred** - deliberately kept out of the near-term scope.

## Product direction

CoolPackHelper started as a missing-mod notification screen. Its longer-term goal is broader: become a safe, local-first workspace for creating, distributing, explaining, and maintaining Minecraft modpacks.

The mod should help authors produce a reproducible pack and help players understand every change the pack proposes. Convenience must not remove player choice or turn a pack configuration into an unrestricted remote installer.

## Non-negotiable principles

Every future module should follow these rules:

1. **Explicit consent** - installation, option replacement, online presence, and external links must be visible and reversible where possible.
2. **Least privilege** - network features receive only the data they need; credentials remain local.
3. **Trust is not safety** - a platform, repository, CDN, or hash changes the confidence level but never proves harmlessness.
4. **Preview before apply** - show a diff or review screen before changing player files/settings.
5. **Back up before replacement** - changes to files or user preferences need a bounded recovery path.
6. **No hidden promotion** - presets and first-party recommendations must be clearly labeled, optional, and removable.
7. **Local-first authoring** - authors should be able to create and inspect pack data without a mandatory cloud account.
8. **Accessible, responsive UI** - keyboard navigation, narration, tooltips, scroll behavior, small windows, and large GUI scale are release criteria.
9. **Schema-driven data** - new features need versioned formats, validation, migration, and readable export.
10. **No silent remote code/config execution** - remote manifests must be authenticated and reviewed; downloaded content never bypasses the normal checks.

## Milestone 0 - Finish and harden 1.0

**Status: Release gate**

Version 1.0 establishes the foundation:

- localized required/recommended mod detection;
- responsive requirements and detailed-mod interfaces;
- multiple download sources;
- guarded Modrinth, CurseForge, GitHub Release, direct HTTPS, and page workflows;
- hash, network, file-size, JAR identity, mod ID, and version checks;
- backups, installation history, and rollback;
- Modrinth/CurseForge scanning;
- project metadata import and icon loading;
- machine-translation drafts;
- multi-window author workspace;
- strict schema validation and migration.

Before the public release, the remaining work should concentrate on quality rather than new categories:

- complete UI and translation review in English and Russian;
- clean-instance tests for every show policy and source type;
- small-window, fullscreen, GUI-scale, keyboard, and narrator passes;
- interrupted download, damaged JAR, redirect, DNS, rollback, and low-disk-space tests;
- config migration tests using every historical schema;
- privacy/credential review;
- release packaging, changelog, issue templates, and reproducible build notes.

New large subsystems should wait until this baseline is stable.

## Milestone 1 - Shared content requirements

**Status: Planned**

Generalize “required mod” into a typed content requirement while preserving a dedicated experience for each content type.

### Resource packs

Planned capabilities:

- a separate resource-pack requirements screen;
- installed/missing/version identification using `pack.mcmeta`, filename, and optional digest;
- localized explanations, icon/preview, authors, license, and resources;
- guarded installation into `resourcepacks`;
- pack order preview;
- detection of incompatible `pack_format`;
- author-defined required/recommended status.

### Shader packs

Planned capabilities:

- a separate shader-pack screen rather than mixing shaders with mods;
- detection in `shaderpacks` by filename, metadata when available, and digest;
- localized descriptions and multiple sources;
- guarded ZIP installation;
- optional compatibility notes for the active shader loader;
- no assumption that a shader can be activated safely on every GPU.

### Shared architecture

The implementation should extract reusable services for:

- content identity and version constraints;
- source resolution and trust display;
- safe ZIP/JAR download and path validation;
- metadata/icon caching;
- localized descriptions and project links;
- bounded backups and installation journal entries.

The UI should still present separate Mods, Resource Packs, and Shaders sections. A single giant mixed list would be harder for players to understand.

## Milestone 2 - Pack defaults and player settings

**Status: Planned, high risk of destructive UX**

Authors often need to distribute a deliberate resource-pack order and sensible key bindings. CoolPackHelper can implement this natively without requiring Default Options, while optionally supporting import/export compatibility later.

### Resource-pack order capture

Proposed author workflow:

1. Arrange packs in Minecraft normally.
2. Select **Capture current resource-pack profile**.
3. Review selected packs, order, incompatible entries, and missing dependencies.
4. Save a named profile in the distributable CoolPackHelper config/data.

Proposed player workflow:

1. Compare the author's profile with the current selection.
2. See which packs will be enabled, disabled, or reordered.
3. Apply with one action.
4. Restore the previous state from a backup.

### Controls and options capture

The mod should not copy the author's entire `options.txt` blindly. The safer model is an explicit allowlist of settings:

- key mapping ID → proposed key;
- conflict report before apply;
- mouse/video/audio/gameplay options selected individually;
- “apply only unset bindings” and “replace selected bindings” modes;
- per-profile backup and rollback;
- pack update diff so a new version changes only intended fields.

Modded keys may disappear or change IDs between versions. Missing mappings must be reported, not silently discarded.

### Default Options compatibility

Native profiles should remain the source of truth. A later adapter may import from or export to Default Options when that mod is installed, but CoolPackHelper should not create a hard dependency or overwrite its data without review.

## Milestone 3 - Secure manifests and CDN delivery

**Status: Candidate / security research**

A CDN is useful for availability and bandwidth, but a CDN hostname alone does not prove publisher identity. “Downloaded through our CDN” must never become a security bypass.

The useful design is a **signed, versioned distribution manifest**:

- stable pack ID and version;
- immutable content IDs;
- exact sizes and SHA-256/SHA-512 digests;
- allowed mirrors/CDN origins;
- expiry and schema version;
- optional platform project/version identifiers;
- a signature verified against a key pinned by the pack configuration or CoolPackHelper trust store.

The CDN would deliver bytes; the signed manifest would authenticate what those bytes are expected to be. Every existing network, redirect, size, archive, ID, and version check would still run.

Open design questions:

- who controls and rotates signing keys;
- how a compromised author account is revoked;
- whether community/self-hosted manifests are supported;
- transparency/audit logs for changed manifests;
- offline behavior and cache expiry;
- cost and rate limiting;
- how to avoid centralizing unrelated packs under BMP infrastructure.

Do not implement unsigned remote configuration updates or a global allowlist that lets arbitrary authors turn unknown files into “trusted” downloads.

## Milestone 4 - Modpack identity and Discord presence

**Status: Candidate**

### Discord Rich Presence

Possible fields:

- pack name and version;
- current game state (menu, single-player, multiplayer) at a coarse level;
- elapsed play session;
- approved large/small assets;
- optional public buttons defined by the pack.

Privacy requirements:

- disabled unless the pack enables a profile and the player allows it;
- never expose server address, username, world name, or coordinates by default;
- show an exact preview of transmitted fields;
- easy per-instance opt-out;
- resilient when Discord is absent;
- no arbitrary Discord application credentials downloaded at runtime.

### Window title and icon

Allow an author to define a pack-aware window title and optional icon with a player override. Validate local assets, restore defaults when the profile is disabled, and treat platform-specific behavior as best effort. Branding must not hide Minecraft/NeoForge warnings or impersonate another product.

This milestone can be developed independently after 1.0 stabilization, but it should not delay content-management work.

## Milestone 5 - Reusable author presets

**Status: Deferred until the content/profile formats stabilize**

Presets could accelerate new projects by bundling commonly selected mods, resource packs, shaders, descriptions, and profile defaults.

### Local author presets

The first safe step:

- save selected entries from the current workspace as a named local template;
- parameterize Minecraft version, loader, and preferred platform;
- preview additions, removals, version choices, and conflicts;
- merge into a project without overwriting existing entries;
- export/import a human-readable preset file.

### Curated catalog

A later optional catalog might offer categories such as UI basics, performance, maps, recipe viewers, accessibility, or localization. A preset must resolve compatible versions at apply time and create a lock/snapshot for reproducibility.

BMP Translations may appear as an optional, clearly labeled BMP recommendation for Russian-language packs. It must not be silently selected, ranked as objectively superior, or bundled where its license/platform rules do not permit it. The same transparent rules should allow useful third-party alternatives.

### Preset security

- a preset is configuration, not executable code;
- every resolved project/file remains visible before application;
- normal trust and download checks still apply;
- remote catalogs require signed manifests and version pinning;
- authors can disable first-party/community catalogs completely.

## Milestone 6 - Pack diagnostics and dependency graph

**Status: Candidate**

Potential author tools:

- visualize mod dependency relationships;
- find missing/incompatible dependencies before export;
- identify duplicate embedded libraries or duplicate mod IDs;
- compare configured requirements against the actual `mods` folder;
- detect stale source URLs, invalid hashes, unavailable platform versions, and expiring links;
- show client-only/server-only/unknown environment hints;
- produce a human-readable pack health report;
- compare two pack versions and summarize changed files/settings.

Diagnostics must separate facts from heuristics. “Likely client-only” is not equivalent to “safe to remove from a server.”

## Milestone 7 - Server pack builder

**Status: Research / late roadmap**

Generating a server version is valuable but dangerous because side classification is often incomplete or wrong. It should be one of the last major modules, after content identity, profiles, manifests, and diagnostics are mature.

Proposed workflow:

1. Scan mods and configuration from a chosen client instance.
2. Classify each item as client-only, server-only, both, or unknown using NeoForge metadata, platform metadata, curated overrides, and dependency analysis.
3. Display evidence and confidence for every classification.
4. Require author decisions for unknown or conflicting entries.
5. Build into a new output directory; never edit the source instance.
6. Include selected configs, scripts, datapacks, default settings, and required libraries.
7. Respect redistribution licenses and platform rules.
8. Produce a manifest, exclusion report, warnings, and startup checklist.
9. Optionally run a local headless smoke test and capture logs.

The builder should not claim that a generated server is production-ready solely because it starts once. Network configuration, permissions, backups, performance, security, and hosting remain administrator responsibilities.

## Milestone 8 - Automatic modpack changelog generator

**Status: Research / very late roadmap**

A future release could create a structured changelog by comparing a new pack build with a previously captured release. This should be more useful than a raw directory diff: authors need a player-facing explanation of what changed and a machine-readable record of how the release was assembled.

### Proposed workflow

1. Finish and validate a release such as `1.0`.
2. Select **Capture release snapshot** and give it the stable pack ID/version.
3. CoolPackHelper inventories all supported components and writes a versioned local snapshot.
4. Develop the next release normally.
5. Capture `1.1` and select `1.0` as its comparison base.
6. Review detected additions, removals, updates, and uncertain changes.
7. Add human explanations, combine noisy entries, hide irrelevant files, and choose the public detail level.
8. Generate Markdown for players and a complete JSON report for automation/audit.
9. Store the reviewed changelog with the new release snapshot so later comparisons use confirmed history.

The author must approve the report. CoolPackHelper can detect that a script or recipe changed, but it cannot reliably infer the gameplay intention behind every line.

### Snapshot contents

Where safe and supported, a snapshot may include:

- mod ID, display name, version, filename, size, and digest;
- resource packs, shader packs, datapacks, and their metadata/order;
- selected configs with normalized semantic values;
- KubeJS startup/server/client scripts and other supported scripting roots;
- recipe identifiers and normalized inputs, outputs, conditions, and operations;
- tags, loot tables, advancements, world-generation data, and selected data-pack registries;
- chosen controls/options profiles;
- CoolPackHelper requirements, sources, metadata, and localization changes;
- server-pack composition once that module exists.

Snapshots should contain metadata and normalized representations, not automatic copies of every user file. Secrets, logs, saves, caches, authentication data, crash reports, and per-player state must be excluded by default.

### Change categories

The generated report should distinguish:

- added, removed, and updated mods;
- same mod version but different JAR digest;
- changed loader/Minecraft/pack version;
- added, removed, enabled, disabled, or reordered packs/shaders;
- created, deleted, renamed, and modified scripts;
- recipes added, removed, or semantically changed;
- config keys added, removed, or changed;
- purely formatting/order changes suppressed by normalization;
- binary/unknown files changed without a semantic explanation;
- manually entered notes and breaking-change warnings.

### Semantic adapters

A useful changelog requires format-aware adapters rather than one universal parser:

- JSON/JSON5/TOML/YAML/properties normalization;
- KubeJS JavaScript analysis with a conservative recipe extractor;
- Minecraft recipe/tag/loot-table data-pack readers;
- optional adapters for CraftTweaker and other popular scripting systems;
- mod-specific config adapters only when their behavior is understood and maintainable.

Dynamic scripts are a hard boundary. Code can build recipes conditionally, read external state, or generate identifiers at runtime. Static analysis must label incomplete or heuristic results. A later sandboxed instrumentation mode could observe registrations in a controlled development run, but must never execute unknown pack scripts outside the normal game environment merely to produce a changelog.

### Noise control and templates

Authors need configurable include/exclude rules and safe defaults:

- ignore timestamps, comments, formatting, cache keys, and known runtime-only values;
- collapse hundreds of similar recipe changes into a reviewed summary;
- group changes by Gameplay, Content, Performance, Fixes, Configuration, and Technical categories;
- map internal IDs to localized display names;
- select concise player notes or full technical notes;
- define a versioned Markdown template with placeholders;
- export JSON so launchers, websites, and CI can reuse the result;
- preserve manual edits across rescans through stable change IDs.

### Integrity and storage

Release snapshots need their own schema, migration, and integrity metadata. They should live outside player-distributed runtime state unless the author explicitly exports them. Comparisons must identify the exact base snapshot and warn when files changed after capture.

This feature depends on the shared content inventory, profile formats, diagnostics, and server-pack manifest. Implementing it last allows each earlier module to contribute a reliable semantic snapshot instead of creating a fragile second scanner.

## Compatibility ports

**Status: Candidate after 1.0 stability**

Support for another Minecraft version should be treated as a tested release line, not a version-number change. Fabric/Quilt/Forge ports require loader-specific detection, metadata inspection, UI integration, and JAR verification. Shared core modules may reduce duplication, but compatibility must be stated per artifact.

## Architecture direction

Future work should preserve clear module boundaries:

- **core schema** - versioned author data and migration;
- **inventory** - local mods/packs/shaders/options inspection;
- **requirements** - satisfaction and status evaluation;
- **metadata** - platform imports and cache;
- **resolver** - platform/repository/direct source resolution;
- **security** - URI, DNS, redirect, digest, archive, and identity checks;
- **transactions** - staging, atomic application, backup, journal, rollback;
- **profiles** - resource-pack order and selected options/key mappings;
- **snapshots** - normalized, versioned release inventories and comparison bases;
- **diff/reporting** - semantic adapters, review state, changelog templates, and exports;
- **workspace UI** - authoring and validation;
- **player UI** - preview, consent, explanation, and recovery.

Each new content type should reuse the security and transaction layers rather than implementing a second downloader.

## Suggested order

| Order | Work | Reason |
|---:|---|---|
| 1 | 1.0 hardening and public release | Establish a reliable baseline. |
| 2 | Shared content model + resource packs | Highest-value expansion with reusable architecture. |
| 3 | Shader-pack support | Reuses most of the content pipeline. |
| 4 | Resource-pack order profiles | Builds on installed content identity. |
| 5 | Controls/selected options profiles | Valuable but requires careful diffs and rollback. |
| 6 | Signed manifests/CDN prototype | Security infrastructure before remote catalogs. |
| 7 | Diagnostics and dependency graph | Creates evidence required by later automation. |
| 8 | Discord presence and window branding | Independent quality-of-life module. |
| 9 | Local then curated presets | Wait until all referenced formats are stable. |
| 10 | Server pack builder | Largest risk; depends on mature diagnostics. |
| 11 | Automatic release snapshots and changelogs | Final integration layer over inventory, profiles, diagnostics, and manifests. |

This order can change based on real user feedback, but dependencies should not be skipped merely to advertise more features.

## Explicit non-goals

CoolPackHelper should not become:

- a general-purpose arbitrary file downloader;
- a remote-code execution/update framework;
- a replacement for antivirus or platform moderation;
- a forced advertising channel;
- a hosted server control panel;
- a tool that silently replaces all player preferences;
- a guarantee that every client pack can be converted automatically into a correct server pack.

## How roadmap decisions should be made

Before promoting a candidate to planned work, answer:

1. Does it solve a repeated author/player problem?
2. Can the player understand and reverse its effects?
3. Can it reuse the existing security and transaction model?
4. Does it introduce credentials, tracking, licensing, or redistribution concerns?
5. Is its data format versioned and portable?
6. Can it be tested on clean and intentionally broken instances?
7. Will maintaining it across Minecraft/loader versions be realistic?

Bug reports and real pack-author workflows should have more influence than feature count. A smaller audited feature is preferable to broad automation that users cannot inspect.
