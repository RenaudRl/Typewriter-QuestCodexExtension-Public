#!/usr/bin/env python3
"""Add the documented nine-slot tracked-quests menu to a Typewriter page."""

import argparse
import json
from pathlib import Path


def custom_item(material: str) -> dict:
    return {"case": "custom_item", "value": {"components": [
        {"case": "material", "value": {"material": material}}
    ]}}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("page", type=Path)
    parser.add_argument("--main-menu-id", required=True)
    args = parser.parse_args()
    document = json.loads(args.page.read_text(encoding="utf-8"))
    entries = document["entries"]
    ids = {entry.get("id") for entry in entries}
    if "tracked_quests_menu" in ids or "tracked_quests_gui" in ids:
        raise SystemExit("Tracked menu entries already exist")

    entries.extend([
        {
            "id": "tracked_quests_gui",
            "name": "tracked_quests_gui",
            "type": "open_gui",
            "criteria": [], "modifiers": [], "triggers": [],
            "guiType": "CUSTOM", "size": "SIZE_27",
            "title": "<gold><bold>Tracked quests",
            "layoutPool": [{"case": "simple", "value": {
                "id": "tracked_quests_layout",
                "items": [{
                    # This authored item is the EMPTY status. Occupied slots are
                    # replaced at runtime with the tracked quest's configured item,
                    # name, state lore and untrack hint.
                    "item": custom_item("GRAY_STAINED_GLASS_PANE"),
                    "displayName": "<dark_gray>Empty tracking slot",
                    "lore": [],
                    "criteria": [], "allowPickup": False, "modifiers": [], "triggers": [],
                    "interactionList": [], "isGhost": False, "cooldownTicks": 0,
                    "buttonType": "TRACKED_QUEST_SLOT",
                    "buttonPrefix": "codex_button:",
                    "x": 0, "y": 1, "count": 9, "direction": "right", "gap": 1, "repeatY": 1,
                }, {
                    "item": custom_item("ARROW"),
                    "displayName": "<red>← Back",
                    "lore": ["<gray>Return to the Quest Menu"],
                    "criteria": [], "allowPickup": False, "modifiers": [], "triggers": [],
                    "interactionList": [], "isGhost": False, "cooldownTicks": 0,
                    "buttonType": "BACK",
                    "buttonPrefix": "codex_button:",
                    "x": 4, "y": 2, "count": 1, "direction": None, "gap": 1, "repeatY": 1,
                }]
            }}],
            "mainLayoutId": "tracked_quests_layout",
        },
        {
            "id": "tracked_quests_menu",
            "name": "tracked_quests_menu",
            "type": "category_menu",
            "category": "@tracked",
            "title": "<gold><bold>Tracked quests",
            "rows": 3,
            "menu": "tracked_quests_gui",
            "sortDisplay": [],
        },
    ])

    main_menu = next(entry for entry in entries if entry.get("id") == args.main_menu_id)
    simple = next(layout["value"] for layout in main_menu["layoutPool"] if layout.get("case") == "simple")
    simple["items"].append({
        "item": custom_item("COMPASS"),
        "displayName": "<gold><bold>Tracked quests",
        "lore": [
            "<gray>Currently tracked: <white>%typewriter_codex_tracked_count%/%typewriter_codex_tracked_limit%",
            "<yellow>Click to view",
        ],
        "criteria": [], "allowPickup": False, "modifiers": [], "triggers": [],
        "interactionList": [{
            "type": "LEFT_CLICK", "commands": ["codex:tracked"],
            "triggers": [], "closeMenu": False, "executeReturn": False,
        }],
        "isGhost": False, "cooldownTicks": 10,
        "x": 4, "y": 5, "count": 1, "direction": "right", "gap": 1, "repeatY": 1,
    })

    config = next((entry for entry in entries if entry.get("type") == "quest_codex"), None)
    if config:
        config["multiTrackingEnabled"] = True
        config["maxTrackedQuests"] = 9
        config.setdefault("questUntrackHint", "<yellow>Click to stop tracking</yellow>")
        config.setdefault("trackingLimitMessage", "<red>You can track at most {max} quests.</red>")

    args.page.write_text(json.dumps(document, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


if __name__ == "__main__":
    main()
