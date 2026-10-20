package com.digitalsignage.tv

/**
 * Privileged PIN commands are deliberately independent from the public
 * configuration lifecycle. The device must already have a valid paired token.
 * No PIN or bearer token may be written to logs, backups or diagnostics.
 */
class DevicePinSync(
    private val settings:SettingsStore,
    private val api:SignageApi,
    private val audit:(String,Long,String)->Unit={_,_,_->}
) {
    fun poll():Boolean {
        val command=try { api.pendingPinCommand() } catch(e:PortalHttpException) {
            if(e.status==404)return false // Compatibility with previous portals.
            throw e
        }
        if(!command.optBoolean("pending",false))return false
        val revision=command.getLong("revision")
        require(revision>0){"Revisión de PIN inválida"}
        val localRevision=settings.remotePinRevision()
        if(revision>localRevision) {
            // Store a new MAC before advancing the local revision. An ACK may be
            // retried indefinitely after a lost response without changing the PIN.
            settings.setPin(command.getString("pin"))
            settings.setRemotePinRevision(revision)
            audit("pin_remote_stored",revision,"PIN recibido y guardado")
        } else if(revision<localRevision || !settings.hasPin()) {
            throw IllegalStateException("Revisión de PIN incongruente; solicita renovación desde portal")
        }
        check(api.acknowledgePinCommand(revision).optBoolean("applied",false)) {
            "No se confirmó la aplicación del PIN"
        }
        audit("pin_remote_applied",revision,"ACK de PIN confirmado")
        return true
    }
}
