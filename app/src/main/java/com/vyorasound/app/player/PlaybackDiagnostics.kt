package com.vyorasound.app.player

import android.net.Uri
import android.util.Log
import com.vyorasound.app.domain.model.PlayerResponseAudit
import com.vyorasound.app.domain.model.PlaybackStream

object PlaybackDiagnostics {
    const val TAG = "VYORA_PLAYER"

    @Volatile
    var logSink: ((level: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun d(message: String) {
        runCatching { Log.d(TAG, message) }
        logSink?.invoke("D", message, null)
    }

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }
        logSink?.invoke("I", message, null)
    }

    fun w(message: String, throwable: Throwable? = null) {
        runCatching { Log.w(TAG, message, throwable) }
        logSink?.invoke("W", message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        runCatching { Log.e(TAG, message, throwable) }
        logSink?.invoke("E", message, throwable)
    }

    /**
     * Redacts sensitive query parameters (sig, lsig, pot, ip, sparams, etc.)
     * while preserving host, path, and non-sensitive format diagnostics (itag, mime).
     */
    fun redactStreamUrl(url: String?): String {
        if (url.isNullOrBlank()) return "<empty>"
        return runCatching {
            val uri = Uri.parse(url)
            val host = uri.host ?: "<unknown-host>"
            val path = uri.path ?: ""
            val itag = uri.getQueryParameter("itag")
            val mime = uri.getQueryParameter("mime")
            buildString {
                append(uri.scheme ?: "https").append("://").append(host).append(path)
                val safeMeta = listOfNotNull(
                    itag?.let { "itag=$it" },
                    mime?.let { "mime=$it" }
                )
                if (safeMeta.isNotEmpty()) {
                    append("?").append(safeMeta.joinToString("&")).append("&<redacted>")
                } else if (!uri.query.isNullOrEmpty()) {
                    append("?<redacted>")
                }
            }
        }.getOrElse { "<invalid-url>" }
    }

    fun logPlayerAudit(
        songTitle: String?,
        videoId: String,
        clientName: String,
        playerHttpStatus: Int,
        audit: PlayerResponseAudit
    ) {
        val titleLabel = songTitle?.takeIf { it.isNotBlank() } ?: "<unknown-title>"
        i(
            "PlayerAudit | title=\"$titleLabel\" | videoId=$videoId | client=$clientName | " +
                "playerHttp=$playerHttpStatus | playabilityStatus=${audit.playabilityStatus ?: "UNKNOWN"} | " +
                "reason=${audit.playabilityReason ?: "-"} | hasStreamingData=${audit.hasStreamingData} | " +
                "expiresIn=${audit.expiresInSeconds}s | sabrOnly=${audit.hasServerAbrStreamingUrl && audit.rankedPlayableStreams.isEmpty()} | " +
                "hlsAvailable=${audit.hasHlsManifestUrl} | formatsCount=${audit.audioCandidates.size}"
        )

        if (audit.audioCandidates.isEmpty()) {
            w("PlayerAudit | title=\"$titleLabel\" | videoId=$videoId | availableAudioFormats=NONE")
        } else {
            val summary = audit.audioCandidates.joinToString(" ; ") { c ->
                "itag=${c.itag},mime=${c.mimeType},codecs=${c.codecs ?: "-"},bitrate=${c.bitrate}," +
                    "quality=${c.audioQuality ?: "-"},len=${c.contentLength ?: -1}," +
                    "url=${c.urlAvailability},drm=${c.isDrmProtected},defaultTrack=${c.isDefaultAudioTrack}"
            }
            d("PlayerAudit | videoId=$videoId | availableAudioFormats=[$summary]")
        }

        val selected = audit.rankedPlayableStreams.firstOrNull()
        if (selected != null) {
            i(
                "ResolverSelected | title=\"$titleLabel\" | videoId=$videoId | client=$clientName | " +
                    "itag=${selected.itag} | selectedMime=${selected.mimeType} | " +
                    "selectedBitrate=${selected.bitrate} | audioQuality=${selected.audioQuality ?: "-"} | " +
                    "isHls=${selected.isHls} | urlAvailable=true | stream=${redactStreamUrl(selected.streamUrl)}"
            )
        } else {
            w(
                "ResolverFailed | title=\"$titleLabel\" | videoId=$videoId | client=$clientName | " +
                    "urlAvailable=false | rootCause=${audit.rootCauseHint ?: "No playable audio format found"}"
            )
        }
    }

    fun logStreamProbe(
        songTitle: String?,
        videoId: String,
        stream: PlaybackStream,
        httpStatus: Int?,
        errorDetail: String? = null
    ) {
        val titleLabel = songTitle?.takeIf { it.isNotBlank() } ?: "<unknown-title>"
        if (httpStatus != null && httpStatus in 200..299) {
            i(
                "StreamProbeOK | title=\"$titleLabel\" | videoId=$videoId | client=${stream.clientName} | " +
                    "itag=${stream.itag} | mime=${stream.mimeType} | bitrate=${stream.bitrate} | " +
                    "httpStatus=$httpStatus | url=${redactStreamUrl(stream.streamUrl)}"
            )
        } else {
            w(
                "StreamProbeFAILED | title=\"$titleLabel\" | videoId=$videoId | client=${stream.clientName} | " +
                    "itag=${stream.itag} | mime=${stream.mimeType} | httpStatus=${httpStatus ?: -1} | " +
                    "rootCause=${errorDetail ?: "HTTP ${httpStatus ?: "error"}"} | " +
                    "url=${redactStreamUrl(stream.streamUrl)}"
            )
        }
    }
}
