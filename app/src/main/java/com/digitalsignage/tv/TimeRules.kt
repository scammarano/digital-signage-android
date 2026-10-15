package com.digitalsignage.tv

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Pure scheduling semantics shared by mode switching and content selection. Monday=bit0. */
object TimeRules {
    fun active(mask:Int,start:String,end:String,from:String?,until:String?,now:LocalDateTime):Boolean {
        if(mask and (1 shl (now.dayOfWeek.value-1))==0)return false
        val date=now.toLocalDate()
        if(!from.isNullOrBlank() && date.isBefore(LocalDate.parse(from)))return false
        if(!until.isNullOrBlank() && date.isAfter(LocalDate.parse(until)))return false
        val open=LocalTime.parse(start.take(5),DateTimeFormatter.ofPattern("HH:mm"))
        val close=LocalTime.parse(end.take(5),DateTimeFormatter.ofPattern("HH:mm"))
        if(!open.isBefore(close))return false // Cross-midnight intentionally requires two blocks.
        return !now.toLocalTime().isBefore(open) && now.toLocalTime().isBefore(close)
    }
    data class ModeBlock(val id:Long,val mode:Int,val daysMask:Int,val start:String,val end:String,
        val validFrom:String?,val validUntil:String?,val priority:Int,val enabled:Boolean)
    fun mode(blocks:List<ModeBlock>,fallback:Int,enabledMask:Int,now:LocalDateTime):Int {
        val sorted=blocks.sortedWith(compareByDescending<ModeBlock>{it.priority}.thenByDescending{it.id})
        for(b in sorted)if(b.enabled && b.mode in 1..3 && enabledMask and (1 shl (b.mode-1))!=0 && active(b.daysMask,b.start,b.end,b.validFrom,b.validUntil,now))return b.mode
        return if(fallback in 1..3 && enabledMask and (1 shl (fallback-1))!=0)fallback else 0
    }
}
