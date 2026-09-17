# CoolPackHelper

CoolPackHelper is a configurable, security-focused assistant for Minecraft modpacks. It detects missing or incompatible mods, explains why the pack needs them, presents localized project resources, and can install supported files through a guarded download workflow.

The mod also provides an in-game workspace for pack authors: configure requirements, import metadata, scan the current `mods` directory, create translations, preview the player experience, and maintain installation history without editing JSON by hand.

> Current target: **Minecraft 1.21.1 · NeoForge 21.1.x · Java 21**

## Highlights

- Detect mods by loaded NeoForge mod ID, Maven version range, or filename pattern.
- Separate required and recommended entries with a responsive, scrollable interface.
- Show localized descriptions, icons, authors, licenses, and clickable project resources.
- Resolve downloads from Modrinth, CurseForge, GitHub Releases, direct HTTPS files, or ordinary web pages.
- Restrict bulk installation to platform-trusted Modrinth and CurseForge files.
- Verify HTTPS destinations, redirects, hashes, file size, JAR structure, mod ID, and version before installation.
- Back up replaced JARs, keep an installation journal, and roll back completed batches.
- Scan installed JARs against Modrinth and CurseForge independently using hashes or fingerprints.
- Import project metadata and links from Modrinth or CurseForge.
- Edit the complete configuration in a movable, resizable, multi-window in-game workspace.
- Localize the pack-facing UI and mod descriptions; optionally create editable machine-translation drafts.

## Documentation

Choose a complete guide:

- [English documentation](docs/GUIDE_EN.md)
- [Документация на русском](docs/GUIDE_RU.md)

Future development and design decisions are tracked separately:

- [Development roadmap — English](docs/ROADMAP_EN.md)
- [План развития — русский](docs/ROADMAP_RU.md)

The guides cover installation, the player workflow, the author workspace, every configuration section, platform scanning, metadata import, translation providers, download security, backups, migration, troubleshooting, and release preparation.

## Quick start for players

1. Install NeoForge for Minecraft 1.21.1 and Java 21.
2. Put the CoolPackHelper JAR in the instance's `mods` directory.
3. Start the game. If the pack has unsatisfied configured requirements, CoolPackHelper opens a compact window over the title screen.
4. Open a mod card to read its description and project resources, or use the download action to review an available source.
5. Restart Minecraft after installing files so NeoForge can load them.

## Quick start for pack authors

1. Launch the game once to generate `config/coolpackhelper.json` and `config/coolpackhelper.schema.json`.
2. Open **Mods → CoolPackHelper Editor**, or select CoolPackHelper in the NeoForge mod list and open its configuration screen.
3. Set the pack identity and language policy.
4. Add mod requirements manually, import the local `mods` folder, or scan Modrinth/CurseForge.
5. Add localized descriptions, project metadata, and one or more download sources.
6. Preview the requirements screen, validate the workspace, and use the top-bar action to save the complete configuration.
7. Export `config/coolpackhelper.json` with the pack. Do not distribute files from `local/coolpackhelper/`; they contain per-instance state, author settings, history, and backups.

The generated example entries are disabled. CoolPackHelper will not show a requirements warning until an author enables and configures at least one entry.

## Safety model

CoolPackHelper treats a link and a safe automatic installation as different things. An ordinary page can always remain a browser-only resource. Automatic installation requires enough trustworthy metadata to verify the selected file.

Trust levels are visible before installation:

- **Platform** - resolved through Modrinth or CurseForge metadata and constrained to their official delivery hosts.
- **Repository** - a GitHub Release asset with a SHA-256 digest; the player must review the repository and owner.
- **Unverified** - a third-party HTTPS file with a configured SHA-256 or SHA-512 digest; it requires an explicit individual decision and is excluded from bulk installation.

A digest proves that the downloaded bytes match the expected bytes; it does not prove that those bytes are harmless. Pack authors remain responsible for the sources they configure, and players remain in control of non-platform downloads.

## Building from source

```powershell
.\gradlew.bat test build
```

The development build is written to `build/libs/cph-1.0.0.jar`.

## Project status

CoolPackHelper 1.0 currently focuses on mod requirements, author tooling, localization, metadata, and guarded downloads. Pack-management ideas such as resource-pack/shader management, control presets, Discord Rich Presence, branding, server-pack generation, reusable content presets, and automatic release changelogs are intentionally outside the 1.0 scope and are discussed in the roadmaps above.

## License

All Rights Reserved. See the project distribution page for release-specific permissions.
