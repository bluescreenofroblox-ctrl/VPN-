package com.example.vpnclient.util

import com.example.vpnclient.model.VlessConfig
import com.example.vpnclient.model.VlessLink
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object VlessParser {
    
    /**
     * Парсит VLESS ссылку в формате:
     * vless://uuid@address:port?parameters#name
     */
    fun parseVlessLink(link: String): VlessConfig? {
        return try {
            if (!link.startsWith("vless://")) {
                return null
            }

            val vlessUri = link.substring(8) // убираем "vless://"
            
            // Разделяем на основную часть и параметры
            val (mainPart, queryAndName) = if (vlessUri.contains("?")) {
                val parts = vlessUri.split("?", limit = 2)
                parts[0] to parts[1]
            } else {
                vlessUri to ""
            }

            // Разделяем на параметры и имя
            val (queryPart, name) = if (queryAndName.contains("#")) {
                val parts = queryAndName.split("#", limit = 2)
                parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
            } else {
                queryAndName to "VLESS Config"
            }

            // Парсим основную часть: uuid@address:port
            val (credentialPart, hostPart) = if (mainPart.contains("@")) {
                val parts = mainPart.rsplit("@", 1)
                parts[0] to parts[1]
            } else {
                return null
            }

            val uuid = credentialPart.trim()
            val (address, portStr) = if (hostPart.contains(":")) {
                val parts = hostPart.rsplit(":", 1)
                parts[0] to parts[1]
            } else {
                return null
            }

            val port = portStr.toIntOrNull() ?: return null

            // Парсим параметры
            val params = parseQueryParameters(queryPart)

            return VlessConfig(
                name = name,
                uuid = uuid,
                address = address,
                port = port,
                transport = params["type"] ?: "tcp",
                tls = params["security"] == "tls" || params["security"] == "reality",
                tlsServerName = params["sni"] ?: "",
                path = params["path"] ?: "",
                host = params["host"] ?: "",
                headerType = params["headerType"] ?: "none",
                congestion = params["congestion"] ?: "",
                alpn = params["alpn"] ?: "",
                allowInsecure = params["allowInsecure"]?.toBoolean() ?: false,
                fingerprint = params["fp"] ?: "",
                publicKey = params["publicKey"] ?: "",
                shortId = params["shortId"] ?: "",
                spiderX = params["spiderX"] ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Парсит query параметры
     */
    private fun parseQueryParameters(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (query.isEmpty()) return params

        query.split("&").forEach { param ->
            val keyValue = param.split("=", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0]
                val value = try {
                    URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
                } catch (e: Exception) {
                    keyValue[1]
                }
                params[key] = value
            }
        }
        return params
    }

    /**
     * Генерирует VLESS ссылку из конфига
     */
    fun generateVlessLink(config: VlessConfig): String {
        val params = mutableListOf<String>()
        
        if (config.transport != "tcp") params.add("type=${config.transport}")
        if (config.tls) {
            params.add("security=tls")
            if (config.tlsServerName.isNotEmpty()) params.add("sni=${config.tlsServerName}")
            if (config.fingerprint.isNotEmpty()) params.add("fp=${config.fingerprint}")
            if (config.alpn.isNotEmpty()) params.add("alpn=${config.alpn}")
        }
        if (config.path.isNotEmpty()) params.add("path=${config.path}")
        if (config.host.isNotEmpty()) params.add("host=${config.host}")
        if (config.headerType != "none") params.add("headerType=${config.headerType}")
        if (config.allowInsecure) params.add("allowInsecure=true")
        
        val queryString = params.joinToString("&")
        val fullQuery = if (queryString.isNotEmpty()) "?$queryString" else ""
        
        return "vless://${config.uuid}@${config.address}:${config.port}$fullQuery#${config.name}"
    }
}
