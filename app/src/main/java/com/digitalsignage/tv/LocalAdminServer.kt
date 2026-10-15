package com.digitalsignage.tv

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Temporary LAN-only configuration endpoint. No bearer tokens/PINs in responses or backups. */
class LocalAdminServer(private val context:Context,private val settings:SettingsStore) {
    private val random=SecureRandom()
    private var server:ServerSocket?=null
    private val executor=Executors.newFixedThreadPool(4)
    @Volatile private var running=false
    @Volatile private var sessionHash=""
    @Volatile private var sessionExpiry=0L
    @Volatile private var failed=0
    @Volatile private var lockedUntil=0L
    @Volatile var address=""
      private set
    @Volatile var code=""
      private set
    @Volatile var expires=0L
      private set
    fun start() {
        require(!running)
        val ip=lanIp()?:throw IllegalStateException("Conecta este equipo a una red Wi-Fi o Ethernet privada")
        // Bind only the WiFi/Ethernet private IPv4 address, never a public interface.
        server=ServerSocket(settings.localAdminPort,8,ip).apply{soTimeout=1000}
        address="http://${ip.hostAddress}:${settings.localAdminPort}/"
        code=(100000+random.nextInt(900000)).toString()
        expires=System.currentTimeMillis()+30*60*1000L
        running=true
        executor.execute{
            while(running && System.currentTimeMillis()<expires){
                try{val client=server?.accept()?:break;executor.execute{handle(client)}}
                catch(_:java.net.SocketTimeoutException){continue}
                catch(_:Exception){if(!running)break}
            }
            close()
        }
    }
    fun close(){running=false;try{server?.close()}catch(_:Exception){};settings.setLocalAdminEnabled(false);code="";sessionHash="";executor.shutdownNow()}
    fun isRunning()=running && System.currentTimeMillis()<expires
    private fun lanIp():Inet4Address? {
        val interfaces=NetworkInterface.getNetworkInterfaces()?:return null
        while(interfaces.hasMoreElements()) {
            val iface=interfaces.nextElement()
            if(!iface.isUp||iface.isLoopback || !(iface.name.startsWith("wlan")||iface.name.startsWith("eth")))continue
            val addrs=iface.inetAddresses
            while(addrs.hasMoreElements()){
                val a=addrs.nextElement()
                if(a is Inet4Address && a.isSiteLocalAddress)return a
            }
        }
        return null
    }
    private fun html(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    private fun randomSecret()=ByteArray(24).also{random.nextBytes(it)}.joinToString(""){"%02x".format(it)}
    private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun validSession(cookie:String?):Boolean {
        val raw=Regex("(?:^|;\\s*)DSLAN=([0-9a-f]{48})").find(cookie?:"")?.groupValues?.get(1)?:return false
        return sessionExpiry>System.currentTimeMillis() && MessageDigest.isEqual(sha(raw).toByteArray(),sessionHash.toByteArray())
    }
    private fun form(body:String):Map<String,String> = body.split('&').take(25).filter{it.contains('=')}.associate {
        val i=it.indexOf('=');URLDecoder.decode(it.substring(0,i),"UTF-8") to URLDecoder.decode(it.substring(i+1),"UTF-8")
    }
    private fun handle(socket:Socket) {socket.use{c->
      try {
        c.soTimeout=4000
        if(!c.inetAddress.isSiteLocalAddress){respond(c,403,"Acceso solo desde la red local");return}
        val reader=BufferedInputStream(c.getInputStream())
        fun line(max:Int):String {
            val out=StringBuilder();while(out.length<max){val b=reader.read();if(b<0 || b==10)break;if(b!=13)out.append(b.toChar())}
            if(out.length>=max)throw IllegalArgumentException("Cabecera excesiva")
            return out.toString()
        }
        val first=line(2048).split(' ');if(first.size<3){respond(c,400,"Solicitud inválida");return}
        val method=first[0];val path=first[1].substringBefore('?')
        val headers=mutableMapOf<String,String>()
        for(i in 0 until 40){val l=line(4096);if(l.isEmpty())break;val k=l.substringBefore(':').trim().lowercase();headers[k]=l.substringAfter(':',"").trim()}
        val length=headers["content-length"]?.toIntOrNull()?:0
        if(length<0 || length>8192){respond(c,413,"Solicitud excesiva");return}
        if(method!="GET"&&method!="POST"){respond(c,405,"Método no permitido");return}
        val body=ByteArray(length);var i=0;while(i<length){val n=reader.read(body,i,length-i);if(n<0)throw IllegalArgumentException("Solicitud incompleta");i+=n}
        val params=form(String(body,Charsets.UTF_8))
        val authorized=validSession(headers["cookie"])
        if(method=="POST" && path=="/pair") {
            if(System.currentTimeMillis()<lockedUntil){respond(c,429,"Espera cinco minutos");return}
            val entered=params["code"]?:""
            if(!isRunning()||entered.length!=6||!MessageDigest.isEqual(entered.toByteArray(),code.toByteArray())){
                failed++;if(failed>=6){lockedUntil=System.currentTimeMillis()+300000;failed=0}
                respond(c,401,"Código inválido o expirado");return
            }
            failed=0;code="";val session=randomSecret();sessionHash=sha(session);sessionExpiry=System.currentTimeMillis()+20*60*1000L
            respond(c,303,"", "Location: /\r\nSet-Cookie: DSLAN=$session; HttpOnly; SameSite=Strict; Path=/; Max-Age=1200\r\n")
            return
        }
        if(!authorized) {
            if(method!="GET"||path!="/"){respond(c,401,"Autorización requerida");return}
            respond(c,200,page("<h2>Vincular navegador</h2><p>Introduce el código que aparece en el televisor.</p><form method=post action='/pair'><input name=code inputmode=numeric maxlength=6 required><button>Entrar</button></form>"),"", "text/html; charset=utf-8")
            return
        }
        if(method=="GET"&&path=="/") {
            val snap=settings.publicSnapshot()
            val csrf=sha(sessionHash+"ds-lan-csrf")
            val opts=StringBuilder()
            val boolFields=listOf("auto_start" to "Inicio automático","splash" to "Mostrar logo","video_audio" to "Audio de video","offline_playback" to "Reproducción offline","external_cache" to "Usar caché externa de app")
            boolFields.forEach{(k,label)->opts.append("<label><input type=checkbox name='$k' value='1' ${if(snap.getBoolean(k)) "checked" else ""}>${html(label)}</label>")}
            for((k,label) in listOf("enabled_modes" to "Modos (máscara 1..7)","sync_minutes" to "Sincronización (min)","cache_mb" to "Caché (MB)","overlay_interval_sec" to "Intervalo Overlay (seg)","overlay_duration_sec" to "Duración Overlay (seg)"))opts.append("<label>${html(label)}<input type=number name='$k' value='${snap.getInt(k)}' required></label>")
            val content="<h2>Digital Signage · Panel local</h2><p>Este panel solo opera en la LAN. Se cerrará automáticamente en 30 minutos.</p><form method=post action='/save'><input type=hidden name=csrf value='$csrf'>$opts<button>Guardar configuración local</button></form><h3>Respaldo</h3><a href='/backup'>Descargar JSON (sin credenciales)</a><form method=post action='/restore'><input type=hidden name=csrf value='$csrf'><textarea name=json rows=7 placeholder='Pega el contenido JSON del respaldo'></textarea><button>Restaurar JSON</button></form><form method=post action='/stop'><input type=hidden name=csrf value='$csrf'><button>Cerrar panel local</button></form>"
            respond(c,200,page(content),"", "text/html; charset=utf-8");return
        }
        if(method=="GET"&&path=="/backup") {
            val exported=JSONObject().put("format","digital-signage-settings").put("schema",1).put("settings",settings.publicSnapshot())
            respond(c,200,exported.toString(2),"Content-Disposition: attachment; filename=signage-settings.json\r\n","application/json; charset=utf-8");return
        }
        if(method=="POST"){
            if(params["csrf"]!=sha(sessionHash+"ds-lan-csrf")){respond(c,403,"Solicitud no autorizada");return}
            if(path=="/stop"){respond(c,200,page("<p>Panel local cerrado.</p>"),"","text/html; charset=utf-8");close();return}
            if(path=="/save"){
                val o=JSONObject();for(k in listOf("auto_start","splash","video_audio","offline_playback","external_cache"))o.put(k,params[k]=="1")
                for(k in listOf("enabled_modes","sync_minutes","cache_mb","overlay_interval_sec","overlay_duration_sec"))o.put(k,params[k]?.toIntOrNull()?:-1)
                settings.validateSettings(o);settings.backupSnapshot("local-web-before-save")
                if(!settings.applySnapshot(o))throw IllegalStateException("No se pudo guardar");respond(c,303,"","Location: /\r\n");return
            }
            if(path=="/restore"){
                val input=JSONObject(params["json"]?:"")
                require(input.optString("format")=="digital-signage-settings"&&input.optInt("schema")==1)
                val o=settings.validateSettings(input.getJSONObject("settings"))
                settings.backupSnapshot("local-web-before-restore")
                if(!settings.applySnapshot(o))throw IllegalStateException("No se pudo restaurar")
                respond(c,303,"","Location: /\r\n");return
            }
        }
        respond(c,404,"Ruta no encontrada")
      }catch(e:Exception){try{respond(c,400,"Solicitud inválida: ${e.javaClass.simpleName}")}catch(_:Exception){} }
    }}
    private fun page(content:String)="""<!doctype html><html lang='es'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Digital Signage · Local</title><style>body{font:16px system-ui;background:#ecf1f7;color:#14243b;margin:0;padding:30px}main{max-width:740px;margin:auto;background:white;padding:24px;border-radius:12px;box-shadow:0 8px 30px #0002}label{display:block;margin:12px 0}input,textarea{padding:10px;border:1px solid #cbd5e1;border-radius:6px;max-width:100%;box-sizing:border-box}textarea{width:100%}button{background:#174589;color:white;border:0;padding:10px 18px;border-radius:6px;cursor:pointer;margin:12px 0}form{border-bottom:1px solid #ddd;margin-bottom:20px}a{color:#174589}</style></head><body><main>$content</main></body></html>"""
    private fun respond(c:Socket,status:Int,body:String,extra:String="",type:String="text/plain; charset=utf-8") {
        val bytes=body.toByteArray(Charsets.UTF_8)
        val out=BufferedOutputStream(c.getOutputStream())
        val reason=when(status){200->"OK";303->"See Other";400->"Bad Request";401->"Unauthorized";403->"Forbidden";404->"Not Found";405->"Method Not Allowed";413->"Payload Too Large";429->"Too Many Requests";else->"Error"}
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'\r\n$extra\r\n".toByteArray())
        out.write(bytes);out.flush()
    }
}
