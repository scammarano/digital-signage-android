# APK 0.4.20-debug: catálogo central de Turnos y Sports

Base: APK 0.4.19, conservando instalador Fire, geometría real, Sports LAN y paridad de los 43 ajustes. Requiere portal 0.4.29 o posterior para este nuevo endpoint.

La pantalla única consulta `api-application-config.php?app=turnos|sports` con su bearer de dispositivo, en segundo plano cada 15 segundos. ABAJO × 3 solicita también esta actualización. El portal debe tener una asignación activa de la pantalla a una sala o pista. No se usan claves de integración de lectura en la APK.

Se valida esquema, empresa, revisión, una única asignación de pantalla y entidades de su sala/pista/sede. Se persiste de forma atómica una copia privada ligada a la URL del portal y a la credencial de emparejamiento; otro portal o emparejamiento no puede reutilizarla. Un error de red conserva la última copia válida. HTTP 401/403/422 elimina la asignación local, evitando conservar una asignación revocada tras reconectar. Una revisión anterior o esquema incompatible conserva la copia válida y muestra fallo de consulta.

La identidad y sala/pista aparecen en el panel. El HUD indica revisión guardada localmente o copia local tras consulta fallida. Los eventos `application_config_received` y `application_config_applied` se exportan por el sistema de logs existente. Estos eventos NO son un endpoint formal ACK v2 ni acreditan llamadas, puntos o envío de WhatsApp.

Esta entrega lee el catálogo de organización. Las llamadas/puntos siguen sus canales existentes; Sports LAN conserva su autoridad y reglas. No crea encuentros, emite tickets, cambia puntuación, descarga logos ni activa controladores desde el catálogo. Las aplicaciones remotas y el motor central de eventos son fases pendientes.

Pruebas instrumentadas API 22/29: copia persistente; separación por portal, token y aplicación; rechazo de esquema, revisión antigua, empresa distinta, doble pantalla y entidades fuera del ámbito; revocación sin destruir previamente la copia por respuestas inválidas. Se mantienen las pruebas de instalador, actualización, proporción, Overlay y Sports LAN. La operación contra el hosting y en Fire Stick físico requiere validación allí.
