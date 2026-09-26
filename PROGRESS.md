# SHS Calendar — Progress

STATUS: RUNNING — Phases 2–5 continuous session active. Read PHASES.md + PHASES_2_5_TASK.md.

## Phase 1 (historical)
- COMPLETE: scaffold, engines (Bengali/Hijri/Gregorian + NOAA astronomy), Room + RRULE,
  reminders, 5-tab UI, 31/31 tests, APK released at GitHub v1.0.0-phase1.

## Current engagement: Phases 2–5 (M1–M12)
- Kickoff: implement M1 first (location layer), then follow PHASES_2_5_TASK.md order.
- After every milestone: update PHASES.md checkboxes, git commit, green builds get pushed.

## Next
- M3 RESUME (coordinator note): dua/DuaCollection.kt has been PROVIDED by the coordinator
  (32 duas, categories, byCategory/search helpers). Remaining M3 work: DuaActivity + list/detail
  adapter wired to DuaCollection (copy/share per dua), register QiblaActivity/TasbihActivity/
  DuaActivity in AndroidManifest, Tasbih count persistence, QiblaEngine unit test (Dhaka bearing
  ~291-293 deg), then commit M3. Qibla engine/activity/view + Tasbih activity already exist in WIP.

- M1 complete: assembleDebug + testDebugUnitTest green (41/41 tests).

- M2 complete: assembleDebug + testDebugUnitTest green (48/48 tests).
  PrayerEngine (10 methods, madhab, high-lat rules, manual offsets, compact
  wrap-around fast path), PrayerSettings mapper, DB v1->v2 migration,
  Settings spinners + offsets dialog, PrayerActivity countdown/timetable,
  nav + quick-tools routing. Dhaka fajr window validated against an
  independent NOAA derivation (04:41 vs engine 04:41:34).

- M3 complete: assembleDebug + testDebugUnitTest green (57/57 tests).
  QiblaEngine (great-circle bearing/distance to Kaaba 21.4225,39.8262, shortestDelta)
  + QiblaCompassView dial + QiblaActivity (magnetometer, calibration hint,
  manual bearing fallback, distance readout); TasbihActivity (5 presets,
  custom target, vibration tick, persisted count); DuaActivity
  (RecyclerView, 10-category spinner filter, copy/share per dua) over the
  32-dua DuaCollection. All three activities registered in AndroidManifest.
