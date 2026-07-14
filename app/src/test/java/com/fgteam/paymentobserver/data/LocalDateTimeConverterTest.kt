package com.fgteam.paymentobserver.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDateTimeConverterTest {
    @Test
    fun `datetime is fixed width text and round trips without timezone conversion`() {
        val localTime = LocalDateTime.of(2026, 7, 13, 10, 15, 0, 123_000_000)

        val stored = LocalDateTimeConverter.fromLocalDateTime(localTime)

        assertEquals("2026-07-13 10:15:00.123", stored)
        assertEquals(localTime, LocalDateTimeConverter.toLocalDateTime(stored))
    }
}
