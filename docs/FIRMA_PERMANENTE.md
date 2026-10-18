# Firma permanente de 2Cam Digital Signage

Las claves debug anteriores no se conservaron entre compilaciones. No es posible recuperarlas a partir de las APK. El certificado permanente de entrega desde 0.4.14 es SHA-256 5390caf27181bb1163bb165867be5a5a83e748899957f0e0f68606ed6282f47a.

Los artefactos CI identificados UNSIGNED-CANDIDATE están firmados solo para pruebas; no son la entrega al usuario. La entrega se vuelve a firmar con el PKCS12 privado conservado por el propietario fuera de GitHub y se verifica con apksigner. Nunca publicar el PKCS12 ni sus contraseñas en código, logs, hosting o artefactos CI. Si falta la clave permanente, detener la entrega; no regenerarla.

El cambio desde una firma anterior puede requerir una única reinstalación. Conservar el mismo applicationId y usar versiones crecientes. Esta revisión no afirma una actualización directa desde la firma perdida de 0.4.13.
