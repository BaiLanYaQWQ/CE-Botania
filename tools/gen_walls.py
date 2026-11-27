"""Generate native wall appearances and the Botania texture overlays."""
from __future__ import annotations

from itertools import product

from gen_steps import display_rotation


WALL_TAG = "minecraft:walls"
SIDES = ("east", "north", "south", "west")
HEIGHTS = ("none", "low", "tall")
BOOLEANS = ("false", "true")


def is_wall(name: str) -> bool:
    return name.endswith("_wall")


def _render_id(name: str, model: str) -> str:
    return f"render/walls/{name}/{model.rsplit('/', 1)[-1]}"


def _multipart(blockstate: dict):
    for entry in blockstate["multipart"]:
        model = entry["apply"]
        yield entry["when"], model[0] if isinstance(model, list) else model


def wall_render_models(name: str, blockstate: dict) -> dict[str, str]:
    return {_render_id(name, model["model"]): model["model"]
            for _, model in _multipart(blockstate)}


def wall_states(name: str, blockstate: dict) -> list[str]:
    lines = ["    behavior:", "      type: botania:wall", "    states:", "      properties:"]
    for side in SIDES:
        lines.extend([
            f"        {side}:",
            "          type: string",
            "          values: [none, low, tall]",
            "          default: none",
        ])
    for flag in ("up", "waterlogged"):
        default = "true" if flag == "up" else "false"
        lines.extend([f"        {flag}:", "          type: boolean", f"          default: {default}"])
    lines.append("      appearances:")
    variants = []
    multipart = tuple(_multipart(blockstate))
    for sides in product(HEIGHTS, repeat=4):
        for up in BOOLEANS:
            wall = dict(zip(SIDES, sides, strict=True), up=up)
            pieces = [model for when, model in multipart if all(wall[key] == value for key, value in when.items())]
            for wet in BOOLEANS:
                state = ",".join(f"{key}={value}" for key, value in (*wall.items(), ("waterlogged", wet)))
                # Preserve the native blockstate model. Displays cover its texture only on Botania walls.
                lines.extend([f"        '{state}':", f'          state: "minecraft:cobblestone_wall[{state}]"'])
                if pieces:
                    lines.append("          entity_renderer:")
                for piece in pieces:
                    lines.extend([
                        "            - type: item_display",
                        f"              item: botania:{_render_id(name, piece['model'])}",
                        "              display_transform: none",
                        "              scale: 1.0125",
                        f"              rotation: {display_rotation(piece)}",
                    ])
                variants.append((state, wet))
    lines.append("      variants:")
    for state, wet in variants:
        lines.extend([f"        '{state}':", f"          appearance: '{state}'"])
        if wet == "true":
            lines.extend(["          settings:", "            fluid_state: water"])
    return lines
