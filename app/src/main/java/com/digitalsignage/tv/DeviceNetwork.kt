package com.digitalsignage.tv

object DeviceNetwork {
    fun addresses():List<String> = try{
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}.sortedBy{if(it.name.startsWith("wlan")||it.name.startsWith("eth"))0 else 1}
            .flatMap{it.inetAddresses.toList()}.filterIsInstance<java.net.Inet4Address>()
            .filter{it.isSiteLocalAddress}.map{it.hostAddress?:""}.filter{it.isNotBlank()}.distinct()
    }catch(_:Exception){emptyList()}
    fun label():String="IP del equipo: "+addresses().joinToString(" · ").ifBlank{"Sin IPv4 privada"}
}
