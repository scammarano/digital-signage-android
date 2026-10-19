package com.digitalsignage.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.Executors

/** Opt-in experimental overlay. Not supported above secure/protected apps or external HDMI. */
class OverlayService:Service(){
    companion object {
        const val ACTION_PREVIEW="com.digitalsignage.tv.PREVIEW_OVERLAY"
        @Volatile var lastShownAt=0L;private set
        @Volatile var lastHiddenAt=0L;private set
        @Volatile var visible=false;private set
        @Volatile var running=false;private set
        @Volatile var itemsShown=0L;private set
        @Volatile var windowSlots=0L;private set
    }
    private val handler=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
    private lateinit var settings:SettingsStore;private lateinit var cache:CacheManager
    private var wm:WindowManager?=null;private var current:View?=null;private var nextAdAt=0L;private var hideAt=0L
    private var previewUntil=0L
    private var dismiss:Runnable?=null
    private var mediaDeck:MediaDeck?=null
    private val serviceGate by lazy{ServiceGate(this)}
    private var servicePollAt=0L
    @Volatile private var servicePollBusy=false
    private fun pollService(){
        val secret=SecureToken(this).load()?:return
        val now=System.currentTimeMillis();if(servicePollBusy||now-servicePollAt<15000L)return
        servicePollAt=now;servicePollBusy=true
        worker.execute{try{val api=SignageApi(settings.portalUrl,secret);val state=api.service()
            if(SecureToken(this).load()==secret){serviceGate.accept(state);handler.post{
                if(serviceGate.suspended)hide()
                if(!serviceGate.suspended||!visible)worker.execute{try{api.reportService(serviceGate.confirmation())}catch(_:Exception){}}
            }}
        }catch(_:Exception){}finally{servicePollBusy=false}}
    }
    private var lastFetch=0L;private var index=0;private var syncing=false
    private var lastDebugConfigPoll=0L
    private var debugText:TextView?=null
    private fun refreshDebugOverlay() {
        val manager=wm?:return
        if(!settings.debugHudEnabled || !AndroidCompat.canOverlay(this)) {
            debugText?.let{try{manager.removeView(it)}catch(_:Exception){}}
            debugText=null
            return
        }
        val label=debugText ?: TextView(this).apply{
            textSize=13f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(10,8,10,8)
            setBackgroundColor(0xDB06213B.toInt())
        }.also{
            val lp=WindowManager.LayoutParams(-1,-2,AndroidCompat.overlayWindowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP}
            try{manager.addView(it,lp);debugText=it}catch(_:Exception){}
        }
        label.text=DeviceDebug.snapshot(this,settings,2,lastFetch,"","Overlay en segundo plano")
    }
    private fun pollDebugConfig(){
        if(!settings.debugHudEnabled)return
        val now=System.currentTimeMillis()
        if(now-lastDebugConfigPoll<5000L)return
        lastDebugConfigPoll=now
        worker.execute{
            try{
                val secret=SecureToken(this).load()?:return@execute
                val api=SignageApi(settings.portalUrl,secret)
                DeviceConfigSync(settings,api){stage,rev,detail->
                    DeviceDebug.record(this,DeviceLogs(this),stage,rev,detail)
                }.poll()
                try{DeviceLogs(this).upload(api)}catch(_:Exception){}
            }catch(_:Exception){}
        }
    }
    private val ticker=object:Runnable{override fun run(){try{pulse()}finally{handler.postDelayed(this,1000)}}}
    override fun onCreate(){super.onCreate();settings=SettingsStore(this);cache=CacheManager(this,settings)
        running=true
        startForeground(91, AndroidCompat.notification(this, "signage_overlay", "Digital Signage Overlay",
            "Digital Signage activo", "Publicidad programada en segundo plano"))
        wm=getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_PREVIEW){
            hide()
            nextAdAt=System.currentTimeMillis()
            previewUntil=nextAdAt+settings.overlayDurationSec*1000L+2000L
        }
        // Start only after the start intent has established preview policy. A ticker
        // queued in onCreate can otherwise stop a new preview in a non-Overlay mode.
        handler.removeCallbacks(ticker);handler.post(ticker)
        DeviceLogs(this).record("info","overlay_service_start","Overlay iniciado",
            "action=${intent?.action?:"normal"};since_boot_ms=${android.os.SystemClock.elapsedRealtime()}")
        // Never resurrect an already disabled service on an unsolicited OS restart.
        return START_NOT_STICKY
    }
    override fun onDestroy(){handler.removeCallbacks(ticker);hide();
        running=false
        debugText?.let{try{wm?.removeView(it)}catch(_:Exception){}}
        debugText=null
        worker.shutdownNow();super.onDestroy()}
    private fun pulse(){
        pollService()
        if(serviceGate.suspended){hide();return}
        refreshDebugOverlay()
        pollDebugConfig()
        if(!AndroidCompat.canOverlay(this)){hide();stopSelf();return}
        val model=cache.active()?.let{ManifestModel(it)}?:return
        if(model.activeMode(settings)!=2 && System.currentTimeMillis()>=previewUntil){hide();stopSelf();return}
        val readiness=ModeReadiness.error(settings.publicSnapshot(),model,2)
        if(readiness!=null){
            hide();DeviceDebug.record(this,DeviceLogs(this),"blocked",settings.remoteRevision,readiness)
        }
        val now=System.currentTimeMillis()
        if(now-lastFetch>settings.syncMinutes*60000L&&!syncing){syncing=true;lastFetch=now
            worker.execute{
                try{val secret=SecureToken(this).load()?:return@execute;val api=SignageApi(settings.portalUrl,secret)
                    try{
                        DeviceConfigSync(settings,api){step,rev,detail->
                            DeviceDebug.record(this,DeviceLogs(this),step,rev,detail)
                        }.poll()
                        try{DeviceLogs(this).upload(api)}catch(_:Exception){}
                    }catch(_:Exception){}
                    cache.sync(api,api.manifest()){};
                    api.heartbeat(JSONObject().put("app_version",BuildConfig.VERSION_NAME).put("active_mode",2).put("cached_media",cache.countMedia()).put("free_storage_bytes",cache.freeBytes()))
                }catch(_:Exception){}finally{syncing=false}
            }
        }
        if(readiness!=null)return
        if(current!=null){if(now>=hideAt)hide(true);return}
        // The current APK configuration owns Overlay policy independently of playlist layout.
        val interval=settings.overlayIntervalSec.toLong()*1000L
        if(nextAdAt==0L)nextAdAt=now+interval
        if(now<nextAdAt)return
        val all=model.entries(settings,2);val images=ArrayList<JSONObject>()
        for(i in 0 until all.length()){val item=all.optJSONObject(i)?:continue;if(item.optString("mime").startsWith("image/"))images.add(item)}
        if(images.isEmpty()){nextAdAt=now+interval;return}
        show(images,settings.overlayDurationSec.toLong()*1000L)
    }
    private fun show(items:List<JSONObject>,duration:Long){
        try {
            val frame=FrameLayout(this).apply{setBackgroundColor(0xDD0A1424.toInt());alpha=0f}
            val screen=resources.displayMetrics;val width=screen.widthPixels;val height=screen.heightPixels
            val layout=settings.overlayLayout
            val w=when(layout){
                "left","right","side"->(width*settings.overlayWidthPct/100f).toInt()
                "half_vertical"->width/2
                else->width
            }.coerceAtLeast(1)
            val h=when(layout){
                "bottom","top"->(height*settings.overlayHeightPct/100f).toInt()
                "half_horizontal"->height/2
                else->height
            }.coerceAtLeast(1)
            val gravity=when(layout){
                "left"->Gravity.TOP or Gravity.LEFT
                "right","side","half_vertical"->Gravity.TOP or Gravity.RIGHT
                "top"->Gravity.TOP
                else->Gravity.BOTTOM
            }
            val attrs=WindowManager.LayoutParams(w,h,AndroidCompat.overlayWindowType,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{this.gravity=gravity;this.alpha=settings.overlayOpacity/100f}
            val manager=wm?:return
            val offset=OverlayMotion.offset(layout,w,h)
            frame.translationX=offset.first;frame.translationY=offset.second
            manager.addView(frame,attrs);current=frame;visible=true
            hideAt=System.currentTimeMillis()+5000L // Attachment watchdog, not media exposure.
            frame.post{
                if(current!==frame || frame.windowToken==null)return@post
                lastShownAt=System.currentTimeMillis();hideAt=lastShownAt+duration
                val deck=MediaDeck(frame,cache,false,true);mediaDeck=deck;windowSlots=0L
                fun advance(){
                    if(current!==frame)return
                    val slot=index%items.size;val item=items[slot];index++
                    deck.display(item,{advance()},{error->
                        DeviceLogs(this).record("warn","overlay_media",error)
                        handler.postDelayed({advance()},200L)
                    },{itemsShown++;if(slot<63)windowSlots=windowSlots or (1L shl slot);DeviceLogs(this).record("info","overlay_item","Ítem ${slot+1}/${items.size}")})
                }
                advance()
                frame.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(minOf(600L,duration/2))
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                dismiss=Runnable{if(current===frame)hide(duration>=1000L)}.also{handler.postDelayed(it,duration)}
            }
            if(DeviceDebug.effective(this)!=settings.remoteRevision ||
               DeviceDebug.effectiveMode(this)!=2){
                DeviceDebug.record(this,DeviceLogs(this),"effective",settings.remoteRevision,
                    "Publicidad Overlay mostrada en pantalla",2)
            }
        }catch(e:Exception){
            DeviceDebug.record(this,DeviceLogs(this),"blocked",settings.remoteRevision,"Overlay: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            hide()
        }
    }
    private fun hide(animated:Boolean=false){
        dismiss?.let{handler.removeCallbacks(it)};dismiss=null
        val view=current?:return;current=null
        val oldDeck=mediaDeck;mediaDeck=null
        nextAdAt=System.currentTimeMillis()+settings.overlayIntervalSec.toLong()*1000L
        visible=false
        lastHiddenAt=System.currentTimeMillis()
        val manager=wm?:return
        fun remove(){oldDeck?.release();try{manager.removeView(view)}catch(_:Exception){}}
        if(!animated){view.animate().cancel();remove();return}
        val offset=OverlayMotion.offset(settings.overlayLayout,view.width,view.height)
        view.animate().translationX(offset.first).translationY(offset.second).alpha(0f).setDuration(350).withEndAction{remove()}.start()
    }
}

