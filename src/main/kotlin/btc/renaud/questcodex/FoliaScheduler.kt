package btc.renaud.questcodex

import com.typewritermc.engine.paper.plugin
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Folia-aware scheduler that transparently supports both Paper and Folia/ASPaper runtimes.
 *
 * All repeating-task APIs on Folia throw [UnsupportedOperationException] when called from
 * the async coroutine thread that [com.typewritermc.core.extension.InitializableManager] uses.
 * Use this object instead of [org.bukkit.Bukkit.getScheduler] in every [com.typewritermc.core.extension.Initializable].
 */
object FoliaScheduler {

    private val isFolia: Boolean by lazy {
        try {
            Bukkit::class.java.getMethod("getGlobalRegionScheduler")
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Cancellable handle — unified across Bukkit and Folia
    // -------------------------------------------------------------------------

    interface TaskHandle {
        fun cancel()
        val isCancelled: Boolean
    }

    private class BukkitHandle(private val task: BukkitTask) : TaskHandle {
        override fun cancel() = task.cancel()
        override val isCancelled get() = task.isCancelled
    }

    private class FoliaHandle(private val task: ScheduledTask) : TaskHandle {
        override fun cancel() { task.cancel() }
        override val isCancelled get() = task.isCancelled
    }

    // -------------------------------------------------------------------------
    // Schedule on the server main thread / global region (one-shot)
    // -------------------------------------------------------------------------

    /**
     * Runs [block] on the main thread (Bukkit) or global region thread (Folia) as soon as possible.
     */
    fun runTask(block: () -> Unit): TaskHandle {
        if (isFolia) {
            val task = Bukkit.getGlobalRegionScheduler().run(plugin) { _ -> block() }
            return FoliaHandle(task)
        }
        return BukkitHandle(Bukkit.getScheduler().runTask(plugin, block))
    }

    /**
     * Runs [block] after [delayTicks] ticks on the main thread / global region thread.
     */
    fun runLater(delayTicks: Long, block: () -> Unit): TaskHandle {
        if (isFolia) {
            val task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { _ -> block() }, delayTicks)
            return FoliaHandle(task)
        }
        return BukkitHandle(Bukkit.getScheduler().runTaskLater(plugin, block, delayTicks))
    }

    // -------------------------------------------------------------------------
    // Repeating task — global region (main-thread equivalent on Folia)
    // -------------------------------------------------------------------------

    /**
     * Schedules a repeating task on the main thread / global region thread.
     *
     * @param initialDelayTicks ticks before first execution
     * @param periodTicks       ticks between subsequent executions
     * @param block             the work to run each tick
     * @return a [TaskHandle] that can be used to cancel the task
     */
    fun runAtFixedRate(initialDelayTicks: Long, periodTicks: Long, block: () -> Unit): TaskHandle {
        if (isFolia) {
            val task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                Consumer<ScheduledTask> { block() },
                initialDelayTicks.coerceAtLeast(1),
                periodTicks.coerceAtLeast(1)
            )
            return FoliaHandle(task)
        }
        return BukkitHandle(
            Bukkit.getScheduler().runTaskTimer(plugin, block, initialDelayTicks, periodTicks)
        )
    }

    // -------------------------------------------------------------------------
    // Async (off main thread)
    // -------------------------------------------------------------------------

    /**
     * Runs [block] asynchronously (off the main thread).
     */
    fun runAsync(block: () -> Unit): TaskHandle {
        if (isFolia) {
            val task = Bukkit.getAsyncScheduler().runNow(plugin) { _ -> block() }
            return FoliaHandle(task)
        }
        return BukkitHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, block))
    }

    /**
     * Schedules a repeating async task.
     */
    fun runAsyncAtFixedRate(initialDelayTicks: Long, periodTicks: Long, block: () -> Unit): TaskHandle {
        if (isFolia) {
            val periodMs = periodTicks * 50L
            val initialDelayMs = initialDelayTicks * 50L
            val task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                Consumer<ScheduledTask> { block() },
                initialDelayMs,
                periodMs,
                TimeUnit.MILLISECONDS
            )
            return FoliaHandle(task)
        }
        return BukkitHandle(
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, block, initialDelayTicks, periodTicks)
        )
    }

    /**
     * Executes [block] synchronously on the main thread / global region thread.
     * Blocks the calling thread until the task completes. Safe to call from any thread.
     */
    fun runSync(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            block()
            return
        }
        if (isFolia) {
            val future = java.util.concurrent.CompletableFuture<Unit>()
            Bukkit.getGlobalRegionScheduler().execute(plugin) {
                try { block(); future.complete(Unit) }
                catch (t: Throwable) { future.completeExceptionally(t) }
            }
            future.join()
        } else {
            val future = java.util.concurrent.CompletableFuture<Unit>()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try { block(); future.complete(Unit) }
                catch (t: Throwable) { future.completeExceptionally(t) }
            })
            future.join()
        }
    }
}
