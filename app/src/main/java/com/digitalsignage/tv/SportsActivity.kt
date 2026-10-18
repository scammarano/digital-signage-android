package com.digitalsignage.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import services.twocam.sportscore.RacketRules

/** Pantalla Sports directa: configuración física y render, sin reglas dentro del reproductor. */
class SportsActivity:Activity(){
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var board:SportsBoardView
    private lateinit var info:TextView
    private var deck:MediaDeck?=null
    private var playing=false
    private val refresh=object:Runnable{override fun run(){try{
        SportsRuntime.start(this@SportsActivity)
        val host=SportsRuntime.host(this@SportsActivity);val snap=host.displaySnapshot()
        board.update(snap,SportsRuntime.board(this@SportsActivity))
        info.text="${SportsRuntime.address.ifEmpty{SportsRuntime.error}} · ${host.controls().length()}/3 controles"+
            (snap.optJSONObject("pairing")?.let{"\nCÓDIGO DE ENLACE: ${it.getString("code")}"}?:"\nAbre un código para enlazar tu tablet")
    }catch(e:Exception){info.text="Sports: ${e.message}"};ui.postDelayed(this,400)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(ScreenStyles.BACKGROUND)}
        board=SportsBoardView(this).apply{tag="sports-board"}
        panel.addView(board,LinearLayout.LayoutParams(-1,0,1f))
        info=ScreenStyles.label(this,"",16f).apply{tag="sports-address"};panel.addView(info)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun action(title:String,tag:String,doIt:()->Unit){val button=ScreenStyles.button(this,title){doIt()}.apply{this.tag=tag};row.addView(button,LinearLayout.LayoutParams(0,-2,1f))}
        action("Nuevo encuentro","sports-configure"){AlertDialog.Builder(this).setTitle("Configurar encuentro").setMessage("Se iniciará un encuentro nuevo. Los controles enlazados se conservan y el marcador anterior queda en la auditoría local.").setNegativeButton("Cancelar",null).setPositiveButton("Continuar"){_,_->configure()}.show()}
        action("Enlazar control","sports-pair"){AlertDialog.Builder(this).setTitle("Permiso del control").setItems(arrayOf("Anotador oficial","Auxiliar A","Auxiliar B")){_,which->try{SportsRuntime.host(this).openPairing(arrayOf("official","home","away")[which]);refreshNow()}catch(e:Exception){error(e)}}.show()}
        action("Controles / revocar","sports-controls"){val host=SportsRuntime.host(this);val controls=host.controls();if(controls.length()==0){Toast.makeText(this,"Sin controles enlazados",Toast.LENGTH_LONG).show()}else{val labels=Array(controls.length()){i->val c=controls.getJSONObject(i);"${c.getString("name")} · ${c.getString("role")}"};AlertDialog.Builder(this).setTitle("Selecciona para desvincular").setItems(labels){_,i->AlertDialog.Builder(this).setMessage("¿Desvincular ${controls.getJSONObject(i).getString("name")}?").setNegativeButton("Cancelar",null).setPositiveButton("Desvincular"){_,_->try{host.revoke(controls.getJSONObject(i).getString("id"));refreshNow()}catch(e:Exception){error(e)}}.show()}.show()}}
        action("Volver","sports-back"){finish()};panel.addView(row)
        val settings=SettingsStore(this);val cache=CacheManager(this,settings)
        val manifest=cache.active()?.let{ManifestModel(it)};val entries=manifest?.entries(settings,4)
        val media=FrameLayout(this).apply{tag="sports-media";setBackgroundColor(android.graphics.Color.BLACK)}
        val layout=settings.modeLayout(4)
        val root=RegionLayout(this,layout,settings.mediaFormatPercent(layout),panel,media).apply{tag="sports-regions"}
        root.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            DeviceLogs(this).record("info","display_region","Sports LAN · $layout","canvas=${root.width}x${root.height}; media=${media.width}x${media.height}; formato=${settings.mediaFormatPercent(layout)}%")
        }
        if(entries!=null&&entries.length()>0){deck=MediaDeck(media,cache,false,true);var index=0
            fun next(){if(!playing)return;val item=entries.optJSONObject(index++%entries.length())?:return;deck?.display(item,{next()},{ui.postDelayed({next()},2000)})}
            playing=true;media.post{next()}
        }
        setContentView(root)
    }
    override fun onStart(){super.onStart();SportsRuntime.start(this);ui.post(refresh)}
    override fun onStop(){ui.removeCallbacks(refresh);SportsRuntime.stop(this);super.onStop()}
    override fun onDestroy(){playing=false;deck?.clear();ui.removeCallbacksAndMessages(null);super.onDestroy()}
    private fun refreshNow(){ui.removeCallbacks(refresh);ui.post(refresh)}
    private fun error(e:Exception){AlertDialog.Builder(this).setMessage(e.message?:"No se pudo completar").setPositiveButton("Aceptar",null).show()}
    private fun configure(){
        val content=ScreenStyles.column(this);val snap=SportsRuntime.host(this).displaySnapshot();val current=snap.getJSONObject("match")
        fun field(label:String,value:String):EditText{content.addView(ScreenStyles.label(this,label,15f));return EditText(this).apply{setText(value);isSingleLine=true;setTextColor(android.graphics.Color.BLACK);setBackgroundColor(android.graphics.Color.WHITE);content.addView(this)}}
        fun select(label:String,values:Array<String>):Spinner{content.addView(ScreenStyles.label(this,label,15f));return Spinner(this).apply{adapter=ArrayAdapter(this@SportsActivity,android.R.layout.simple_spinner_dropdown_item,values);content.addView(this)}}
        val screen=field("Nombre de pantalla","Fire · Sports");val court=field("Cancha / pista","Cancha 1");val a=field("Jugador o pareja A",current.getJSONArray("names").getString(0));val b=field("Jugador o pareja B",current.getJSONArray("names").getString(1))
        val discipline=select("Disciplina",arrayOf("Tenis","Pádel"));val best=select("Formato",arrayOf("Mejor de 3 sets","Mejor de 5 sets","Un set / proset"));val deuce=select("Iguales",arrayOf("Ventaja","Punto de oro / sin ventaja","Star Point (pádel)"))
        val games=field("Games para ganar el set (por ejemplo 6 u 8)","6");val at=field("Tiebreak al igualar en (por ejemplo 6, 7 u 8)","6");val noTie=CheckBox(this).apply{text="Set sin tiebreak (diferencia de 2 games)";setTextColor(android.graphics.Color.WHITE)};content.addView(noTie)
        val target=field("Puntos del tiebreak normal (diferencia de 2)","7");val final=select("Set decisivo",arrayOf("Set completo","Supertiebreak en lugar del último set"));val finalPoints=field("Puntos del desempate decisivo (7 o 10)","10");val first=select("Primer saque",arrayOf("A","B"))
        val scroll=ScrollView(this).apply{addView(content)};val dialog=AlertDialog.Builder(this).setTitle("Reglas del encuentro").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Iniciar encuentro",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{try{
            val set=JSONObject().put("kind","games").put("games",games.text.toString().toInt()).put("tiebreakAt",if(noTie.isChecked)JSONObject.NULL else at.text.toString().toInt()).put("tiebreakPoints",target.text.toString().toInt())
            val deciding=if(final.selectedItemPosition==1)JSONObject().put("kind","match_tiebreak").put("points",finalPoints.text.toString().toInt()) else JSONObject(set.toString()).put("tiebreakPoints",finalPoints.text.toString().toInt())
            val format=JSONObject().put("discipline",if(discipline.selectedItemPosition==0)"tennis" else "padel").put("bestOf",intArrayOf(3,5,1)[best.selectedItemPosition]).put("deuce",arrayOf("advantage","golden","star")[deuce.selectedItemPosition]).put("set",set).put("decidingSet",deciding)
            SportsRuntime.host(this).configure(screen.text.toString(),court.text.toString(),a.text.toString(),b.text.toString(),format,first.selectedItemPosition);dialog.dismiss();refreshNow()
        }catch(e:Exception){error(e)}}};dialog.show()
    }
}

