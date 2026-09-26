# SHS Calendar — Progress

STATUS: ALL-PHASES-DONE — Phases 2-5 complete (M1-M12). v2.0.0 shipped.

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

- M8 UI complete: the Tasks/Notes/Search screens that were left as
  uncommitted work-in-progress are now wired and verified. TasksActivity
  (checklist with overdue marking, open/done filters, CSV export) and
  NotesActivity (pinned notes, tags, linked-entry chips) sit on the
  repositories committed in 6f5d612, with SearchActivity providing one
  entry point across events, tasks, notes and holidays. All three are
  registered in AndroidManifest and Tasks is reachable from the home
  quick-tools row.
  Verified, not assumed: pre-build static checks confirmed 3/3 referenced
  strings, 6/6 @layout references and 30/30 R.id references resolve.
  The build was then run detached with --rerun-tasks so nothing could be
  served from cache: 44/44 tasks executed, BUILD SUCCESSFUL, and the
  test task line was bare (no UP-TO-DATE / FROM-CACHE). Test counts were
  read from the XML reports, not the console: 18 report files, 214 tests,
  0 failures, 0 errors, 0 skipped.
  Note the first build log examined was 18 minutes stale and showed
  43 of 44 tasks UP-TO-DATE; it predated this work and was discarded
  rather than quoted. An earlier "build still running" signal was a false
  positive from pgrep matching the long-lived Gradle and Kotlin daemons.

- M9 PARTIAL — sync logic complete, transport and accounts UI NOT started.
  PHASES.md M9 stays UNCHECKED: it reads "CalDAV sync + accounts UI +
  queue/ETag logic" and only the third clause is done. A green build here
  reflects the pure logic being tested, not the whole feature.
  Delivered and unit-tested (the four testable pillars):
    - sync/DavXml.kt    — PROPFIND/multistatus parsing, per-propstat status
    - sync/ETagPolicy.kt — optimistic-concurrency If-Match/If-None-Match
    - sync/SyncQueue.kt  — offline push queue: per-UID coalescing and
      exponential backoff (60s doubling, 6h cap), clock-skew safe
    - sync/EventMapper.kt — VEVENT <-> EventEntity mapping
  Schema: MIGRATION_3_4 adds davUid/davEtag/davAccountId/davCalendarHref/
  davDirty to events, version 3 -> 4, plus indices on davUid and davDirty.
  davUid is nullable so a purely-local event stays null = "never synced".
  Still to do for M9: the HTTP/CalDAV transport, account storage, and the
  accounts UI. NOT testable in the sandbox (no reachable server), so those
  need a manual/instrumented pass.
  Three defects found and fixed while getting the suite green:
    1. DavXml.has() tested propStatus with startsWith("2"), but RFC 4918
       status values are full status lines ("HTTP/1.1 200 OK"), so has()
       was false for EVERY real response and the pull path would have
       treated all returned properties as absent. Now parses the numeric
       code via statusCode().
    2. The leaf-property regex required a closing tag, so a self-closing
       property such as <d:owner/> was dropped together with the HTTP
       status that proves the server was asked about it. Now matched
       separately.
    3. DavXmlTest looked up "ctag" while the fixture element is
       <cs:getctag>, whose local name is "getctag" — and the same test
       file's own comment at line 78 says it must be reachable as
       "getctag". Test typo; the parser was right.
  Verified, not assumed: detached build with --rerun-tasks, 44/44 tasks
  executed, BUILD SUCCESSFUL, bare test task line (no UP-TO-DATE /
  FROM-CACHE). Counts read from the XML reports: 22 report files,
  253 tests, 0 failures, 0 errors, 0 skipped — 214 before this work,
  so all 39 new tests genuinely ran.
  Note: the two DavXml failures were pre-existing uncommitted WIP, not a
  regression from this milestone.

## Next
(M9 session — 276 tests green, committed 9be2252)

Just landed, all compiling and unit-tested:
  - sync/DavDiscovery.kt — principal -> calendar-home-set -> calendar-list
    PROPFIND chain. Per-hop Depth 0/0/1: depth 1 on the server root would
    enumerate every principal on the host. resourcetype is read as two
    sibling elements, which is why it has no entry in DavXml's map.
  - sync/SyncCoordinator.kt — runs a pass over enabled accounts. Push runs
    before pull, because mergePulled refuses to overwrite a dirty row and so
    the local edit survives to be pushed. mergePulled is pure and returns a
    MergePlan; that is the half worth unit-testing, since insert-vs-update is
    what silently loses user data when wrong.
  - EventDao — dirtyForAccount / dirtyWithoutAccount / findByDavUid /
    findAllByDavUid / findByCalendarHref / deleteAllForAccount. The five
    dav* columns were written by the mapper and never read back until now.
  - test/sync/DavDiscoveryTest.kt — 9 tests.

