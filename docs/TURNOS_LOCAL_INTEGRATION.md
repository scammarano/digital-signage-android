# Integración nativa Turnos → Digital Signage · candidato 0.4.17

Base: `feat/four-modes-parity-v0413` @ `aeb113e678b4a48edb09a69a8f2af834f9f411db` (APK 0.4.16). No reemplaza `main` ni la entrega firmada del otro hilo. La APK original Turnos 0.2.0 sigue siendo la autoridad y el control.

## Uso previsto tras firmar e instalar esta revisión de Signage

1. Inicia Turnos en el Fire principal en modo Pantalla principal. El servicio conserva la cola aunque abras Signage.
2. En Turnos abre Enlazar y selecciona **Visor secundario** (no supervisor/operador). Anota dirección y código.
3. En Signage: Configuración con PIN → **Turnos LAN · enlazar APK Turnos como visor**. Introduce IP privada y código. El token se guarda en AndroidKeyStore, en un espacio distinto al token del portal.
4. Configura modo **Turnos**, su diseño y su playlist desde los ajustes existentes de Signage. Los formatos centrales, orientación y asignaciones de playlist se mantienen. El nuevo enlace es una credencial local, no parte del esquema público de opciones.
5. Los controles de recepción/operador siguen enlazados a Turnos. Signage solo consulta snapshot/eventos, sin acceso a operaciones de ticket.

Consulta local cada segundo, independiente del intervalo de medios. Conserva snapshot público y muestra **ESTADO DESACTUALIZADO** si cae red, autoridad o autorización. No acepta otra identidad ni una secuencia anterior. El enlace de visor no consume una de las tres plazas de controles; usa una de las cinco plazas de visores de Turnos.

Para audio, elige una sola pantalla: silencia Turnos y las pantallas secundarias antes de habilitar audio en este visor. Tono/TTS dependen del dispositivo. Los videos de modo Turnos permanecen silenciados; se omite la locución histórica al reconectar. Con publicidad temporal a pantalla completa, una llamada activa mantiene el turno visible y evita que la publicidad lo tape.

HTTP se permite para el cliente de LAN solo después de validar IP privada/loopback, rutas limitadas y sin redirecciones. Las llamadas del portal y los medios conservan sus validadores HTTPS y mismo servidor. No usar esta versión LAN en redes no confiables; no es aún el contrato HTTPS multiempresa.

No inicia silenciosamente otra app tras reiniciar: abre Turnos principal después del reboot. La dirección puede necesitar actualizarse si cambia DHCP. No se modifica el portal en esta entrega.

## Verificación

Workflow `turnos-integration.yml`: pruebas existentes de Sports/formatos/arranque, pruebas del núcleo Turnos, compilación y firma de prueba API19+, y prueba Android API22 del consumidor con el **núcleo y servidor HTTP originales de Turnos** de un checkout fijado. Estas clases de autoridad están únicamente en el APK de instrumentación; un control DEX comprueba que no aparecen en el reproductor.

La instrumentación prueba enlace nativo protegido por PIN, rechazo de rol operador, ticket/puesto junto a imagen publicitaria real de caché, desconexión conservando último estado, reconexión, restauración de autoridad desde disco, revocación y rechazo de otra identidad. Es un servidor real en el mismo emulador usando las clases originales, no una prueba física de dos APK en dos dispositivos. Mando, multicast, audio y latencia end-to-end física siguen pendientes.

La prueba detectó `EADDRINUSE` al reiniciar inmediatamente el listener de Turnos. El checkout de autoridad queda fijado a `67f3782f3a32efdda97c49c28698a72f3f150a14`, con `SO_REUSEADDR` antes del bind y espera acotada del hilo accept al cerrar ([Turnos PR 1](https://github.com/scammarano/2cam-turnos/pull/1)). Incluye cuatro restauraciones seguidas verificando una lectura HTTP tras cada una. Esa corrección necesita una siguiente APK Turnos; no está dentro del binario 0.2.0 ni de su sobre RSA ya publicado.

## Firma y entrega

El artefacto de CI es un **candidato de prueba, NO una actualización para instalar sobre la APK firmada del cliente**. No se publica una release automáticamente. Requiere firma con el certificado permanente documentado en `FIRMA_PERMANENTE.md` y después el sobre RSA que reconoce el portal. No crear ni sustituir claves para superar esta condición.

El sobre de Turnos 0.2.0 ya publicado y el candidato nuevo de Signage son entregas distintas. La clave del certificado Android y la clave RSA del sobre del portal también son distintas.
