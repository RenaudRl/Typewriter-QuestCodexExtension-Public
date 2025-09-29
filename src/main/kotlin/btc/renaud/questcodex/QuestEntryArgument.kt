package btc.renaud.questcodex

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.command.dsl.ArgumentBlock
import com.typewritermc.engine.paper.command.dsl.DslCommandTree
import com.typewritermc.engine.paper.command.dsl.argument
import com.typewritermc.quest.QuestEntry
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.Locale
import java.util.concurrent.CompletableFuture

fun <S> DslCommandTree<S, *>.questEntry(
    name: String,
    filter: (QuestEntry) -> Boolean = { true },
    block: ArgumentBlock<S, QuestEntry> = {},
) = argument(name, QuestEntryArgumentType(filter), QuestEntry::class, block)

class QuestEntryArgumentType(
    private val filter: (QuestEntry) -> Boolean,
) : CustomArgumentType.Converted<QuestEntry, String> {
    override fun convert(nativeType: String): QuestEntry {
        val quest = Query.findById(QuestEntry::class, nativeType)
            ?: Query.findByName(QuestEntry::class, nativeType)
            ?: throw questNotFound(nativeType)

        if (!filter(quest)) {
            throw SimpleCommandExceptionType(LiteralMessage("Quest did not pass filter")).create()
        }

        return quest
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining
        val loweredInput = input.lowercase(Locale.ROOT)

        Query.findWhere(QuestEntry::class) { quest ->
            if (!filter(quest)) {
                return@findWhere false
            }

            val nameMatches = quest.name.lowercase(Locale.ROOT).startsWith(loweredInput)
            val idMatches = loweredInput.length > 3 && quest.id.lowercase(Locale.ROOT).startsWith(loweredInput)
            nameMatches || idMatches
        }.forEach { quest ->
            builder.suggest(quest.name)
        }

        return builder.buildFuture()
    }

    private fun questNotFound(nativeType: String) =
        SimpleCommandExceptionType(LiteralMessage("Could not find quest $nativeType")).create()
}
