# Digital Signage TV 0.4.3 — Android 4.4+
- minSdk 19; API 19/20 es experimental.
- Amazon Fire TV Stick 2.ª generación: Fire OS 5 / API 22.
- MultiDex y desugaring de java.time.
- Servicios y notificaciones protegidos por comprobaciones de versión.
- Tokens Keystore; no se exportan con las copias de configuración.
- No se deshabilita la validación HTTPS. Se intenta TLS 1.2 en Android 4.4.
- La ejecución de GitHub Actions valida minSdk, integridad y firma debug.
- Overlay y autoinicio dependen del fabricante y permisos.
- Pruebas de instalación y reproducción requeridas en dispositivos físicos.
