package com.digitalsignage.tv

import android.os.Build
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLHandshakeException

/** Enable TLS 1.2 on Android 4.4; preserve standard trust and hostname verification. */
object LegacyTls {
    fun configure(connection: HttpsURLConnection) {
        if (Build.VERSION.SDK_INT in 19..20)
            connection.sslSocketFactory = Tls12SocketFactory(connection.sslSocketFactory)
    }
    private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        private fun configureSocket(socket: Socket): Socket {
            if (socket is SSLSocket) {
                if (!socket.supportedProtocols.contains("TLSv1.2"))
                    throw SSLHandshakeException("TLS 1.2 no disponible en este Android")
                socket.enabledProtocols = arrayOf("TLSv1.2")
            }
            return socket
        }
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            configureSocket(delegate.createSocket(socket, host, port, autoClose))
        override fun createSocket(host: String, port: Int): Socket =
            configureSocket(delegate.createSocket(host, port))
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
            configureSocket(delegate.createSocket(host, port, local, localPort))
        override fun createSocket(host: InetAddress, port: Int): Socket =
            configureSocket(delegate.createSocket(host, port))
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
            configureSocket(delegate.createSocket(host, port, local, localPort))
    }
}
