# Pill Box (علبة الدواء) — Android medicine reminder

Free, fully offline medicine reminder and tracker for Android. Built for older adults who have difficulty seeing small text. Arabic (RTL) and English from day one.

## Source of truth
- `docs/DESIGN.md` — audit, rule tables (late dose, pause, hours schedule, min gap and daily max), Android notes, decisions, message inventory, large-text rules. **Read before building any feature.**
- `design/en/*.png` and `design/ar/*.png` — every screen, light. Names match screens (e.g. `add-med-2-hours.png`, `today.png`). Dark: `today-dark`, `med-detail-dark`, `settings-dark`. 24-hour: `today-24`, `med-detail-24`. Large text (200%): `large-*`. Messages and errors: `stock-updated`, `unsaved-changes`, `save-failed`, and so on. If a screen is not drawn, ask; do not invent.
- `design/tokens/pillbox-tokens.json` and `PillBoxTheme.kt` — colors (light, dark, high contrast), type scale, radius, spacing. **Use these tokens only. Never hard-code colors or sizes.**
- Decisions already made (do not reopen): no app lock in v1; dark theme yes; 24-hour time yes; as-needed medicines yes; weekly automatic backup to a user-picked folder plus optional Google Drive; feedback, rating and crash reports go through the user's email app (ACTION_SENDTO); the app sends nothing by itself.

## Stack
Kotlin, Jetpack Compose, Material 3, Room, DataStore, Glance (widget), WorkManager (weekly backup only), AlarmManager exact alarms for doses. Min SDK 26. Single module to start. ViewModel + repository; no DI framework unless it clearly pays for itself (manual DI or a tiny container is fine). No analytics, no accounts. Network permission only for the optional Google Drive backup.

## Non-negotiables
- Accessibility first: touch targets >= 48dp, text >= 13sp, support system font scale up to 200% (heights are min-heights, text wraps), contrast >= 4.5:1, color is never the only signal, TalkBack label on every icon button, each dose row reads as one sentence, toasts announced as status.
- RTL: Arabic UI is a first-class language. Arabic time is digits then ص/م with ص/م on the left (use a right-to-left isolate). Digits and week start follow Settings. Use plurals; Arabic has dual and few forms.
- Reminders are the product. Use exact alarms, schedule only the next dose per medicine and re-arm after each. Re-arm on boot, app update, time change and time zone change. Handle denied notification and exact-alarm permissions with the designed screens. Always keep the notification itself as fallback for full-screen intent.
- Notifications show at most 3 actions (Take all, Snooze, Open). Per-medicine Take/Skip live on a full-screen reminder screen.
- All user data stays on the phone. Backup file is versioned; check version on restore.
- Strings go in resources (`values/` and `values-ar/`), never inline.

## Working style
- One milestone per session/PR (see `docs/MILESTONES.md`). Small commits. Branch `claude/<milestone>`; open a PR, do not push to `main`.
- Write unit tests for all schedule, late-dose, pause, minimum-gap and daily-max logic (pure Kotlin, no Android needed). Add Compose screenshot tests (Roborazzi or Paparazzi) for key screens in en/ar, light/dark, font scale 1.0 and 2.0.
- Verify before saying done: `./gradlew testDebugUnitTest assembleDebug` (or the GitHub Actions equivalent). If the sandbox cannot download the Android SDK, say so and rely on the CI workflow instead.
- When the design and Android disagree, follow Android best practice and note the difference in the PR description.
- Ask the user (Mahmoud, UX/UI designer, not an Android developer) only when a decision changes product behavior. Explain choices briefly in plain language.
