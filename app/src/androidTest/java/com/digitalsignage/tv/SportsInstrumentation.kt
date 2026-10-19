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
    private var pinVerifyMs=-1L
    private val verificationDetails=StringBuilder()
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){val result=Bundle();try{exercise();result.putString("stream","SPORTS_FIRE_DIRECT_PASS\nMEDIA_FORMATS_CENTRAL_PASS\nPIN_MIGRATION_PASS subsequent_verification_ms=$pinVerifyMs\nOVERLAY_WINDOW_PASS BOOT_BACKGROUND_POLICY_PASS MODE_PLAYLIST_GATE_PASS\n$verificationDetails");finish(Activity.RESULT_OK,result)}catch(e:Throwable){result.putString("stream","SPORTS_FIRE_DIRECT_FAIL ${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}\n$verificationDetails");finish(Activity.RESULT_CANCELED,result)}}
    private fun api(method:String,path:String,token:String?=null,body:JSONObject?=null,key:String?=null):JSONObject {
        // The test is a LAN controller of the raw-socket authority. Do not relax the portal's
        // production HTTPS-only HttpURLConnection policy just to act as a local test client.
        val url=java.net.URL(SportsRuntime.address+"/api/v1"+path)
        val address=java.net.InetAddress.getByName(url.host);check(url.protocol=="http"&&address.isSiteLocalAddress)
        val payload=body?.toString()?.toByteArray(Charsets.UTF_8)?:ByteArray(0)
        java.net.Socket().use{socket->
            socket.connect(java.net.InetSocketAddress(address,url.port),4000);socket.soTimeout=4000
            val headers=buildString{
                append("$method ${url.file} HTTP/1.1\r\nHost: ${url.host}:${url.port}\r\nConnection: close\r\n")
                if(token!=null)append("Authorization: Bearer $token\r\n")
                if(key!=null)append("Idempotency-Key: $key\r\n")
                if(body!=null)append("Content-Type: application/json\r\nContent-Length: ${payload.size}\r\n")
                append("\r\n")
            }
            socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(payload);flush()}
            val reader=socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val code=reader.readLine().split(' ')[1].toInt()
            while(!reader.readLine().isNullOrEmpty()){}
            val raw=reader.readText();check(code in 200..299){"HTTP $code $raw"};return JSONObject(raw)
        }
    }

    private fun exerciseBootLocalVeto(){
        val settings=SettingsStore(targetContext)
        val snapshot=settings.publicSnapshot()
        val previousBlock=settings.localBootBlocked()
        try {
            check(settings.setBootAutoStartFromTv(false))
            check(!settings.bootAutoStartEnabled()){"TV off must veto boot"}
            check(settings.applySnapshot(JSONObject(settings.publicSnapshot().toString()).put("auto_start",true)))
            check(!settings.bootAutoStartEnabled()){"Portal sync overrode explicit local off"}
            check(settings.setBootAutoStartFromTv(true))
            check(settings.bootAutoStartEnabled()){"TV on did not clear local veto"}
            check(settings.applySnapshot(JSONObject(settings.publicSnapshot().toString()).put("auto_start",false)))
            check(!settings.bootAutoStartEnabled()){"Portal disabled boot but effective status is on"}
            println("BOOT_LOCAL_VETO_PASS manual_off_survives_portal_on portal_off_blocks_start")
        } finally {
            settings.setBootAutoStartFromTv(!previousBlock)
            check(settings.applySnapshot(snapshot))
        }
    }

    private fun exercise(){
        val formats=JSONObject().put("revision",4).put("side_width_pct",35).put("bottom_height_pct",30)
        check(MediaFormats.percent(formats,"side")==35 && MediaFormats.percent(formats,"bottom")==30)
        check(MediaFormats.percent(null,"side")==40 && MediaFormats.percent(null,"bottom")==20)
        val formatSettings=SettingsStore(targetContext)
        check(formatSettings.schemaFields().size==43)
        val channels=formatSettings.schemaFields().first{it.getString("key")=="update_channel"}.getJSONObject("options")
        check(channels.length()==2 && channels.getString("stable")=="Solo estables" && channels.getString("debug")=="Estables y pruebas")
        check(formatSettings.validateSettings(formatSettings.publicSnapshot().put("update_channel","beta")).getString("update_channel")=="stable")
        val old=formatSettings.publicSnapshot().put("overlay_width_pct",77).put("queue_height_pct",66)
        check(!formatSettings.validateSettings(old).has("overlay_width_pct"))
        var rejected=false;try{MediaFormats.validate(JSONObject().put("revision",1).put("side_width_pct",101).put("bottom_height_pct",20))}catch(_:IllegalArgumentException){rejected=true};check(rejected)
        exerciseServiceGate()
        exerciseMediaTiming()
        exercisePin()
        exerciseSettingsRemote()
        exerciseBootLocalVeto()
        exerciseLanTabs()
        exerciseOverlay()
        exerciseInstaller()
        exerciseDisplayRegions()
        waitForIdleSync();Thread.sleep(400)
        exerciseApplicationCatalog()
        val settings=SettingsStore(targetContext)
        settings.prepareProductionStartup()
        val savedSettings=settings.publicSnapshot()
        try {
            val values=settings.publicSnapshot().put("enabled_modes",15).put("primary_mode",4)
                .put("scoreboard_layout","bottom").put("scoreboard_playlist_bottom",42)
            check(settings.applySnapshot(values))
            check(settings.primaryMode==4 && settings.playlistId(4)==42)
            val model=ManifestModel(JSONObject().put("orientation","landscape")
                .put("fallback",JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("name","CARTELERA")))))
            check(model.entries(settings,3).length()==0){"Turnos inherited Cartelera"}
            check(model.entries(settings,4).length()==0){"Marcador inherited Cartelera"}
            val catalog=JSONObject().put("42",JSONObject().put("layout","bottom").put("orientation","landscape")
                .put("items",org.json.JSONArray().put(JSONObject().put("name","MARCADOR"))))
            model.json.put("playlist_catalog",catalog)
            check(model.entries(settings,4).getJSONObject(0).getString("name")=="MARCADOR")
            settings.applySnapshot(settings.publicSnapshot().put("scoreboard_layout","side"))
            check(model.entries(settings,4).length()==0){"Different geometry inherited bottom playlist"}
            try{settings.validateSettings(settings.publicSnapshot().put("overlay_opacity",101));error("Invalid opacity accepted")}catch(_:IllegalArgumentException){}
            val migrated=settings.migrateV1(JSONObject().put("primary_mode",3).put("enabled_modes",7).put("smart_app","scoreboard"))
            check(migrated.getInt("primary_mode")==4 && migrated.getInt("enabled_modes")==15)
        } finally {check(settings.applySnapshot(savedSettings));settings.manualMode=0}
        val activity=startActivitySync(Intent(targetContext,SportsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SportsActivity
        val host=SportsRuntime.host(targetContext);host.configure("Fire de prueba","Pista API22","Verde","Azul",RacketRules.standard("padel",3,"star",6,false),0)
        for(i in 0 until 30){if(SportsRuntime.address.startsWith("http://"))break;Thread.sleep(100);runOnMainSync{SportsRuntime.start(targetContext)}}
        check(SportsRuntime.address.startsWith("http://")){
            SportsRuntime.error+"; interfaces="+java.net.NetworkInterface.getNetworkInterfaces().toList().joinToString{n->n.name+":"+n.inetAddresses.toList().joinToString{it.hostAddress?:"?"}}
        }
        val path="/screens/${host.screenId()}";val code=host.openPairing("official").getString("code")
        val pair=api("POST","$path/pair",body=JSONObject().put("name","Control Android prueba").put("code",code));val token=pair.getString("controlToken")
        val accepted=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001")
        check(accepted.getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        val repeat=api("POST","$path/commands",token,JSONObject().put("type","point").put("side",0).put("expectedVersion",1),"fire-point-0001");check(repeat.getLong("sequence")==accepted.getLong("sequence"))
        Thread.sleep(900);var text="";runOnMainSync{text=(activity.window.decorView.findViewWithTag<SportsBoardView>("sports-board")).summaryText};check(text.contains("15")){"Marcador no actualizado"}
        val snap=api("GET","$path/snapshot",token);check(snap.getJSONObject("match").getInt("version")==2)
        val saved=java.io.File(targetContext.filesDir,"sports-authority-v1.json").readText();val restored=LocalSportsHost(saved){_ ->};check(restored.displaySnapshot().getJSONObject("match").getJSONArray("labels").getString(0)=="15")
        for(role in listOf("home","away")){val pin=host.openPairing(role).getString("code");api("POST","$path/pair",body=JSONObject().put("name",role).put("code",pin))}
        check(host.controls().length()==3);try{host.openPairing("home");error("Aceptó cuarto control")}catch(e:LocalSportsHost.Failure){check(e.status==409)}
        host.revoke(pair.getString("controlId"));check(host.controls().length()==2)
        runOnMainSync{activity.finish()}
    }
    private fun exerciseApplicationCatalog(){
        for(app in listOf("turnos","sports")){
            val actual=JSONObject(context.assets.open("portal-catalog-$app.json").bufferedReader().use{it.readText()})
            val parsed=ApplicationCatalog.validate(actual,app)
            check(parsed.getLong("revision")==9L)
            check(!parsed.toString().contains("privad"))
            val rows=parsed.getJSONArray("entities");for(i in 0 until rows.length())rows.getJSONObject(i).getJSONObject("config")
            check(ApplicationCatalog.scopeLabel(parsed).contains(if(app=="turnos")"Sala central" else "Pista central"))
        }
        val app="turnos";val base="https://portal.invalid/public/";val secret="catalog-ci-"+System.nanoTime()
        fun entity(id:Int,kind:String,parent:Int?=null)=JSONObject().put("id",id).put("kind",kind).put("name","$kind $id")
            .put("parent_id",parent?:JSONObject.NULL).put("enabled",true).put("config",JSONObject()).put("device_id",if(kind=="screen")91 else JSONObject.NULL)
        fun snapshot(application:String="turnos",revision:Int=2):JSONObject{
            val rows=org.json.JSONArray()
            if(application=="turnos")rows.put(entity(1,"room")).put(entity(2,"screen",1)).put(entity(3,"service",1))
            else rows.put(entity(1,"venue")).put(entity(2,"court",1)).put(entity(3,"screen",2)).put(entity(4,"team",1))
            return JSONObject().put("schema_version",1).put("application",application).put("tenant_id",7).put("revision",revision)
                .put("settings",JSONObject().put("title","Empresa de prueba")).put("entities",rows)
        }
        fun rejects(action:()->Unit){var rejected=false;try{action()}catch(_:Exception){rejected=true};check(rejected)}
        val cache=ApplicationCatalog(targetContext,base,secret,app)
        try{
            check(cache.cached()==null)
            val input=snapshot();cache.accept(input);input.put("revision",999)
            check(cache.cached()!!.getInt("revision")==2)
            check(ApplicationCatalog.scopeLabel(cache.cached()!!).contains("room 1"))
            rejects{cache.accept(snapshot(revision=1))}
            rejects{cache.accept(snapshot().put("schema_version",2))}
            rejects{cache.accept(snapshot().put("tenant_id",8))}
            rejects{cache.accept(snapshot("sports"))}
            rejects{cache.accept(snapshot().apply{getJSONArray("entities").put(entity(8,"room"))})}
            rejects{cache.accept(snapshot().apply{getJSONArray("entities").put(entity(9,"screen",1))})}
            rejects{cache.accept(snapshot().apply{getJSONArray("entities").getJSONObject(2).put("parent_id",90)})}
            rejects{cache.accept(snapshot().apply{getJSONArray("entities").getJSONObject(2).put("config",org.json.JSONArray().put("unexpected"))})}
            check(cache.cached()!!.getInt("revision")==2){"Rejected snapshot destroyed last valid copy"}
            check(ApplicationCatalog(targetContext,base,secret+"other",app).cached()==null)
            check(ApplicationCatalog(targetContext,base+"other/",secret,app).cached()==null)
            check(ApplicationCatalog(targetContext,base,secret,"sports").cached()==null)
            val sports=snapshot("sports");ApplicationCatalog.validate(sports,"sports")
            rejects{ApplicationCatalog.validate(sports.apply{getJSONArray("entities").getJSONObject(3).put("parent_id",99)},"sports")}
            cache.accept(snapshot(revision=3));check(cache.cached()!!.getInt("revision")==3)
            cache.revoke();check(cache.cached()==null)
            verificationDetails.append("APPLICATION_CATALOG_PASS real_PHP29_JSON schema scope revision isolation persistence revocation\n")
        }finally{cache.revoke()}
    }
    private fun exerciseSettingsRemote(){
        val settings=SettingsStore(targetContext);val saved=settings.publicSnapshot();settings.setPin("864209")
        val activity=startActivitySync(Intent(targetContext,SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
        fun nodes(node:android.view.accessibility.AccessibilityNodeInfo?):List<android.view.accessibility.AccessibilityNodeInfo>{
            if(node==null)return emptyList();val out=mutableListOf(node);for(i in 0 until node.childCount)out.addAll(nodes(node.getChild(i)));return out
        }
        fun findNode(test:(android.view.accessibility.AccessibilityNodeInfo)->Boolean):android.view.accessibility.AccessibilityNodeInfo{
            for(i in 0 until 50){nodes(uiAutomation.rootInActiveWindow).firstOrNull(test)?.let{return it};Thread.sleep(100)}
            error("Settings accessibility node did not appear")
        }
        fun write(value:String){val node=findNode{it.className=="android.widget.EditText"};check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))}
        fun click(text:String){val node=findNode{it.text?.toString()==text&&it.isClickable};check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))}
        fun views(view:android.view.View):List<android.view.View>{val out=mutableListOf(view);if(view is android.view.ViewGroup)for(i in 0 until view.childCount)out.addAll(views(view.getChildAt(i)));return out}
        try{
            Thread.sleep(400);write("864209");click("Entrar")
            var ready=false;for(i in 0 until 50){runOnMainSync{ready=activity.window.decorView.findViewWithTag<android.view.View>("tab:Overlay")!=null};if(ready)break;Thread.sleep(100)};check(ready){"Settings did not open after PIN"}
            runOnMainSync{activity.window.decorView.findViewWithTag<android.view.View>("tab:Overlay").performClick()}
            Thread.sleep(200)
            runOnMainSync{
                val button=views(activity.window.decorView).filterIsInstance<android.widget.Button>().first{it.text.toString().startsWith("Opacidad")}
                check(button.isFocusable&&button.width<activity.resources.displayMetrics.widthPixels*.9f);check(button.requestFocus())
            }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(200)
            check(nodes(uiAutomation.rootInActiveWindow).any{it.className=="android.widget.EditText"&&it.isFocused}){"Numeric editor not focused after remote OK"}
            write("73");click("Guardar");Thread.sleep(200)
            check(settings.overlayOpacity==73){"TV editor did not persist numeric input"}
            runOnMainSync{activity.window.decorView.findViewWithTag<android.view.View>("tab:Actualizaciones").performClick()}
            runOnMainSync{check(views(activity.window.decorView).filterIsInstance<android.widget.Button>().any{it.text=="Buscar actualización de APK ahora"})}
            verificationDetails.append("TV_TABS_DPAD_PASS PIN tabs numeric_OK_editor persistence safe_width IP\n")
        }finally{runOnMainSync{activity.finish()};check(settings.applySnapshot(saved))}
    }
    private fun exerciseLanTabs(){
        val settings=SettingsStore(targetContext);val oldPort=settings.localAdminPort
        settings.localAdminPort=18787
        val server=LocalAdminServer(targetContext,settings)
        try{
            server.start();val url=java.net.URL(server.address)
            fun request(method:String,path:String,body:String="",cookie:String=""):String{
                val bytes=body.toByteArray(Charsets.UTF_8)
                java.net.Socket().use{s->s.connect(java.net.InetSocketAddress(url.host,url.port),3000);s.soTimeout=4000
                    s.getOutputStream().apply{write(("$method $path HTTP/1.1\r\nHost: ${url.host}:${url.port}\r\nConnection: close\r\nCookie: $cookie\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray());write(bytes);flush()}
                    return s.getInputStream().bufferedReader().readText()
                }
            }
            val login=request("POST","/pair","code="+server.code)
            check(login.startsWith("HTTP/1.1 303"))
            val cookie=Regex("DSLAN=[a-f0-9]{48}").find(login)!!.value
            val page=request("GET","/",cookie=cookie)
            check(page.startsWith("HTTP/1.1 200"))
            check(Regex("data-tab-target='").findAll(page).count()==8)
            check(Regex("data-tab-panel='").findAll(page).count()==8)
            check(page.contains(DeviceNetwork.label())&&page.contains("formaction='/preview'"))
            val script=Regex("<script>(.*?)</script>").find(page)!!.groupValues[1]
            val hash=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(script.toByteArray()),android.util.Base64.NO_WRAP)
            check(page.contains("script-src 'sha256-$hash'")){"LAN tab script blocked by CSP"}
            check(request("POST","/preview","key=signage_playlist_id",cookie).startsWith("HTTP/1.1 403")){"Preview CSRF bypass"}
            verificationDetails.append("LAN_TABS_PASS authenticated form eight groups IP preview CSRF CSP\n")
        }finally{server.close();settings.localAdminPort=oldPort}
    }
    private fun exerciseInstaller(){
        val source=java.io.File(targetContext.applicationInfo.sourceDir)
        val dir=java.io.File(targetContext.filesDir,"apk-updates").apply{mkdirs()}
        val cached=java.io.File(dir,"pending.apk");source.copyTo(cached,true)
        val candidates=ApkInstaller.intents(targetContext,cached)
        val expectedScheme=if(android.os.Build.VERSION.SDK_INT<24)"file" else "content"
        check(candidates.all{it.data?.scheme==expectedScheme})
        var attempts=0
        ApkInstaller.launch(candidates){attempts++;if(attempts==1)throw android.content.ActivityNotFoundException("Fallback fixture")}
        check(attempts==2){"Missing alternate installer action"}
        var denied=false;try{ApkInstaller.launch(candidates){throw android.content.ActivityNotFoundException()}}catch(_:IllegalStateException){denied=true};check(denied)
        val resolved=candidates.firstOrNull{targetContext.packageManager.resolveActivity(it,0)!=null}
            ?:error("System installer not found for $expectedScheme")
        val installer=targetContext.packageManager.resolveActivity(resolved,0)!!.activityInfo.packageName
        if(expectedScheme=="file"){
            val copy=java.io.File(resolved.data!!.path!!)
            check(targetContext.packageManager.getPackageArchiveInfo(copy.path,0)?.packageName==targetContext.packageName){"Legacy staged APK unreadable"}
        }else{
            targetContext.contentResolver.openInputStream(resolved.data!!).use{check(it!=null&&it.read()==0x50)}
        }
        val activity=startActivitySync(Intent(targetContext,DiagnosticHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try{
            runOnMainSync{ApkInstaller.launch(candidates){activity.startActivity(it)}}
            var actual="";var screen=""
            fun text(node:android.view.accessibility.AccessibilityNodeInfo?):String {
                if(node==null)return ""
                val all=StringBuilder(node.text?.toString()?:"")
                for(i in 0 until node.childCount)all.append(' ').append(text(node.getChild(i)))
                return all.toString()
            }
            for(i in 0 until 60){val root=uiAutomation.rootInActiveWindow;actual=root?.packageName?.toString()?:"";screen=text(root);if(actual==installer&&screen.contains("2Cam Digital Signage"))break;Thread.sleep(100)}
            check(actual==installer&&screen.contains("2Cam Digital Signage")){"Installer did not parse/display application: $actual / $installer; $screen"}
            println("APK_INSTALLER_HANDOFF_PASS sdk=${android.os.Build.VERSION.SDK_INT} scheme=$expectedScheme package=$installer; no installation accepted")
            verificationDetails.append("APK_INSTALLER_HANDOFF_PASS sdk=${android.os.Build.VERSION.SDK_INT} scheme=$expectedScheme package=$installer\n")
        }finally{
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(300);runOnMainSync{activity.finish()}
            cached.delete()
        }
    }
    private fun exerciseDisplayRegions(){
        val settings=SettingsStore(targetContext);val old=settings.publicSnapshot()
        val active=java.io.File(targetContext.filesDir,"signage-manifest-v1/active.json")
        val previous=if(active.isFile)active.readBytes()else null
        val bitmap=android.graphics.Bitmap.createBitmap(1920,216,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
        val bytes=java.io.ByteArrayOutputStream().also{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val file=CacheManager(targetContext,settings).mediaFile(sha).apply{writeBytes(bytes)}
        var activity:SportsActivity?=null
        try{
            settings.applySnapshot(settings.publicSnapshot().put("scoreboard_layout","bottom").put("scoreboard_playlist_bottom",92))
            val item=JSONObject().put("sha256",sha).put("mime","image/png").put("layout","bottom").put("duration",60).put("transition","cut")
            val list=JSONObject().put("layout","bottom").put("orientation","landscape").put("items",org.json.JSONArray().put(item))
            val manifest=JSONObject().put("schema_version",3).put("orientation","landscape").put("playlist_catalog",JSONObject().put("92",list))
                .put("media_formats",JSONObject().put("revision",1).put("bottom_height_pct",20).put("side_width_pct",40))
                .put("fallback",JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("name","DO NOT INHERIT CARTELERA"))))
            active.parentFile?.mkdirs();active.writeText(manifest.toString())
            activity=startActivitySync(Intent(targetContext,SportsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SportsActivity
            var attached=false
            for(i in 0 until 60){runOnMainSync{val stage=activity!!.window.decorView.findViewWithTag<android.widget.FrameLayout>("sports-media");attached=stage.width>0&&(stage.getChildAt(0)?.height?:0)>0};if(attached)break;Thread.sleep(100)}
            check(attached){"Sports playlist never shown"};waitForIdleSync()
            runOnMainSync{
                val root=activity!!.window.decorView.findViewWithTag<RegionLayout>("sports-regions")
                val media=root.media
                check(kotlin.math.abs(media.height-root.height*.2f)<=1f){"Bottom is not 20% of full canvas: ${media.height}/${root.height}"}
                check(media.width==root.width){"Bottom does not span canvas width"}
                val box=media.getChildAt(0) as android.widget.FrameLayout
                check(box.height==media.height&&box.width==media.width){"Percentage applied a second time"}
                val image=box.getChildAt(0) as android.widget.ImageView
                check(image.height==media.height&&image.width==media.width)
                val rect=android.graphics.RectF(0f,0f,image.drawable.intrinsicWidth.toFloat(),image.drawable.intrinsicHeight.toFloat());image.imageMatrix.mapRect(rect)
                check(kotlin.math.abs(rect.width()-image.width)<2&&kotlin.math.abs(rect.height()-image.height)<2){"Compatible banner does not fill media area: $rect vs ${image.width}x${image.height}"}
                println("SPORTS_REGION_20_PASS canvas=${root.width}x${root.height}; media=${media.width}x${media.height}; image=$rect")
                verificationDetails.append("SPORTS_REGION_20_PASS canvas=${root.width}x${root.height}; media=${media.width}x${media.height}; image=$rect\n")
                for((w,h) in listOf(1920 to 1080,1280 to 720,1080 to 1920))for(p in listOf(20,30)){
                    val b=DisplayRegions.split(w,h,"bottom",p);check(b.media.height==h*p/100&&b.media.width==w&&b.board.height+b.media.height==h)
                    val s=DisplayRegions.split(w,h,"side",40);check(s.media.width==w*40/100&&s.media.height==h&&s.board.width+s.media.width==w)
                }
            }
            uiAutomation.takeScreenshot()?.let{shot->targetContext.openFileOutput("sports-region.png",0).use{shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};shot.recycle()}
        }finally{runOnMainSync{activity?.finish()};settings.applySnapshot(old);if(previous!=null)active.writeBytes(previous)else active.delete();file.delete()}
    }
    private fun exerciseServiceGate(){
        val prefs=targetContext.getSharedPreferences("signage_service_policy_v1",0);val old=prefs.all.toMap();prefs.edit().clear().commit()
        try{
            val gate=ServiceGate(targetContext);check(!gate.suspended)
            gate.accept(JSONObject().put("revision",2).put("suspended",true).put("reason","Prueba de suspensión"))
            check(gate.suspended && ServiceGate(targetContext).suspended){"Suspension did not survive a new instance"}
            gate.accept(JSONObject().put("revision",1).put("suspended",false));check(gate.suspended){"Old response resumed service"}
            try{gate.accept(JSONObject().put("revision",2).put("suspended",false));error("Conflicting service revision accepted")}catch(_:IllegalArgumentException){}
            val settings=SettingsStore(targetContext);val config=settings.publicSnapshot();check(settings.applySnapshot(config));check(gate.suspended){"Local settings bypassed suspension"}
            gate.accept(JSONObject().put("revision",3).put("suspended",false));check(!gate.suspended && gate.confirmation().getLong("revision")==3L)
            println("SERVICE_POLICY_PASS persistent suspension, stale rejection, no local override, resume")
        }finally{val e=prefs.edit().clear();for((k,v) in old)when(v){is String->e.putString(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v)};e.commit()}
    }
    private fun exerciseMediaTiming(){
        check(MediaTiming.durationMs(JSONObject().put("duration",0.1))==100L)
        check(MediaTiming.durationMs(JSONObject().put("duration",1.5))==1500L)
        check(MediaTiming.durationMs(JSONObject().put("duration",0))==100L)
        check(MediaTiming.durationMs(JSONObject())==10000L)
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val bytes=java.io.ByteArrayOutputStream().also{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val cache=CacheManager(targetContext,SettingsStore(targetContext));val file=cache.mediaFile(sha);file.writeBytes(bytes)
        val complete=java.util.concurrent.CountDownLatch(1);var elapsed=0L;var shown=0L;var failure:String?=null;var deck:MediaDeck?=null
        runOnMainSync{
            deck=MediaDeck(android.widget.FrameLayout(targetContext),cache,false)
            deck!!.display(JSONObject().put("sha256",sha).put("mime","image/png").put("duration",0.1).put("transition","cut"),
                {elapsed=android.os.SystemClock.elapsedRealtime()-shown;complete.countDown()},
                {failure=it;complete.countDown()},
                {shown=android.os.SystemClock.elapsedRealtime()})
        }
        check(complete.await(4,java.util.concurrent.TimeUnit.SECONDS)){"Short image did not complete"}
        runOnMainSync{deck?.release()}
        val original=cache.active()?.toString()
        val preview=JSONObject().put("fallback",JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("sha256",sha).put("size",bytes.size))))
        cache.preview(SignageApi("https://preview.invalid/",null),preview){}
        check(cache.active()?.toString()==original){"Preview replaced active programming"}
        cache.releasePreview()
        file.delete()
        check(failure==null && elapsed in 70L..900L){"Fractional image timing: $elapsed ms; $failure"}
        println("FRACTIONAL_MEDIA_PASS actual_image_ms=$elapsed")
    }
    private fun exercisePin(){
        val settings=SettingsStore(targetContext)
        val prefs=targetContext.getSharedPreferences("signage_settings_v1",0)
        val pin="864209";val salt=ByteArray(16){it.toByte()}
        val algorithm=if(android.os.Build.VERSION.SDK_INT>=26)"PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val hash=javax.crypto.SecretKeyFactory.getInstance(algorithm).generateSecret(javax.crypto.spec.PBEKeySpec(pin.toCharArray(),salt,120000,256)).encoded
        prefs.edit().remove("pin_mac_v2").putString("pin_salt",android.util.Base64.encodeToString(salt,2))
            .putString("pin_hash",android.util.Base64.encodeToString(hash,2)).commit()
        check(settings.verifyPin(pin)){"Legacy PIN migration failed"}
        check(prefs.contains("pin_mac_v2")&&!prefs.contains("pin_hash")){"PIN not migrated"}
        val start=android.os.SystemClock.elapsedRealtime()
        check(settings.verifyPin(pin));val elapsed=android.os.SystemClock.elapsedRealtime()-start;pinVerifyMs=elapsed
        check(!settings.verifyPin("111111")){"Wrong PIN accepted"}
        for(i in 1..4)check(!settings.verifyPin("111111"))
        try{settings.verifyPin(pin);error("PIN attempt lock bypassed")}catch(_:IllegalStateException){}
        settings.setPin(pin);check(settings.verifyPin(pin))
        println("PIN_MIGRATION_PASS subsequent_verification_ms=$elapsed")
    }
    private fun exerciseOverlay(){
        val settings=SettingsStore(targetContext);settings.prepareProductionStartup()
        val original=settings.publicSnapshot();val manual=settings.manualMode
        val active=java.io.File(targetContext.filesDir,"signage-manifest-v1/active.json")
        val originalManifest=if(active.isFile)active.readBytes() else null
        val image=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GREEN)
        val bytes=java.io.ByteArrayOutputStream().also{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();image.recycle()
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val file=CacheManager(targetContext,settings).mediaFile(sha);file.writeBytes(bytes)
        try{
            val config=JSONObject(original.toString()).put("enabled_modes",15).put("primary_mode",2).put("overlay_layout","bottom")
                .put("overlay_playlist_bottom",91).put("debug_hud_enabled",false).put("overlay_duration_sec",3)
            settings.applySnapshot(config);settings.manualMode=2
            check(ModeReadiness.error(config,null,2)!=null)
            val empty=ManifestModel(JSONObject().put("orientation","landscape"))
            check(ModeReadiness.error(config,empty,2)!=null){"Missing playlist accepted"}
            val item=JSONObject().put("mime","image/png").put("layout","bottom").put("sha256",sha).put("size",bytes.size).put("duration",0.1)
            val list=JSONObject().put("id",91).put("layout","bottom").put("orientation","landscape").put("item_count",1).put("image_count",1)
                .put("items",org.json.JSONArray().put(JSONObject(item.toString()).put("duration",0.4).put("transition","fade")).put(JSONObject(item.toString()).put("duration",0.4).put("transition","slide-left")))
            val manifest=JSONObject().put("schema_version",3).put("orientation","landscape").put("playlist_catalog",JSONObject().put("91",list))
            val model=ManifestModel(manifest)
            check(ModeReadiness.error(config,model,2)==null)
            val legacy=ManifestModel(JSONObject().put("orientation","landscape").put("fallback",JSONObject()
                .put("playlist_id",91).put("playlist_name","Franja heredada").put("layout","bottom").put("orientation","landscape")
                .put("items",org.json.JSONArray().put(item))))
            check(ModeReadiness.error(config,legacy,2)==null){"Explicit compatible legacy assignment rejected"}
            check(ModeReadiness.error(JSONObject(config.toString()).put("overlay_playlist_bottom",0),legacy,2)!=null){"Implicit Cartelera inheritance accepted"}
            list.put("image_count",0);check(ModeReadiness.error(config,model,2)!=null){"Overlay without image accepted"};list.put("image_count",1)
            check(StartupPolicy.mode(settings,model)==2 && StartupPolicy.background(2))
            check(!StartupPolicy.background(1) && !StartupPolicy.background(3))
            check(OverlayMotion.offset("bottom",80,20).second>0 && OverlayMotion.offset("side",80,20).first>0)
            check(OverlayMotion.offset("top",80,20).second<0 && OverlayMotion.offset("left",80,20).first<0)
            active.parentFile?.mkdirs();active.writeText(manifest.toString())
            settings.applySnapshot(JSONObject(config.toString()).put("primary_mode",1));settings.manualMode=0
            val before=OverlayService.lastShownAt;val shownBefore=OverlayService.itemsShown
            val gate=ServiceGate(targetContext)
            gate.accept(JSONObject().put("revision",10).put("suspended",true))
            runOnMainSync{AndroidCompat.startServiceCompat(targetContext,Intent(targetContext,OverlayService::class.java).setAction(OverlayService.ACTION_PREVIEW))}
            Thread.sleep(1200)
            check(!OverlayService.visible && OverlayService.lastShownAt==before){"Suspended Overlay became visible"}
            gate.accept(JSONObject().put("revision",11).put("suspended",false))
            runOnMainSync{AndroidCompat.startServiceCompat(targetContext,Intent(targetContext,OverlayService::class.java).setAction(OverlayService.ACTION_PREVIEW))}
            for(i in 0 until 60){if(OverlayService.lastShownAt>before)break;Thread.sleep(100)}
            check(OverlayService.lastShownAt>before){"Overlay window was not attached"}
            for(i in 0 until 100){if(OverlayService.lastHiddenAt>=OverlayService.lastShownAt)break;Thread.sleep(50)}
            val visibleMs=OverlayService.lastHiddenAt-OverlayService.lastShownAt
            check(visibleMs in 2700L..4200L){"Overlay playlist window: $visibleMs ms"}
            check(OverlayService.itemsShown-shownBefore>=2){"Overlay showed only one image"}
            check(OverlayService.windowSlots and 3L==3L){"Overlay did not advance A/B within the same window"}
            println("OVERLAY_PLAYLIST_WINDOW_PASS actual_window_ms=$visibleMs; items=${OverlayService.itemsShown-shownBefore}")
            check(settings.primaryMode==1 && settings.manualMode==0){"Preview changed saved mode"}
            println("OVERLAY_WINDOW_PASS BOOT_BACKGROUND_POLICY_PASS MODE_PLAYLIST_GATE_PASS")
            println("SERVICE_OVERLAY_PASS suspended window blocked, resume displayed")
        }finally{
            runOnMainSync{targetContext.stopService(Intent(targetContext,OverlayService::class.java))}
            check(settings.applySnapshot(original));settings.manualMode=manual
            if(originalManifest!=null)active.writeBytes(originalManifest)else active.delete()
            file.delete()
            targetContext.getSharedPreferences("signage_service_policy_v1",0).edit().clear().commit()
        }
    }
}

