# Release and full-test checklist

## Build a release APK (one time setup)
1. Make a release key once and keep it safe (lose it and nobody can update the app):
   `keytool -genkeypair -v -keystore pillbox-release.jks -alias pillbox -keyalg RSA -keysize 4096 -validity 10000`
2. Build with the key set in the environment (never commit it):
   `PILLBOX_KEYSTORE=/path/pillbox-release.jks PILLBOX_STORE_PASSWORD=... PILLBOX_KEY_ALIAS=pillbox PILLBOX_KEY_PASSWORD=... ./gradlew assembleRelease`
3. Get its SHA-1: `keytool -list -v -keystore pillbox-release.jks -alias pillbox`
4. Google Cloud, project pill-box: create a second Android OAuth client (package `com.pillbox`) with that SHA-1.
5. OAuth consent screen: press **Publish app** so anyone can sign in to Drive (no test-user list).
6. Raise `versionCode` for every build you hand out.

## Full test (phone, English then Arabic)
- Install fresh. Add 3 medicines (daily, hours, as needed). Allow notifications and exact alarms.
- Reminder fires on time: lock screen full-screen reminder, Take, Skip, Snooze, Take all from the notification.
- Restart the phone: next reminder still fires. Change time zone and the time: still correct.
- Quiet hours, sound, read aloud, late-dose setting.
- Settings > Font size 200% (phone settings) on every screen: nothing cut off, nothing overlapping.
- TalkBack on: each dose row reads as one sentence; every button has a name.
- Arabic: layout mirrored, times like `8:00 ص`, Latin medicine names do not scramble the sentence.
- Dark theme, high contrast, 24-hour time.
- Backup to a folder, restore, delete all data. Google Drive: sign in, back up, restore.
- Widget: add Small, Medium, Large; Take all from the widget; dark mode.
- Feedback and rating open the email app; nothing is sent by the app.
