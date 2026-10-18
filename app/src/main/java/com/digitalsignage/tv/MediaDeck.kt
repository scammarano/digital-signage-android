package com.digitalsignage.tv

import android.graphics.BitmapFactory
import android.os.Build
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Double-buffered image/video viewer; old frame remains until next element is ready. */
class MediaDeck(private val stage:FrameLayout,private val cache:CacheManager,private val playAudio:Boolean,private val regionIsSized:Boolean=false) {
    private val decode=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var serial=0
    private var current:FrameLayout?=null
    private var currentVideo:VideoView?=null
    private var pendingTimer:Runnable?=null
    fun clear(){++serial;pendingTimer?.let{main.removeCallbacks(it)};pendingTimer=null;currentVideo?.stopPlayback();currentVideo=null;current=null;stage.removeAllViews()}
    fun release(){clear();decode.shutdownNow()}
    private fun sizeFor(item:JSONObject):FrameLayout.LayoutParams {
        if(regionIsSized)return FrameLayout.LayoutParams(-1,-1)
        val width=stage.width.takeIf{it>0}?:stage.resources.displayMetrics.widthPixels
        val height=stage.height.takeIf{it>0}?:stage.resources.displayMetrics.heightPixels
        val layout=item.optString("layout","fullscreen")
        return when(layout){
          "bottom"->FrameLayout.LayoutParams(width,(height*cache.formatPercent("bottom")/100f).toInt().coerceAtLeast(1),Gravity.BOTTOM)
          "side"->FrameLayout.LayoutParams((width*cache.formatPercent("side")/100f).toInt().coerceAtLeast(1),height,Gravity.END)
          "half_horizontal"->FrameLayout.LayoutParams(width,height/2,Gravity.BOTTOM)
          "half_vertical"->FrameLayout.LayoutParams(width/2,height,Gravity.END)
          else->FrameLayout.LayoutParams(-1,-1)
        }
    }
    fun display(item:JSONObject,onComplete:()->Unit,onError:(String)->Unit,onShown:()->Unit={}){
        val stamp=++serial
        pendingTimer?.let{main.removeCallbacks(it)};pendingTimer=null
        val sha=item.optString("sha256");val media=cache.mediaFile(sha)
        if(!media.isFile){onError("El medio no está disponible localmente");return}
        val isVideo=item.optString("mime").startsWith("video/")
        val box=FrameLayout(stage.context).apply{setBackgroundColor(0xFF060A12.toInt());alpha=0f}
        val parms=sizeFor(item)
        if(isVideo){
            val video=VideoView(stage.context)
            box.addView(video,FrameLayout.LayoutParams(-1,-1))
            video.setOnErrorListener{_,_,_-> if(stamp==serial)onError("Formato de video no compatible");true}
            video.setOnPreparedListener{mp->
                if(stamp!=serial){video.stopPlayback();return@setOnPreparedListener}
                mp.isLooping=false;try{mp.setVolume(if(playAudio)1f else 0f,if(playAudio)1f else 0f)}catch(_:Exception){}
                switchTo(box,parms,item,video);video.start();onShown()
                video.setOnCompletionListener{if(stamp==serial)onComplete()}
            }
            video.setVideoURI(Uri.fromFile(media))
        }else{
            decode.execute{
                try{
                    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(media.absolutePath,options)
                    val max=if(Build.VERSION.SDK_INT <= 20) 1280 else 2048;var sample=1
                    while(options.outWidth/sample>max||options.outHeight/sample>max)sample*=2
                    val bitmap=BitmapFactory.decodeFile(media.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})?:throw IllegalStateException("Imagen dañada")
                    main.post{
                        if(stamp!=serial){bitmap.recycle();return@post}
                        val image=ImageView(stage.context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(bitmap)}
                        box.addView(image,FrameLayout.LayoutParams(-1,-1));switchTo(box,parms,item,null);onShown()
                        pendingTimer=Runnable{if(stamp==serial)onComplete()}
                        main.postDelayed(pendingTimer!!,MediaTiming.durationMs(item))
                    }
                }catch(e:Exception){main.post{if(stamp==serial)onError(e.message?:"Imagen no compatible")}}
            }
        }
    }

    /** Crossfade time and image zoom movement are independently configurable. */
    private fun switchTo(box:FrameLayout,params:FrameLayout.LayoutParams,item:JSONObject,video:VideoView?){
        val effect=item.optString("transition","fade")
        val slideMs=MediaTiming.durationMs(item)
        val crossfade=item.optLong("transition_ms",600L).coerceIn(0L,5000L)
            .coerceAtMost(slideMs/2)
        val prev=current
        val prevVideo=currentVideo
        stage.addView(box,params)
        if(effect=="zoom" && video==null){
            val child=box.getChildAt(0)
            val raw=item.optDouble("zoom_percent",20.0)
            val percent=(if(raw.isNaN())20.0 else raw).coerceIn(0.0,60.0)
            val factor=1f+(percent/100.0).toFloat()
            val direction=item.optString("zoom_direction","in")
            val from=if(direction=="out")factor else 1f
            val to=if(direction=="out")1f else factor
            val defaultZoomMs=maxOf(1800L,item.optLong("transition_ms",600L)*4L)
            val zoomMs=item.optLong("zoom_duration_ms",defaultZoomMs)
                .coerceIn(300L,20000L).coerceAtMost(slideMs)
            child?.apply{
                scaleX=from;scaleY=from
                animate().scaleX(to).scaleY(to).setDuration(zoomMs)
                    .setInterpolator(android.view.animation.LinearInterpolator()).start()
            }
            box.alpha=if(prev==null || crossfade==0L)1f else 0f
            if(prev!=null && crossfade>0L)box.animate().alpha(1f).setDuration(crossfade).start()
        }else if(effect=="cut" || prev==null || crossfade==0L){
            box.alpha=1f
        }else{
            box.alpha=0f
            when(effect){
                "slide-left"->{
                    box.translationX=stage.width.toFloat()
                    box.animate().translationX(0f).alpha(1f).setDuration(crossfade).start()
                }
                "slide-right"->{
                    box.translationX=-stage.width.toFloat()
                    box.animate().translationX(0f).alpha(1f).setDuration(crossfade).start()
                }
                else->box.animate().alpha(1f).setDuration(crossfade).start()
            }
        }
        if(prev!=null){
            if(effect=="cut" || crossfade==0L){
                stage.removeView(prev);prevVideo?.stopPlayback()
            }else{
                prev.animate().alpha(0f).setDuration(crossfade).withEndAction{
                    stage.removeView(prev);prevVideo?.stopPlayback()
                }.start()
            }
        }
        current=box
        currentVideo=video
    }
}

