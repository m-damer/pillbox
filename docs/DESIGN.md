# Pill Box (علبة الدواء): Pre-Development Audit

> **Source of truth for development.** Exported from the design audit doc (Oct 2026). The design canvas is v102. Some status lines in the Verdict and checklist predate later fixes: all blockers and decisions are done, and dark, 24-hour, large-text and message screens now exist. Screenshots are in `design/`.


2026-10-19 · @Mahmoud Damer

**Verdict (updated 19 Oct, canvas v90): the 9 blockers are fixed in the design, in English and Arabic. A few items and the decisions below remain; development can start.** The offline app is well designed and complete in both languages: 56 English and 56 Arabic screens, no dead links, every flow ends somewhere. The gaps are in what the screens do not yet cover (dose amount, schedules, restore, delete-all) and in a few places where the design cannot be built on Android as drawn.

Scope checked: all 112 offline screens (English and Arabic), their links, visible text, font sizes, tap-target heights and colour contrast, measured from the design files. The online half of the canvas was not audited; see Decisions. Items marked *measured* come from that scan; the rest are my review.

## Status after the fix round (canvas v90)

**Fixed in the offline design (English and Arabic):**

- Blockers 1 to 9: dose amount; days, start date and a 3-step add flow; Once, 3 times and As needed variants; every-few-hours rule (first dose + N hours); Add person, Edit person, Remove person all reachable; Restore (pick, check, done, wrong file) and Delete all (confirm, done); notification shade with 3 actions; 24-hour and minutes time pickers; privacy wording ("nothing is sent unless you choose to contact the developer"); feedback and ratings are sent to the developer's email with optional email or phone, and the Privacy policy says so.
- Missing screens: Finished tab and finished medicine with Restart, out of stock, early dose warning, permission denied, People empty, backup saved and weekly backup warning, Simple mode.
- Polish: touch targets raised to 48 px, week strip text 13 px with 3-letter days, contrast fixed (green, placeholder), sample data on Sat 17 Oct, meal offset 30 min, Check your medicines shows only the new medicine, delete text mentions the doctor report.

**Still open:**

- Done (canvas v96): all 7 decisions and crash reports are applied. Dark theme now covers 13 screens in each language, with text contrast measured at 4.5:1 or better, and the new screens have flow arrows. Page 2 holds the online draft; dark theme; 24-hour screens; As needed log and limit; backup to a folder and Google Drive; email-based feedback and rating; app lock removed. No decisions remain open.
- High contrast rule: black text on white, 2 px borders on cards, no tinted blocks, darker indigo.
- Done in v93: widget settings, History empty state, Edit my profile (name on the doctor report), start-date picker and chosen-date screens for all three day patterns.

**Rules for the developer:**

- Late dose: fixed-gap medicines (every N hours) always ask; others follow the Late setting.
- Pause: no reminders, no refill alerts, "days left" frozen, history keeps a Paused mark; resumes 9:00 AM on the end date.
- Hours schedule: first dose, then every N hours, wrapping past midnight, all 24 hours.
- Minimum gap: As needed and hours-based medicines store a minimum gap; an early Take shows the warning sheet and the user may still confirm.
- Time zones: dose times keep the same local clock time; interval doses keep real elapsed time.
- Digits and week start follow the Language settings. Arabic time is always digits then ص or م, with ص and م on the left.
- Success messages: after Start my reminders a success prompt ("Metformin was added", first reminder time, Go to Today or Add another medicine). After Save changes on Edit medicine, a "Changes saved" message on the Medicine screen. Both are also announced to TalkBack.

## Rule tables for the developer

These turn the screens into logic. Where the design is silent I chose a safe default, marked **Default**. Change a default only by changing the screen too.

### 1. Late dose

A dose counts as late after the threshold in Settings (15 min, 30 min or 1 hour). Before that it is on time.

| Medicine kind | Setting: Ask me | Setting: Move next doses | Setting: Keep my times |
|---|---|---|---|
| Fixed times (once, twice, 3 times a day, chosen days, every N days) | Show the Taken late sheet: Move next doses or Keep my times, with Always do this | Shift only today's remaining doses of this medicine later by the delay. Tomorrow returns to the normal times | Record the dose as late. Change nothing |
| Every few hours (fixed gap) | Always ask, whatever the setting | Always ask. Move: next dose = actual time + N hours, and the rest of the day follows | Always ask. Keep: next dose stays; show the real gap ("gap is 7 h 20 min") and the early-dose warning if it is below N hours |
| As needed | Not applicable: there is no due time, so nothing is ever late | Not applicable | Not applicable |

