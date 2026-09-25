# SHS Calendar — Progress

STATUS: RUNNING — Phases 2–5 continuous session active. Read PHASES.md + PHASES_2_5_TASK.md.

## Phase 1 (historical)
- COMPLETE: scaffold, engines (Bengali/Hijri/Gregorian + NOAA astronomy), Room + RRULE,
  reminders, 5-tab UI, 31/31 tests, APK released at GitHub v1.0.0-phase1.

## Current engagement: Phases 2–5 (M1–M12)
- Kickoff: implement M1 first (location layer), then follow PHASES_2_5_TASK.md order.
- After every milestone: update PHASES.md checkboxes, git commit, green builds get pushed.

## Next
- M1 Location layer: LocationRepository + optional permission flow + manual city picker +
  timezone detection + travel mode.

- M1 complete: assembleDebug + testDebugUnitTest green (41/41 tests).

- M2 complete: assembleDebug + testDebugUnitTest green (48/48 tests).
  PrayerEngine (10 methods, madhab, high-lat rules, manual offsets, compact
  wrap-around fast path), PrayerSettings mapper, DB v1->v2 migration,
  Settings spinners + offsets dialog, PrayerActivity countdown/timetable,
  nav + quick-tools routing. Dhaka fajr window validated against an
  independent NOAA derivation (04:41 vs engine 04:41:34).
