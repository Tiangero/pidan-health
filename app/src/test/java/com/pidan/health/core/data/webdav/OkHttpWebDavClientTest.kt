package com.pidan.health.core.data.webdav

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** T13：OkHttpWebDavClient 与 MockWebServer 的集成单测（认证头/方法/路径/错误映射） */
class OkHttpWebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpWebDavClient
    private lateinit var config: WebDavConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpWebDavClient()
        config = WebDavConfig(
            baseUrl = server.url("/dav/").toString(),
            username = "user@example.com",
            password = "app-pass"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `test connection sends propfind with depth 0 and basic auth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<D:multistatus xmlns:D=\"DAV:\"/>"))

        val result = client.testConnection(config)

        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("0", recorded.getHeader("Depth"))
        assertTrue(recorded.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun `list returns parsed files`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/backup.zip</D:href>
                    <D:propstat><D:prop>
                      <D:resourcetype/>
                      <D:getcontentlength>2048</D:getcontentlength>
                      <D:getlastmodified>Mon, 12 Oct 2026 07:00:00 GMT</D:getlastmodified>
                    </D:prop></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent()
            )
        )

        val files = client.list(config, "皮蛋健康").getOrThrow()

        assertEquals(1, files.size)
        assertEquals("backup.zip", files[0].name)
        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("1", recorded.getHeader("Depth"))
        assertTrue(recorded.path!!.endsWith("/dav/%E7%9A%AE%E8%9B%8B%E5%81%A5%E5%BA%B7"))
    }

    @Test
    fun `list on 404 returns empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(client.list(config, "missing").getOrThrow().isEmpty())
    }

    @Test
    fun `ensure directory creates segments and tolerates exists`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405)) // 已存在
        server.enqueue(MockResponse().setResponseCode(201)) // 新建成功

        val result = client.ensureDirectory(config, "皮蛋健康/子目录")

        assertTrue(result.isSuccess)
        assertEquals("/dav/%E7%9A%AE%E8%9B%8B%E5%81%A5%E5%BA%B7", server.takeRequest().path)
        assertEquals("/dav/%E7%9A%AE%E8%9B%8B%E5%81%A5%E5%BA%B7/%E5%AD%90%E7%9B%AE%E5%BD%95", server.takeRequest().path)
    }

    @Test
    fun `ensure directory fails on auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.ensureDirectory(config, "皮蛋健康")
        assertTrue(result.isFailure)
        assertEquals("账号或应用密码错误", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `upload puts bytes to encoded path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = client.upload(config, "皮蛋健康/a.zip", "hello".toByteArray())

        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("hello", recorded.body.readUtf8())
    }

    @Test
    fun `download returns bytes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("zip-bytes")
        )

        val bytes = client.download(config, "皮蛋健康/a.zip").getOrThrow()

        assertEquals("zip-bytes", String(bytes))
    }

    @Test
    fun `download on 401 maps to friendly message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.download(config, "a.zip")
        assertEquals("账号或应用密码错误", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `blank credentials fail fast without network`() = runTest {
        val result = client.testConnection(config.copy(username = ""))
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }
}