- **Default, when a dose becomes Missed:** when the next dose of the same medicine is due, or at 11:59 PM for the last dose of the day. Until then the dose stays "late" and Today asks "Did you take these?".
- Late doses show a Late mark with the minutes in History and in the doctor report. Moving doses never changes History for doses already taken.
- "Always do this" saves the choice into the Late setting and applies to fixed-time medicines only.

### 2. Pause

| Thing | While paused | On resume |
|---|---|---|
| Reminders | None | Restart at the next scheduled time. No catch-up for missed doses |
| Today | Doses are hidden; a Paused banner shows the end date | Doses return |
| Refill alerts | None | Restart |
| Days left | Frozen at the paused value; stock does not decrease | Counting continues |
| History and 30-day percentage | Paused days show a Paused mark and are not counted as missed or taken | Normal counting |
| Alerts to people you care for (offline, on this phone) | None | Restart |
| Widget | Medicine hidden | Shown |
| Schedule on return | Days of the week: same weekdays. Every N days: counting restarts from the first day on or after the resume date. Hours: first dose is the next first-dose time |  |

- Length options: 1 day, 3 days, Other (days), Until I resume. A timed pause ends at **9:00 AM** on the end date (Default). Resume now ends it at once. Editing or deleting a paused medicine is allowed; deleting follows the normal delete warning.

### 3. Every-few-hours schedule

| Rule | Detail |
|---|---|
| Inputs | First dose time and N hours (1 to 24, as drawn) |
| Doses | First dose, then first + N, first + 2N, and so on, wrapping past midnight, while the dose is before the next day's first dose |
| Minimum gap guarantee | A dose is added only if at least N hours remain before the next day's first dose. So the gap is never below N |
| Example, N = 2, first 8:00 AM | 8, 10, 12, 14, 16, 18, 20, 22, 0, 2, 4, 6 (12 doses) |
| Example, N = 5, first 8:00 AM | 8:00 AM, 1:00 PM, 6:00 PM, 11:00 PM (4 doses; 4:00 AM is dropped because 4:00 AM to 8:00 AM is only 4 hours) |
| Day of a past-midnight dose | It belongs to the day it rings, for Today and History |
| Editing | Changing the first dose or N rebuilds future doses only. History is kept |
| Late "Move next doses" | The chain restarts from the actual time for the rest of the day; the next day starts again at the first dose (Default) |
| Time zone change | Interval doses keep real elapsed time; first dose keeps local clock time |

### 4. Minimum gap and daily maximum

| Medicine kind | Gap rule | Daily maximum | When the user taps Take too soon |
|---|---|---|---|
| As needed | Least time between doses (designed default 6 hours, user can change), measured from the last actual taken time | Most doses in one day (designed default 4) | Gap not met: Too soon sheet with Wait or Take anyway. Take anyway logs the dose with an Early mark |
| Every few hours | Gap = N hours, measured from the last actual taken time | The number of doses in the cycle | Same sheet. Take anyway logs it with an Early mark |
| Fixed times | None. The schedule already spaces the doses | None | Not applicable |

- **Daily maximum reached (As needed only):** no Take button. The screen says the most for today is reached, when the next dose is allowed, and offers OK and Undo last dose. There is no override on purpose; the user is told to call a doctor or pharmacist if pain continues. A day is the local calendar day (Default); the gap rule still applies across midnight.
- Every As needed take is logged with the time. Today shows "2 of 4" and the next allowed time. Early marks appear in History and the doctor report.
- Store per medicine: minGapMinutes, maxPerDay (nullable), and on each dose event a flag early (true or false).

## Blockers

