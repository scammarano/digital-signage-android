package com.digitalsignage.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** First real native signage player: pairing, offline images/video, layouts, local scheduling. */
class MainActivity : Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val applicationWorker=Executors.newSingleThreadExecutor()
    private lateinit var settings:SettingsStore
    private lateinit var token:SecureToken
    private lateinit var cache:CacheManager
    private var manifest:ManifestModel?=null
    private var deck:MediaDeck?=null
    private var stage:FrameLayout?=null
    private var panel:LinearLayout?=null
    private var status:TextView?=null
    private var startingSplash=false
    private var recoveryMode=false
    private var overlayRunning=false
    private var activeMode=-1
    private var playlistIdentity=""
    private var entries=JSONArray()
    private var index=0
    private var lastSync=0L
    private var lastLivePoll=0L
    private var lastError=""
    private lateinit var logs:DeviceLogs
    private var lastControlPoll=0L
    private val serviceGate by lazy{ServiceGate(this)}
    private var serviceScreen=false
    private var servicePollAt=0L
    @Volatile private var servicePollBusy=false
    private fun pollService(){
        val secret=token.load()?:return
        val now=System.currentTimeMillis();if(servicePollBusy||now-servicePollAt<15000L)return
        servicePollAt=now;servicePollBusy=true
        worker.execute{try{
            val api=SignageApi(settings.portalUrl,secret);val state=api.service()
            if(token.load()==secret){serviceGate.accept(state);ui.post{
                showMode()
                ui.postDelayed({if(!isFinishing&&token.load()==secret&&(!serviceGate.suspended||!OverlayService.visible))worker.execute{try{api.reportService(serviceGate.confirmation())}catch(_:Exception){}}},200L)
            }}
        }catch(_:Exception){}finally{servicePollBusy=false}}
    }
    private var lastDebugConfigPoll=0L
    private fun pollDebugConfig() {
        if(!settings.debugHudEnabled || token.load()==null)return
        val now=System.currentTimeMillis()
        if(now-lastDebugConfigPoll<5000L)return
        lastDebugConfigPoll=now
        worker.execute {
            try{
                val api=SignageApi(settings.portalUrl,token.load())
                DeviceConfigSync(settings,api){stage,rev,detail->
                    DeviceDebug.record(this,logs,stage,rev,detail)
                }.poll()
                try{logs.upload(api)}catch(_:Exception){}
            }catch(e:Exception){
                logs.record("warn","debug_config_poll",e.javaClass.simpleName,
                    (e.message?:"").take(180))
            }
        }
    }
    private var lastAppUpdateCheck=0L
    private var remoteControlRevision=0L
    private var syncInFlight=false
    private var lastLive=JSONObject()
    private var applicationCatalog:JSONObject?=null
    private var applicationState="Configuración central: pendiente"
    private var lastApplicationPoll=0L
    private var applicationPollBusy=false
    private var applicationBinding=""
    private fun pollApplicationConfig(){
        if(activeMode !in 3..4||serviceGate.suspended)return
        val secret=token.load()?:return;val portal=settings.portalUrl
        val app=if(activeMode==3)"turnos" else "sports"
        val binding=portal+"\n"+secret+"\n"+app
        if(binding!=applicationBinding){applicationBinding=binding;applicationCatalog=null;lastApplicationPoll=0L;applicationState="Configuración central: pendiente"}
        val now=System.currentTimeMillis();if(applicationPollBusy||now-lastApplicationPoll<15000L)return
        applicationPollBusy=true;lastApplicationPoll=now
        applicationWorker.execute{
            val store=ApplicationCatalog(this,portal,secret,app)
            var value=store.cached();var state="Configuración central: consulta fallida";var failure:Exception?=null
            val local=value
            if(local!=null)ui.post{
                if(!isFinishing&&!isDestroyed&&token.load()==secret&&settings.portalUrl==portal&&applicationBinding==binding){
                    applicationCatalog=local;applicationState="${app.uppercase()} r${local.getLong("revision")} · copia local, comprobando portal"
                    if(activeMode==if(app=="turnos")3 else 4)drawLiveBoard()
                }
            }
            try{
                val received=SignageApi(portal,secret).applicationConfig(app)
                val old=value?.getLong("revision")
                value=store.accept(received)
                state="${app.uppercase()} r${value.getLong("revision")} · guardada en dispositivo"
                if(old!=value.getLong("revision")){
                    logs.record("info","application_config_received","$app r${value.getLong("revision")}")
                    logs.record("info","application_config_applied","$app r${value.getLong("revision")}","Catálogo validado y persistido; eventos de operación independientes")
                }
            }catch(e:Exception){
                failure=e
                if(e is PortalHttpException&&e.status in listOf(401,403,422)){store.revoke();value=null;state="Configuración central: asignación no disponible (HTTP ${e.status})"}
                else if(value!=null)state="${app.uppercase()} r${value.getLong("revision")} · copia local, consulta fallida"
            }
            val result=value;val status=state
            ui.post{
                applicationPollBusy=false
                if(isFinishing||isDestroyed||token.load()!=secret||settings.portalUrl!=portal||applicationBinding!=binding)return@post
                applicationCatalog=result;applicationState=status
                failure?.let{logs.record("warn","application_config_unavailable",app,it.javaClass.simpleName)}
                if(activeMode==if(app=="turnos")3 else 4)drawLiveBoard()
            }
        }
    }
    private var mode3Header:TextView?=null
    private var lanBoard:SportsBoardView?=null
    private var liveAdCycleStartedAt=0L
    private var lastBlockedMode=""
    private var lastRenderSettings=""
    private var lastSmartApp=""
    private var lastSmartHeaderPct=0
    private var lastCallSequence=-1L
    private val debugHud by lazy {DeviceDebugHud(this)}
    private var lastSelectedMode=-1
    private fun refreshDebugHud() {
        if(!::settings.isInitialized)return
        debugHud.update(settings.debugHudEnabled,
            DeviceDebug.snapshot(this,settings,activeMode,lastSync,lastError,
                "Medios: "+(if(::cache.isInitialized)cache.countMedia() else 0)+
                    (if(activeMode in 3..4)"\n$applicationState" else "")))
    }
    private val tick=object:Runnable{override fun run(){
        try{pulse()}catch(e:Throwable){
            try{logs.record("error","tick",e.javaClass.simpleName,e.message?:"")}catch(_:Throwable){}
            showRecoveryError("Error del reproductor: ${e.message}")
        }finally{ui.postDelayed(this,1000)}
    }}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings=SettingsStore(this);settings.prepareProductionStartup();token=SecureToken(this);cache=CacheManager(this,settings);logs=DeviceLogs(this)
        logs.record("info","activity","Reproductor iniciado")
        if(logs.recentCrash()) {
            recoveryMode=true
            showRecoveryError("Se detectó un cierre inesperado. Los logs quedaron guardados para enviarlos al portal.")
            worker.execute {try{token.load()?.let{logs.upload(SignageApi(settings.portalUrl,it))}}catch(_:Exception){}}
            return
        }
        // Diagnostic build: reproduce ONLY after the user chooses it from the launcher.
        // No automatic sync, overlay, playback, or update check on this path.
        if(settings.diagnosticMode){
            logs.record("info","player","Manual player stage requested")
            reloadFromDisk()
            if(manifest==null)showOfflineError() else showMode()
            return
        }
        reloadFromDisk()
        if(settings.splash){
            startingSplash=true
            val splash=FrameLayout(this).apply{setBackgroundColor(ScreenStyles.BACKGROUND)}
            val brand=ScreenStyles.label(this,"2Cam Digital Signage",36f).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER}
            splash.addView(brand,FrameLayout.LayoutParams(-1,-1))
            setContentView(splash)
            ui.postDelayed({startingSplash=false; if(token.load()==null)showPairing() else {showMode();sync();checkAppUpdates()}; ui.post(tick)},900)
        }else{
            if(token.load()==null)showPairing() else {showMode();checkAppUpdates()}
            ui.postDelayed(tick,1000)
        }
    }
    override fun onDestroy(){ui.removeCallbacks(tick);debugHud.clear();deck?.clear();applicationWorker.shutdownNow();worker.shutdownNow();super.onDestroy()}
    override fun onResume(){super.onResume();refreshDebugHud();if(::settings.isInitialized && !recoveryMode && !settings.diagnosticMode && !startingSplash && token.load()!=null){lastSync=0L;sync();}}

    private var shortcutKey=0
    private var shortcutCount=0
    private var shortcutLastAt=0L

    /**
     * Dispatch before any focused player/overlay child consumes DPAD events.
     * Three UP key presses open PIN-protected settings.
     * Three DOWN key presses force a background manifest refresh.
     */
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0 &&
            activeMode in 1..4 &&
            (event.keyCode==KeyEvent.KEYCODE_DPAD_UP || event.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)){
            val now=android.os.SystemClock.uptimeMillis()
            shortcutCount=if(shortcutKey==event.keyCode && now-shortcutLastAt<=2500L)
                shortcutCount+1 else 1
            shortcutLastAt=now;shortcutKey=event.keyCode
            if(shortcutCount>=3){
                shortcutCount=0
                if(event.keyCode==KeyEvent.KEYCODE_DPAD_UP){
                    logs.record("info","remote_shortcut","Triple ARRIBA · Abrir configuración")
                    openSettings()
                }else{
                    logs.record("info","remote_shortcut","Triple ABAJO · Sincronización manual")
                    android.widget.Toast.makeText(this,"Buscando cambios de programación…",
                        android.widget.Toast.LENGTH_SHORT).show()
                    sync()
                }
                return true
            }
        }else if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0){
            shortcutCount=0
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean {
        if(keyCode==KeyEvent.KEYCODE_MENU){
            openSettings();return true
        }
        if(keyCode==KeyEvent.KEYCODE_DPAD_CENTER && event?.isLongPress==true){
            openSettings();return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyLongPress(keyCode:Int,event:KeyEvent?):Boolean {
        if(keyCode==KeyEvent.KEYCODE_DPAD_CENTER && activeMode in 1..4){
            openSettings();return true
        }
        return super.onKeyLongPress(keyCode,event)
    }
    private fun checkAppUpdates(force:Boolean=false) {
        if(!::settings.isInitialized || token.load()==null)return
        if(!force && settings.updatePolicy==0)return
        if(!force && System.currentTimeMillis()-lastAppUpdateCheck<30L*60L*1000L)return
        lastAppUpdateCheck=System.currentTimeMillis()
        AppUpdater.check(this,settings,force)
    }
    private fun openSettings(){startActivity(Intent(this,SettingsActivity::class.java))}
    private fun openDiagnostics(){startActivity(Intent(this,DiagnosticsActivity::class.java))}
    private fun showRecoveryError(message:String) {
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"DIGITAL SIGNAGE · RECUPERACIÓN",25f))
        box.addView(ScreenStyles.label(this,message,16f))
        box.addView(ScreenStyles.button(this,"Enviar logs y sincronizar ahora"){
            logs.acknowledgeCrash();recoveryMode=false;sync();showConnectingSafe()
        })
        box.addView(ScreenStyles.button(this,"Buscar actualización de APK"){checkAppUpdates(true)})
        box.addView(ScreenStyles.button(this,"Diagnóstico por pasos"){startActivity(Intent(this,DiagnosticHomeActivity::class.java))})
        box.addView(ScreenStyles.button(this,"Ver logs") {openDiagnostics()})
        box.addView(ScreenStyles.button(this,"Configuración / modo") {openSettings()})
        setContentView(android.widget.ScrollView(this).apply{addView(box)})
    }
    private fun showConnectingSafe(){
        if(token.load()==null)showPairing()
        else {
            val box=ScreenStyles.column(this)
            box.addView(ScreenStyles.label(this,"Sincronizando...",25f))
            box.addView(ScreenStyles.button(this,"Configuración") {openSettings()})
            box.addView(ScreenStyles.button(this,"Diagnóstico") {openDiagnostics()})
            setContentView(box)
        }
    }
    private fun reloadFromDisk(){manifest=cache.active()?.let{ManifestModel(it)};lastLive=manifest?.widget()?:JSONObject()}
    private fun showPairing(){activeMode=-1;stopOverlay();deck?.clear();deck=null
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"DIGITAL SIGNAGE TV",32f))
        box.addView(ScreenStyles.label(this,"Vincula el dispositivo desde Pantallas → Generar código",19f))
        val code=EditText(this).apply{hint="Código de 6 dígitos";inputType=2;isSingleLine=true;setTextColor(0xFF151515.toInt());setBackgroundColor(0xFFFFFFFF.toInt());setSingleLine(true)}
        box.addView(code)
        val line=ScreenStyles.label(this,"Introduce el código que aparece en el portal",16f)
        box.addView(line)
        box.addView(ScreenStyles.button(this,"Emparejar"){
            val entered=code.text.toString().trim();if(!Regex("[0-9]{6}").matches(entered)){line.text="El código debe contener 6 dígitos";return@button}
            line.text="Vinculando…"
            worker.execute{
                try {
                    logs.record("info","pairing","Intentando vinculación")
                    val reply=SignageApi(settings.portalUrl,null).pair(entered)
                    token.save(reply.getString("token"))
                    logs.record("info","pairing","Vinculación guardada","device_id=${reply.optInt("device_id")}")
                    ui.post { line.text="Vinculado. Sincronizando...";showMode();sync();checkAppUpdates() }
                }catch(e:Exception){logs.record("error","pairing",e.javaClass.simpleName,e.message?:"")
                    ui.post { line.text="Error: ${e.message}" }}
            }
        })
        box.addView(ScreenStyles.button(this,"Configuración local (PIN)"){openSettings()})
        box.addView(ScreenStyles.button(this,"Sports local · enlazar tablet"){openSettings()})
        setContentView(box)
    }
    private fun showMode(){
        if(serviceGate.suspended){
            if(!serviceScreen){stopOverlay();deck?.clear();deck=null;stage=null;activeMode=0;entries=JSONArray()
                val box=ScreenStyles.column(this);box.addView(ScreenStyles.label(this,"Servicio suspendido",30f));box.addView(ScreenStyles.label(this,serviceGate.reason.ifBlank{"Contacta al administrador"},20f));box.addView(ScreenStyles.button(this,"Configuración local (PIN)"){openSettings()});setContentView(box);serviceScreen=true
                logs.record("info","service_suspended","Servicio suspendido por el administrador")}
            return
        }
        if(serviceScreen){serviceScreen=false;activeMode=-1;lastSync=0L;logs.record("info","service_resumed","Servicio reactivado")}
        val model=manifest
        if(model==null){showConnecting();return}
        val mode=model.activeMode(settings)
        val modeError=ModeReadiness.error(settings.publicSnapshot(),model,mode,true)
        if(modeError!=null){
            if(lastBlockedMode!=modeError){
                stopOverlay();deck?.clear();deck=null;stage=null;activeMode=0
                val box=ScreenStyles.column(this)
                box.addView(ScreenStyles.label(this,"Modo pendiente de configuración",25f))
                box.addView(ScreenStyles.label(this,modeError,18f))
                box.addView(ScreenStyles.button(this,"Configurar playlist / modo"){openSettings()})
                box.addView(ScreenStyles.button(this,"Sincronizar catálogo"){sync()})
                setContentView(box);lastBlockedMode=modeError
                DeviceDebug.record(this,logs,"blocked",settings.remoteRevision,modeError)
            }
            return
        }
        lastBlockedMode=""
        val renderSettings=settings.publicSnapshot().toString()
        if(mode==activeMode && lastRenderSettings==renderSettings && (stage!=null || mode==2))return
        lastRenderSettings=renderSettings;liveAdCycleStartedAt=System.currentTimeMillis()
        activeMode=mode;playlistIdentity="";deck?.clear();deck=null;stage=null;mode3Header=null;lanBoard=null
        when(mode){1->showSignage(false);2->showOverlayControl();3,4->showSignage(true);else->showDisabled()}
        if(mode!=lastSelectedMode){
            DeviceDebug.record(this,logs,"started",settings.remoteRevision,
                "Solicitado modo "+DeviceDebug.mode(mode))
            lastSelectedMode=mode
        }
        if(mode==2 && !AndroidCompat.canOverlay(this))
            DeviceDebug.record(this,logs,"blocked",settings.remoteRevision,
                "Permiso de Overlay no concedido por Fire OS")
        refreshDebugHud()
    }
    private fun showConnecting(){
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"2Cam Digital Signage · Sincronizando",25f))
        box.addView(ScreenStyles.label(this,"Conservará el último contenido completo cuando no haya Internet."))
        box.addView(ScreenStyles.button(this,"Sincronizar ahora"){sync()})
        box.addView(ScreenStyles.label(this,"Modo: configurado desde el portal o desde Ajustes locales.",15f))
        box.addView(ScreenStyles.button(this,"Configuración / elegir modo"){openSettings()})
        box.addView(ScreenStyles.button(this,"Diagnóstico y logs"){openDiagnostics()})
        setContentView(box);sync()
    }
    private fun showDisabled(){stopOverlay();val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Pantalla en mantenimiento",26f))
        box.addView(ScreenStyles.button(this,"Configuración / modo"){openSettings()})
        box.addView(ScreenStyles.button(this,"Diagnóstico"){startActivity(Intent(this,DiagnosticHomeActivity::class.java))});setContentView(box)
    }
    private fun showSignage(smart:Boolean){stopOverlay()
        requestedOrientation=if(manifest?.json?.optString("orientation")=="portrait")ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val layout=if(smart)settings.modeLayout(activeMode) else "fullscreen"
        val media=FrameLayout(this).apply{setBackgroundColor(0xFF020609.toInt())}
        val root:FrameLayout=if(smart){
            val label=ScreenStyles.label(this,"Esperando aplicación…",26f).apply{gravity=Gravity.CENTER;setBackgroundColor(0xFF152845.toInt())}
            mode3Header=label
            val board:View=if(activeMode==4&&settings.settingString("scoreboard_source")=="lan")SportsBoardView(this).also{lanBoard=it} else label
            RegionLayout(this,layout,settings.mediaFormatPercent(layout),board,media)
        }else FrameLayout(this).apply{addView(media,FrameLayout.LayoutParams(-1,-1))}
        root.setBackgroundColor(ScreenStyles.BACKGROUND)
        stage=media;deck=MediaDeck(media,cache,if(smart)false else settings.videoAudio,smart)
        val hud=ScreenStyles.label(this,"Digital Signage",13f).apply{setBackgroundColor(0x990A1424.toInt());gravity=Gravity.END}
        status=hud
        if(settings.signageShowHud && settings.signageHudSeconds>0){
            root.addView(hud,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
            hud.postDelayed({hud.visibility=View.GONE},settings.signageHudSeconds*1000L)
        }
        setContentView(root)
        if(smart)drawLiveBoard()
        selectPlaylist()
    }
    private fun drawLiveBoard(){
        val expectedApp=if(activeMode==4)"sports" else "turnos"
        val currentBinding=settings.portalUrl+"\n"+(token.load()?:"")+"\n"+expectedApp
        val catalog=applicationCatalog?.takeIf{applicationBinding==currentBinding&&it.optString("application")==expectedApp}
        val scope=catalog?.let{ApplicationCatalog.scopeLabel(it)}?:""
        if(activeMode==4 && settings.settingString("scoreboard_source")=="lan"){
            SportsRuntime.start(this)
            lanBoard?.scopeLabel=scope
            lanBoard?.update(SportsRuntime.host(this).displaySnapshot(),SportsRuntime.board(this))
            if(SportsRuntime.error.isBlank() && (DeviceDebug.effective(this)!=settings.remoteRevision || DeviceDebug.effectiveMode(this)!=4))
                DeviceDebug.record(this,logs,"effective",settings.remoteRevision,"Sports directo LAN mostrado",4)
            return
        }
        val m=lastLive;val type=if(activeMode==4)"scoreboard" else "queue";val d=m.optJSONObject("payload")?:JSONObject()
        if(m.optString("widget_type",type)!=type){
            mode3Header?.text=(if(scope.isBlank())"" else "$scope\n")+if(type=="queue")"SALA DE ESPERA\nEsperando turnos desde el portal"
                else "MARCADOR\nEsperando puntos desde el portal"
            return
        }
        mode3Header?.text=(if(scope.isBlank())"" else "$scope\n")+if(type=="scoreboard"){
            "${d.optString("competition","PARTIDO")}\n${d.optString("home","LOCAL")}    ${d.optString("home_score","0")} : ${d.optString("away_score","0")}    ${d.optString("away","VISITANTE")}\n${d.optString("period","")}   ${d.optString("clock","")}".trim()
        }else{
            "${d.optString("title","SALA DE ESPERA")}\nTURNO  ${d.optString("ticket","--")}\nPUESTO ${d.optString("counter","--")}\n${d.optString("message","")}".trim()
        }
        if(activeMode in 3..4 && (DeviceDebug.effective(this)!=settings.remoteRevision ||
            DeviceDebug.effectiveMode(this)!=activeMode)){
            DeviceDebug.record(this,logs,"effective",settings.remoteRevision,
                "Panel ${DeviceDebug.mode(activeMode)} mostrado",activeMode)
        }
    }
    private fun showOverlayControl(){
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Modo Overlay programado",30f))
        val available=AndroidCompat.canOverlay(this)
        box.addView(ScreenStyles.label(this,if(available)"Overlay activo en segundo plano. Puedes elegir tu aplicación de video desde Inicio. Con Inicio automático activado, no necesitas abrir Digital Signage después de reiniciar el dispositivo." else "Debes autorizar Mostrar sobre otras aplicaciones para usar Overlay."))
        if(!available)box.addView(ScreenStyles.button(this,"Autorizar overlay"){
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName"))) }
                catch(e:Exception) { lastError="Configuración Overlay no disponible: ${e.message}" }
            } else {
                android.widget.Toast.makeText(this, "Overlay administrado por Fire OS / fabricante", android.widget.Toast.LENGTH_LONG).show()
            }
        })
        else startOverlay()
        box.addView(ScreenStyles.button(this,"Configuración local"){openSettings()})
        box.addView(ScreenStyles.label(this,"La superposición depende del modelo, Fire OS/Android y las protecciones de otras aplicaciones.",16f))
        setContentView(box)
    }
    private fun startOverlay(){if(overlayRunning)return
        if(!AndroidCompat.canOverlay(this))return
        try{val intent=Intent(this,OverlayService::class.java);AndroidCompat.startServiceCompat(this,intent);overlayRunning=true}catch(e:Exception){lastError="Overlay no disponible: ${e.message}"}
    }
    private fun stopOverlay(){if(overlayRunning){stopService(Intent(this,OverlayService::class.java));overlayRunning=false}}
    private fun selectPlaylist(){
        val model=manifest?:return

        // The old fingerprint ignored transition, zoom, layout and item order.
        // The full selected playlist also detects visual-only changes.
        val list=model.entries(settings,activeMode)
        val fingerprint="${model.activeMode(settings)}:${settings.publicSnapshot()}:${model.json.optString("orientation")}:${list.toString()}"
        if(fingerprint==playlistIdentity)return
        playlistIdentity=fingerprint;entries=list;index=0;deck?.clear()
        stage?.visibility=if(activeMode in 3..4 && settings.modeLayout(activeMode)=="fullscreen")View.GONE else View.VISIBLE
        if(entries.length()>0)advance() else status?.apply{visibility=View.VISIBLE;text="${DeviceDebug.mode(activeMode)} · Sin playlist compatible para ${settings.modeLayout(activeMode)}"}
    }
    private fun advance(){
        if(activeMode !in listOf(1,3,4))return
        if(entries.length()==0)return
        val item=entries.optJSONObject(index%entries.length())?:return
        index=(index+1)%entries.length()
        try{deck?.display(item,{advance()},{message->
            lastError=message;logs.record("error","media",message)
            status?.apply{visibility=View.VISIBLE;text=message};ui.postDelayed({advance()},2500)
        },{
            if(stage?.visibility==View.VISIBLE && (DeviceDebug.effective(this)!=settings.remoteRevision ||
                DeviceDebug.effectiveMode(this)!=activeMode)){
                DeviceDebug.record(this,logs,"effective",settings.remoteRevision,
                    "Medio renderizado en "+DeviceDebug.mode(activeMode),activeMode)
            }
            refreshDebugHud()
        })}catch(e:Exception) {
            logs.record("error","advance",e.javaClass.simpleName,e.message?:"")
            ui.postDelayed({advance()},2500)
        }
    }
    private fun refreshLiveAdvertising(){
        if(activeMode !in 3..4 || settings.modeLayout(activeMode)!="fullscreen")return
        val prefix=if(activeMode==4)"scoreboard" else "queue"
        val interval=settings.settingInt(prefix+"_ad_interval_sec")*1000L
        val duration=settings.settingInt(prefix+"_ad_duration_sec")*1000L
        val elapsed=System.currentTimeMillis()-liveAdCycleStartedAt
        stage?.visibility=if(entries.length()>0 && elapsed%(interval+duration)>=interval)View.VISIBLE else View.GONE
    }
    private fun pulse(){
        pollService()
        refreshLiveAdvertising()
        refreshDebugHud()
        pollDebugConfig()
        if(settings.diagnosticMode)return
        if(token.load()==null)return
        val model=manifest
        if(model!=null){
            val nextMode=model.activeMode(settings)
            showMode();if(activeMode in listOf(1,3,4))selectPlaylist()
        }

        // During an active test, query for changed playlist definitions every ~15s.
        // Normal playback preserves the configured interval; control requests are polled separately.
        val refreshMs=if(settings.debugHudEnabled)15000L else settings.syncMinutes*60000L
        if(System.currentTimeMillis()-lastSync>=refreshMs)sync()
        if(System.currentTimeMillis()-lastControlPoll>=15000L)checkRemoteSync()
        checkAppUpdates()
        if(activeMode in 3..4){
            pollApplicationConfig()
            if(activeMode==4 && settings.settingString("scoreboard_source")=="lan")drawLiveBoard()
            else if(System.currentTimeMillis()-lastLivePoll>settings.smartPollSec*1000L)pollLive()
        }
    }
    private fun pollLive(){val secret=token.load()?:return;lastLivePoll=System.currentTimeMillis()
        worker.execute{try{val live=SignageApi(settings.portalUrl,secret).live().optJSONObject("live")?:return@execute
            ui.post{
                if(settings.smartSound && activeMode==3 && live.optString("widget_type")=="queue"){
                    val seq=live.optJSONObject("payload")?.optLong("call_sequence",0L)?:0L
                    if(lastCallSequence>=0 && seq>lastCallSequence){
                        try{
                            val tone=android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,65)
                            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,450)
                            ui.postDelayed({try{tone.release()}catch(_:Exception){}},800)
                        }catch(_:Exception){}
                    }
                    lastCallSequence=seq
                }
                if(token.load()!=secret)return@post
                lastLive=live;drawLiveBoard()
            }}catch(_:Exception){}}
    }
    private fun checkRemoteSync() {
        val secret=token.load()?:return;lastControlPoll=System.currentTimeMillis()
        worker.execute {
            try {
                val desired=SignageApi(settings.portalUrl,secret).control().optLong("requested_version",0L)
                if(desired>settings.lastControlVersion)
                    ui.post {remoteControlRevision=desired;sync()}
            }catch(e:Exception){logs.record("warn","control",e.javaClass.simpleName,e.message?:"")}
        }
    }

    private fun playbackManifestKey(obj:JSONObject):String {
        val fields=listOf("media_formats","orientation","timezone","schedules","fallback","mode_blocks","device_options","playlist_catalog")
        return fields.joinToString("|"){key->obj.opt(key)?.toString()?:""}
    }
    private fun sync(){val secret=token.load()?:return;if(syncInFlight)return;syncInFlight=true;lastSync=System.currentTimeMillis();lastApplicationPoll=0L
        logs.record("info","sync","Inicio de sincronización")
        worker.execute{
            try{
                val api=SignageApi(settings.portalUrl,secret)
                // Diagnostic upload always precedes content and settings.
                try{logs.upload(api)}catch(e:Exception){logs.record("warn","logs_upload",e.javaClass.simpleName,e.message?:"")}
                // Apply remote settings first; the player then uses the new preferences.
                // If the remote config endpoint is temporarily unavailable, preserve cached playback.
                try {
                    DeviceConfigSync(settings,api){stage,rev,detail->
                        DeviceDebug.record(this,logs,stage,rev,detail)
                    }.poll()
                    try{logs.upload(api)}catch(_:Exception){}
                }catch(e:Exception){
                    lastError="Configuración remota: ${e.message}"
                    logs.record("error","config_sync",e.javaClass.simpleName,lastError)
                    try{logs.upload(api)}catch(_:Exception){}
                }
                val obj=api.manifest()
                logs.record("info","manifest","Programación recibida","schema=${obj.optInt("schema_version")}; device=${obj.optInt("device_id")}")

                val cached=cache.active()
                val changed=cached==null || playbackManifestKey(cached)!=playbackManifestKey(obj) ||
                    ManifestModel(obj).allMedia().any { item->
                        val file=cache.mediaFile(item.optString("sha256"))
                        !file.isFile || file.length()!=item.optLong("size",-1L)
                    }
                if(changed){
                    cache.sync(api,obj){msg->
                        ui.post{status?.apply{visibility=View.VISIBLE;text=msg}}
                    }
                    logs.record("info","playlist_changed","Programación actualizada en caliente",
                        "items=${ManifestModel(obj).entries().length()}")
                    if(settings.debugHudEnabled)ui.post{
                        android.widget.Toast.makeText(this,"Nueva lista aplicada sin reiniciar",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }else if(settings.debugHudEnabled){
                    logs.record("info","playlist_unchanged","Consulta realizada, sin cambios")
                }
                val pending=remoteControlRevision
                if(pending>settings.lastControlVersion) {
                    try{
                        api.acknowledgeControl(pending);settings.lastControlVersion=pending
                        logs.record("info","sync","Sincronización remota confirmada","revision=$pending")
                    }catch(e:Exception){logs.record("warn","sync_ack",e.javaClass.simpleName,e.message?:"")}
                }
                val model=ManifestModel(obj)
                ui.post{manifest=model;lastLive=model.widget();syncInFlight=false;showMode();selectPlaylist();drawLiveBoard();status?.postDelayed({if(entries.length()>0)status?.visibility=View.GONE},3500)}
                api.heartbeat(JSONObject().put("app_version",BuildConfig.VERSION_NAME).put("active_mode",model.activeMode(settings))
                 .put("cached_media",cache.countMedia()).put("free_storage_bytes",cache.freeBytes()).put("last_error",lastError.take(200)))
            }catch(e:Exception){lastError=e.message?:"Error de sincronización"
                logs.record("error","sync",e.javaClass.simpleName,lastError)
                ui.post{syncInFlight=false;if((manifest==null||!settings.offlinePlayback)&&token.load()!=null)showOfflineError();else status?.apply{visibility=View.VISIBLE;text="Sin conexión, reproduciendo caché: $lastError"}}}
        }
    }
    private fun showOfflineError(){val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Sin programación descargada",25f))
        box.addView(ScreenStyles.label(this,"Conecta el dispositivo a Internet para descargar la primera lista."))
        box.addView(ScreenStyles.button(this,"Reintentar"){sync()})
        box.addView(ScreenStyles.button(this,"Configuración"){openSettings()})
        box.addView(ScreenStyles.button(this,"Diagnóstico seguro"){startActivity(Intent(this,DiagnosticHomeActivity::class.java))})
        setContentView(box)
    }
}

