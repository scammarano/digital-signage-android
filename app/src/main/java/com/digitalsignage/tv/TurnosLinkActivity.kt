package com.digitalsignage.tv
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import java.util.concurrent.Executors

/** Local credential configuration is separate from public/remote playlist settings. */
class TurnosLinkActivity:Activity(){
    private val io=Executors.newSingleThreadExecutor()
    private lateinit var binding:TurnosBinding
    @JvmField var pinDialog:AlertDialog?=null
    override fun onCreate(state:Bundle?){super.onCreate(state);binding=TurnosBinding(this);authenticate()}
    private fun authenticate(){
        val settings=SettingsStore(this)
        if(!settings.hasPin()){AlertDialog.Builder(this).setMessage("Crea primero el PIN en Configuración de Digital Signage").setPositiveButton("Cerrar"){_,_->finish()}.setCancelable(false).show();return}
        val field=EditText(this).apply{hint="PIN de Digital Signage";inputType=18;tag="turnos-pin"}
        val dialog=AlertDialog.Builder(this).setTitle("Enlace Turnos · Administración").setView(field).setNegativeButton("Cancelar"){_,_->finish()}.setPositiveButton("Entrar",null).create();pinDialog=dialog
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false;val value=field.text.toString()
            io.execute{try{val valid=settings.verifyPin(value);runOnUiThread{if(isFinishing)return@runOnUiThread;if(valid){dialog.dismiss();form()}else{field.error="PIN incorrecto";dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true}}}catch(e:Exception){runOnUiThread{field.error=e.message?:"No se pudo verificar";dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true}}}
        }};dialog.show()
    }
    private fun form(){
        val root=ScreenStyles.column(this);root.addView(ScreenStyles.label(this,"TURNOS LOCAL · SOLO LECTURA",25f))
        root.addView(ScreenStyles.label(this,"En la APK Turnos abre Enlazar y elige VISOR SECUNDARIO. No usa uno de los tres controles de operación. Dirección y credencial no se envían en playlists ni en respaldos públicos.",16f))
        val address=EditText(this).apply{hint="IP privada de Turnos:8990";setText(binding.base);isSingleLine=true;tag="turnos-address"};root.addView(address)
        val code=EditText(this).apply{hint="Código de 6 dígitos (Visor)";inputType=2;isSingleLine=true;tag="turnos-code"};root.addView(code)
        val status=ScreenStyles.label(this,if(binding.enabled)"Enlace guardado · ${binding.id}" else "Sin enlace local",16f).apply{tag="turnos-link-status"};root.addView(status)
        lateinit var source:CheckBox
        val link=ScreenStyles.button(this,"Enlazar como visor"){ }
        link.tag="turnos-link";link.setOnClickListener{val base=address.text.toString();val entered=code.text.toString();link.isEnabled=false;status.text="Enlazando…"
            io.execute{try{binding.connect(base,entered);runOnUiThread{if(!isFinishing){source.isChecked=true;status.text="Enlazado como visor. Selecciona modo Turnos y su playlist en Configuración.";link.isEnabled=true}}}catch(e:Exception){runOnUiThread{if(!isFinishing){status.text=e.message?:"No se pudo enlazar. Revisa el rol y el código en Turnos.";link.isEnabled=true}}}}
        };root.addView(link)
        source=CheckBox(this).apply{text="Usar esta fuente local en modo Turnos";isChecked=binding.enabled;setOnCheckedChangeListener{_,checked->try{if(checked)check(binding.token()!=null){"Enlaza primero"};binding.enabled=checked}catch(e:Exception){isChecked=false;status.text=e.message}}};root.addView(source)
        root.addView(CheckBox(this).apply{text="Audio en esta pantalla (silencia las otras para evitar eco)";isChecked=binding.audio;setOnCheckedChangeListener{_,checked->binding.audio=checked}})
        root.addView(ScreenStyles.label(this,"El modo, el layout y la playlist publicitaria siguen configurándose en Digital Signage. La sincronización de medios conserva su intervalo; los turnos se consultan por separado cada segundo. HTTP solo en red privada de confianza.",15f))
        root.addView(ScreenStyles.button(this,"Abrir APK Turnos en este Fire"){
            val open=packageManager.getLaunchIntentForPackage("services.twocam.turnos")
            if(open!=null)startActivity(open)else status.text="Instala/abre Turnos en el Fire principal o escribe su IP si está en otro equipo."
        })
        root.addView(ScreenStyles.button(this,"Borrar enlace de este visor"){
            AlertDialog.Builder(this).setMessage("Borrará el enlace y snapshot de Signage. Revoca también este visor en Turnos para liberar su plaza.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar"){_,_->try{binding.clear();form()}catch(e:Exception){status.text=e.message}}.show()
        })
        root.addView(ScreenStyles.button(this,"Volver a Configuración"){finish()});setContentView(ScrollView(this).apply{addView(root)})
    }
    override fun onDestroy(){io.shutdownNow();super.onDestroy()}
}
