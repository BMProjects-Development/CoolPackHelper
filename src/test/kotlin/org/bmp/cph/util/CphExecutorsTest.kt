package org.bmp.cph.util

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors

class CphExecutorsTest {
    @Test
    fun `rejected work becomes a failed future instead of crashing the caller`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdownNow()

        val future = CphExecutors.supply(executor) { "never" }

        assertThrows(CompletionException::class.java) { future.join() }
    }
}
