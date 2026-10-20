# Milestones (one session and one PR each)

- [x] M0 Skeleton: Gradle project, Compose, theme from tokens, en/ar strings, RTL, dark mode, CI workflow that builds and uploads a debug APK
- [x] M1a Schedule engine and rules, pure Kotlin with unit tests (done in PR: claude/m1a-schedule-engine)
- [x] M1b Room database (person, medicine, schedule, pause, dose event; settings go to DataStore in M8; schema v1 exported), DAOs, repository, migrations test
- [x] M2 Add medicine flow (steps 1-3 for every frequency, review plan, success)
- [x] M3 Today screen (Take, Skip with reason, Undo, past and future days, empty and paused states)
- [x] M4 Reminders: exact alarms, channels, notification with 3 actions, full-screen reminder, permissions screens, re-arm on boot/update/time change (quiet hours move to M8 Settings)
- [x] M5 Medicine detail, edit, pause/resume, stop/restart, stock and refill alerts (refill alert settings and daily notification: M5f)
- [x] M6 History and doctor report PDF
- [x] M7 People
- [x] M8 Settings: language, dark, 24-hour, accessibility (large text, high contrast, simple mode), meal times, late-dose setting
- [x] M9 Backup and restore (weekly to folder, optional Google Drive, restore checks, delete all)
- [x] M10 Feedback, rating, crash-report prompt (email intents), privacy policy, about
- [ ] M11 Widget
- [ ] M12 Large-text and TalkBack polish; test on real phones
