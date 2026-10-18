# APK 0.4.19 · Instalador Fire OS y proporción Sports LAN

Base: `feat/four-modes-parity-v0413`, commit `19924b6d570395764c52921035bee5df868b765b` (0.4.18). Se conserva Sports LAN, reglas compartidas, HUD, contrato de 43 ajustes y política de suspensión.

## Error de actualización observado

La foto muestra `No Activity found` al enviar `ACTION_VIEW` con un URI `content://` al instalador del Fire. Se produce después de verificar la APK; no es el error de certificado distinto.

0.4.19 usa una copia comprobada con SHA-256 y un URI `file://` para Android/API19–23. Prefiere la carpeta externa de la propia aplicación; si no hay almacenamiento compartido montado, prepara una carpeta de instalación interna y permite la lectura de esa única APK verificada, sin cambiar la lectura de otros archivos privados. API24+ conserva el proveedor privado y permiso temporal de lectura. Se intenta `ACTION_INSTALL_PACKAGE` y, si no hay actividad para esa acción, `ACTION_VIEW`. La apertura se registra como solicitud al instalador, nunca como actualización ya instalada. No se aceptan archivos de otro paquete, versión o certificado.

Referencia Android/AOSP: https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/content/InstallApk.java (ejemplo `getApkUri`, API anterior a N frente a FileProvider).

La versión antigua que ya está ejecutándose mantiene su código de apertura defectuoso. Para adoptar esta corrección por primera vez hay que abrir la nueva descarga con el instalador del Fire, Downloader o `adb install -r`, conservando la aplicación y sus datos. No se recomienda desinstalar para resolver este error. Si el certificado instalado fuera distinto, Android seguirá rechazando la sustitución: la corrección del URI no cambia las reglas de firma. El usuario confirma la instalación en Android/Fire OS.

## Franja pequeña en la foto de Sports directo

`SportsActivity` usaba una zona ponderada de medios y `MediaDeck` volvía a calcular el 20% dentro de esa zona. El resultado era una franja de aproximadamente 6–8% del alto total y una imagen centrada aún más pequeña al conservar proporción.

Se reserva la región una sola vez a partir del lienzo realmente medido: inferior 20% del alto central configurado; lateral usa el porcentaje central de ancho; medias pantallas 50%. `MediaDeck` recibe esa región ya dimensionada. Se conserva la proporción de los archivos; no se estira ni se recorta un arte incompatible.

El temporizador de Overlay empieza cuando la ventana está adjunta, no antes. En Android moderno, el primer permiso/alta de ventana podía consumir casi toda una aparición de 0,1 s antes de mostrarla.

Sports directo usa exclusivamente la lista asignada a Marcador deportivo y a su formato; deja de heredar la programación de Cartelera. Los puntos, juegos y sets se muestran en columnas amplias, con tamaño ajustado a la región disponible. La vista de administración Sports sigue teniendo sus controles de encuentro/enlace; la integración del nuevo catálogo central del portal continúa pendiente y no se declara terminada aquí.

## Validación

CI ejecuta la apertura real del instalador en emuladores API22 y API29 y la cancela sin aceptar una instalación. Comprueba ruta `file`/`content`, copia legible y fallback de acción. Una imagen PNG de 1920×216 debe ocupar todo el ancho y exactamente 20% del alto del lienzo Sports, sin volver a reducirse dentro de la región. Se prueban también 1280×720, 1920×1080 y orientación vertical, porcentajes centrales y lista independiente. La prueba física en el Fire del usuario queda pendiente.
