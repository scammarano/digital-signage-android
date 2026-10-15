package com.digitalsignage.tv

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object ScreenStyles {
    const val BACKGROUND=0xFF0B172A.toInt()
    const val FOREGROUND=Color.WHITE
    fun label(c:Context,text:String,size:Float=20f):TextView=TextView(c).apply{
        this.text=text;this.textSize=size;setTextColor(FOREGROUND);gravity=Gravity.CENTER_VERTICAL;setPadding(12,12,12,12)
    }
    fun button(c:Context,text:String,action:()->Unit):Button=Button(c).apply{this.text=text;textSize=18f;isFocusable=true;setOnClickListener{action()}}
    fun column(c:Context):LinearLayout=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(28,24,28,24);setBackgroundColor(BACKGROUND)}
}
