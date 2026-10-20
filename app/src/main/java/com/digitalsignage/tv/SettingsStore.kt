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
            if(old.optString("overlay_layout") =="right")old.put("overlay_layout","side")
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
    fun mediaFormatPercent(layout:String):Int=MediaFormats.percent(CacheManager(context,this).active()?.optJSONObject("media_formats"),layout)
    val overlayWidthPct:Int get()=mediaFormatPercent("side")
    val overlayHeightPct:Int get()=mediaFormatPercent("bottom")
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
    /** Explicit local off vetoes a later portal refresh that enables auto_start.
      * Re-enable from the TV to clear the local veto. Does not stop manual play. */
    fun localBootBlocked():Boolean=p.getBoolean("boot_local_block",false)
    fun bootAutoStartEnabled():Boolean=autoStart && !localBootBlocked()
    fun setBootAutoStartFromTv(enabled:Boolean):Boolean=
        p.edit().putBoolean("auto_start",enabled)
            .putBoolean("boot_local_block",!enabled).commit()
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
        get() = (p.getInt("enabled_modes",15) and 15) or 3
        set(v) = p.edit().putInt("enabled_modes",(v and 15) or 3).apply()
    var primaryMode:Int
        get()=p.getInt("primary_mode",1).coerceIn(1,4)
        set(v){require(v in 1..4);p.edit().putInt("primary_mode",v).apply()}
    var updatePolicy:Int
        get()=p.getInt("update_policy",2).coerceIn(0,2)
        set(v){require(v in 0..2);p.edit().putInt("update_policy",v).apply()}
    var updateChannel:String
        get()=p.getString("update_channel","debug")?.let{if(it=="beta")"stable" else it}?.takeIf{it in listOf("stable","debug")} ?: "debug"
        set(v){require(v in listOf("stable","debug"));p.edit().putString("update_channel",v).apply()}
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
    private val pinSecret by lazy {SecureToken(context,"admin_pin_v2")}
    fun hasPin() = p.contains("pin_mac_v2") || p.contains("pin_hash")
    @Synchronized fun verifyPin(input:String):Boolean {
        if(!hasPin())return false
        val now=System.currentTimeMillis()
        check(now>=p.getLong("pin_locked_until",0L)){"Demasiados intentos. Espera antes de reintentar"}
        val valid=if(p.contains("pin_mac_v2")){
            val secret=pinSecret.load()?:throw IllegalStateException("No se pudo abrir la clave del PIN en AndroidKeyStore")
            val salt=Base64.decode(p.getString("pin_salt_v2","")!!,Base64.NO_WRAP)
            val actual=pinMac(input,salt,secret)
            java.security.MessageDigest.isEqual(actual,Base64.decode(p.getString("pin_mac_v2","")!!,Base64.NO_WRAP))
        }else{
            val salt=Base64.decode(p.getString("pin_salt", "")!!,Base64.NO_WRAP)
            val actual=pinHash(input,salt)
            val matched=java.security.MessageDigest.isEqual(actual,Base64.decode(p.getString("pin_hash", "")!!,Base64.NO_WRAP))
            if(matched)setPin(input) // One successful legacy verification migrates the PIN without changing it.
            matched
        }
        if(valid)p.edit().putInt("pin_failures",0).remove("pin_locked_until").apply()
        else{
            val failures=p.getInt("pin_failures",0)+1
            val edit=p.edit().putInt("pin_failures",failures)
            if(failures>=5)edit.putLong("pin_locked_until",now+minOf(300000L,30000L*(failures-4)))
            edit.commit()
        }
        return valid
    }
    @Synchronized fun setPin(pin:String){
        require(pin.length>=6 && pin.all{it.isDigit()})
        val secret=pinSecret.load()?:ByteArray(32).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}.also{pinSecret.save(it)}
        val salt=ByteArray(16);SecureRandom().nextBytes(salt)
        val mac=pinMac(pin,salt,secret)
        check(p.edit().putString("pin_salt_v2",Base64.encodeToString(salt,Base64.NO_WRAP))
            .putString("pin_mac_v2",Base64.encodeToString(mac,Base64.NO_WRAP))
            .remove("pin_hash").remove("pin_salt").remove("pin_locked_until").putInt("pin_failures",0).commit())
    }
    private fun pinMac(input:String,salt:ByteArray,secret:String):ByteArray {
        val key=ByteArray(secret.length/2){secret.substring(it*2,it*2+2).toInt(16).toByte()}
        return javax.crypto.Mac.getInstance("HmacSHA256").run{
            init(javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"))
            doFinal("2cam-admin-pin-v2".toByteArray(Charsets.UTF_8)+salt+input.toByteArray(Charsets.UTF_8))
        }
    }
    private fun pinHash(pin:String,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,120000,256)
        return try{ SecretKeyFactory.getInstance(if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally{spec.clearPassword()}
    }
    // Only allow-listed, non-secret options can be backed up or sent remotely.
    val schema:org.json.JSONObject by lazy {org.json.JSONObject(context.assets.open("config-schema.json").bufferedReader().use{it.readText()})}
    fun schemaFields():List<org.json.JSONObject> {val a=schema.getJSONArray("fields");return (0 until a.length()).map{a.getJSONObject(it)}}
    fun publicSnapshot():org.json.JSONObject = org.json.JSONObject().apply {
        for(f in schemaFields()) {val k=f.getString("key");val d=f.get("default")
            val value:Any=when(k){
                "debug_hud_enabled"->debugHudEnabled
                "offline_playback"->offlinePlayback
                "update_channel"->updateChannel
                "enabled_modes"->enabledModes
                else->when(d){is Boolean->p.getBoolean(k,d);is Int->p.getInt(k,d);else->p.getString(k,d.toString())?:d.toString()}
            };put(k,value)
        }
    }
    @Volatile private var catalogUntil=0L
    private var catalogCache:org.json.JSONObject?=null
    @Synchronized fun prepareFormData(){
        schemaFields()
        catalogCache=CacheManager(context,this).active()?.let{ManifestModel(it).catalog()}
        catalogUntil=android.os.SystemClock.elapsedRealtime()+5000L
    }
    fun activationError(mode:Int,snapshot:org.json.JSONObject=publicSnapshot()):String? =
        ModeReadiness.error(snapshot,CacheManager(context,this).active()?.let{ManifestModel(it)},mode)
    fun requireActivationChange(snapshot:org.json.JSONObject){
        if(ModeReadiness.activationChanged(publicSnapshot(),snapshot)){
            activationError(snapshot.getInt("primary_mode"),snapshot)?.let{throw IllegalArgumentException(it)}
        }
    }
    fun playlistChoices(def:org.json.JSONObject):List<Pair<Int,String>> {
        if(android.os.SystemClock.elapsedRealtime()>=catalogUntil)prepareFormData()
        val catalog=catalogCache
        val out=mutableListOf(0 to (if(def.getString("key")=="signage_playlist_id")"Programación del portal" else "Sin asignar"))
        catalog?.keys()?.forEach{k->val p=catalog.optJSONObject(k);if(p!=null && p.optString("layout")==def.getString("layout"))out.add(p.getInt("id") to ("#${p.getInt("id")} · ${p.getString("name")}"))}
        val current=publicSnapshot().getInt(def.getString("key"))
        if(current>0 && out.none{it.first==current})out.add(current to "#$current · No disponible")
        return out.sortedBy{it.first}
    }
    fun settingInt(k:String)=publicSnapshot().getInt(k)
    fun settingString(k:String)=publicSnapshot().getString(k)
    /** Overlay is the sole advertising source for Overlay, Turnos and Sports.
      * Operational engines remain distinct, but public media assignments do not. */
    fun modeLayout(mode:Int):String=if(mode in 2..4)when(overlayLayout){"top"->"bottom";"left"->"side";else->overlayLayout} else "fullscreen"
    fun playlistId(mode:Int):Int=if(mode==1)settingInt("signage_playlist_id")
        else if(mode in 2..4)settingInt("overlay_playlist_"+modeLayout(mode)) else 0
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
        if(j.optString("update_channel")=="beta")j.put("update_channel","stable")
        if(app=="scoreboard"){
            if(j.optInt("primary_mode")==3)j.put("primary_mode",4)
            if(j.has("enabled_modes") && j.optInt("enabled_modes") and 4!=0)j.put("enabled_modes",j.getInt("enabled_modes") or 8)
        }
        j.remove("smart_app");j.remove("smart_header_pct")
        for(m in listOf("overlay","queue","scoreboard"))for(k in listOf("width","height"))j.remove("${m}_${k}_pct")
        if(j.optString("overlay_layout") =="right")j.put("overlay_layout","side")
        return j
    }
    fun validateSettings(j:org.json.JSONObject):org.json.JSONObject {
        val full=publicSnapshot();val allowed=schemaFields().map{it.getString("key")}.toSet()
        val migrated=migrateV1(j);val keys=migrated.keys();while(keys.hasNext()){val k=keys.next();require(k in allowed){"Parámetro desconocido: $k"};full.put(k,migrated.get(k))}
        for(f in schemaFields()) {val k=f.getString("key");val v=full.get(k)
            when(f.getString("type")) {
                "bool"->require(v is Boolean){"Tipo inválido: $k"}
                "enum"->require((if(f.get("default") is Int)v is Int else v is String) && f.getJSONObject("options").has(v.toString())){"Opción inválida: $k"}
                else->require(v is Int && v in f.getInt("min")..f.getInt("max")){"Fuera de rango: $k"}
            }
        }
        full.put("enabled_modes",full.getInt("enabled_modes") or 3)
        require(full.getInt("enabled_modes") and (1 shl (full.getInt("primary_mode")-1))!=0){"El módulo principal seleccionado no está habilitado"}
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
        val overlayLayouts=setOf("bottom","top","left","side","fullscreen","half_horizontal","half_vertical")
        const val DEFAULT_PORTAL="https://www.2cam.services/digitaltv/portal/public/" }
}

