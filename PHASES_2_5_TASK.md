# PHASES 2–5 TASK BRIEF — SHS Calendar (one continuous session)

You are resuming the SHS Calendar repo. Phase 1 is DONE (build + 31 tests green, released
v1.0.0-phase1). Your job now: implement PHASES 2, 3, 4, 5 **fully, in this one continuous
engagement**, as incremental milestone groups M1–M12 below. SPEC.md remains the authoritative
spec — read it again for every milestone you touch. Work incrementally: keep everything that
works, no rewrites of Phase 1 code, commit after every milestone, keep PHASES.md and
PROGRESS.md current.

## NON-NEGOTIABLE RULES (from owner)

1. **No AI features anywhere.** No AI event creation, no NL parsing, no chatbot, no AI
   provider abstraction. Deterministic, privacy-first calendar only.
2. **No hardcoded credentials/tokens in source, resources, manifests, gradle files, commits,
   logs or docs.** Weather uses Open-Meteo (no API key). CalDAV credentials come only from
   user input at runtime, stored in EncryptedSharedPreferences.
3. **Real algorithms only.** No fabricated sample data presented as real: prayer times,
   qibla bearing, moon phase, calendar conversion must all be computed. Never hardcode date
   mappings.
4. **Location optional.** If permission denied or GPS off, the calendar + prayer screens must
   still work (manual location fallback with a city list + lat/lon).
5. **Offline-first.** Everything except weather refresh / holiday updates / CalDAV works
   offline with cached data and honest cache status.
6. **Sandbox build discipline:** gradle only detached:
   `setsid nohup ./gradlew <tasks> > build_output.log 2>&1 &` then poll
   (`sleep 60; tail -5 build_output.log`). Never foreground gradle >120s. If your turn ends
   while a build runs, record state in PROGRESS.md "## Next" and finish; the coordinator will
   resume you.
7. **Commit after every milestone** (git add -A && git commit), push after every green build,
   update PHASES.md checkbox states.

## MILESTONES

### PHASE 2 — Location, Prayer, Islamic toolkit, Traditional view

- **M1 Location layer.** `location` package: LocationRepository (FusedLocation via
  Play-services-free approach: LocationManager GPS/NETWORK passive; the app has no Play
  dependency), permission flow (ACCESS_COARSE/FINE optional, rationale dialog, graceful
  deny), manual location picker (searchable city list ~100+ major cities w/ lat/lon/tz +
  free-text lat/lon entry), IANA timezone detection, travel mode toggle (auto-use device
  timezone + prompt to recalc prayer/sun times), stored in DataStore/Room settings.
  ACCEPT: deny permission → dashboard shows manual location pill, everything still works.
- **M2 Prayer engine + UI.** `prayer` package: full astronomical prayer calculation
  (astronomical sun elevation based) with methods: MWL, ISNA, Egypt, Makkah (Umm al-Qura),
  Karachi, Dubai, Kuwait, Qatar, Singapore, Turkey; Asr madhab Standard/Hanafi; high-latitude
  adjustments (None, AngleBased, OneSeventh, MidnightSun); manual minute offsets per prayer;
  times: Imsak, Fajr, Sunrise, Dhuhr, Asr, Sunset, Maghrib, Isha, Midnight, Tahajjud (last
  third), Suhoor end / Iftar. UI: Prayer tab screen — big next-prayer countdown card, all
  times list, current prayer highlighted, method/madhab/offsets settings wired. Unit tests:
  Dhaka 2026-09-26 plausible window (Fajr ~04:45–05:15, Maghrib ~17:45–18:15), Makkah anchors,
  Hanafi Asr later than Standard, high-latitude no-crash at 65°N in June/December.
- **M3 Qibla + Tasbih + Dua.** Qibla: great-circle bearing to Kaaba (21.4225, 39.8262) from
  location, compass (magnetometer) UI with degree dial, manual direction fallback when no
  sensor (show bearing number), calibration hint. Tasbih: counter with preset dhikr
  (SubhanAllah/Alhamdulillah/AllahuAkbar/…), custom targets, vibration tick, persisted count.
  Dua: local curated collection (≥40 duas across categories: morning/evening, food, travel,
  sleep, forgiveness) with Arabic + transliteration + Bengali + English, copy/share; **only
  well-known, correctly attributed texts; no invented religious content**.
- **M4 Traditional Bangla calendar view.** New screen replicating a traditional Bengali
  printed calendar as premium digital: deep-green outer background, thick green border, white
  cells, red/pink background for holidays, yellow + cyan sub-strips for Bengali/Hijri dates,
  green Bengali numerals for Bengali date, blue Gregorian + Hijri numbers, red holiday marks,
  Bengali weekday headers রবি সোম মঙ্গল বুধ বৃহস্পতি শুক্র শনি. Header format exactly:
  "আজ ২৫ সেপ্টেম্বর ২০২৬ ইংরেজি / রোজ - শুক্রবার / আগস্ট ২০২৬ / শ্রাবণ — ভাদ্র ১৪৩৩ বাংলা /
  সফর — রবিউল আউয়াল ১৪৪৮ হিজরি" (computed live from engines — NEVER copy this example
  verbatim). Shares the same date/event engine as modern views. Entry from Tools + calendar
  screen toggle. World clock screen: multi-timezone list (add/remove, 12/24h).

