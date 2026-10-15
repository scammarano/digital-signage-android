# Sports directo · Digital Signage 0.4.10 de prueba

El Fire ejecuta la autoridad Sports dentro de la APK. La tablet se conecta por Wi-Fi directamente; **no se utiliza Node, computadora, VPS ni internet para cada punto**. Las reglas pertenecen al componente separado `sports-core`, cuyo origen es el repositorio `2cam-sports/native-core`. El reproductor solo presenta el estado y los medios.

## Probar

1. Instalar `2cam-digital-signage-0.4.10-sports-direct-test.apk` en el Fire. Es el mismo paquete de Digital Signage, versión de prueba. Si Android rechaza actualizar por firma diferente, no borres la instalación sin antes guardar configuración y considerar que habría que vincularla de nuevo al portal. La firma debug no es la firma definitiva de distribución.
2. Abrir Digital Signage → Configuración local (PIN) → **SPORTS LOCAL · tablet directa al Fire (sin PC)**. Puede abrirse sin vincular el portal, desde la pantalla inicial de emparejamiento → Configuración local.
3. En el Fire elegir **Nuevo encuentro**. Indicar pista, jugadores/parejas, tenis/pádel, games, mejor de 1/3/5 sets, iguales y tiebreak/supertiebreak. No reiniciar un encuentro en curso sin intención de sustituirlo. El historial anterior se conserva localmente.
4. Pulsar **Enlazar control**, elegir oficial/auxiliar A/auxiliar B. El Fire muestra su dirección `http://IP-DEL-FIRE:8989` y un código temporal.
5. En la APK Sports Control introducir esa dirección, buscar pantallas y enlazar con el código. La versión Control 0.2.0 ya es compatible por dirección manual. La versión 0.3.0 añade búsqueda de anuncios mDNS cuando la red lo permita.
6. Sumar puntos desde la tablet: se guardan en el Fire antes de confirmar y aparecen en su marcador. Hasta tres controles persistentes. **Controles / revocar** libera cupos.

Dejar abierta la pantalla Sports del Fire mientras se juega. Salir de ella cierra el receptor local y los controles detectan desconexión; el partido y enlaces se conservan en el almacenamiento privado para la siguiente apertura. Si cambia la IP del Fire, usar la dirección nueva que muestra. Si no tiene LAN privada disponible, no se abrirá el receptor.

Si existen medios ya descargados desde el portal, esta pantalla reutiliza la programación local de Digital Signage en una zona inferior y mantiene su audio silenciado. No descarga ni configura campañas nuevas desde Sports. Si no hay medios en caché, muestra el marcador sin anuncios. El resto de modos y la sincronización del portal conservan sus rutas anteriores.

## Seguridad y alcance

- Puerto Sports 8989 separado del panel administrativo temporal 8787. Vinculación física por código, un uso, 2 minutos, cinco errores; roles fijados desde el Fire. No usa el PIN del portal como token del control.
- Tokens almacenados como hash en Fire; la tablet conserva su sesión cifrada. APIs autenticadas salvo salud, catálogo y canje de código. No se registran tokens en respuestas de diagnóstico.
- Transacción lógica sincronizada con AtomicFile antes del ACK; clave idempotente y versión del marcador evitan duplicaciones/conflictos. Los datos de esta prueba quedan en el Fire; sincronización/históricos en el portal Sports todavía pendientes.
- Solo LAN privada. HTTP de laboratorio, sin exposición WAN. HTTPS/distribución de certificados pendiente de producción.
- Una pantalla Fire primaria por encuentro en esta entrega. Réplicas de otras pantallas y migración de autoridad no implementadas todavía.
- Compatibilidad APK mínima API19 conservada, prueba automatizada específica en emulador API22. Fire OS/hardware real debe verificarse con el usuario. No se promete latencia ni compatibilidad Bluetooth sin prueba.

## Verificación

`sports-core:test` compara cada punto de ocho partidos completos (1.314 puntos) con fixtures del motor JavaScript, y prueba persistencia, cupos, permisos, idempotencia y fallo de disco. El test Android API22 ejecuta el receptor de la APK, enlaza controles mediante HTTP local, actualiza la vista, reabre el archivo persistente y rechaza un cuarto enlace. No inicia un servicio Node en CI.
