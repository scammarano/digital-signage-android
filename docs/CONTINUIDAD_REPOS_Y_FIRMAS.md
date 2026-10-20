# Continuidad obligatoria — Android TV/Fire OS y portal

Antes de cada cambio, leer GitHub real y su HEAD (no confiar solo en mensajes de otros hilos):
- APK: `scammarano/digital-signage-android`, rama de continuidad `feat/boot-diagnostics-v0422`.
- Web: `scammarano/2camdigitaltvweb`, rama de continuidad `feat/device-log-export-v0431`.
- Revisar `README.md`, `docs/FIRMA_PERMANENTE.md`, versión en `app/build.gradle.kts`, modelos de modo, emparejamiento/PIN, receptor de boot, Overlay, LAN, contracts/API, CI y la release GitHub más reciente.
- El estado GitHub no confirma que el portal ni la TV estén instalados. Documentar los tres estados por separado.

## Firmas actuales — no perder ni regenerar
- Certificado de la APK Signage 2026: X.509 SHA256 `52468eda51fbe7de6886fd800874f73566bdaa7165732e96f03764483840ec2f`. Requerido para futuras actualizaciones en sitio.
- Clave pública RSA 2026 del actualizador web: en repo web `.release-assets/update-public.txt`, DER SHA256 `5ad31c4313966cbb414facb56fcd8196be065910201aabfedfbee042bc2c49ae`. En hosting `config/update-rsa-pubkey.pem`.
- Clave pública histórica de Sports/Turnos: repo web `app/trust/legacy-apk-rsa-public.txt`, DER SHA256 `1c6623f7cd4ed5b1c18b7f3254e6e050a39c6bea8f2d7cd9faaecc56287a10f4`; solo paquetes heredados Sports/Turnos.
- Material privado de firma: conservarlo en almacenamiento seguro y persistente fuera de repositorios/chat/artefactos. Nunca cambiar de firmante por perder el entorno; detener publicación si el original no está disponible.

## Arquitectura aprobada
- Cartelera: pantalla completa. Overlay, Turnos y Marcador comparten las playlists publicitarias de Overlay por formato/orientación; los motores funcionales de tickets y puntos siguen separados.
- Selector de fuente única Automático/Manual local; no confundirlo con `enabled_modes`. No pedir checks de modos redundantes.

## Incidencias observadas todavía abiertas
1. PIN definido en portal antes del emparejamiento: la aplicación 0.4.22 solicita crear PIN antes de buscar la orden remota; servidor ata orden a token emparejado. Resolver flujo y demostrarlo con prueba de instalación limpia.
2. Inicio automático: actualmente puede detenerse por ausencia de catálogo cacheado antes de sincronizar. Asegurar boot+sync e instrumentar causa.
3. Overlay «modo deshabilitado»: revisar y migrar máscara `enabled_modes` en los tres configuradores y backend sin perder datos. Preview compatible no equivale a modo habilitado.
Probar en emuladores API22 y29 y Fire Stick; no declarar solucionado solo por commit.
