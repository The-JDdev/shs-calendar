# SHS Calendar — Phase 1 Progress

STATUS: DONE — Phase 1 complete: `./gradlew assembleDebug testDebugUnitTest` green (app-debug.apk built; 31/31 unit tests pass). Ships: Gradle/AGP scaffold, adaptive icon, dark navy+cyan design system, Bengali/Hijri/Gregorian conversion engines (computed, no hardcoded tables), NOAA astronomy (sun/moon/twilight), Room data layer with RFC 5545 RRULE expansion, AlarmManager reminders with boot restore, and full 5-tab UI (dashboard, calendar, tools, event editor/agenda, settings). Phase 2+: richer month/year views & widgets, notifications polish, cloud/backup sync, further conversion tools, and per SPEC roadmap.

## Plan (appended before code, per workflow rules)

1. Inspect repo + environment (SPEC read; JDK 17, ANDROID_HOME, platform-34, build-tools 34.0.0 confirmed).
   - Criterion: env verified, no local.properties needed.
2. Gradle scaffold: Gradle 8.x wrapper + settings/build files, single `:app`, Kotlin, AGP 8.x,
   gradle.properties with SPEC memory flags.
   - Criterion: `./gradlew help` succeeds.
3. Adaptive app icon from design/app_icon_source.png (anydpi-v26 fg/bg + all densities + legacy).
   - Criterion: mipmaps generated, label "SHS Calendar".
4. Design system: colors.xml / themes.xml / dimens.xml / styles.xml (dark navy + cyan/teal).
   - Criterion: resources compile; premium dark Material theme applied.
5. Calendar engine (`com.shs.calendar.calendar`): GregorianEngine, BengaliEngine, HijriEngine,
   ConversionEngine, names/numerals helpers. No hardcoded tables.
   - Criterion: anchor 2026-09-25 = 10 Ashshin 1433 holds.
6. Astronomy engine (`com.shs.calendar.astronomy`): NOAA solar + twilight + golden hour, synodic moon phase.
   - Criterion: Dhaka 2026-09-25 sunrise/sunset in plausible window.
7. Data layer: Room + KSP entities/DAOs/db/repositories + RRULE expansion (RFC 5545 subset).
   - Criterion: compiles; repository API complete.
8. Reminders: AlarmManager scheduler, receivers, notification channels, BOOT_COMPLETED restore.
   - Criterion: compiles; reschedules from DB.
9. UI (XML): MainActivity dashboard, calendar card, Age Calculator, Date Converter, event editor,
   agenda list, bottom nav (5 tabs), placeholder screens, Settings.
   - Criterion: all screens compile and are wired.
10. Unit tests (src/test): Bengali anchors + leap years + boundaries + Hijri sanity + RRULE +
    astronomy window + year-3000 navigation.
    - Criterion: testDebugUnitTest green.
11. `./gradlew assembleDebug testDebugUnitTest` green → incremental commits → push → STATUS: DONE.
    - Criterion: build green, tests green, pushed to origin/main.

## Done
- Steps 1–9: scaffold, icon, design system, calendar + astronomy engines, Room data layer,
  reminders, all UI screens (commit 993f368; engines/data/reminders in 2c884fa).

## Next
- Step 10: unit tests (Bengali anchors/leap years/boundaries, Hijri sanity, RRULE,
  astronomy window, year-3000 navigation) → `./gradlew testDebugUnitTest` green.
- Step 11: `./gradlew assembleDebug testDebugUnitTest` green → push → STATUS: DONE.
