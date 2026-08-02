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

Multiple waypoints are supported by creating multiple `quest_codex_waypoint`
entries. Each entry can reference a different tracked objective; tracking two
quests therefore produces two independent text displays. The bundled public test
page contains a second quest/objective/waypoint for this scenario.

The display entities are packet-only and are updated on the player's scheduler;
they are never persisted as server entities. A layer can be enabled per player by
using its `enabled` variable.
