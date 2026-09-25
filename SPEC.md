# SHS Calendar — Implementation Specification (Authoritative)

Premium offline-first Android calendar: Gregorian + Bengali + Hijri.
Privacy-focused, open-source, deterministic. **NO AI FEATURES OF ANY KIND.**

> This document is the single source of truth for the implementation.
> Anything marked REMOVED must never be implemented.

## REMOVED (must NOT exist anywhere in the project)

- AI event creation, AI natural-language parsing, AI assistant/chatbot
- AI provider abstraction, AI API integration, AI-generated content
- Any analytics, tracking, advertising SDKs
- Any hardcoded credentials/tokens

## App identity

- Name: **SHS Calendar**; package `com.shs.calendar`
- Icon: `design/app_icon_source.png` (supplied) → adaptive icon (API 26+),
  foreground scaled ~66%, white/near-white background layer OK
- Feels premium: dark navy/midnight background (#0B1026-ish), large rounded
  cards (20–28dp radius), cyan/teal accents, purple/indigo secondary,
  occasional magenta/orange, white primary text, muted secondary text,
  soft gradients used sparingly, subtle shadows, glass-like translucency
  where appropriate. NOT a generic CRUD calendar.

## Three calendar systems (one engine)

1. **Gregorian** — via `java.time` (minSdk 26 has it). Large range support;
   navigation must be smooth through year 3000 and beyond.
2. **Bengali (Bangabda)** — implement the classic Bangladesh/West-Bengal rule:
   1 Boishakh = April 14 (fixed); months Boishakh..Bhadro = 31 days
   (5 months), Ashwin..Choitro = 30 days (7 months); Falgun = 31 in
   Gregorian leap years else 30. Year = Gregorian − 593 after Pohela
   Boishakh (−594 before). VALIDATION ANCHOR (from product owner):
   **2026-09-25 = 10 Ashshin 1433 (Friday)**. Month names: বৈশাখ, জ্যৈষ্ঠ,
   আষাঢ়, শ্রাবণ, ভাদ্র, আশ্বিন/আশ্বিন, কার্তিক, অগ্রহায়ণ, পৌষ, মাঘ,
   ফাল্গুন, চৈত্র. Seasons (ঋতু) mapping required. Do NOT invent the 2019
   reform rules; expose a `bengali_variant` setting hook instead.
3. **Hijri** — `java.time.chrono.HijrahDate` (Umm al-Qura) base +
   configurable manual adjustment (−3..+3 days, moon-sighting offset).
   Islamic events derived from Hijri month/day (Ramadan start, Eid al-Fitr,
   Eid al-Adha, Hajj, Arafah, Ashura, Islamic New Year, Mawlid, Laylat
   al-Qadr estimate — all computed, never fabricated to Gregorian).

Conversion: Gregorian ↔ Bengali ↔ Hijri, instant recalculation both ways.
Pure-Kotlin deterministic code in a dedicated `calendar` engine package,
fully unit-tested. No hardcoded date tables for conversions.

## Product scope (full spec summary — phases below)

- Date/time/location engine: GPS (optional!), manual location, saved places,
  timezone detection, multiple timezones, world clock, travel mode,
  12/24h, first-day-of-week, sunrise/sunset/solar noon/golden hour,
  civil/nautical/astronomical twilight, moonrise/moonset/moon phase.
  Calendar fully functional with location permission DENIED.
- Astronomy: NOAA solar algorithm (pure Kotlin). Moon phase: synodic/Meeus.
  Never hardcode example times — always compute or mark unavailable.
- Dashboard: header (SHS Calendar + EN/BN/AR indicator + widget button),
  hero Gregorian date + Bengali + Hijri lines, live clock card, weather card
  (Phase 3), location pill, sunrise/sunset cards, quick tools
  (Age calculator, Date converter, Tasbih, Dua collection, Widgets),
  additional tools (world clock, Qibla, moon phase, countdown, date
  difference, working days, timezone converter), daily inspiration card
  (local curated DB, copy/share/refresh, attributed quotes only — never
  fabricate citations), premium calendar card.
- Prayer dashboard: Fajr, Sunrise, Dhuhr, Asr, Sunset, Maghrib, Isha +
  Imsak, Midnight, Tahajjud; highlight current/upcoming; location-based;
  settings: calculation method (MWL, ISNA, Egypt, Makkah, Karachi, Dubai,
  Kuwait, Qatar, Singapore, Turkey), madhab (Shafi/Hanafi Asr),
  high-latitude rule, per-prayer minute adjustments.
- Islamic toolkit: prayer times, Qibla (sensors + GPS + manual), Tasbih
  counter, Dua collection (local), Hijri calendar view, Islamic events,
  Ramadan section (Suhoor/Iftar), moon phase. Nearby mosques = online
  optional, never required.
- Traditional Bangla calendar view: deep-green printed-calendar look —
  strong dark-green borders, white cells, red/pink holiday cells, yellow +
  cyan secondary strips, green Bengali numerals, blue Gregorian/Hijri
  numerals, Bengali weekday headers রবি সোম মঙ্গল বুধ বৃহঃ শুক্র শনি,
  header lines for today (Bengali), Bengali month-year, Hijri month-year.
  SAME engine as modern view.
- Event system: events/meetings/birthdays/anniversaries/reminders/tasks/
  notes/habits; fields: title, description, start, end, all-day, location,
  URL, category, color, participants, organizer, multiple reminders,
  recurrence (daily/weekly/monthly/yearly/custom RRULE, end-after-N /
  end-on-date / never), privacy flag, timezone. RFC 5545 RRULE semantics.
- Tasks (VTODO where practical): due date/time, priority, category,
  checklist, recurring, reminders, overdue, convert-to-event.
- Notes/journal (VJOURNAL where practical): rich text, tags, search;
  photos/attachments/voice = Phase 5 if resources permit.
- Global search: events, dates, people, notes, tasks, holidays, Islamic and
  Bengali events. Deterministic matching only (no AI). Bengali + Arabic
  text must match.
- Conflict detection: overlap/double-booking/reminder conflicts with
  duration display (e.g. "⚠️ 30-minute overlap").
- Holidays: Bangladesh, India, Saudi Arabia, USA, UK + Islamic + Bengali
  cultural. Local curated DB with year coverage; online update optional
  (Phase 3+), cached. Do not fabricate dates; store with sources.
- Weather (Phase 3): provider abstraction interface + Open-Meteo default
  (keyless), current + hourly + daily, offline cache of last success.
  Units configurable. Never required for core features.
- CalDAV/iCloud-style sync (Phase 4): CalDAV client (Nextcloud/self-hosted
  etc.), multiple accounts/calendars, two-way sync, offline queue, retries.
  Standard iCalendar formats everywhere (ICS import/export, CSV round-trip,
  backup/restore).
- Offline-first: everything except online-only providers works with zero
  network. Local DB + repository layer. Handle: no internet, GPS off,
  permission denied, provider unavailable, sync failure, invalid dates,
  unsupported ranges, timezone changes, reboot, DST, leap years,
  recurrence edge cases.
- Widgets (Phase 3): monthly calendar, today, Bengali date, Hijri date,
  upcoming events, agenda, prayer times, countdown, weather, clock, moon
  phase — visually matching the app.
- Settings: calendar (default system, first day, weekend, numerals, date
  format), Bengali (variant, numerals, month names), Hijri (method,
  adjustment, moon-sighting), prayer (method/madhab/adjust/notifications),
  location, weather, notifications, appearance (light/dark/AMOLED, accent,
  font size), privacy (app lock, PIN, biometric), sync, backup, language
  (English/বাংলা/العربية with RTL).
- Privacy: no ads/tracking/analytics, minimal permissions, local-first,
  export everything, delete everything, optional app lock.
- Navigation: 5 bottom tabs — Tools, Prayer, Calendar (center, emphasized
  cyan circular), Accounts, Settings.
- Accessibility: dynamic font sizes, content descriptions, high contrast,
  large targets, not color-only indicators, RTL, Bengali labels.

## Technical constraints (BINDING for this environment)

- Language: Kotlin, XML views. **NO Jetpack Compose.** minSdk 26,
  targetSdk 34, compileSdk 34, AGP 8.x, Gradle 8.x, JDK 17.
- Single Gradle module `:app` (modularize by package, not by Gradle module).
- Room via KSP (no kapt). No Firebase. No analytics. No advertising.
- gradle.properties MUST contain (sandbox RAM is 3.9GB):
  `org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=384m`
  `kotlin.daemon.jvmargs=-Xmx1024m`
  `org.gradle.workers.max=2`
  `org.gradle.parallel=false`
  `android.useAndroidX=true`
  `android.nonTransitiveRClass=true`
- Build targets here: `assembleDebug` + `testDebugUnitTest` only.
  No emulator/instrumented tests in this sandbox.
- Architecture: repository pattern, layered packages:
  `calendar` (engines), `astronomy`, `data` (Room), `domain` (models),
  `ui` (activities/fragments/adapters/viewmodels), `reminders`,
  `search`, `settings`, `sync` (interface now, impl Phase 4),
  `weather` (interface now, impl Phase 3).
- Every date engine must be deterministic pure Kotlin with unit tests
  (leap years, month/year boundaries, conversions both directions,
  year 3000 navigation, HijrahDate adjustments, Bengali anchors).

## Phases

- PHASE 1 (now): project scaffold, resources/adaptive icon, theme,
  calendar engine (3 systems + conversions), astronomy solar engine,
  Room schema for events/tasks/notes/settings, dashboard UI (hero date,
  live clock, cards, mini calendar with trilingual cells), month view +
  navigation, event editor + list, reminders (AlarmManager + receiver +
  boot restore), settings basics, unit tests, **build green + push**.
- PHASE 2: location layer (optional permission flow), prayer engine +
  UI, Qibla, Tasbih, Dua, traditional Bangla calendar view, travel mode,
  world clock.
- PHASE 3: weather provider + UI, holiday DB, widgets, tasks/notes UI,
  global search, conflict detection, ICS/CSV import/export + backup.
- PHASE 4: CalDAV sync, accounts UI, advanced sync.
- PHASE 5: accessibility, performance, hardening, docs, release prep.
