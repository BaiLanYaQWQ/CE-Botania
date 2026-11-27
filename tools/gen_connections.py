"""生成玻璃板、栅栏和栅栏门的连接状态及上游外观。"""
from __future__ import annotations

from itertools import product

from gen_steps import display_rotation

CONNECTION_KINDS = {
    "bifrost_pane": "pane",
    "elf_glass_pane": "pane",
    "mana_glass_pane": "pane",
    "dreamwood_fence": "fence",
    "livingwood_fence": "fence",
    "dreamwood_fence_gate": "gate",
    "livingwood_fence_gate": "gate",
}
CONNECTION_TAGS = {
    "pane": ("botania:glass_panes",),
    "fence": ("minecraft:fences", "minecraft:wooden_fences"),
    "gate": ("minecraft:fence_gates",),
}
DIRECTIONS = ("east", "north", "south", "west")
BOOLEAN_VALUES = ("false", "true")


def connection_kind(name: str) -> str | None:
    return CONNECTION_KINDS.get(name)


def render_id(name: str, path: str) -> str:
    return f"render/connections/{name}/{path.rsplit('/', 1)[-1]}"


def connection_render_models(name: str, blockstate: dict) -> dict[str, str]:
    models = blockstate["variants"].values() if "variants" in blockstate else (
        part["apply"] for part in blockstate["multipart"]
    )
    rendered = {}
    for model in models:
        path = model["model"]
        # 木栏横梁偏离方块中心，不能整体等比放大来覆盖底模。
        rendered[render_id(name, path)] = path + "_overlay" if path.endswith("_fence_side") else path
    return rendered


def render_elements(name: str, kind: str, models: list[dict]) -> list[str]:
    lines = ["          entity_renderer:"]
    for model in models:
        lines.extend([
            "            - type: item_display",
            f"              item: botania:{render_id(name, model['model'])}",
            "              display_transform: none",
            f"              rotation: {display_rotation(model)}",
        ])
        if kind == "fence" and model["model"].endswith("_fence_post"):
            # 原版栅栏仍使用原纹理。将同形木构件稍微放大，完全覆盖自定义栅栏的底模。
            lines.append("              scale: '1.005,1.005,1.005'")
    return lines


def gate_states(blockstate: dict):
    for selector, model in blockstate["variants"].items():
        yield selector, f"minecraft:spruce_fence_gate[{selector},powered=true]", [model]


def multipart_states(kind: str, blockstate: dict):
    keys = (*DIRECTIONS, "waterlogged")
    carrier = "copper_bars" if kind == "pane" else "oak_fence"
    for values in product(BOOLEAN_VALUES, repeat=len(keys)):
        state = dict(zip(keys, values, strict=True))
        selector = ",".join(f"{key}={value}" for key, value in state.items())
        models = [
            part["apply"] for part in blockstate["multipart"]
            if all(state[key] == value for key, value in part.get("when", {}).items())
        ]
        yield selector, f"minecraft:{carrier}[{selector}]", models


def connection_states(name: str, kind: str, blockstate: dict) -> list[str]:
    lines = ["    behavior:"]
    if kind == "gate":
        lines.extend([
            "      type: fence_gate_block",
            "      can_open_with_hand: true",
            "      can_open_by_wind_charge: true",
            "      sounds:",
            "        open: minecraft:block.fence_gate.open",
            "        close: minecraft:block.fence_gate.close",
        ])
    elif kind == "fence":
        lines.extend(["      type: fence_block", "      connectable_block_tag: minecraft:wooden_fences",
                      "      can_leash: true"])
    else:
        lines.append("      type: botania:pane")

    lines.extend(["    states:", "      properties:"])
    if kind == "gate":
        lines.extend(["        facing:", "          type: horizontal_direction", "          default: north"])
        properties = ("in_wall", "open", "powered")
    else:
        properties = (*DIRECTIONS, "waterlogged")
    for prop in properties:
        lines.extend([f"        {prop}:", "          type: boolean", "          default: false"])

    lines.append("      appearances:")
    appearances = gate_states(blockstate) if kind == "gate" else multipart_states(kind, blockstate)
    selectors = []
    for selector, carrier, models in appearances:
        lines.extend([f"        '{selector}':", f'          state: "{carrier}"'])
        if kind != "fence":
            lines.append("          model: botania:block/empty")
        if kind == "pane":
            lines.append("          transparent: true")
        lines.extend(render_elements(name, kind, models))
        selectors.append(selector)
    lines.append("      variants:")
    for selector in selectors:
        if kind == "gate":
            for powered in BOOLEAN_VALUES:
                lines.extend([f"        '{selector},powered={powered}':", f"          appearance: '{selector}'"])
        else:
            lines.extend([f"        '{selector}':", f"          appearance: '{selector}'"])
            if selector.endswith("waterlogged=true"):
                lines.extend(["          settings:", "            fluid_state: water"])
    return lines
