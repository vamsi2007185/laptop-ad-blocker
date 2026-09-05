package com.example.androidadblocker.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Forwards allowed DNS queries to upstream DNS resolver.
 */
class DnsResolver(
    var upstreamHost: String = "1.1.1.1",
    var upstreamPort: Int = 53,
    private val socketProtector: ((DatagramSocket) -> Boolean)? = null
) {
    fun resolve(dnsPayload: ByteArray, timeoutMs: Int = 3000): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socketProtector?.invoke(socket)
            socket.soTimeout = timeoutMs

            val upstreamAddr = InetAddress.getByName(upstreamHost)
            val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddr, upstreamPort)
            socket.send(sendPacket)

            val buffer = ByteArray(4096)
            val recvPacket = DatagramPacket(buffer, buffer.size)
            socket.receive(recvPacket)

            recvPacket.data.copyOfRange(0, recvPacket.length)
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }
}
