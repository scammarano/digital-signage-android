package com.digitalsignage.tv

import android.content.Context
import android.os.Environment
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Non-secret user settings. Device token is stored separately in AndroidKeyStore. */
class SettingsStore(private val context: Context) {
    private val p = context.getSharedPreferences("signage_settings_v1", Context.MODE_PRIVATE)
    var portalUrl: String
        get() = p.getString("portal", DEFAULT_PORTAL) ?: DEFAULT_PORTAL
        set(v) { require(v.startsWith("https://") && v.endsWith("/")); p.edit().putString("portal", v).apply() }
    var autoStart: Boolean
        get() = p.getBoolean("auto_start", true)
        set(v) = p.edit().putBoolean("auto_start",v).apply()
    var splash: Boolean
        get() = p.getBoolean("splash", true)
        set(v) = p.edit().putBoolean("splash",v).apply()
    var videoAudio: Boolean
        get() = p.getBoolean("video_audio",true)
        set(v) = p.edit().putBoolean("video_audio",v).apply()
    var offlinePlayback: Boolean
        get() = p.getBoolean("offline", true)
        set(v) = p.edit().putBoolean("offline",v).apply()
    var enabledModes: Int
        get() = p.getInt("enabled_modes",7) and 7
        set(v) = p.edit().putInt("enabled_modes",v and 7).apply()
    var syncMinutes: Int
        get() = p.getInt("sync_minutes",5).coerceIn(1,60)
        set(v) = p.edit().putInt("sync_minutes",v.coerceIn(1,60)).apply()
    var cacheMb: Int
        get() = p.getInt("cache_mb",1024).coerceIn(128,16384)
        set(v) = p.edit().putInt("cache_mb",v.coerceIn(128,16384)).apply()
    var externalCache: Boolean
        get() = p.getBoolean("external_cache",false)
        set(v) = p.edit().putBoolean("external_cache",v).apply()
    var overlayPermissionAcknowledged: Boolean
        get() = p.getBoolean("overlay_ack",false)
        set(v) = p.edit().putBoolean("overlay_ack",v).apply()
    fun cacheDirectory(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val parent = if(externalCache && external!=null) external else context.filesDir
        return File(parent,"signage-cache-v1").also{ if(!it.exists()) it.mkdirs() }
    }
    fun hasPin() = p.contains("pin_hash")
    fun verifyPin(input:String): Boolean {
        if(!hasPin()) return false
        val salt=Base64.decode(p.getString("pin_salt", "")!!,Base64.NO_WRAP)
        val actual=pinHash(input,salt)
        val expected=Base64.decode(p.getString("pin_hash", "")!!,Base64.NO_WRAP)
        return java.security.MessageDigest.isEqual(actual,expected)
    }
    fun setPin(pin:String){ require(pin.length>=6 && pin.all{it.isDigit()})
        val salt=ByteArray(16); SecureRandom().nextBytes(salt)
        p.edit().putString("pin_salt",Base64.encodeToString(salt,Base64.NO_WRAP))
         .putString("pin_hash",Base64.encodeToString(pinHash(pin,salt),Base64.NO_WRAP)).apply()
    }
    private fun pinHash(pin:String,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,120000,256)
        return try{ SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally{spec.clearPassword()}
    }
    companion object { const val DEFAULT_PORTAL="https://www.2cam.services/digitaltv/portal/public/" }
}