Defect found and fixed this session:
  DavRequest.absolute() resolved an origin by substringBeforeLast('/') twice.
  For a bare-origin serverUrl like "https://dav.example.com/" the second cut
  landed inside the scheme's "//", giving "https:/" — so a root-relative
  href became "https:/dav/..." and the request went to a nonexistent host.
  This broke discovery for every server entered as a bare origin, i.e. the
  common case. Replaced with explicit origin()/base() helpers that skip past
  the scheme before looking for a path separator.

Lesson worth keeping: the failing test was written against the spec, not
against the implementation, and the implementation was wrong. When a new
test fails on fresh code, establish which side is wrong before "fixing" the
test.

STILL MISSING for M9: accounts UI, calendar list, manual + periodic sync
wiring, per-calendar color, offline UI states. Sync is not reachable from the
UI yet — SyncCoordinator has no caller.

## Next
(M9 — accounts screen committed deaf97b, build green, 276 tests)

Landed this round:
  - ui/caldav/CalDavAccountsActivity.kt + CalDavAccountsAdapter.kt
  - layout/activity_caldav_accounts.xml, layout/item_caldav_account.xml
  - 12 new strings (M9 block), manifest entry.
  SyncCoordinator now has its first caller, so a pass can actually be
  started from the app for the first time. The coordinator writes
  lastSyncMillis / lastErrorMessage back to the account rows and the screen
  re-renders from observeAll(), so the result outlives the process.

GAPS TO CLOSE, in order:
  1. NO ENTRY POINT. CalDavAccountsActivity is registered and working but
     nothing navigates to it; SettingsActivity has no row for it. It is
     currently reachable only by adb. This is the next task.
  2. Add-account form — needs a live PROPFIND to list calendars, so it
     cannot be validated in the sandbox. The FAB toasts that it is
     unavailable rather than opening a screen that cannot finish.
  3. Periodic/background sync (WorkManager), per-calendar colour, offline
     states.
  Note: 3 of the 4 remaining items are the "manual + periodic sync wiring"
  from the M9 line — manual sync IS done (deaf97b), periodic is not.

Three self-review catches, all found before compiling:
  1. R.color.shs_red does not exist in this app; the error colour is
     shs_danger. Found by grepping colors.xml, not assumed.
  2. The add-account FAB handler set syncNow.text, relabelling the Sync
     button to explain the FAB's absence — hiding a control that works to
     explain one that doesn't. Now Toast.
  3. Toast was used without its import; caught by scanning used-but-not-
     imported symbols.
  Build then compiled all six files (18 tasks executed, not UP-TO-DATE) with
  no new warnings. All four warnings are pre-existing files.

