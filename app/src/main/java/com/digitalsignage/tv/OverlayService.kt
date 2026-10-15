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
    private val handler=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
    private lateinit var settings:SettingsStore;private lateinit var cache:CacheManager
    private var wm:WindowManager?=null;private var current:View?=null;private var nextAdAt=0L;private var hideAt=0L
    private var lastFetch=0L;private var index=0;private var syncing=false
    private var lastDebugConfigPoll=0L
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
        startForeground(91, AndroidCompat.notification(this, "signage_overlay", "Digital Signage Overlay",
            "Digital Signage activo", "Publicidad programada en segundo plano"))
        wm=getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler.post(ticker)
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int=START_STICKY
    override fun onDestroy(){handler.removeCallbacks(ticker);hide();worker.shutdownNow();super.onDestroy()}
    private fun pulse(){
        pollDebugConfig()
        if(!AndroidCompat.canOverlay(this)){hide();stopSelf();return}
        val model=cache.active()?.let{ManifestModel(it)}?:return
        if(model.activeMode(settings)!=2){hide();stopSelf();return}
        val now=System.currentTimeMillis()
        if(now-lastFetch>settings.syncMinutes*60000L&&!syncing){syncing=true;lastFetch=now
            worker.execute{
                try{val secret=SecureToken(this).load()?:return@execute;val api=SignageApi(settings.portalUrl,secret)
                    try{DeviceConfigSync(settings,api).poll()}catch(_:Exception){}
                    cache.sync(api,api.manifest()){};
                    api.heartbeat(JSONObject().put("app_version","0.4.8").put("active_mode",2).put("cached_media",cache.countMedia()).put("free_storage_bytes",cache.freeBytes()))
                }catch(_:Exception){}finally{syncing=false}
            }
        }
        if(current!=null){if(now>=hideAt)hide();return}
        // The current APK configuration owns Overlay policy independently of playlist layout.
        val interval=settings.overlayIntervalSec.toLong()*1000L
        if(nextAdAt==0L)nextAdAt=now+interval
        if(now<nextAdAt)return
        val all=model.entries();val images=ArrayList<JSONObject>()
        for(i in 0 until all.length()){val item=all.optJSONObject(i)?:continue;if(item.optString("mime").startsWith("image/"))images.add(item)}
        if(images.isEmpty()){nextAdAt=now+interval;return}
        val item=images[index%images.size];index++
        val duration=settings.overlayDurationSec.toLong()*1000L
        show(item);hideAt=now+duration;nextAdAt=now+interval
    }
    private fun show(item:JSONObject){
        val file=cache.mediaFile(item.optString("sha256"));if(!file.isFile)return
        try {
            val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,opts)
            var sample=1;while(opts.outWidth/sample>1200||opts.outHeight/sample>1200)sample*=2
            val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?:return
            val image=ImageView(this).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER}
            val frame=FrameLayout(this).apply{setBackgroundColor(0xDD0A1424.toInt());addView(image,FrameLayout.LayoutParams(-1,-1));alpha=0f}
            val screen=resources.displayMetrics;val width=screen.widthPixels;val height=screen.heightPixels
            val layout=settings.overlayLayout
            val w=when(layout){
                "left","right"->(width*settings.overlayWidthPct/100f).toInt()
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
                "right","half_vertical"->Gravity.TOP or Gravity.RIGHT
                "top"->Gravity.TOP
                else->Gravity.BOTTOM
            }
            val attrs=WindowManager.LayoutParams(w,h,AndroidCompat.overlayWindowType,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{this.gravity=gravity;this.alpha=settings.overlayOpacity/100f}
            wm?.addView(frame,attrs);current=frame;frame.animate().alpha(1f).setDuration(500).start()
        }catch(_:Exception){hide()}
    }
    private fun hide(){current?.let{try{wm?.removeView(it)}catch(_:Exception){}};current=null}
}
