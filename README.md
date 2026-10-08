# QuestCodex Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper-blue)
![Typewriter](https://img.shields.io/badge/Typewriter-0.9.0--beta--177-purple)

**QuestCodex Extension** is a quest management interface for **TypeWriter**, engineered for **BTC Studio** infrastructure. It provides players with a comprehensive codex to view and track their quest progress.

---

## 🚀 Key Features

### 📜 Quest Management
- **Interactive Codex**: A unified interface for viewing all quests.
- **Progress Tracking**: Real-time status updates (active, completed, available).

### 🗂️ Organization
- **Categorization**: Organize quests into logical categories for easy navigation.
- **Multiple Menus**: Specialized views for different quest types or regions.

---

## ⚙️ Configuration

QuestCodex Extension configuration is managed via TypeWriter's manifest system.

**Requirements**: Typewriter `0.9.0-beta-177` on Paper, the Quest extension (`typewritermc:Quest`) and the
GUI extension (`renaud:GuiAndDialogs`, [OmniGUI](https://github.com/RenaudRl/Typewriter-OmniGUIExtension)).
BlueMap is only needed for the `bluemap_icon` entry.

### 📚 Entries

| Entry | Id |
|---|---|
| Quest Codex Global Settings | `quest_codex` |
| Category Menu Configuration | `category_menu` |
| Quest Category | `quest_category` |
| Quest Assignment to Category | `quest_assignment` |
| Quest Additional Lore | `quest_lore` |
| Advancement Definition | `advancement_definition` |
| Grant Advancement (action) | `grant_advancement` |
| Quest Codex Waypoint | `quest_codex_waypoint` |
| Quest Codex Locator Bar | `quest_codex_locator_bar` |
| BlueMap Icon | `bluemap_icon` |
| Tracking artifact | `quest_codex_tracking_artifact` |
| Recovery artifact | `quest_codex_recovery_artifact` |

### Commands & permissions

| Command | Permission | Description |
|---|---|---|
| `/tw codex` | `typewriter.codex.open` | Open the main codex menu. |
| `/tw codex tracked` | `typewriter.codex.open` | Open the tracked quests menu. |
| `/tw codex <category>` | `typewriter.codex.open` | Open one category. |

The extension declares `typewriter.codex.open` itself, with an operator-only default, so it shows up in
permissions plugins such as LuckPerms. Grant it to the groups that may browse the codex. Opening a menu
from an action or a button needs no permission.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/Typewriter-QuestCodexExtension-Public.git
cd Typewriter-QuestCodexExtension-Public

# Build the project
./gradlew clean build
```

### Artifact Locations:
- `build/libs/QuestCodex-[Version].jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

---

## Documentation

Full documentation available at [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/questcodex/).

## Interaction recovery

Create one `quest_codex_recovery_artifact` entry and link it from the global
`quest_codex` entry through `recoveryArtifact`. When enabled, Quest Codex keeps
only the active Typewriter dialogue or cinematic, including the cinematic frame,
in the artifact. The snapshot expires automatically and is restored a few ticks
after the player rejoins. Persistence is asynchronous and versioned.

## Client-side waypoints

The `quest_codex_waypoint` entry provides a modular GPS display with these target
types: fixed position, locatable objective, highest-priority/first/closest tracked
objective, and entity instance. Layers can be combined in one entry:

- text display, target block display, and an optional beacon-style block display;
- HUD layers anchored to the player's camera and target-anchored layers;
- configurable text placeholders such as `{distance}`, `{direction}`, `{target}`
  and `{icon}`;
- near-target mode that places the text above the objective within a configurable
  distance.
- a configurable horizontal visibility cone (180 degrees by default), so a
  waypoint behind the player is hidden until the player turns toward it;
- near-target breathing animation only. Normal HUD tracking stays static and
  sends updates only when the position actually changes.

`icon` is a text variable on the waypoint entry. Put `{icon}` in a text layer and
set it to a MiniMessage string or a resource-pack glyph, for example
`<font:my_pack:waypoint>◆</font>`. Waypoints no longer create item display entities.

The block and beacon layers use a centered transformation pivot. Beacon rotation is
performed around the center of its footprint while keeping the beam vertical.

### Display modes

`displayMode` controls how markers are placed, and any layer may override it with
its own `mode` field:

| Mode | Behaviour |
| --- | --- |
| `HUD_LOCKED` | Default. Pinned in front of the player's camera, limited to `hudVisibilityAngle`. |
| `WORLD_DIRECTIONAL` | Projected onto a sphere of radius `projectionRadius` around the player's eyes, along the true direction of the target. Looking at a marker means looking at its destination, so no visibility cone applies. |
| `TARGET_ANCHORED` | Placed on the target itself, within `targetViewDistance`. |
| `ADAPTIVE` | `TARGET_ANCHORED` up close, easing onto the projection sphere over `adaptiveTransitionBand` blocks. |

With `constantApparentSize` enabled, a marker pulled closer than the projection
radius is scaled down so every marker reads at the same on-screen size regardless
of how far its destination is. `declutterAngle` and `declutterSpacing` stack
markers vertically when several destinations share a line of sight.

### Multiple targets

A single `quest_codex_waypoint` entry now renders several destinations at once.
The `tracked_objective_waypoint_target` selection accepts `ALL` (every tracked
locatable objective) and `ONE_PER_QUEST` (the best objective of each tracked
quest), both capped by `maxTargets`. Multi-tracking is read from QuestCodex's own
tracking service, so secondary quests are no longer ignored.

Creating several `quest_codex_waypoint` entries is still supported when different
destinations need different styling.

### Locator bar

`quest_codex_locator_bar` renders the same resolved targets as vanilla locator bar
dots (Minecraft 1.21.6+). It spawns no entity, so it pairs with a 3D waypoint
rather than replacing it.

The display entities are packet-only and are updated on the player's scheduler;
they are never persisted as server entities. A layer can be enabled per player by
using its `enabled` variable.

---

## 📜 Licence

**GNU General Public License v3.0 or later** — [LICENSE](LICENSE) — with a
**linking exception** for the Typewriter engine — [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md).

| | |
|---|---|
| You may | Run it anywhere, **including on a monetised server**. Study it, modify it, use it as a base, and redistribute it — **even for a fee**. GPLv3 §4 explicitly allows charging for a copy. |
| You must | Publish the complete corresponding source of your version under GPLv3, preserve the copyright notices, and **state that you modified it and when** (§5(a)). |
| You may not | Ship a closed-source or proprietary version, relicense under stricter terms, or strip the attribution and present this work as your own — §8 terminates your rights automatically. |
| Marks | **"Born To Craft"** and **"BTC Studio"** are **not** covered by the GPL. Fork it freely, sell your fork if you like — but **rebrand it**. |

> Reselling this code is legally allowed and practically pointless: whoever buys a
> copy from you receives, under the GPL, the right to redistribute it for free.
> That is the protection — not a clause forbidding sale, which the GPL does not
> permit us to add.

### About Typewriter

This is a **third-party extension**. It uses the public extension API of the
[Typewriter](https://github.com/gabber235/Typewriter) engine by gabber235 and
contains none of its source. Born To Craft Studio is not affiliated with or
endorsed by the Typewriter project.

The engine itself is **not** free software — its licence forbids redistributing
it. **Get it from the Typewriter project, and never redistribute it**, including
inside a fork of this repository.

Full attribution, the statement of modifications required by §5(a), and the
trademark reservation are in **[NOTICE.md](NOTICE.md)**. Read it before
redistributing.

© 2026 Born To Craft Studio.
