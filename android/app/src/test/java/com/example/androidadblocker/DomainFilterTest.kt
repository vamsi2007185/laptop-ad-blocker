package com.example.androidadblocker

import com.example.androidadblocker.filter.DomainFilter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class DomainFilterTest {

    @Test
    fun testExactDomainIsBlocked() {
        val filter = DomainFilter()
        filter.addBlockedDomain("ads.example.com")

        assertTrue(filter.isBlocked("ads.example.com"))
        assertFalse(filter.isBlocked("example.com"))
    }

    @Test
    fun testSubdomainIsBlocked() {
        val filter = DomainFilter()
        filter.addBlockedDomain("doubleclick.net")

        assertTrue(filter.isBlocked("ad.doubleclick.net"))
        assertTrue(filter.isBlocked("sub.ad.doubleclick.net"))
        assertFalse(filter.isBlocked("notdoubleclick.net"))
    }

    @Test
    fun testWhitelistOverridesBlocklist() {
        val filter = DomainFilter()
        filter.addBlockedDomain("example.com")
        filter.addAllowedDomain("safe.example.com")

        assertFalse(filter.isBlocked("safe.example.com"))
        assertFalse(filter.isBlocked("sub.safe.example.com"))
        assertTrue(filter.isBlocked("evil.example.com"))
        assertTrue(filter.isBlocked("example.com"))
    }

    @Test
    fun testParseLineWithCommentsAndHosts() {
        assertEquals("adservice.google.com", DomainFilter.parseLine("0.0.0.0 adservice.google.com # tracker"))
        assertEquals("telemetry.com", DomainFilter.parseLine("127.0.0.1 telemetry.com"))
        assertEquals("pixel.facebook.com", DomainFilter.parseLine("pixel.facebook.com"))
        assertNull(DomainFilter.parseLine("# just a comment"))
        assertNull(DomainFilter.parseLine("127.0.0.1 localhost"))
    }

    @Test
    fun testLoadFromStream() {
        val content = """
            # Ad list
            badtracker.com
            0.0.0.0 ads.network.com # inline comment
        """.trimIndent()

        val filter = DomainFilter()
        filter.loadBlocklist(ByteArrayInputStream(content.toByteArray()))

        assertTrue(filter.isBlocked("badtracker.com"))
        assertTrue(filter.isBlocked("sub.badtracker.com"))
        assertTrue(filter.isBlocked("ads.network.com"))
        assertFalse(filter.isBlocked("safe.org"))
    }
}
