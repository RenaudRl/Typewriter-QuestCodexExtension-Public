package btcrenaud.questcodex

import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.PluginManager
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class QuestCodexPermissionsTest {

    /** The only two calls the registration makes; anything else is a contract change worth failing on. */
    private fun fakePluginManager(store: MutableMap<String, Permission>): PluginManager =
        Proxy.newProxyInstance(
            PluginManager::class.java.classLoader,
            arrayOf(PluginManager::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getPermission" -> store[args[0] as String]
                "addPermission" -> {
                    val permission = args[0] as Permission
                    store[permission.name] = permission
                    null
                }
                else -> error("unexpected PluginManager call: ${method.name}")
            }
        } as PluginManager

    @Test
    fun `open permission is declared as operator-only, as documented`() {
        val store = mutableMapOf<String, Permission>()

        QuestCodexPermissions.register(fakePluginManager(store))

        val declared = assertNotNull(store["typewriter.codex.open"])
        assertEquals(PermissionDefault.OP, declared.default)
    }

    @Test
    fun `registering twice keeps the permission declared the first time`() {
        val store = mutableMapOf<String, Permission>()
        val manager = fakePluginManager(store)

        QuestCodexPermissions.register(manager)
        val first = store.getValue(QuestCodexPermissions.OPEN)
        QuestCodexPermissions.register(manager)

        assertSame(first, store.getValue(QuestCodexPermissions.OPEN))
    }

    @Test
    fun `a node declared by a permissions plugin is not overwritten`() {
        val granted = Permission(QuestCodexPermissions.OPEN, PermissionDefault.TRUE)
        val store = mutableMapOf(QuestCodexPermissions.OPEN to granted)

        QuestCodexPermissions.register(fakePluginManager(store))

        assertSame(granted, store.getValue(QuestCodexPermissions.OPEN))
    }
}
