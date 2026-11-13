# Changelog

## 2.18.0 — 2026-11-13

- **New: nested categories, with no depth limit.** Tag slots `CATEGORY_SLOT` in a category's menu and they
  list its direct sub-categories (same icon, lore and click as the main menu, `hideWhenLocked`
  respected) next to its `QUEST_SLOT` quests. Once a menu has a `CATEGORY_SLOT`, `QUEST_SLOT` lists only
  the quests the category declares itself; a menu without one is unchanged and still flattens the
  sub-categories' quests. Both marker types share one PAGE_NEXT / PAGE_PREV: a group that fits its slots
  stays on every page, only an overflowing group pages. A category without its own `category_menu`
  now opens with its nearest ancestor's, so a deep tree does not need one menu per level (a title set on
  that shared menu is shared too; leave it empty to show each category's own title). BACK from a
  sub-category returns to its parent. `CATEGORY_SLOT` in a category menu is no longer reported at startup.
- **New: default lore for quest buttons.** Turn on `questLoreDefaults.enabled` in `quest_codex` and every
  quest button shows a lore without any per-quest setup: a message for not started and completed quests,
  and the objectives the player currently sees for quests in progress (all of them when several run in
  parallel, coloured by Typewriter's `quest.objective.*` snippets). Off by
  default: an existing codex looks exactly the same after the update.
- **New: `<objectives>` token.** Write it in a `quest_assignment` or `quest_lore` lore to place the active
  objectives where you want; the automatic list is then not added a second time. Works even with the
  default lore turned off.
- **New: quest description.** Typewriter's `quest` entry has no description, so `quest_lore` gets a
  `description` field: shown at the top of the quest button in every state, above the objectives.
  Write `<description>` in a lore to place it elsewhere. Empty by default, existing entries are unchanged.
- **Fixed: a quest declared in a sub-category keeps its `quest_assignment` names, items and lore in the
  parent's menu.** A menu without `CATEGORY_SLOT` flattens the sub-categories, and those quests fell back to
  the plain quest name. The parent's own assignment still wins when both exist.
- **Fixed: a locked category no longer shows the "click" hint** (`categoryClickHint`) in its button.
- **Fixed: `hideObjectivesWhenInProgress` now hides the objectives.** The option was saved but had no effect.
- **Fixed: a quest no longer disappears from its category when a state item is left empty.** The web editor
  saves an untouched `notStartedItem`, `inProgressItem` or `completedItem` without a material, and the quest
  button was drawn as air. It now falls back to the default icon (book, clock, emerald).

## 2.17.2 — 2026-11-02

- **Fixed: entries created in the web editor now start with the intended default values.** The
  editor only knows defaults declared with `@Default`, so every numeric and boolean setting of a
  new entry used to start at `0`/`false` (and enums, materials and target types at their first
  value). A new `quest_codex_waypoint` was therefore invisible: its marker was projected 1 block
  from the player's eyes. Switching a waypoint layer between Text, Block and Beacon also reset its
  fields to `0`; it now resets them to the layer's real defaults. A new waypoint also starts with
  one text layer showing the distance, as the code always intended.
- **Fixed: a waypoint following tracked objectives now shows a quest started during the session.**
  The codex tracking copied the official tracker only the first time it saw a player, and kept that
  list across reconnections. A player first seen without an active quest therefore stayed
  untracked until the server restarted, and `tracked_objective_waypoint_target` showed nothing.
  A quest that becomes active is now tracked, as the official tracker does, and an empty tracking
  list follows the official tracker again.
- **Fixed: a block or beacon layer whose material is an item (for example `EMERALD` instead of
  `EMERALD_BLOCK`) now logs one clear warning.** It used to throw on every render, flooding the
  console with "Provided material must be a block", and the marker stayed invisible.
- **Changed: a text layer's `backgroundOpacity` is now a whole number from 0 (no background) to
  255 (opaque).** It was a byte the web editor could not edit at all, capped at 127 (half
  opacity). A page that already holds a number between 0 and 127 keeps the same look.
- **Existing pages keep their stored values.** An entry created before this version still holds
  the zeros it was saved with: recreate it, or set its values by hand.

## 2.17.1 — 2026-10-30

- **Fixed: a Category Menu whose category is `@tracked` now opens the tracked-quests menu**, where
  it used to fail with "Category '@tracked' not found".

## 2.15 — 2026-10-08

- **`typewriter.codex.open` is now declared by the extension**, with an operator-only default
  (`PermissionDefault.OP`), as the documentation states. Behaviour is unchanged for servers that
  already grant it through a permissions plugin; the node now shows up in permission plugins
  (tab completion, listing) and a node already declared elsewhere is left untouched.

## 2.14 — 2026-09-19

- **Documented a breaking change made in 0.3.0 (2026-07-16) that was never announced.** Until
  then, codex markers used their own repetition formula, where `gap` was a SPACING: the step
  applied was `gap + 1`, so `count: 3, direction: right, gap: 1` from x=2 produced x=2, 4, 6.
  Since 0.3.0 markers go through the GUI engine's shared formula, where `gap` is a STEP: the
  same marker now produces x=2, 3, 4. A menu written before 0.3.0 therefore places its markers
  one slot apart where its author expected one empty slot between each, and collides with
  whatever decoration sits in between. **To restore the former spacing, double the step:
  `gap: 1` becomes `gap: 2`.** The step semantics are the ones documented for every other
  extension, and are kept.
- Indexed markers now reset `gap` along with `count` and `repeatY`. An indexed marker occupies
  one cell; leaving `gap` at its original value described a repetition nothing plays any more,
  which menu validation reported as an orphan repetition.
- Built against OmniGUI 0.18, whose slot-overlap warning now names `gap` and recalls that it is
  a step — the cause of a collision between a repeated marker and a decoration is readable from
  the console. The pin was still on 0.16.

## 2.11 - 2026-08-29

- Rebuilt against OmniGUI (GuiAndDialogs) v0.14.

## 2.9 — 2026-08-12

- The extended-chassis promotion is carried by inheritance rather than by a literal layout id.
  A chassis whose root is named anything other than `shell_root` lost its extended chrome, so
  every control anchored in the bottom band vanished at render — and was therefore unclickable.
- The extension is built against the GUI engine sources in this build instead of a pinned release
  jar. It was frozen on v0.11 of the engine, so every engine addition after that release was
  invisible here and had to be re-implemented locally.
