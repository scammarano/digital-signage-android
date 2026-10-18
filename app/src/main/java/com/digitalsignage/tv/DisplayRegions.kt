package com.digitalsignage.tv

import android.content.Context
import android.view.View
import android.widget.FrameLayout

data class DisplayRegion(val x:Int,val y:Int,val width:Int,val height:Int)
data class DisplaySplit(val board:DisplayRegion,val media:DisplayRegion)
object DisplayRegions {
    fun split(width:Int,height:Int,layout:String,percent:Int):DisplaySplit {
        val w=width.coerceAtLeast(1);val h=height.coerceAtLeast(1);val p=percent.coerceIn(5,80)
        val mw=when(layout){"side","left","right"->w*p/100;"half_vertical"->w/2;else->w}.coerceAtLeast(1)
        val mh=when(layout){"bottom","top"->h*p/100;"half_horizontal"->h/2;else->h}.coerceAtLeast(1)
        val vertical=layout in listOf("side","left","right","half_vertical")
        val horizontal=layout in listOf("bottom","top","half_horizontal")
        val media=DisplayRegion(if(layout=="left")0 else w-mw,if(layout=="top")0 else h-mh,mw,mh)
        val board=DisplayRegion(if(layout=="left")mw else 0,if(layout=="top")mh else 0,if(vertical)w-mw else w,if(horizontal)h-mh else h)
        return DisplaySplit(board,media)
    }
}
/** Size against the measured playback canvas, once. Children never apply the percentage again. */
class RegionLayout(context:Context,private val layout:String,private val percent:Int,
    val board:View,val media:FrameLayout):FrameLayout(context){
    init{addView(board);addView(media)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        super.onSizeChanged(w,h,oldw,oldh)
        val split=DisplayRegions.split(w,h,layout,percent)
        fun place(view:View,r:DisplayRegion){view.layoutParams=LayoutParams(r.width,r.height).apply{leftMargin=r.x;topMargin=r.y}}
        place(board,split.board);place(media,split.media)
    }
}
