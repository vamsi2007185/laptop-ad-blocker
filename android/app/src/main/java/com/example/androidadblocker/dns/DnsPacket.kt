package com.example.androidadblocker.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses raw IPv4 / UDP / DNS packets from the TUN interface and crafts DNS responses.
 */
object DnsPacket {

    data class ParsedDnsQuery(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val ipHeaderLength: Int,
        val txId: Short,
        val qName: String,
        val qType: Int,
        val questionBytes: ByteArray,
        val dnsPayload: ByteArray
    )

    /**
     * Attempts to parse an IPv4 UDP packet targeting DNS port 53.
     * Returns null if the packet is not an IPv4 UDP DNS query.
     */
    fun parseQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28) return null // 20 bytes min IPv4 + 8 bytes UDP

        val version = (packet[0].toInt() and 0xF0) ushr 4
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP is 17

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != 53) return null

        val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        if (udpLen < 8 || length < ihl + udpLen) return null

        val dnsOffset = ihl + 8
        val dnsLen = udpLen - 8
        if (dnsLen < 12) return null // DNS header is 12 bytes

        val txId = ByteBuffer.wrap(packet, dnsOffset, 2).order(ByteOrder.BIG_ENDIAN).short
        val flags = ByteBuffer.wrap(packet, dnsOffset + 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val qdCount = ByteBuffer.wrap(packet, dnsOffset + 4, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        if (qdCount < 1) return null

        // Parse Question Name
        var offset = dnsOffset + 12
        val labels = mutableListOf<String>()
        val packetEnd = dnsOffset + dnsLen

        while (offset < packetEnd) {
            val labelLen = packet[offset].toInt() and 0xFF
            offset++
            if (labelLen == 0) break
            if ((labelLen and 0xC0) != 0) {
                // Compression pointer
                offset++
                break
            }
            if (offset + labelLen > packetEnd) return null
            labels.add(String(packet, offset, labelLen, Charsets.US_ASCII))
            offset += labelLen
        }

        if (offset + 4 > packetEnd) return null
        val qType = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
        val qEnd = offset + 4

        val questionBytes = packet.copyOfRange(dnsOffset + 12, qEnd)
        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val qName = labels.joinToString(".")

        return ParsedDnsQuery(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            ipHeaderLength = ihl,
            txId = txId,
            qName = qName,
            qType = qType,
            questionBytes = questionBytes,
            dnsPayload = dnsPayload
        )
    }

    /**
     * Builds an NXDOMAIN (RCODE=3) DNS response packet inside an IPv4 UDP wrapper.
     */
    fun buildNxDomainResponse(query: ParsedDnsQuery): ByteArray {
        val dnsResponse = buildDnsHeaderAndQuestion(query.txId, 0x8183.toShort(), query.questionBytes)
        return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, dnsResponse)
    }

    /**
     * Wraps a raw DNS response payload from an upstream server into an IPv4 UDP packet.
     */
    fun buildForwardedResponse(query: ParsedDnsQuery, upstreamDnsPayload: ByteArray): ByteArray {
        return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, upstreamDnsPayload)
    }

    private fun buildDnsHeaderAndQuestion(txId: Short, flags: Short, questionBytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(12 + questionBytes.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(txId)
        buffer.putShort(flags)
        buffer.putShort(1.toShort()) // QDCOUNT = 1
        buffer.putShort(0.toShort()) // ANCOUNT = 0
        buffer.putShort(0.toShort()) // NSCOUNT = 0
        buffer.putShort(0.toShort()) // ARCOUNT = 0
        buffer.put(questionBytes)
        return buffer.array()
    }

    /**
     * Wraps payload into an IPv4 + UDP packet with correct checksums.
     */
    fun wrapInIpUdp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + payload.size

        val packet = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        // --- IPv4 Header ---
        packet.put((0x45).toByte()) // Version 4, IHL 5 (20 bytes)
        packet.put(0.toByte())      // DSCP / ECN
        packet.putShort(totalLength.toShort())
        packet.putShort(0x1A2B.toShort()) // Identification
        packet.putShort(0x4000.toShort()) // Flags: Don't Fragment
        packet.put(64.toByte())     // TTL
        packet.put(17.toByte())     // Protocol: UDP
        packet.putShort(0.toShort())// Checksum placeholder
        packet.put(srcIp)
        packet.put(dstIp)

        // Compute and insert IPv4 header checksum
        val ipChecksum = computeChecksum(packet.array(), 0, ipHeaderLen)
        packet.putShort(10, ipChecksum.toShort())

        // --- UDP Header ---
        val udpOffset = ipHeaderLen
        packet.position(udpOffset)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        val udpLen = udpHeaderLen + payload.size
        packet.putShort(udpLen.toShort())
        packet.putShort(0.toShort()) // UDP Checksum (0 = disabled in IPv4 UDP, fully valid per RFC 768)

        // --- Payload ---
        packet.put(payload)

        return packet.array()
    }

    /**
     * Computes standard 16-bit one's complement internet checksum.
     */
    fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        var remaining = length

        while (remaining > 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
            remaining -= 2
        }

        if (remaining == 1) {
            val high = data[i].toInt() and 0xFF
            sum += (high shl 8)
        }

        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return (sum.inv()) and 0xFFFF
    }
}
