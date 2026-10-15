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
    // Only allow-listed, non-secret options can be backed up or sent remotely.
    fun publicSnapshot():org.json.JSONObject = org.json.JSONObject().apply {
        put("auto_start",autoStart);put("splash",splash);put("video_audio",videoAudio)
        put("offline_playback",offlinePlayback);put("enabled_modes",enabledModes)
        put("sync_minutes",syncMinutes);put("cache_mb",cacheMb);put("external_cache",externalCache)
        put("overlay_interval_sec",overlayIntervalSec);put("overlay_duration_sec",overlayDurationSec)
    }
    var overlayIntervalSec:Int
        get()=p.getInt("overlay_interval_sec",900).coerceIn(60,86400)
        set(v){require(v in 60..86400);p.edit().putInt("overlay_interval_sec",v).apply()}
    var overlayDurationSec:Int
        get()=p.getInt("overlay_duration_sec",12).coerceIn(3,120)
        set(v){require(v in 3..120);p.edit().putInt("overlay_duration_sec",v).apply()}
    var remoteRevision:Long
        get()=p.getLong("remote_revision",0L)
        private set(v)=p.edit().putLong("remote_revision",v).commit().let {require(it)}
    var localAdminPort:Int
        get()=p.getInt("lan_port",8787).coerceIn(1024,65535)
        set(v){require(v in 1024..65535);p.edit().putInt("lan_port",v).apply()}
    fun localAdminEnabled()=p.getBoolean("lan_admin_enabled",false)
    fun setLocalAdminEnabled(on:Boolean)=p.edit().putBoolean("lan_admin_enabled",on).commit()
    fun validateSettings(j:org.json.JSONObject):org.json.JSONObject {
        val keys=setOf("auto_start","splash","video_audio","offline_playback","enabled_modes", "sync_minutes","cache_mb","external_cache","overlay_interval_sec","overlay_duration_sec")
        val itr=j.keys();while(itr.hasNext())require(itr.next() in keys){"Parámetro no reconocido"}
        fun boolean(k:String):Boolean{require(j.has(k) && j.get(k) is Boolean){"Tipo de $k inválido"};return j.getBoolean(k)}
        fun integer(k:String,min:Int,max:Int):Int{require(j.has(k)&&j.get(k) is Int){"Tipo de $k inválido"};val v=j.getInt(k);require(v in min..max){"$k fuera de rango"};return v}
        val out=org.json.JSONObject()
        for(k in listOf("auto_start","splash","video_audio","offline_playback","external_cache"))out.put(k,boolean(k))
        out.put("enabled_modes",integer("enabled_modes",1,7))
        out.put("sync_minutes",integer("sync_minutes",1,60))
        out.put("cache_mb",integer("cache_mb",128,16384))
        out.put("overlay_interval_sec",integer("overlay_interval_sec",60,86400))
        out.put("overlay_duration_sec",integer("overlay_duration_sec",3,120))
        require(out.getInt("overlay_duration_sec")<out.getInt("overlay_interval_sec")){"Duración Overlay supera el intervalo"}
        return out
    }
    /** Single SharedPreferences.commit() makes config apply atomic at app level. Credentials/PIN stay untouched. */
    @Synchronized fun applySnapshot(snapshot:org.json.JSONObject,newRevision:Long?=null):Boolean {
        val v=validateSettings(snapshot)
        if(newRevision!=null)require(newRevision>=0)
        val e=p.edit()
        for(k in listOf("auto_start","splash","video_audio","offline_playback","external_cache"))e.putBoolean(k,v.getBoolean(k))
        for(k in listOf("enabled_modes","sync_minutes","cache_mb","overlay_interval_sec","overlay_duration_sec"))e.putInt(k,v.getInt(k))
        if(newRevision!=null)e.putLong("remote_revision",newRevision)
        return e.commit()
    }
    fun backupSnapshot(label:String):java.io.File {
        val dir=java.io.File(context.filesDir,"config-history").apply{mkdirs()}
        val f=java.io.File(dir,"settings-${System.currentTimeMillis()}-${java.security.SecureRandom().nextInt(99999)}.json")
        val data=org.json.JSONObject().put("format","digital-signage-settings").put("schema",1).put("label",label.take(80)).put("settings",publicSnapshot())
        f.writeText(data.toString(2),Charsets.UTF_8)
        dir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(10)?.forEach{it.delete()}
        return f
    }
    companion object { const val DEFAULT_PORTAL="https://www.2cam.services/digitaltv/portal/public/" }
}
