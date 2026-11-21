package com.vyorasound.app.network

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

interface YouTubeAuthProvider {
    val isSignedIn: StateFlow<Boolean>
    fun refreshSessionState(): Boolean
    fun buildAuthHeaders(origin: String = "https://music.youtube.com"): Map<String, String>
    fun signOut()
}

object NoOpYouTubeAuthProvider : YouTubeAuthProvider {
    private val _signedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = _signedIn.asStateFlow()
    override fun refreshSessionState(): Boolean = false
    override fun buildAuthHeaders(origin: String): Map<String, String> = emptyMap()
    override fun signOut() = Unit
}

class YouTubeSessionManager : YouTubeAuthProvider {

    private val _isSignedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    init {
        refreshSessionState()
    }

    override fun refreshSessionState(): Boolean {
        val cookieString = readCookieString("https://music.youtube.com")
        val hasSession = extractSapisid(cookieString) != null
        _isSignedIn.value = hasSession
        return hasSession
    }

    override fun buildAuthHeaders(origin: String): Map<String, String> {
        val cookieString = readCookieString(origin)
        if (cookieString.isBlank()) return emptyMap()
        val sapisid = extractSapisid(cookieString) ?: return emptyMap()
        val timestampSec = System.currentTimeMillis() / 1000L
        val hash = sha1Hex("$timestampSec $sapisid $origin")
        return mapOf(
            "Cookie" to cookieString,
            "Authorization" to "SAPISIDHASH ${timestampSec}_$hash",
            "X-Origin" to origin,
            "X-Goog-AuthUser" to "0"
        )
    }

    override fun signOut() {
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
        }
        _isSignedIn.value = false
    }

    private fun readCookieString(url: String): String {
        return runCatching {
            CookieManager.getInstance().getCookie(url).orEmpty()
        }.getOrDefault("")
    }

    internal fun extractSapisid(cookieString: String): String? {
        if (cookieString.isBlank()) return null
        val pairs = cookieString.split(";")
        var fallback3P: String? = null
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (value.isBlank()) continue
            if (name == "SAPISID") return value
            if (name == "__Secure-3PAPISID") fallback3P = value
        }
        return fallback3P
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
