package com.shs.calendar.sync

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal WebDAV transport over [HttpURLConnection].
 *
 * The project ships no OkHttp/Retrofit (see app/build.gradle.kts) and the
 * existing weather provider already uses HttpURLConnection, so CalDAV reuses
 * the same stack rather than adding a dependency. Request construction lives
 * in [DavRequest] and response parsing in [DavXml]; this class only moves
 * bytes, which is why it is the one part with no unit tests.
 */
class DavHttp(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) {

    data class Result(
        val status: Int,
        val body: String,
        val etag: String?,
        val ctag: String?
    )

    private fun open(url: String, method: String, headers: Map<String, String>, auth: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        // Applied here rather than at each verb so propfind/put/delete cannot
        // drift apart. It is a per-call parameter, not constructor state: one
        // DavHttp serves every account, and a captured header would send one
        // account's password to another account's server.
        auth?.let { conn.setRequestProperty("Authorization", it) }
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    /**
     * PROPFIND on a collection at depth 1. Returns the raw multistatus body;
     * [DavXml] turns it into responses so this layer stays protocol-agnostic.
     */
    fun propfind(url: String, auth: String? = null): Result =
        send(url, DavRequest.PROPFIND_METHOD, DavRequest.propfindHeaders(), DavRequest.PROPFIND_BODY, auth)

    /** Creates or replaces one VEVENT, guarded by If-Match when [localEtag] is known. */
    fun put(url: String, body: String, localEtag: String?, auth: String? = null): Result =
        send(url, "PUT", DavRequest.putHeaders(localEtag), body, auth)

    /** Removes one VEVENT, guarded by If-Match when [localEtag] is known. */
    fun delete(url: String, localEtag: String?, auth: String? = null): Result =
        send(url, "DELETE", DavRequest.deleteHeaders(localEtag), null, auth)

    private fun send(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        auth: String?
    ): Result {
        val conn = open(url, method, headers, auth)
        try {
            if (body != null) {
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val text = readBody(conn, status)
            return Result(
                status = status,
                body = text,
                etag = conn.getHeaderField("ETag"),
                ctag = conn.getHeaderField("CS:getctag") ?: conn.getHeaderField("getctag")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
    }
}
