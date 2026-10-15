# Digital Signage TV — Android v0.4.0 (código fuente funcional inicial)

Una única APK destinada a Android TV y Fire TV que comparte API con el portal Digital Signage.

## Funcional hoy en el código fuente
- Emparejamiento de seis dígitos **solo una vez**, token AES-GCM Android Keystore.
- Acceso a ajustes con PIN local (crear PIN al entrar por primera vez), inicio automático de mejor esfuerzo, habilitación de modos, tamaño/ubicación de caché y sincronización.
- Descarga HTTPS autenticada de imágenes/videos y validación de tamaño + SHA-256. Activación atómica después de completar descargas.
- Modo 1: listas y listas anidadas expandidas desde API, archivos locales, reproducción continua de imágenes y videos, transición fade/slide/zoom/cut, formatos de zona.
- Programación **local** de contenido y modos (Cartelera/Overlay/Pantalla inteligente), incluso offline, con zona horaria del sitio.
- Modo 2 **experimental**: servicio Foreground, permiso overlay, banners de imagen sobre otras apps cuando el firmware lo permite. El servicio verifica modo y deja de mostrar anuncios fuera del bloque.
- Modo 3 **primera fase**: widgets de turnos o marcador alimentados manualmente desde portal, con panel de publicidad y actualización de datos mientras haya Internet.
- Heartbeat de salud al portal.

## Limitaciones importantes
1. **Este ZIP contiene proyecto Android Studio, no un APK ya compilada.** Debe compilarse e instalarse en un Fire Stick/Android TV físico. No se ha ejecutado una prueba real de dispositivo ni una compilación Gradle aquí por falta de Android SDK/Gradle.
2. El modo Overlay está condicionado por permiso `SYSTEM_ALERT_WINDOW`, foreground service y restricciones de aplicaciones (video DRM, Netflix, YouTube, Fire OS). No afecta señales HDMI externas ni controla audio de terceros.
3. El inicio al encender es `best effort`: versiones modernas bloquean el lanzamiento automático de actividades; incluye notificación para abrir la app. Kiosk/device-owner requeriría gestión especial.
4. Modo 3 ofrece presentación manual básica; aún no hay flujo completo de tickets, impresora, operador, cronómetro deportivo automático ni proveedores de resultados.
5. Videos reproducidos con VideoView/MediaPlayer del sistema; se recomiendan MP4 H.264/AAC. El overlay de esta fase muestra solo imágenes; video overlay y compatibilidad por fabricante necesitan pruebas.
6. Las animaciones de video pueden mostrar un breve fotograma negro en ciertas implementaciones de hardware: requiere perfeccionamiento con Media3/ExoPlayer y Surface/TextureView.
7. No hay actualización silenciosa de APK. Android requiere autorización del usuario o administración del dispositivo.

## Cómo compilar
- Android Studio moderno (JDK 17; SDK Platform 35; Build Tools instalados).
- Abrir carpeta `android-tv/` como proyecto Gradle. Gradle Plugin Android 8.7.3, Kotlin 2.0.21. Si tu Android Studio solicita instalar el Gradle wrapper, usar Gradle 8.9 compatible con AGP 8.7.
- Build → Build APK(s) o `gradle assembleDebug` con Android SDK y Gradle instalados. Archivo esperado: `app/build/outputs/apk/debug/app-debug.apk`.
- Instalar con `adb install -r app-debug.apk` en Fire TV / Android TV con depuración habilitada, o transferencia autorizada de APK.
- Abrir, emparejar con código de Pantallas, configurar PIN, asignar lista y probar offline.
- Para Overlay: abrir Configuración, autorizar mostrar sobre otras apps. Hacer pruebas por modelo/OS/app.

## Seguridad
- No hay credenciales privadas del portal ni claves privadas de firma de actualizaciones en la APK.
- No usar HTTP: solo HTTPS, sin redirecciones automáticas para la descarga de medios.
- No instalar en producción antes de probar la compatibilidad, los fallos de energía, el consumo de memoria y el almacenamiento de vídeos largos.
