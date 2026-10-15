package com.digitalsignage.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Best effort only: Android/Fire OS can prohibit boot-time foreground activity starts. */
class BootReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){
   if(intent?.action!=Intent.ACTION_BOOT_COMPLETED)return
   val s=SettingsStore(context);if(!s.autoStart||SecureToken(context).load()==null)return
   if(Settings.canDrawOverlays(context))try{
     context.startForegroundService(Intent(context,OverlayService::class.java))
   }catch(_:Exception){}
   // Some TV firmware permits this; otherwise use a visible notification to launch.
   try{context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
   catch(_:Exception){}
   val mgr=context.getSystemService(NotificationManager::class.java)
   mgr.createNotificationChannel(NotificationChannel("signage_start","Inicio Digital Signage",NotificationManager.IMPORTANCE_DEFAULT))
   val launch=PendingIntent.getActivity(context,1,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   mgr.notify(92,Notification.Builder(context,"signage_start").setContentTitle("Digital Signage listo")
     .setContentText("Pulsa para abrir el reproductor").setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(launch).setAutoCancel(true).build())
 }
}
