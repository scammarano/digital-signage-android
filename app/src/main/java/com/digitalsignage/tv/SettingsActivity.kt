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
    private fun field(label:String,value:String,save:(String)->Unit){root.addView(ScreenStyles.button(this,"$label: $value"){fieldDialog(label,value,save)})}
    private var selectedTab="General"
    private fun showSettings(){
        val shell=ScreenStyles.column(this).apply{
            val dm=resources.displayMetrics
            setPadding((dm.widthPixels*.07f).toInt(),(dm.heightPixels*.05f).toInt(),(dm.widthPixels*.07f).toInt(),(dm.heightPixels*.05f).toInt())
        }
        shell.addView(ScreenStyles.label(this,"2CAM · CONFIGURACIÓN · "+BuildConfig.VERSION_NAME,24f))
        shell.addView(ScreenStyles.label(this,DeviceNetwork.label(),18f))
        val definitions=config.schema.getJSONArray("groups")
        val groups=(0 until definitions.length()).map{definitions.getString(it)}+listOf("Equipo y panel LAN")
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val bar=android.widget.HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=true;addView(tabs)}
        groups.forEach{g->tabs.addView(ScreenStyles.button(this,g,g==selectedTab){selectedTab=g;showSettings()}.apply{
            tag="tab:$g";layoutParams=LinearLayout.LayoutParams(-2,-2).apply{setMargins(5,0,5,8)}
        })}
        shell.addView(bar)
        root=ScreenStyles.column(this).apply{setPadding(12,8,12,12)}
        val centered=android.widget.FrameLayout(this)
        val dm=resources.displayMetrics
        centered.addView(root,android.widget.FrameLayout.LayoutParams(minOf((820*dm.density).toInt(),(dm.widthPixels*.82f).toInt()),-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        shell.addView(ScrollView(this).apply{isFillViewport=true;addView(centered)},LinearLayout.LayoutParams(-1,0,1f))
        fun action(title:String,run:()->Unit){root.addView(ScreenStyles.button(this,title,action=run).apply{minHeight=(52*dm.density).toInt();textSize=20f})}
        if(selectedTab=="General"){
            val manual=config.manualMode in 1..4
            root.addView(ScreenStyles.label(this,"Control del modo",23f))
            action(if(manual)"○ Automático · usar modo del portal" else "● Automático · portal activo"){
                config.manualMode=0;DeviceLogs(this).record("info","mode_source","Control automático del portal");showSettings()
            }
            action(if(manual)"● Manual local · "+DeviceDebug.mode(config.manualMode) else "○ Manual local · seleccionar modo"){
                val names=arrayOf("Cartelera","Overlay","Turnos","Marcador deportivo")
                AlertDialog.Builder(this).setTitle("Seleccionar único modo local")
                    .setSingleChoiceItems(names,if(manual)config.manualMode-1 else -1){dialog,index->
                        val mode=index+1
                        val error=config.activationError(mode)
                        if(error!=null)AlertDialog.Builder(this).setMessage(error).setPositiveButton("Aceptar",null).show()
                        else{config.manualMode=mode;DeviceLogs(this).record("info","mode_source","Control manual", "mode=$mode");dialog.dismiss();showSettings()}
                    }.setNegativeButton("Cancelar",null).show()
            }
            root.addView(ScreenStyles.label(this,"Modo configurado en portal: "+DeviceDebug.mode(config.primaryMode)+
                "\nLos permisos de modo se administran en el portal; aquí eliges solo quién controla esta pantalla.",17f))
        }
        val snapshot=config.publicSnapshot()
        for(def in config.schemaFields().filter{it.getString("group")==selectedTab &&
            !(selectedTab=="General" && it.getString("key") in setOf("enabled_modes","primary_mode"))}){
            val key=def.getString("key");val label=def.getString("label")
            fun save(value:Any){val next=config.validateSettings(config.publicSnapshot().put(key,value));config.requireActivationChange(next);check(config.applySnapshot(next))}
            when(def.getString("type")){
                "bool"->toggle(if(key=="auto_start")"Iniciar al arrancar Fire OS" else label,
                    if(key=="auto_start")config.bootAutoStartEnabled() else snapshot.getBoolean(key)){enabled->
                    try {
                        if(key=="auto_start"){
                            check(config.setBootAutoStartFromTv(enabled))
                            DeviceLogs(this).record("info","boot_setting",
                                if(enabled)"Inicio automático autorizado desde TV" else "Inicio automático bloqueado desde TV")
                        }else save(enabled)
                    }catch(e:Exception){android.widget.Toast.makeText(this,e.message,1).show()}
                    showSettings()
                }
                "mask"->for(mode in 1..4){val bit=1 shl(mode-1);toggle(DeviceDebug.mode(mode),snapshot.getInt(key) and bit!=0){enabled->
                    try{save(if(enabled)config.enabledModes or bit else config.enabledModes and bit.inv())}catch(e:Exception){android.widget.Toast.makeText(this,e.message,1).show()};showSettings()
                }}
                "playlist"->{val choices=config.playlistChoices(def);val current=snapshot.getInt(key)
                    action(label+": "+(choices.find{it.first==current}?.second?:"#$current · No disponible")){
                        AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(choices.map{it.second}.toTypedArray(),choices.indexOfFirst{it.first==current}){dialog,n->
                            try{save(choices[n].first);dialog.dismiss();showSettings()}catch(e:Exception){android.widget.Toast.makeText(this,e.message,1).show()}
                        }.setNegativeButton("Cancelar",null).show()
                    }
                    action("▶ Probar "+label){startActivity(Intent(this,PlaylistPreviewActivity::class.java).putExtra("key",key))}
                }
                "enum"->{val options=def.getJSONObject("options");val ids=options.keys().asSequence().toList()
                    action(label+": "+options.getString(snapshot.get(key).toString())){
                        AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(ids.map{options.getString(it)}.toTypedArray(),ids.indexOf(snapshot.get(key).toString())){dialog,n->
                            try{save(if(def.get("default") is Int)ids[n].toInt() else ids[n]);dialog.dismiss();showSettings()}catch(e:Exception){android.widget.Toast.makeText(this,e.message,1).show()}
                        }.setNegativeButton("Cancelar",null).show()
                    }
                }
                else->field(label,snapshot.get(key).toString()){save(it.toInt());showSettings()}
            }
        }
        if(selectedTab=="General")root.addView(ScreenStyles.label(this,
            "Inicio automático: solo tras reiniciar Fire OS. Encender la TV sin reiniciar Fire no genera evento de arranque. Abrir 2Cam manualmente puede iniciar el modo elegido.",17f))
        if(selectedTab=="Overlay"){
            root.addView(ScreenStyles.label(this,"Cada aparición reproduce la lista con sus tiempos y transiciones durante toda la duración visible. Después espera el intervalo configurado. Solo imágenes.",18f))
            action("▶ Probar Overlay sobre otras aplicaciones"){
                val error=config.activationError(2)
                if(error!=null||!AndroidCompat.canOverlay(this))AlertDialog.Builder(this).setMessage(error?:"Autoriza Mostrar sobre otras aplicaciones").setPositiveButton("Aceptar",null).show()
                else AndroidCompat.startServiceCompat(this,Intent(this,OverlayService::class.java).setAction(OverlayService.ACTION_PREVIEW))
            }
        }
        if(selectedTab=="Actualizaciones"){
            action("Buscar actualización de APK ahora"){AppUpdater.check(this,config,true)}
            action("Instalar APK ya descargada"){AppUpdater.installCached(this)}
            root.addView(ScreenStyles.label(this,"Si falla: revisa Diagnóstico y logs. Downloader permite abrir la misma descarga; no borres la aplicación.",17f))
        }
        if(selectedTab=="Diagnóstico"){
            action("Abrir diagnóstico de soporte"){startActivity(Intent(this,DiagnosticHomeActivity::class.java))}
            action("Diagnóstico y logs locales"){startActivity(Intent(this,DiagnosticsActivity::class.java))}
            action("Sports directo LAN · enlazar control"){startActivity(Intent(this,SportsActivity::class.java))}
        }
        if(selectedTab=="Equipo y panel LAN"){
            root.addView(ScreenStyles.label(this,DeviceNetwork.label()+"\nPanel local: "+(LocalAdminService.runningUrl.ifBlank{"Cerrado · puerto "+config.localAdminPort}),20f))
            field("URL del portal HTTPS",config.portalUrl){v->require(v.startsWith("https://")&&v.endsWith("/")&&java.net.URL(v).host.isNotBlank());config.portalUrl=v}
            field("Puerto del panel LAN (1024–65535)",config.localAdminPort.toString()){config.localAdminPort=it.toInt();showSettings()}
            action("Iniciar panel web local durante 30 min"){
                config.setLocalAdminEnabled(true);AndroidCompat.startServiceCompat(this,Intent(this,LocalAdminService::class.java).setAction(LocalAdminService.ACTION_START))
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showLanAccessCard()
                },700)
            }
            action("Cerrar administración LAN"){startService(Intent(this,LocalAdminService::class.java).setAction(LocalAdminService.ACTION_STOP))}
            action("Autorizar superposición Android"){if(android.os.Build.VERSION.SDK_INT>=23)try{startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")))}catch(_:Exception){}else android.widget.Toast.makeText(this,"Permiso administrado por Fire OS",1).show()}
            action("Cambiar PIN"){fieldDialog("Nuevo PIN de 6 o más dígitos",""){config.setPin(it)}}
            action("Respaldar configuración local"){config.backupSnapshot("manual-from-tv");android.widget.Toast.makeText(this,"Respaldo sin PIN ni token creado",1).show()}
            action("Desvincular dispositivo"){AlertDialog.Builder(this).setMessage("¿Desvincular esta pantalla?").setNegativeButton("Cancelar",null).setPositiveButton("Desvincular"){_,_->SecureToken(this).clear();stopService(Intent(this,OverlayService::class.java));finish()}.show()}
        }
        shell.addView(ScreenStyles.button(this,"Volver al reproductor"){finish()})
        setContentView(shell)
        tabs.getChildAt(groups.indexOf(selectedTab)).apply{requestFocus();post{bar.smoothScrollTo(left,0)}}
    }
    private fun showLanAccessCard(){
        val content=ScreenStyles.column(this).apply{setPadding(36,24,36,28)}
        fun line(value:String,sp:Float){
            content.addView(ScreenStyles.label(this,value,sp).apply{
                gravity=Gravity.CENTER;setPadding(12,12,12,12)
            })
        }
        val ips=DeviceNetwork.addresses()
        line("PANEL WEB LOCAL",34f)
        line("IP PRINCIPAL",23f)
        line(ips.firstOrNull()?:"Sin conexión IPv4",40f)
        line("DIRECCIÓN PARA NAVEGADOR",22f)
        line(LocalAdminService.runningUrl.ifBlank{"Iniciando panel local…"},30f)
        line("CÓDIGO TEMPORAL",22f)
        line(LocalAdminService.pairingCode.ifBlank{"No disponible"},48f)
        if(ips.size>1)line("Otras IP: "+ips.drop(1).joinToString(" · "),18f)
        if(LocalAdminService.lastError.isNotBlank())line("Estado: "+LocalAdminService.lastError,20f)
        val dialog=AlertDialog.Builder(this)
            .setView(ScrollView(this).apply{addView(content)})
            .setPositiveButton("Cerrar",null).create()
        dialog.show()
        val metrics=resources.displayMetrics
        dialog.window?.setLayout((metrics.widthPixels*0.90f).toInt(),(metrics.heightPixels*0.84f).toInt())
    }
    private fun fieldDialog(label:String,value:String,save:(String)->Unit){
        val edit=EditText(this).apply{setSingleLine(true);setText(value);textSize=24f;isFocusable=true;isFocusableInTouchMode=true;setSelectAllOnFocus(true);inputType=if(label.contains("HTTPS"))1 else 2;setPadding(24,20,24,20)}
        val dialog=AlertDialog.Builder(this).setTitle(label).setView(edit).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener{edit.requestFocus();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            try{save(edit.text.toString().trim());dialog.dismiss()}catch(e:Exception){edit.error=e.message?:"Valor inválido"}
        }}
        dialog.show()
    }
}

