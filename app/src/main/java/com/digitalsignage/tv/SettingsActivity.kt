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
    private val authWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    override fun onDestroy(){authWorker.shutdownNow();super.onDestroy()}
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
            if(pin.length<6||!pin.all{it.isDigit()}){field.error="Usa 6 dígitos como mínimo";return@setOnClickListener}
            val button=dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            button.isEnabled=false;button.text="Verificando…";field.isEnabled=false
            authWorker.execute{
                val started=android.os.SystemClock.elapsedRealtime()
                try{
                    val valid=if(create){config.setPin(pin);true}else config.verifyPin(pin)
                    if(valid)config.prepareFormData()
                    DeviceLogs(this).record("info","settings_auth","Verificación administrativa completada","elapsed_ms=${android.os.SystemClock.elapsedRealtime()-started}")
                    runOnUiThread{
                        if(!isFinishing && !isDestroyed){
                            if(valid){dlg.dismiss();showSettings()}
                            else{field.error="PIN incorrecto";field.isEnabled=true;button.isEnabled=true;button.text="Entrar"}
                        }
                    }
                }catch(e:Exception){runOnUiThread{if(!isFinishing){field.error=e.message?:"No se pudo verificar";field.isEnabled=true;button.isEnabled=true;button.text="Reintentar"}}}
            }
        }};dlg.show()
    }
    private fun toggle(label:String,checked:Boolean,change:(Boolean)->Unit){
        root.addView(CheckBox(this).apply{text=label;isChecked=checked;textSize=18f;setTextColor(ScreenStyles.FOREGROUND);setPadding(4,8,4,8);setOnCheckedChangeListener{_,state->change(state)}})
    }
    private fun field(label:String,value:String,save:(String)->Unit){root.addView(ScreenStyles.label(this,label,17f))
        val edit=EditText(this).apply{setSingleLine(true);setText(value);setTextColor(0xFF131313.toInt());setBackgroundColor(0xFFFFFFFF.toInt());setSelectAllOnFocus(true);setPadding(20,12,20,12);textSize=18f;layoutParams=LinearLayout.LayoutParams(-1,-2)}
        root.addView(edit);root.addView(ScreenStyles.button(this,"Guardar $label"){
            try{save(edit.text.toString().trim());android.widget.Toast.makeText(this,"Guardado",android.widget.Toast.LENGTH_SHORT).show()}
            catch(e:Exception){edit.error=e.message?:"Valor inválido"}
        })
    }
    private fun showSettings(){root=ScreenStyles.column(this).apply{val dm=resources.displayMetrics;val x=maxOf((dm.widthPixels*0.06f).toInt(),(24*dm.density).toInt());val y=maxOf((dm.heightPixels*0.05f).toInt(),(16*dm.density).toInt());setPadding(x,y,x,y)}
        root.addView(ScreenStyles.label(this,"2CAM DIGITAL SIGNAGE · CONFIGURACIÓN",28f))
        root.addView(ScreenStyles.label(this,"Ajustes de este equipo, no modifican otras pantallas",16f))
        root.addView(ScreenStyles.label(this,
            "En reproducción: ARRIBA 3 veces abre este menú; ABAJO 3 veces sincroniza. " +
            "También funciona MENU cuando el control dispone de esa tecla.",16f))
        root.addView(ScreenStyles.label(this,"Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${LegacyProfile.description()}",14f))
        root.addView(ScreenStyles.label(this,"Modo seleccionado: "+arrayOf("Según portal/horarios","Cartelera","Overlay","Turnos","Marcador deportivo")[config.manualMode],18f))
        root.addView(ScreenStyles.label(this,"El botón verde indica el modo seleccionado.",15f))
        listOf("Automático (portal)" to 0,"Cartelera" to 1,"Overlay" to 2,"Turnos" to 3,"Marcador deportivo" to 4).forEach{ (title,value)->
            root.addView(ScreenStyles.button(this,title,config.manualMode==value){
                val error=if(value==0)null else config.activationError(value)
                if(error!=null){AlertDialog.Builder(this).setTitle("No se puede activar el modo").setMessage(error).setPositiveButton("Aceptar",null).show()}
                else{config.manualMode=value;showSettings()}
            })
        }
        root.addView(ScreenStyles.button(this,"Seleccionar modo local (para pruebas)"){
            val choices=arrayOf("Automático (según portal y horarios)","Cartelera","Overlay","Turnos","Marcador deportivo")
            AlertDialog.Builder(this).setTitle("Modo principal")
                .setSingleChoiceItems(choices,config.manualMode){dialog,selected->
                    val error=if(selected==0)null else config.activationError(selected)
                    if(error!=null){android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show();return@setSingleChoiceItems}
                    config.manualMode=selected;dialog.dismiss()
                    AlertDialog.Builder(this).setMessage("Modo guardado. Vuelve al reproductor para aplicarlo.")
                        .setPositiveButton("Aceptar",null).show()
                }.setNegativeButton("Cancelar",null).show()
        })
        root.addView(ScreenStyles.label(this,"Modo principal configurado: "+arrayOf("","Cartelera","Overlay","Turnos","Marcador deportivo")[config.primaryMode],16f))
        root.addView(ScreenStyles.button(this,"Abrir diagnóstico de soporte"){
            startActivity(Intent(this,DiagnosticHomeActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Buscar actualización de APK ahora"){AppUpdater.check(this,config,true)})
        root.addView(ScreenStyles.button(this,"Instalar APK ya descargada"){AppUpdater.installCached(this)})
        root.addView(ScreenStyles.label(this,"Actualizaciones: "+when(config.updatePolicy){0->"Manual";1->"Avisar";else->"Descargar y confirmar"}+" · "+config.updateChannel,15f))
        root.addView(ScreenStyles.button(this,"Diagnóstico y logs locales"){
            startActivity(Intent(this,DiagnosticsActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Probar Overlay ahora"){
            val error=config.activationError(2)
            when {
                error!=null->AlertDialog.Builder(this).setTitle("Overlay pendiente").setMessage(error).setPositiveButton("Aceptar",null).show()
                !AndroidCompat.canOverlay(this)->AlertDialog.Builder(this).setMessage("Autoriza la superposición Android antes de probar Overlay").setPositiveButton("Aceptar",null).show()
                else->{AndroidCompat.startServiceCompat(this,Intent(this,OverlayService::class.java).setAction(OverlayService.ACTION_PREVIEW))}
            }
        })
        root.addView(ScreenStyles.button(this,"Sports directo LAN · enlazar control"){
            startActivity(Intent(this,SportsActivity::class.java))
        })
        root.addView(ScreenStyles.button(this,"Turnos LAN · enlazar APK Turnos como visor"){
            startActivity(Intent(this,TurnosLinkActivity::class.java))
        })
        root.addView(ScreenStyles.label(this,"Formatos centrales: lateral ${config.mediaFormatPercent("side")}% del ancho · inferior ${config.mediaFormatPercent("bottom")}% del alto. Se administran en Medios, no por pantalla.",15f))
        val snapshot=config.publicSnapshot()
        var group=""
        for(def in config.schemaFields()){
            val key=def.getString("key");val label=def.getString("label");val section=def.getString("group")
            if(group!=section){root.addView(ScreenStyles.label(this,section,23f));group=section}
            fun save(value:Any){val next=config.validateSettings(config.publicSnapshot().put(key,value));config.requireActivationChange(next);check(config.applySnapshot(next))}
            when(def.getString("type")){
                "bool"->toggle(label,snapshot.getBoolean(key)){save(it)}
                "mask"->for(mode in 1..4){val bit=1 shl (mode-1);toggle(config.schema.getJSONObject("modes").getString(mode.toString()),snapshot.getInt(key) and bit!=0){enabled->
                    try{val old=config.enabledModes;save(if(enabled)old or bit else old and bit.inv())}catch(e:Exception){android.widget.Toast.makeText(this,e.message,android.widget.Toast.LENGTH_LONG).show()};showSettings()
                }}
                "playlist"->{val choices=config.playlistChoices(def);val current=snapshot.getInt(key)
                    root.addView(ScreenStyles.button(this,label+": "+(choices.find{it.first==current}?.second?:"#$current · No disponible")){
                        AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(choices.map{it.second}.toTypedArray(),choices.indexOfFirst{it.first==current}){dialog,n->
                            save(choices[n].first);dialog.dismiss();showSettings()
                        }.setNegativeButton("Cancelar",null).show()
                    })
                }
                "enum"->{val options=def.getJSONObject("options");val ids=options.keys().asSequence().toList()
                    root.addView(ScreenStyles.button(this,label+": "+options.getString(snapshot.get(key).toString())){
                        AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(ids.map{options.getString(it)}.toTypedArray(),ids.indexOf(snapshot.get(key).toString())){dialog,n->
                            try{save(if(def.get("default") is Int)ids[n].toInt() else ids[n]);dialog.dismiss();showSettings()}catch(e:Exception){android.widget.Toast.makeText(this,e.message,android.widget.Toast.LENGTH_LONG).show()}
                        }.setNegativeButton("Cancelar",null).show()
                    })
                }
                else->field(label,snapshot.get(key).toString()){save(it.toInt())}
            }
        }
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
        val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false;addView(root)}
        setContentView(scroll)
    }
}


