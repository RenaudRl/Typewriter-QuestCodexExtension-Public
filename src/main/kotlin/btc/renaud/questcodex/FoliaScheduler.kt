package btc.renaud.questcodex

import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

/**
 * Utility scheduler that transparently supports both Paper and Folia runtimes.
 */
object FoliaScheduler {
    private val foliaSchedulersAvailable: Boolean by lazy { detectFolia() }

    private fun detectFolia(): Boolean {
        return try {
            Bukkit::class.java.getMethod("getGlobalRegionScheduler")
            Bukkit::class.java.getMethod("getAsyncScheduler")
            true
        } catch (_: NoSuchMethodException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun runSync(task: () -> Unit) {
        if (!plugin.isEnabled) return
        if (Bukkit.isPrimaryThread()) {
            runCatching { task() }.onFailure { throwable ->
                plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to execute synchronous task", throwable)
            }
            return
        }

        val future = CompletableFuture<Void?>()
        if (foliaSchedulersAvailable) {
            val scheduler = runCatching { Bukkit.getGlobalRegionScheduler() }.getOrNull()
            if (scheduler != null) {
                scheduler.execute(plugin) {
                    executeTask(task, future)
                }
                future.join()
                return
            } else {
                plugin.logger.log(
                    Level.WARNING,
                    "[QuestCodex] Falling back to Bukkit scheduler for synchronous execution"
                )
            }
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            executeTask(task, future)
        })
        future.join()
    }

    fun runAsync(task: () -> Unit) {
        if (!plugin.isEnabled) return
        if (foliaSchedulersAvailable) {
            val asyncScheduler = runCatching { Bukkit.getAsyncScheduler() }.getOrNull()
            if (asyncScheduler != null) {
                asyncScheduler.runNow(plugin) { _ ->
                    runCatching { task() }.onFailure { throwable ->
                        plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to execute asynchronous task", throwable)
                    }
                }
                return
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching { task() }.onFailure { throwable ->
                plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to execute asynchronous task", throwable)
            }
        })
    }

    fun cancelAllTasks() {
        if (!plugin.isEnabled) return
        Bukkit.getScheduler().cancelTasks(plugin)
        if (foliaSchedulersAvailable) {
            runCatching { Bukkit.getGlobalRegionScheduler().cancelTasks(plugin) }
            runCatching { Bukkit.getAsyncScheduler().cancelTasks(plugin) }
        }
    }

    private fun executeTask(task: () -> Unit, future: CompletableFuture<Void?>) {
        try {
            task()
            future.complete(null)
        } catch (throwable: Throwable) {
            plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to execute synchronous task", throwable)
            future.completeExceptionally(throwable)
        }
    }
}
