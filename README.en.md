<div align="center">

[简体中文](README.md) | [繁體中文](README.zh-TW.md) | English

# Cove Recipe

**An offline, local-first Android personal recipe book with kitchen mode, cooking timer, source-tracked shopping list, and full ZIP backup.**

[![Release](https://img.shields.io/github/v/release/vanvan-program/cove-recipe?style=flat-square)](https://github.com/vanvan-program/cove-recipe/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square)](#installation--data-safety)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21%20%7C%20Compose%20%7C%20Room-7F52FF?style=flat-square)](#tech-stack--project-structure)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

[Download v1.5.2 APK](https://github.com/vanvan-program/cove-recipe/releases/download/v1.5.2/CoveRecipe-1.5.2.apk) · [GHFast mirror](https://ghfast.top/https://github.com/vanvan-program/cove-recipe/releases/download/v1.5.2/CoveRecipe-1.5.2.apk) · [Release notes and SHA-256 checksum](https://github.com/vanvan-program/cove-recipe/releases/tag/v1.5.2)

[Changelog](CHANGELOG.md) · [Third-Party Notices](THIRD_PARTY_NOTICES.md) · [Issues](https://github.com/vanvan-program/cove-recipe/issues)

</div>

---

## Overview

Cove Recipe is a Chinese-language personal cookbook for Android 8.0 (API 26) and later. There are no ads or accounts. Recipes, photos, and shopping lists stay on your phone. You can record recipes and cook offline. The app connects to the internet when you check for updates or download a new version.

## What’s new in v1.5.2

- The cover uses lighter tan leather, with a warm brown highlight for the selected tab. A swipe slides the whole sheet out or back into place with a slight tilt.
- Add buttons look like stickers, the recipe picker uses a photo-print button, and Settings has four sticky notes.
- Bulk clearing updates the shopping list and counts immediately. Undo for an individual deletion appears next to the item count.

## Screenshots

Captured from v1.5.2 on an Android 16 emulator with sample recipes. You can also download and open the [interactive HTML preview](ui-preview/cove-recipe-notebook.html).

| Recipe list | Recipe details | Shopping list |
| :---: | :---: | :---: |
| <a href="docs/screenshots/152-home.webp"><img src="docs/screenshots/152-home.webp" width="230" alt="Recipe list" /></a> | <a href="docs/screenshots/152-detail.webp"><img src="docs/screenshots/152-detail.webp" width="230" alt="Recipe details" /></a> | <a href="docs/screenshots/152-shopping.webp"><img src="docs/screenshots/152-shopping.webp" width="230" alt="Shopping list" /></a> |

| Settings | More settings | About |
| :---: | :---: | :---: |
| <a href="docs/screenshots/152-settings.webp"><img src="docs/screenshots/152-settings.webp" width="230" alt="Settings" /></a> | <a href="docs/screenshots/152-settings-bottom.webp"><img src="docs/screenshots/152-settings-bottom.webp" width="230" alt="More settings" /></a> | <a href="docs/screenshots/152-about.webp"><img src="docs/screenshots/152-about.webp" width="230" alt="About" /></a> |

## Key Features

- **Structured Recipe Book**:
  - Store cover photos (compressed to ~1600px max edge in private storage), structured **ingredients**, **seasoning notes**, **prep instructions**, reorderable steps, heat levels, and step timers.
  - Drag-and-drop manual ordering (preserving pinned groups + accessibility move actions), up to 3-level categories, freeform tags, unified search, favorites, pins, and recently used recipes.
- **Kitchen Mode & Global Cooking Timer**:
  - Large typography, keep-screen-on, step-by-step navigation, and quick ingredient/seasoning overlay while cooking.
  - Monotonic-clock global countdown timer (1s – 7d via H/M/S wheel picker) with live notification countdown and continuous vibration alert on completion until acknowledged.
- **Source-Tracked Shopping List**:
  - Add ingredients and seasonings from recipe details, multi-select on home, or directly inside the shopping list; duplicates merge automatically while listing all source recipe names.
  - Supports buying notes, checked state, and undo on delete.
- **ZIP v4 backup and restore**:
  - A single ZIP contains recipes (including the recycle bin), categories, tags, the shopping list, and cover images. The app can import backup formats v1 through v4.
  - Validates schema version, referential integrity, size limits (JSON ≤ 16 MB, image ≤ 32 MB, total ≤ 256 MB), and image paths before atomically replacing the database inside a transaction.

## Installation & Data Safety

- **First install or upgrade from v1.4.0.** Use either download link at the top for the same signed APK. To keep your recipes, open the APK with the old version still installed and choose **Update**.
- **Updates from v1.5.0 onward.** Open Settings → About version → Check for updates (设置 → 关于版本 → 检查更新). When an update is available, tap 下载更新 to download it. After verification, tap 安装更新 and confirm in Android. If v1.5.0 cannot check for updates, download the APK above and install it over the existing app.
- **Slow downloads.** The app starts with GitHub and tries GHFast if the connection times out or averages below 128 KiB/s for 8 seconds. To prefer your own HTTPS mirror, enter its base URL in 下载源设置. Downloads resume across sources when the next server supports byte ranges.
- The package name is `cn.myrecipe.app`. To upgrade using your own build, sign it with the same key as the installed app. You can export a ZIP backup from Settings. Android cloud backup and device transfer are disabled.
- Grant **Notifications** and **Exact Alarms** for reliable background cooking timers. Exported ZIP backups are unencrypted—store them safely.

## Tech Stack & Project Structure

- **Stack**: Kotlin `2.0.21` · Jetpack Compose (BOM `2025.02.00`) · Material 3 · Room `2.6.1` (database v4 with migrations from v1 through v3) · Navigation `2.8.9` · DataStore `1.1.3` · AGP `8.9.2` · Gradle `8.11.1`
- **Architecture** (single `app` module):
  - `core` — Room database, image storage, timer service, ZIP backups, in-app updates, and theming
  - `data/repository` — Local repositories
  - `feature` — Recipes, Editor, Kitchen Mode, Shopping List, Categories/Tags, and Settings (ViewModel + StateFlow)

## Building & Testing

- **Requirements**: JDK `17+` · Android SDK `36` · Gradle `8.11.1`

```powershell
git clone https://github.com/vanvan-program/cove-recipe.git
cd cove-recipe
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
- On macOS / Linux, use `./gradlew`. With a local `.tools/` cache, run `.\scripts\build.ps1 -Offline` (add `-DeviceTests` for emulator tests).
- The v1.5.1 build passed unit tests, Android lint, and Android 15 emulator tests. [View the build and test results](https://github.com/vanvan-program/cove-recipe/actions/runs/44917329446)
- For each release, run `python scripts/release-feed.py CoveRecipe-X.Y.Z.apk updates/releases.json` with the signed APK. Commit the resulting manifest to the default branch and upload the same `releases.json` as a release asset.

## License

Licensed under the **[MIT License](LICENSE)**. Third-party attributions are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
