package com.shs.calendar.sync

import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.io.IcsCodec

/**
 * ICS <-> [EventEntity] mapping for CalDAV VEVENT payloads.
 *
 * Pure and Android-free, so the round trip is unit-testable without a server.
 *
 * Two rules govern the mapping:
 *  - An ETag is never carried in the .ics body. It is a transport validator and
 *    lives in [EventEntity.davEtag] / the DAV response, so a body round trip
 *    cannot invent or clear one.
 *  - The dirty flag is a local transport concern, never part of the payload.
 */

/** Field name is DAV: the ICS field is SUMMARY. */
object EventMapper {

    /**
     * [EventEntity] -> VEVENT.
     *
     * The transport validator ([EventEntity.davEtag]) and the account id are
     * deliberately NOT parameters here: neither belongs in an .ics body, so
     * this mapping cannot smuggle a stale ETag onto the wire. The caller keeps
     * them and applies the If-Match header from [ETagPolicy] instead.
     */
    fun toComponent(event: EventEntity): IcsCodec.Component = IcsCodec.Component(
        kind = IcsCodec.Kind.VEVENT,
        uid = event.davUid ?: uidOrDerived(event),
        summary = event.title,
        description = event.description,
        location = event.location,
        url = event.url,
        categories = event.category.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        startUtcMillis = event.startUtcMillis,
        endUtcMillis = event.endUtcMillis,
        allDay = event.allDay,
        organizer = event.organizer,
        participants = event.participants.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        rrule = event.rrule,
        createdAtUtcMillis = event.createdAtUtcMillis,
        updatedAtUtcMillis = event.updatedAtUtcMillis
    )

    /**
     * A stable UID for an event that has never been pushed.
     *
     * Deterministic from immutable fields, so pushing the same row twice yields
     * the same UID and the server recognises the second PUT as an update
     * instead of creating a duplicate.
     */
    fun uidOrDerived(event: EventEntity): String =
        "shs-local-${event.id}-${event.startUtcMillis}"

    /**
     * VEVENT -> [EventEntity], for the pull half of a sync.
     *
     * [etag] is threaded in from the DAV response rather than parsed from the
     * body, and the row arrives dirty so the user's local copy is not
     * overwritten before the conflict check has run.
     */
    fun fromComponent(
        component: IcsCodec.Component,
        etag: String? = null,
        accountId: String? = null,
        calendarHref: String? = null
    ): EventEntity? {
        // A VEVENT with no start cannot be placed on a calendar; skipping it is
        // better than persisting an event that renders nowhere.
        val start = component.startUtcMillis ?: return null
        val end = component.endUtcMillis ?: start
        return EventEntity(
            title = component.summary,
            description = component.description,
            startUtcMillis = start,
            endUtcMillis = end,
            allDay = component.allDay,
            location = component.location,
            url = component.url,
            category = component.categories.joinToString(","),
            color = "",
            participants = component.participants.joinToString(","),
            organizer = component.organizer,
            reminders = "",
            rrule = component.rrule,
            privacy = "PUBLIC",
            notes = "",
            timezone = "UTC",
            createdAtUtcMillis = component.createdAtUtcMillis,
            updatedAtUtcMillis = component.updatedAtUtcMillis,
            davUid = component.uid,
            davEtag = etag,
            davAccountId = accountId,
            davCalendarHref = calendarHref,
            davDirty = false
        )
    }
}
