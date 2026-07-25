#!/usr/bin/env python3
"""Migrate embedded category_menu layouts to referenced open_gui entries."""

import argparse
import json
import shutil
from pathlib import Path


def migrate_page(path: Path) -> int:
    document = json.loads(path.read_text(encoding="utf-8"))
    entries = document.get("entries", [])
    existing_ids = {entry.get("id") for entry in entries}
    added = []
    migrated = 0
    for entry in entries:
        if entry.get("type") != "category_menu" or "layoutPool" not in entry:
            continue
        menu_id = f"{entry['id']}_layout"
        suffix = 2
        while menu_id in existing_ids:
            menu_id = f"{entry['id']}_layout_{suffix}"
            suffix += 1
        existing_ids.add(menu_id)
        added.append({
            "id": menu_id,
            "name": f"{entry.get('name', entry['id'])}_layout",
            "type": "open_gui",
            "criteria": [],
            "modifiers": [],
            "triggers": [],
            "guiType": entry.pop("guiType", "CUSTOM"),
            "size": f"SIZE_{max(1, min(6, int(entry.get('rows', 4)))) * 9}",
            "title": entry.get("title", ""),
            "layoutPool": entry.pop("layoutPool"),
            "mainLayoutId": entry.pop("mainLayoutId", ""),
            "audio": entry.pop("audio", {}),
        })
        entry["menu"] = menu_id
        migrated += 1
    if migrated:
        entries.extend(added)
        path.write_text(json.dumps(document, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return migrated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pages", type=Path)
    parser.add_argument("--backup", type=Path, required=True)
    args = parser.parse_args()
    args.backup.mkdir(parents=True, exist_ok=False)
    total = 0
    for page in args.pages.glob("*.json"):
        shutil.copy2(page, args.backup / page.name)
        total += migrate_page(page)
    print(f"Migrated {total} category menus; backup: {args.backup}")


if __name__ == "__main__":
    main()
