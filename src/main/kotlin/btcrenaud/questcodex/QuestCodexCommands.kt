package btcrenaud.questcodex

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.*
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture

/**
 * Custom argument type for QuestCodex category names with tab completion.
 */
class CodexCategoryArgumentType : CustomArgumentType.Converted<String, String> {
    override fun convert(nativeType: String): String {
        val category = QuestCategoryRegistry.find(nativeType)
            ?: throw SimpleCommandExceptionType(
                LiteralMessage("Unknown category: $nativeType")
            ).create()
        return nativeType
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining.lowercase()
        QuestCategoryRegistry.all()
            .filter { it.name.lowercase().startsWith(input) }
            .forEach { builder.suggest(it.name) }
        return builder.buildFuture()
    }
}

/**
 * `/typewriter codex` commands.
 * - `/tw codex` → opens the main codex menu (requires a category_menu entry with empty category)
 * - `/tw codex <category>` → opens a specific category with tab completion
 */
@TypewriterCommand
fun CommandTree.questCodexCommands() = literal("codex") {
    withPermission("typewriter.codex.open")

    // /tw codex → opens the main codex menu
    executePlayer { player ->
        QuestCodexInitializer.openMainMenu(player)
    }

    literal("tracked") {
        executePlayer { player ->
            QuestCodexInitializer.openTrackedQuestsMenu(player)
        }
    }

    // /tw codex <category> → opens specific category
    argument("category", CodexCategoryArgumentType(), String::class) { categoryArg ->
        executePlayer { player ->
            val category = categoryArg()
            QuestCodexInitializer.openCategoryMenu(player, category)
        }
    }
}
