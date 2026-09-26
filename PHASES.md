# SHS Calendar — Phase 2–5 Milestone Tracker

STATUS: IN-PROGRESS — Phase 2-5 continuous session running (see PHASES_2_5_TASK.md)

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
- [ ] M9 CalDAV sync + accounts UI + queue/ETag logic — IN PROGRESS: sync engine + queue/ETag policy + VEVENT mapping + ICS/XML/ETag/queue unit tests (869c839); passwords in EncryptedSharedPreferences, per-request CalDAV auth, MIGRATION_5_6 (5117680); principal/calendar-home-set discovery, sync coordinator, DAV-aware EventDao queries (9be2252); sync accounts screen — list, per-account state, manual sync (deaf97b); Settings entry point (241f4cb); periodic/background sync on the app AlarmManager convention — SyncScheduler, SyncReceiver, boot re-arm; build green, 276 tests 0 failures. add-account form — CalDavAddAccountActivity, server/username/password fields, calendar picker populated by an explicit "Find calendars" PROPFIND, reachable from the accounts FAB (was a dead-end toast), per-calendar colour (colorHex on the entity, MIGRATION_6_7, v7), AccountInput as the pure validation/normalisation layer with 19 unit tests; build green, 295 tests 0 failures. Still missing: a WorkManager migration to lift goAsync's ~10s ceiling — BLOCKED offline, androidx.work is not in the Gradle cache and the build runs --offline, so it cannot be compiled or tested here; tracked, not half-built. Offline UI states are NOT missing: CalDavAccountsAdapter renders disabled, last-error, last-synced and never-synced from real account state (never guessed from connectivity), and the add-account form reports a failed PROPFIND in place instead of a spinner that never stops. Fixed a real discovery bug: DavRequest.absolute chopped the URL at its last '/' before isolating the scheme, so the "//" of "https://" was split and a root-relative href resolved to "https:/dav/..." — every server entered as a bare origin failed.

## Phase 5 — Polish
- [ ] M10 Accessibility + theming + EN/BN/AR + RTL
- [ ] M11 Performance + hardening + tests (≥70 green)
- [ ] M12 Release prep v2.0.0 + README + final build + push

## Rules reminders
- No AI features. No hardcoded credentials. Real algorithms only.
- Location optional (deny → full function via manual location). Offline-first.
- Detached gradle builds only (setsid nohup → build_output.log, poll).
- Commit per milestone; push after green builds.
