package com.digitalsignage.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pairing bearer token is encrypted at rest using non-exportable Android Keystore AES-GCM key. */
class SecureToken(context:Context) {
    private val p=context.getSharedPreferences("signage_auth_v1",Context.MODE_PRIVATE)
    private val alias="signage_tv_pairing_token_aes_v1"
    private fun key():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
         .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
         .setKeySize(256).build())
        return gen.generateKey()
    }
    fun save(token:String) {
        require(Regex("[a-f0-9]{64}").matches(token))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        p.edit().putString("token",Base64.encodeToString(cipher.iv+bytes,Base64.NO_WRAP)).apply()
    }
    fun load():String? {
        val encoded=p.getString("token",null)?:return null
        return try{
            val raw=Base64.decode(encoded,Base64.NO_WRAP);if(raw.size<29)return null
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,raw.copyOfRange(0,12)))
            String(cipher.doFinal(raw.copyOfRange(12,raw.size)),Charsets.UTF_8)
        }catch(_:Exception){ null }
    }
    fun clear(){p.edit().remove("token").apply()}
}
