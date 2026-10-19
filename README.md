# Pill Box (علبة الدواء)

Free, offline medicine reminder for Android with large, readable text. Arabic and English.

Start with `CLAUDE.md`, then `docs/DESIGN.md` and `docs/MILESTONES.md`. Screens are in `design/`.

## Build

- Locally: `./gradlew testDebugUnitTest assembleDebug` (needs the Android SDK; set `ANDROID_HOME` or `local.properties`).
- CI: every push runs `.github/workflows/android.yml`. The run page has three downloads: `pillbox-debug-apk` (install on a phone), `screenshots` (the screens in English and Arabic, light and dark, normal and 200% text) and, if something failed, `test-reports`.

Fonts: Inter and Baloo Bhaijaan 2 are bundled under the SIL Open Font License (`licenses/`).
