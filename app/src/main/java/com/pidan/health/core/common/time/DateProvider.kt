package com.pidan.health.core.common.time

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "今天"的抽象供给源。业务层（快照生成、当日聚合）一律经此获取日期，
 * 不得直接调 LocalDate.now()，否则无法在单测中控制时间。
 * 日期格式与 daily_snapshot 主键一致："YYYY-MM-DD"。
 */
interface DateProvider {
    fun today(): String

    /** n 天前的日期字符串（days=1 即昨天），供"近7天日均"等区间计算 */
    fun daysAgo(days: Int): String
}

/** 生产环境默认实现（minSdk 26，java.time 可直接使用） */
object SystemDateProvider : DateProvider {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun today(): String = LocalDate.now().format(formatter)

    override fun daysAgo(days: Int): String = LocalDate.now().minusDays(days.toLong()).format(formatter)
}
