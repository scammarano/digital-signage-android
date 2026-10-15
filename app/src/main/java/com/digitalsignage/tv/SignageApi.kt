package com.digitalsignage.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** No networking on main thread. HTTPS only and bearer credentials never put in URLs. */
class SignageApi(private val baseUrl:String, private val token:String?) {
    init { require(baseUrl.startsWith("https://") && baseUrl.endsWith("/")) }
    fun pair(code:String):JSONObject {
        require(Regex("[0-9]{6}").matches(code))
        return call("api-device.php?op=pair",JSONObject().put("code",code))
    }
    fun manifest()=call("api-device.php?op=manifest")
    fun live()=call("api-device.php?op=live")
    fun desiredConfig()=call("api-device-config.php?op=desired")
    fun reportConfig(data:JSONObject)=call("api-device-config.php?op=report",data)
    fun heartbeat(data:JSONObject)=call("api-device.php?op=heartbeat",data)
    private fun call(path:String,payload:JSONObject?=null):JSONObject {
        val url=URL(baseUrl+path)
        val conn=url.openConnection() as HttpsURLConnection
        try {
            conn.instanceFollowRedirects=false;conn.connectTimeout=15000;conn.readTimeout=20000
            conn.setRequestProperty("Accept","application/json")
            conn.setRequestProperty("Cache-Control","no-store")
            if(token!=null)conn.setRequestProperty("Authorization","Bearer $token")
            if(payload!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}}
            val code=conn.responseCode
            val raw=(if(code in 200..299)conn.inputStream else conn.errorStream)?.use{readBounded(it,4*1024*1024)}?:""
            if(code !in 200..299){
                val msg=try{JSONObject(raw).optString("error","HTTP $code")}catch(_:Exception){"HTTP $code"}
                throw IllegalStateException(msg.take(250))
            }
            return JSONObject(raw)
        }finally{conn.disconnect()}
    }
    private fun readBounded(stream:java.io.InputStream,limit:Int):String {
        val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);var n:Int
        while(stream.read(buf).also{n=it}!=-1){if(out.size()+n>limit)throw IllegalStateException("Respuesta demasiado grande");out.write(buf,0,n)}
        return out.toString("UTF-8")
    }
    fun mediaConnection(source:String): HttpsURLConnection {
        val dest=URL(source);val root=URL(baseUrl)
        require(dest.protocol=="https" && dest.host.equals(root.host,true) && dest.port==root.port && dest.path.startsWith(root.path)){"Medio fuera del servidor autorizado"}
        val conn=dest.openConnection() as HttpsURLConnection
        conn.instanceFollowRedirects=false;conn.connectTimeout=20000;conn.readTimeout=40000
        if(token!=null)conn.setRequestProperty("Authorization","Bearer $token")
        conn.setRequestProperty("Accept","*/*")
        return conn
    }
}
