package com.digitalsignage.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Recovery-first launcher: NO playback, sync, service, or update is auto-triggered.
 * Each network/media step is deliberately started by the user's remote control.
 */
class DiagnosticHomeActivity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var logs:DeviceLogs
    private lateinit var settings:SettingsStore
    private val status=linkedMapOf<String,Int>()
    private var busy=false
    private var detail="Selecciona una prueba. Ninguna se ejecuta automáticamente."
    private var candidate:JSONObject?=null

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        settings=SettingsStore(this)
        logs=DeviceLogs(this)
        // Keep manual diagnostics on even when previous app settings were migrated.
        settings.diagnosticMode=true
        logs.record("info","manual_launcher","Menú de diagnóstico iniciado","v0.4.8 API ${android.os.Build.VERSION.SDK_INT}")
        render()
    }
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
    private fun key():String?=SecureToken(this).load()
    private fun api():SignageApi=SignageApi(settings.portalUrl,
        key()?:throw IllegalStateException("Primero debes emparejar esta pantalla"))
    private fun test(id:String,name:String,action:()->String){
        if(busy)return
        busy=true
        detail="Ejecutando: $name"
        render()
        logs.record("info","diag_begin",name)
        worker.execute {
            var message=""
            var ok=false
            try {
                message=action()
                ok=true
                logs.record("info","diag_ok",name,message)
            }catch(error:Throwable){
                message="${error.javaClass.simpleName}: ${error.message?:"Sin detalle"}"
                logs.record("error","diag_failed",name,message+"\n"+error.stackTrace.take(8).joinToString("\n"))
            }
            ui.post{
                busy=false
                status[id]=if(ok)1 else 2
                detail=(if(ok)"CORRECTO: " else "ERROR: ")+name+"\n"+message
                render()
            }
        }
    }
    private fun pair(){
        if(busy)return
        val field=EditText(this).apply{
            hint="Seis dígitos"
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            isSingleLine=true
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        AlertDialog.Builder(this).setTitle("Vincular 2Cam Digital Signage")
            .setMessage("Genera un código desde Pantallas en el portal. Aquí no arrancará el reproductor.")
            .setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Emparejar"){_,_->
                val code=field.text.toString().trim()
                if(!Regex("[0-9]{6}").matches(code)){
                    detail="El código debe tener exactamente 6 dígitos.";render()
                    return@setPositiveButton
                }
                test("pair","Emparejamiento"){
                    val result=SignageApi(settings.portalUrl,null).pair(code)
                    SecureToken(this).save(result.getString("token"))
                    "Token guardado localmente; dispositivo ID ${result.optInt("device_id")}. Sin procesos automáticos."
                }
            }.show()
    }
    private fun render(){
        val root=ScreenStyles.column(this)
        root.addView(ScreenStyles.label(this,"2CAM DIGITAL SIGNAGE",28f))
        root.addView(ScreenStyles.label(this,"v0.4.8 · DIAGNÓSTICO DE SOPORTE MANUAL · Android API ${android.os.Build.VERSION.SDK_INT}",15f))
        val connected=key()!=null
        root.addView(ScreenStyles.label(this,if(connected)"Vinculación local: GUARDADA" else "Vinculación local: NO EXISTE",17f))
        root.addView(ScreenStyles.label(this,"No hay reproducción ni sincronización en segundo plano. Ejecuta cada botón en orden.",16f))
        val stateLabel=ScreenStyles.label(this,detail,16f).apply{
            setBackgroundColor(if(detail.startsWith("ERROR"))ScreenStyles.RED else 0xFF123858.toInt())
            setPadding(18,15,18,15)
        }
        root.addView(stateLabel)
        fun step(id:String,title:String,action:()->Unit){
            val state=status[id]?:0
            val tag=when(state){1->"✓ ";2->"✕ ";else->"○ "}
            val btn=ScreenStyles.statusButton(this,tag+title,state,action)
            btn.isEnabled=!busy
            root.addView(btn)
        }
        step("pair","1. Emparejar (solo si falta)"){pair()}
        step("token","2. Comprobar token local"){
            test("token","Comprobar token") {
                val saved=key()?:throw IllegalStateException("Token ausente o no descifrable")
                "Token recuperado correctamente (${saved.length} caracteres)."
            }
        }
        step("logs","3. Enviar logs al portal") {
            test("logs","Envío de logs"){
                val sent=logs.upload(api())
                "Eventos confirmados por el servidor: $sent"
            }
        }
        step("connection","4. Probar conexión y autorización"){
            test("connection","Consulta de control remoto"){
                val reply=api().control()
                "Servidor respondió. Revisión solicitada: ${reply.optLong("requested_version",0)}"
            }
        }
        step("config","5. Consultar / aplicar configuración"){
            test("config","Configuración remota"){
                val applied=DeviceConfigSync(settings,api()).poll()
                "Configuración ${if(applied)"confirmada" else "pendiente"}. Modo principal: ${settings.primaryMode}."
            }
        }
        step("manifest","6. Descargar programación (sin reproducir)"){
            test("manifest","Manifiesto remoto"){
                val reply=api().manifest()
                candidate=reply
                "Recibido. Listas: ${reply.optJSONArray("schedules")?.length()?:0}. Fallback: ${reply.has("fallback")}."
            }
        }
        step("cache","7. Descargar medios y activar caché"){
            test("cache","Caché de medios"){
                val manifest=candidate ?: api().manifest().also{candidate=it}
                val count=CacheManager(this,settings).sync(api(),manifest){progress->
                    logs.record("info","cache_progress",progress)
                }
                "Archivos validados: $count. Manifiesto local disponible."
            }
        }
        step("player","8. ABRIR REPRODUCTOR (última prueba)"){
            try{
                val cache=CacheManager(this,settings)
                require(cache.active()!=null){"Primero completa el paso 7 para guardar la programación."}
                status["player"]=1
                logs.record("info","diag_player","Usuario inicia reproductor manualmente")
                detail="Abriendo reproductor. Si se cierra, vuelve a iniciar 2Cam Digital Signage y envía los logs."
                render()
                startActivity(Intent(this,MainActivity::class.java))
            }catch(e:Exception){status["player"]=2;detail="ERROR: Reproductor\n${e.message}";render()}
        }
        root.addView(ScreenStyles.button(this,"Volver a arranque automático"){
            settings.diagnosticMode=false
            startActivity(Intent(this,MainActivity::class.java))
            finish()
        })
        root.addView(ScreenStyles.button(this,"Ver logs almacenados"){
            startActivity(Intent(this,DiagnosticsActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Configuración local / elegir modo"){
            startActivity(Intent(this,SettingsActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Buscar actualización manual de la APK"){
            AppUpdater.check(this,settings,true)
        })
        root.addView(ScreenStyles.button(this,"Desactivar reproducción hasta cerrar diagnóstico",true){
            Toast.makeText(this,"La reproducción automática está deshabilitada en esta versión.",Toast.LENGTH_LONG).show()
        })
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(root)}
        setContentView(scroll)
    }
}
