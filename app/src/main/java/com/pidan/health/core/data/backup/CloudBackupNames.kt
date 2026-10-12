package com.pidan.health.core.data.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 云端备份文件命名（T13）：
 * - 自动备份固定覆盖一个文件（不堆积）
 * - 手动备份带秒级时间戳（多端「最新时间戳为准」的依据）
 * 纯 JVM 实现，单测可直接覆盖。
 */
object CloudBackupNames {

    /** 云端备份目录（相对 WebDAV baseUrl） */
    const val REMOTE_DIR = "皮蛋健康"

    /** 自动备份固定文件名（每次覆盖） */
    const val AUTO_FILE_NAME = "皮蛋健康_auto.zip"

    private const val MANUAL_PREFIX = "皮蛋健康_backup_"

    private val manualFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /** 手动备份文件名：皮蛋健康_backup_20261012_153045.zip */
    fun manualFileName(atMillis: Long): String =
        MANUAL_PREFIX +
            LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
                .format(manualFormatter) + ".zip"

    /**
     * 从文件名解析备份时间戳（多端冲突时「最新为准」的排序依据）；
     * 非备份命名文件返回 null（按服务器 lastModified 兜底）。
     */
    fun extractTimestampMillis(fileName: String): Long? {
        if (!fileName.startsWith(MANUAL_PREFIX) || !fileName.endsWith(".zip")) return null
        val stamp = fileName.removePrefix(MANUAL_PREFIX).removeSuffix(".zip")
        if (stamp.length != 15 || stamp[8] != '_') return null
        return runCatching {
            LocalDateTime.parse(stamp, manualFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
