# Changelog

## 2.11 - 2026-08-29

- Rebuilt against OmniGUI (GuiAndDialogs) v0.14.

## 2.9 — 2026-08-12

- The extended-chassis promotion is carried by inheritance rather than by a literal layout id.
  A chassis whose root is named anything other than `shell_root` lost its extended chrome, so
  every control anchored in the bottom band vanished at render — and was therefore unclickable.
- The extension is built against the GUI engine sources in this build instead of a pinned release
  jar. It was frozen on v0.11 of the engine, so every engine addition after that release was
  invisible here and had to be re-implemented locally.
