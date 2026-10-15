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
    private lateinit var settings:SettingsStore
    private lateinit var token:SecureToken
    private lateinit var cache:CacheManager
    private var manifest:ManifestModel?=null
    private var deck:MediaDeck?=null
    private var stage:FrameLayout?=null
    private var panel:LinearLayout?=null
    private var status:TextView?=null
    private var startingSplash=false
    private var overlayRunning=false
    private var activeMode=-1
    private var playlistIdentity=""
    private var entries=JSONArray()
    private var index=0
    private var lastSync=0L
    private var lastLivePoll=0L
    private var lastError=""
    private var syncInFlight=false
    private var lastLive=JSONObject()
    private var mode3Header:TextView?=null
    private val tick=object:Runnable{override fun run(){try{pulse()}finally{ui.postDelayed(this,5000)}}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings=SettingsStore(this);token=SecureToken(this);cache=CacheManager(this,settings)
        reloadFromDisk()
        if(settings.splash){
            startingSplash=true
            val splash=FrameLayout(this).apply{setBackgroundColor(ScreenStyles.BACKGROUND)}
            val brand=ScreenStyles.label(this,"DIGITAL SIGNAGE TV",36f).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER}
            splash.addView(brand,FrameLayout.LayoutParams(-1,-1))
            setContentView(splash)
            ui.postDelayed({startingSplash=false; if(token.load()==null)showPairing() else {showMode();sync()}; ui.post(tick)},900)
        }else{
            if(token.load()==null)showPairing() else showMode()
            ui.postDelayed(tick,1000)
        }
    }
    override fun onDestroy(){ui.removeCallbacks(tick);deck?.clear();worker.shutdownNow();super.onDestroy()}
    override fun onResume(){super.onResume();if(::settings.isInitialized && !startingSplash && token.load()!=null){lastSync=0L;sync();}}
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean {
        if(keyCode==KeyEvent.KEYCODE_MENU || (keyCode==KeyEvent.KEYCODE_DPAD_CENTER && (event?.isLongPress==true))){openSettings();return true}
        return super.onKeyDown(keyCode,event)
    }
    private fun openSettings(){startActivity(Intent(this,SettingsActivity::class.java))}
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
              try{val r=SignageApi(settings.portalUrl,null).pair(entered);token.save(r.getString("token"));ui.post{line.text="Vinculado";showMode();sync()}}
              catch(e:Exception){ui.post{line.text="Error: ${e.message}"}}
            }
        })
        box.addView(ScreenStyles.button(this,"Configuración local (PIN)"){openSettings()})
        setContentView(box)
    }
    private fun showMode(){
        val model=manifest
        if(model==null){showConnecting();return}
        val mode=model.activeMode(settings)
        if(mode==activeMode&&stage!=null)return
        activeMode=mode;playlistIdentity="";deck?.clear();deck=null;stage=null;mode3Header=null
        when(mode){1->showSignage(false);2->showOverlayControl();3->showSignage(true);else->showDisabled()}
    }
    private fun showConnecting(){
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Digital Signage · Sincronizando programación",25f))
        box.addView(ScreenStyles.label(this,"Conservará el último contenido completo cuando no haya Internet."))
        box.addView(ScreenStyles.button(this,"Sincronizar ahora"){sync()})
        box.addView(ScreenStyles.button(this,"Configuración"){openSettings()})
        setContentView(box);sync()
    }
    private fun showDisabled(){stopOverlay();val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Pantalla en mantenimiento",26f))
        box.addView(ScreenStyles.button(this,"Configuración"){openSettings()});setContentView(box)
    }
    private fun showSignage(smart:Boolean){stopOverlay()
        requestedOrientation=if(manifest?.json?.optString("orientation")=="portrait")ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val root=FrameLayout(this).apply{setBackgroundColor(ScreenStyles.BACKGROUND)}
        if(smart){
            val label=ScreenStyles.label(this,"Cargando pantalla inteligente…",32f).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;setBackgroundColor(0xFF152845.toInt())}
            mode3Header=label
            root.addView(label,FrameLayout.LayoutParams(-1,0).apply{height=(resources.displayMetrics.heightPixels*0.38f).toInt();gravity=Gravity.TOP})
        }
        val media=FrameLayout(this).apply{setBackgroundColor(0xFF020609.toInt())}
        val params=if(smart)FrameLayout.LayoutParams(-1,0).apply{height=(resources.displayMetrics.heightPixels*0.60f).toInt();gravity=Gravity.BOTTOM} else FrameLayout.LayoutParams(-1,-1)
        root.addView(media,params);stage=media;deck=MediaDeck(media,cache,settings.videoAudio)
        val hud=ScreenStyles.label(this,"Digital Signage",13f).apply{setBackgroundColor(0x990A1424.toInt());gravity=Gravity.END}
        status=hud;root.addView(hud,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        hud.postDelayed({hud.visibility=View.GONE},10000)
        setContentView(root)
        if(smart)drawLiveBoard()
        selectPlaylist()
    }
    private fun drawLiveBoard(){
        val m=lastLive;val type=m.optString("widget_type","queue");val d=m.optJSONObject("payload")?:JSONObject()
        mode3Header?.text=if(type=="scoreboard"){
            "${d.optString("competition","PARTIDO")}\n${d.optString("home","LOCAL")}    ${d.optString("home_score","0")} : ${d.optString("away_score","0")}    ${d.optString("away","VISITANTE")}\n${d.optString("period","")}   ${d.optString("clock","")}".trim()
        }else{
            "${d.optString("title","SALA DE ESPERA")}\nTURNO  ${d.optString("ticket","--")}\nPUESTO ${d.optString("counter","--")}\n${d.optString("message","")}".trim()
        }
    }
    private fun showOverlayControl(){
        val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Modo Overlay programado",30f))
        val available=AndroidCompat.canOverlay(this)
        box.addView(ScreenStyles.label(this,if(available)"Overlay autorizado. Pulsa Inicio del control remoto para abrir otra app." else "Debes autorizar Mostrar sobre otras aplicaciones para usar Overlay."))
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
        if(!Settings.canDrawOverlays(this))return
        try{val intent=Intent(this,OverlayService::class.java);AndroidCompat.startServiceCompat(this,intent);overlayRunning=true}catch(e:Exception){lastError="Overlay no disponible: ${e.message}"}
    }
    private fun stopOverlay(){if(overlayRunning){stopService(Intent(this,OverlayService::class.java));overlayRunning=false}}
    private fun selectPlaylist(){
        val model=manifest?:return
        val list=model.entries();val ids=StringBuilder()
        for(i in 0 until list.length()){val item=list.optJSONObject(i);ids.append(item?.optString("sha256")).append('/').append(item?.optInt("duration")).append(';')}
        val fingerprint="${model.activeMode(settings)}:$ids"
        if(fingerprint==playlistIdentity)return
        playlistIdentity=fingerprint;entries=list;index=0;deck?.clear()
        if(entries.length()>0)advance() else status?.apply{visibility=View.VISIBLE;text="Sin contenido. Asigna una lista en el portal."}
    }
    private fun advance(){
        if(activeMode!=1&&activeMode!=3)return
        if(entries.length()==0)return
        val item=entries.optJSONObject(index%entries.length())?:return
        index=(index+1)%entries.length()
        deck?.display(item,{advance()},{message->lastError=message;status?.apply{visibility=View.VISIBLE;text=message};ui.postDelayed({advance()},2500)})
    }
    private fun pulse(){
        if(token.load()==null)return
        val model=manifest
        if(model!=null){
            val nextMode=model.activeMode(settings)
            if(nextMode!=activeMode)showMode() else if(activeMode==1||activeMode==3)selectPlaylist()
        }
        if(System.currentTimeMillis()-lastSync>settings.syncMinutes*60000L)sync()
        if(activeMode==3&&System.currentTimeMillis()-lastLivePoll>30000L)pollLive()
    }
    private fun pollLive(){val secret=token.load()?:return;lastLivePoll=System.currentTimeMillis()
        worker.execute{try{val live=SignageApi(settings.portalUrl,secret).live().optJSONObject("live")?:return@execute
            ui.post{lastLive=live;drawLiveBoard()}}catch(_:Exception){}}
    }
    private fun sync(){val secret=token.load()?:return;if(syncInFlight)return;syncInFlight=true;lastSync=System.currentTimeMillis()
        worker.execute{
            try{
                val api=SignageApi(settings.portalUrl,secret)
                // Apply remote settings first; the player then uses the new preferences.
                // If the remote config endpoint is temporarily unavailable, preserve cached playback.
                try{DeviceConfigSync(settings,api).poll()}catch(e:Exception){lastError="Configuración remota: ${e.message}"}
                val obj=api.manifest()
                cache.sync(api,obj){msg->ui.post{status?.apply{visibility=View.VISIBLE;text=msg}}}
                val model=ManifestModel(obj)
                ui.post{manifest=model;lastLive=model.widget();syncInFlight=false;showMode();selectPlaylist();drawLiveBoard();status?.postDelayed({status?.visibility=View.GONE},3500)}
                api.heartbeat(JSONObject().put("app_version","0.4.3").put("active_mode",model.activeMode(settings))
                 .put("cached_media",cache.countMedia()).put("free_storage_bytes",cache.freeBytes()).put("last_error",lastError.take(200)))
            }catch(e:Exception){lastError=e.message?:"Error de sincronización";ui.post{syncInFlight=false;if((manifest==null||!settings.offlinePlayback)&&token.load()!=null)showOfflineError();else status?.apply{visibility=View.VISIBLE;text="Sin conexión, reproduciendo caché: $lastError"}}}
        }
    }
    private fun showOfflineError(){val box=ScreenStyles.column(this)
        box.addView(ScreenStyles.label(this,"Sin programación descargada",25f))
        box.addView(ScreenStyles.label(this,"Conecta el dispositivo a Internet para descargar la primera lista."))
        box.addView(ScreenStyles.button(this,"Reintentar"){sync()})
        box.addView(ScreenStyles.button(this,"Configuración"){openSettings()});setContentView(box)
    }
}
