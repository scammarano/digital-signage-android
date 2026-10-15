package com.digitalsignage.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** User-initiated 30-minute LAN panel. Never autostart from BOOT_COMPLETED. */
class LocalAdminService:Service(){
    private var server:LocalAdminServer?=null
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val autoStop=Runnable{stopLocal();stopSelf()}
    companion object {
        const val ACTION_START="com.digitalsignage.tv.START_LAN"
        const val ACTION_STOP="com.digitalsignage.tv.STOP_LAN"
        @Volatile var runningUrl:String=""
        @Volatile var pairingCode:String=""
        @Volatile var lastError:String=""
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP){stopLocal();stopSelf();return START_NOT_STICKY}
        if(intent?.action!=ACTION_START)return START_NOT_STICKY
        try {
            val settings=SettingsStore(this)
            if(!settings.localAdminEnabled())throw IllegalStateException("Inicia el panel desde el menú local")
            if(server?.isRunning()==true)return START_NOT_STICKY
            startForeground(4101, AndroidCompat.notification(this, "signage_lan_admin", "Administración local",
                "Digital Signage · Panel LAN temporal", "La administración local se cerrará en 30 minutos"))
            server=LocalAdminServer(this,settings).also{it.start()}
            runningUrl=server!!.address;pairingCode=server!!.code;lastError=""
            handler.removeCallbacks(autoStop);handler.postDelayed(autoStop,30*60*1000L)
        }catch(e:Exception){lastError=e.message?:"No disponible";settingsDisable();stopSelf()}
        return START_NOT_STICKY
    }
    private fun settingsDisable(){SettingsStore(this).setLocalAdminEnabled(false)}
    private fun stopLocal(){server?.close();server=null;runningUrl="";pairingCode="";settingsDisable()}
    override fun onDestroy(){handler.removeCallbacks(autoStop);stopLocal();super.onDestroy()}
}
