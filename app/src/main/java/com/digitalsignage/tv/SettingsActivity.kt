package com.digitalsignage.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** PIN-protected settings. TV remote can open with MENU from player. */
class SettingsActivity:Activity(){
    private lateinit var config:SettingsStore
    private lateinit var root:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);config=SettingsStore(this)
        if(config.hasPin())askPin(false)else askPin(true)
    }
    private fun askPin(create:Boolean){
        val field=EditText(this).apply{hint=if(create)"Nuevo PIN de al menos 6 dígitos" else "PIN administrativo";inputType=2}
        val dlg=AlertDialog.Builder(this).setTitle(if(create)"Crear PIN de administración" else "Acceso a configuración")
         .setView(field).setCancelable(false).setNegativeButton("Cancelar"){_,_->finish()}
         .setPositiveButton(if(create)"Guardar" else "Entrar",null).create()
        dlg.setOnShowListener{dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val pin=field.text.toString()
            if(create){if(pin.length<6||!pin.all{it.isDigit()}){field.error="Usa 6 dígitos como mínimo";return@setOnClickListener};config.setPin(pin);dlg.dismiss();showSettings()}
            else if(config.verifyPin(pin)){dlg.dismiss();showSettings()}else{field.error="PIN incorrecto"}
        }};dlg.show()
    }
    private fun toggle(label:String,checked:Boolean,change:(Boolean)->Unit){
        root.addView(CheckBox(this).apply{text=label;isChecked=checked;textSize=18f;setTextColor(ScreenStyles.FOREGROUND);setPadding(4,8,4,8);setOnCheckedChangeListener{_,state->change(state)}})
    }
    private fun field(label:String,value:String,save:(String)->Unit){root.addView(ScreenStyles.label(this,label,17f))
        val edit=EditText(this).apply{setSingleLine(true);setText(value);setTextColor(0xFF131313.toInt());setBackgroundColor(0xFFFFFFFF.toInt());setSelectAllOnFocus(true)}
        root.addView(edit);root.addView(ScreenStyles.button(this,"Guardar $label"){
            try{save(edit.text.toString().trim());android.widget.Toast.makeText(this,"Guardado",android.widget.Toast.LENGTH_SHORT).show()}
            catch(e:Exception){edit.error=e.message?:"Valor inválido"}
        })
    }
    private fun showSettings(){root=ScreenStyles.column(this)
        root.addView(ScreenStyles.label(this,"2CAM DIGITAL SIGNAGE · CONFIGURACIÓN",28f))
        root.addView(ScreenStyles.label(this,"Ajustes de este equipo, no modifican otras pantallas",16f))
        root.addView(ScreenStyles.label(this,"Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${LegacyProfile.description()}",14f))
        root.addView(ScreenStyles.label(this,"Modo seleccionado: "+arrayOf("Según portal/horarios","Cartelera","Overlay","Pantalla inteligente")[config.manualMode],18f))
        root.addView(ScreenStyles.label(this,"El botón verde indica el modo seleccionado.",15f))
        listOf("Automático (portal)" to 0,"Cartelera" to 1,"Overlay" to 2,"Pantalla inteligente" to 3).forEach{ (title,value)->
            root.addView(ScreenStyles.button(this,title,config.manualMode==value){
                config.manualMode=value
                showSettings()
            })
        }
        root.addView(ScreenStyles.button(this,"Seleccionar modo local (para pruebas)"){
            val choices=arrayOf("Automático (según portal y horarios)","Cartelera","Overlay","Pantalla inteligente")
            AlertDialog.Builder(this).setTitle("Modo principal")
                .setSingleChoiceItems(choices,config.manualMode){dialog,selected->
                    config.manualMode=selected;dialog.dismiss()
                    AlertDialog.Builder(this).setMessage("Modo guardado. Vuelve al reproductor para aplicarlo.")
                        .setPositiveButton("Aceptar",null).show()
                }.setNegativeButton("Cancelar",null).show()
        })
        root.addView(ScreenStyles.label(this,"Modo principal configurado: "+arrayOf("","Cartelera","Overlay","Pantalla inteligente")[config.primaryMode],16f))
        root.addView(ScreenStyles.button(this,"Abrir diagnóstico de soporte"){
            startActivity(Intent(this,DiagnosticHomeActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Buscar actualización de APK ahora"){AppUpdater.check(this,config,true)})
        root.addView(ScreenStyles.button(this,"Instalar APK ya descargada"){AppUpdater.installCached(this)})
        root.addView(ScreenStyles.label(this,"Actualizaciones: "+when(config.updatePolicy){0->"Manual";1->"Avisar";else->"Descargar y confirmar"}+" · "+config.updateChannel,15f))
        root.addView(ScreenStyles.button(this,"Diagnóstico y logs locales"){
            startActivity(Intent(this,DiagnosticsActivity::class.java))
        })
        root.addView(ScreenStyles.label(this,"CARTELERA",24f))
        toggle("Mostrar información temporal sobre contenido",config.signageShowHud){config.signageShowHud=it}
        field("Duración de información (0–60 seg)",config.signageHudSeconds.toString()){config.signageHudSeconds=it.toInt()}
        root.addView(ScreenStyles.label(this,"OVERLAY (superposición)",24f))
        fun select(title:String,current:String,options:List<Pair<String,String>>,save:(String)->Unit){
            root.addView(ScreenStyles.button(this,"$title: "+(options.find{it.first==current}?.second?:current)){
                val labels=options.map{it.second}.toTypedArray()
                android.app.AlertDialog.Builder(this).setTitle(title)
                    .setSingleChoiceItems(labels,options.indexOfFirst{it.first==current}){dialog,which->
                        save(options[which].first);dialog.dismiss();showSettings()
                    }.setNegativeButton("Cancelar",null).show()
            })
        }
        select("Posición",config.overlayLayout,listOf(
            "bottom" to "Franja inferior","top" to "Franja superior",
            "left" to "Franja izquierda","right" to "Franja derecha",
            "fullscreen" to "Pantalla completa temporal",
            "half_horizontal" to "Media pantalla horizontal",
            "half_vertical" to "Media pantalla vertical"
        )){config.overlayLayout=it}
        field("Intervalo Overlay (60–86400 seg)",config.overlayIntervalSec.toString()){config.overlayIntervalSec=it.toInt()}
        field("Duración Overlay (3–120 seg)",config.overlayDurationSec.toString()){config.overlayDurationSec=it.toInt()}
        field("Ancho franja Overlay (%)",config.overlayWidthPct.toString()){config.overlayWidthPct=it.toInt()}
        field("Alto franja Overlay (%)",config.overlayHeightPct.toString()){config.overlayHeightPct=it.toInt()}
        field("Opacidad Overlay (%)",config.overlayOpacity.toString()){config.overlayOpacity=it.toInt()}
        root.addView(ScreenStyles.label(this,"PANTALLA INTELIGENTE",24f))
        root.addView(ScreenStyles.button(this,"SPORTS LOCAL · tablet directa al Fire (sin PC)"){
            startActivity(Intent(this,SportsActivity::class.java))
        })
        select("Aplicación",config.smartApp,listOf("queue" to "Sala de espera / Turnos","scoreboard" to "Marcador / Puntos")){config.smartApp=it}
        field("Área superior inteligente (%)",config.smartHeaderPct.toString()){config.smartHeaderPct=it.toInt()}
        field("Refrescar Turnos / Marcador (seg)",config.smartPollSec.toString()){config.smartPollSec=it.toInt()}
        toggle("Aviso sonoro al llamar turno",config.smartSound){config.smartSound=it}
        root.addView(ScreenStyles.label(this,"GENERAL",24f))
        select("Modo principal",config.primaryMode.toString(),listOf("1" to "Cartelera","2" to "Overlay","3" to "Pantalla inteligente")){choice->
            val mode=choice.toInt()
            if(config.enabledModes and (1 shl (mode-1)) !=0)config.primaryMode=mode
            else android.widget.Toast.makeText(this,"Habilita primero este modo",android.widget.Toast.LENGTH_LONG).show()
        }
        select("Actualización APK",config.updatePolicy.toString(),listOf("0" to "Manual","1" to "Avisar","2" to "Descargar y pedir instalación")){config.updatePolicy=it.toInt()}
        select("Canal de actualización",config.updateChannel,listOf("stable" to "Estable","beta" to "Beta","debug" to "Pruebas")){config.updateChannel=it}
        toggle("Arrancar automáticamente cuando Android lo permita",config.autoStart){config.autoStart=it}
        toggle("Mostrar logo al iniciar la app",config.splash){config.splash=it}
        toggle("Mantener último contenido offline",config.offlinePlayback){config.offlinePlayback=it}
        toggle("Reproducir audio en videos de cartelera",config.videoAudio){config.videoAudio=it}
        fun toggleMode(bit:Int,enabled:Boolean){
            val mask=if(enabled)config.enabledModes or bit else config.enabledModes and bit.inv()
            if(mask==0){
                android.widget.Toast.makeText(this,"Mantén habilitado al menos un modo",android.widget.Toast.LENGTH_LONG).show()
            }else{
                config.enabledModes=mask
                if(mask and (1 shl (config.primaryMode-1))==0){
                    config.primaryMode=when{mask and 1!=0->1;mask and 2!=0->2;else->3}
                }
            }
            showSettings()
        }
        toggle("Habilitar Cartelera",config.enabledModes and 1!=0){toggleMode(1,it)}
        toggle("Habilitar Overlay",config.enabledModes and 2!=0){toggleMode(2,it)}
        toggle("Habilitar Pantalla inteligente",config.enabledModes and 4!=0){toggleMode(4,it)}
        toggle("Almacenar caché en espacio externo propio de la app",config.externalCache){config.externalCache=it}
        field("Límite de caché en MB",config.cacheMb.toString()){config.cacheMb=it.toInt()}
        field("Sincronización cada N minutos",config.syncMinutes.toString()){config.syncMinutes=it.toInt()}
        field("URL del portal HTTPS",config.portalUrl){v->
            require(v.startsWith("https://")&&v.endsWith("/")&&java.net.URL(v).host.isNotBlank()){"URL HTTPS inválida"};config.portalUrl=v
        }
        root.addView(ScreenStyles.button(this,"Autorizar superposición Android"){
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName"))) }
                catch (_:Exception) { AlertDialog.Builder(this).setMessage("Ajuste Overlay no disponible").setPositiveButton("Aceptar",null).show() }
            } else {
                AlertDialog.Builder(this).setMessage("Overlay heredado: configurar permisos desde Fire OS / fabricante.").setPositiveButton("Aceptar",null).show()
            }
        })
        root.addView(ScreenStyles.button(this,"Cambiar PIN"){val input=EditText(this).apply{hint="Nuevo PIN de 6 o más dígitos";inputType=2}
            AlertDialog.Builder(this).setTitle("Cambiar PIN").setView(input).setNegativeButton("Cancelar",null)
             .setPositiveButton("Guardar"){_,_->try{config.setPin(input.text.toString())}catch(_:Exception){}}.show()
        })
        field("Puerto del panel LAN (1024–65535)",config.localAdminPort.toString()){config.localAdminPort=it.toInt()}
        root.addView(ScreenStyles.button(this,"Iniciar panel web local durante 30 min"){
            config.setLocalAdminEnabled(true)
            val intent=Intent(this,LocalAdminService::class.java).setAction(LocalAdminService.ACTION_START)
            try{AndroidCompat.startServiceCompat(this,intent)}catch(e:Exception){config.setLocalAdminEnabled(false)}
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val url=LocalAdminService.runningUrl
                val code=LocalAdminService.pairingCode
                AlertDialog.Builder(this).setTitle("Administración desde laptop")
                  .setMessage(if(url.isNotBlank())"Abre en tu navegador:\n$url\n\nCódigo temporal: $code\n\nNo expongas este puerto a Internet. Se cierra automáticamente en 30 minutos."
                    else "No pudo iniciarse: ${LocalAdminService.lastError}")
                  .setPositiveButton("Cerrar",null).show()
            },650)
        })
        root.addView(ScreenStyles.button(this,"Cerrar administración LAN"){
            startService(Intent(this,LocalAdminService::class.java).setAction(LocalAdminService.ACTION_STOP))
        })
        root.addView(ScreenStyles.button(this,"Respaldar configuración local"){
            val file=config.backupSnapshot("manual-from-tv")
            AlertDialog.Builder(this).setTitle("Respaldo creado").setMessage("Respaldo local:\n${file.name}\nNo contiene PIN ni token del dispositivo.").setPositiveButton("Aceptar",null).show()
        })
        root.addView(ScreenStyles.button(this,"Desvincular dispositivo"){
            AlertDialog.Builder(this).setTitle("Desvincular dispositivo").setMessage("Requerirá un código nuevo. La programación local se conserva hasta que la borres.")
              .setNegativeButton("Cancelar",null).setPositiveButton("Desvincular"){_,_->SecureToken(this).clear();stopService(Intent(this,OverlayService::class.java));finish()}.show()
        })
        root.addView(ScreenStyles.button(this,"Cerrar configuración") {finish()})
        val scroll=ScrollView(this).apply{addView(root)}
        setContentView(scroll)
    }
}
