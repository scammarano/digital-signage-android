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
        val legacyMask=json.optJSONObject("device_options")?.optInt("modes_enabled",15)?:15
        val remoteMask=if(json.optInt("schema_version",2)<3 && legacyMask and 4!=0)legacyMask or 8 else legacyMask
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
        if(mode==1 && id==0){
            val legacy=entries();val full=JSONArray()
            for(i in 0 until legacy.length()){val item=legacy.optJSONObject(i)?:continue;if(item.optString("layout","fullscreen")=="fullscreen")full.put(item)}
            return full
        }
        if(id==0)return JSONArray()
        val catalog=catalog()
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
    /** Legacy portals expose one default playlist; still require explicit per-mode assignment. */
    fun catalog():JSONObject {
        json.optJSONObject("playlist_catalog")?.let{return it}
        val result=JSONObject();val fallback=json.optJSONObject("fallback")?:return result
        val id=fallback.optInt("playlist_id")
        if(id<=0)return result
        val items=fallback.optJSONArray("items")?:JSONArray()
        var images=0
        for(i in 0 until items.length())if(items.optJSONObject(i)?.optString("mime")?.startsWith("image/")==true)images++
        result.put(id.toString(),JSONObject().put("id",id).put("name",fallback.optString("playlist_name","Playlist #$id"))
            .put("layout",fallback.optString("layout","fullscreen")).put("orientation",fallback.optString("orientation",json.optString("orientation")))
            .put("items",items).put("item_count",items.length()).put("image_count",images))
        return result
    }
}

