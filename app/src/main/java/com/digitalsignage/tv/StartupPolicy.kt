package com.digitalsignage.tv

/** Runtime selection at boot is identical to normal playback, including manual overrides. */
object StartupPolicy {
    fun mode(settings:SettingsStore,model:ManifestModel?):Int = model?.activeMode(settings)
        ?: if(settings.manualMode in 1..4)settings.manualMode else settings.primaryMode
    fun background(mode:Int)=mode==2
}
