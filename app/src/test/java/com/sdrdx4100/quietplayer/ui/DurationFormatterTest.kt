package com.sdrdx4100.quietplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {
    @Test fun formatsMinutesAndSeconds() = assertEquals("3:07", formatDuration(187_000))
    @Test fun formatsHours() = assertEquals("1:02:03", formatDuration(3_723_000))
    @Test fun clampsNegativeDurations() = assertEquals("0:00", formatDuration(-1))
}