| # | Problem | Why it blocks development | Fix |
|---|---|---|---|
| 1 | No dose amount in Add medicine. Step 2 never asks "how much per dose", yet review, Today and notifications show "1 capsule", "1 tablet". | The data model cannot store what the user took. Half tablets and syrup (5 ml) are impossible. | Add a "Dose" field to step 2 (number + unit that follows the type: tablet, capsule, ml, drops, injection). |
| 2 | Schedules are only daily patterns. No weekdays, no every other day, no weekly, no start date. | Many real medicines (weekly, alternate day, Mon/Wed/Fri) cannot be entered. The schedule table needs these columns from day one. | Add "Which days" (Every day / Specific days / Every N days) and "Starts" (Today / pick date) to step 2. |
| 3 | "Only when needed" and "3 times a day" have no screen. Only "Twice a day" and "Every few hours" are designed. | Four of five frequency chips lead to undefined states. | Design Once, 3 times and As needed variants. As needed needs a "Take now" log and a minimum gap and daily maximum. |
| 4 | Every-few-hours list starts at 6:00 AM while the first dose says 8:00 AM. | The generation rule is undefined: doses must start at the first dose and repeat every N hours. | Rule: first dose + N hours, wrapping past midnight. Redraw the 2-hour example as 8, 10, 12 ... 6. |
| 5 | Remove person is unreachable (measured: no link leads to it). Edit person has no Remove button. | A designed screen with no entry point. | Add "Remove" on the Edit person screen. Separate Add person and Edit person titles. |
| 6 | No screens for Restore from backup or Delete all my data. Both buttons exist in Backup. | Delete-all is destructive and has no confirm; restore has no file picker, overwrite warning, success or error. | Design: pick file, overwrite warning, success, wrong-file error; delete-all confirm with typed or double confirm. |
| 7 | The notification mock has 7 buttons. Android allows 3 action buttons on a notification. | The core reminder cannot be built as drawn. | See Android notes: 3-action notification plus a full-screen reminder screen that carries the per-medicine buttons. |
| 8 | Time format setting (Phone / 12 / 24 hour) exists, but every screen and the time picker are 12-hour only. | Users who pick 24-hour get nothing. | Design the 24-hour picker and one 24-hour Today state, or remove the setting from v1. |
| 9 | Privacy copy contradicts Send feedback and Rate app. Privacy says "Nothing is sent to us"; feedback asks for email or phone and rating says it goes straight to you. | Play Store data-safety answers and legal text must match what the app does. | Resolved: feedback and ratings go to the developer's email with optional contact details, and the Privacy policy states it. |

## Missing screens and states

Beyond the blockers above. Each needs an English and an Arabic version.

| Area | Missing | Priority |
|---|---|---|
| Medicines | Finished tab (the Active / Finished switch has only the Active state); restart a stopped medicine (Stop says "You can restart it any time" but no screen does it); out-of-stock state (0 left) and what Take does then | High |
| Today | Person switched (Today for Mom shows whose list it is); nothing planned today; Today when all medicines are paused; banner when permissions were skipped ("Reminders are off") | High |
| Permissions | Denied and "don't ask again" states for notifications and exact alarms; what "Set up later" leads to | High |
| Time picker | Minute step (only the hour dial is drawn); keyboard-entry mode | Medium |
| People | Empty state (only me); first-time explanation of what People are on one phone | Medium |
| Backup | Weekly backup: where it is saved and a warning that a backup on the same phone is lost with the phone | High |
| Report | Patient name: the PDF shows "Ali Hassan" but the app never asks for a name. Add a profile name (Welcome or Edit person) | Medium |
| History | Empty state (first week); per-medicine filter | Medium |
| Legal | First-launch acceptance. Terms and Privacy are reachable only from About; link them on Welcome | Medium |
| Security | Not in v1 (decided): app lock and "hide medicine names on the lock screen" | Medium |
| System | Widget configuration; "Take all" result on the widget; app update or new-phone first run after restore | Low |
| Accessibility | High contrast and Simple mode are settings with no designed result. Draw one screen of each, or specify them as rules | Medium |

## Logic and data-model gaps

