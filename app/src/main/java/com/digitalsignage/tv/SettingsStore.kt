package com.digitalsignage.tv

import android.content.Context
import android.os.Environment
import android.os.Build
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
    /** Temporary safety lock: every network/playback stage requires a button press. */
    /** Upgrade from the manual diagnostic build: return to automated boot exactly once. */
    fun prepareProductionStartup() {
        if(!p.getBoolean("automatic_boot_migrated_048",false)){
            check(p.edit().putBoolean("diagnostic_manual_start",false)
                .putBoolean("automatic_boot_migrated_048",true).commit())
        }
    }
    var diagnosticMode:Boolean
        get()=p.getBoolean("diagnostic_manual_start",false)
        set(value){p.edit().putBoolean("diagnostic_manual_start",value).commit()}
    // One common schema is used by the portal, native settings and LAN panel.
    var signageShowHud:Boolean
        get()=p.getBoolean("signage_show_hud",true)
        set(v)=p.edit().putBoolean("signage_show_hud",v).apply()
    var signageHudSeconds:Int
        get()=p.getInt("signage_hud_seconds",10).coerceIn(0,60)
        set(v){require(v in 0..60);p.edit().putInt("signage_hud_seconds",v).apply()}
    var overlayLayout:String
        get()=p.getString("overlay_layout","bottom")?.takeIf{it in overlayLayouts}?:"bottom"
        set(v){require(v in overlayLayouts);p.edit().putString("overlay_layout",v).apply()}
    var overlayWidthPct:Int
        get()=p.getInt("overlay_width_pct",30).coerceIn(15,100)
        set(v){require(v in 15..100);p.edit().putInt("overlay_width_pct",v).apply()}
    var overlayHeightPct:Int
        get()=p.getInt("overlay_height_pct",25).coerceIn(10,100)
        set(v){require(v in 10..100);p.edit().putInt("overlay_height_pct",v).apply()}
    var overlayOpacity:Int
        get()=p.getInt("overlay_opacity",90).coerceIn(40,100)
        set(v){require(v in 40..100);p.edit().putInt("overlay_opacity",v).apply()}
    var smartApp:String
        get()=p.getString("smart_app","queue")?.takeIf{it=="queue"||it=="scoreboard"}?:"queue"
        set(v){require(v=="queue"||v=="scoreboard");p.edit().putString("smart_app",v).apply()}
    var smartHeaderPct:Int
        get()=p.getInt("smart_header_pct",40).coerceIn(25,70)
        set(v){require(v in 25..70);p.edit().putInt("smart_header_pct",v).apply()}
    var smartPollSec:Int
        get()=p.getInt("smart_poll_sec",10).coerceIn(5,120)
        set(v){require(v in 5..120);p.edit().putInt("smart_poll_sec",v).apply()}
    var smartSound:Boolean
        get()=p.getBoolean("smart_sound",false)
        set(v)=p.edit().putBoolean("smart_sound",v).apply()
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
    var primaryMode:Int
        get()=p.getInt("primary_mode",1).coerceIn(1,3)
        set(v){require(v in 1..3);p.edit().putInt("primary_mode",v).apply()}
    var updatePolicy:Int
        get()=p.getInt("update_policy",2).coerceIn(0,2)
        set(v){require(v in 0..2);p.edit().putInt("update_policy",v).apply()}
    var updateChannel:String
        get()=p.getString("update_channel","debug")?.takeIf{it in listOf("stable","beta","debug")} ?: "debug"
        set(v){require(v in listOf("stable","beta","debug"));p.edit().putString("update_channel",v).apply()}
    var syncMinutes: Int
        get() = p.getInt("sync_minutes",1).coerceIn(1,60)
        set(v) = p.edit().putInt("sync_minutes",v.coerceIn(1,60)).apply()
    var lastControlVersion:Long
        get()=p.getLong("control_applied",0L)
        set(value) {p.edit().putLong("control_applied",value).commit()}
    var manualMode:Int
        get()=p.getInt("manual_mode",0).coerceIn(0,3)
        set(value){require(value in 0..3);p.edit().putInt("manual_mode",value).commit()}
    var cacheMb: Int
        get() = p.getInt("cache_mb",if(Build.VERSION.SDK_INT <= 20) 128 else 256).coerceIn(128,16384)
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
        return try{ SecretKeyFactory.getInstance(if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally{spec.clearPassword()}
    }
    // Only allow-listed, non-secret options can be backed up or sent remotely.
    fun publicSnapshot():org.json.JSONObject = org.json.JSONObject().apply {
        put("signage_show_hud",signageShowHud);put("signage_hud_seconds",signageHudSeconds)
        put("overlay_layout",overlayLayout);put("overlay_width_pct",overlayWidthPct)
        put("overlay_height_pct",overlayHeightPct);put("overlay_opacity",overlayOpacity)
        put("smart_app",smartApp);put("smart_header_pct",smartHeaderPct)
        put("smart_poll_sec",smartPollSec);put("smart_sound",smartSound)
        put("auto_start",autoStart);put("splash",splash);put("video_audio",videoAudio)
        put("offline_playback",offlinePlayback);put("enabled_modes",enabledModes)
        put("primary_mode",primaryMode);put("update_policy",updatePolicy);put("update_channel",updateChannel)
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
        val allowed=setOf("auto_start","splash","video_audio","offline_playback",
            "enabled_modes","primary_mode","sync_minutes","cache_mb","external_cache",
            "update_policy","update_channel","overlay_interval_sec","overlay_duration_sec",
            "signage_show_hud","signage_hud_seconds","overlay_layout","overlay_width_pct",
            "overlay_height_pct","overlay_opacity","smart_app","smart_header_pct",
            "smart_poll_sec","smart_sound")
        val cursor=j.keys()
        while(cursor.hasNext())require(cursor.next() in allowed){"Parámetro desconocido"}
        // Old backups/configs without new options get backward-compatible defaults.
        val full=publicSnapshot()
        val keys=j.keys()
        while(keys.hasNext()){val key=keys.next();full.put(key,j.get(key))}
        fun bool(k:String):Boolean {
            require(full.get(k) is Boolean){"Tipo de $k inválido"}
            return full.getBoolean(k)
        }
        fun number(k:String,low:Int,high:Int):Int {
            require(full.get(k) is Int){"Tipo de $k inválido"}
            return full.getInt(k).also{require(it in low..high){"$k fuera de rango"}}
        }
        val result=org.json.JSONObject()
        for(k in listOf("auto_start","splash","video_audio","offline_playback",
            "external_cache","signage_show_hud","smart_sound"))result.put(k,bool(k))
        for((k,bounds) in mapOf(
            "enabled_modes" to (1 to 7),"primary_mode" to (1 to 3),
            "sync_minutes" to (1 to 60),"cache_mb" to (128 to 16384),
            "update_policy" to (0 to 2),"overlay_interval_sec" to (60 to 86400),
            "overlay_duration_sec" to (3 to 120),"signage_hud_seconds" to (0 to 60),
            "overlay_width_pct" to (15 to 100),"overlay_height_pct" to (10 to 100),
            "overlay_opacity" to (40 to 100),"smart_header_pct" to (25 to 70),
            "smart_poll_sec" to (5 to 120)
        ))result.put(k,number(k,bounds.first,bounds.second))
        for((k,options) in mapOf(
            "update_channel" to setOf("stable","beta","debug"),
            "overlay_layout" to overlayLayouts,
            "smart_app" to setOf("queue","scoreboard")
        )){
            require(full.get(k) is String && full.getString(k) in options){"Opción inválida para $k"}
            result.put(k,full.getString(k))
        }
        require(result.getInt("enabled_modes") and (1 shl (result.getInt("primary_mode")-1))!=0){
            "El modo principal debe estar habilitado"
        }
        require(result.getInt("overlay_duration_sec")<result.getInt("overlay_interval_sec")){
            "La duración de Overlay debe ser menor que el intervalo"
        }
        return result
    }
    /** Single SharedPreferences.commit() makes config apply atomic at app level. Credentials/PIN stay untouched. */
    @Synchronized fun applySnapshot(snapshot:org.json.JSONObject,newRevision:Long?=null):Boolean {
        val v=validateSettings(snapshot)
        if(newRevision!=null)require(newRevision>=0)
        val e=p.edit()
        for(k in listOf("auto_start","splash","video_audio","offline_playback","external_cache","signage_show_hud","smart_sound"))e.putBoolean(k,v.getBoolean(k))
        for(k in listOf("enabled_modes","primary_mode","update_policy","sync_minutes","cache_mb","overlay_interval_sec","overlay_duration_sec","signage_hud_seconds","overlay_width_pct","overlay_height_pct","overlay_opacity","smart_header_pct","smart_poll_sec"))e.putInt(k,v.getInt(k))
        e.putString("update_channel",v.getString("update_channel"))
        e.putString("overlay_layout",v.getString("overlay_layout"))
        e.putString("smart_app",v.getString("smart_app"))
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
    companion object {
        val overlayLayouts=setOf("bottom","top","left","right","fullscreen","half_horizontal","half_vertical")
        const val DEFAULT_PORTAL="https://www.2cam.services/digitaltv/portal/public/" }
}
