package com.digitalsignage.tv

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Best-effort autostart: firmware may restrict background activity launches. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = SettingsStore(context)
        if (!settings.autoStart || settings.diagnosticMode || SecureToken(context).load() == null) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(92, AndroidCompat.notification(context, "signage_start", "Inicio Digital Signage",
                "Digital Signage listo", "Pulsa para abrir el reproductor"))
        } catch (_: Exception) {}
    }
}
