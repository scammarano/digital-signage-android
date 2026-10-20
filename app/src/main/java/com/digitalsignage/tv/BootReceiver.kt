package com.digitalsignage.tv

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Overlay starts directly as a background service; signage requests the player activity. */
class BootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent?){
        if(intent?.action !in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        val pending=goAsync();val app=context.applicationContext
        Thread{
            val logs=DeviceLogs(app)
            try{
                val settings=SettingsStore(app);settings.prepareProductionStartup()
                val sinceBoot=android.os.SystemClock.elapsedRealtime()
                logs.record("info","boot_received","Fire OS notificó arranque o actualización",
                    "action=${intent?.action};since_boot_ms=$sinceBoot;auto_start=${settings.autoStart};local_block=${settings.localBootBlocked()};version=${BuildConfig.VERSION_NAME};sdk=${android.os.Build.VERSION.SDK_INT}")
                if(!settings.bootAutoStartEnabled()){
                    logs.record("info","boot_skipped","Arranque automático desactivado por portal o TV")
                    return@Thread
                }
                logs.record("info","boot_allowed","Inicio autorizado","auto_start=true;local_block=false")
                if(settings.diagnosticMode){
                    logs.record("info","boot_skipped","Modo diagnóstico manual activo")
                    return@Thread
                }
                if(SecureToken(app).load()==null){
                    logs.record("warn","pairing_required","Arranque sin token; abriendo emparejamiento")
                    app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@Thread
                }
                val model=CacheManager(app,settings).active()?.let{ManifestModel(it)}
                val mode=StartupPolicy.mode(settings,model)
                logs.record("info","boot_token_found","Token local disponible",
                    "mode=$mode;cache_ready=${model!=null};manual_mode=${settings.manualMode}")
                if(model==null){
                    // Fresh install must start the synchronizing player instead of
                    // returning from the receiver. The player owns retries and diagnostics.
                    logs.record("info","boot_sync_started","Caché vacía; iniciando sincronización")
                    app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@Thread
                }
                logs.record("info","boot_manifest_ready","Catálogo local disponible","mode=$mode")
                val error=ModeReadiness.error(settings.publicSnapshot(),model,mode)
                if(error!=null){
                    logs.record("warn","boot_mode_pending",error,"mode=$mode")
                    // Content/entitlements may have changed at the portal.
                    // Allow an authenticated sync before treating cached data as final.
                    app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@Thread
                }
                logs.record("info","boot_mode_ready","Modo validado","mode=$mode")
                if(StartupPolicy.background(mode)){
                    if(!AndroidCompat.canOverlay(app)){
                        notify(app,"Overlay bloqueado","Abre Ajustes para autorizar la superposición")
                        logs.record("warn","overlay_permission_missing","Falta permiso Overlay")
                        app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        return@Thread
                    }
                    AndroidCompat.startServiceCompat(app,Intent(app,OverlayService::class.java))
                    logs.record("info","boot_overlay","Servicio Overlay iniciado sin abrir el reproductor")
                }else{
                    app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    logs.record("info","boot_player","Inicio automático solicitado","mode=$mode")
                }
            }catch(e:Exception){
                logs.record("error","boot_blocked",e.javaClass.simpleName,e.message?:"")
                notify(app,"Inicio automático bloqueado","El sistema impidió iniciar Digital Signage. Consulta Diagnóstico")
            }finally{pending.finish()}
        }.start()
    }
    private fun notify(c:Context,title:String,message:String){try{
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(92,
            AndroidCompat.notification(c,"signage_start","Inicio Digital Signage",title,message))
    }catch(_:Exception){}}
}
