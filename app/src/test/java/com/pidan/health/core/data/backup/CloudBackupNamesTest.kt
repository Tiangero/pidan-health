package com.pidan.health.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T13：云端备份文件命名与时间戳解析单测 */
class CloudBackupNamesTest {

    @Test
    fun `manual file name format is timestamped`() {
        val millis = 1760000000000L
        val name = CloudBackupNames.manualFileName(millis)
        assertTrue(name.startsWith("皮蛋健康_backup_"))
        assertTrue(name.endsWith(".zip"))
        // yyyyMMdd_HHmmss = 15 字符
        assertEquals(15, name.removePrefix("皮蛋健康_backup_").removeSuffix(".zip").length)
    }

    @Test
    fun `timestamp roundtrip`() {
        val millis = System.currentTimeMillis()
        val name = CloudBackupNames.manualFileName(millis)
        val parsed = CloudBackupNames.extractTimestampMillis(name)
        // 文件名只保留到秒，解析值允许秒内误差
        assertTrue(parsed != null && Math.abs(parsed - millis) < 1000)
    }

    @Test
    fun `later backup produces greater timestamp`() {
        val earlier = CloudBackupNames.manualFileName(1760000000000L)
        val later = CloudBackupNames.manualFileName(1760000060000L)
        assertTrue(
            CloudBackupNames.extractTimestampMillis(later)!! >
                CloudBackupNames.extractTimestampMillis(earlier)!!
        )
    }

    @Test
    fun `non backup names return null`() {
        assertNull(CloudBackupNames.extractTimestampMillis(CloudBackupNames.AUTO_FILE_NAME))
        assertNull(CloudBackupNames.extractTimestampMillis("other.zip"))
        assertNull(CloudBackupNames.extractTimestampMillis("皮蛋健康_backup_notatime.zip"))
        assertNull(CloudBackupNames.extractTimestampMillis("皮蛋健康_backup_20261012_15304.zip"))
    }
}
