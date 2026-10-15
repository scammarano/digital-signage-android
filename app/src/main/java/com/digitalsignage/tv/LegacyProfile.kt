package com.digitalsignage.tv

import android.os.Build

object LegacyProfile {
    fun description(): String = when {
        Build.VERSION.SDK_INT <= 20 -> "Android antiguo: caché ligera; priorizar JPG y MP4 H.264 a 480p/720p"
        Build.VERSION.SDK_INT <= 22 -> "Fire OS 5 / Android 5.1: priorizar MP4 H.264 720p"
        else -> "Perfil estándar; resolución según capacidades de este equipo"
    }
}
