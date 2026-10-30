package btcrenaud.questcodex.ui

import btcrenaud.gui.editor.api.ButtonTypeRegistry
import btcrenaud.gui.editor.api.MenuEditContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexButtonTypesTest {

    private fun menu(entryType: String) = object : MenuEditContext {
        override val entryId = "test"
        override val entryType = entryType
        override fun stringField(name: String): String? = null
    }

    @BeforeTest
    fun register() = CodexButtonTypes.register()

    @AfterTest
    fun unregister() = CodexButtonTypes.unregister()

    @Test
    fun `every type the resolver parses is declared under the resolver prefix`() {
        val context = menu("category_menu")

        for (type in CodexButtonType.entries) {
            assertEquals(
                true,
                ButtonTypeRegistry.isKnownButtonType(type.name, CodexButtonResolverLayout.PREFIX, context),
                "${type.name} is parsed by CodexButtonResolverLayout but not declared to the editor",
            )
        }
    }

    @Test
    fun `indexed markers stay known through their base type`() {
        assertEquals(true, ButtonTypeRegistry.isKnownButtonType("QUEST_SLOT#3", CodexButtonResolverLayout.PREFIX, menu("open_gui")))
    }

    @Test
    fun `no declared type is unknown to the resolver`() {
        val declared = CodexButtonTypes.contribution.types.map { it.id }

        assertEquals(CodexButtonType.entries.map { it.name }.sorted(), declared.sorted())
    }

    @Test
    fun `a type the resolver does not parse is reported unknown`() {
        assertEquals(false, ButtonTypeRegistry.isKnownButtonType("NOT_A_BUTTON", CodexButtonResolverLayout.PREFIX, menu("category_menu")))
    }

    @Test
    fun `other entry types are not offered codex buttons`() {
        assertNull(ButtonTypeRegistry.isKnownButtonType("BACK", CodexButtonResolverLayout.PREFIX, menu("shop_definition")))
    }

    @Test
    fun `unregistering removes the contribution`() {
        CodexButtonTypes.unregister()

        assertTrue(ButtonTypeRegistry.all().none { it.extensionId == CodexButtonTypes.EXTENSION_ID })
    }
}
