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
        if(reply.optInt("schema") !in 1..2)throw IllegalStateException("Esquema de configuración no soportado")
        val wanted=reply.getLong("revision")
        val changed=wanted>settings.remoteRevision
        if(changed)audit("received",wanted,"descargada desde portal")
        val desired=if(reply.optInt("schema")==1)settings.migrateV1(reply.getJSONObject("settings")) else reply.getJSONObject("settings")
        fun reportSettings():JSONObject {
            if(reply.optInt("schema")==2)return settings.publicSnapshot()
            val actual=settings.publicSnapshot();val old=JSONObject();val keys=reply.getJSONObject("settings").keys()
            while(keys.hasNext()){val k=keys.next();when(k){
                "smart_app"->old.put(k,if(settings.primaryMode==4)"scoreboard" else "queue")
                "smart_header_pct"->old.put(k,settings.smartHeaderPct)
                "overlay_layout"->old.put(k,if(settings.overlayLayout=="side")"right" else settings.overlayLayout)
                "primary_mode"->old.put(k,if(settings.primaryMode==4)3 else settings.primaryMode)
                "enabled_modes"->old.put(k,(settings.enabledModes and 7) or (if(settings.enabledModes and 8!=0)4 else 0))
                else->if(actual.has(k))old.put(k,actual.get(k))
            }};return old
        }
        val clean=try{settings.validateSettings(desired)}catch(e:Exception){
            audit("rejected",wanted,e.message?:"opciones inválidas")
            try{
                api.reportConfig(JSONObject().put("revision",settings.remoteRevision)
                    .put("status","rejected").put("settings",reportSettings())
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
                        .put("status","rejected").put("settings",reportSettings())
                        .put("error",(e.message?:"Error al aplicar").take(150)))
                }catch(_:Exception){}
                throw e
            }
        }
        val applied=settings.remoteRevision
        api.reportConfig(JSONObject().put("revision",applied).put("status","applied")
            .put("settings",reportSettings())
            .put("sha256",reply.optString("sha256")))
        if(changed)audit("reported",applied,"confirmación aceptada por portal")
        // Applying a PIN is a separate privileged workflow: no PIN value in logs,
        // snapshots, backups, telemetry, or config schema.
        try {
            val command=api.pendingPinCommand()
            if(command.optBoolean("pending",false)){
                val rev=command.getLong("revision")
                val pin=command.getString("pin")
                settings.setPin(pin)
                check(api.acknowledgePinCommand(rev).optBoolean("applied",false)){"El portal no confirmó la revisión del PIN"}
                audit("pin_applied",rev,"PIN administrativo aplicado desde portal")
            }
        }catch(e:PortalHttpException){
            // Older portals do not have this endpoint. Keep old APK/portal pairing
            // interoperable while the operator installs the signed web upgrade.
            if(e.status!=404)throw e
        }
        return applied==wanted
    }
}

