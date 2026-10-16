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
        if(!p.getBoolean("four_modes_migrated",false)){
            val old=publicSnapshot()
            if(p.getString("smart_app","queue")=="scoreboard"){
                if(old.getInt("primary_mode")==3)old.put("primary_mode",4)
                if(old.getInt("enabled_modes") and 4!=0)old.put("enabled_modes",old.getInt("enabled_modes") or 8)
                if(manualMode==3)manualMode=4
            }
            if(old.optString("overlay_layout") in listOf("left","right"))old.put("overlay_layout","side")
            if(old.optString("overlay_layout")=="top")old.put("overlay_layout","bottom")
            check(applySnapshot(old));check(p.edit().putBoolean("four_modes_migrated",true).commit())
        }
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
        get()=p.getInt("smart_poll_sec",10).coerceIn(1,120)
        set(v){require(v in 1..120);p.edit().putInt("smart_poll_sec",v).apply()}
    var smartSound:Boolean
        get()=p.getBoolean("smart_sound",false)
        set(v)=p.edit().putBoolean("smart_sound",v).apply()
    /** Debug HUD is local-only until the portal ships schema v2. */
    var debugHudEnabled:Boolean
        get()=p.getBoolean("debug_hud_enabled",p.getBoolean("debug_hud",true))
        set(v){p.edit().putBoolean("debug_hud_enabled",v).apply()}
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
        get() = p.getBoolean("offline_playback",p.getBoolean("offline",true))
        set(v) = p.edit().putBoolean("offline_playback",v).apply()
    var enabledModes: Int
        get() = p.getInt("enabled_modes",15) and 15
        set(v) = p.edit().putInt("enabled_modes",v and 15).apply()
    var primaryMode:Int
        get()=p.getInt("primary_mode",1).coerceIn(1,4)
        set(v){require(v in 1..4);p.edit().putInt("primary_mode",v).apply()}
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
        get()=p.getInt("manual_mode",0).coerceIn(0,4)
        set(value){require(value in 0..4);p.edit().putInt("manual_mode",value).commit()}
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
    val schema:org.json.JSONObject get()=org.json.JSONObject(context.assets.open("config-schema.json").bufferedReader().use{it.readText()})
    fun schemaFields():List<org.json.JSONObject> {val a=schema.getJSONArray("fields");return (0 until a.length()).map{a.getJSONObject(it)}}
    fun publicSnapshot():org.json.JSONObject = org.json.JSONObject().apply {
        for(f in schemaFields()) {val k=f.getString("key");val d=f.get("default")
            val value:Any=when(k){
                "debug_hud_enabled"->debugHudEnabled
                "offline_playback"->offlinePlayback
                else->when(d){is Boolean->p.getBoolean(k,d);is Int->p.getInt(k,d);else->p.getString(k,d.toString())?:d.toString()}
            };put(k,value)
        }
    }
    fun playlistChoices(def:org.json.JSONObject):List<Pair<Int,String>> {
        val catalog=CacheManager(context,this).active()?.optJSONObject("playlist_catalog")
        val out=mutableListOf(0 to (if(def.getString("key")=="signage_playlist_id")"Programación del portal" else "Sin asignar"))
        catalog?.keys()?.forEach{k->val p=catalog.optJSONObject(k);if(p!=null && p.optString("layout")==def.getString("layout"))out.add(p.getInt("id") to ("#${p.getInt("id")} · ${p.getString("name")}"))}
        val current=publicSnapshot().getInt(def.getString("key"))
        if(current>0 && out.none{it.first==current})out.add(current to "#$current · No disponible")
        return out.sortedBy{it.first}
    }
    fun settingInt(k:String)=publicSnapshot().getInt(k)
    fun settingString(k:String)=publicSnapshot().getString(k)
    fun modeLayout(mode:Int):String=when(mode){2->overlayLayout;3->settingString("queue_layout");4->settingString("scoreboard_layout");else->"fullscreen"}
    fun playlistId(mode:Int):Int=if(mode==1)settingInt("signage_playlist_id") else settingInt((when(mode){2->"overlay";3->"queue";else->"scoreboard"})+"_playlist_"+modeLayout(mode))
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
    fun migrateV1(input:org.json.JSONObject):org.json.JSONObject {
        val j=org.json.JSONObject(input.toString());val app=j.optString("smart_app","queue")
        if(app=="scoreboard"){
            if(j.optInt("primary_mode")==3)j.put("primary_mode",4)
            if(j.has("enabled_modes") && j.optInt("enabled_modes") and 4!=0)j.put("enabled_modes",j.getInt("enabled_modes") or 8)
        }
        j.remove("smart_app");j.remove("smart_header_pct")
        if(j.optString("overlay_layout") in listOf("left","right"))j.put("overlay_layout","side")
        if(j.optString("overlay_layout")=="top")j.put("overlay_layout","bottom")
        return j
    }
    fun validateSettings(j:org.json.JSONObject):org.json.JSONObject {
        val full=publicSnapshot();val allowed=schemaFields().map{it.getString("key")}.toSet()
        val keys=j.keys();while(keys.hasNext()){val k=keys.next();require(k in allowed){"Parámetro desconocido: $k"};full.put(k,j.get(k))}
        for(f in schemaFields()) {val k=f.getString("key");val v=full.get(k)
            when(f.getString("type")) {
                "bool"->require(v is Boolean){"Tipo inválido: $k"}
                "enum"->require((if(f.get("default") is Int)v is Int else v is String) && f.getJSONObject("options").has(v.toString())){"Opción inválida: $k"}
                else->require(v is Int && v in f.getInt("min")..f.getInt("max")){"Fuera de rango: $k"}
            }
        }
        require(full.getInt("enabled_modes") and (1 shl (full.getInt("primary_mode")-1))!=0){"El modo principal debe estar habilitado"}
        require(full.getInt("overlay_duration_sec")<full.getInt("overlay_interval_sec")){"Duración Overlay debe ser menor que intervalo"}
        return full
    }
    @Synchronized fun applySnapshot(snapshot:org.json.JSONObject,newRevision:Long?=null):Boolean {
        val v=validateSettings(snapshot);if(newRevision!=null)require(newRevision>=0)
        val e=p.edit();for(f in schemaFields()){val k=f.getString("key");val d=f.get("default")
            when(d){is Boolean->e.putBoolean(k,v.getBoolean(k));is Int->e.putInt(k,v.getInt(k));else->e.putString(k,v.getString(k))}
        }
        if(newRevision!=null)e.putLong("remote_revision",newRevision)
        return e.commit()
    }
    fun backupSnapshot(label:String):java.io.File {
        val dir=java.io.File(context.filesDir,"config-history").apply{mkdirs()}
        val f=java.io.File(dir,"settings-${System.currentTimeMillis()}-${java.security.SecureRandom().nextInt(99999)}.json")
        val data=org.json.JSONObject().put("format","digital-signage-settings").put("schema",2).put("label",label.take(80)).put("settings",publicSnapshot())
        f.writeText(data.toString(2),Charsets.UTF_8)
        dir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(10)?.forEach{it.delete()}
        return f
    }
    companion object {
        val overlayLayouts=setOf("bottom","side","fullscreen","half_horizontal","half_vertical")
        const val DEFAULT_PORTAL="https://www.2cam.services/digitaltv/portal/public/" }
}

