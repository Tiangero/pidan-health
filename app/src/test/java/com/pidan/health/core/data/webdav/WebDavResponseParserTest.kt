package com.pidan.health.core.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T13：PROPFIND multistatus 解析器单测（前缀/无前缀/URL 编码文件名/目录排除） */
class WebDavResponseParserTest {

    @Test
    fun `parse files with D prefix skips collection and decodes encoded name`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/backup/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                    <D:getlastmodified>Mon, 12 Oct 2026 07:00:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/backup/%E7%9A%AE%E8%9B%8B%E5%81%A5%E5%BA%B7_auto.zip</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength>12345</D:getcontentlength>
                    <D:getlastmodified>Mon, 12 Oct 2026 07:00:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/backup/pidan_backup_20261012.zip</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength>999</D:getcontentlength>
                    <D:getlastmodified>Sun, 11 Oct 2026 23:30:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val files = WebDavResponseParser.parse(xml)

        assertEquals(2, files.size)
        val auto = files.first { it.name == "皮蛋健康_auto.zip" }
        assertEquals(12345L, auto.sizeBytes)
        assertEquals(parseExpectedMillis("Mon, 12 Oct 2026 07:00:00 GMT"), auto.lastModifiedMillis)
        assertTrue(files.none { it.name == "backup" })
    }

    @Test
    fun `parse without namespace prefix`() {
        val xml = """
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/a.zip</href>
                <propstat>
                  <prop>
                    <resourcetype/>
                    <getcontentlength>1</getcontentlength>
                    <getlastmodified>Mon, 12 Oct 2026 07:00:00 GMT</getlastmodified>
                  </prop>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val files = WebDavResponseParser.parse(xml)

        assertEquals(1, files.size)
        assertEquals("a.zip", files[0].name)
    }

    @Test
    fun `trailing slash href treated as directory fallback`() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/backup/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>0</D:getcontentlength>
                  </D:prop>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        assertTrue(WebDavResponseParser.parse(xml).isEmpty())
    }

    @Test
    fun `invalid xml throws WebDavException`() {
        try {
            WebDavResponseParser.parse("not xml at all")
            throw AssertionError("expected WebDavException")
        } catch (e: WebDavException) {
            // 预期
        }
    }

    @Test
    fun `invalid date returns zero and does not crash`() {
        assertEquals(0L, WebDavResponseParser.parseRfc1123("garbage"))
        assertEquals(0L, WebDavResponseParser.parseRfc1123(null))
        assertEquals(0L, WebDavResponseParser.parseRfc1123(""))
    }

    private fun parseExpectedMillis(rfc1123: String): Long =
        WebDavResponseParser.parseRfc1123(rfc1123)
}
