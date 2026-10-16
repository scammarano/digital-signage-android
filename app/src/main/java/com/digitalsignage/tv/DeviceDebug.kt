package com.digitalsignage.tv

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Non-sensitive reception, persistence and runtime telemetry. */
object DeviceDebug {
    private const val PREFS="signage_debug_v1"
    private val main=Handler(Looper.getMainLooper())
    private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun received(c:Context)=prefs(c).getLong("received_revision",0L)
    fun applied(c:Context)=prefs(c).getLong("applied_revision",0L)
    fun effective(c:Context)=prefs(c).getLong("effective_revision",0L)
    fun effectiveMode(c:Context)=prefs(c).getInt("effective_mode",0)
    fun lastEvent(c:Context)=prefs(c).getString("event","Sin eventos")?:"Sin eventos"
    fun mode(mode:Int):String=when(mode){1->"CARTELERA";2->"OVERLAY";3->"PANTALLA INTELIGENTE";else->"SIN MODO"}
    fun record(c:Context,logs:DeviceLogs,stage:String,revision:Long,detail:String="",runtimeMode:Int=0){
        val tag=when(stage){"received"->"RECIBIDO";"applied"->"GUARDADO";"reported"->"CONFIRMADO EN PORTAL";"started"->"MODO SELECCIONADO";
            "effective"->"EN EJECUCIÓN";"blocked"->"NO OPERATIVO";"rejected"->"RECHAZADO";else->stage.uppercase(Locale.US)}
        val text="$tag · config r$revision ${detail.take(150)}".trim()
        val previous=lastEvent(c)
        val e=prefs(c).edit().putString("event",text).putLong("at",System.currentTimeMillis())
        when(stage){
            "received"->e.putLong("received_revision",revision)
            "applied"->e.putLong("applied_revision",revision)
            "effective"->{
                e.putLong("effective_revision",revision)
                if(runtimeMode in 1..3)e.putInt("effective_mode",runtimeMode)
            }
        }
        e.apply()
        if(text!=previous) {
            logs.record(if(stage=="blocked"||stage=="rejected")"warn" else "info","config_$stage",text)
            if(SettingsStore(c).debugHudEnabled && stage in listOf("received","applied","effective","blocked","rejected")) {
                main.post{Toast.makeText(c.applicationContext,text,Toast.LENGTH_SHORT).show()}
            }
        }
    }
    fun snapshot(c:Context,settings:SettingsStore,mode:Int,syncAt:Long,lastError:String,extra:String=""):String {
        val fmt=SimpleDateFormat("HH:mm:ss",Locale.US)
        val stamp=prefs(c).getLong("at",0L)
        val eventAt=if(stamp>0L)fmt.format(Date(stamp)) else "--"
        val sync=if(syncAt>0L)fmt.format(Date(syncAt)) else "--"
        val origin=if(settings.manualMode in 1..3)"CONTROL LOCAL" else "PORTAL / HORARIOS"
        val module=if(mode==3)when(settings.smartApp){"queue"->"TURNOS";else->"SPORTS"} else "--"
        return "2Cam Digital Signage · APK 0.4.12 DEBUG · API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Modo: ${mode(mode)} · Origen: $origin · Módulo: $module\n" +
            "Control: ARRIBA×3 = Ajustes · ABAJO×3 = Sincronizar\n" +
            "Revisión: recibida r${received(c)} / guardada r${applied(c)} / efectiva r${effective(c)} (${mode(effectiveMode(c))})\n" +
            "Últ. sync $sync · $eventAt: ${lastEvent(c)}" +
            (if(lastError.isBlank())"" else "\nERROR: ${lastError.take(120)}") +
            (if(extra.isBlank())"" else "\n${extra.take(120)}")
    }
}

/** Read-only overlay inside the app content, no SYSTEM_ALERT_WINDOW permission required. */
class DeviceDebugHud(private val activity:Activity) {
    private var view:TextView?=null
    fun update(enabled:Boolean,text:String){
        val content=activity.findViewById<FrameLayout>(android.R.id.content)?:return
        if(!enabled){view?.let{(it.parent as? ViewGroup)?.removeView(it)};return}
        val tv=view ?: TextView(activity).apply{
            setTextColor(Color.WHITE)
            textSize=13f
            setPadding(12,10,12,10)
            setBackgroundColor(0xDC06213B.toInt())
            isClickable=false
            isFocusable=false
        }.also{view=it}
        if(tv.parent !== content){
            (tv.parent as? ViewGroup)?.removeView(tv)
            content.addView(tv,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        }
        tv.text=text
        tv.bringToFront()
    }
    fun clear(){view?.let{(it.parent as? ViewGroup)?.removeView(it)};view=null}
}
