package com.digitalsignage.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import org.json.JSONObject

/** Public presentation only. Points, games and sets are computed by sports-core. */
class SportsBoardView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var snapshot:JSONObject?=null
    var scopeLabel=""
    var summaryText="Preparando Sports…";private set
    fun update(value:JSONObject,summary:String){snapshot=value;summaryText=summary;contentDescription=summary;invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);canvas.drawColor(ScreenStyles.BACKGROUND)
        val match=snapshot?.optJSONObject("match")?:return
        val state=match.optJSONObject("state")?:return
        val names=match.optJSONArray("names");val labels=match.optJSONArray("labels")
        val games=state.optJSONArray("games");val sets=state.optJSONArray("setsWon")
        val w=width.toFloat();val h=height.toFloat();val base=minOf(w/16f,h/7f)
        fun text(value:String,x:Float,y:Float,size:Float,maxWidth:Float,bold:Boolean=false){
            paint.color=Color.WHITE;paint.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;paint.textSize=size
            val length=paint.measureText(value);if(length>maxWidth)paint.textSize=size*maxWidth/length
            canvas.drawText(value,x,y,paint)
        }
        if(scopeLabel.isNotBlank())text(scopeLabel,w*.04f,h*.07f,base*.30f,w*.92f)
        text("PUNTOS",w*.49f,h*.17f,base*.38f,w*.19f)
        text("JUEGOS",w*.72f,h*.17f,base*.38f,w*.13f)
        text("SETS",w*.88f,h*.17f,base*.38f,w*.11f)
        for(i in 0..1){val y=h*(if(i==0).48f else .85f)
            text(names?.optString(i,"Lado ${i+1}")?:"Lado ${i+1}",w*.04f,y,base*.85f,w*.4f,true)
            text(labels?.optString(i,"0")?:"0",w*.49f,y,base*1.9f,w*.19f,true)
            text((games?.optInt(i,0)?:0).toString(),w*.74f,y,base*1.1f,w*.12f,true)
            text((sets?.optInt(i,0)?:0).toString(),w*.9f,y,base*1.1f,w*.09f,true)
        }
        if(!state.isNull("winner"))text("GANADOR: "+names?.optString(state.optInt("winner")),w*.04f,h*.98f,base*.35f,w*.9f,true)
        else if(match.optBoolean("decidingPoint"))text("PUNTO DECISIVO",w*.04f,h*.98f,base*.35f,w*.9f,true)
    }
}
