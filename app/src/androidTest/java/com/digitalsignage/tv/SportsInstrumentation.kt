package com.digitalsignage.tv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import services.twocam.sportscore.RacketRules
import services.twocam.sportscore.LocalSportsHost

/** API22 real: servidor dentro de APK, sin Node ni computadora como autoridad. */
class SportsInstrumentation:Instrumentation(){
    private var pinVerifyMs=-1L
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){val result=Bundle();try{exercise();result.putString("stream","SPORTS_FIRE_DIRECT_PASS\nMEDIA_FORMATS_CENTRAL_PASS\nPIN_MIGRATION_PASS subsequent_verification_ms=$pinVerifyMs\nOVERLAY_WINDOW_PASS BOOT_BACKGROUND_POLICY_PASS MODE_PLAYLIST_GATE_PASS\n");finish(Activity.RESULT_OK,result)}catch(e:Throwable){result.putString("stream","SPORTS_FIRE_DIRECT_FAIL ${e.javaClass.simpleName}: ${e.message}\n");finish(Activity.RESULT_CANCELED,result)}}
    private fun api(method:String,path:String,token:String?=null,body:JSONObject?=null,key:String?=null):JSONObject {
        val c=java.net.URL(SportsRuntime.address+"/api/v1"+path).openConnection() as java.net.HttpURLConnection
        c.connectTimeout=4000;c.readTimeout=4000;c.requestMethod=method
        if(token!=null)c.setRequestProperty("Authorization","Bearer $token");if(key!=null)c.setRequestProperty("Idempotency-Key",key)
        try{if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=c.responseCode;val raw=(if(code>=400)c.errorStream else c.inputStream).bufferedReader().use{it.readText()};check(code in 200..299){"HTTP $code $raw"};return JSONObject(raw)
        }finally{c.disconnect()}
    }
    private fun exercise(){
        val formats=JSONObject().put("revision",4).put("side_width_pct",35).put("bottom_height_pct",30)
        check(MediaFormats.percent(formats,"side")==35 && MediaFormats.percent(formats,"bottom")==30)
        check(MediaFormats.percent(null,"side")==40 && MediaFormats.percent(null,"bottom")==20)
        val settings=SettingsStore(targetContext)
        check(settings.schemaFields().size==43)
        val old=settings.publicSnapshot().put("overlay_width_pct",77).put("queue_height_pct",66)
        check(!settings.validateSettings(old).has("overlay_width_pct"))
        var rejected=false;try{MediaFormats.validate(JSONObject().put("revision",1).put("side_width_pct",101).put("bottom_height_pct",20))}catch(_:IllegalArgumentException){rejected=true};check(rejected)
        exercisePin()
        exerciseOverlay()
        val settings=SettingsStore(targetContext)
        settings.prepareProductionStartup()
        val savedSettings=settings.publicSnapshot()
        try {
            val values=settings.publicSnapshot().put("enabled_modes",15).put("primary_mode",4)
                .put("scoreboard_layout","bottom").put("scoreboard_playlist_bottom",42)
            check(settings.applySnapshot(values))
            check(settings.primaryMode==4 && settings.playlistId(4)==42)
            val model=ManifestModel(JSONObject().put("orientation","landscape")
                .put("fallback",JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("name","CARTELERA")))))
            check(model.entries(settings,3).length()==0){"Turnos inherited Cartelera"}
            check(model.entries(settings,4).length()==0){"Marcador inherited Cartelera"}
            val catalog=JSONObject().put("42",JSONObject().put("layout","bottom").put("orientation","landscape")
                .put("items",org.json.JSONArray().put(JSONObject().put("name","MARCADOR"))))
            model.json.put("playlist_catalog",catalog)
            check(model.entries(settings,4).getJSONObject(0).getString("name")=="MARCADOR")
            settings.applySnapshot(settings.publicSnapshot().put("scoreboard_layout","side"))
            check(model.entries(settings,4).length()==0){"Different geometry inherited bottom playlist"}
            try{settings.validateSettings(settings.publicSnapshot().put("overlay_opacity",101));error("Invalid opacity accepted")}catch(_:IllegalArgumentException){}
            val migrated=settings.migrateV1(JSONObject().put("primary_mode",3).put("enabled_modes",7).put("smart_app","scoreboard"))
            check(migrated.getInt("primary_mode")==4 && migrated.getInt("enabled_modes")==15)
        } finally {check(settings.applySnapshot(savedSettings));settings.manualMode=0}
        val activity=startActivitySync(Intent(targetContext,SportsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SportsActivity
        val host=SportsRuntime.host(targetContext);host.configure("Fire de prueba","Pista API22","Verde","Azul",RacketRules.standard("padel",3,"star",6,false),0)
        check(SportsRuntime.address.startsWith("http://")){SportsRuntime.error}
        val path="/screens/${host.screenId()}";val code=host.openPairing("official").getString("code")
        val pair=api("POST","$path/pair",body=JSONObject().put("name","Control Android prueba").put("code",code));val token=pair.getString("controlToken")
        val accepted=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001")
        check(accepted.getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        val repeat=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001");check(repeat.getLong("sequence")==accepted.getLong("sequence"))
        Thread.sleep(900);var text="";runOnMainSync{text=(activity.window.decorView.findViewWithTag<android.widget.TextView>("sports-board")).text.toString()};check(text.contains("15")){"Marcador no actualizado"}
        val snap=api("GET","$path/snapshot",token);check(snap.getJSONObject("match").getInt("version")==2)
        val saved=java.io.File(targetContext.filesDir,"sports-authority-v1.json").readText();val restored=LocalSportsHost(saved){_ ->};check(restored.displaySnapshot().getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        for(role in listOf("home","away")){val pin=host.openPairing(role).getString("code");api("POST","$path/pair",body=JSONObject().put("name",role).put("code",pin))}
        check(host.controls().length()==3);try{host.openPairing("home");error("Aceptó cuarto control")}catch(e:LocalSportsHost.Failure){check(e.status==409)}
        host.revoke(pair.getString("controlId"));check(host.controls().length()==2)
        runOnMainSync{activity.finish()}
    }
    private fun exercisePin(){
        val settings=SettingsStore(targetContext)
        val prefs=targetContext.getSharedPreferences("signage_settings_v1",0)
        val pin="864209";val salt=ByteArray(16){it.toByte()}
        val algorithm=if(android.os.Build.VERSION.SDK_INT>=26)"PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val hash=javax.crypto.SecretKeyFactory.getInstance(algorithm).generateSecret(javax.crypto.spec.PBEKeySpec(pin.toCharArray(),salt,120000,256)).encoded
        prefs.edit().remove("pin_mac_v2").putString("pin_salt",android.util.Base64.encodeToString(salt,2))
            .putString("pin_hash",android.util.Base64.encodeToString(hash,2)).commit()
        check(settings.verifyPin(pin)){"Legacy PIN migration failed"}
        check(prefs.contains("pin_mac_v2")&&!prefs.contains("pin_hash")){"PIN not migrated"}
        val start=android.os.SystemClock.elapsedRealtime()
        check(settings.verifyPin(pin));val elapsed=android.os.SystemClock.elapsedRealtime()-start;pinVerifyMs=elapsed
        check(!settings.verifyPin("111111")){"Wrong PIN accepted"}
        for(i in 1..4)check(!settings.verifyPin("111111"))
        try{settings.verifyPin(pin);error("PIN attempt lock bypassed")}catch(_:IllegalStateException){}
        settings.setPin(pin);check(settings.verifyPin(pin))
        println("PIN_MIGRATION_PASS subsequent_verification_ms=$elapsed")
    }
    private fun exerciseOverlay(){
        val settings=SettingsStore(targetContext);settings.prepareProductionStartup()
        val original=settings.publicSnapshot();val manual=settings.manualMode
        val active=java.io.File(targetContext.filesDir,"signage-manifest-v1/active.json")
        val originalManifest=if(active.isFile)active.readBytes() else null
        val image=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GREEN)
        val bytes=java.io.ByteArrayOutputStream().also{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();image.recycle()
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val file=CacheManager(targetContext,settings).mediaFile(sha);file.writeBytes(bytes)
        try{
            val config=JSONObject(original.toString()).put("enabled_modes",15).put("primary_mode",2).put("overlay_layout","bottom")
                .put("overlay_playlist_bottom",91).put("debug_hud_enabled",false)
            settings.applySnapshot(config);settings.manualMode=2
            check(ModeReadiness.error(config,null,2)!=null)
            val empty=ManifestModel(JSONObject().put("orientation","landscape"))
            check(ModeReadiness.error(config,empty,2)!=null){"Missing playlist accepted"}
            val item=JSONObject().put("mime","image/png").put("layout","bottom").put("sha256",sha).put("size",bytes.size).put("duration",3)
            val list=JSONObject().put("id",91).put("layout","bottom").put("orientation","landscape").put("item_count",1).put("image_count",1)
                .put("items",org.json.JSONArray().put(item))
            val manifest=JSONObject().put("schema_version",3).put("orientation","landscape").put("playlist_catalog",JSONObject().put("91",list))
            val model=ManifestModel(manifest)
            check(ModeReadiness.error(config,model,2)==null)
            val legacy=ManifestModel(JSONObject().put("orientation","landscape").put("fallback",JSONObject()
                .put("playlist_id",91).put("playlist_name","Franja heredada").put("layout","bottom").put("orientation","landscape")
                .put("items",org.json.JSONArray().put(item))))
            check(ModeReadiness.error(config,legacy,2)==null){"Explicit compatible legacy assignment rejected"}
            check(ModeReadiness.error(JSONObject(config.toString()).put("overlay_playlist_bottom",0),legacy,2)!=null){"Implicit Cartelera inheritance accepted"}
            list.put("image_count",0);check(ModeReadiness.error(config,model,2)!=null){"Overlay without image accepted"};list.put("image_count",1)
            check(StartupPolicy.mode(settings,model)==2 && StartupPolicy.background(2))
            check(!StartupPolicy.background(1) && !StartupPolicy.background(3))
            check(OverlayMotion.offset("bottom",80,20).second>0 && OverlayMotion.offset("side",80,20).first>0)
            check(OverlayMotion.offset("top",80,20).second<0 && OverlayMotion.offset("left",80,20).first<0)
            active.parentFile?.mkdirs();active.writeText(manifest.toString())
            settings.applySnapshot(JSONObject(config.toString()).put("primary_mode",1));settings.manualMode=0
            val before=OverlayService.lastShownAt
            runOnMainSync{AndroidCompat.startServiceCompat(targetContext,Intent(targetContext,OverlayService::class.java).setAction(OverlayService.ACTION_PREVIEW))}
            for(i in 0 until 60){if(OverlayService.lastShownAt>before)break;Thread.sleep(100)}
            check(OverlayService.lastShownAt>before){"Overlay window was not attached"}
            check(settings.primaryMode==1 && settings.manualMode==0){"Preview changed saved mode"}
            println("OVERLAY_WINDOW_PASS BOOT_BACKGROUND_POLICY_PASS MODE_PLAYLIST_GATE_PASS")
        }finally{
            runOnMainSync{targetContext.stopService(Intent(targetContext,OverlayService::class.java))}
            check(settings.applySnapshot(original));settings.manualMode=manual
            if(originalManifest!=null)active.writeBytes(originalManifest)else active.delete()
            file.delete()
        }
    }
}

