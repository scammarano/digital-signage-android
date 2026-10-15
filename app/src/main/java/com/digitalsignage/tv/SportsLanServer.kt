package com.digitalsignage.tv

import org.json.JSONObject
import services.twocam.sportscore.LocalSportsHost
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** API directa de controles, separada del panel LAN temporal y de sus cookies/PIN. */
class SportsLanServer(private val host:LocalSportsHost) {
    private var server:ServerSocket?=null
    private var acceptThread:Thread?=null
    private val pool=ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(12))
    @Volatile private var running=false
    var address="";private set
    fun start(){
        val ip=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback&&(it.name.startsWith("wlan")||it.name.startsWith("eth"))}.flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().firstOrNull{it.isSiteLocalAddress}?:throw IllegalStateException("Conecta el Fire a Wi-Fi o Ethernet privada")
        server=ServerSocket(8989,8,ip).apply{soTimeout=1000};address="http://${ip.hostAddress}:8989";running=true
        acceptThread=Thread({while(running){try{val c=server?.accept()?:break;try{pool.execute{handle(c)}}catch(_:Exception){c.close()}}catch(_:java.net.SocketTimeoutException){}catch(_:Exception){if(!running)break}}},"sports-lan").also{it.start()}
    }
    fun close(){running=false;try{server?.close()}catch(_:Exception){};pool.shutdownNow()}
    private fun handle(c:Socket){c.use{socket->try{
        socket.soTimeout=4000
        if(!socket.inetAddress.isSiteLocalAddress){respond(socket,403,JSONObject().put("error","Solo red local"));return}
        val input=socket.getInputStream().buffered()
        fun line(limit:Int):String{val s=StringBuilder();while(s.length<limit){val b=input.read();if(b<0||b==10)break;if(b!=13)s.append(b.toChar())};require(s.length<limit);return s.toString()}
        val first=line(2048).split(' ');require(first.size==3);val method=first[0];val path=first[1]
        val headers=mutableMapOf<String,String>();var total=0
        for(i in 0..39){val l=line(4096);total+=l.length;require(total<16384);if(l.isEmpty())break;headers[l.substringBefore(':').trim().lowercase()]=l.substringAfter(':').trim()}
        if(method!="GET"&&method!="POST"){respond(socket,405,JSONObject().put("error","Método no permitido"));return}
        if(!path.startsWith("/api/v1/")){respond(socket,404,JSONObject().put("error","Ruta no encontrada"));return}
        val origin=headers["origin"];if(origin!=null&&java.net.URI(origin).authority!=headers["host"]){respond(socket,403,JSONObject().put("error","Origen no permitido"));return}
        require(!headers.containsKey("transfer-encoding"))
        val length=headers["content-length"]?.toIntOrNull()?:0;require(length in 0..8192)
        if(method=="POST"&&!headers["content-type"].orEmpty().startsWith("application/json")){respond(socket,415,JSONObject().put("error","Se requiere JSON"));return}
        val bytes=ByteArray(length);var count=0;while(count<length){val n=input.read(bytes,count,length-count);require(n>0);count+=n}
        val body=if(method=="POST")JSONObject(String(bytes,Charsets.UTF_8)) else JSONObject()
        val result=host.request(method,path.removePrefix("/api/v1"),headers["authorization"]?.removePrefix("Bearer "),headers["idempotency-key"],body)
        respond(socket,200,result)
    }catch(e:LocalSportsHost.Failure){respond(socket,e.status,JSONObject().put("error",e.message))}
    catch(_:Exception){try{respond(socket,422,JSONObject().put("error","Solicitud Sports inválida o no se pudo guardar"))}catch(_:Exception){}}
    }}
    private fun respond(socket:Socket,status:Int,data:Any){val bytes=data.toString().toByteArray(Charsets.UTF_8);val out=socket.getOutputStream();out.write("HTTP/1.1 $status Sports\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n".toByteArray(Charsets.US_ASCII));out.write(bytes);out.flush()}
}
