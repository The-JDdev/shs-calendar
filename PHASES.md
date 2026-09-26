# SHS Calendar — Phase 2–5 Milestone Tracker

STATUS: IN-PROGRESS — Phase 2-5 continuous session running (see PHASES_2_5_TASK.md)

## Phase 2 — Location, Prayer, Islamic toolkit, Traditional view
- [x] M1 Location layer (optional permission, manual fallback, travel mode)
- [x] M2 Prayer engine + UI (methods, madhab, high-lat, offsets, tests) — 48/48 green
- [x] M3 Qibla + Tasbih + Dua collection — 57/57 green (assembleDebug + testDebugUnitTest)
- [x] M4 Traditional Bangla calendar view + World clock — 74/74 green (assembleDebug + testDebugUnitTest)

## Phase 3 — Weather, Holidays, Widgets, Tasks/Notes, Search, Portability
- [x] M5 Weather (Open-Meteo provider, cache, offline) — committed 11e4178
- [ ] M6 Holiday DB (BD/IN/SA/US/UK + Bengali cultural + Islamic)
- [ ] M7 All 11 widgets
- [ ] M8 Tasks + Notes + Search + Conflicts + ICS/CSV/Backup

## Phase 4 — CalDAV
- [ ] M9 CalDAV sync + accounts UI + queue/ETag logic

## Phase 5 — Polish
- [ ] M10 Accessibility + theming + EN/BN/AR + RTL
- [ ] M11 Performance + hardening + tests (≥70 green)
- [ ] M12 Release prep v2.0.0 + README + final build + push

## Rules reminders
- No AI features. No hardcoded credentials. Real algorithms only.
- Location optional (deny → full function via manual location). Offline-first.
- Detached gradle builds only (setsid nohup → build_output.log, poll).
- Commit per milestone; push after green builds.
