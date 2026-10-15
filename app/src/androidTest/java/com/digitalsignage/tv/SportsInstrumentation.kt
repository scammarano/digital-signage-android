package com.digitalsignage.tv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import services.twocam.sportscore.RacketRules
import services.twocam.sportscore.LocalSportsHost

/** API22 real: servidor dentro de APK, sin Node ni computadora como autoridad. */
class SportsInstrumentation:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){val result=Bundle();try{exercise();result.putString("stream","SPORTS_FIRE_DIRECT_PASS\n");finish(Activity.RESULT_OK,result)}catch(e:Throwable){result.putString("stream","SPORTS_FIRE_DIRECT_FAIL ${e.javaClass.simpleName}: ${e.message}\n");finish(Activity.RESULT_CANCELED,result)}}
    private fun api(method:String,path:String,token:String?=null,body:JSONObject?=null,key:String?=null):JSONObject {
        val c=java.net.URL(SportsRuntime.address+"/api/v1"+path).openConnection() as java.net.HttpURLConnection
        c.connectTimeout=4000;c.readTimeout=4000;c.requestMethod=method
        if(token!=null)c.setRequestProperty("Authorization","Bearer $token");if(key!=null)c.setRequestProperty("Idempotency-Key",key)
        try{if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=c.responseCode;val raw=(if(code>=400)c.errorStream else c.inputStream).bufferedReader().use{it.readText()};check(code in 200..299){"HTTP $code $raw"};return JSONObject(raw)
        }finally{c.disconnect()}
    }
    private fun exercise(){
        val activity=startActivitySync(Intent(targetContext,SportsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SportsActivity
        val host=SportsRuntime.host(targetContext);host.configure("Fire de prueba","Pista API22","Verde","Azul",RacketRules.standard("padel",3,"star",6,false),0)
        check(SportsRuntime.address.startsWith("http://")){SportsRuntime.error}
        val path="/screens/${host.screenId()}";val code=host.openPairing("official").getString("code")
        val pair=api("POST","$path/pair",body=JSONObject().put("name","Control Android prueba").put("code",code));val token=pair.getString("controlToken")
        val accepted=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001")
        check(accepted.getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        val repeat=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001");check(repeat.getLong("sequence")==accepted.getLong("sequence"))
        Thread.sleep(900);var text="";runOnMainSync{text=(activity.window.decorView.findViewWithTag<android.widget.TextView>("sports-board")).text.toString()};check(text.contains("15")){"Marcador no actualizado"}
        val snap=api("GET","$path/snapshot",token);check(snap.getJSONObject("match").getInt("version")==2)
        val saved=java.io.File(targetContext.filesDir,"sports-authority-v1.json").readText();val restored=LocalSportsHost(saved){_ ->};check(restored.displaySnapshot().getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        for(role in listOf("home","away")){val pin=host.openPairing(role).getString("code");api("POST","$path/pair",body=JSONObject().put("name",role).put("code",pin))}
        check(host.controls().length()==3);try{host.openPairing("home");error("Aceptó cuarto control")}catch(e:LocalSportsHost.Failure){check(e.status==409)}
        host.revoke(pair.getString("controlId"));check(host.controls().length()==2)
        runOnMainSync{activity.finish()}
    }
}
