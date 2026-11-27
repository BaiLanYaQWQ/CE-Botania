"""从上游模型生成 CraftEngine 台阶和楼梯的状态、载体、外观及掉落。"""
from __future__ import annotations

from math import cos, radians, sin


STEP_PROPERTIES = {
    "slab": (("type", "slab_type", "bottom"),),
    "stairs": (
        ("facing", "horizontal_direction", "north"),
        ("half", "single_block_half", "bottom"),
        ("shape", "stairs_shape", "straight"),
    ),
}
STEP_TAGS = {"slab": "minecraft:slabs", "stairs": "minecraft:stairs"}


def step_output_paths(names: set[str]) -> dict[str, str]:
    # 楼梯有 80 个状态，每 17 种楼梯拆成一份，避开 SnakeYAML 的单文件大小上限。
    stairs = (name for name in sorted(names) if step_kind(name) == "stairs")
    paths = {name: f"blocks/generated_stairs_{i // 17 + 1}.yml"
             for i, name in enumerate(stairs)}
    paths.update((name, "blocks/generated_slabs.yml") for name in names if step_kind(name) == "slab")
    return paths


def step_kind(name: str) -> str | None:
    suffix = name.rsplit("_", 1)[-1]
    return suffix if suffix in STEP_PROPERTIES else None


def model_definitions(blockstate: dict):
    for variant, choices in blockstate["variants"].items():
        # 随机材质沿用其他方块的既有规则，展示用模型选择第一项。
        yield variant, choices[0] if isinstance(choices, list) else choices


def render_id(name: str, path: str) -> str:
    basename = path.rsplit("/", 1)[-1]
    return f"render/{name}" if basename == name else f"render/steps/{name}/{basename}"


def step_render_models(name: str, blockstate: dict) -> dict[str, str]:
    return {render_id(name, model["model"]): model["model"]
            for _, model in model_definitions(blockstate)}


def display_rotation(model: dict) -> str:
    # 原版方块模型使用 Ry(-y) * Rx(-x)，物品展示实体还会额外旋转 180°。
    # Rx 只有 0° 或 180°，与补偿用的 Ry(180°) 旋转可交换。
    pitch = radians(-model.get("x", 0)) / 2
    yaw = radians((180 - model.get("y", 0)) % 360) / 2
    x, y, z, w = (cos(yaw) * sin(pitch), sin(yaw) * cos(pitch),
                  -sin(yaw) * sin(pitch), cos(yaw) * cos(pitch))
    return "[" + ", ".join(f"{n:.8f}" if abs(n) > 1e-8 else "0" for n in (x, y, z, w)) + "]"


def step_states(name: str, kind: str, blockstate: dict) -> list[str]:
    properties = (*STEP_PROPERTIES[kind], ("waterlogged", "boolean", "false"))
    lines = ["    behavior:", f"      type: {kind}_block", "    states:", "      properties:"]
    for prop, prop_type, default in properties:
        lines.extend([f"        {prop}:", f"          type: {prop_type}", f"          default: {default}"])

    lines.append("      appearances:")
    variants = []
    for variant, model in model_definitions(blockstate):
        for wet in ("false", "true"):
            state = f"{variant},waterlogged={wet}"
            # CE 将氧化切制铜映射到同外观的涂蜡铜；默认棕榈楼梯占用普通切制铜。
            lines.extend([
                f"        '{state}':",
                f'          state: "minecraft:oxidized_cut_copper_{kind}[{state}]"',
                "          model: botania:block/empty",
                "          transparent: true",
                "          entity_renderer:",
                "            type: item_display",
                f"            item: botania:{render_id(name, model['model'])}",
                "            display_transform: none",
                f"            rotation: {display_rotation(model)}",
            ])
            variants.append(state)
    lines.append("      variants:")
    for state in variants:
        lines.extend([f"        '{state}':", f"          appearance: '{state}'"])
        if state.endswith("waterlogged=true"):
            lines.extend(["          settings:", "            fluid_state: water"])
    return lines


def slab_loot(name: str) -> list[str]:
    return [
        "    loot:",
        "      pools:",
        "        - rolls: 1",
        "          entries:",
        "            - type: item",
        f"              item: botania:{name}",
        "              functions:",
        "                - type: set_count",
        "                  count: 2",
        "                  add: false",
        "                  conditions:",
        "                    - type: match_block_property",
        "                      properties:",
        "                        type: double",
        "                - type: explosion_decay",
    ]
