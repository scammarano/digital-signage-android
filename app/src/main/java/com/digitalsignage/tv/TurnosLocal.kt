package com.digitalsignage.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/** Read-only native consumer. Turnos owns every ticket operation; no queue rules in this APK. */
object TurnosLocalApi {
    class Failure(val status:Int):Exception("Turnos HTTP $status")
    fun endpoint(input:String):String {
        val uri=URI(if(input.contains("://"))input.trim() else "http://${input.trim()}")
        val host=uri.host?:error("Escribe la IP privada de Turnos")
        val parts=host.split('.');require(parts.size==4&&parts.all{it.matches(Regex("[0-9]{1,3}"))&&it.toInt() in 0..255}){"Solo IPv4 privada"}
        val n=parts.map{it.toInt()};require(n[0]==10||(n[0]==172&&n[1] in 16..31)||(n[0]==192&&n[1]==168)||n[0]==127){"Solo red privada local"}
        require(uri.scheme=="http"&&uri.userInfo==null&&uri.query==null&&uri.fragment==null&&uri.path in listOf("","/")){"Dirección local inválida"}
        val port=if(uri.port<0)8990 else uri.port;require(port in 1..65535)
        return "http://$host:$port"
    }
    fun read(base:String,path:String,token:String?=null):JSONObject {
        require(path=="/discovery"||path=="/snapshot"||path.matches(Regex("/events\\?after_sequence=[0-9]{1,16}")))
        return exchange(base,path,token,null)
    }
    fun pair(base:String,code:String):JSONObject {
        require(code.matches(Regex("[0-9]{6}"))){"Código: 6 dígitos"}
        return exchange(base,"/pair",null,JSONObject().put("name","Digital Signage · visor").put("code",code))
    }
    private fun exchange(base:String,path:String,token:String?,body:JSONObject?):JSONObject {
        val c=URL(endpoint(base)+"/api/v1"+path).openConnection() as HttpURLConnection
        try{
            c.instanceFollowRedirects=false;c.connectTimeout=2500;c.readTimeout=3500
            c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-store")
            if(token!=null){require(token.matches(Regex("[a-f0-9]{64}")));c.setRequestProperty("Authorization","Bearer $token")}
            if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val status=c.responseCode;if(status!=200)throw Failure(status)
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use{val buffer=ByteArray(8192);while(true){val count=it.read(buffer);if(count<0)break;require(out.size()+count<=1048576){"Respuesta demasiado grande"};out.write(buffer,0,count)}}
            return JSONObject(out.toString("UTF-8"))
        }finally{c.disconnect()}
    }
    fun validate(snapshot:JSONObject,id:String):JSONObject {
        require(snapshot.getString("schema")=="2cam.turnos.local.snapshot.v1"&&snapshot.getString("screen_id")==id){"Identidad de Turnos distinta"}
        require(snapshot.getString("role")=="viewer"){"Se requiere enlace de solo lectura"}
        require(snapshot.getLong("sequence")>=0&&snapshot.getString("name").length<=60&&snapshot.getString("service").length<=60)
        require(snapshot.getInt("waiting") in 0..5000&&snapshot.getInt("finished") in 0..5000)
        val calls=snapshot.getJSONArray("active_calls");require(calls.length()<=4)
        val counters=mutableSetOf<Int>()
        for(i in 0 until calls.length()){
            val call=calls.getJSONObject(i);require(call.getString("ticket_label").length in 1..24&&call.getString("counter_label").length<=80)
            require(call.getInt("counter_id") in 1..4&&counters.add(call.getInt("counter_id")))
            require(call.getString("state") in listOf("called","serving"));UUID.fromString(call.getString("ticket_id"))
        }
        return JSONObject(snapshot.toString())
    }
}

class TurnosBinding(private val context:Context) {
    private val prefs=context.getSharedPreferences("turnos_local_binding_v1",0)
    private val secret=SecureToken(context,"turnos_local")
    private val cache=AtomicFile(File(context.filesDir,"turnos-public-snapshot-v1.json"))
    var enabled:Boolean
        get()=prefs.getBoolean("enabled",false)
        set(value){check(prefs.edit().putBoolean("enabled",value).commit())}
    var audio:Boolean
        get()=prefs.getBoolean("audio",false)
        set(value){check(prefs.edit().putBoolean("audio",value).commit())}
    val base:String get()=prefs.getString("base","")?:""
    val id:String get()=prefs.getString("screen_id","")?:""
    fun token():String?=secret.load()
    fun connect(address:String,code:String) {
        val base=TurnosLocalApi.endpoint(address);val discovery=TurnosLocalApi.read(base,"/discovery")
        require(discovery.getString("mode")=="turnos");val id=discovery.getString("screen_id");UUID.fromString(id)
        val linked=TurnosLocalApi.pair(base,code)
        require(linked.getString("role")=="viewer"){"El código no era de Visor. Revoca este dispositivo en Turnos y genera un código de Visor."}
        require(linked.getString("screen_id")==id){"La identidad de la sala cambió"}
        val token=linked.getString("token");val snapshot=TurnosLocalApi.validate(TurnosLocalApi.read(base,"/snapshot",token),id)
        secret.save(token)
        if(!prefs.edit().putString("base",base).putString("screen_id",id).putBoolean("enabled",true).commit()){secret.clear();error("No se pudo guardar el enlace; revócalo en Turnos y enlaza de nuevo")}
        cache.delete();save(snapshot)
    }
    fun cached():JSONObject?=try{TurnosLocalApi.validate(JSONObject(String(cache.readFully(),Charsets.UTF_8)),id)}catch(_:Exception){null}
    fun save(snapshot:JSONObject){
        val value=TurnosLocalApi.validate(snapshot,id);var out:java.io.FileOutputStream?=null
        try{out=cache.startWrite();out.write(value.toString().toByteArray(Charsets.UTF_8));cache.finishWrite(out)}catch(e:Exception){if(out!=null)cache.failWrite(out);throw e}
    }
    fun clear(){enabled=false;secret.clear();check(prefs.edit().clear().commit());cache.delete()}
}

