package com.digitalsignage.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Offline-friendly screen for crash recovery and log upload. */
class DiagnosticsActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val ui=Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val settings=SettingsStore(this);val logs=DeviceLogs(this)
        val root=ScreenStyles.column(this)
        root.addView(ScreenStyles.label(this,"DIGITAL SIGNAGE · DIAGNÓSTICO",25f))
        root.addView(ScreenStyles.label(this,"Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}",16f))
        root.addView(ScreenStyles.label(this,"Vinculación: "+if(SecureToken(this).load()!=null)"Sí" else "No",16f))
        val status=ScreenStyles.label(this,"Eventos guardados localmente",15f);root.addView(status)
        root.addView(ScreenStyles.button(this,"Enviar logs al portal"){
            status.text="Enviando..."
            worker.execute{
                try{
                    val secret=SecureToken(this).load()?:throw IllegalStateException("Vincula la pantalla primero")
                    val count=logs.upload(SignageApi(settings.portalUrl,secret))
                    ui.post{status.text="Confirmados por el portal: $count"}
                }catch(e:Exception){ui.post{status.text="No enviados: ${e.message}"}}
            }
        })
        root.addView(TextView(this).apply{
            setTextColor(ScreenStyles.FOREGROUND);textSize=12f;text=logs.summary()
        })
        root.addView(ScreenStyles.button(this,"Volver"){finish()})
        setContentView(ScrollView(this).apply{addView(root)})
    }
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