- **Sample data disagrees with itself.** Today says Sat 17 Oct; Refill says Mon 19 Oct. Metformin shows "4 days left" on both days. Pick one date and recompute all stock numbers.
- **"After meal · 0 min" versus the times shown.** Dinner is 8:00 PM but Metformin rings at 8:30 PM; lunch is 1:30 PM but it rings at 2:00 PM. Either the offset is 30 minutes (change the label) or the times are wrong.
- **Add flow shows other medicines.** After saving one medicine, "Check your medicines" lists Amoxicillin and Metformin together, a leftover of the removed prescription flow. It should show only what was just added, then Review plan.
- **Late-dose rule.** Fixed-gap medicines (every 8 hours) always ask; others follow the setting. Write this as a table of rules for the developer: medicine kind × setting × result.
- **Pause.** Define what happens to refill alerts, "days left" and history while paused, and when it auto-resumes (at 9:00 AM on the end date?).
- **Deleting a medicine** deletes its history and changes the 30-day percentage and the doctor report. State that, or offer "Stop" first.
- **Hours-based schedules** need a decision: run all 24 hours, or only while awake? Add a "from / until" window if not.
- **Overdose guard.** For As needed and every-few-hours medicines, store a minimum gap and warn on an early Take ("Last taken 1 h ago").
- **Time zones and daylight saving.** Specify: doses stay at the same local clock time; interval doses keep the real elapsed time.
- **Numbers and week start settings.** The Language screen offers Western or Arabic-Indic digits and week start Sat / Sun / Mon. Today's week strip is drawn Monday-first only. State that it follows the setting.
- **Notifications for other people.** Offline, Mom's and Dad's reminders ring on this phone only. The screens say so; keep this wording on the Add person and Caregiver screens too.
- **Data model to hand over:** Person, Medicine (type, strength, unit, stock), Schedule (days, times or interval, start, end, with-food rule), Dose event (planned time, taken time, status, skip reason), Settings, Backup file with a schema version.

## Design and accessibility (measured)

The design rules (13 px minimum, 48 px targets, 4.5:1 contrast) are held on most screens. These are the exceptions found in the English files; the Arabic files share the same layouts.