## Next — gap #1 CLOSED
CalDavAccountsActivity is now reachable. Added to activity_settings.xml as a
"Sync" section card (SHS.Card + SHS.Text.Accent header, matching the prayer
card's title/subtitle/trailing-Button row) and wired in SettingsActivity:
  findViewById<View>(R.id.settings_sync_accounts_button) -> startActivity(...)

Verified: detached gradlew testDebugUnitTest BUILD SUCCESSFUL; 25 suites,
276 tests, 0 failures, 0 errors, 0 skipped (read from the JUnit XML, not
from memory). The SettingsActivity elvis warning moved 187 -> 193, exactly
the 6 lines inserted, so the edit added no new warning.

Lesson worth keeping: I made a no-op edit here (old_str == new_str) and it
still reported "Replaced in ...". A successful tool return does not mean a
change happened. Check `git diff --stat` after any edit whose old_str and
new_str could coincide.

Remaining M9 work, in order:
  1. Add-account form — needs live PROPFIND to list calendars, so it cannot
     be validated in the sandbox. Currently the FAB toasts "not available".
  2. Periodic/background sync (WorkManager) — manual sync is done.
  3. Per-calendar colour, offline UI states.

## M9 periodic/background sync (uncommitted at time of writing)
Added, on the app's existing AlarmManager convention rather than WorkManager:
  - sync/SyncScheduler.kt   — arms one inexact RTC_WAKEUP alarm, fixed request
    code so re-scheduling updates rather than stacks, SharedPreferences-backed
    enabled switch. Deliberately not WorkManager: the app already schedules
    this way for reminders and restores on boot, and a second scheduling
    idiom for a job sharing the same DB is a second lifecycle to reason about.
  - sync/SyncReceiver.kt     — goAsync + CoroutineScope(SupervisorJob + IO),
    re-arms itself in a finally block so a failed night doesn't end background
    sync until the app is next opened.
  - AndroidManifest.xml      — receiver registered, exported=false, NO
    intent-filter (see below).
  - BootCompletedReceiver    — re-arms sync on boot/package-replace/time change,
    same trigger set as reminders.

TWO DEFECTS CAUGHT IN SELF-REVIEW, both before compiling:
  1. I gave SyncReceiver an <intent-filter> for BOOT_COMPLETED. Wrong twice
     over: unnecessary (AlarmManager delivers via explicit-component
     PendingIntent) and harmful — the boot broadcast would also be delivered
     to a receiver whose onReceive never inspects the action, causing a sync
     pass on every boot via a second unaccounted path. Removed the filter.
  2. Three unused coroutine imports in SyncScheduler carried over from
     ReminderScheduler, plus a missing SyncScheduler import in
     BootCompletedReceiver that would have broken the build.

KNOWN LIMITATION, deliberately documented not hidden: goAsync() allows ~10s
before the system may reclaim the process. A pass over several accounts on a
slow connection can exceed that. Safe to interrupt (mergePullled writes only
after each pull returns; the next alarm re-arms from finally), so an
interrupted pass costs at most one account and is retried. Migrating to
WorkManager would remove the ceiling — tracked, not silently omitted.

Verified: detached gradlew BUILD SUCCESSFUL; compileDebugKotlin actually
executed; SyncScheduler.class + SyncReceiver.class present; 25 suites,
276 tests, 0 failures read from fresh JUnit XML. The zero-warning result is
real but scoped — only changed files recompiled, and they emit no warnings;
the 4 pre-existing warnings belong to untouched files.

STILL MISSING: offline UI states, and a WorkManager migration to lift the
goAsync ~10s ceiling (documented above).

M9 — add-account form, per-calendar colour.

  The "unverifiable without a live PROPFIND" blocker was a design error, not
  a limit. Discovery already returns a list; the missing piece was a screen.
  CalDavAddAccountActivity: server, username, password, a calendar Spinner
  and a name field. The accounts FAB navigated to a dead-end toast before and
  now opens it.

  Discovery is behind an explicit "Find calendars" button, never on text
  change: a PROPFIND per keystroke would hammer the user's server and would
  send the password to a host the user had not finished typing. The password
  is read only at the moment of the request, never logged, and never
  persisted anywhere but SyncCredentialStore. Because the credential key is
  (serverUrl, username) and not a row id, the secret is written before the
  row and removed again if the insert fails, so a failed save cannot strand a
  credential no account refers to.

  Per-calendar colour: colorHex on SyncAccountEntity, migrated by
  MIGRATION_6_7 (v6 -> v7) using the server's own normalised #rrggbb or "".

  AccountInput holds the rules the form enforces about a typed address —
  scheme defaulting to https, plain http respected, path/port preserved,
  non-http schemes rejected before they reach the transport, username
  trimmed (it is half of a Basic "user:password" pair), label falling back
  to the calendar name and then the host. Kept out of the Activity on
  purpose: the project has no Robolectric, so pure logic is the only part
  that can be tested. 19 tests, including one that asserts the validated
  result has no field which could hold a password.

  The accounts list shows serverUrl verbatim, so it is stored as the origin
  rather than a pasted deep path — otherwise the accounts screen would
  display a collection URL as though it were a server. Sync reads the column
  only as the credential key; fetches go through calendarUrl, so the two
  strings cannot drift apart.

Verified: detached gradlew compileDebugKotlin + testDebugUnitTest; 26
suites, 295 tests, 0 failures, 0 errors, 0 skipped read from fresh JUnit
XML. AccountInputTest alone is 19 tests. CalDavAddAccountActivity.class and
AccountInput$Validated.class both present, so the new code really compiled
rather than being skipped as up-to-date.

M10 — appearance: theme, accent, text size, language.

  Four controls on Settings: theme (system/dark/amoled/light), accent
  (six), in-app text size (small/system/large/xl), and language (follow
  system, en, bn, ar). Each takes effect immediately — no restart prompt,
  no Save button — and is applied at the earliest hook that can carry it:
  font scale in Application.attachBaseContext, before the first
  inflation; night mode via setDefaultNightMode; locale via
  setApplicationLocales; accent as a ThemeOverlay applied before
  setContentView.

  AppearanceStore is SharedPreferences, deliberately not SettingsEntity.
  attachBaseContext runs long before the Room database is open, and
  font scale has to be in the Configuration before views inflate, so a
  synchronous in-memory read is the only thing that works there. Values
  are stored as enum *names*, never ordinals, so reordering an enum
  cannot silently reinterpret an existing user's choice, and an
  unrecognised name falls back rather than throwing.

  Accent.hex duplicates colors.xml as a string, which is a real
  duplication hazard, so ContrastTest pins all six to the palette: a
  palette edit that desyncs the enum fails the build rather than tinting
  the app with a colour nothing else uses.

  Two things this milestone found and fixed rather than shipped past.
  shs_teal_deep was #0D9488, 3.52:1 against shs_text_primary — and
  themes.xml pairs exactly those two as colorSecondaryContainer /
  colorOnSecondaryContainer, so secondary-container text was below WCAG
  AA with nothing in the build noticing. It is now #1B7F73 (4.56:1),
  the minimal darkening that clears AA while staying teal. The other
  five accent containers are computed, not chosen by eye: each is the
  darkest-adjacent step that clears 4.5:1, ranging 4.51:1 (magenta) to
  4.59:1 (indigo). ContrastTest asserts every pair, so a later palette
  tweak fails here instead of shipping.

  Accent is six ThemeOverlay style resources, not one computed style.
  setTheme() takes a style *resource id*; a theme attribute cannot be
  given a runtime string, so the "build the overlay from accent.hex"
  approach cannot work. CYAN maps to 0 — the base theme is already
  correct for it, and it must NOT map to android.R.style.ThemeOverlay,
  which is an overlay *parent* and would replace the SHS palette
  outright for the default user.

  Both gaps above are now CLOSED (M10 LIGHT + AMOLED):
    - Theme.LIGHT and Theme.AMOLED render a real light / true-black theme.
      values/colors.xml is the light palette and values-night/colors.xml
      holds the original deep-navy identity, both computed to clear WCAG
      AA 4.5:1. ContrastTest asserts every text pair in BOTH palettes;
      it previously read one hardcoded path, so the dark palette would
      have gone entirely unasserted while the test still reported green.
    - The accent and AMOLED choices now apply to all 20 Activities via
      SHSBaseActivity, which applies the theme before super.onCreate, so
      a newly added Activity inherits it and cannot forget to.
    - AMOLED needed a ThemeOverlay, not a values-night-v? qualifier: it is
      also a NIGHT configuration, so no resource qualifier can separate it.
      setTheme() takes one style id, so the accent is composed INSIDE each
      AMOLED overlay rather than applied as a second call.

  Two real defects found and fixed while doing this, both of which the
  previous test suite could not see:
    - shs_bengali_green measured 3.55:1 on the night background (below AA)
      and shipped that way, because the old ContrastTest never checked
      semantic colours. Now 5.16:1, and asserted.
    - 13 icon drawables hardcoded #FFFFFFFF with no tint - notably ic_back,
      used in 7 layouts - which would have been invisible on the light
      theme. They now use @color/shs_text_primary, which is itself
      config-qualified and so follows the palette.

  Language selection is by index into languageOptions(), not by matching
  the spinner's label text. The original label round-trip could mis-map a
  language if a translation collided or a label were reworded, so the tag
  is now read positionally and the label never participates in the
  decision. The labels are still strings.xml entries, so they remain
  translated — only the mapping became non-localised.

  The cost of that is a real, unguarded coupling: position N of
  R.array.appearance_languages must be languageOptions()[N]. Verified by
  hand (4 items, order system/en/bn/ar) but not by any test, because
  languageOptions() is a private Activity method and this project has no
  Robolectric. Reordering the array without reordering the list would make
  every language silently select the wrong one. If that coupling matters,
  the next step is to move the tag list into AppearanceOptions where a
  plain JUnit test can assert it against strings.xml — the same treatment
  Accent.hex already gets.

Verified: detached gradlew assembleDebug + testDebugUnitTest; 314 tests,
0 failures, 0 errors, read from fresh JUnit XML. ContrastTest is 9
tests, AppearanceOptionsTest 10. All five ThemeOverlay styles confirmed
present in themes.xml and every R.style reference in AccentOverlay.kt
resolved against them by grep.

The tests cover the decision logic and the resource/enum consistency,
not the wiring: the project has no Robolectric, so nothing here proves
the spinners, RadioGroups or overlay actually drive the UI. That
limitation is why the decision logic was kept in pure Kotlin
(AppearanceOptions) rather than in the Activity.
