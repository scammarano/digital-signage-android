# 2Cam Digital Signage 0.4.11-debug — configuración y ejecución

Compilación experimental sobre `feat/sports-direct-lan` (v0.4.10). **Mantiene Sports LAN**.

## Activación
Desde la APK: Configuración (PIN) → PRUEBAS Y TELEMETRÍA → activar
«DEBUG: versión, modo y confirmaciones sobre el contenido». Como alternativa,
activar `debug_hud` desde el panel LAN local (requiere su código temporal).

Al activarlo muestra versión real, Android API, modo elegido, origen del modo,
módulo Turnos/Sports, revisiones recibidas/guardadas/efectivas, último evento,
sincronización y errores. Recibe cambios de configuración cada ~5 s en DEBUG.
Los avisos de recepción, guardado y ejecución aparecen brevemente.

## Confirmaciones
`RECEIVED`: la APK descargó revisión; `APPLIED`: persistió configuración;
`REPORTED`: servidor aceptó ACK existente; `EFFECTIVE`: componente realmente
renderizó un medio, un panel inteligente o una publicidad Overlay.
`BLOCKED`: Overlay no permitido.

Se envían eventos de etapa por los logs del portal, además del ACK viejo
compatible. La interfaz web aún no presenta el ciclo completo en columnas;
**es necesario actualizar la fuente PHP vigente** antes de ese despliegue.

## Atención
APK DEBUG no publicada en el portal. La firma Android puede impedir instalar
sobre otra APK sin reinstalar, lo que borraría emparejamiento y logs.
No desplegarla en pantallas comerciales sin pruebas físicas.
