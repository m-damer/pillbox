# Pill Box (علبة الدواء)

Free, offline medicine reminder for Android with large, readable text. Arabic and English.

Start with `CLAUDE.md`, then `docs/DESIGN.md` and `docs/MILESTONES.md`. Screens are in `design/`.

## Build

- Locally: `./gradlew testDebugUnitTest assembleDebug` (needs the Android SDK; set `ANDROID_HOME` or `local.properties`).
- CI: every push runs `.github/workflows/android.yml`. The run page has three downloads: `pillbox-debug-apk` (install on a phone), `screenshots` (the screens in English and Arabic, light and dark, normal and 200% text) and, if something failed, `test-reports`.

## Download and updates

The signed app is on the Releases page. `https://github.com/m-damer/pillbox/releases/latest/download/PillBox.apk` always points to the newest version. The app looks for a new version about once a week (it can be turned off) and shows its own Updates screen; Android asks for confirmation before installing. Obtainium can also follow the Releases page.

Fonts: Inter and Baloo Bhaijaan 2 are bundled under the SIL Open Font License (`licenses/`).
