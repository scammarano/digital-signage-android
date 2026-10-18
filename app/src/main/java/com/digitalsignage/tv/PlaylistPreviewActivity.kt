package com.digitalsignage.tv

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.json.JSONArray

/** Cached presentation test. Never changes desired/manual mode, revision, or runtime ACK. */
class PlaylistPreviewActivity:Activity(){
    private var deck:MediaDeck?=null
    private var index=0
    private var items=JSONArray()
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var restoreOverlay=false
    private var previewCache:CacheManager?=null
    override fun onCreate(state:Bundle?){super.onCreate(state)
        val settings=SettingsStore(this);val cache=CacheManager(this,settings)
        previewCache=cache
        restoreOverlay=OverlayService.running
        if(restoreOverlay)stopService(android.content.Intent(this,OverlayService::class.java))
        val model=cache.active()?.let{ManifestModel(it)}
        val key=intent.getStringExtra("key")?:"signage_playlist_id"
        val def=settings.schemaFields().firstOrNull{it.getString("key")==key&&it.getString("type")=="playlist"}
        val id=if(def!=null)intent.getIntExtra("id",settings.publicSnapshot().getInt(key))else 0
        val list=model?.catalog()?.optJSONObject(id.toString())
        items=if(id==0&&key=="signage_playlist_id")model?.entries()?:JSONArray() else list?.optJSONArray("items")?:JSONArray()
        val layout=def?.optString("layout","fullscreen")?:"fullscreen"
        fun compatible(a:JSONArray):JSONArray{if(!key.startsWith("overlay_"))return a;return JSONArray().apply{for(i in 0 until a.length()){val item=a.getJSONObject(i);if(item.optString("mime").startsWith("image/"))put(item)}}}
        items=compatible(items)
        cache.retainPreview(items)
        val root=ScreenStyles.column(this)
        root.addView(ScreenStyles.label(this,"PRUEBA · ${list?.optString("name")?:"Programación"}",20f))
        root.addView(ScreenStyles.label(this,"${DeviceNetwork.label()} · No cambia el modo ni la programación",15f))
        val media=FrameLayout(this);val board=ScreenStyles.label(this,if(key.startsWith("queue"))"TURNOS · Simulación de área" else if(key.startsWith("scoreboard"))"MARCADOR · Simulación de área" else "Área de la otra aplicación",24f)
        val canvas=RegionLayout(this,layout,settings.mediaFormatPercent(layout),board,media)
        root.addView(canvas,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(ScreenStyles.button(this,"Terminar prueba"){finish()})
        setContentView(root)
        if(ServiceGate(this).suspended){board.text="Servicio suspendido";return}
        if(items.length()==0&&id>0){
            board.text="Preparando vista previa…"
            worker.execute{try{
                val secret=SecureToken(this).load()?:error("Primero empareja la pantalla")
                val portal=settings.portalUrl;val api=SignageApi(portal,secret);val preview=api.previewPlaylist(id)
                require(preview.getString("preview_layout")==layout){"Formato de lista incompatible"}
                cache.preview(api,preview){}
                runOnUiThread{if(!isFinishing&&!isDestroyed&&SecureToken(this).load()==secret&&settings.portalUrl==portal){
                    items=compatible(preview.getJSONObject("fallback").getJSONArray("items"))
                    if(items.length()==0)board.text="La lista está vacía" else{deck=MediaDeck(media,cache,false,true);next()}
                }}
            }catch(e:Exception){runOnUiThread{if(!isFinishing)board.text="No se pudo preparar la lista: "+(e.message?:e.javaClass.simpleName)}}finally{if(isFinishing||isDestroyed)cache.releasePreview()}}
        }else if(items.length()==0)board.text="Sin contenido en la programación actual"
        else{deck=MediaDeck(media,cache,false,true);next()}
    }
    private fun next(){if(isFinishing||items.length()==0)return
        deck?.display(items.getJSONObject(index++%items.length()),{next()},{error->
            android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show();finish()
        })
    }
    override fun onDestroy(){deck?.release();worker.shutdownNow();previewCache?.releasePreview();if(restoreOverlay&&!ServiceGate(this).suspended)AndroidCompat.startServiceCompat(this,android.content.Intent(this,OverlayService::class.java));super.onDestroy()}
}
