package org.bmp.cph.util

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Bounded daemon pools for blocking client work; never use the common ForkJoin pool for I/O. */
object CphExecutors {
    val network: ExecutorService = pool("network", 6, 256)
    val image: ExecutorService = pool("image", 3, 64)
    val disk: ExecutorService = pool("disk", 2, 64)
    val download: ExecutorService = pool("download", 2, 32)

    fun <T> supply(executor: ExecutorService, task: () -> T): CompletableFuture<T> = try {
        CompletableFuture.supplyAsync({ task() }, executor)
    } catch (exception: RejectedExecutionException) {
        CompletableFuture.failedFuture(exception)
    }

    private fun pool(name: String, threads: Int, queue: Int): ExecutorService = ThreadPoolExecutor(
        threads,
        threads,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(queue),
        NamedDaemonFactory(name),
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    private class NamedDaemonFactory(private val pool: String) : ThreadFactory {
        private val sequence = AtomicInteger()

        override fun newThread(task: Runnable): Thread = Thread(task, "cph-$pool-${sequence.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
}
