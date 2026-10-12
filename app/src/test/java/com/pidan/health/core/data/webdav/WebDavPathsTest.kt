package com.pidan.health.core.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * WebDavPaths 路径拼接测试：核心保护点是
 * segment 内含 '/' 时必须拆段编码，绝不能把 '/' 编码成 %2F
 * （T13 联调发现的真 bug：上传文件落不进备份目录）。
 */
class WebDavPathsTest {

    @Test
    fun `join 每段独立编码且不产生 %2F`() {
        val dir = CloudDirFixture.DIR
        val file = "皮蛋健康_backup_20261012_090000.zip"
        val url = WebDavPaths.join("https://dav.jianguoyun.com/dav/", "$dir/$file")

        assertFalse("路径中不应出现 %2F: $url", url.contains("%2F"))
        assertEquals(
            "https://dav.jianguoyun.com/dav/" +
                WebDavPaths.encodeSegment(dir) + "/" + WebDavPaths.encodeSegment(file),
            url
        )
    }

    @Test
    fun `join 多段与单段含斜杠写法等价`() {
        val dir = CloudDirFixture.DIR
        val file = "auto.zip"
        assertEquals(
            WebDavPaths.join("https://example.com/dav/", dir, file),
            WebDavPaths.join("https://example.com/dav/", "$dir/$file")
        )
    }

    @Test
    fun `join 忽略空段与多余斜杠`() {
        assertEquals(
            WebDavPaths.join("https://example.com/dav", "a", "b.zip"),
            WebDavPaths.join("https://example.com/dav//", "/a//b.zip")
        )
    }
}

/** 引用真实备份目录名，避免测试硬编码与 CloudBackupNames 漂移 */
private object CloudDirFixture {
    val DIR: String = com.pidan.health.core.data.backup.CloudBackupNames.REMOTE_DIR
}
