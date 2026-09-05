package com.example.androidadblocker

import com.example.androidadblocker.dns.DnsPacket
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketTest {

    private fun createSampleDnsQueryPacket(domain: String, txId: Short = 0x1234.toShort()): ByteArray {
        val labels = domain.split(".")
        val qnameBuffer = ByteBuffer.allocate(256)
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            qnameBuffer.put(bytes.size.toByte())
            qnameBuffer.put(bytes)
        }
        qnameBuffer.put(0.toByte())
        val qnameBytes = ByteArray(qnameBuffer.position())
        System.arraycopy(qnameBuffer.array(), 0, qnameBytes, 0, qnameBytes.size)

        // DNS header (12 bytes) + Question (qname + 4 bytes)
        val dnsLen = 12 + qnameBytes.size + 4
        val dnsBuffer = ByteBuffer.allocate(dnsLen).order(ByteOrder.BIG_ENDIAN)
        dnsBuffer.putShort(txId)
        dnsBuffer.putShort(0x0100.toShort()) // Standard query, RD=1
        dnsBuffer.putShort(1.toShort())      // QDCOUNT
        dnsBuffer.putShort(0.toShort())      // ANCOUNT
        dnsBuffer.putShort(0.toShort())      // NSCOUNT
        dnsBuffer.putShort(0.toShort())      // ARCOUNT
        dnsBuffer.put(qnameBytes)
        dnsBuffer.putShort(1.toShort())      // QTYPE: A
        dnsBuffer.putShort(1.toShort())      // QCLASS: IN

        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(1, 1, 1, 1)
        val srcPort = 12345
        val dstPort = 53

        return DnsPacket.wrapInIpUdp(srcIp, dstIp, srcPort, dstPort, dnsBuffer.array())
    }

    @Test
    fun testParseValidDnsQuery() {
        val packet = createSampleDnsQueryPacket("ads.doubleclick.net", 0x4321.toShort())
        val parsed = DnsPacket.parseQuery(packet, packet.size)

        assertNotNull(parsed)
        parsed!!
        assertEquals("ads.doubleclick.net", parsed.qName)
        assertEquals(1, parsed.qType)
        assertEquals(0x4321.toShort(), parsed.txId)
        assertEquals(12345, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), parsed.srcIp)
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), parsed.dstIp)
    }

    @Test
    fun testIgnoreNonDnsPacket() {
        val dummy = ByteArray(40)
        dummy[0] = 0x45.toByte() // IPv4
        dummy[9] = 6.toByte()    // TCP instead of UDP

        val parsed = DnsPacket.parseQuery(dummy, dummy.size)
        assertNull(parsed)
    }

    @Test
    fun testBuildNxDomainResponse() {
        val queryPacket = createSampleDnsQueryPacket("pagead2.googlesyndication.com", 0x7788.toShort())
        val query = DnsPacket.parseQuery(queryPacket, queryPacket.size)!!

        val responsePacket = DnsPacket.buildNxDomainResponse(query)

        // Verify IPv4 header
        assertEquals(4, (responsePacket[0].toInt() and 0xF0) ushr 4) // IPv4
        assertEquals(17, responsePacket[9].toInt() and 0xFF)         // UDP

        // Verify IP checksum
        val ihl = (responsePacket[0].toInt() and 0x0F) * 4
        val computedChecksum = DnsPacket.computeChecksum(responsePacket, 0, ihl)
        assertEquals(0, computedChecksum) // Checksum of header including checksum field must be 0

        // Verify DNS header inside response
        val dnsOffset = ihl + 8
        val respTxId = ByteBuffer.wrap(responsePacket, dnsOffset, 2).order(ByteOrder.BIG_ENDIAN).short
        val respFlags = ByteBuffer.wrap(responsePacket, dnsOffset + 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        val anCount = ByteBuffer.wrap(responsePacket, dnsOffset + 6, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

        assertEquals(0x7788.toShort(), respTxId)
        assertEquals(0x8183, respFlags) // Response, Recursion Available, NXDOMAIN
        assertEquals(0, anCount)
    }
}
