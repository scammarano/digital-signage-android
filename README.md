# Digital Signage Android TV / Fire TV — código fuente v0.4.1

**NO es un APK compilado.** El código necesita compilación con Android SDK 35, Gradle 8.9, Java 17 y el Android Gradle Plugin declarado. En este entorno no hay SDK ni acceso a los repositorios para instalarlo: **no hay prueba de compilación ni de ejecución en Fire TV**.

## Qué incluye
- API de emparejamiento con código de seis dígitos, guardado seguro de token.
- Motor de reproducción y caché de imágenes/videos heredado de v0.4.0, con modo cartelera, overlay experimental y paneles iniciales de turnos y marcador.
- Sondeo remoto de configuración deseada, revisión versionada, respaldo local automático previo, aplicación y ACK de estado.
- Pantalla TV protegida por PIN, opciones locales y activación voluntaria del servidor web temporal por IP/puerto.
- Panel LAN de 30 minutos, con código visible en el televisor, sesión de 20 minutos, bloqueo temporal por intentos, copia JSON sin PIN ni token, edición y restauración del JSON. **Solo red Wi-Fi/Ethernet privada**, puerto configurable (8787 predeterminado).
- GitHub Actions opcional `.github/workflows/build-debug-apk.yml` para producir una APK debug a través de un repositorio GitHub; el archivo debe colocarse en la raíz del repo Android.

## Compilar con Android Studio
1. Abre la carpeta `android-tv` como proyecto Gradle. Instala SDK Platform 35, Build Tools 35 y JDK 17 cuando Android Studio lo solicite.
2. Sincroniza Gradle (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9) y ejecuta **Build > Build APK(s)** o `gradle assembleDebug` si tienes Gradle 8.9 en PATH.
3. Busca la APK en `app/build/outputs/apk/debug/app-debug.apk`.
4. Instálala en un Fire TV/Android TV de pruebas habilitando depuración ADB. Ejemplo: `adb connect IP_DISPOSITIVO:5555` seguido de `adb install -r app-debug.apk` (según dispositivo y si ADB de red está habilitado).
5. En el portal, registra la pantalla y genera código de seis dígitos. Ingresa ese código una sola vez en la app.

## Panel local por IP
1. Dentro de la app usa **MENU** o pulsación larga del botón central → Configuración. Crea un PIN local.
2. Pulsa «Iniciar panel web local durante 30 min». El televisor mostrará la URL y un código temporal.
3. Desde la misma red local abre esa IP:puerto. Ingresa el código del televisor (se consume al utilizarlo).
4. Cambia opciones, descarga el respaldo JSON o restaura otro. No copia identidad, token ni PIN.
5. Para cerrar, pulsa «Cerrar panel local» en la app o espera a su cierre automático de 30 minutos.

**Seguridad:** el panel LAN de esta versión utiliza HTTP *sin cifrado*, por lo que debes utilizarlo **solo en una red local privada de confianza**, nunca abrir el puerto en el router ni usar redes públicas/compartidas. Está deshabilitado hasta su activación presencial; requiere código de vinculación temporal. Para entornos corporativos con redes no confiables debe implementarse TLS local gestionado o un túnel autenticado antes de habilitarlo.

## Configuración remota por HTTPS
En el portal abre **Administración remota APK** → selecciona empresa/pantalla → publica cambios. Se almacenan con nueva revisión. En el siguiente ciclo de sincronización la app consulta `api-device-config.php?op=desired`, valida y guarda respaldo anterior, aplica cambios permitidos y confirma a `op=report`. El portal distingue pendiente/aplicada/rechazada/diferente. Por diseño **la contraseña PIN, la URL del servidor, la identidad del equipo, los permisos Android y la activación del panel LAN no pueden cambiarse remotamente**.

## Limitaciones y pruebas obligatorias
- No hay APK binaria ni prueba sobre hardware en esta entrega.
- La gestión remota sondea mientras el reproductor está ejecutándose; Overlay sondea cuando su servicio está activo. La entrega **no es instantánea** y no garantiza tareas cuando Android detiene la app.
- `SYSTEM_ALERT_WINDOW`, arranque automático y la presentación sobre otras apps dependen de permisos y fabricante. Algunas apps protegidas pueden bloquear superposiciones. No funciona sobre HDMI externo.
- El panel local no intenta ser un servidor web público ni un servidor de administración de dispositivos empresariales; únicamente una herramienta temporal para red privada.
- El modo deportivo y de turnos sigue siendo una primera implementación, no un sistema de marcador en tiempo real completo.
- La carpeta de caché se elige entre el almacenamiento interno de la app y el espacio externo propio de la app; no se ofrecen carpetas arbitrarias.
