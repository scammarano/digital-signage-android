package com.digitalsignage.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Only the Android package installer gets temporary read access to this exact file. */
class ApkProvider: ContentProvider() {
    override fun onCreate():Boolean=true
    private fun file(uri:Uri):File {
        if(uri.authority!="com.digitalsignage.tv.apkfile" || uri.path!="/pending.apk")
            throw FileNotFoundException("Ruta inválida")
        val root=File(context!!.filesDir,"apk-updates").canonicalFile
        val dest=File(root,"pending.apk").canonicalFile
        if(dest.parentFile!=root || !dest.isFile || dest.length()<1024)
            throw FileNotFoundException("APK no encontrada")
        return dest
    }
    override fun getType(uri:Uri):String {file(uri);return "application/vnd.android.package-archive"}
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r")throw FileNotFoundException("Sólo lectura")
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
      selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val apk=file(uri)
        val cols=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(cols).apply{
            addRow(cols.map{when(it){OpenableColumns.DISPLAY_NAME->"DigitalSignage-update.apk";OpenableColumns.SIZE->apk.length();else->null}}.toTypedArray())
        }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int=0
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int=0
}
