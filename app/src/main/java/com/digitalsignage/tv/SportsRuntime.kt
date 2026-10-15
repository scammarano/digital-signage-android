package com.digitalsignage.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.AtomicFile
import org.json.JSONObject
import services.twocam.sportscore.LocalSportsHost
import java.io.File

/** Adaptador del reproductor: la autoridad y reglas pertenecen a sports-core. */
object SportsRuntime {
    private var instance:LocalSportsHost?=null
    private var server:SportsLanServer?=null
    private var registration:NsdManager.RegistrationListener?=null
    var address:String=""; private set
    var error:String=""; private set
    @Synchronized fun host(context:Context):LocalSportsHost {
        instance?.let{return it}
        val atomic=AtomicFile(File(context.applicationContext.filesDir,"sports-authority-v1.json"))
        val saved=try{atomic.openRead().use{String(it.readBytes(),Charsets.UTF_8)}}catch(_:java.io.FileNotFoundException){null}
        return LocalSportsHost(saved){json->
            val out=atomic.startWrite()
            try{out.write(json.toByteArray(Charsets.UTF_8));atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}
        }.also{instance=it}
    }
    @Synchronized fun start(context:Context) {
        if(server!=null)return
        try{
            val h=host(context);val s=SportsLanServer(h);s.start();server=s;address=s.address;error=""
            val manager=context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
            val listener=object:NsdManager.RegistrationListener {
                override fun onServiceRegistered(info:NsdServiceInfo){}
                override fun onRegistrationFailed(info:NsdServiceInfo,code:Int){}
                override fun onServiceUnregistered(info:NsdServiceInfo){}
                override fun onUnregistrationFailed(info:NsdServiceInfo,code:Int){}
            }
            registration=listener
            try{manager.registerService(NsdServiceInfo().apply{serviceName="2Cam Sports ${h.screenId().take(8)}";serviceType="_2cam-sports._tcp.";port=8989},NsdManager.PROTOCOL_DNS_SD,listener)}catch(_:Exception){registration=null}
        }catch(e:Exception){error=e.message?:"No se pudo abrir Sports LAN";address="";server=null}
    }
    @Synchronized fun stop(context:Context) {
        server?.close();server=null;address=""
        registration?.let{try{(context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(it)}catch(_:Exception){}};registration=null
    }
    fun board(context:Context):String {
        val snap=host(context).displaySnapshot();val m=snap.getJSONObject("match");val s=m.getJSONObject("state");val names=m.getJSONArray("names");val labels=m.getJSONArray("labels");val games=s.getJSONArray("games");val won=s.getJSONArray("setsWon")
        return buildString{
            append("${names.getString(0)}    ${labels.getString(0)}    GAMES ${games.getInt(0)}    SETS ${won.getInt(0)}\n")
            append("${names.getString(1)}    ${labels.getString(1)}    GAMES ${games.getInt(1)}    SETS ${won.getInt(1)}\n")
            val history=s.getJSONArray("completedSets")
            for(i in 0 until history.length()){val r=history.getJSONObject(i);val pair=r.getJSONArray(if(r.getString("kind")=="match_tiebreak")"tiePoints" else "games");append("[${pair.getInt(0)}–${pair.getInt(1)}] ")}
            if(!s.isNull("winner"))append("  GANADOR: ${names.getString(s.getInt("winner"))}")
            else if(m.optBoolean("decidingPoint"))append("  PUNTO DECISIVO")
            else if(s.getString("phase").contains("tiebreak"))append("  ${s.getString("phase").uppercase()}")
        }
    }
}
