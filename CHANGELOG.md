# Changelog

## 2.17.2 — unreleased

- **Fixed: entries created in the web editor now start with the intended default values.** The
  editor only knows defaults declared with `@Default`, so every numeric and boolean setting of a
  new entry used to start at `0`/`false` (and enums, materials and target types at their first
  value). A new `quest_codex_waypoint` was therefore invisible: its marker was projected 1 block
  from the player's eyes. Switching a waypoint layer between Text, Block and Beacon also reset its
  fields to `0`; it now resets them to the layer's real defaults. A new waypoint also starts with
  one text layer showing the distance, as the code always intended.
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
