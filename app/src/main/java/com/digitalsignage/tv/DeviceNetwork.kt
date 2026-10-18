package com.digitalsignage.tv

object DeviceNetwork {
    fun addresses():List<String> = try{
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}
            .flatMap{it.inetAddresses.toList()}.filterIsInstance<java.net.Inet4Address>()
            .filter{it.isSiteLocalAddress}.map{it.hostAddress?:""}.filter{it.isNotBlank()}.distinct()
    }catch(_:Exception){emptyList()}
    fun label():String="IP del equipo: "+addresses().joinToString(" · ").ifBlank{"Sin conexión de red"}
}
