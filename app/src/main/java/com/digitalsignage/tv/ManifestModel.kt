package com.digitalsignage.tv

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/** Locally selects the proper programming after offline time/window changes. */
class ManifestModel(val json:JSONObject){
    val timezone:ZoneId=try{ZoneId.of(json.optString("timezone","UTC"))}catch(_:Exception){ZoneId.of("UTC")}
    fun localNow():LocalDateTime = LocalDateTime.now(timezone)
    fun activeMode(settings:SettingsStore):Int {
        val override=settings.manualMode
        if(override in 1..4 && (settings.enabledModes and (1 shl (override-1)))!=0)return override
        val rows=json.optJSONArray("mode_blocks")?:JSONArray()
        val blocks=ArrayList<TimeRules.ModeBlock>()
        for(i in 0 until rows.length()){
            val r=rows.optJSONObject(i)?:continue
            blocks.add(TimeRules.ModeBlock(r.optLong("id"),r.optInt("mode"),r.optInt("days_mask",127),
                r.optString("start_time","00:00"),r.optString("end_time","23:59"),
                r.optString("valid_from").takeIf{it.isNotBlank()&&it!="null"},
                r.optString("valid_until").takeIf{it.isNotBlank()&&it!="null"},r.optInt("priority"),r.optInt("enabled",1)==1))
        }
        val remoteMask=json.optJSONObject("device_options")?.optInt("modes_enabled",15)?:15
        return TimeRules.mode(blocks,settings.primaryMode,settings.enabledModes and remoteMask,localNow())
    }
    fun entries():JSONArray {
        val rows=json.optJSONArray("schedules")?:JSONArray();val matches=ArrayList<JSONObject>();val now=localNow()
        for(i in 0 until rows.length()){
            val r=rows.optJSONObject(i)?:continue
            try{if(TimeRules.active(r.optInt("days_mask",127),r.optString("start_time",r.optString("start","08:00")),r.optString("end_time",r.optString("end","18:00")),
                    r.optString("valid_from").takeIf{it.isNotEmpty()&&it!="null"},r.optString("valid_until").takeIf{it.isNotEmpty()&&it!="null"},now))matches.add(r)}catch(_:Exception){}
        }
        val selected=matches.maxWithOrNull(compareBy<JSONObject>{it.optInt("priority")}.thenBy{it.optInt("id")})
        val selectedItems=selected?.optJSONArray("items")
        if(selectedItems!=null&&selectedItems.length()>0)return selectedItems
        return json.optJSONObject("fallback")?.optJSONArray("items") ?: JSONArray()
    }
    fun entries(settings:SettingsStore,mode:Int):JSONArray {
        val id=settings.playlistId(mode)
        if(mode==1 && id==0)return entries()
        if(id==0)return JSONArray()
        val catalog=json.optJSONObject("playlist_catalog")?:return JSONArray()
        val list=catalog.optJSONObject(id.toString())?:return JSONArray()
        val layout=settings.modeLayout(mode)
        if(list.optString("layout")!=layout)return JSONArray()
        val ori=list.optString("orientation")
        if(ori!="any" && ori!=json.optString("orientation"))return JSONArray()
        return list.optJSONArray("items")?:JSONArray()
    }
    fun allMedia():List<JSONObject>{
        val out=linkedMapOf<String,JSONObject>()
        fun add(a:JSONArray?){if(a==null)return;for(i in 0 until a.length()){
            val item=a.optJSONObject(i)?:continue;val digest=item.optString("sha256");if(Regex("[a-f0-9]{64}").matches(digest))out[digest]=item
        }}
        add(json.optJSONObject("fallback")?.optJSONArray("items"))
        val schedules=json.optJSONArray("schedules")?:JSONArray()
        for(i in 0 until schedules.length())add(schedules.optJSONObject(i)?.optJSONArray("items"))
        val catalog=json.optJSONObject("playlist_catalog")
        catalog?.keys()?.forEach{k->add(catalog.optJSONObject(k)?.optJSONArray("items"))}
        return out.values.toList()
    }
    fun widget():JSONObject = json.optJSONObject("live")?:JSONObject()
}

