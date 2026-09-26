# SHS Calendar

A premium, privacy-first calendar for Android with three complete calendar
systems — **Gregorian**, **Bengali** (Bangladesh-focused) and **Hijri** — plus
a full Islamic toolkit, astronomical calculations, weather, tasks, notes and
CalDAV sync. Deterministic and offline-first: **no AI features, no ads, no
trackers, no accounts required.**

## Highlights

- **Three calendars, one engine** — every date renders in all three systems
  simultaneously; conversions run in both directions from any system. Real
  algorithms (computed anchors, no hardcoded date tables), validated against
  known Bengali and Hijri anchors and exercised from year 1900 to beyond 3000.
- **Traditional Bangla calendar view** — a digital recreation of the classic
  Bengali wall calendar: deep-green frame, Bengali numerals, holiday tints,
  live trilingual header.
- **Astronomy** — sunrise, sunset, solar noon, golden hour, three twilights,
  moonrise, moonset and moon phase from NOAA solar/lunar formulations.
- **Prayer times** — 10 calculation methods, Asr madhab choice, four
  high-latitude rules, per-prayer minute offsets, Imsak/Midnight/Tahajjud/
  Suhoor/Iftar, next-prayer countdown.
- **Islamic toolkit** — Qibla compass with manual fallback, Tasbih counter,
  curated dua collection (correctly attributed, Quran + established hadith
  collections, with Bengali and English meanings).
- **Weather** — current/hourly/daily via Open-Meteo (no API key), cached for
  offline, honest online/cached/offline status, fully optional.
- **Events, tasks (VTODO), notes (VJOURNAL)** — reminders with boot-restore,
  RFC 5545 RRULE recurrence, priorities, tags, photos, voice notes.
- **Deterministic global search** — events, tasks, notes, dates, holidays,
  duas (no AI, pure matching).
- **Conflict detection** — overlap, double-booking and reminder-storm warnings.
- **Import / export** — ICS, CSV, full JSON backup/restore via the system file
  picker. Your data is never locked in.
- **CalDAV sync** — standard servers (Nextcloud, Radicale, Fastmail, …),
  multi-account, per-calendar colors, encrypted credentials, ETag conflict
  handling, offline queue.
- **11 home-screen widgets** — calendar, today, Bengali date, Hijri date,
  upcoming, agenda, prayer, countdown, weather, clock, moon phase.
- **Accessibility & localization** — WCAG AA contrast (asserted by tests),
  dynamic text sizes, 48dp touch targets, full Bengali and core Arabic
  translations, RTL layout support, dark/AMOLED/light themes, six accent
  colors.

## Privacy

- Local-first: everything works offline; the database lives on your device.
- Location is **optional** — deny permission and the calendar still works;
  pick any city manually or type coordinates.
- Only outbound network uses: weather (Open-Meteo), optional holiday-data
  refresh, CalDAV servers you explicitly configure. Nothing is uploaded to us
  (there is no "us" — no backend exists).
- Open source, no analytics, no ads, no tracking.

## Build from source

Requirements: JDK 17, Android SDK (platform 34, build-tools 34).

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # 320+ unit tests
```

The debug APK installs directly. Release builds expect a signing config you
provide locally (never commit keystores).

## Tech notes

- Kotlin, Room + KSP, Material 3, AlarmManager (no Play-services dependency),
  RemoteViews widgets, CalDAV over plain HTTP/XML — a deliberately small
  dependency footprint for low-end devices.
- Database migrations are registered for every schema bump; the sync schema
  and events index ship as migrations 1→8.
- Unit tests cover conversion anchors, leap years, Hijri method differences,
  year-3000 navigation, RRULE edge cases, DST boundaries, prayer windows,
  Qibla bearing, ICS/CSV roundtrips, conflict detection and contrast ratios.