| Finding | Where | Fix |
|---|---|---|
| Tap target under 48 px: 44 px | Frequency chips in Add medicine step 2; Take / Skip row on Today, Dose taken, Missed dose | Raise to 48 px |
| Tap target under 48 px: 40 px | Edit and Delete (trash) buttons on Medicine detail and Medicine paused | Raise to 48 px |
| Tap target under 48 px: 36 px | Person switcher "Me" on Check your medicines and Medicines (3 states); "Edit" for meal timing on Review plan | Raise to 48 px, or make the whole header the target |
| Tap target 38 px | The 12 dose time boxes on the Every-few-hours screen | Allow 48 px height; 2 per row if needed |
| Text under 13 px | Weekday labels in the week strip on 5 Today screens (7 instances each) | 13 px minimum; shorten to 1 letter or allow wrapping |
| Contrast 4.25:1 | White text on green (#2E8B57) in the History calendar, 22 uses | Darken green to about #277A4C (4.5:1 or more) |
| Contrast 3.81:1 | Placeholder text (#7B829C) on white in Add medicine and Send feedback | Use #667085 or darker |
| Contrast 4.48:1 | Muted text (#666D82) on grey in Today states | Darken slightly to pass 4.5:1 |

**Rules to specify, since the mockups cannot show them:**

- **Large text.** The Largest setting must be tested at 200% system font scale. Fixed heights (44, 56, 88 px) must become minimum heights so text wraps instead of clipping. The Today list, notification and time boxes are the most at risk.
- **Dark theme.** Not designed. Android users expect it. Decide: ship light-only in v1 (and say so), or draw Today, Notification and one form in dark.
- **Colour is never the only signal.** Already true in most places (check mark on chips, "!" in History). Keep it as a rule for taken, late, missed and paused.
- **TalkBack.** Each dose row reads as one sentence ("Metformin, 1 tablet, after meal, 2:00 PM, not taken") with Take and Skip as actions. The timeline bar has a text description; keep it.
- **Small screens.** Check 360 × 640 dp. The 1010 px Every-few-hours screen scrolls; the primary Save button should stay pinned.
- **Good as is:** Undo after Take, a reason sheet on Skip, confirm dialogs on destructive actions, plain wording, and a check mark on selected chips.

## Android implementation notes

Reliable reminders are the whole product. These are the points that decide whether a dose is missed. Verify each against the current Android and Google Play documentation when you start; policies change.

| Topic | Recommendation |
|---|---|
| Stack | Kotlin, Jetpack Compose with Material 3, Room for data, DataStore for settings, Glance for the widget. The Material 3 Compose TimePicker matches the picker designed here. |
| Scheduling | Use AlarmManager exact alarms (setAlarmClock or setExactAndAllowWhileIdle), never WorkManager, for dose times. Schedule only the next dose per medicine and re-arm after each one. |
| Re-scheduling | Alarms are lost on reboot, app update, time change and time zone change. Re-arm from receivers for boot, package replaced, time set and time zone changed. |
| Exact alarm permission | From Android 14, SCHEDULE_EXACT_ALARM is not granted by default for new installs. Check canScheduleExactAlarms() and send users to the system page (this is the Permissions screen). USE_EXACT_ALARM is for alarm-clock and calendar apps, so a medicine app should not rely on it. |
| Notifications | POST_NOTIFICATIONS is a runtime permission on Android 13+. Use a high-importance channel for doses and a separate low-importance one for refill alerts, so quiet hours can mute only refills. |
| Notification buttons | A notification shows at most 3 action buttons. Use Take all, Snooze, Open. Per-medicine Take / Skip live on a full-screen reminder activity. From Android 14 the full-screen intent may need user approval, so always keep the notification itself as the fallback. |
| Battery | Do not request battery-optimisation exemption in code; Google Play restricts it. Link to the system battery settings from "Reminders not ringing?" and add per-brand steps (Samsung, Xiaomi, Huawei, Oppo) for auto-start and background limits. |
| Do Not Disturb | An app cannot override it without the user granting notification-policy access. Keep the "Fix" row as guidance. |
| Storage | Local database only. Mark rows with a schema version. Turn off or restrict Android Auto Backup for the database so health data does not go to Google Drive unexpectedly, unless you decide to use it. |
| Backup file | Export a single file (JSON or zip) with a version number through the system file picker, so the user chooses where to share it. Automatic weekly backup: a WorkManager periodic job that writes to the folder picked with the system folder picker (keep the persisted URI permission). Google Drive (optional): Google Sign-In with the Drive app-data scope, runs only with internet, and must be declared in Play Data safety. Offer an optional password. Check version on restore. |
| Language and RTL | Per-app language (system settings on Android 13+, AppCompat on older versions). Declare RTL support. Use string plurals; Arabic has dual and few forms ("مرتان"). Keep ص and م to the left of digits using a right-to-left isolate around each time. |
| Privacy | No accounts and no analytics. The network permission is needed only for the optional Google Drive backup. Feedback and rating buttons open the user's email app, so the app sends nothing itself. That makes the Play Data safety form simple. Complete the Health apps declaration in Play Console. |
| Devices to test | A low-RAM Android Go phone, Samsung, Xiaomi, an Android 14+ phone, a tablet, 360 dp width, font scale 200%, TalkBack on, Arabic locale. |

## Decisions needed from you

- **Online version.** Decided: kept as is and not used. It is moved to page 2 of the canvas, named "online version draft". Developers build only from page 1 (Offline version). Options A and C are separate explorations.
- **Dark theme in v1?** Decided: yes. Dark versions of Today, Notification, Medicine, Settings, Accessibility, Add medicine and History are drawn in English and Arabic. Open Accessibility, then Dark theme. Dark colours: background #0F1220, card #1B2033, text #EDEFFA, muted #AEB5D3, accent #B4AFFF.
- **24-hour time in v1?** Decided: designed. 24-hour versions of Today, Notification, Medicine, Add medicine (twice a day), History and the time picker are drawn in English and Arabic.
- **App lock and lock-screen privacy in v1?** Decided: not in v1. The lock and lock-screen privacy screens were removed.
- **Backup location.** Decided: automatic weekly saves to a folder the user picks, plus an option to save to Google Drive automatically (optional, needs internet). Screens drawn: Backup, choose folder, Google Drive connect, Drive on.
- **As needed medicines in v1?** Decided: yes, in v1. Drawn: dose log with "2 of 4 today" and the next allowed time, and the daily limit reached screen with an Undo.
- **Feedback and rating.** Decided: email is required and phone is optional. The app is offline, so it cannot send by itself: the button opens the user's email app with the message or rating ready, and the user presses Send there. The Privacy policy says so. Check Play Data safety: contact info is collected only when the user sends it.
- **Crash reports.** Decided: after a crash, the next launch shows a prompt. If the user accepts, the app opens their email app with the report ready (error text, app version, phone model; never medicines) and the user presses Send. Nothing is sent without the user's choice. Screen: Crash report prompt. The Privacy policy says so.

## Design tokens

- Colours: indigo #4338CA, indigo dark #2E2699, text #171B2E, muted #596079, background #F3F5FA, card #FFFFFF, border #C9CDE6, success #1B5E36 on #E3F4E8, warning #6B4200 on #FFF1D6, danger #B42318 on #FFE4E0.
- Dose blocks: Morning #E3F4E8, Noon #DDF0FF, Evening #F0E8FF, Night #E4E8F4.
- Type: Inter (English), Baloo Bhaijaan 2 (Arabic); 13 px minimum, 15 px body, 22 px titles, 36 px big time.
- Shape: cards 22 px radius, buttons fully rounded, 52 to 56 px primary button height, 48 px minimum touch target.
- Arabic time: digits then ص or م, with ص and م to the left of the digits.

## Handoff checklist

**Before development**

- [ ] Fix the 9 blockers and redraw the affected screens in English and Arabic
- [ ] All decisions answered (done)
- [ ] Recompute sample data on one date; fix the meal-offset label
- [ ] Online half archived on page 2 (done)
- [x] Write the rule tables: late dose, pause, hours-based schedule, minimum gap

**Design polish (parallel with development)**

- [ ] Raise all targets to 48 px; weekday labels to 13 px; fix the 3 contrast cases
- [ ] Draw the missing states (Finished tab, denied permissions, empty states, out of stock)
- [ ] Draw High contrast and Simple mode, or specify them as rules
- [x] Done: design tokens exported as pillbox-tokens.json and PillBoxTheme.kt (Compose). Light, dark and high-contrast colours, type scale 13 to 48, radius, spacing and sizes. Every text and background pair is at least 4.5:1 in all three modes
- [ ] Name screens in the canvas to match code (Today, Medicines, People, Settings)

**Test plan for the first build**

- [ ] Reminder fires on time after reboot, app update, time zone change and force close
- [ ] Fires with battery saver on, on Samsung and Xiaomi, and on Android 14+ with exact alarms denied
- [ ] Font scale 200%, TalkBack, 360 dp width, Arabic right-to-left
- [ ] Backup then restore on a second phone; wrong-file and newer-version restore
- [ ] Take, Skip and Undo update stock and history correctly
- [ ] Pause and resume; delete medicine; remove person

## Messages inventory (v100)

Every success, warning and error message now has a designed screen in English and Arabic (canvas rows 18–21).

**Success toasts (14):** stock updated, person saved, person added, settings saved, medicine resumed, medicine stopped, medicine restarted, dose skipped, report saved, widget saved, backup folder set, Drive connected, thanks after feedback/rating, time zone changed. Plus the full-screen confirmations: medicine added, medicine changes saved.

**Warnings and errors (dialogs, 7):** unsaved changes, duplicate medicine, no email app, Drive backup failed, backup folder unavailable, newer backup found, save failed.

**Form errors (3):** medicine name missing, tablets per box missing, invalid feedback email.

**Developer rule:** toasts stay 5 seconds, are announced by TalkBack as status, never hold the only copy of important information, and destructive actions offer Undo where possible.

## Size tests (automated, on the design screens)

**360 dp width, normal text:** all screens pass, nothing overflows or clips.

**360 dp width, 200% text:** about 79 screens overflow sideways. Same causes everywhere, so these are layout rules for the developer, not 79 separate fixes:

- Bottom navigation labels (Today, Medicines, People, Settings): allow two lines, never fixed width.
- Time chips and time rows (8:00 AM, AM/PM toggle, Morning/Evening labels): wrap under the label instead of sitting beside it.
- Medicine detail stat tiles (2/day, use): stack vertically at large text.
- Segmented controls (Largest, Missed day): scroll or wrap to two rows.
- Date picker buttons (Cancel, OK): stack full width.
- Medicine rows with name plus time: name on one line, time below.
- Heights are minimums (min-height), never fixed.

**TalkBack:** cannot be tested on a design. Test on a real phone with the first build: reading order, a label on every icon button, toasts announced as status.

## Large-text (200%) screens (v102)

40 screens have a large-text version in English and Arabic (canvas rows 22–26, titled "… · large text"). Regular screens are unchanged. They pass the overflow check at 360 dp width.

**Covered:** Today (and its variants), Medicine, Medicines list, Add medicine (all frequency variants), People, Settings, History, Dose taken, date pickers, edit medicine, meal times, quiet hours, refill alert, report export, review plan, widget settings, permissions, sound, paused and finished medicine.

**Not drawn (same rules apply):** dark, 24-hour and high-contrast variants (same layouts), lock-screen notifications (drawn by Android), toast-only screens (base screen plus a toast).

**Message screens:** each success, warning and error screen carries a "From: …" label naming the screen and action that triggers it.

- **3 times a day (changed by Mahmoud):** doses are every 8 hours around the clock (first dose, +8 h, +16 h). The canvas hint said "About every 6 hours while you are awake"; the app now says "Every 8 hours, around the clock". Existing medicines keep their saved times until the first dose is changed in Edit.
