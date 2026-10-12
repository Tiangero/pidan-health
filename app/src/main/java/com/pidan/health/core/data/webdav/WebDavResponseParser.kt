package com.pidan.health.core.data.webdav

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PROPFIND multistatus（207 响应体）解析器：纯 JVM 实现，单测可直接覆盖。
 *
 * 兼容带前缀（d:response / D:response）与无前缀两种命名空间写法，
 * 目录项（resourcetype 含 collection）不作为文件返回。
 */
object WebDavResponseParser {

    private val rfc1123 = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        .withZone(ZoneId.of("GMT"))

    /**
     * 解析 multistatus XML，返回目录下的文件列表（不含目录项）。
     * 解析失败抛 [WebDavException]，由调用方决定降级方式。
     */
    fun parse(xml: String): List<WebDavFile> {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrElse {
            throw WebDavException("云端响应解析失败", it)
        }

        val results = mutableListOf<WebDavFile>()
        val responseNodes = document.getElementsByTagName("*").toList()
            .filter { it is Element && localName(it) == "response" }

        for (node in responseNodes) {
            val element = node as Element
            var href: String? = null
            var lastModifiedMillis: Long = 0L
            var sizeBytes: Long = 0L
            var isCollection = false

            for (child in element.childNodes.toList().filterIsInstance<Element>()) {
                when (localName(child)) {
                    "href" -> href = child.textContent?.trim()
                    "propstat" -> {
                        // propstat/prop/{getlastmodified,getcontentlength,resourcetype}
                        val prop = child.children().firstOrNull { localName(it) == "prop" }
                        prop?.children()?.forEach { p ->
                            when (localName(p)) {
                                "getlastmodified" ->
                                    lastModifiedMillis = parseRfc1123(p.textContent?.trim())
                                "getcontentlength" ->
                                    sizeBytes = p.textContent?.trim()?.toLongOrNull() ?: 0L
                                "resourcetype" ->
                                    isCollection = p.children().any { localName(it) == "collection" }
                            }
                        }
                    }
                }
            }

            // resourcetype 缺失时以 href 结尾 '/' 兜底判断目录
            val directory = isCollection || (href?.endsWith("/") == true)
            val name = href?.let { WebDavPaths.decodeName(it) }
            if (!directory && !name.isNullOrBlank()) {
                results.add(WebDavFile(name = name, sizeBytes = sizeBytes, lastModifiedMillis = lastModifiedMillis))
            }
        }
        return results
    }

    /** RFC 1123（"Mon, 12 Oct 2026 07:00:00 GMT"）→ epoch millis；无效返回 0 */
    fun parseRfc1123(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching<Long> {
            // withZone(GMT) 的 formatter 解析后自带 INSTANT_SECONDS，可直接 Instant.from
            val accessor = rfc1123.parse(value, java.text.ParsePosition(0)) ?: return 0L
            Instant.from(accessor).toEpochMilli()
        }.getOrDefault(0L)
    }

    /** 兼容 d:/D:/无前缀：取冒号后的 localName */
    private fun localName(element: Element): String =
        element.tagName.substringAfterLast(':')

    private fun Element.children(): List<Element> =
        childNodes.toList().filterIsInstance<Element>()

    private fun org.w3c.dom.NodeList.toList(): List<org.w3c.dom.Node> =
        (0 until length).map { item(it) }
}
