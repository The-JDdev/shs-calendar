package com.shs.calendar.holidays

import java.time.LocalDate

/**
 * M6: seam for a future online holiday refresh.
 *
 * The M6 data set is fully offline and rule-derived, so nothing in the app
 * depends on this. It exists so that a later release can fetch corrections
 * (official moon-sighting announcements, one-off state holidays) without
 * touching the screens: a source reports an [UpdateResult], the app shows an
 * honest status pill, and the rules remain the fallback.
 *
 * Deliberately no implementation ships now: the brief forbids invented
 * religious content, and an unverified network feed is not a source we can
 * attribute.
 */
interface HolidayUpdateSource {

    /** Human-readable id shown in settings, e.g. "offline rules". */
    val id: String

    /** True when this source can currently be reached. */
    suspend fun isAvailable(): Boolean

    /**
     * Fetches any corrections valid for [from]..[to]. Returns
     * [UpdateResult.Unavailable] rather than throwing so callers can render
     * cached data with an honest status instead of crashing.
     */
    suspend fun fetch(from: LocalDate, to: LocalDate): UpdateResult
}

/** Outcome of a holiday refresh attempt. */
sealed class UpdateResult {

    /** No corrections: the computed rules are the data. */
    data class UpToDate(val checkedAt: LocalDate) : UpdateResult()

    /**
     * Corrections returned by a source. These are *overlays*: they are shown
     * alongside the computed rules and never replace them, so a bad or
     * partial feed cannot silently corrupt the calendar.
     */
    data class Updated(val holidays: List<Holiday>) : UpdateResult()

    /** Source unreachable; callers should keep serving cached/computed data. */
    data class Unavailable(val reason: String) : UpdateResult()
}

/**
 * M6 default: no online source. Holiday data always comes from the computed
 * rules, which is the honest, offline-first behaviour the brief requires.
 */
object OfflineRulesUpdateSource : HolidayUpdateSource {

    override val id: String = "offline-rules"

    override suspend fun isAvailable(): Boolean = false

    override suspend fun fetch(from: LocalDate, to: LocalDate): UpdateResult =
        UpdateResult.UpToDate(LocalDate.now())
}
