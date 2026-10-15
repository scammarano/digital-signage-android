# Digital Signage 0.4.5: actualizaciones de APK y modo principal
- Al iniciar con emparejamiento, y cada 30 minutos mientras la app corre, consulta al portal.
- 0: manual; 1: aviso; 2: descarga automática y solicitud de confirmación de instalación.
- Validaciones: descarga HTTPS restringida al portal, tamaño, SHA-256, packageName, versionCode y certificado firmado idéntico al ya instalado.
- API 19+ en modo experimental, compatible con Fire OS 5 / Android 5.1.
- Android/Fire OS no permiten instalaciones silenciosas generales sin privilegios de administración. El usuario debe confirmar en el instalador.
- Los debug APK de GitHub Actions se firman con claves efímeras distintas. La primera migración a una clave de firma persistente requerirá reinstalación manual. Configurar secretos de GitHub Actions y guardar copia segura externa de la keystore; no subir la clave al repositorio ni al portal.
- Prioridad de modos: modo local de prueba explícito > bloque por horario activo > modo principal definido en la web.
- El modo realmente ejecutado se envía en el heartbeat.
