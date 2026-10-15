package io.github.stonecoldos.thortube.youtube

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.ThorTubeApp
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Optional YouTube sign-in, the way TV apps do it: the TV shows a short code, you enter it at
 * youtube.com/activate on your phone, and YouTube hands this app a token. Your password never
 * touches the app, and you can revoke it any time from your Google account (Security > third-party
 * access).
 *
 * This is not an official integration: it uses the same sign-in the YouTube TV app uses.
 * The token is stored encrypted with a key kept in Android's keystore, and is never backed up.
 */
object YouTubeAccount {

    private const val TAG = "ThorTube"
    private const val BASE = "https://www.youtube.com"
    private const val PREFS = "youtube_account"
    private const val KEY_ALIAS = "thortube_youtube_account"
    private val JSON = "application/json".toMediaType()

    // Public identifiers of the YouTube TV web app, read from its script when possible.
    private const val FALLBACK_CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val FALLBACK_CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"

    private lateinit var prefs: android.content.SharedPreferences

    private var accessToken: String? = null
    private var accessExpires = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val isSignedIn: Boolean get() = ::prefs.isInitialized && prefs.contains("refresh")

    /** What the TV shows while you sign in. */
    data class DeviceCode(val userCode: String, val verificationUrl: String, val deviceCode: String, val intervalSecs: Int, val expiresAt: Long)

    sealed interface Poll {
        data object Waiting : Poll
        data object SlowDown : Poll
        data object Done : Poll
        data class Failed(val reason: String) : Poll
    }

    private data class Client(val id: String, val secret: String)

    /** The TV app's client id and secret, from its current script (falls back to the known pair). */
    private suspend fun client(): Client = withContext(Dispatchers.IO) {
        runCatching {
            val tv = get("$BASE/tv")
            val script = Regex("""<script\s+id="base-js"\s+src="([^"]+)"[^>]*></script>""").find(tv)?.groupValues?.get(1)
                ?: error("no base-js on /tv")
            val js = get(if (script.startsWith("http")) script else BASE + script)
            val m = Regex("""clientId:"([^"]+)",[^"]*?:"([^"]+)"""").find(js) ?: error("no clientId in base.js")
            Client(m.groupValues[1], m.groupValues[2])
        }.onFailure { AppLog.w(TAG, "Couldn't read the TV app's client id, using the known one: ${it.message}") }
            .getOrDefault(Client(FALLBACK_CLIENT_ID, FALLBACK_CLIENT_SECRET))
    }

    private var pendingClient: Client? = null

    /** Step 1: ask YouTube for a code to show on the screen. */
    suspend fun startSignIn(): DeviceCode = withContext(Dispatchers.IO) {
        val c = client()
        pendingClient = c
        val body = JSONObject()
            .put("client_id", c.id)
            .put("scope", "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content")
            .put("device_id", UUID.randomUUID().toString())
            .put("device_model", "ytlr::")
        val r = post("$BASE/o/oauth2/device/code", body)
        DeviceCode(
            userCode = r.getString("user_code"),
            verificationUrl = r.optString("verification_url", "https://www.youtube.com/activate"),
            deviceCode = r.getString("device_code"),
            intervalSecs = r.optInt("interval", 5).coerceAtLeast(2),
            expiresAt = System.currentTimeMillis() + r.optLong("expires_in", 1800) * 1000,
        )
    }

    /** Step 2: ask whether you've entered the code yet. Call every [DeviceCode.intervalSecs]. */
    suspend fun poll(code: DeviceCode): Poll = withContext(Dispatchers.IO) {
        val c = pendingClient ?: return@withContext Poll.Failed("Start again")
        if (System.currentTimeMillis() > code.expiresAt) return@withContext Poll.Failed("The code expired")
        val body = JSONObject()
            .put("client_id", c.id)
            .put("client_secret", c.secret)
            .put("code", code.deviceCode)
            .put("grant_type", "http://oauth.net/grant_type/device/1.0")
        val r = runCatching { post("$BASE/o/oauth2/token", body, allowError = true) }
            .getOrElse { return@withContext Poll.Waiting }   // network blip: just try again
        when (val err = r.optString("error")) {
            "" -> {
                val refresh = r.optString("refresh_token")
                if (refresh.isEmpty()) return@withContext Poll.Failed("YouTube didn't send a token")
                save(c, refresh)
                accessToken = r.optString("access_token").ifEmpty { null }
                accessExpires = System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000
                AppLog.i(TAG, "Signed in to YouTube")
                Poll.Done
            }
            "authorization_pending" -> Poll.Waiting
            "slow_down" -> Poll.SlowDown
            "access_denied" -> Poll.Failed("Sign-in was declined")
            "expired_token" -> Poll.Failed("The code expired")
            else -> Poll.Failed(err)
        }
    }

    fun signOut() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
        accessToken = null
        accessExpires = 0
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
        AppLog.i(TAG, "Signed out of YouTube")
    }

    /** A fresh access token, refreshing it when it's about to run out. Null if not signed in. */
    suspend fun token(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!isSignedIn) return@withContext null
        val now = System.currentTimeMillis()
        accessToken?.let { if (!forceRefresh && now < accessExpires - 60_000) return@withContext it }
        val refresh = decrypt(prefs.getString("refresh", null)) ?: return@withContext null
        val body = JSONObject()
            .put("client_id", prefs.getString("client_id", FALLBACK_CLIENT_ID))
            .put("client_secret", decrypt(prefs.getString("client_secret", null)) ?: FALLBACK_CLIENT_SECRET)
            .put("refresh_token", refresh)
            .put("grant_type", "refresh_token")
        val r = post("$BASE/o/oauth2/token", body, allowError = true)
        val err = r.optString("error")
        if (err == "invalid_grant") {
            // Revoked from the Google account, or expired: nothing to do but sign in again.
            AppLog.w(TAG, "YouTube sign-in was revoked; signing out")
            signOut()
            return@withContext null
        }
        if (err.isNotEmpty()) error("Token refresh failed: $err")
        accessToken = r.getString("access_token")
        accessExpires = now + r.optLong("expires_in", 3600) * 1000
        accessToken
    }

    // ------------------------------------------------------------------ storage

    private fun save(c: Client, refresh: String) {
        prefs.edit()
            .putString("client_id", c.id)
            .putString("client_secret", encrypt(c.secret))
            .putString("refresh", encrypt(refresh))
            .apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String? = runCatching {
        val all = Base64.decode(stored ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
        String(cipher.doFinal(all, 12, all.size - 12))
    }.onFailure { AppLog.w(TAG, "Couldn't read the saved YouTube sign-in", it) }.getOrNull()

    // ------------------------------------------------------------------ http

    private fun get(url: String): String =
        ThorTubeApp.http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", YouTubeFeed.TV_USER_AGENT)
                .header("Referer", "$BASE/tv")
                .header("Accept-Language", "en-US")
                .build(),
        ).execute().use {
            check(it.isSuccessful) { "GET $url: ${it.code}" }
            it.body!!.string()
        }

    private fun post(url: String, body: JSONObject, allowError: Boolean = false): JSONObject =
        ThorTubeApp.http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", YouTubeFeed.TV_USER_AGENT)
                .post(body.toString().toRequestBody(JSON))
                .build(),
        ).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && !allowError) error("POST $url: ${response.code}")
            runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", "http_${response.code}") }
        }
}
