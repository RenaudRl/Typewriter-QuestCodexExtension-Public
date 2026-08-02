param(
    [Parameter(Mandatory = $false)]
    [string] $PagesPath = 'H:\Serveurs Minecraft\serveur typewriter officiel\plugins\Typewriter\pages'
)

$ErrorActionPreference = 'Stop'
$path = Join-Path $PagesPath 'questcodex-waypoint-recovery-test-20260729.json'
if (-not (Test-Path -LiteralPath $path)) {
    throw "Waypoint test page not found: $path"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$backupPath = "$path.$(Get-Date -Format 'yyyyMMdd-HHmmss').bak"
[System.IO.File]::WriteAllText($backupPath, $content, $utf8NoBom)

$page = $content | ConvertFrom-Json
$waypoint = @($page.entries) | Where-Object id -eq 'questCodexWaypointTest' | Select-Object -First 1
if ($null -eq $waypoint) {
    throw "Waypoint entry questCodexWaypointTest not found in $path"
}

$sampleIcon = '<yellow>' + [char]0x25C6 + '</yellow>'
$waypoint | Add-Member -MemberType NoteProperty -Name icon -Value $sampleIcon -Force
$waypoint.refreshTicks = 1
$waypoint.hideWithinDistance = 0.0
if ($waypoint.PSObject.Properties.Name -notcontains 'hudVisibilityAngle') {
    $waypoint | Add-Member -MemberType NoteProperty -Name hudVisibilityAngle -Value 180.0
}
$waypoint.hudVisibilityAngle = 180.0
$waypoint.hudForwardDistance = 5.5
$waypoint.hudVerticalOffset = 1.0
if ($waypoint.PSObject.Properties.Name -notcontains 'nearTargetDistance') {
    $waypoint | Add-Member -MemberType NoteProperty -Name nearTargetDistance -Value 10.0
}
if ($waypoint.PSObject.Properties.Name -notcontains 'nearTargetVerticalOffset') {
    $waypoint | Add-Member -MemberType NoteProperty -Name nearTargetVerticalOffset -Value 2.25
}
$waypoint.nearTargetDistance = 10.0
$waypoint.nearTargetVerticalOffset = 2.25

$textLayer = @($waypoint.layers) |
    Where-Object { $_.case -eq 'waypoint_text_layer' } |
    Select-Object -First 1
if ($null -eq $textLayer) {
    throw "Text layer not found in questCodexWaypointTest"
}

$textLayer.value.text = "<gold>{icon} {target}</gold>`n<white>{distance} m</white>`n<gray>{direction} {vertical_direction}</gray>"
$waypoint.layers = @(@($waypoint.layers) | Where-Object {
    $_.case -notin @('waypoint_item_layer', 'waypoint_directional_item_layer')
})

# Add a second quest/objective/waypoint so two independently tracked targets can
# be rendered at the same time. The second waypoint uses an explicit objective
# reference; tracking that quest is still required by the resolver.
$existingIds = @($page.entries) | ForEach-Object id
if ($existingIds -notcontains 'questCodexWaypointTestQuest2') {
    $copy = {
        param($value)
        return (($value | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json)
    }

    $activeFact = @($page.entries) | Where-Object id -eq 'questCodexWaypointTestActiveFact' | Select-Object -First 1
    $completedFact = @($page.entries) | Where-Object id -eq 'questCodexWaypointTestCompletedFact' | Select-Object -First 1
    $quest = @($page.entries) | Where-Object id -eq 'questCodexWaypointTestQuest' | Select-Object -First 1
    $objective = @($page.entries) | Where-Object id -eq 'questCodexWaypointTestObjective' | Select-Object -First 1
    $assignment = @($page.entries) | Where-Object id -eq 'questCodexWaypointTestAssignment' | Select-Object -First 1
    if ($null -eq $activeFact -or $null -eq $completedFact -or $null -eq $quest -or
        $null -eq $objective -or $null -eq $assignment) {
        throw "Base waypoint test entries required for the multi-tracking fixture are missing"
    }

    $activeFact2 = & $copy $activeFact
    $activeFact2.id = 'questCodexWaypointTestActiveFact2'
    $activeFact2.name = 'quest_codex_waypoint_test_active_2'
    $activeFact2.comment = 'Keeps the second waypoint test quest active.'

    $completedFact2 = & $copy $completedFact
    $completedFact2.id = 'questCodexWaypointTestCompletedFact2'
    $completedFact2.name = 'quest_codex_waypoint_test_completed_2'
    $completedFact2.comment = 'Reserved for completing the second waypoint test quest.'

    $quest2 = & $copy $quest
    $quest2.id = 'questCodexWaypointTestQuest2'
    $quest2.name = 'quest_codex_waypoint_test_quest_2'
    $quest2.displayName = '<light_purple>Quest Codex Waypoint Test 2'
    @($quest2.activeCriteria)[0].fact = 'questCodexWaypointTestActiveFact2'
    @($quest2.completedCriteria)[0].fact = 'questCodexWaypointTestCompletedFact2'

    $objective2 = & $copy $objective
    $objective2.id = 'questCodexWaypointTestObjective2'
    $objective2.name = 'quest_codex_waypoint_test_objective_2'
    $objective2.quest = 'questCodexWaypointTestQuest2'
    $objective2.display = '<light_purple>Reach waypoint test point 2'
    $objective2.targetLocation.x = 1192.8
    $objective2.targetLocation.y = 115.0
    $objective2.targetLocation.z = 587.79
    $objective2.targetLocation.yaw = 0.0
    $objective2.targetLocation.pitch = 0.0
    $objective2.priorityOverride.value = 90

    $assignment2 = & $copy $assignment
    $assignment2.id = 'questCodexWaypointTestAssignment2'
    $assignment2.name = 'quest_codex_waypoint_test_assignment_2'
    $assignment2.questRefs = @('questCodexWaypointTestQuest2')
    $assignment2.notStartedName = '<light_purple>Waypoint test 2'
    $assignment2.inProgressName = '<light_purple>Waypoint test 2'
    $assignment2.completedName = '<green>Waypoint test 2 completed'
    $assignment2.notStartedLore = '<gray>Second quest for Quest Codex multi-tracking.'
    $assignment2.inProgressLore = '<gray>Track this quest to activate the second waypoint.'
    $assignment2.completedLore = '<green>The second waypoint test is complete.'

    $waypoint2 = & $copy $waypoint
    $waypoint2.id = 'questCodexWaypointTest2'
    $waypoint2.name = 'quest_codex_waypoint_test_2'
    $waypoint2.target = [pscustomobject]@{
        case = 'objective_waypoint_target'
        value = [pscustomobject]@{ objective = 'questCodexWaypointTestObjective2' }
    }
    $waypoint2.icon = '<aqua>' + [char]0x25C7 + '</aqua>'
    $waypoint2.hudVisibilityAngle = 180.0
    $waypoint2.hudForwardDistance = 5.5
    $waypoint2.hudVerticalOffset = 1.0
    $secondTextLayer = & $copy $textLayer
    $secondTextLayer.value.text = "<aqua>{icon} {target}</aqua>`n<white>{distance} m</white>`n<gray>{direction} {vertical_direction}</gray>"
    $secondTextLayer.value.offset.y = -1.25
    $waypoint2.layers = @($secondTextLayer)

    $page.entries = @($page.entries) + @(
        $activeFact2, $completedFact2, $quest2, $objective2, $assignment2, $waypoint2
    )
} else {
    $waypoint2 = @($page.entries) | Where-Object id -eq 'questCodexWaypointTest2' | Select-Object -First 1
    if ($null -ne $waypoint2) {
        if ($waypoint2.PSObject.Properties.Name -notcontains 'hudVisibilityAngle') {
            $waypoint2 | Add-Member -MemberType NoteProperty -Name hudVisibilityAngle -Value 180.0
        }
        $waypoint2.hudVisibilityAngle = 180.0
        $waypoint2.hudForwardDistance = 5.5
        $waypoint2.hudVerticalOffset = 1.0
    }
}

$json = $page | ConvertTo-Json -Depth 100 -Compress
[System.IO.File]::WriteAllText($path, $json, $utf8NoBom)
Write-Host "Updated $path (backup: $backupPath)"
