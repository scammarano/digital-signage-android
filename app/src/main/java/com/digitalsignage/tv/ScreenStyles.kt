package com.digitalsignage.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object ScreenStyles {
    const val BACKGROUND=0xFF08172A.toInt()
    const val FOREGROUND=Color.WHITE
    const val GREEN=0xFF07833C.toInt()
    const val BLUE=0xFF174589.toInt()
    const val RED=0xFF9A252A.toInt()
    const val IDLE=0xFF233B55.toInt()
    fun label(c:Context,text:String,size:Float=20f):TextView=TextView(c).apply {
        this.text=text;this.textSize=size;setTextColor(FOREGROUND)
        gravity=Gravity.CENTER_VERTICAL;setPadding(12,12,12,12)
    }
    private fun background(fill:Int,border:Int=0xFF6593C1.toInt()):GradientDrawable =
        GradientDrawable().apply{
            shape=GradientDrawable.RECTANGLE
            cornerRadius=14f
            setColor(fill)
            setStroke(2,border)
        }
    fun button(c:Context,text:String,selected:Boolean=false,action:()->Unit):Button=Button(c).apply {
        this.text=text;textSize=18f;isFocusable=true;isFocusableInTouchMode=true
        setAllCaps(false);setTextColor(Color.WHITE)
        val default=if(selected)GREEN else BLUE
        setBackgroundDrawable(background(default))
        val margin=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,7,0,7)}
        layoutParams=margin
        setOnFocusChangeListener{_,focused->
            setBackgroundDrawable(background(if(focused)0xFF0765AF.toInt() else default,
                if(focused)0xFF8DEBAF.toInt() else 0xFF6593C1.toInt()))
        }
        setOnClickListener{action()}
    }
    fun statusButton(c:Context,text:String,state:Int,action:()->Unit):Button=button(c,text,false,action).apply{
        val chosen=when(state){1->GREEN;2->RED;else->IDLE}
        setBackgroundDrawable(background(chosen))
        setOnFocusChangeListener{_,focused->setBackgroundDrawable(
            background(if(focused)0xFF0765AF.toInt() else chosen,if(focused)0xFF8DEBAF.toInt() else 0xFF6593C1.toInt())
        )}
    }
    fun column(c:Context):LinearLayout=LinearLayout(c).apply {
        orientation=LinearLayout.VERTICAL;setPadding(28,24,28,24);setBackgroundColor(BACKGROUND)
    }
}
