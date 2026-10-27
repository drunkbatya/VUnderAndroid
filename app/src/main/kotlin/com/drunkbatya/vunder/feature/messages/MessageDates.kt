package com.drunkbatya.vunder.feature.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drunkbatya.vunder.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object MessageDates {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val day = DateTimeFormatter.ofPattern("d MMM")
    private val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun short(epochSeconds: Long): String {
        val dateTime = localDateTime(epochSeconds)
        val today = LocalDate.now()
        return when {
            dateTime.toLocalDate() == today -> time.format(dateTime)
            dateTime.year == today.year -> day.format(dateTime)
            else -> full.format(dateTime)
        }
    }

    fun timeOnly(epochSeconds: Long): String = time.format(localDateTime(epochSeconds))

    fun localDate(epochSeconds: Long): LocalDate = localDateTime(epochSeconds).toLocalDate()

    fun fullDate(date: LocalDate): String = full.format(date)

    private fun localDateTime(epochSeconds: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
}

@Composable
internal fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.messages_today)
        today.minusDays(1) -> stringResource(R.string.messages_yesterday)
        else -> MessageDates.fullDate(date)
    }
}
