package com.digitalsignage.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Detect, verify and prepare new APK; standard Android requires user consent to install. */
object AppUpdater {
    private val busy=AtomicBoolean(false)
    private val ui=Handler(Looper.getMainLooper())
    private const val LIMIT=125829120L
    private const val URI="content://com.digitalsignage.tv.apkfile/pending.apk"
    fun check(activity:Activity,settings:SettingsStore,force:Boolean=false) {
        if(!force && settings.updatePolicy==0)return
        if(!busy.compareAndSet(false,true))return
        thread(name="signage-apk-check") {
            try {
                val secret=SecureToken(activity).load()?:throw IllegalStateException("Pantalla todavía sin emparejar")
                val api=SignageApi(settings.portalUrl,secret)
                val info=api.latestUpdate(settings.updateChannel,Build.VERSION.SDK_INT)
                @Suppress("DEPRECATION")
                val current=activity.packageManager.getPackageInfo(activity.packageName,0).versionCode
                if(!info.optBoolean("available") || info.optInt("version_code")<=current){
                    if(force)message(activity,"Sin novedades","Esta pantalla ya utiliza la última versión disponible en su canal.")
                    return@thread
                }
                val version=info.getString("version")
                if(settings.updatePolicy==1) {
                    confirm(activity,"Nueva versión $version","Hay una mejora publicada. ¿Descargarla y preparar su instalación?"){
                        prepare(activity,api,info)
                    }
                } else prepare(activity,api,info)
            }catch(e:Exception){
                DeviceLogs(activity).record("warn","apk_update",e.javaClass.simpleName,e.message?:"")
                if(force)message(activity,"Actualización no disponible",e.message?:"Error al consultar")
            }finally{busy.set(false)}
        }
    }
    private fun prepare(activity:Activity,api:SignageApi,info:JSONObject){
        thread(name="signage-apk-download"){
            try {
                val file=download(activity,api,info)
                verifyIdentity(activity,file,info)
                // Persist expected hash and version for later, before invoking Android installer.
                File(file.parentFile,"pending.json").writeText(info.toString())
                DeviceLogs(activity).record("info","apk_update","APK descargada y verificada",info.getString("version"))
                confirm(activity,"Instalar v"+info.getString("version"),
                    "Actualización descargada y validada. Android / Fire OS solicitará confirmar la instalación.") {
                    installCached(activity)
                }
            }catch(e:Exception){
                DeviceLogs(activity).record("error","apk_update",e.javaClass.simpleName,e.message?:"")
                message(activity,"No se puede instalar esta versión",e.message?:"Descarga no válida")
            }
        }
    }
    private fun download(activity:Activity,api:SignageApi,info:JSONObject):File {
        val expected=info.getString("sha256").lowercase(java.util.Locale.US)
        val size=info.getLong("size")
        require(size in 1024L..LIMIT && Regex("[a-f0-9]{64}").matches(expected)){"Metadatos APK inválidos"}
        require(info.getString("package")==activity.packageName){"Paquete diferente"}
        require(info.getInt("min_sdk")<=Build.VERSION.SDK_INT){"Versión de Android incompatible"}
        val dir=File(activity.filesDir,"apk-updates").apply{mkdirs()}
        val temp=File(dir,"pending.part");val target=File(dir,"pending.apk")
        temp.delete()
        val digest=MessageDigest.getInstance("SHA-256")
        var read=0L
        try {
            val conn=api.mediaConnection(info.getString("url"))
            try {
                require(conn.responseCode==200){"Error HTTP ${conn.responseCode}"}
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use{out ->
                        val buffer=ByteArray(32768)
                        while(true){
                            val n=input.read(buffer);if(n<0)break
                            read+=n;require(read<=size && read<=LIMIT){"APK supera tamaño declarado"}
                            digest.update(buffer,0,n);out.write(buffer,0,n)
                        }
                        out.fd.sync()
                    }
                }
            }finally{conn.disconnect()}
            require(read==size){"APK incompleta: $read de $size"}
            val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
            require(actual==expected){"Integridad SHA-256 incorrecta"}
            target.delete();require(temp.renameTo(target)){"No se pudo guardar la APK"}
            return target
        }finally{temp.delete()}
    }
    @Suppress("DEPRECATION")
    private fun verifyIdentity(activity:Activity,file:File,info:JSONObject){
        val pm=activity.packageManager
        val candidate=pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNATURES)
            ?:throw IllegalStateException("APK ilegible")
        val current=pm.getPackageInfo(activity.packageName,PackageManager.GET_SIGNATURES)
        require(candidate.packageName==activity.packageName && candidate.versionCode==info.getInt("version_code")){"Identidad/versión APK inválida"}
        val old=current.signatures;val fresh=candidate.signatures
        require(old!=null && fresh!=null && old.size==1 && fresh.size==1 && old[0].toByteArray().contentEquals(fresh[0].toByteArray())) {
            "El certificado de esta versión no coincide con la APK instalada. Es necesaria una única reinstalación manual para adoptar una clave de firma persistente. Se ha bloqueado la actualización incompatible."
        }
    }
    private fun hashFile(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf=ByteArray(32768)
            while(true){val n=input.read(buf);if(n<0)break;digest.update(buf,0,n)}
        }
        return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    fun installCached(activity:Activity){
        try {
            val dir=File(activity.filesDir,"apk-updates")
            val file=File(dir,"pending.apk")
            val saved=File(dir,"pending.json")
            require(file.isFile && saved.isFile){"Todavía no hay una actualización descargada"}
            val info=JSONObject(saved.readText())
            val expected=info.getString("sha256")
            require(expected.matches(Regex("[a-f0-9]{64}")) && hashFile(file)==expected){"APK descargada alterada"}
            verifyIdentity(activity,file,info)
            if(Build.VERSION.SDK_INT>=26 && !activity.packageManager.canRequestPackageInstalls()){
                message(activity,"Autorización requerida","Permite instalaciones desde Digital Signage y después pulsa «Instalar APK ya descargada» en Configuración.")
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+activity.packageName)))
                return
            }
            ApkInstaller.launch(ApkInstaller.intents(activity,file)){activity.startActivity(it)}
            DeviceLogs(activity).record("info","apk_installer_opened","Instalador solicitado; confirmación del usuario pendiente",info.optString("version"))
        }catch(e:Exception){
            DeviceLogs(activity).record("error","apk_installer_failed",e.javaClass.simpleName,e.message?.take(240)?:"Sin instalador")
            message(activity,"Instalación no iniciada",e.message?:"Sin instalador")
        }
    }
    private fun confirm(activity:Activity,title:String,message:String,onConfirm:()->Unit) {
        ui.post{if(!activity.isFinishing)AlertDialog.Builder(activity)
            .setTitle(title).setMessage(message).setPositiveButton("Continuar"){_,_->onConfirm()}
            .setNegativeButton("Más tarde",null).show()}
    }
    private fun message(activity:Activity,title:String,message:String){
        ui.post{if(!activity.isFinishing)AlertDialog.Builder(activity).setTitle(title)
            .setMessage(message).setPositiveButton("Aceptar",null).show()}
    }
}

