# Firma permanente de Digital Signage — continuidad 2026

La APK oficial Signage desde v0.4.22-debug usa el certificado X.509 SHA256:
`52468eda51fbe7de6886fd800874f73566bdaa7165732e96f03764483840ec2f`.

Este es el certificado **vigente**, no el usado en las APK 0.4.21 y anteriores:
`5390caf27181bb1163bb165867be5a5a83e748899957f0e0f68606ed6282f47a`.

NO generar un keystore nuevo por sesión o compilación. El firmante correspondiente debe estar respaldado fuera del repositorio, en una bóveda duradera controlada por el propietario, y restaurarse únicamente en el entorno seguro de firmado. El guard `scripts/require_signing_key.py` debe rechazar cualquier firmante cuya huella no coincida con la vigente.

Una clave pública o certificado no permite firmar; hace falta la clave privada correcta. Nunca guardarla o publicarla en GitHub, chats, CI artifacts, logs o paquetes de cliente.

Los artifacts CI marcados `UNSIGNED-CANDIDATE` son pruebas, no el instalable oficial. Al publicar: firmar externamente, verificar con `apksigner`, comprobar minSdk 19, versionCode creciente, SHA256 y que el portal acepte el sobre RSA. La sustitución del firmante anterior por el de 2026 requirió una instalación limpia; NO repetir rotación ni prometer actualización in-place desde firmas incompatibles.

La clave pública de la release web y la clave histórica Sports/Turnos se gestionan en el repositorio `scammarano/2camdigitaltvweb`. No son reemplazables entre sí. Leer `docs/CONTINUIDAD_REPOS_Y_FIRMAS.md` en ambos repositorios antes de hacer otra release.
