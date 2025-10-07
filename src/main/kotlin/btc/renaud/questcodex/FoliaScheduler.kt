package btc.renaud.questcodex

import com.tcoded.folialib.FoliaLib
import com.typewritermc.engine.paper.plugin

object FoliaScheduler {
    private val foliaLib = FoliaLib(plugin)

    fun runSync(task: () -> Unit) {
        foliaLib.scheduler.runNextTick { task() }
    }

    fun runAsync(task: () -> Unit) {
        foliaLib.scheduler.runAsync { task() }
    }

    fun cancelAllTasks() {
        foliaLib.scheduler.cancelAllTasks()
    }
}
