package com.digitalsignage.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Durable sanitized queue. Uploaded events are deleted only after server acknowledgment. */
class DeviceLogs(context:Context) {
    private val dir=File(context.filesDir,"diagnostics").apply{mkdirs()}
    private val file=File(dir,"queue.jsonl")
    private val state=context.getSharedPreferences("signage_diag_v1",Context.MODE_PRIVATE)
    // Bounded cumulative archive, independent of the delivery queue. 4 x 2 MiB
    // segments survive successful uploads, offline periods and application restarts.
    private val archiveSegmentBytes=2_000_000L
    private fun archiveFile(n:Int)=File(dir,"history-$n.jsonl")
    private fun appendArchive(line:String) {
        val active=archiveFile(0)
        if(active.length()+line.toByteArray(Charsets.UTF_8).size>archiveSegmentBytes) {
            archiveFile(3).delete()
            for(n in 2 downTo 0) {
                val old=archiveFile(n)
                if(old.exists())old.renameTo(archiveFile(n+1))
            }
        }
        archiveFile(0).appendText(line)
    }
    @Synchronized fun archiveBytes():Long=(0..3).map{archiveFile(it).length()}.sum()

    private fun safe(s:String,limit:Int):String=s
        .replace(Regex("(?i)Bearer\\s+[^\\s]+"),"Bearer [REDACTED]")
        .replace(Regex("(?i)(token|password|secret|pin)[=:]\\s*[^\\s,;]+"),"$1=[REDACTED]")
        .replace(Regex("\\b[a-f0-9]{64}\\b",RegexOption.IGNORE_CASE),"[REDACTED]").take(limit)
    @Synchronized fun record(level:String,area:String,message:String,details:String="") {
        try{
            val severity=if(level in listOf("debug","info","warn","error","fatal"))level else "info"
            val event=JSONObject().put("id",UUID.randomUUID().toString().replace("-",""))
                .put("at",System.currentTimeMillis()).put("level",severity)
                .put("area",safe(area,40)).put("message",safe(message,360)).put("details",safe(details,1500))
            if(file.length()>300000){
                file.writeText(file.readLines().takeLast(350).joinToString("\n",postfix="\n"))
            }
            val line=event.toString()+"\n"
            appendArchive(line)
            file.appendText(line)
            if(level=="fatal")state.edit().putLong("last_fatal",System.currentTimeMillis()).commit()
        }catch(_:Throwable){}
    }
    fun recentCrash():Boolean=System.currentTimeMillis()-state.getLong("last_fatal",0L)<600000
    fun acknowledgeCrash(){state.edit().remove("last_fatal").apply()}
    @Synchronized fun snapshot(limit:Int=25):JSONArray {
        val out=JSONArray()
        try{file.readLines().filter{it.isNotBlank()}.take(limit).forEach{out.put(JSONObject(it))}}catch(_:Exception){}
        return out
    }
    @Synchronized fun summary():String=try{file.readLines().takeLast(30).joinToString("\n")}catch(_:Exception){"Sin eventos"}
    fun upload(api:SignageApi):Int {
        val batch=snapshot()
        if(batch.length()==0)return 0
        val response=api.submitLogs(batch).optJSONArray("accepted")?:return 0
        val ids=HashSet<String>()
        for(i in 0 until response.length()) ids.add(response.optString(i))
        synchronized(this){
            try{
                val keep=file.readLines().filter{line->
                    try{!ids.contains(JSONObject(line).optString("id"))}catch(_:Exception){false}
                }
                val tmp=File(dir,"queue.new")
                tmp.writeText(keep.joinToString("\n",postfix=if(keep.isEmpty())"" else "\n"))
                if(!tmp.renameTo(file)){file.writeText(tmp.readText());tmp.delete()}
            }catch(_:Exception){}
        }
        return ids.size
    }
    /** Resend the retained history explicitly. Server de-duplicates by event ID.
      * Acknowledgement NEVER deletes archive segments; only the delivery queue
      * is drained by upload(). This is bounded retention, not infinite storage. */
    fun uploadArchive(api:SignageApi,onProgress:(Int)->Unit={}):Int {
        var confirmed=0
        var batch=JSONArray()
        fun sendBatch() {
            if(batch.length()==0)return
            val accepted=api.submitLogs(batch).optJSONArray("accepted")
                ?:throw IllegalStateException("El portal no confirmó los eventos")
            if(accepted.length()!=batch.length())throw IllegalStateException("Confirmación de logs incompleta")
            confirmed+=accepted.length()
            onProgress(confirmed)
            batch=JSONArray()
        }
        for(n in 3 downTo 0) {
            val segment=archiveFile(n)
            if(!segment.exists())continue
            segment.forEachLine { line ->
                if(line.isNotBlank()) {
                    try{batch.put(JSONObject(line))}catch(_:Exception){}
                    if(batch.length()>=25)sendBatch()
                }
            }
        }
        sendBatch()
        return confirmed
    }

}
