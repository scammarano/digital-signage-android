package com.digitalsignage.tv

import android.content.Context
import org.json.JSONObject

/** Server policy, deliberately absent from local/LAN settings and export/restore. */
class ServiceGate(private val context:Context) {
    private val p=context.getSharedPreferences("signage_service_policy_v1",0)
    private fun binding():String=java.security.MessageDigest.getInstance("SHA-256").digest((SecureToken(context).load()?:"").toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun current()=p.getString("binding","")==binding()
    val suspended:Boolean get()=current()&&p.getBoolean("suspended",false)
    val revision:Long get()=if(current())p.getLong("revision",0L) else 0L
    val reason:String get()=if(current())p.getString("reason","")?:"" else ""
    fun accept(state:JSONObject){
        require(state.get("suspended") is Boolean)
        val raw=state.get("revision");require(raw is Number && raw.toDouble()==raw.toLong().toDouble() && raw.toLong()>=0)
        val rev=raw.toLong();if(current()&&rev<revision)return
        if(current()&&rev==revision&&p.contains("revision"))require(state.getBoolean("suspended")==suspended)
        p.edit().putString("binding",binding()).putLong("revision",rev).putBoolean("suspended",state.getBoolean("suspended")).putString("reason",state.optString("reason").take(190)).commit()
    }
    fun confirmation():JSONObject=JSONObject().put("revision",revision).put("suspended",suspended)
}
