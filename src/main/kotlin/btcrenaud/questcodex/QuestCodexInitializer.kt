package btcrenaud.questcodex

import btcrenaud.gui.GuiAudioData
import btcrenaud.gui.GuiType
import btcrenaud.gui.InventorySize
import btcrenaud.gui.LayoutData
import btcrenaud.gui.SimpleLayoutData
import btcrenaud.gui.GuiItemData
import btcrenaud.gui.GuiSlotBuilder
import btcrenaud.gui.api.LayoutParser
import btcrenaud.gui.api.MenuDefinition
import btcrenaud.gui.api.MenuLayout
import btcrenaud.gui.api.SimpleLayout
import btcrenaud.gui.api.Viewport
import btcrenaud.gui.api.CompositeLayout
import btcrenaud.gui.api.ScrollableLayout
import btcrenaud.gui.api.PaginatedLayout
import btcrenaud.gui.api.EmptyLayout
import btcrenaud.gui.services.MenuSessionService
import btcrenaud.questcodex.entries.CategoryMenuEntry
import btcrenaud.questcodex.entries.QuestAssignmentEntry
import btcrenaud.questcodex.entries.QuestCodexConfigEntry
import btcrenaud.questcodex.entries.QuestLoreEntry
import btcrenaud.questcodex.entries.SortModeConfig
import btcrenaud.questcodex.entries.QuestCodexConfig
import btcrenaud.questcodex.navigation.CodexNavAction
import btcrenaud.questcodex.navigation.CodexNavDefaults
import btcrenaud.questcodex.ui.AugmentedSimpleLayout
import btcrenaud.questcodex.ui.CodexButtonResolverLayout
import btcrenaud.questcodex.ui.CodexButtonType
import btcrenaud.questcodex.ui.DynamicSlotContent
import btcrenaud.questcodex.tracking.QuestCodexTrackingService
import btcrenaud.questcodex.recovery.QuestCodexRecoveryService
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.QuestStatus
import com.typewritermc.core.entries.ref
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import com.typewritermc.engine.paper.interaction.PlayerSessionManager

/**
 * Main initializer for the Quest Codex extension.
 *
 * Loads all codex entries, registers categories, assigns quests,
 * and sets up the GUI-based menu system.
 */
@Singleton
object QuestCodexInitializer : Initializable {

    /** Sort mode for quest filtering in category menus. */
    enum class SortMode { ALL, NOT_STARTED, ACTIVE, COMPLETED }

    private val mm = MiniMessage.miniMessage()
    private var currentSortMode: SortMode = SortMode.ALL

