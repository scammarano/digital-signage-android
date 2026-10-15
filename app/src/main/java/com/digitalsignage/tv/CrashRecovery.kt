package com.digitalsignage.tv

import android.os.Build

class CrashRecovery: androidx.multidex.MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        val logs=DeviceLogs(this)
        logs.record("info","startup","Proceso iniciado","API ${Build.VERSION.SDK_INT}; version 0.4.4")
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try{logs.record("fatal","uncaught",error.javaClass.simpleName+": "+(error.message?:""),
                error.stackTrace.take(8).joinToString("\n"))}catch(_:Throwable){}
            if(previous!=null)previous.uncaughtException(thread,error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
