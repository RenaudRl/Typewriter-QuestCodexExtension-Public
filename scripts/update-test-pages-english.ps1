param(
    [Parameter(Mandatory = $false)]
    [string] $PagesPath = 'H:\Serveurs Minecraft\serveur typewriter officiel\plugins\Typewriter\pages'
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$replacements = [ordered]@{
    'При достижении 1000 опыта' = 'Upon reaching 1,000 experience'
    'он автоматически конвертируется в 1 wellcoin.' = 'it is automatically converted into 1 Wellcoin.'
    'он автоматически конвертируется в 1' = 'it is automatically converted into 1'
    'Ежедневные квесты дают' = 'Daily quests grant'
    'Количество выполненных заданий:' = 'Completed quests:'
    'Взаимодействовать с функциональными блоками' = 'Interact with functional blocks'
    'Попасть по сущностям снарядом' = 'Hit entities with a projectile'
    'Получить урон от любого источника' = 'Take damage from any source'
    'Добыть любой вид бревна' = 'Mine logs of any type'
    'Совершить торговлю' = 'Complete trades'
    'Добыть "Железная руда"' = 'Mine iron ore'
    'Использовать функциональные блоки' = 'Use functional blocks'
    'Количество опыта:' = 'Experience:'
    'Еженедельные задания' = 'Weekly Quests'
    'Ежемесячные задания' = 'Monthly Quests'
    'Ежедневные задания' = 'Daily Quests'
    'Уникальные задания' = 'Unique Quests'
    'Еженедельное задание' = 'Weekly Quest'
    'Ежемесячное задание' = 'Monthly Quest'
    'Ежедневное задание' = 'Daily Quest'
    'Уникальное задание' = 'Unique Quest'
    'Вернуться в главное меню' = 'Return to the main menu'
    'Нажмите для смены фильтра' = 'Click to change filter'
    'Вы уже выполнили это задание' = 'You have already completed this quest'
    'Вы перестали отслеживать:' = 'You stopped tracking:'
    'Вы начали отслеживать:' = 'You are now tracking:'
    'Это задание ещё не начато' = 'This quest has not started yet'
    'Нажмите, чтобы отслеживать' = 'Click to track'
    'Всего заданий:' = 'Total quests:'
    'Общий прогресс:' = 'Overall progress:'
    'Не начато:' = 'Not started:'
    'В процессе:' = 'In progress:'
    'Выполнено:' = 'Completed:'
    'Статистика и информация' = 'Statistics and Information'
    'Фильтр: Все задания' = 'Filter: All quests'
    'Фильтр: Не начатые' = 'Filter: Not started'
    'Фильтр: В процессе' = 'Filter: In progress'
    'Фильтр: Выполненные' = 'Filter: Completed'
    'Нажмите, чтобы открыть' = 'Click to open'
    'Прокрутить список' = 'Scroll the list'
    'Меню заданий!' = 'Quest Menu!'
    'Создать предметы' = 'Craft items'
    'Установить блоки' = 'Place blocks'
    'Установить блок' = 'Place blocks'
    'Потреблять еду' = 'Consume food'
    'Добыть железную руду' = 'Mine iron ore'
    'Добыть брёвна' = 'Mine logs'
    'Получить урон' = 'Take damage'
    'Убить зомби' = 'Kill zombies'
    'Убить скелета' = 'Kill skeletons'
    'Убить паука' = 'Kill spiders'
    'Убить сущность зомби' = 'Kill zombies'
    'Убить сущность скелет' = 'Kill skeletons'
    'Убить сущность паук' = 'Kill spiders'
    'Зайдите в игру' = 'Log in to the server'
    'Выполняется' = 'In progress'
    'Выполнено' = 'Completed'
    'Примечание:' = 'Note:'
    'Прогресс:' = 'Progress:'
    'Задача:' = 'Objective:'
    'Тип:' = 'Type:'
    'Фильтр:' = 'Filter:'
    'Назад' = 'Back'
    'Вверх' = 'Up'
    'Вниз' = 'Down'
    ' раз' = ' times'
    ' опыта.' = ' experience.'
}

$targets = @(
    (Join-Path $PagesPath '30OCcFbQ2UvvFkp.json'),
    (Join-Path $PagesPath 'quest_definitions_temp.json')
)

foreach ($path in $targets) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required Typewriter page not found: $path"
    }

    $content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $backupPath = "$path.$timestamp.bak"
    [System.IO.File]::WriteAllText($backupPath, $content, $utf8NoBom)

    foreach ($pair in $replacements.GetEnumerator()) {
        $content = $content.Replace($pair.Key, $pair.Value)
    }
    $content = $content.Replace(' Раз', ' times')
    $content = $content.Replace('Добыть', 'Mine')
    $content = $content.Replace('Железная руда', 'iron ore')

    # These two test-page slots were authored with the former cursor-copy ghost semantics.
    # Keeping allowPickup=false provides the intended non-removable button behaviour.
    $content = $content.Replace('"isGhost":true', '"isGhost":false')

    if ([System.IO.Path]::GetFileName($path) -eq '30OCcFbQ2UvvFkp.json') {
        $trackingArtifactId = 'questCodexTrackingData'
        if (-not $content.Contains('"type":"quest_codex_tracking_artifact"')) {
            $generatedArtifactId = [guid]::NewGuid().ToString()
            $artifactEntry = '{"id":"' + $trackingArtifactId + '","name":"quest_codex_tracking_data","type":"quest_codex_tracking_artifact","artifactId":"' + $generatedArtifactId + '"}'
            $content = $content.Replace('"entries":[', '"entries":[' + $artifactEntry + ',')
        }
        if ($content.Contains('"artifactId":"quest_codex_tracking"')) {
            $content = $content.Replace(
                '"artifactId":"quest_codex_tracking"',
                '"artifactId":"' + [guid]::NewGuid().ToString() + '"'
            )
        }
        if (-not $content.Contains('"trackingArtifact":"' + $trackingArtifactId + '"')) {
            $content = $content.Replace(
                '"maxTrackedQuests":9,',
                '"maxTrackedQuests":9,"trackingArtifact":"' + $trackingArtifactId + '",'
            )
        }

        # Keep the page portable with older QuestCodex builds too. Current builds enforce
        # this prefix in code when indexing dynamic markers.
        $content = $content.Replace(
            '"buttonType":"TRACKED_QUEST_SLOT","x":0',
            '"buttonType":"TRACKED_QUEST_SLOT","buttonPrefix":"codex_button:","x":0'
        )
        $content = $content.Replace(
            '{"item":{"case":"custom_item","value":{"components":[{"case":"material","value":{"material":"CLOCK"}}]}},"displayName":"<gold>{quest}","lore":["<gray>Click to stop tracking"]',
            '{"item":{"case":"custom_item","value":{"components":[{"case":"material","value":{"material":"GRAY_STAINED_GLASS_PANE"}}]}},"displayName":"<dark_gray>Empty tracking slot","lore":[]'
        )

        $trackedLayoutStart = '"id":"tracked_quests_layout","items":['
        $backButton = '{"item":{"case":"custom_item","value":{"components":[{"case":"material","value":{"material":"ARROW"}}]}},"displayName":"<red>← Back","lore":["<gray>Return to the Quest Menu"],"criteria":[],"allowPickup":false,"modifiers":[],"triggers":[],"interactionList":[],"isGhost":false,"cooldownTicks":0,"buttonType":"BACK","buttonPrefix":"codex_button:","x":4,"y":2,"count":1,"direction":null,"gap":1,"repeatY":1}'
        if (-not $content.Contains('"lore":["<gray>Return to the Quest Menu"]')) {
            $content = $content.Replace($trackedLayoutStart, "$trackedLayoutStart$backButton,")
        }
        $content = $content.Replace(
            '"buttonType":"BACK","buttonPrefix":"codex_button:","x":0,"y":2',
            '"buttonType":"BACK","buttonPrefix":"codex_button:","x":4,"y":2'
        )
    }

    if ($content -match '[А-Яа-яЁё]') {
        $remaining = [regex]::Matches($content, '[^"\\]*[А-Яа-яЁё][^"\\]*') |
            ForEach-Object { $_.Value } |
            Sort-Object -Unique
        throw "Untranslated Cyrillic text remains in $path`n$($remaining -join "`n")"
    }

    $null = $content | ConvertFrom-Json
    [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
    Write-Host "Updated $path (backup: $backupPath)"
}
