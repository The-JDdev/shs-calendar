# SHS Calendar — Phase 2–5 Milestone Tracker

STATUS: ALL-PHASES-DONE — Phases 2-5 complete (M1-M12). v2.0.0 shipped with a
fatal startup crash; **v2.0.1 (versionCode 3) supersedes it and must be the
installed build**.

## v2.0.1 hotfix (post-M12, launch-crash fix)
- [x] Reproduced on the JVM Android framework (Robolectric 4.13): the v2.0.0
      APK crashed before any Activity — ShsCalendarApp.attachBaseContext read
      appearance prefs via Context.getApplicationContext(), which is NULL
      during Application.attach on a real device (NPE, process dead on launch).
- [x] Fixed 5 further view-type mismatches left by the M10 redesign that
      crashed MainActivity onCreate (month steppers, quick-tools row,
      inspiration strip, language chips) and EventsAgenda/DateConverter on
      first open; AMOLED/accent overlays re-parented so setTheme() keeps the
      Material3 base.
- [x] LaunchSmokeTest now boots the real Application+MainActivity in EN/BN/AR
      and AMOLED/accent variants — a build that cannot open can never ship.
      scripts/check_view_casts.py statically cross-checks all 122 findViewById
      casts against layout tags (0 problems).
- [x] 325 unit tests, 0 failures; assembleDebug green; released as v2.0.1.

## Phase 2 — Location, Prayer, Islamic toolkit, Traditional view
- [x] M1 Location layer (optional permission, manual fallback, travel mode)
- [x] M2 Prayer engine + UI (methods, madhab, high-lat, offsets, tests) — 48/48 green
- [x] M3 Qibla + Tasbih + Dua collection — 57/57 green (assembleDebug + testDebugUnitTest)
- [x] M4 Traditional Bangla calendar view + World clock — 74/74 green (assembleDebug + testDebugUnitTest)

## Phase 3 — Weather, Holidays, Widgets, Tasks/Notes, Search, Portability
- [x] M5 Weather (Open-Meteo provider, cache, offline) — committed 11e4178
- [x] M6 Holiday DB (BD/IN/SA/US/UK + Bengali cultural + Islamic) — data layer + tests, 94/94 green (UI lands in M7/M8)
- [x] M7 All 11 widgets — calendar/today/bengali/hijri/upcoming/agenda/prayer/countdown/weather/clock/moon; build green, 102/102 tests
- [x] M8 Tasks + Notes + Search + Conflicts + ICS/CSV/Backup — core logic 6f5d612; UI (Tasks/Notes/Search screens) + wiring complete, build green, 214/214 tests

## Phase 4 — CalDAV
- [x] M9 CalDAV sync + accounts UI + queue/ETag logic — DONE (295+ tests): engine, discovery, coordinator, ETag/queue, EncryptedSharedPreferences credentials, accounts screen + add-account form with calendar picker + per-calendar colour (v7), periodic/background sync + boot re-arm, honest offline/error states. Only gap: WorkManager async-lift BLOCKED offline (androidx.work not in Gradle cache) — goAsync 10s ceiling documented, AlarmManager convention kept.

## Phase 5 — Polish
- [x] M10 Accessibility + theming + EN/BN/AR + RTL — DONE: light/AMOLED palettes + 6 accents via SHSBaseActivity, WCAG AA asserted by 13-case ContrastTest, fixed 13 untinted icons + one sub-AA colour; FULL Bengali translation (all 294 strings + arrays), core Arabic translation, RTL audit clean (supportsRtl, start/end padding).
- [x] M11 Performance + hardening + tests (≥70 green) — DONE: 318 unit tests 0 failures; events table indexed on startUtcMillis (MIGRATION_7_8, db v8); bounded memo-cache (4096) in ConversionEngine for month/year grids; defensive error states for no-network/GPS-off/permission-denied/API-failure/sync-failure/invalid dates verified across screens.
- [x] M12 Release prep — DONE: versionName 2.0.0 / versionCode 2, README rewrite (features/build/privacy), security sweep clean (no tokens/keys/AI code), final assembleDebug + testDebugUnitTest green, pushed.

## Rules reminders
- No AI features. No hardcoded credentials. Real algorithms only.
- Location optional (deny → full function via manual location). Offline-first.
- Detached gradle builds only (setsid nohup → build_output.log, poll).
- Commit per milestone; push after green builds.
