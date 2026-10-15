package com.digitalsignage.tv

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Atomic manifest activation: last known good remains active until all media are validated. */
class CacheManager(private val context:Context,private val settings:SettingsStore) {
    companion object { private val syncLock=Any() }
    private val internal=File(context.filesDir,"signage-manifest-v1").apply{mkdirs()}
    fun active():JSONObject?=parse(File(internal,"active.json"))
    private fun parse(file:File):JSONObject?=try{if(file.isFile)JSONObject(file.readText())else null}catch(_:Exception){null}
    fun mediaFile(sha:String):File=File(settings.cacheDirectory(),sha)
    fun countMedia():Int=settings.cacheDirectory().listFiles()?.count{it.isFile&&it.name.matches(Regex("[a-f0-9]{64}"))}?:0
    fun freeBytes():Long=StatFs(settings.cacheDirectory().absolutePath).availableBytes
    fun sync(api:SignageApi,manifest:JSONObject,onProgress:(String)->Unit):Int = synchronized(syncLock) {
        val model=ManifestModel(manifest);val list=model.allMedia();val cache=settings.cacheDirectory()
        val expected=list.sumOf{maxOf(it.optLong("size",0),0)}
        val limit=settings.cacheMb.toLong()*1024L*1024L
        if(expected>limit)throw IllegalStateException("La programación excede la caché configurada: ${expected/1048576} MB")
        var done=0
        for(item in list){
            val sha=item.getString("sha256");val size=item.optLong("size",-1)
            if(size<=0||size>limit)throw IllegalStateException("Tamaño no válido para ${item.optString("name")}")
            val output=mediaFile(sha)
            if(output.isFile && output.length()==size) {
                // Validate even pre-existing cached files, not only freshly downloaded content.
                val md=MessageDigest.getInstance("SHA-256")
                output.inputStream().use { input-> val chunk=ByteArray(64*1024)
                    while(true){val n=input.read(chunk);if(n<0)break;md.update(chunk,0,n)} }
                val check=md.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
                if(check==sha){done++;continue} else output.delete()
            }
            val reserve=size+16L*1024L*1024L
            if(freeBytes()<reserve)throw IllegalStateException("Sin espacio para descargar ${item.optString("name")}")
            val tmp=File(cache,".$sha.download")
            if(tmp.exists())tmp.delete()
            try{
                onProgress("Descargando ${done+1}/${list.size}: ${item.optString("name")}")
                val conn=api.mediaConnection(item.getString("url"))
                try {
                    if(conn.responseCode!=200)throw IllegalStateException("Descarga HTTP ${conn.responseCode}")
                    val length=conn.contentLengthLong
                    if(length>size)throw IllegalStateException("Tamaño HTTP inesperado")
                    val digest=MessageDigest.getInstance("SHA-256");var count=0L
                    conn.inputStream.use{src->FileOutputStream(tmp).use{dst->
                        val chunk=ByteArray(64*1024)
                        while(true){val n=src.read(chunk);if(n<0)break;count+=n;if(count>size)throw IllegalStateException("Archivo más grande que manifiesto");digest.update(chunk,0,n);dst.write(chunk,0,n)}
                        dst.fd.sync()
                    }}
                    val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
                    if(actual!=sha || count!=size)throw IllegalStateException("SHA-256 o tamaño inválido: ${item.optString("name")}")
                    if(!tmp.renameTo(output))throw IllegalStateException("No se pudo activar el medio")
                }finally{conn.disconnect()}
                done++
            }finally{if(tmp.exists())tmp.delete()}
        }
        // Keep prior playlist content until the new manifest is complete; never switch on partial downloads.
        val now=File(internal,"active.json");val previous=File(internal,"previous.json")
        val candidate=File(internal,"candidate.json")
        candidate.writeText(manifest.toString());
        if(now.exists())now.copyTo(previous,overwrite=true)
        if(!candidate.renameTo(now))throw IllegalStateException("No se pudo activar la programación")
        // Keep media for the current AND previous manifests, then remove unreferenced completed files.
        val keep=model.allMedia().map{it.optString("sha256")}.toMutableSet()
        parse(previous)?.let{keep.addAll(ManifestModel(it).allMedia().map{x->x.optString("sha256")})}
        cache.listFiles()?.forEach{if(it.isFile&&it.name.matches(Regex("[a-f0-9]{64}"))&&!keep.contains(it.name))it.delete()}
        return done
    }
}
