# 2Cam Digital Signage 0.4.11-debug — observabilidad de configuración

La rama `feat/config-ack-debug-v0411` deriva de `feat/sports-direct-lan` (APK v0.4.10).

## Qué implementa ya en Android
- HUD de soporte habilitable desde Configuración (PIN) y navegador LAN.
- Registra revisiones remotas: `received`, `applied`, `reported`, `effective`, `blocked`, `rejected`.
- Ventana de aviso transitorio en recepción y ejecución si DEBUG está habilitado.
- Transmisión de eventos por la API de logs existente; conserva el ACK `applied` previo para compatibilidad del portal.
- Poll rápido de configuración (5s) solo mientras DEBUG está activo, sin rehacer descargas de playlist.
- Identifica modo Cartelera, Overlay o inteligente (Turnos/Sports), override local, sincronización y error.
- En Overlay utiliza ventana de diagnóstico no interactiva cuando Fire OS permite `SYSTEM_ALERT_WINDOW`.

## Pendiente web
No implementar `debug_hud_enabled` como campo remoto hasta actualizar la definición central del servidor (esquema v2).
Se requiere importación del PHP/MySQL vigente. El repositorio `2camdigitaltvweb` es documentación, no una copia desplegable.

El portal debe distinguir **enviado, recibido, guardado, efectivo** con timestamps y revisar los resultados de modo reales;
el endpoint viejo de `reportConfig(status=applied)` no es prueba de reproducción.

## Seguridad
Nunca registrar PIN, tokens, claves ni contenido sensible. El modo DEBUG local no bloquea cartelera.
La integración Sports LAN debe permanecer intacta. No fusionar esta rama ni instalar en producción sin pruebas físicas.
