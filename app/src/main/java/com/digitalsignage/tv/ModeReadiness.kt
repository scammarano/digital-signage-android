package com.digitalsignage.tv

import org.json.JSONObject

/** Shared activation gate for native settings, LAN and runtime. Draft settings may be incomplete. */
object ModeReadiness {
    fun layout(config:JSONObject,mode:Int):String=when(mode){
        2->when(val value=config.getString("overlay_layout")){"top"->"bottom";"left"->"side";else->value}
        3->config.getString("queue_layout")
        4->config.getString("scoreboard_layout")
        else->"fullscreen"
    }
    fun playlist(config:JSONObject,mode:Int):Int=if(mode==1)config.getInt("signage_playlist_id") else
        config.getInt((when(mode){2->"overlay";3->"queue";else->"scoreboard"})+"_playlist_"+layout(config,mode))
    fun error(config:JSONObject,model:ManifestModel?,mode:Int):String? {
        if(mode !in 1..4)return "Modo inválido"
        if(config.getInt("enabled_modes") and (1 shl (mode-1))==0)return "Este modo está deshabilitado"
        val name=DeviceDebug.mode(mode)
        if(model==null)return "$name: sincroniza el catálogo y asigna una playlist compatible antes de activar"
        val id=playlist(config,mode)
        if(mode==1 && id==0){
            val items=model.entries()
            for(i in 0 until items.length())if(items.optJSONObject(i)?.optString("layout","fullscreen")=="fullscreen")return null
            return "Cartelera: no hay playlist de pantalla completa disponible en la programación actual"
        }
        if(id<=0)return "$name: asigna una playlist para ${layout(config,mode)} antes de activar"
        val list=model.json.optJSONObject("playlist_catalog")?.optJSONObject(id.toString())
            ?:return "$name: la playlist #$id no está disponible. Sincroniza el catálogo"
        if(list.optString("layout")!=layout(config,mode))return "$name: la playlist no corresponde al diseño seleccionado"
        if(list.optString("orientation")!="any" && list.optString("orientation")!=model.json.optString("orientation"))return "$name: orientación incompatible"
        val items=list.optJSONArray("items")
        val count=if(mode==2)list.optInt("image_count",(0 until (items?.length()?:0)).count{items?.optJSONObject(it)?.optString("mime")?.startsWith("image/")==true})
            else list.optInt("item_count",items?.length()?:0)
        if(count<=0)return "$name: la playlist está vacía o no contiene medios reproducibles" + if(mode==2)" (Overlay requiere imágenes)" else ""
        return null
    }
    fun activationChanged(before:JSONObject,after:JSONObject):Boolean {
        val mode=after.getInt("primary_mode")
        return before.getInt("primary_mode")!=mode || layout(before,mode)!=layout(after,mode) || playlist(before,mode)!=playlist(after,mode)
    }
}

/** Motion begins outside the screen edge associated with the selected region. */
object OverlayMotion {
    fun offset(layout:String,width:Int,height:Int):Pair<Float,Float> = when(layout){
        "left"->-width.toFloat() to 0f
        "side","right","half_vertical"->width.toFloat() to 0f
        "top"->0f to -height.toFloat()
        else->0f to height.toFloat()
    }
}
