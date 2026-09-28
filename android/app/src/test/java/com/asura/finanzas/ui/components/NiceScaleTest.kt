package com.asura.finanzas.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceScaleTest {
    @Test
    fun ticksCarryNoFloatingPointNoise() {
        // Sep 2026 income: a 3,000 step came out as 3000.0000000000005 and the
        // axis printed "12000.000000000002".
        val s = niceScale(0.0, 11_748.19, fromZero = true)
        assertEquals(listOf(0.0, 3_000.0, 6_000.0, 9_000.0, 12_000.0), s.ticks)
        assertEquals(listOf("0", "3000", "6000", "9000", "12000"), s.ticks.map(::jsNumber))
    }

    @Test
    fun largerStepsStayRound() {
        val s = niceScale(0.0, 40_468.84, fromZero = true)
        assertEquals(listOf(0.0, 15_000.0, 30_000.0, 45_000.0, 60_000.0), s.ticks)
        assertEquals(60_000.0, s.max, 0.0)
    }

    @Test
    fun fractionalStepsStayClean() {
        // recharts' getNiceTickValues([0, 0.37], 5, true) on the web; in plain
        // doubles 3 × 0.095 is 0.28500000000000003.
        val s = niceScale(0.0, 0.37, fromZero = true)
        assertEquals(listOf("0", "0.095", "0.19", "0.285", "0.38"), s.ticks.map(::jsNumber))
    }
}
