package com.digitalsignage.tv

import org.json.JSONObject
import kotlin.math.roundToLong

/** Milliseconds throughout the player; JSON seconds may contain tenths. */
object MediaTiming {
    fun durationMs(item:JSONObject):Long {
        val raw=item.optDouble("duration",10.0)
        val seconds=if(raw.isNaN()||raw.isInfinite())10.0 else raw.coerceIn(0.1,86400.0)
        return (seconds*1000.0).roundToLong()
    }
}
