package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class PageFlowTest {
    @Test fun pullProgressFillsOverTheHoldTimeAndStopsAtOne() {
        assertEquals(0f, pullToAddProgress(0))
        assertEquals(0.5f, pullToAddProgress(PULL_TO_ADD_HOLD_MS / 2))
        assertEquals(1f, pullToAddProgress(PULL_TO_ADD_HOLD_MS * 2))
        assertEquals(0f, pullToAddProgress(-10))
    }
}
