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

- M4 complete: assembleDebug + testDebugUnitTest green (74/74 tests).
  TraditionalHeader renders the spec's five-line Bangla header from the live
  Bengali/Hijri engines ("আজ ২৫ সেপ্টেম্বর ২০২৬ ইংরেজি / রোজ - শুক্রবার /
  আগস্ট ২০২৬ / শ্রাবণ — ভাদ্র ১৪৩৩ বাংলা / সফর — রবিউল আউয়াল ১৪৪৮ হিজরি")
  plus a TraditionalCalendarActivity (green frame, red holiday cells, yellow
  + cyan Bengali/Hijri sub-strips, Bangla weekday headers) over a new
  HolidayProvider. WorldClock (ZoneId entry, offset label, 12/24h + date
  formatters) + WorldClockActivity: seeded with the device zone, add via a
  zone-id dialog with invalid-zone toast, remove on row long-press, 60s
  tick that rebinds without rebuilding the list, and the 12/24h preference
  read from SettingsRepository.observe(). Both screens registered in
  AndroidManifest and wired into the dashboard quick tools.
  Known limitation: the Traditional header renders once and does not
  refresh at midnight (the world clock does tick).

- M3 complete: assembleDebug + testDebugUnitTest green (57/57 tests).
  QiblaEngine (great-circle bearing/distance to Kaaba 21.4225,39.8262, shortestDelta)
  + QiblaCompassView dial + QiblaActivity (magnetometer, calibration hint,
  manual bearing fallback, distance readout); TasbihActivity (5 presets,
  custom target, vibration tick, persisted count); DuaActivity
  (RecyclerView, 10-category spinner filter, copy/share per dua) over the
  32-dua DuaCollection. All three activities registered in AndroidManifest.

- M6 complete: assembleDebug + testDebugUnitTest green (94/94 tests).
  HolidayEngine resolves rules to dated Holiday records for 1900..3000,
  computing every date rather than tabulating: fixed Gregorian month/day
  rules, Islamic rules via HijriEngine, Bengali rules via BengaliEngine,
  and Easter via the anonymous Gregorian computus. Rule sources split into
  FixedHolidays (BD/IN/SA/US/UK fixed, nth-weekday and Easter rules),
  IslamicHolidays (11) and BengaliHolidays (14); HolidayDatabase adds
  country filter, month and range queries plus asHolidayProvider() for the
  existing HolidayProvider seam; HolidayUpdateSource is the offline
  up-to-date check. 13 HolidayEngineTest cases cover all four categories,
  the 1900/3000 boundaries, unsupported years returning empty rather than
  throwing, the single-country filter, and search over Bengali + English
  names with blank tolerance.
  Two engine fixes were needed: islamic() now passes its adjustment into
  HijriDate (it was accepted and silently dropped, so the moon-sighting
  shift never moved a lunar holiday), and the Hijri year probe is guarded
  because java.time's HijrahDate only spans ~1882..2174 CE -- outside that
  window Islamic rules now return null while fixed/Bengali/Easter rules
  still resolve, rather than throwing.
  Also fixed three pre-existing M5 defects that had left the whole test
  source set uncompilable, so M5 tests had never run: missing imports in
  WeatherRepository, a missing HourlyPoint.isRaining member, and a
  precipitation_probability assertion in OpenMeteoProviderTest that
  expected the array length (3) instead of the fixture value (20).
  Known limitation: M6 is the data layer only. There is no holiday screen
  yet -- TraditionalCalendarActivity consumes a HolidayProvider but is not
  wired to HolidayDatabase; holiday UI arrives with M7 widgets / M8.