    // ── Loaded data ──
    private val categoryMenuEntries = mutableMapOf<String, CategoryMenuEntry>()
    private val questLoreEntries = mutableMapOf<String, MutableList<QuestLoreEntry>>()
    private val playerLifecycleListener = object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onJoin(event: PlayerJoinEvent) {
            event.player.scheduler.runDelayed(
                plugin,
                { QuestCodexTrackingService.restorePrimary(event.player) },
                null,
                2L,
            )
        }
    }

    override suspend fun initialize() {
        val manager = Bukkit.getPluginManager()

        // BTC Engine Native Support
        try {
            val itemCompanion = Class.forName("com.typewritermc.engine.paper.utils.item.Item\$Companion")
            val instance = itemCompanion.getField("INSTANCE").get(null)
            val registerAll = itemCompanion.getMethod("registerAll")
            registerAll.invoke(instance)
            plugin.logger.info("[QuestCodex] BTC Custom Engine detected. Native item types registered.")
        } catch (_: Exception) {
            // Not running on BTC Engine
        }

        // ── Step 1: Load global config ──
        loadGlobalConfig()
        val trackingArtifact = QuestCodexConfig.trackingArtifact.get()
        QuestCodexTrackingService.initialize(trackingArtifact, QuestCodexConfig.maxTrackedQuests)
        if (trackingArtifact == null) {
            plugin.logger.warning(
                "[QuestCodex] No trackingArtifact is configured; multi-tracking will be memory-only."
            )
        }
        QuestCodexRecoveryService.initialize(
            QuestCodexConfig.recoveryArtifact.get(),
            QuestCodexConfig.recoveryEnabled,
            QuestCodexConfig.recoveryRetentionSeconds,
            QuestCodexConfig.recoveryRestoreDelayTicks,
        )
        if (QuestCodexConfig.recoveryEnabled && QuestCodexConfig.recoveryArtifact.get() == null) {
            plugin.logger.warning(
                "[QuestCodex] No recoveryArtifact is configured; dialogue/cinematic recovery is disabled."
            )
        }
        manager.registerEvents(playerLifecycleListener, plugin)

        // ── Step 2: Load category menu configs ──
        Query.find<CategoryMenuEntry>().forEach { entry ->
            categoryMenuEntries[entry.category.lowercase()] = entry
        }

        // ── Step 3: Register categories ──
        Query.find<QuestCategoryDefinitionEntry>().forEach { entry ->
            registerCategory(entry)
        }

        // ── Step 4: Load quest lore entries ──
        Query.find<QuestLoreEntry>().forEach { entry ->
            val questId = entry.quest.id
            if (questId.isNotBlank()) {
                questLoreEntries.getOrPut(questId) { mutableListOf() }.add(entry)
            }
        }

        // ── Step 5: Assign quests to categories ──
        Query.find<QuestAssignmentEntry>().forEach { entry ->
            assignQuests(entry)
        }

        // ── Step 6: Register GUI command handlers ──
        registerCommandHandlers()

        // ── Step 7: Initialize BlueMap integration ──
        BlueMapIntegrationService.initialize()

        plugin.logger.info("[QuestCodex] Initialized: ${QuestCategoryRegistry.all().size} categories, ${categoryMenuEntries.size} menu configs")
    }

    /**
     * Registers custom command handlers for codex internal commands.
     * These are intercepted by [MenuSessionService] before being dispatched as console commands.
     */
    private fun registerCommandHandlers() {
        MenuSessionService.registerCustomCommandHandler("codex:nav ") { player, session, cmd, _, _ ->
            val actionName = cmd.removePrefix("codex:nav ").trim()
            val action = runCatching { CodexNavAction.valueOf(actionName) }.getOrNull()
            if (action != null) {
                handleCodexNav(player, session, action)
            }
        }
        MenuSessionService.registerCustomCommandHandler("codex:open ") { player, _, cmd, _, _ ->
            val categoryName = cmd.removePrefix("codex:open ").trim()
            if (categoryName.isNotBlank()) {
                openCategoryMenu(player, categoryName)
            }
        }
        MenuSessionService.registerCustomCommandHandler("codex:quest ") { player, session, cmd, _, _ ->
            val questId = cmd.removePrefix("codex:quest ").trim()
            if (questId.isNotBlank()) {
                handleQuestClick(player, session, questId)
            }
        }
        MenuSessionService.registerCustomCommandHandler("codex:tracked") { player, _, _, _, _ ->
            openTrackedQuestsMenu(player)
        }
        plugin.logger.info("[QuestCodex] Registered GUI command handlers (codex:nav, codex:open, codex:quest)")
    }

    /**
     * Handles a [CodexNavAction] from a navigation button click.
     */
    private fun handleCodexNav(player: Player, session: MenuSessionService.ActiveSession, action: CodexNavAction) {
        val layout = session.definition.layout
        when (action) {
            CodexNavAction.PAGE_NEXT -> {
                // Find PaginatedLayout and go to next page
                val paginatedId = findFirstPaginatedId(layout)
                if (paginatedId != null) {
                    val current = session.pageStates[paginatedId] ?: 0
                    session.pageStates[paginatedId] = current + 1
                    MenuSessionService.refresh(player)
                }
            }
            CodexNavAction.PAGE_PREV -> {
                val paginatedId = findFirstPaginatedId(layout)
                if (paginatedId != null) {
                    val current = session.pageStates[paginatedId] ?: 0
                    if (current > 0) {
                        session.pageStates[paginatedId] = current - 1
                        MenuSessionService.refresh(player)
                    }
                }
            }
            CodexNavAction.SCROLL_UP -> MenuSessionService.scroll(player, 0, -1)
            CodexNavAction.SCROLL_DOWN -> MenuSessionService.scroll(player, 0, 1)
            CodexNavAction.SCROLL_LEFT -> MenuSessionService.scroll(player, -1, 0)
            CodexNavAction.SCROLL_RIGHT -> MenuSessionService.scroll(player, 1, 0)
            CodexNavAction.BACK -> {
                // BACK always returns to the main menu when one is configured;
                // otherwise it falls back to the session history, then closes.
                val mainConfig = categoryMenuEntries[""]
                when {
                    mainConfig != null && mainConfig.usesLayoutPool -> openMainMenu(player)
                    QuestCodexConfig.mainMenuTrigger.id.isNotBlank() ->
                        listOf(QuestCodexConfig.mainMenuTrigger).triggerEntriesFor(player, context())
                    session.history.isNotEmpty() ->
                        MenuSessionService.register(player, session.history.pop(), pushHistory = false)
                    else -> player.closeInventory()
                }
            }
            CodexNavAction.CLOSE -> player.closeInventory()
            CodexNavAction.SORT -> {
                currentSortMode = when (currentSortMode) {
                    SortMode.ALL -> SortMode.NOT_STARTED
                    SortMode.NOT_STARTED -> SortMode.ACTIVE
                    SortMode.ACTIVE -> SortMode.COMPLETED
                    SortMode.COMPLETED -> SortMode.ALL
                }
                val id = session.definition.id
                if (id.startsWith("codex:category:")) {
                    val categoryName = id.removePrefix("codex:category:")
                    // Save history before re-registering (pushHistory=false discards current session)
                    val savedHistory = session.history.toList()
                    openCategoryMenu(player, categoryName, pushHistory = false)
                    // Restore history to the new session so BACK returns to main menu, not through sort states
                    val newSession = MenuSessionService.getSession(player)
                    if (newSession != null) {
                        newSession.history.clear()
                        newSession.history.addAll(savedHistory)
                    }
                }
            }
        }
    }

    /**
     * Finds the first PaginatedLayout id in the layout tree.
     */
    private fun findFirstPaginatedId(layout: MenuLayout): String? {
        if (layout is PaginatedLayout && layout.id != null) return layout.id
        when (layout) {
            is CompositeLayout -> {
                for (child in layout.children) {
                    val id = findFirstPaginatedId(child)
                    if (id != null) return id
                }
            }
            is ScrollableLayout -> return findFirstPaginatedId(layout.layout)
            else -> layout.innerLayout?.let { return findFirstPaginatedId(it) }
        }
        return null
    }

    /**
     * Handles a quest click/tracking action.
     */
    private fun handleQuestClick(player: Player, session: MenuSessionService.ActiveSession, questId: String) {
        val quest = Query.findById<QuestEntry>(questId)
        if (quest == null) {
            plugin.logger.warning("[QuestCodex] Quest '$questId' not found when clicked.")
            return
        }
        val status = quest.questStatus(player)
        val questName = quest.displayName.get(player)
        when (status) {
            QuestStatus.ACTIVE -> {
                when (QuestCodexTrackingService.toggle(
                    player,
                    quest,
                    QuestCodexConfig.multiTrackingEnabled,
                    QuestCodexConfig.maxTrackedQuests,
                )) {
                    QuestCodexTrackingService.ToggleResult.UNTRACKED -> {
                    val msg = QuestCodexConfig.stoppedTrackingMessage.replace("{quest}", questName).parsePlaceholders(player)
                    if (msg.isNotBlank()) {
                        player.sendMessage(mm.deserialize(msg))
                    }
                    }
                    QuestCodexTrackingService.ToggleResult.TRACKED -> {
                    val msg = QuestCodexConfig.nowTrackingMessage.replace("{quest}", questName).parsePlaceholders(player)
                    if (msg.isNotBlank()) {
                        player.sendMessage(mm.deserialize(msg))
                    }
                    }
                    QuestCodexTrackingService.ToggleResult.LIMIT_REACHED -> {
                        val msg = QuestCodexConfig.trackingLimitMessage
                            .replace("{max}", QuestCodexConfig.maxTrackedQuests.toString())
                            .parsePlaceholders(player)
                        if (msg.isNotBlank()) {
                            player.sendMessage(mm.deserialize(msg))
                        }
                    }
                }
            }
            QuestStatus.INACTIVE -> {
                val msg = QuestCodexConfig.questInactiveMessage.replace("{quest}", questName).parsePlaceholders(player)
                if (msg.isNotBlank()) {
                    player.sendMessage(mm.deserialize(msg))
                }
            }
            QuestStatus.COMPLETED -> {
                val msg = QuestCodexConfig.questCompletedMessage.replace("{quest}", questName).parsePlaceholders(player)
                if (msg.isNotBlank()) {
                    player.sendMessage(mm.deserialize(msg))
                }
            }
        }
        if (session.definition.id == "codex:category:@tracked") {
            // Rebuild the provider so the removed quest disappears immediately and
            // remaining tracked quests compact toward the first configured slot.
            openTrackedQuestsMenu(player, pushHistory = false)
        } else {
            MenuSessionService.refresh(player)
        }
    }

    override suspend fun shutdown() {
        categoryMenuEntries.clear()
        questLoreEntries.clear()
        QuestCategoryRegistry.clear()
        HandlerList.unregisterAll(playerLifecycleListener)
        QuestCodexRecoveryService.shutdown()
        QuestCodexTrackingService.shutdown()
        QuestCodexConfig.reset()
    }

    // ── Global config ──

    private fun loadGlobalConfig() {
        QuestCodexConfig.reset()
        val configEntries = Query.find<QuestCodexConfigEntry>().toList()
        if (configEntries.isEmpty()) {
            plugin.logger.info("[QuestCodex] No quest_codex entry found; using defaults.")
        } else {
            configEntries.forEach { QuestCodexConfig.apply(it) }
        }
    }

    // ── Category registration ──

    private fun registerCategory(entry: QuestCategoryDefinitionEntry) {
        val name = entry.category
        if (name.isBlank()) {
            plugin.logger.warning("[QuestCodex] QuestCategoryDefinitionEntry '${entry.id}' has empty category name; skipping.")
            return
        }

        val menuConfig = categoryMenuEntries[name.lowercase()]

        QuestCategoryRegistry.register(
            name = name,
            title = entry.title.ifBlank { name },
            icon = entry.icon,
            parent = entry.parent,
            order = entry.order,
            activeCriteria = entry.activeCriteria,
            completedCriteria = entry.completedCriteria,
            blockedMessage = parseLines(entry.blockedMessage),
            activeMessage = parseLines(entry.activeMessage),
            completedMessage = parseLines(entry.completedMessage),
            hideWhenLocked = entry.hideWhenLocked,
            rows = menuConfig?.rows ?: QuestCodexConfig.defaultRows,
        )
    }

    // ── Quest assignment ──

    private fun assignQuests(entry: QuestAssignmentEntry) {
        val categoryName = entry.category
        if (categoryName.isBlank()) {
            plugin.logger.warning("[QuestCodex] QuestAssignmentEntry '${entry.id}' has empty category; skipping.")
            return
        }

        if (entry.orders.size > entry.questRefs.size) {
            plugin.logger.warning("[QuestCodex] QuestAssignmentEntry '${entry.id}' has more orders than quest refs.")
        }

        entry.questRefs.forEachIndexed { index, ref ->
            val questId = ref.id
            if (questId.isBlank()) return@forEachIndexed

            val quest = ref.get()
            if (quest == null) {
                plugin.logger.warning("[QuestCodex] Quest '$questId' in assignment '${entry.id}' could not be resolved.")
                return@forEachIndexed
            }

            val order = entry.orders.getOrNull(index)

            // Build item overrides
            val itemOverrides = if (entry.notStartedItem != null || entry.inProgressItem != null || entry.completedItem != null) {
                QuestItemOverrides(
                    notStarted = entry.notStartedItem ?: CustomItem(),
                    inProgress = entry.inProgressItem ?: CustomItem(),
                    completed = entry.completedItem ?: CustomItem(),
                ).takeIf { it.hasOverrides() }
            } else null

            // Build display overrides
            val displayOverrides = QuestDisplayOverrides(
                notStarted = QuestStateDisplayOverride(
                    name = entry.notStartedName,
                    lore = parseLines(entry.notStartedLore),
                    hideQuest = entry.hideWhenNotStarted,
                ),
                inProgress = QuestStateDisplayOverride(
                    name = entry.inProgressName,
                    lore = parseLines(entry.inProgressLore),
                    hideQuest = entry.hideWhenInProgress,
                    hideObjectives = entry.hideObjectivesWhenInProgress,
                ),
                completed = QuestStateDisplayOverride(
                    name = entry.completedName,
                    lore = parseLines(entry.completedLore),
                    hideQuest = entry.hideWhenCompleted,
                    hideObjectives = entry.hideObjectivesWhenCompleted,
                ),
            ).takeIf { it.hasOverrides() }

            // Build additional lore
            val additionalLore = questLoreEntries[questId]?.let { entries ->
                var merged = QuestAdditionalLore()
                entries.filter { it.category.isBlank() || it.category.equals(categoryName, ignoreCase = true) }
                    .forEach { merged = merged.overrideWith(it.toAdditionalLore()) }
                merged.takeIf { it.hasContent() }
            }

            QuestCategoryRegistry.addQuest(
                categoryName = categoryName,
                questRef = ref,
                quest = quest,
                order = order,
                overrides = itemOverrides,
                displayOverrides = displayOverrides,
                additionalLore = additionalLore,
            )
        }
    }

    // ── Menu building ──

    /**
     * Opens a menu using the layout pool from a [CategoryMenuEntry].
     *
     * QUEST_SLOT/CATEGORY_SLOT markers are rewritten into indexed markers
     * ("QUEST_SLOT#0", "QUEST_SLOT#1", ...) BEFORE layout parsing, so they live
     * inside the layout tree and follow scrollable/paginated/frame viewports.
     * [CodexButtonResolverLayout] swaps each visible marker for its quest/category
     * content at render time.
     */
    private fun openMenuFromLayoutPool(
        player: Player,
        menuConfig: CategoryMenuEntry,
        entries: List<*>,
        isMainMenu: Boolean,
        pushHistory: Boolean = true,
        targetViewId: String? = null,
        reopenOnView: (Player, String) -> Unit = { _, _ -> },
    ) {
        val ctx = com.typewritermc.core.interaction.context()
        val rows = menuConfig.rows.coerceIn(1, 6)
        val size = InventorySize.entries.getOrNull(rows - 1) ?: InventorySize.SIZE_54

        // 1. Index dynamic markers so they traverse parsing as ordinary tagged slots.
        val markerType = when {
            isMainMenu -> CodexButtonType.CATEGORY_SLOT.name
            menuConfig.category.equals("@tracked", ignoreCase = true) -> CodexButtonType.TRACKED_QUEST_SLOT.name
            else -> CodexButtonType.QUEST_SLOT.name
        }
        val (indexedPool, markerCount) = indexDynamicMarkers(menuConfig.layoutPool.filterNotNull(), markerType)

        // 2. The sort button stays a fixed overlay (it must not scroll away).
        var sortSlotPos: Pair<Int, Int>? = null
        val finalPool = indexedPool.map { layoutData ->
            val (cleaned, sortPos) = extractSortSlot(layoutData)
            if (sortPos != null) sortSlotPos = sortPos
            cleaned
        }
        val inherited = btcrenaud.gui.api.MenuViewSupport.inherit(
            baseMenuId = menuConfig.baseMenuId,
            ownPool = finalPool,
            ownViews = menuConfig.views,
            ownMainLayoutId = menuConfig.mainLayoutId,
            ownDefaultViewId = menuConfig.defaultViewId,
        )
        val pool = inherited.pool
        val rootLayoutId = inherited.mainLayoutId

        val baseLayoutCleaned: MenuLayout = if (rootLayoutId != null && pool.containsKey(rootLayoutId)) {
            LayoutParser.parse(player, ctx, menuConfig.guiType, size.slots, pool, pool[rootLayoutId]!!)
        } else {
            EmptyLayout
        }

        // 3. Render-time provider mapping marker indices to quest/category content.
        val dynamicProvider: (Int) -> DynamicSlotContent?
        if (isMainMenu) {
            @Suppress("UNCHECKED_CAST")
            val categories = entries as List<QuestCategory>
            if (categories.size > markerCount) {
                plugin.logger.warning("[QuestCodex] Main menu has ${categories.size} categories but only $markerCount CATEGORY_SLOT markers.")
            }
            dynamicProvider = { index ->
                categories.getOrNull(index)?.let { category ->
                    DynamicSlotContent(
                        item = buildCategoryIcon(player, category),
                        commands = listOf("codex:open ${category.name}"),
                    )
                }
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            val questEntries = entries as List<QuestEntry>
            val category = QuestCategoryRegistry.find(menuConfig.category)
            if (questEntries.size > markerCount) {
                plugin.logger.warning("[QuestCodex] Category '${menuConfig.category}' has ${questEntries.size} quests but only $markerCount QUEST_SLOT markers.")
            }
            dynamicProvider = { index ->
                val quest = questEntries.getOrNull(index)
                if (quest == null || category == null) null
                else DynamicSlotContent(
                    item = buildQuestIcon(player, quest, category),
                    commands = listOf("codex:quest ${quest.id}"),
                )
            }
        }

        val dynamicSlots = mutableListOf<btcrenaud.gui.api.GuiSlot>()

        // Build dynamic sort button if SORT_SLOT was found
        if (sortSlotPos != null) {
            val sortModeConfig = when (currentSortMode) {
                SortMode.ALL -> SortModeConfig.ALL
                SortMode.NOT_STARTED -> SortModeConfig.NOT_STARTED
                SortMode.ACTIVE -> SortModeConfig.ACTIVE
                SortMode.COMPLETED -> SortModeConfig.COMPLETED
            }
            val display = menuConfig.sortDisplayFor(sortModeConfig)
            val sortItem = if (display?.item != null) {
                display.item.build(player)
            } else {
                CodexNavDefaults.defaultItem(CodexNavAction.SORT).build(player)
            }
            val sortLabel = display?.label?.takeIf { it.isNotBlank() }
                ?: when (currentSortMode) {
                    SortMode.ALL -> "<yellow>📋 All quests"
                    SortMode.NOT_STARTED -> "<white>📋 Not started"
                    SortMode.ACTIVE -> "<green>📋 In progress"
                    SortMode.COMPLETED -> "<gray>📋 Completed"
                }
            val sortLore = display?.lore?.takeIf { it.isNotEmpty() }
                ?: listOf("<gray>Click to change sorting")
            val sortMeta = sortItem.itemMeta
            sortMeta.displayName(
                mm.deserialize(sortLabel)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            )
            sortMeta.lore(sortLore.map {
                mm.deserialize(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            })
            sortItem.itemMeta = sortMeta
            dynamicSlots.add(btcrenaud.gui.api.GuiSlot(
                x = sortSlotPos.first,
                y = sortSlotPos.second,
                item = sortItem,
                allowPickup = false,
                commands = listOf("codex:nav SORT"),
            ))
        }

        fun decorate(base: MenuLayout): MenuLayout = CodexButtonResolverLayout(
            inner = AugmentedSimpleLayout(inner = base, dynamicSlots = dynamicSlots),
            player = player,
            dynamicProvider = dynamicProvider,
        )

        val fallbackTitle = if (isMainMenu) {
            menuConfig.title.ifBlank { "<dark_gray>Quest Codex" }
        } else {
            menuConfig.title.ifBlank { QuestCategoryRegistry.find(menuConfig.category)?.title ?: menuConfig.category }
        }

        val resolvedView = if (inherited.views.isEmpty()) null else
            btcrenaud.gui.api.MenuViewSupport.resolve(
                player = player,
                context = ctx,
                guiType = menuConfig.guiType,
                totalSize = size.slots,
                pool = pool,
                mainLayoutId = rootLayoutId,
                views = inherited.views,
                defaultViewId = inherited.defaultViewId,
                targetViewId = targetViewId,
                baseTitle = fallbackTitle,
                breadcrumbSeparator = menuConfig.breadcrumbSeparator,
                decorate = ::decorate,
            )

        val resolvedLayout = resolvedView?.layout ?: decorate(baseLayoutCleaned)
        val rawTitle = (resolvedView?.rawTitle ?: fallbackTitle).parsePlaceholders(player)

        val componentTitle = try {
            mm.deserialize(rawTitle)
        } catch (_: Exception) {
            mm.deserialize("<white>${rawTitle.replace("&", "§")}")
        }

        val definition = MenuDefinition(
            id = if (isMainMenu) "codex:main" else "codex:category:${menuConfig.category.lowercase()}",
            type = menuConfig.guiType,
            title = componentTitle,
            rawTitle = rawTitle,
            size = size,
            layout = resolvedLayout,
            audio = btcrenaud.gui.api.MenuAudioConfig(
                onOpen = QuestCodexConfig.soundOnOpen,
                onClick = QuestCodexConfig.soundOnClick,
                onScroll = QuestCodexConfig.soundOnSwitch,
            ),
            activeViewId = resolvedView?.activeViewId,
            breadcrumb = resolvedView?.breadcrumb ?: emptyList(),
            viewSwitcher = if (inherited.views.isEmpty()) null else reopenOnView,
        )

        MenuSessionService.register(player, definition, pushHistory = pushHistory)
    }

    /**
     * Rewrites QUEST_SLOT/CATEGORY_SLOT marker items into single-position indexed
     * markers ("QUEST_SLOT#0", "QUEST_SLOT#1", ...). Indexing follows pool order,
     * then item order, then the item's own repetition (row-major), giving a stable
     * quest ordering that survives viewport clipping and scrolling.
     */
    private fun indexDynamicMarkers(pool: List<LayoutData>, markerType: String): Pair<List<LayoutData>, Int> {
        var counter = 0
        val rewritten = pool.map { data ->
            if (data !is SimpleLayoutData) return@map data
            val newItems = data.items.flatMap { item ->
                if (item.buttonType != markerType) listOf(item)
                else expandMarkerPositions(item).map { (px, py) ->
                    item.copy(
                        buttonType = "$markerType#${counter++}",
                        buttonPrefix = "codex_button:",
                        x = px,
                        y = py,
                        direction = null,
                        count = 1,
                        repeatY = 1,
                    )
                }
            }
            data.copy(items = newItems)
        }
        return rewritten to counter
    }

    private fun extractSortSlot(data: LayoutData): Pair<LayoutData, Pair<Int, Int>?> {
        return when (data) {
            is SimpleLayoutData -> {
                var pos: Pair<Int, Int>? = null
                val remaining = data.items.filter { item ->
                    if (item.buttonType == "SORT_SLOT") {
                        pos = Pair(item.x, item.y)
                        false
                    } else true
                }
                data.copy(items = remaining) to pos
            }
            else -> data to null
        }
    }

    /**
     * Expands a marker item's repetition into individual positions. Delegates to
     * [GuiSlotBuilder.expandPositions] so codex markers always spread exactly like the
     * GUI extension renders them — a private copy of this maths is how the semantics
     * drifted between extensions in the first place.
     */
    private fun expandMarkerPositions(item: GuiItemData): List<Pair<Int, Int>> =
        GuiSlotBuilder.expandPositions(item)

    /**
     * Opens the main codex menu (list of root categories) for a player.
     *
     * Supports two modes:
     * - **Layout Pool Mode**: Uses [CategoryMenuEntry.usesLayoutPool].
     *   Categories are injected as dynamic slots at positions tagged "codex_button:CATEGORY_SLOT".
     * - **Legacy Mode**: Builds the menu programmatically (kept for backward compatibility).
     */
    fun openMainMenu(player: Player, targetViewId: String? = null) {
        val categories = QuestCategoryRegistry.roots().filter { it.isVisible(player) }
        val menuConfig = categoryMenuEntries[""]

        if (menuConfig == null || !menuConfig.usesLayoutPool) {
            // Fallback: a custom main menu (e.g. an open_gui entry) declared in the quest_codex config.
            val trigger = QuestCodexConfig.mainMenuTrigger
            if (trigger.id.isNotBlank()) {
                listOf(trigger).triggerEntriesFor(player, context())
                return
            }
            plugin.logger.warning("[QuestCodex] Main menu opened but no category_menu entry with layout pool found. Create a category_menu entry with empty category, or set mainMenuTrigger in the quest_codex config.")
            return
        }

        fun openOn(target: Player, viewId: String?, push: Boolean) {
            openMenuFromLayoutPool(
                target,
                menuConfig,
                categories,
                isMainMenu = true,
                pushHistory = push,
                targetViewId = viewId,
                reopenOnView = { next, nextViewId ->
                    openOn(next, nextViewId, menuConfig.pushHistoryOnViewSwitch)
                },
            )
        }
        openOn(player, targetViewId, true)
    }

    /**
     * Opens a category menu (list of quests in a category) for a player.
     *
     * Uses the [CategoryMenuEntry] layout pool for full customization.
     * Quests are injected as dynamic slots at positions tagged "codex_button:QUEST_SLOT".
     */
    fun openCategoryMenu(
        player: Player,
        categoryName: String,
        pushHistory: Boolean = true,
        targetViewId: String? = null,
    ) {
        val category = QuestCategoryRegistry.find(categoryName)
        if (category == null) {
            plugin.logger.warning("[QuestCodex] Category '$categoryName' not found.")
            return
        }

        val menuConfig = categoryMenuEntries[categoryName.lowercase()]
        val allQuests = category.getVisibleQuests(player)
        // Filter by current sort mode
        val quests = when (currentSortMode) {
            SortMode.ALL -> allQuests
            SortMode.NOT_STARTED -> allQuests.filter { it.questStatus(player) == QuestStatus.INACTIVE }
            SortMode.ACTIVE -> allQuests.filter { it.questStatus(player) == QuestStatus.ACTIVE }
            SortMode.COMPLETED -> allQuests.filter { it.questStatus(player) == QuestStatus.COMPLETED }
        }

        if (menuConfig == null || !menuConfig.usesLayoutPool) {
            plugin.logger.warning("[QuestCodex] Category '$categoryName' opened but no category_menu entry with layout pool found. Create a category_menu entry for this category with a layout pool.")
            return
        }

        fun openOn(target: Player, viewId: String?, push: Boolean) {
            openMenuFromLayoutPool(
                target,
                menuConfig,
                quests,
                isMainMenu = false,
                pushHistory = push,
                targetViewId = viewId,
                reopenOnView = { next, nextViewId ->
                    openOn(next, nextViewId, menuConfig.pushHistoryOnViewSwitch)
                },
            )
        }
        openOn(player, targetViewId, pushHistory)
    }

    fun openTrackedQuestsMenu(player: Player, pushHistory: Boolean = true) {
        val menuConfig = categoryMenuEntries["@tracked"]
        if (menuConfig == null || !menuConfig.usesLayoutPool) {
            plugin.logger.warning("[QuestCodex] No category_menu with category '@tracked' is configured.")
            return
        }
        val quests = QuestCodexTrackingService.trackedQuestIds(player)
            .mapNotNull { Query.findById<QuestEntry>(it) }
        val syntheticCategory = QuestCategoryRegistry.register(
            name = "@tracked",
            title = menuConfig.title.ifBlank { "<gold>Tracked quests" },
            rows = menuConfig.rows,
        )
        syntheticCategory.quests.clear()
        syntheticCategory.quests.addAll(quests.map { it.ref() })
        fun openOn(target: Player, viewId: String?, push: Boolean) {
            openMenuFromLayoutPool(
                target,
                menuConfig,
                quests,
                isMainMenu = false,
                pushHistory = push,
                targetViewId = viewId,
                reopenOnView = { next, nextViewId ->
                    openOn(next, nextViewId, menuConfig.pushHistoryOnViewSwitch)
                },
            )
        }
        openOn(player, null, pushHistory)
    }

    // ── Icon building ──

    private fun buildCategoryIcon(player: Player, category: QuestCategory): ItemStack {
        val item = if (!category.icon.isEffectivelyEmpty()) {
            category.icon.build(player)
        } else {
            ItemStack(Material.BOOK)
        }

        val meta = item.itemMeta ?: return item
        val status = category.categoryStatus(player)

        // Display name
        val nameComponent = category.title.parsePlaceholders(player).asMiniWithoutItalic()
        meta.displayName(nameComponent)

        // Lore
        val loreLines = mutableListOf<String>()
        val quests = category.allQuests()
        val total = quests.size
        val completed = quests.count { it.questStatus(player) == QuestStatus.COMPLETED }

        loreLines.add(QuestCodexConfig.categoryProgressMessage.replace("{completed}", completed.toString()).replace("{total}", total.toString()))
        loreLines.add(QuestCodexConfig.categoryClickHint)

        when (status) {
            CategoryStatus.BLOCKED -> loreLines.addAll(category.blockedMessage)
            CategoryStatus.IN_PROGRESS -> loreLines.addAll(category.activeMessage)
            CategoryStatus.COMPLETED -> loreLines.addAll(category.completedMessage)
        }

        meta.lore(loreLines.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
        item.itemMeta = meta
        return item
    }

    private fun buildQuestIcon(player: Player, quest: QuestEntry, category: QuestCategory): ItemStack {
        val status = quest.questStatus(player)
        val overrides = category.questItems[quest.id]
        val displayOverrides = category.questDisplays[quest.id]
        val additionalLore = category.questAdditionalLore[quest.id]

        // Base item
        val item = overrides?.itemFor(status)?.build(player)
            ?: ItemStack(when (status) {
                QuestStatus.COMPLETED -> Material.EMERALD
                QuestStatus.ACTIVE -> Material.CLOCK
                QuestStatus.INACTIVE -> Material.BOOK
            })

        val meta = item.itemMeta ?: return item

        // Display name
        val name = displayOverrides?.state(status)?.name?.ifBlank { null }
            ?: quest.displayName.get(player).parsePlaceholders(player)
        meta.displayName(name.asMiniWithoutItalic())

        // Lore
        val loreLines = mutableListOf<String>()
        val stateOverride = displayOverrides?.state(status)
        stateOverride?.lore?.let { loreLines.addAll(it) }

        // Additional lore
        additionalLore?.forStatus(status)?.let { loreLines.addAll(it) }

        // Track hint
        if (status == QuestStatus.ACTIVE) {
            loreLines.add(
                if (QuestCodexTrackingService.isTracked(player, quest.id)) {
                    QuestCodexConfig.questUntrackHint
                } else {
                    QuestCodexConfig.questTrackHint
                }
            )
        }

        meta.lore(loreLines.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
        item.itemMeta = meta
        return item
    }

    // ── Helpers ──

    private fun QuestCategory.isVisible(player: Player): Boolean {
        if (hideWhenLocked && categoryStatus(player) == CategoryStatus.BLOCKED) return false
        return true
    }

    private fun QuestCategory.getVisibleQuests(player: Player): List<QuestEntry> {
        return allQuests()
            .filter { quest ->
                val status = quest.questStatus(player)
                val display = questDisplays[quest.id]
                when (status) {
                    QuestStatus.INACTIVE -> !(display?.notStarted?.hideQuest ?: false)
                    QuestStatus.ACTIVE -> !(display?.inProgress?.hideQuest ?: false)
                    QuestStatus.COMPLETED -> !(display?.completed?.hideQuest ?: false)
                }
            }
            .sortedWith(compareBy({ questOrders[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    private fun QuestLoreEntry.toAdditionalLore(): QuestAdditionalLore = QuestAdditionalLore(
        notStarted = parseLines(notStartedLore),
        inProgress = parseLines(inProgressLore),
        completed = parseLines(completedLore),
    )

    private fun parseLines(raw: String): List<String> {
        val sanitized = raw.replace("\r", "")
        if (sanitized.isBlank()) return emptyList()
        return sanitized.split("\n")
    }

    private fun parseSlotRanges(ranges: List<String>): List<Int> {
        val slots = mutableListOf<Int>()
        ranges.forEach { range ->
            if (range.contains("-")) {
                val parts = range.split("-")
                val start = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                slots.addAll(start..end)
            } else {
                range.trim().toIntOrNull()?.let { slots.add(it) }
            }
        }
        return slots.filter { it in 0..53 }
    }
}
