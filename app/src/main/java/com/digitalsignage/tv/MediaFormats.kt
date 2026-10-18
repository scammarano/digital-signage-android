package com.digitalsignage.tv

import org.json.JSONObject

/** Global media geometry from the cached, authenticated portal manifest. Never a device override. */
object MediaFormats {
    fun validate(input:JSONObject?):JSONObject {
        val v=input?:JSONObject().put("revision",0).put("bottom_height_pct",20).put("side_width_pct",40)
        require(v.optLong("revision",-1)>=0){"Revisión de formatos inválida"}
        for(k in listOf("bottom_height_pct","side_width_pct"))require(v.get(k) is Int && v.getInt(k) in 5..80){"Formato central inválido: $k"}
        return v
    }
    fun percent(input:JSONObject?,layout:String):Int=when(layout){
        "side","left","right"->validate(input).getInt("side_width_pct")
        "bottom","top"->validate(input).getInt("bottom_height_pct")
        "half_horizontal","half_vertical"->50
        else->100
    }
}