### PHASE 3 — Weather, Holidays, Widgets, Tasks/Notes, Search, Data portability

- **M5 Weather.** `weather` package: WeatherProvider interface + OpenMeteoProvider
  (open-meteo.com, no key) + CachedProvider; current + hourly (24h) + daily (7d) with
  feels-like, humidity, wind, rain chance, UV, visibility; dashboard weather card (temp,
  condition, online/cache-status pill); manual refresh; offline serves last good response
  labeled "cached"; settings: enable/disable, units, provider selectable. Parsing via
  org.json (no extra deps). Failures never crash the app.
- **M6 Holidays.** `holidays` package: deterministic datasets for Bangladesh, India, Saudi
  Arabia, US, UK (fixed-date national days + computed Islamic dates via HijriEngine +
  Bengali cultural dates via BengaliEngine: Pohela Boishakh, Nabanna, Ekushey February etc.),
  categorization national/islamic/bengali/regional, year range 1900–3000 (computed, not
  stored per-year tables where astronomical/Islamic), calendar cell badges + holiday list
  screen + global search integration. Cache/update hook for future online refresh.
- **M7 Widgets (all 11).** RemoteViews AppWidgetProviders: (1) monthly calendar widget,
  (2) today card, (3) Bengali date, (4) Hijri date, (5) upcoming events, (6) agenda list,
  (7) prayer times, (8) countdown, (9) weather, (10) clock, (11) moon phase. Shared
  dark-navy/cyan design, click-through into app sections, update via AlarmManager/periodic +
  on data change broadcasts, resize-friendly previews, widget config labels. Widget update
  logic unit-tested where possible.
- **M8 Tasks, Notes, Search, Conflicts, Import/Export.** Tasks screen on VTODO model:
  priority, due, subchecklist, recurrence reuse of RRULE, overdue styling, convert-to-event.
  Notes/Journal (VJOURNAL): rich-ish text (styled spans), tags, photo attach (content URI),
  voice note (app-private file), search. Global search screen (deterministic, substring +
  normalized Bengali diacritics-insensitive): events, tasks, notes, dates ("25 December
  2026"), holidays, duas. Conflict detection on event save/list: overlap, double-booking,
  reminder storm (≥3 reminders same minute) with "⚠️ 30-minute overlap"-style messages.
  Import/export: ICS import (VEVENT+VTODO+VJOURNAL, RRULE) + export all; CSV import/export
  events; full backup/restore (JSON) to user-chosen file via SAF. No data locked in private
  formats.

### PHASE 4 — CalDAV

- **M9 CalDAV sync + accounts.** `sync` package: account manager UI (add server URL +
  username + password → stored EncryptedSharedPreferences), discover principal/current-user-
  via PROPFIND, calendar-home-set, list calendars; sync: REPORT calendar-query → parse
  VEVENT → upsert by UID; push: PUT with If-Match/If-None-Match ETag handling; sync token /
  ETag conflict policy (last-writer-wins with conflict copy), retry queue + offline queue,
  background periodic sync + manual sync + per-account enable; multi-account, per-calendar
  color. Plain OkHttp/HttpURLConnection + minimal XML pull-parsing, no huge deps. Works with
  standard servers (Nextcloud, Radicale, Fastmail/Google CalDAV endpoints). Remote sync NOT
  testable in sandbox — must be unit-testable: ICS↔model roundtrip, XML parse of a captured
  multistatus sample, ETag decision logic, queue behavior. Honest UI states when offline.

### PHASE 5 — Polish

- **M10 Accessibility + theming + language.** contentDescription everywhere, touch targets
  ≥48dp, dynamic font scale respects user setting (Small/Normal/Large/XL), contrast-checked
  palette, AMOLED theme, dynamic color (Material You when available, API31+), accent picker,
  language switch EN/বাংলা/العربية with full RTL mirroring, Bengali strings for all major
  screens (values-bn), Arabic (values-ar core screens).
- **M11 Performance + hardening + tests.** Lazy month-grid recycling, widget query limits,
  DB indices verify, avoid recomputing thousands of dates (cache month conversions),
  error states for: no network, GPS off, permission denied, API failure, sync failure,
  invalid dates (Feb 30, Hijri adjust overflow), DST transitions, leap-year Feb 29
  recurrences, process restart persistence. New unit tests: prayer anchors (≥4 cities),
  qibla bearing (Dhaka ~291–293°), holiday dates 2026/2030, ICS roundtrip, RRULE edge cases,
  conflict detector, CSV roundtrip, search normalization, year-3000 on new engines. Target:
  total test suite ≥70 tests green.
- **M12 Release prep.** versionName 2.0.0/versionCode 2, README rewrite (features, build
  from source, permissions rationale, privacy), PROGRESS.md final, SECURITY note, final
  `assembleDebug testDebugUnitTest` green, push. Set PHASES.md ALL-DONE.

## DEFINITION OF DONE (whole engagement)

- All milestones M1–M12 checked in PHASES.md; STATUS: ALL-PHASES-DONE in PROGRESS.md.
- `./gradlew assembleDebug testDebugUnitTest` green; suite ≥70 tests.
- app-release-style final debug APK copied to /home/z/my-project/download/SHS-Calendar-v2.0.0-debug.apk
- Security sweep: grep repo for token/key patterns — clean; no AI code; git log clean.
- All work pushed to origin main.
