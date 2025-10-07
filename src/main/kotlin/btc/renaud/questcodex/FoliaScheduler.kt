package btc.renaud.questcodex

import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit

object FoliaScheduler {
    fun runSync(task: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, task)
    }

    fun runAsync(task: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
    }

    fun cancelAllTasks() {
        Bukkit.getScheduler().cancelTasks(plugin)
    }
}
