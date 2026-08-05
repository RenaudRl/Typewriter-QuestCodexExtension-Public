# QuestCodex Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20BTC--CORE-blue)

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

## 📜 License
Licensed under the **MIT License**.

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
