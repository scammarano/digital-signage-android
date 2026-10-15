package com.digitalsignage.tv

import org.json.JSONObject

/**
 * Four-stage remote configuration lifecycle, compatible with the existing portal.
 * Receipt and runtime stages are sent as diagnostic events; the old applied ACK
 * remains unchanged until the web backend gains a dedicated lifecycle endpoint.
 */
class DeviceConfigSync(
    private val settings:SettingsStore,
    private val api:SignageApi,
    private val audit:(String,Long,String)->Unit={_,_,_->}
){
    fun poll():Boolean {
        val reply=api.desiredConfig()
        if(reply.optInt("schema")!=1)throw IllegalStateException("Esquema de configuración no soportado")
        val wanted=reply.getLong("revision")
        val changed=wanted>settings.remoteRevision
        if(changed)audit("received",wanted,"descargada desde portal")
        val desired=reply.getJSONObject("settings")
        val clean=try{settings.validateSettings(desired)}catch(e:Exception){
            audit("rejected",wanted,e.message?:"opciones inválidas")
            try{
                api.reportConfig(JSONObject().put("revision",settings.remoteRevision)
                    .put("status","rejected").put("settings",settings.publicSnapshot())
                    .put("error",(e.message?:"validación").take(150)))
            }catch(_:Exception){}
            throw e
        }
        if(changed) {
            settings.backupSnapshot("before-remote-$wanted")
            try {
                if(!settings.applySnapshot(clean,wanted))
                    throw IllegalStateException("No se pudo persistir configuración")
                audit("applied",wanted,"guardada en el equipo")
            }catch(e:Exception) {
                audit("rejected",wanted,e.message?:"error local")
                try {
                    api.reportConfig(JSONObject().put("revision",settings.remoteRevision)
                        .put("status","rejected").put("settings",settings.publicSnapshot())
                        .put("error",(e.message?:"Error al aplicar").take(150)))
                }catch(_:Exception){}
                throw e
            }
        }
        val applied=settings.remoteRevision
        api.reportConfig(JSONObject().put("revision",applied).put("status","applied")
            .put("settings",settings.publicSnapshot())
            .put("sha256",reply.optString("sha256")))
        if(changed)audit("reported",applied,"confirmación aceptada por portal")
        return applied==wanted
    }
}