/** Own one-second clock, independent of playlist/download intervals. */
class TurnosConsumer(context:Context,private val changed:()->Unit,private val announce:(JSONObject)->Unit) {
    private val binding=TurnosBinding(context)
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private var epoch=0
    private var running=false
    private var busy=false
    var snapshot:JSONObject?=binding.cached();private set
    var connected=false;private set
    var error="Sin conexión · estado desactualizado";private set
    private val tick=object:Runnable{override fun run(){if(!running)return;poll();main.postDelayed(this,1000)}}
    fun start(){if(running)return;running=true;epoch++;snapshot=binding.cached();connected=false;changed();main.post(tick)}
    fun stop(){running=false;epoch++;busy=false;main.removeCallbacks(tick);connected=false}
    fun close(){stop();io.shutdownNow()}
    fun board():String {
        val snap=snapshot?:return "TURNOS\nSin estado confirmado\n$error"
        val out=StringBuilder(snap.getString("name")).append("\n").append(snap.getString("service")).append("\n")
        val calls=snap.getJSONArray("active_calls")
        if(calls.length()==0)out.append("Espere su llamado\n")
        for(i in 0 until calls.length()){val call=calls.getJSONObject(i);out.append(call.getString("ticket_label")).append("   →   ").append(call.getString("counter_label")).append(if(call.getString("state")=="serving")" · En atención" else "").append("\n")}
        out.append("En espera: ").append(snap.getInt("waiting")).append("\n")
        out.append(if(connected)"En línea · secuencia ${snap.getLong("sequence")}" else "SIN CONEXIÓN · ESTADO DESACTUALIZADO\n$error")
        return out.toString()
    }
    private fun poll(){
        if(busy||!binding.enabled)return;busy=true;val current=epoch;val previous=snapshot?.optLong("sequence",-1L)?:-1L;val wasConnected=connected
        io.execute{try{
            val token=binding.token()?:error("Enlace ausente o no se pudo descifrar");val fresh=TurnosLocalApi.validate(TurnosLocalApi.read(binding.base,"/snapshot",token),binding.id)
            require(fresh.getLong("sequence")>=previous){"La secuencia retrocedió. Revincula la sala desde Configuración."}
            val sounds=ArrayList<JSONObject>()
            if(binding.audio&&wasConnected&&previous>=0&&fresh.getLong("sequence")>previous){
                try{
                    val events=TurnosLocalApi.read(binding.base,"/events?after_sequence=$previous",token).getJSONArray("events")
                    for(i in 0 until events.length()){
                        val e=events.getJSONObject(i)
                        require(e.getString("tenant_ref")=="local:${binding.id}"&&e.getJSONObject("scope").getString("area_id")==binding.id)
                        val seq=e.getLong("sequence")
                        if(seq>previous&&seq<=fresh.getLong("sequence")&&e.getString("type") in listOf("turnos.ticket_called","turnos.call_repeated")&&System.currentTimeMillis()-e.getLong("occurred_at_ms") in 0L..8000L){
                            val payload=e.getJSONObject("payload");require(payload.getString("ticket_label").length<=24&&payload.getString("counter_label").length<=80);sounds.add(JSONObject(payload.toString()))
                        }
                    }
                }catch(_:Exception){/* rendering and confirmed attention never depend on optional sound */}
            }
            if(fresh.getLong("sequence")!=previous)binding.save(fresh)
            main.post{if(!running||current!=epoch)return@post;busy=false;snapshot=fresh;connected=true;error="";changed();for(payload in sounds)announce(payload)}
        }catch(e:Exception){main.post{if(!running||current!=epoch)return@post;busy=false;connected=false;error=when{e is TurnosLocalApi.Failure&&e.status==401->"Enlace revocado: vuelve a enlazar como Visor";e is IllegalArgumentException->e.message?:"Contrato inválido";else->"Abre Turnos y revisa la red local"};changed()}}}
    }
}
