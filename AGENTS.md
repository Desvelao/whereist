# AGENTS.md

Guidance for AI coding agents working in this repository. `README.md` is for humans.

## What this is

A native Android app (Kotlin, Jetpack Compose, Material 3, Room) to save where a thing is and find it with one
search. **Fully offline.** Single `app` module.

## Hard constraints (do not break)

1. **No `INTERNET` permission**, and no analytics, ads, remote config or network libraries. After any dependency
   change, check the merged manifest. The only runtime permissions are foreground `ACCESS_FINE_LOCATION` and
   `ACCESS_COARSE_LOCATION` (Here button) and `POST_NOTIFICATIONS` (reminders, asked when one is first set); the
   manifest also declares `RECEIVE_BOOT_COMPLETED` (reminders are rescheduled after a reboot or an app update).
   Never add background location or `CAMERA` (photos use the system camera through `FileProvider`). Reminders use
   an inexact alarm, so they can arrive a few minutes late; do not add the exact-alarm permission. Glance (widget) brings WorkManager, which adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and
   `FOREGROUND_SERVICE`; that is fine, `INTERNET` is not.
2. **Docker-only toolchain.** Never ask the user to install Java, Gradle, Android Studio or SDK packages on the
   host. Every build, test and `adb` command runs through `make` (which uses `docker compose run --rm dev`).
3. **Name only in `app.properties`.** App name, id and URL are build parameters. Do not hard-code the name in
   code, resources or docs: read `R.string.app_name` / `BuildConfig`. Internal names stay neutral
   (`Locator`, `app.locator`, `android-dev`).
4. **Native only.** No cross-platform frameworks, no WebView.
5. **No secrets in git:** `release.jks` and `keystore.properties` are git-ignored and must never be deleted or
   overwritten (a released keystore cannot be regenerated). Check `git status` before committing; never
   `git add -A` blindly.
6. **Changelog.** Every user-visible change adds a line under `[Unreleased]` in `CHANGELOG.md`. CI
   (`.github/workflows/release.yml`) builds on GitHub's runner, not in Docker; on a tagged release the entries move
   to a `## [x.y.z] - date` heading.

## Commands

`make check` (tests + lint), `make debug`, `make install`, `make release`, `make adb-devices`, `make help`.
CI calls `make check RUN=` and `make release RUN=`; keep the Makefile and workflow in sync. A change is done when
tests pass, `lint` reports 0 errors and the release build still builds (`make check-release` checks that without
signing; `make release` needs the keystore). Do not chain `git commit` after a check
through a pipe: check the result first.

## Conventions

- **Database:** a schema change needs a version bump, a real `Migration` plus migration test, and the exported
  schema in `app/schemas/` committed. Never use destructive migration. The newest `LocationRecord` of an item is
  its current location; records are append-only.
- **Usual place:** `Item.usualPlaceId` (`ON DELETE SET NULL`) and `usualText` say where an item normally lives;
  `ItemWithCurrent.isOutOfPlace` compares them with the newest record and `ItemRepository.putBack` saves them as a
  new record.
- **Places:** `Place` is a flat table with `parentId`; all tree logic (paths, order, subtree, breadcrumb, the browse
  view) is pure code in `data/PlaceTree.kt`, cycle-safe. A record keeps its text and may also have a `placeId`
  (`ON DELETE SET NULL`); deleting a place goes through `PlaceDao.deleteKeepingChildren` so inner places move up.
  Sibling names are unique (accent- and case-insensitive); `addPlace` reuses an existing sibling.
- **Photos and backup:** photos are files in `filesDir/photos/`, referenced by relative path; always resolve them
  through `PhotoStore.file()` (it refuses anything else). Backup import validates into a staging folder before it
  replaces data, and ignores zip entries that are not `data.json` or a valid photo path. The backup JSON is
  version 3 (places, and each item's usual place); version 1 and 2 files must keep importing. A change to it needs a `BackupCodec.VERSION` bump and a
  decoder for every older version, and places must be written parents-first.
- **Quick save and reminder:** the widget (`widget/`) and the static shortcut (`res/xml/shortcuts.xml`, which reads the
  package from the generated `application_id` string) both open `QuickSaveActivity`, a foreground screen: never take
  a GPS fix from a receiver or background code. The reminder is an inexact-while-idle alarm
  (`reminder/AlarmReminderScheduler`), so no exact-alarm permission; `BootReceiver` reschedules after a reboot.
  `remindAt` is not part of backups. Keep scheduling behind `ReminderScheduling` so tests can fake it.
- **Location:** use `LocationProvider` (a fake in tests); the app uses the platform `LocationManager`, no Play Services.
- **UI text** lives in string resources: English in `res/values` (the fallback), plus `values-es`, `values-de`, `values-fr` and `values-it`; add every string to
  all five. A new language also goes in `res/xml/locales_config.xml` and `AppLanguage.supported`. The language select in Settings
  uses `AppLanguage` (`LocaleManager` on Android 13+, preferences plus `attachBaseContext` before). Touch targets at least 48dp, `contentDescription` on icon-only controls.
- **Theme and design:** tokens live in `ui/theme/Theme.kt`; colour roles are documented there (cards =
  `surfaceContainerLow`, bars and sheets = `surfaceContainerHigh`, icon tiles = `surfaceContainerHighest`, warnings =
  `MaterialTheme.extras`). Keep `surfaceVariant` different from the container colours. Dark and light must both work.
  On a row the location is the biggest line; build screens from `ui/Components.kt` (`PillButton`, `PillOutlinedButton`,
  `IconTile`, `SectionLabel`, `InfoChip`, `StatusDot`). Fonts: Manrope (headings) and Inter (text), bundled in
  `res/font/`, licences in `fonts-license/`. Resolve strings with `stringResource`, never `LocalContext.getString`.

## Gotchas

- AGP 9 has built-in Kotlin: do not apply `org.jetbrains.kotlin.android`. `compileSdk`/`targetSdk` 37, `minSdk` 26.
- The launcher icon folder must stay `mipmap-anydpi-v26`.
- The debug and release builds use different signatures: uninstall one before installing the other.
- Run adb only in the container (`network_mode: host`); do not run it on the host at the same time.

## App parameters

`APP_NAME`, `APP_ID`, `APP_URL`, `APP_VERSION_NAME`, `APP_VERSION_CODE` come from, in order: a `-P` Gradle
property, an environment variable, `app.properties` (name, id, url only), then the neutral default. Signing uses
`SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` or `keystore.properties`.
