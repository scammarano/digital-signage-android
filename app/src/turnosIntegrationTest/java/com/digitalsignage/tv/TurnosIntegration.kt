package com.digitalsignage.tv

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import services.twocam.turnos.TurnosFixture
import java.io.File

object TurnosIntegration {
    @JvmStatic fun exercise(test:Instrumentation){
        val context=test.targetContext;val settings=SettingsStore(context)
        val original=settings.publicSnapshot();val originalManual=settings.manualMode;val originalDiagnostic=settings.diagnosticMode
        val cache=CacheManager(context,settings);val active=File(context.filesDir,"signage-manifest-v1/active.json");val saved=if(active.isFile)active.readBytes() else null
        val binding=TurnosBinding(context);binding.clear();val fixture=TurnosFixture(context)
        var player:MainActivity?=null
        try{
            check(TurnosLocalApi.endpoint("192.168.1.20:8990")=="http://192.168.1.20:8990")
            for(bad in listOf("http://example.com","http://8.8.8.8:8990","http://192.168.1.1/commands","http://user@192.168.1.1","https://192.168.1.1")){
                try{TurnosLocalApi.endpoint(bad);error("Unsafe endpoint accepted")}catch(_:IllegalArgumentException){}
            }
            try{binding.connect(fixture.base,fixture.wrongCode());error("Operator token accepted for signage")}catch(_:IllegalArgumentException){}
            check(!binding.enabled&&binding.token()==null)
            settings.setPin("864209")
            val linker=test.startActivitySync(Intent(context,TurnosLinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TurnosLinkActivity
            test.waitForIdleSync()
            test.runOnMainSync{
                val dialog=linker.pinDialog?:error("PIN gate missing")
                dialog.window!!.decorView.findViewWithTag<EditText>("turnos-pin").setText("864209")
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            }
            waitFor(test,linker,"turnos-address",null)
            val code=fixture.viewerCode()
            test.runOnMainSync{
                linker.window.decorView.findViewWithTag<EditText>("turnos-address").setText(fixture.base)
                linker.window.decorView.findViewWithTag<EditText>("turnos-code").setText(code)
                linker.window.decorView.findViewWithTag<View>("turnos-link").performClick()
            }
            waitFor(test,linker,"turnos-link-status","Enlazado como visor")
            check(binding.enabled&&binding.id==fixture.id()&&binding.token()!=null)
            check(!settings.publicSnapshot().toString().contains(binding.token()!!))
            test.runOnMainSync{linker.finish()};test.waitForIdleSync()
            val png=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)}
            val bytes=java.io.ByteArrayOutputStream().also{png.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();png.recycle()
            val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
            cache.mediaFile(sha).writeBytes(bytes)
            val media=JSONObject().put("name","Publicidad de prueba").put("mime","image/png").put("sha256",sha).put("size",bytes.size).put("layout","bottom").put("duration",30).put("transition","cut")
            val manifest=JSONObject().put("orientation","landscape").put("timezone","UTC").put("schema_version",3)
                .put("media_formats",JSONObject().put("revision",1).put("side_width_pct",40).put("bottom_height_pct",20))
                .put("fallback",JSONObject().put("items",JSONArray()))
                .put("playlist_catalog",JSONObject().put("97",JSONObject().put("layout","bottom").put("orientation","landscape").put("item_count",1).put("items",JSONArray().put(media))))
            active.parentFile!!.mkdirs();active.writeText(manifest.toString())
            check(settings.applySnapshot(settings.publicSnapshot().put("enabled_modes",15).put("primary_mode",3).put("queue_layout","bottom").put("queue_playlist_bottom",97).put("debug_hud_enabled",false)))
            settings.manualMode=3;settings.diagnosticMode=true
            val screen=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity;player=screen
            waitFor(test,screen,"live-board","En línea")
            fixture.issue();val call=fixture.call();val ticket=call.getJSONObject("ticket").getString("ticket_id")
            waitFor(test,screen,"live-board","A-001")
            test.runOnMainSync{
                val zone=screen.window.decorView.findViewWithTag<FrameLayout>("advertising-zone")
                check(zone.width>0&&zone.height>0&&zone.height<screen.resources.displayMetrics.heightPixels/2){"Advertising geometry changed"}
                val board=screen.window.decorView.findViewWithTag<TextView>("live-board")
                check(board.bottom<=zone.top){"Advertising covers the called ticket after orientation change"}
            }
            waitChildren(test,screen)
            fixture.stop();waitFor(test,screen,"live-board","ESTADO DESACTUALIZADO")
            test.runOnMainSync{check(screen.window.decorView.findViewWithTag<TextView>("live-board").text.contains("A-001"))}
            fixture.start();waitFor(test,screen,"live-board","En línea")
            fixture.command("start",ticket);fixture.command("finish",ticket);fixture.issue();fixture.call()
            waitFor(test,screen,"live-board","A-002")
            fixture.restore();waitFor(test,screen,"live-board","En línea")
            check(binding.cached()!!.getLong("sequence")>0)
            fixture.revokeViewers();waitFor(test,screen,"live-board","Enlace revocado")
            test.runOnMainSync{screen.finish()};test.waitForIdleSync()
            // Cached public snapshot survives activity reconstruction while the authority is unreachable.
            fixture.stop();val reopened=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity;player=reopened
            waitFor(test,reopened,"live-board","A-002");waitFor(test,reopened,"live-board","ESTADO DESACTUALIZADO")
            val other=JSONObject(binding.cached().toString()).put("screen_id",java.util.UUID.randomUUID().toString())
            try{TurnosLocalApi.validate(other,binding.id);error("Cross-scope snapshot accepted")}catch(_:IllegalArgumentException){}
            test.sendStatus(0,android.os.Bundle().apply{putString("stream","TURNOS_SIGNAGE_INTEGRATION_PASS native_pairing mixed_media offline reconnect restart revoked_scope no_queue_logic_in_player\n")})
        }finally{
            player?.let{test.runOnMainSync{it.finish()}};fixture.close();binding.clear();settings.applySnapshot(original);settings.manualMode=originalManual;settings.diagnosticMode=originalDiagnostic
            if(saved==null)active.delete()else active.writeBytes(saved)
        }
    }
    private fun waitFor(test:Instrumentation,activity:android.app.Activity,tag:String,contains:String?){
        val until=System.currentTimeMillis()+18000
        while(System.currentTimeMillis()<until){var ready=false;test.runOnMainSync{val view=activity.window.decorView.findViewWithTag<View>(tag);ready=view!=null&&(contains==null||view is TextView&&view.text.contains(contains))};if(ready)return;Thread.sleep(100)}
        error("$tag did not show $contains")
    }
    private fun waitChildren(test:Instrumentation,activity:android.app.Activity){val until=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<until){var found=false;test.runOnMainSync{found=activity.window.decorView.findViewWithTag<FrameLayout>("advertising-zone").childCount>0};if(found)return;Thread.sleep(100)};error("Cached advertising image did not render")}
}
