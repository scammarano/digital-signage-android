# 2Cam Digital Signage · 0.4.12-debug · Playlists y control remoto

## Alcance
Rama experimental `feat/player-playlists-remote-v0412`, derivada de `feat/config-ack-debug-v0411` (que conserva `feat/sports-direct-lan`).
No afecta a la instalación web actual. El repositorio web solo contiene documentación: para implementar el formulario web se requiere el snapshot PHP/MySQL VIGENTE, no paquetes viejos.

## Menú accesible durante reproducción
- **Flecha ARRIBA × 3**, tres pulsaciones en máximo 2,5 segundos: abre Ajustes con PIN.
- **Flecha ABAJO × 3**, tres pulsaciones en máximo 2,5 segundos: fuerza sincronización del manifiesto y contenido.
- MENU conserva acceso cuando existe en el control. Mantener el diagnóstico DEBUG (versión, modo, ACK y errores) seleccionable.
- El atajo se intercepta antes de que un VideoView o botón con foco absorba las flechas.

## Transiciones admitidas
| Tipo | Efecto | Variable | Rango / recomendación |
|---|---|---|---|
| `cut` | cambio inmediato | no requiere duración | 0 ms |
| `fade` | disolución | `transition_ms` | 300–1500 ms |
| `slide-left` | entra desde derecha a izquierda | `transition_ms` | 400–1200 ms |
| `slide-right` | entra desde izquierda a derecha | `transition_ms` | 400–1200 ms |
| `zoom` | movimiento progresivo sobre IMAGEN | `zoom_percent`, `zoom_direction`, `zoom_duration_ms` | 12–25 %, `in/out`, 3000–8000 ms |

### Zoom: detalle importante
En la APK anterior el zoom cambiaba un 6 % y duraba como máximo 3.000 ms, además no aparecía en la primera imagen.
En la rama nueva:
- `zoom_percent` = porcentaje de ampliación (ej. 20 => 1.20×). Default 20, límite 0–60.
- `zoom_direction` = `in` (acercar, default) o `out` (alejar).
- `zoom_duration_ms` = duración independiente del movimiento (300–20000 ms). Se limita al tiempo de la imagen.
- `transition_ms` = disolución entre una imagen y la siguiente (0–5000 ms). Si el portal NO envía `zoom_duration_ms`, la APK calcula un zoom visible de `max(1800,transition_ms*4)` milisegundos.
- Si la imagen dura 5 segundos, pedir zoom de 9 segundos no la alarga: el movimiento termina cuando sale la imagen. Los videos pueden usar fade, cut y slide; zoom de imagen no se aplica al video.
- La primera imagen ahora también ejecuta su zoom.

**Ejemplo de preset suave para imágenes:** `transition=zoom`, `duration=10`, `transition_ms=700`, `zoom_percent=20`, `zoom_direction=in`, `zoom_duration_ms=7000`.

**Ejemplo de preset dinámico:** `transition=zoom`, `duration=6`, `transition_ms=450`, `zoom_percent=28`, `zoom_direction=out`, `zoom_duration_ms=4800`.

## Solicitud UI al portal web (no implementada aún)
En el CRUD de renglones de playlist:
1. Selector `Transición` y ayuda desplegable contextual breve.
2. `Duración de transición (ms)` con unidad real y preset sugerido.
3. Si Zoom: selector `Acercar/Alejar`, intensidad %, duración del zoom (ms), mini-preview (imagen de prueba, sin cambiar producción).
4. Avisar si el zoom supera el tiempo de exposición. No usar sliders ambiguos sin mostrar números.
5. Permitir aplicar preset a un renglón o a todos los elementos, conservando overrides por pieza.
6. Tras guardar, marcar dispositivos afectados como `sincronización pendiente` y permitir `Actualizar pantallas ahora`. Mostrar recibido/aplicado/efectivo por pantalla, no confundir Guardado con Reproducido.

## Cambio de playlists sin reiniciar
La APK compara ahora TODA la definición activa: orden, SHA, segundos de exposición, transición, ms, zoom y layout.
Solo descarga/valida el caché completo cuando cambia el manifiesto de programación o falta un medio; si no cambia, evita descargar y verificar repetidamente.
- Con DEBUG activado: consulta normal de playlist aproximadamente cada 15 s (más latencia de red/decodificación).
- Sin DEBUG: sigue el intervalo configurable `sync_minutes` (mínimo 1 minuto). Los cambios urgentes solicitados desde el portal usan `control` cada ~15 s *si la web los emite*.
- Desde TV: ABAJO × 3 fuerza la sincronización incluso si todavía no venció el intervalo.

## Criterios de prueba
- Crear playlist con imágenes A,B,C; variar solo la transición B; la APK debe mostrar nueva transición sin salir.
- Probar Zoom en imagen 1 y 2, in/out, 5/20/30% y 1000/6000ms.
- Modificar orden y duración, y comprobar cambio sin salir.
- Pulsar ARRIBA × 3 durante video para llegar a PIN; Cancelar vuelve al reproductor.
- Pulsar ABAJO × 3 y comprobar evento de sincronización en logs / HUD.
- Desconectar Internet: reproducir último manifiesto validado. Reconectar: recuperar cambios.
- No declarar actualizado si API del portal no publica la revisión o el manifiesto correcto.
