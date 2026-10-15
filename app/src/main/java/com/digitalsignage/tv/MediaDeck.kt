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
class MediaDeck(private val stage:FrameLayout,private val cache:CacheManager,private val playAudio:Boolean) {
    private val decode=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var serial=0
    private var current:FrameLayout?=null
    private var currentVideo:VideoView?=null
    private var pendingTimer:Runnable?=null
    fun clear(){++serial;pendingTimer?.let{main.removeCallbacks(it)};pendingTimer=null;currentVideo?.stopPlayback();currentVideo=null;current=null;stage.removeAllViews()}
    private fun sizeFor(item:JSONObject):FrameLayout.LayoutParams {
        val width=stage.width.takeIf{it>0}?:stage.resources.displayMetrics.widthPixels
        val height=stage.height.takeIf{it>0}?:stage.resources.displayMetrics.heightPixels
        val layout=item.optString("layout","fullscreen")
        return when(layout){
          "bottom"->FrameLayout.LayoutParams(width,(height*0.20f).toInt().coerceAtLeast(1),Gravity.BOTTOM)
          "side"->FrameLayout.LayoutParams((width*0.40f).toInt().coerceAtLeast(1),height,Gravity.END)
          "half_horizontal"->FrameLayout.LayoutParams(width,height/2,Gravity.BOTTOM)
          "half_vertical"->FrameLayout.LayoutParams(width/2,height,Gravity.END)
          else->FrameLayout.LayoutParams(-1,-1)
        }
    }
    fun display(item:JSONObject,onComplete:()->Unit,onError:(String)->Unit){
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
                switchTo(box,parms,item,video);video.start()
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
                        box.addView(image,FrameLayout.LayoutParams(-1,-1));switchTo(box,parms,item,null)
                        pendingTimer=Runnable{if(stamp==serial)onComplete()}
                        main.postDelayed(pendingTimer!!,item.optLong("duration",10).coerceIn(1,3600)*1000)
                    }
                }catch(e:Exception){main.post{if(stamp==serial)onError(e.message?:"Imagen no compatible")}}
            }
        }
    }
    private fun switchTo(box:FrameLayout,params:FrameLayout.LayoutParams,item:JSONObject,video:VideoView?){
        val effect=item.optString("transition","fade")
        val duration=item.optLong("transition_ms",600).coerceIn(0,3000)
        val prev=current;val prevVideo=currentVideo
        stage.addView(box,params)
        if(effect=="cut"||prev==null||duration==0L){box.alpha=1f;stage.removeView(prev);if(prevVideo!=null)prevVideo.stopPlayback()}
        else{
          box.alpha=0f
          if(effect=="slide-left"){box.translationX=stage.width.toFloat();box.animate().translationX(0f).alpha(1f).setDuration(duration).start()}
          else if(effect=="slide-right"){box.translationX=-stage.width.toFloat();box.animate().translationX(0f).alpha(1f).setDuration(duration).start()}
          else if(effect=="zoom"){box.scaleX=1.06f;box.scaleY=1.06f;box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(duration).start()}
          else box.animate().alpha(1f).setDuration(duration).start()
          prev.animate().alpha(0f).setDuration(duration).withEndAction{stage.removeView(prev);prevVideo?.stopPlayback()}.start()
        }
        current=box;currentVideo=video
    }
}
