package btc.renaud.questcodex

import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.snippets.SnippetDatabase
import com.typewritermc.engine.paper.utils.reloadable
import com.typewritermc.core.extension.annotations.Singleton
import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Replacement [SnippetDatabase] that gracefully handles malformed YAML files.
 *
 * The default implementation throws a [NullPointerException] when the backing
 * `snippets.yml` contains invalid nodes. This version catches those errors and
 * falls back to the provided default value, preventing the engine from
 * spamming the server log while ticking.
 */
@Singleton
class SafeSnippetDatabase : SnippetDatabase, KoinComponent {
    private val plugin: JavaPlugin by inject()

    private val file: File by lazy {
        val file = File(plugin.dataFolder, "snippets.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            runCatching { plugin.saveResource("snippets.yml", false) }
                .onFailure { file.createNewFile() }
        }
        file
    }

    private val ymlConfiguration by reloadable { YamlConfiguration.loadConfiguration(file) }
    private val cache by reloadable { mutableMapOf<String, Any>() }

    override fun get(path: String, default: Any, comment: String): Any {
        cache[path]?.let { return it }

        val value = ymlConfiguration.get(path)
        if (value == null) {
            removeConflictingParents(path)
            ymlConfiguration.set(path, default)
            if (comment.isNotBlank()) {
                runCatching { ymlConfiguration.setComments(path, comment.lines()) }
            }
            cache[path] = default
            runCatching { ymlConfiguration.save(file) }
                .onFailure { logger.warning("Failed to save snippet '$path': ${it.message}") }
            return default
        }

        cache[path] = value
        return value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSnippet(path: String, klass: KClass<T>, default: T, comment: String): T {
        val value = get(path, default, comment)
        val casted: T? = when {
            klass == List::class && value is String -> listOf(value) as T
            klass == List::class && value is List<*> ->
                if (value.isEmpty()) null else value.map { it.toString() } as T
            else -> klass.safeCast(value)
        }
        if (casted == null) {
            removeConflictingParents(path)
            ymlConfiguration.set(path, default)
            if (comment.isNotBlank()) {
                runCatching { ymlConfiguration.setComments(path, comment.lines()) }
            }
            cache[path] = default
            runCatching { ymlConfiguration.save(file) }
                .onFailure { logger.warning("Failed to save snippet '$path': ${it.message}") }
            return default
        }
        return casted
    }

    private fun removeConflictingParents(path: String) {
        val parts = path.split('.')
        if (parts.isEmpty()) return
        for (i in 1 until parts.size) {
            val parent = parts.take(i).joinToString(".")
            val parentValue = ymlConfiguration.get(parent)
            if (parentValue != null && parentValue !is org.bukkit.configuration.ConfigurationSection) {
                ymlConfiguration.set(parent, null)
            }
        }
    }

    override fun registerSnippet(path: String, defaultValue: Any, comment: String) {
        get(path, defaultValue, comment)
    }
}