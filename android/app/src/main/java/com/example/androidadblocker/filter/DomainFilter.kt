package com.example.androidadblocker.filter

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast in-memory domain filter with hierarchical subdomain matching.
 * Maintains full parity with the Python DomainFilter implementation.
 */
class DomainFilter {
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val allowedDomains = ConcurrentHashMap.newKeySet<String>()

    fun loadBlocklist(stream: InputStream) {
        loadFromStream(stream, blockedDomains)
    }

    fun loadWhitelist(stream: InputStream) {
        loadFromStream(stream, allowedDomains)
    }

    fun addBlockedDomain(domain: String) {
        val normalized = normalize(domain)
        if (normalized.isNotEmpty()) {
            blockedDomains.add(normalized)
        }
    }

    fun removeBlockedDomain(domain: String) {
        blockedDomains.remove(normalize(domain))
    }

    fun addAllowedDomain(domain: String) {
        val normalized = normalize(domain)
        if (normalized.isNotEmpty()) {
            allowedDomains.add(normalized)
        }
    }

    fun removeAllowedDomain(domain: String) {
        allowedDomains.remove(normalize(domain))
    }

    fun getBlockedDomains(): Set<String> = blockedDomains.toSet()
    fun getAllowedDomains(): Set<String> = allowedDomains.toSet()

    /**
     * Checks whether a domain or its parent domains are blocked.
     * Whitelist entries always take precedence over blocklist entries.
     */
    fun isBlocked(domain: String): Boolean {
        val normalized = normalize(domain)
        if (normalized.isEmpty()) return false

        val labels = normalized.split(".")
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (allowedDomains.contains(candidate)) {
                return false
            }
            if (blockedDomains.contains(candidate)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val IGNORED_HOSTS = setOf("localhost", "localhost.localdomain", "broadcasthost")

        fun normalize(domain: String): String {
            return domain.trim().lowercase().removeSuffix(".")
        }

        fun parseLine(raw: String): String? {
            val line = raw.split("#", limit = 2)[0].trim().lowercase()
            if (line.isEmpty()) return null

            val parts = line.split("\\s+".toRegex())
            val domain = parts.last().removePrefix(".")
            if (domain.isNotEmpty() && domain !in IGNORED_HOSTS) {
                return domain.removeSuffix(".")
            }
            return null
        }

        private fun loadFromStream(stream: InputStream, targetSet: MutableSet<String>) {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    parseLine(line)?.let { targetSet.add(it) }
                }
            }
        }
    }
}
