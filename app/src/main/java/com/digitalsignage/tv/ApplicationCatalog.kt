package com.digitalsignage.tv

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Portal catalogue only: never a queue/scoring authority. Cache is bound to URL + device credential. */
class ApplicationCatalog(context:Context,portal:String,secret:String,val application:String){
    init{require(application in listOf("turnos","sports"))}
    private val binding=MessageDigest.getInstance("SHA-256").digest((portal+"\n"+secret).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    private val file=AtomicFile(File(context.filesDir,"application-$binding-$application.json"))
    fun cached():JSONObject?=try{
        val bytes=file.openRead().use{stream->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var n:Int
            while(stream.read(buffer).also{n=it}!=-1){require(out.size()+n<=4*1024*1024);out.write(buffer,0,n)}
            out.toByteArray()
        }
        validate(JSONObject(String(bytes,Charsets.UTF_8)),application)
    }catch(_:Exception){null}
    fun accept(value:JSONObject):JSONObject{
        val checked=validate(value,application)
        cached()?.let{old->
            require(old.getLong("tenant_id")==checked.getLong("tenant_id")){"La empresa cambió: vuelve a enlazar la pantalla"}
            require(checked.getLong("revision")>=old.getLong("revision")){"Revisión de aplicación anterior a la guardada"}
        }
        val stream=file.startWrite()
        try{stream.write(checked.toString().toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
        catch(e:Exception){file.failWrite(stream);throw e}
        return checked
    }
    fun revoke(){file.delete()}
    companion object{
        private fun integer(o:JSONObject,key:String,positive:Boolean=false):Long{
            val n=o.get(key);require(n is Number && n.toDouble()==n.toLong().toDouble())
            return n.toLong().also{require(if(positive)it>0 else it>=0)}
        }
        fun validate(value:JSONObject,app:String):JSONObject{
            require(app in listOf("turnos","sports"))
            val normalized=JSONObject(value.toString())
            require(integer(normalized,"schema_version")==1L){"Esquema de aplicación incompatible"}
            require(normalized.getString("application")==app){"Aplicación incompatible"}
            integer(normalized,"tenant_id",true);integer(normalized,"revision")
            val settings=normalized.getJSONObject("settings");require(settings.getString("title").length<=160)
            val entities=normalized.getJSONArray("entities");require(entities.length() in 2..10000)
            val rows=linkedMapOf<Long,JSONObject>()
            val allowed=if(app=="turnos")setOf("room","service","counter","screen") else setOf("venue","court","tournament","team","match","screen")
            for(i in 0 until entities.length()){
                val row=entities.getJSONObject(i);val id=integer(row,"id",true)
                require(!rows.containsKey(id)&&row.get("enabled")==true&&row.getString("kind") in allowed)
                require(row.getString("name").length<=160)
                // PHP29 serializes an empty associative configuration as []; accept only
                // that empty legacy representation, never a non-empty array of values.
                val config=row.get("config")
                if(config is org.json.JSONArray){require(config.length()==0);row.put("config",JSONObject())}
                else require(config is JSONObject)
                if(!row.isNull("parent_id"))integer(row,"parent_id",true)
                require(!row.has("user_id"));rows[id]=row
            }
            val screens=rows.values.filter{it.getString("kind")=="screen"};require(screens.size==1){"Asignación de pantalla ambigua"}
            integer(screens.single(),"device_id",true)
            val parent=rows[screens.single().getLong("parent_id")]?:error("Falta sala o pista")
            require(parent.getString("kind")==if(app=="turnos")"room" else "court")
            val scope=mutableSetOf(parent.getLong("id"))
            if(app=="sports"){
                val venue=rows[parent.getLong("parent_id")]?:error("Falta sede")
                require(venue.getString("kind")=="venue");scope.add(venue.getLong("id"))
            }
            for(row in rows.values){
                val kind=row.getString("kind");val id=row.getLong("id")
                if(kind!="screen"&&id !in scope){
                    val expected=if(kind in setOf("team","tournament"))parent.getLong("parent_id") else parent.getLong("id")
                    require(row.getLong("parent_id")==expected){"Entidad fuera de la sala o pista asignada"}
                    require(kind !in setOf("room","court","venue"))
                }
            }
            // Deep copy: callers cannot mutate the validated/cached object by retaining input references.
            return normalized
        }
        fun scopeLabel(value:JSONObject):String{
            val rows=value.getJSONArray("entities");val parts=mutableListOf<String>()
            value.getJSONObject("settings").optString("title").takeIf{it.isNotBlank()}?.let{parts.add(it)}
            for(kind in listOf("venue","room","court"))for(i in 0 until rows.length()){
                val row=rows.getJSONObject(i);if(row.getString("kind")==kind)parts.add(row.getString("name"))
            }
            return parts.joinToString(" · ")
        }
    }
}
