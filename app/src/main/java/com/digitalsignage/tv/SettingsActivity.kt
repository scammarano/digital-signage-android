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
        root.addView(ScreenStyles.label(this,"DIGITAL SIGNAGE · CONFIGURACIÓN",28f))
        root.addView(ScreenStyles.label(this,"Ajustes de este equipo, no modifican otras pantallas",16f))
        root.addView(ScreenStyles.label(this,"Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${LegacyProfile.description()}",14f))
        root.addView(ScreenStyles.button(this,"Seleccionar modo local (para pruebas)"){
            val choices=arrayOf("Automático (según portal y horarios)","Cartelera","Overlay","Pantalla inteligente")
            AlertDialog.Builder(this).setTitle("Modo principal")
                .setSingleChoiceItems(choices,config.manualMode){dialog,selected->
                    config.manualMode=selected;dialog.dismiss()
                    AlertDialog.Builder(this).setMessage("Modo guardado. Vuelve al reproductor para aplicarlo.")
                        .setPositiveButton("Aceptar",null).show()
                }.setNegativeButton("Cancelar",null).show()
        })
        root.addView(ScreenStyles.label(this,"Modo principal remoto: "+arrayOf("","Cartelera","Overlay","Pantalla inteligente")[config.primaryMode],16f))
        root.addView(ScreenStyles.button(this,"Buscar actualización de APK ahora"){AppUpdater.check(this,config,true)})
        root.addView(ScreenStyles.button(this,"Instalar APK ya descargada"){AppUpdater.installCached(this)})
        root.addView(ScreenStyles.label(this,"Actualizaciones: "+when(config.updatePolicy){0->"Manual";1->"Avisar";else->"Descargar y confirmar"}+" · "+config.updateChannel,15f))
        root.addView(ScreenStyles.button(this,"Diagnóstico y logs locales"){
            startActivity(Intent(this,DiagnosticsActivity::class.java))
        })
        toggle("Arrancar automáticamente cuando Android lo permita",config.autoStart){config.autoStart=it}
        toggle("Mostrar logo al iniciar la app",config.splash){config.splash=it}
        toggle("Mantener último contenido offline",config.offlinePlayback){config.offlinePlayback=it}
        toggle("Reproducir audio en videos de cartelera",config.videoAudio){config.videoAudio=it}
        toggle("Habilitar Cartelera",config.enabledModes and 1!=0){config.enabledModes=if(it)config.enabledModes or 1 else config.enabledModes and 1.inv()}
        toggle("Habilitar Overlay",config.enabledModes and 2!=0){config.enabledModes=if(it)config.enabledModes or 2 else config.enabledModes and 2.inv()}
        toggle("Habilitar Pantalla inteligente",config.enabledModes and 4!=0){config.enabledModes=if(it)config.enabledModes or 4 else config.enabledModes and 4.inv()}
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
