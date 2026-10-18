package com.digitalsignage.tv

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.security.auth.x500.X500Principal

/** Keystore AES-GCM on API 23+, Keystore RSA on API 19-22. No plaintext bearer token. */
class SecureToken(private val context: Context, private val slot:String="pairing") {
    init { require(slot.matches(Regex("[a-z0-9_]{1,32}"))) }
    private val prefs=context.getSharedPreferences(if(slot=="pairing")"signage_auth_v1" else "signage_secret_$slot", Context.MODE_PRIVATE)
    private val aesAlias=if(slot=="pairing")"signage_tv_pairing_token_aes_v1" else "signage_${slot}_aes_v1"
    private val rsaAlias=if(slot=="pairing")"signage_tv_pairing_token_rsa_legacy_v1" else "signage_${slot}_rsa_v1"
    private fun aesKey():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(aesAlias,null) as? SecretKey)?.let{return it}
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(aesAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }
    @Suppress("DEPRECATION")
    private fun rsaKey():KeyPair {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val privateKey=ks.getKey(rsaAlias,null) as? PrivateKey
        val publicKey=ks.getCertificate(rsaAlias)?.publicKey
        if (privateKey!=null && publicKey!=null) return KeyPair(publicKey,privateKey)
        val start=Calendar.getInstance()
        val end=Calendar.getInstance().apply{add(Calendar.YEAR,20)}
        val spec=KeyPairGeneratorSpec.Builder(context).setAlias(rsaAlias)
            .setSubject(X500Principal("CN=Digital Signage legacy device token"))
            .setSerialNumber(BigInteger.valueOf(1L))
            .setStartDate(start.time).setEndDate(end.time).setKeySize(2048).build()
        val generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore")
        generator.initialize(spec)
        return generator.generateKeyPair()
    }
    fun save(token:String) {
        require(Regex("[a-f0-9]{64}").matches(token))
        val value=if (Build.VERSION.SDK_INT >= 23) {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,aesKey())
            "aes:"+Base64.encodeToString(cipher.iv+cipher.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        } else {
            val cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE,rsaKey().public)
            "rsa:"+Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        }
        check(prefs.edit().putString("token",value).commit()){"No se pudo guardar la vinculación"}
    }
    fun load():String? {
        val encoded=prefs.getString("token",null)?:return null
        return try {
            val decrypted=if(encoded.startsWith("rsa:")) {
                val raw=Base64.decode(encoded.removePrefix("rsa:"),Base64.NO_WRAP)
                val cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE,rsaKey().private)
                cipher.doFinal(raw)
            } else {
                if(Build.VERSION.SDK_INT < 23)return null
                val raw=Base64.decode(encoded.removePrefix("aes:"),Base64.NO_WRAP)
                if(raw.size<29)return null
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,aesKey(),GCMParameterSpec(128,raw.copyOfRange(0,12)))
                cipher.doFinal(raw.copyOfRange(12,raw.size))
            }
            String(decrypted,Charsets.UTF_8).takeIf{Regex("[a-f0-9]{64}").matches(it)}
        } catch (_:Exception){null}
    }
    fun clear(){prefs.edit().remove("token").commit()}
}

