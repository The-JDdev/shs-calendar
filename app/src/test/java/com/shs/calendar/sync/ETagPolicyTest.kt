package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-table tests for [ETagPolicy].
 *
 * The remote sync path cannot be exercised in the sandbox, so the conditional
 * header logic is tested directly as a total function.
 */
class ETagPolicyTest {

    @Test
    fun `absent remote resource plans a create`() {
        val plan = ETagPolicy.plan(localEtag = null, remoteEtag = null)
        assertEquals(PushDecision.CREATE, plan.decision)
        assertEquals("If-None-Match", plan.header)
        assertEquals("*", plan.value)
        assertEquals(mapOf("If-None-Match" to "*"), plan.headers())
    }

    @Test
    fun `new local event pushes over an existing remote copy as a conflict`() {
        // We know a remote copy exists but have never seen it, so we cannot prove
        // it is unchanged: creating is impossible and clobbering is unsafe.
        val plan = ETagPolicy.plan(localEtag = null, remoteEtag = "\"abc\"")
        assertEquals(PushDecision.CONFLICT, plan.decision)
        assertTrue(plan.headers().isEmpty())
    }

    @Test
    fun `unchanged remote copy plans a conditional replace`() {
        val plan = ETagPolicy.plan(localEtag = "\"abc\"", remoteEtag = "\"abc\"")
        assertEquals(PushDecision.REPLACE, plan.decision)
        assertEquals("If-Match", plan.header)
        assertEquals("\"abc\"", plan.value)
    }

    @Test
    fun `remote changed under us is a conflict`() {
        val plan = ETagPolicy.plan(localEtag = "\"abc\"", remoteEtag = "\"def\"")
        assertEquals(PushDecision.CONFLICT, plan.decision)
        assertTrue(plan.headers().isEmpty())
    }

    @Test
    fun `blank etags are treated as absent not as a match`() {
        // Regression: "" == "" must not authorise an overwrite on a value that is
        // not a usable validator.
        assertEquals(PushDecision.CREATE, ETagPolicy.plan(null, "").decision)
        assertEquals(PushDecision.CONFLICT, ETagPolicy.plan("", "\"abc\"").decision)
        assertEquals(PushDecision.CONFLICT, ETagPolicy.plan("  ", "\"abc\"").decision)
    }

    @Test
    fun `weak etags compare verbatim including prefix and quotes`() {
        val plan = ETagPolicy.plan(localEtag = "W/\"abc\"", remoteEtag = "W/\"abc\"")
        assertEquals(PushDecision.REPLACE, plan.decision)
        // A strong validator must not satisfy a weak one.
        assertEquals(
            PushDecision.CONFLICT,
            ETagPolicy.plan(localEtag = "W/\"abc\"", remoteEtag = "\"abc\"").decision
        )
    }

    @Test
    fun `create wins over a stale local etag when the resource is gone`() {
        // The user deleted the event on the server; recreating it is correct.
        val plan = ETagPolicy.plan(localEtag = "\"abc\"", remoteEtag = null)
        assertEquals(PushDecision.CREATE, plan.decision)
        assertEquals("If-None-Match", plan.header)
    }
}
