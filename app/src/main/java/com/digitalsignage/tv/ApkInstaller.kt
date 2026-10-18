package com.digitalsignage.tv

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** The pre-N installer expects a readable file URI; modern Android uses the private provider. */
object ApkInstaller {
    const val MIME="application/vnd.android.package-archive"
    private fun digest(file:File):ByteArray {
        val hash=MessageDigest.getInstance("SHA-256")
        file.inputStream().use{input->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}}
        return hash.digest()
    }
    fun legacyFile(context:Context,verified:File):File {
        check(Build.VERSION.SDK_INT<24){"La ruta antigua solo corresponde a Android anterior a 7"}
        val root=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?:throw IllegalStateException("No hay almacenamiento disponible para entregar la APK al instalador de Fire OS")
        val dir=File(root,"apk-install").apply{check(mkdirs()||isDirectory)}
        val target=File(dir,"DigitalSignage-update.apk");val part=File(dir,"pending.part")
        try{
            verified.inputStream().use{input->FileOutputStream(part).use{output->input.copyTo(output);output.fd.sync()}}
            check(part.length()==verified.length()&&digest(part).contentEquals(digest(verified))){"Copia de instalación incompleta"}
            if(target.exists())check(target.delete())
            check(part.renameTo(target)){"No se pudo preparar el archivo para Fire OS"}
            check(target.canRead()){ "El instalador no puede leer la APK preparada" }
            return target
        }finally{part.delete()}
    }
    fun intents(context:Context,verified:File):List<Intent> {
        val uri=if(Build.VERSION.SDK_INT<24)Uri.fromFile(legacyFile(context,verified))
            else Uri.parse("content://com.digitalsignage.tv.apkfile/pending.apk")
        return listOf(Intent.ACTION_INSTALL_PACKAGE,Intent.ACTION_VIEW).map{action->
            Intent(action).apply{
                setDataAndType(uri,MIME)
                if(uri.scheme=="content"){
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData=ClipData.newRawUri("Actualización Digital Signage",uri)
                }
            }
        }
    }
    fun launch(candidates:List<Intent>,start:(Intent)->Unit) {
        for(candidate in candidates)try{start(candidate);return}catch(_:ActivityNotFoundException){}
        throw IllegalStateException("Fire OS no ofrece un instalador para esta APK. Abre la descarga con el instalador del dispositivo; no es necesario desinstalar Digital Signage. Revisa también el permiso para instalar aplicaciones desconocidas.")
    }
}
