package com.digitalsignage.tv

import org.json.JSONObject
import java.security.MessageDigest

/** Applies only new revisions, backs up locally, and acknowledges what really happened. */
class DeviceConfigSync(private val settings:SettingsStore, private val api:SignageApi) {
    fun poll():Boolean {
        val reply=api.desiredConfig()
        if(reply.optInt("schema")!=1)throw IllegalStateException("Esquema de configuración no soportado")
        val remoteRev=reply.getLong("revision")
        val desired=reply.getJSONObject("settings")
        val sanitized=settings.validateSettings(desired)
        if(remoteRev>settings.remoteRevision) {
            settings.backupSnapshot("before-remote-$remoteRev")
            try {
                if(!settings.applySnapshot(sanitized,remoteRev))throw IllegalStateException("No se pudo guardar configuración")
            } catch(e:Exception) {
                try{api.reportConfig(JSONObject().put("revision",settings.remoteRevision).put("status","rejected")
                  .put("settings",settings.publicSnapshot()).put("error",e.message?.take(150)?:"Error al aplicar"))}catch(_:Exception){}
                throw e
            }
        }
        val applied=settings.remoteRevision
        val snapshot=settings.publicSnapshot()
        // Canonical hash is computed by PHP from an ordered allow-listed schema. Reply carries it for the desired version.
        // A config changed locally is reported at the same revision; the portal marks it as drift.
        val response=JSONObject().put("revision",applied).put("status","applied")
            .put("settings",snapshot).put("sha256",reply.optString("sha256"))
        api.reportConfig(response)
        return applied==remoteRev
    }
}
