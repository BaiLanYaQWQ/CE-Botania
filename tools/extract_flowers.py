#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 Botania 源码（只读）提取所有花的机制参数，产出：
  build/flowers.json   结构化数据，供 Java 插件实现参考
  build/flowers.md     中文速查表

用法： python3 tools/extract_flowers.py
原理： 用正则抽取 static final 常量、数组、getMaxMana() 返回值、ticksExisted % N 间隔，
      再用白名单表达式求值器把常量名换算成数字；语义描述（condition/effect）来自人工阅读源码，
      固定写在本脚本的 FLOWERS 表里，数值一律用常量名引用，避免硬编码漂移。

注意： /tmp/Botania-src 只读，本脚本不写源码目录。
"""

import ast
import json
import os
import re
import sys

SRC = "/tmp/Botania-src/Xplat/src/main/java/vazkii/botania/common/block/flower"
LANG = "/tmp/Botania-src/Xplat/src/main/resources/assets/botania/lang/zh_cn.json"
API = "/tmp/Botania-src/Xplat/src/main/java/vazkii/botania/api"
LOONIUM_CONF = "/tmp/Botania-src/Xplat/src/main/java/vazkii/botania/api/configdata/LooniumStructureConfiguration.java"
OUT_DIR = "/home/BaiLanYa/1/Botania/build"

# --------------------------------------------------------------------------
# 1. 源码常量抽取
# --------------------------------------------------------------------------

_SCALAR_RE = re.compile(
    r'(?:public|private|protected)?\s*static\s+final\s+'
    r'(int|double|float|long)\s+([A-Z][A-Z0-9_]*)\s*=\s*([^;]+);'
)
_ARRAY_RE = re.compile(
    r'(?:public|private|protected)?\s*static\s+final\s+'
    r'(?:int|double|float|long)\s*\[\]\s+([A-Z][A-Z0-9_]*)\s*=\s*\{(.*?)\}',
    re.S,
)
_MAXMANA_RE = re.compile(
    r'public\s+int\s+getMaxMana\s*\(\s*\)\s*\{(.*?)\n\t\}', re.S
)
_INTERVAL_RE = re.compile(r'ticksExisted\s*%\s*([A-Za-z0-9_]+)\s*==\s*0')
_COST_CALL_RE = re.compile(r'addMana\s*\(\s*-\s*([^)]+)\)')


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def parse_consts(src_text):
    """抽取 static final 标量与数组常量，值保持为源码表达式字符串。"""
    consts = {}
    for m in _ARRAY_RE.finditer(src_text):
        name, body = m.group(1), m.group(2)
        items = [x.strip() for x in body.replace("\n", " ").split(",") if x.strip()]
        if items:
            consts[name] = [x for x in items]
    for m in _SCALAR_RE.finditer(src_text):
        typ, name, expr = m.group(1), m.group(2), m.group(3).strip()
        if name in consts:
            continue
        consts[name] = expr
    return consts


_BIN_OPS = {
    ast.Add: lambda a, b: a + b,
    ast.Sub: lambda a, b: a - b,
    ast.Mult: lambda a, b: a * b,
    ast.Div: lambda a, b: a / b,
    ast.FloorDiv: lambda a, b: a // b,
    ast.Pow: lambda a, b: a ** b,
    ast.Mod: lambda a, b: a % b,
}


def eval_numeric(expr, scope, fallback=None):
    """白名单数值求值：数字、常量名、数组下标、四则运算、括号、Math.min/Math.max。

    scope 的值可以是数值表达式字符串，也可以是字符串列表（数组常量）。
    常量之间允许前向引用，用 memo + 递归栈保护，求不出来返回 fallback。
    """
    if expr is None:
        return fallback
    cache = {}

    def value_of(name, stack):
        if name in cache:
            return cache[name]
        if name in stack or name not in scope or isinstance(scope[name], list):
            return None
        val = compute(scope[name], stack | {name})
        cache[name] = val
        return val

    def compute(text, stack):
        s = text.strip()
        s = s.replace("Math.min", "min").replace("Math.max", "max")

        def repl_index(m):
            name, idx = m.group(1), m.group(2).strip()
            if name not in scope or not isinstance(scope[name], list):
                raise ValueError("bad array")
            items = scope[name]
            if idx == "length":
                return str(len(items))
            iv = compute(idx, stack)
            if iv is None:
                raise ValueError("bad index")
            item = items[int(iv)].strip()
            return "(%s)" % item

        s = re.sub(r'\b([A-Z][A-Z0-9_]*)\s*\[\s*([^\]]+?)\s*\]', repl_index, s)
        s = re.sub(
            r'\b([A-Z][A-Z0-9_]*)\.length\b',
            lambda m: str(len(scope[m.group(1)]))
            if isinstance(scope.get(m.group(1)), list) else m.group(0),
            s,
        )
        tree = ast.parse(s, mode="eval")

        def ev(node):
            if isinstance(node, ast.Expression):
                return ev(node.body)
            if isinstance(node, ast.Constant):
                if isinstance(node.value, (int, float)):
                    return node.value
                raise ValueError("bad const")
            if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
                return -ev(node.operand)
            if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.UAdd):
                return ev(node.operand)
            if isinstance(node, ast.BinOp) and type(node.op) in _BIN_OPS:
                return _BIN_OPS[type(node.op)](ev(node.left), ev(node.right))
            if isinstance(node, ast.Name):
                v = value_of(node.id, stack)
                if v is None:
                    raise ValueError("unresolved " + node.id)
                return v
            if isinstance(node, ast.Call) and getattr(node.func, "id", "") in ("min", "max"):
                args = [ev(a) for a in node.args]
                return min(args) if node.func.id == "min" else max(args)
            raise ValueError("not allowed")

        return ev(tree)

    try:
        val = compute(expr, frozenset())
    except Exception:
        return fallback
    if val is None:
        return fallback
    if isinstance(val, float):
        val = int(round(val))
    return int(val)


# --------------------------------------------------------------------------
# 2. 每朵花的语义定义（人工阅读源码后整理；数值全部用常量名引用）
# --------------------------------------------------------------------------

# 无法从常量直接算出的派生值：按源码公式手工给出，notes 里注明公式出处
EXTRA = {
    "gourmaryllis_max": 18144,   # getDigestingMana(12, 1.8) = 12*12*70*1.8
    "narslimmus_max": 19200,     # manaForSize(4) = 1200 * 2^4
    "rafflowsia_max": 638554,    # STREAK_OUTPUTS[last]
    "endoflame_per_burn": 3,     # addMana(3)，每 2 tick
    "spectrolus_max": 16000,
}

FLOWERS = [
    # ---------------- 产魔花 ----------------
    dict(
        id="endoflame", cls="EndoflameBlockEntity", cat="generating",
        file="generating/EndoflameBlockEntity.java",
        condition="附近 3 格（RANGE）内有可熔炼的物品实体；焚化一次后按该物品燃烧时间持续产魔，燃烧期间每 2 tick 产 3 魔力",
        fields={"mana_per_action": "3", "interval_ticks": "2"},
        notes="RANGE=3 的方形范围；FUEL_CAP=32000，实际 burnTime=min(FUEL_CAP, 物品燃烧时间)/2；"
              "期间 ticksExisted%2==0 时 addMana(3)；getMaxMana()=300；跳过 hasCraftingRemainingItem() 的物品，"
              "魔力发射器方块不作为燃料；存满 300 后不再拾取新燃料",
        key="每 2 tick +3 魔力；上限 300",
    ),
    dict(
        id="hydroangeas", cls="HydroangeasBlockEntity", cat="generating",
        file="generating/HydroangeasBlockEntity.java",
        condition="周围 8 个水平相邻位置有水源方块（源方块且周围水少于 2 格才被消耗）；消耗后开始燃烧计时产魔",
        fields={"mana_per_action": "1", "interval_ticks": "3"},
        notes="构造 super(..., FluidTags.WATER, 40, 1)：startBurnTime=40、manaPerTick=1；"
              "getGenerationDelay() 雨天返回 2、否则 3（雨天产魔更快）；"
              "DECAY_TIME=72000 后自行销毁并留下枯死灌木；getMaxMana()=150；getCooldownTime 恒为 0",
        key="1 魔力/3 tick（雨天 2 tick）；上限 150；72000 tick 枯死",
    ),
    dict(
        id="thermalily", cls="ThermalilyBlockEntity", cat="generating",
        file="generating/ThermalilyBlockEntity.java",
        condition="周围有岩浆源方块，消耗后 600 tick 内每 tick 产 45 魔力；结束后进入 400*cooldownStrength 冷却",
        fields={"mana_per_action": "45", "interval_ticks": "1"},
        notes="构造 super(..., FluidTags.LAVA, 600, 45)：startBurnTime=600、manaPerTick=45；"
              "COOLDOWN_TICKS_MULTIPLER=400，冷却 = 400*cooldownStrength；cooldownStrength 由 COOLDOWN_ROLL_PDF"
              "加权掷点得到，取值 1..15，默认 15；getMaxMana()=750",
        key="45 魔力/tick，持续 600 tick；冷却 400×1~15 tick；上限 750",
    ),
    dict(
        id="entropinnyum", cls="EntropinnyumBlockEntity", cat="generating",
        file="generating/EntropinnyumBlockEntity.java",
        condition="附近 12 格（RANGE）内存在引信仅剩 1 tick 的 TNT，且 TNT 所在位置没有流体；吸收后一次充满魔力",
        fields={"mana_per_action": "6500", "interval_ticks": "1"},
        notes="RANGE=12；addMana(getMaxMana()) 一次补满，getMaxMana()=6500；"
              "仅当 getMana()==0 时才吸收（源码 if (getMana() == 0)）；"
              "TNT 带 unethical 组件时只给 3 魔力（addMana(unethical ? 3 : getMaxMana())）；TNT 被 discard",
        key="吸收 TNT 一次 +6500（不道德 TNT 仅 +3）；上限 6500",
    ),
    dict(
        id="kekimurus", cls="KekimurusBlockEntity", cat="generating",
        file="generating/KekimurusBlockEntity.java",
        condition="附近 5 格（RANGE）内有蛋糕，每 80 tick 吃掉一片（BITES+1，吃完移除方块）并产魔",
        fields={"mana_per_action": "1800", "interval_ticks": "80"},
        notes="RANGE=5；局部变量 mana=1800（非常量名）；ticksExisted%80==0；"
              "仅当 getMaxMana()-getMana() >= 1800 时进食；getMaxMana()=9001",
        key="每 80 tick +1800（吃一片蛋糕）；上限 9001",
    ),
    dict(
        id="gourmaryllis", cls="GourmaryllisBlockEntity", cat="generating",
        file="generating/GourmaryllisBlockEntity.java",
        max_mana_override=18144,
        condition="附近 1 格（RANGE）内有可食用掉落物，吞下后按食物营养值产魔；连续吃同种食物收益递减，换新食物收益递增",
        fields={"mana_per_action": "70", "interval_ticks": "2"},
        notes="RANGE=1；产魔量 = getDigestingMana(foodValue, streak) = foodValue^2 * FOOD_MANA_FACTOR(70) * streakFactor；"
              "MAX_FOOD_VALUE=12；STREAK_MULTIPLIERS={0,1,1.3,1.5,1.6,1.7,1.75,1.8}；"
              "消化期间每 munchInterval=2+2*lastFoodCount tick 咀嚼一次，冷却 cooldown=getCooldown(val)=max(1,val*FOOD_COOLDOWN_FACTOR)，"
              "FOOD_COOLDOWN_FACTOR=10；getMaxMana()=MAX_MANA=getDigestingMana(12,1.8)=18144。"
              "字段 mana_per_action 填的是公式系数 FOOD_MANA_FACTOR，不是单次固定值",
        key="单次 = 营养²×70×连吃系数(1~1.8)；上限 18144",
    ),
    dict(
        id="munchdew", cls="MunchdewBlockEntity", cat="generating",
        file="generating/MunchdewBlockEntity.java",
        condition="上方 8×16 范围内有树叶且相邻有空气，每 4 tick 咬掉一片叶子产魔，之后进入 1600 tick 冷却",
        fields={"mana_per_action": "160", "interval_ticks": "4"},
        notes="RANGE=8、RANGE_Y=16；MANA_PER_LEAF=160；COOLDOWN_TICKS=1600（吃一口后停 1600 tick）；"
              "ticksExisted%4==0；仅当存量缺口 >= MANA_PER_LEAF 时进食；getMaxMana()=10000",
        key="每片叶子 +160，间隔 4 tick，之后冷却 1600 tick；上限 10000",
    ),
    dict(
        id="narslimmus", cls="NarslimmusBlockEntity", cat="generating",
        file="generating/NarslimmusBlockEntity.java",
        max_mana_override=19200,
        condition="附近 2 格（RANGE）内有自然生成的史莱姆；吞掉后按体型产魔",
        fields={"mana_per_action": "1200", "interval_ticks": "5"},
        notes="RANGE=2；manaForSize(size) = MANA_BASE * 2^size，size=min(size,4) 上限 4；"
              "MANA_BASE=1200（装 GoG 时为 MANA_BASE_GOG=MANA_BASE/4=300）；"
              "ticksExisted%5==0；只吃 isNaturalSpawned() 的自然生成史莱姆；getMaxMana()=MAX_MANA=manaForSize(4)=19200",
        key="体型 n 的史莱姆 = 1200×2^n（n≤4，最大 19200）；上限 19200",
    ),
    dict(
        id="rafflowsia", cls="RafflowsiaBlockEntity", cat="generating",
        file="generating/RafflowsiaBlockEntity.java",
        condition="附近 5 格（RANGE）内存在非自身的神奇花，每 40 tick 吞掉一朵；连续吞不同种类的花收益逐级上升",
        fields={"mana_per_action": "2000", "interval_ticks": "40"},
        notes="RANGE=5；STREAK_OUTPUTS 为 48 项递增数组（首个 2000，末个 638554），"
              "实得 = STREAK_OUTPUTS[streakLength] / lastFlowerCount；ticksExisted%40==0；"
              "门槛检查局部变量 mana=2100；getMaxMana()=STREAK_OUTPUTS[last]=638554。"
              "字段 mana_per_action 填的是连击第 0 档的基础值",
        key="连击 2000→638554（48 档，吃不同花递增）；间隔 40 tick；上限 638554",
    ),
    dict(
        id="dandelifeon", cls="DandelifeonBlockEntity", cat="generating",
        file="generating/DandelifeonBlockEntity.java",
        condition="需要红石信号；在 12 格半径的生命游戏棋盘上每 10 游戏刻演化一代，中心 3×3 内被消耗的细胞按代数产魔",
        fields={"mana_per_action": "60", "interval_ticks": "10"},
        notes="RANGE=12（棋盘直径 25）；SPEED=10（gameTime%SPEED==0 且 hasNeighborSignal）；"
              "OVERGROWN_SPEED=SPEED/2=5；MAX_MANA_GENERATIONS=100（细胞代数上限）；"
              "MANA_PER_GEN=60，产魔 = prevCell * MANA_PER_GEN；getMaxMana()=50000；"
              "中心 allowDist=1（3×3）内的活细胞会被 CONSUME 结算",
        key="每代每细胞 +60×代数（代数≤100）；10 游戏刻一代；上限 50000",
    ),
    dict(
        id="spectrolus", cls="SpectrolusBlockEntity", cat="generating",
        file="generating/SpectrolusBlockEntity.java",
        condition="按颜色顺序（从白到黑循环）吃羊毛或未剪毛的羊；只有当前期望颜色的羊毛/羊才生效",
        fields={"mana_per_action": "1200", "interval_ticks": "1"},
        notes="WOOL_GEN=1200、SHEEP_GEN=5000、BABY_SHEEP_GEN=1；RANGE=1（羊必须进入花所在方块）；"
              "每吃一次 nextColor 前进一档（DyeColor.BLACK 后回到 WHITE）；getMaxMana()=16000。"
              "无固定 tick 间隔，每 tick 检查",
        key="羊毛 +1200 / 成羊 +5000 / 幼羊 +1，颜色须匹配且按序循环；上限 16000",
    ),
    dict(
        id="shulk_me_not", cls="ShulkMeNotBlockEntity", cat="generating",
        file="generating/ShulkMeNotBlockEntity.java",
        condition="8 格半径内存在潜影贝，且其攻击目标是带漂浮效果的敌对生物；两者相邻时同时消灭并产魔",
        fields={"mana_per_action": "75000", "interval_ticks": "1"},
        notes="RADIUS=8（distToCenterSqr < RADIUS²，圆形范围）；MANA_PER_SHULKER=75000；"
              "addMana(generate) 其中 generate=getMaxMana()=MANA_PER_SHULKER=75000；"
              "仅在魔力缺口 >= generate 时才触发；无固定 tick 间隔，每 tick 检查",
        key="潜影贝+漂浮目标 +75000；上限 75000",
    ),
    dict(
        id="rosa_arcana", cls="RosaArcanaBlockEntity", cat="generating",
        file="generating/RosaArcanaBlockEntity.java",
        condition="附近 1 格（RANGE）内有玩家经验、经验球或附魔物品；每消耗 1 点经验产 50 魔力，附魔物品转为经验球",
        fields={"mana_per_action": "50", "interval_ticks": "1"},
        notes="MANA_PER_XP=50；RANGE=1；玩家需 experienceLevel>0 或 experienceProgress>0 且 onGround()，"
              "每次 giveExperiencePoints(-1) 换 addMana(50)；经验球按 orb.getValue()*MANA_PER_XP 结算；"
              "附魔书/附魔物品会被祛魔成经验球；getMaxMana()=6000",
        key="每点经验 +50；上限 6000",
    ),
    dict(
        id="fluid_generator", cls="FluidGeneratorBlockEntity", cat="generating",
        file="generating/FluidGeneratorBlockEntity.java",
        condition="抽象基类：子类传入消耗的流体标签、startBurnTime 与 manaPerTick，检测相邻流体源方块后进入燃烧计时",
        fields={},
        notes="抽象基类，本身不注册为花；hydroangeas 传 (FluidTags.WATER, 40, 1)，thermalily 传 (FluidTags.LAVA, 600, 45)；"
              "DECAY_TIME=72000；燃烧期间 ticksExisted%getGenerationDelay()==0 时 addMana(manaPerTick)；"
              "getGenerationDelay() 默认 1；OFFSETS 为 8 个水平相邻方向；getCooldownTime 由子类实现",
        key="基类：产魔=manaPerTick，间隔=getGenerationDelay()",
    ),

    # ---------------- 功能花 ----------------
    dict(
        id="agricarnation", cls="AgricarnationBlockEntity", cat="functional",
        file="functional/AgricarnationBlockEntity.java",
        effect="给范围内作物施加骨粉生长效果（50% 成功概率）",
        fields={"mana_cost": "5", "interval_ticks": "6", "radius": "5"},
        notes="MANA_COST=5；RANGE=5（迷你花 RANGE_MINI=2）；BONEMEAL_SUCCESS_CHANCE=0.5f；"
              "ticksExisted%6==0 且无红石信号；getMaxMana()=200",
        key="5 魔力/次，6 tick 一次，半径 5，骨粉成功率 50%",
    ),
    dict(
        id="bellethorn", cls="BellethornBlockEntity", cat="functional",
        file="functional/BellethornBlockEntity.java",
        effect="伤害范围内生物并施加缓慢",
        fields={"mana_cost": "24", "interval_ticks": "5", "radius": "6"},
        notes="getManaCost()=24；RANGE=6（迷你 RANGE_MINI=1）；ticksExisted%5==0；getMaxMana()=1000",
        key="24 魔力/次，5 tick 一次，半径 6",
    ),
    dict(
        id="bergamute", cls="BergamuteBlockEntity", cat="functional",
        file="functional/BergamuteBlockEntity.java",
        effect="吸收/静音范围内的声音事件",
        fields={"radius": "4"},
        notes="RANGE=4（圆形，distToCenterSqr <= RANGE²）；不需要魔力，也不是 FunctionalFlowerBlockEntity，"
              "继承 SpecialFlowerBlockEntity；getLevel().hasNeighborSignal 时 disabled",
        key="无需魔力，半径 4 的静音区",
    ),
    dict(
        id="bubbell", cls="BubbellBlockEntity", cat="functional",
        file="functional/BubbellBlockEntity.java",
        effect="把范围内水源替换为假空气方块，让实体可在水下呼吸；作用半径逐步扩张",
        fields={"mana_cost": "4", "interval_ticks": "1", "radius": "12"},
        notes="COST_PER_TICK=4，每 tick addMana(-COST_PER_TICK)；RANGE=12（迷你 RANGE_MINI=6）；"
              "每 10 tick range++ 直到 getRange()；只替换 Blocks.WATER；getMaxMana()=2000",
        key="4 魔力/tick，半径 12（每 10 tick +1），上限 2000",
    ),
    dict(
        id="clayconia", cls="ClayconiaBlockEntity", cat="functional",
        file="functional/ClayconiaBlockEntity.java",
        effect="把范围内的沙/沙砾方块转化为黏土球掉落物",
        fields={"mana_cost": "80", "interval_ticks": "5", "radius": "5"},
        notes="COST=80；RANGE=5（迷你 2）、RANGE_Y=3（迷你 1）；ticksExisted%5==0；getMaxMana()=640",
        key="80 魔力/块，5 tick 一次，半径 5×3",
    ),
    dict(
        id="daffomill", cls="DaffomillBlockEntity", cat="functional",
        file="functional/DaffomillBlockEntity.java",
        effect="朝花所朝方向吹动物品实体（每 20 tick 消耗 1 魔力）",
        fields={"mana_cost": "1", "interval_ticks": "20", "radius": "16"},
        notes="windTicks=20，每 20 tick addMana(-1)；aabbForOrientation() 的 w=2、h=3、l=16（长 16 格、宽 2、高 3）；"
              "红石信号（isRedstonePowered）时停止吹风；getMaxMana()=100",
        key="1 魔力/20 tick，吹风通道 16×2×3",
    ),
    dict(
        id="dreadthorn", cls="DreadthornBlockEntity", cat="functional",
        file="functional/DreadthornBlockEntity.java",
        effect="伤害范围内非幼年动物",
        fields={"mana_cost": "30", "interval_ticks": "5", "radius": "6"},
        notes="继承 BellethornBlockEntity，仅重写 getSelector()（Animal 且非幼年）与 getManaCost()=30；"
              "RANGE=6 与 ticksExisted%5 来自父类；getMaxMana()=1000（父类）",
        key="30 魔力/次，5 tick 一次，半径 6，只打非幼年动物",
    ),
    dict(
        id="exoflame", cls="ExoflameBlockEntity", cat="functional",
        file="functional/ExoflameBlockEntity.java",
        effect="加速范围内熔炉/高炉的燃烧与烧炼进度",
        fields={"mana_cost": "300", "interval_ticks": "2", "radius": "5"},
        notes="COST=300；RANGE=5、RANGE_Y=2；燃烧时间不足 2 时 boostBurnTime() 并扣 300 魔力，"
              "ticksExisted%2==0 时 boostCookTime()；getMaxMana()=300；getMana()<=2 时停止工作",
        key="补燃料 300 魔力/次，烧炼速度每 2 tick 推进一次，半径 5×2",
    ),
    dict(
        id="fallen_kanade", cls="FallenKanadeBlockEntity", cat="functional",
        file="functional/FallenKanadeBlockEntity.java",
        effect="给范围内玩家与宠物施加再生 II（59 tick）",
        fields={"mana_cost": "120", "interval_ticks": "1", "radius": "2"},
        notes="COST=120；RANGE=2；addEffect(MobEffects.REGENERATION, 59, 2)；"
              "已有再生效果的目标不重复施加；getMaxMana()=900",
        key="120 魔力/次，再生 II 59 tick，半径 2",
    ),
    dict(
        id="heisei_dream", cls="HeiseiDreamBlockEntity", cat="functional",
        file="functional/HeiseiDreamBlockEntity.java",
        effect="让范围内至少两只敌对生物互相攻击",
        fields={"mana_cost": "100", "interval_ticks": "1", "radius": "5"},
        notes="COST=100；RANGE=5；需要 mobs.size() > 1 才发动，每次 brainwashEntity 成功扣 100 魔力；"
              "getMaxMana()=1000",
        key="100 魔力/次，半径 5，需 ≥2 只敌对生物",
    ),
    dict(
        id="hopperhock", cls="HopperhockBlockEntity", cat="functional",
        file="functional/HopperhockBlockEntity.java",
        effect="拾取附近掉落物并放入相邻容器（可挂物品过滤器）",
        fields={"interval_ticks": "1", "radius": "6"},
        notes="getRange() = getMana()>0 ? RANGE_MANA(10) : RANGE(6)，迷你花为 2/1；"
              "有魔力时作用范围扩大到 10 格，但不消耗魔力；getMaxMana()=20；"
              "新合成注入的物品有 5 tick 延迟（spawnedByInWorldRecipe）",
        key="不耗魔力；半径 6，有魔力时 10",
    ),
    dict(
        id="hyacidus", cls="HyacidusBlockEntity", cat="functional",
        file="functional/HyacidusBlockEntity.java",
        effect="给范围内非玩家生物施加中毒（60 tick）",
        fields={"mana_cost": "20", "interval_ticks": "1", "radius": "6"},
        notes="COST=20；RANGE=6；每个目标 20 魔力，已中毒者跳过；红石信号 >0 时停止；getMaxMana()=180",
        key="20 魔力/目标，中毒 60 tick，半径 6",
    ),
    dict(
        id="jaded_amaranthus", cls="JadedAmaranthusBlockEntity", cat="functional",
        file="functional/JadedAmaranthusBlockEntity.java",
        effect="在附近方块上方催生花（骨粉式生成）",
        fields={"mana_cost": "100", "interval_ticks": "30", "radius": "4"},
        notes="COST=100；RANGE=4；ticksExisted%30==0 且 getMana()>=COST；getMaxMana()=COST=100",
        key="100 魔力/次，30 tick 一次，半径 4",
    ),
    dict(
        id="jiyuulia", cls="JiyuuliaBlockEntity", cat="functional",
        file="functional/JiyuuliaBlockEntity.java",
        effect="把范围内实体从花的位置向外推开（与缠莓花相反）",
        fields={"mana_cost": "1", "interval_ticks": "4", "radius": "8"},
        notes="继承 TangleberrieBlockEntity；RANGE=8（迷你 RANGE_MINI=3）；getMaxDistance()=0；"
              "getMotionVelocity() = -super()*2（速度取反并翻倍）；tick 继承父类，ticksExisted%4==0 时 addMana(-1)；"
              "getMaxMana()=20（父类）",
        key="1 魔力/4 tick，半径 8，向外推开",
    ),
    dict(
        id="labellia", cls="LabelliaBlockEntity", cat="functional",
        file="functional/LabelliaBlockEntity.java",
        effect="用命名牌给范围内的生物/掉落物改名",
        fields={"mana_cost": "500", "interval_ticks": "1", "radius": "2"},
        notes="COST=500；RENAME_RANGE=2（改名范围）、PICKUP_RANGE=0（拾取命名牌范围，即脚下）；"
              "红石信号不为 0 时停止；getMaxMana()=6000",
        key="500 魔力/次，改名半径 2",
    ),
    dict(
        id="loonium", cls="LooniumBlockEntity", cat="functional",
        file="functional/LooniumBlockEntity.java",
        effect="按周围结构生成带有战利品表的怪物（模拟地牢战利品）",
        fields={"mana_cost": "35000", "interval_ticks": "100", "radius": "5"},
        notes="ticksExisted%100==0 才会尝试；实际消耗取自 LooniumStructureConfiguration.manaCost，"
              "默认 DEFAULT_COST=35000（getMaxMana() 也返回它）；RANGE=5、CHECK_RANGE=9；"
              "和平难度下不工作；附近怪物数达到 maxNearbyMobs 时不再生成",
        key="100 tick 一次，35,000 魔力/次，半径 5",
    ),
    dict(
        id="marimorphosis", cls="MarimorphosisBlockEntity", cat="functional",
        file="functional/MarimorphosisBlockEntity.java",
        effect="把石头转化为当前生物群系对应的石头/矿物变种（配方类型 MARIMORPHOSIS_TYPE）",
        fields={"mana_cost": "12", "interval_ticks": "2", "radius": "8"},
        notes="继承 OrechidBlockEntity；COST=12；getDelay()=2；RANGE=8、RANGE_Y=5（迷你 2/1）；"
              "getMaxMana()=1000；重写 playSound() 为空（无音效）",
        key="12 魔力/块，2 tick 一次，半径 8×5",
    ),
    dict(
        id="medumone", cls="MedumoneBlockEntity", cat="functional",
        file="functional/MedumoneBlockEntity.java",
        effect="给范围内非玩家实体施加强烈缓慢（持续 2 tick、等级 100）",
        fields={"mana_cost": "1", "interval_ticks": "1", "radius": "6"},
        notes="RANGE=6；每个实体 addMana(-1)，魔力耗尽即中断；"
              "new MobEffectInstance(MOVEMENT_SLOWDOWN, 2, 100)；红石信号禁用；getMaxMana()=4000",
        key="1 魔力/实体/tick，缓慢 100 级，半径 6",
    ),
    dict(
        id="orechid", cls="OrechidBlockEntity", cat="functional",
        file="functional/OrechidBlockEntity.java",
        effect="把范围内的石头方块转化为矿石（配方带权重随机）",
        fields={"mana_cost": "17500", "interval_ticks": "100", "radius": "5"},
        notes="COST=17500，装 GoG 时 getCost()=COST_GOG=700；DELAY=100，装 GoG 时 getDelay()=DELAY_GOG=2；"
              "RANGE=5、RANGE_Y=3；getMaxMana()=getCost()；红石信号 >0 或 canOperate()=false 时停止；"
              "配方按 getWeight() 加权随机，成功后 addMana(-getCost())",
        key="17,500 魔力/块（GoG 700），100 tick 一次（GoG 2），半径 5×3",
    ),
    dict(
        id="orechid_ignem", cls="OrechidIgnemBlockEntity", cat="functional",
        file="functional/OrechidIgnemBlockEntity.java",
        effect="在下界把下界岩转化为下界矿石（配方类型 ORECHID_IGNEM_TYPE）",
        fields={"mana_cost": "20000", "interval_ticks": "100", "radius": "5"},
        notes="继承 OrechidBlockEntity；COST=20000；canOperate() 重写为 dimensionType().hasCeiling()（需要有顶的维度，即下界）；"
              "DELAY=100 / RANGE=5 / RANGE_Y=3 继承自父类；getMaxMana()=getCost()=20000",
        key="20,000 魔力/块，100 tick 一次，半径 5×3，仅下界类维度",
    ),
    dict(
        id="pollidisiac", cls="PollidisiacBlockEntity", cat="functional",
        file="functional/PollidisiacBlockEntity.java",
        effect="把附近掉落物喂给范围内动物（繁殖/喂养）",
        fields={"mana_cost": "12", "interval_ticks": "1", "radius": "6"},
        notes="MANA_COST=12；RANGE=6；每次成功喂养扣 12 魔力；成年动物优先，幼崽按年龄从小到大；"
              "getMaxMana()=120",
        key="12 魔力/次喂养，半径 6",
    ),
    dict(
        id="rannuncarpus", cls="RannuncarpusBlockEntity", cat="functional",
        file="functional/RannuncarpusBlockEntity.java",
        effect="拾取附近的方块类掉落物并放置到附近合适位置（可挂过滤器）",
        fields={"interval_ticks": "10", "radius": "6"},
        notes="PLACE_INTERVAL_TICKS=10；放置范围 getPlaceRange() = getMana()>0 ? RANGE_PLACE_MANA(8) : RANGE_PLACE(6)，"
              "Y 方向 RANGE_PLACE_Y=6，迷你花为 3/2 与 2；拾取范围 PICKUP_RANGE=2、PICKUP_RANGE_Y=3；"
              "不消耗魔力，有魔力仅扩大范围；getMaxMana()=20；受 rannuncarpusModBlacklist/ItemBlacklist 黑名单限制",
        key="10 tick 一次，放置半径 6（有魔力 8），不耗魔力",
    ),
    dict(
        id="solegnolia", cls="SolegnoliaBlockEntity", cat="functional",
        file="functional/SolegnoliaBlockEntity.java",
        effect="阻止范围内的物品被远程磁化/吸走（抑制其他花的吸引效果）",
        fields={"radius": "5"},
        notes="RANGE=5（圆形，迷你 RANGE_MINI=1）；getMaxMana()=1；不主动消耗魔力，"
              "通过静态 hasSolegnoliaAround(entity) 供其他逻辑查询；红石信号可关闭（acceptsRedstone=true）",
        key="无需魔力，半径 5 的反吸附区",
    ),
    dict(
        id="spectranthemum", cls="SpectranthemumBlockEntity", cat="functional",
        file="functional/SpectranthemumBlockEntity.java",
        effect="把附近掉落物传送到绑定的容器/位置",
        fields={"mana_cost": "2", "interval_ticks": "1", "radius": "2"},
        notes="BASE_COST=2；实际消耗 cost = BASE_COST * stack.getCount() * sqrt(bindPos 与物品的距离)，"
              "即按数量与距离计费；RANGE=2；含魔力的物品（findManaItem 非空）不传送；"
              "红石信号禁用；getMaxMana()=5000",
        key="消耗 = 2×数量×√距离；半径 2",
    ),
    dict(
        id="tangleberrie", cls="TangleberrieBlockEntity", cat="functional",
        file="functional/TangleberrieBlockEntity.java",
        effect="把超出 6 格的实体拉向花（限制实体活动范围）",
        fields={"mana_cost": "1", "interval_ticks": "4", "radius": "7"},
        notes="RANGE=7、MAXDISTANCE=6（超过 6 格才拉回），迷你花 RANGE_MINI=3 / MAXDISTANCE_MINI=2；"
              "ticksExisted%4==0 时 addMana(-1)；玩家与不可跨维实体被跳过；getMaxMana()=20",
        key="1 魔力/4 tick，半径 7，限制距离 6",
    ),
    dict(
        id="tigerseye", cls="TigerseyeBlockEntity", cat="functional",
        file="functional/TigerseyeBlockEntity.java",
        effect="安抚范围内的爬行者，使其不再爆炸",
        fields={"mana_cost": "70", "interval_ticks": "1", "radius": "10"},
        notes="COST=70；RANGE=10、RANGE_Y=4；每只爬行者扣 70 魔力并标记 pacified；"
              "同时把 currentFuseTime 设为 2 并清除攻击目标；getMaxMana()=1000",
        key="70 魔力/只，半径 10×4",
    ),
    dict(
        id="vinculotus", cls="VinculotusBlockEntity", cat="functional",
        file="functional/VinculotusBlockEntity.java",
        effect="拦截附近末影人的传送，把落点改到花附近",
        fields={"mana_cost": "50", "radius": "64"},
        notes="RANGE=64（pointDistanceSpace < RANGE 的球形范围）、TARGET_RANGE=1；"
              "传送拦截逻辑在静态 onEndermanTeleport()，局部变量 cost=50，魔力不足或红石信号时跳过；"
              "getMaxMana()=500",
        key="50 魔力/次拦截，半径 64",
    ),

    # ---------------- 其他 ----------------
    dict(
        id="manastar", cls="ManastarBlockEntity", cat="other",
        file="ManastarBlockEntity.java",
        effect="检测相邻魔力池的魔力变化趋势，用配色显示魔力在增加还是减少",
        fields={},
        notes="水平四方向的 ManaPool 魔力求和；每 ticksExisted%60==0 采样一次（lastMana 更新）；"
              "状态常量 NONE=0/DECREASING=1/INCREASING=2；getRadius() 返回 null（唯一没有作用范围的花），"
              "不消耗魔力",
        key="无范围、无魔力消耗，每 60 tick 采样一次魔力流向",
    ),
    dict(
        id="pure_daisy", cls="PureDaisyBlockEntity", cat="other",
        file="PureDaisyBlockEntity.java",
        effect="把周围 8 个水平相邻方块按白雏菊配方转化为其他方块（如石头→活石、水→雪等）",
        fields={},
        notes="POSITIONS 为 8 个水平相邻偏移；每 tick 轮询一个位置（positionAt 循环 0..7）；"
              "每个位置的转化时间由配方 recipe.getTime() 决定（ticksRemaining 倒计时）；"
              "不需要魔力，继承 SpecialFlowerBlockEntity",
        key="无需魔力；8 个水平相邻方块，按配方时间逐个转化",
    ),
]


# 输出字段顺序（与需求文档的格式对齐；没有的字段自动省略）
FIELD_ORDER = {
    "generating": ["id", "class", "mana_per_action", "interval_ticks", "condition", "notes"],
    "functional": ["id", "class", "mana_cost", "interval_ticks", "effect", "radius", "notes"],
    "other": ["id", "class", "effect", "notes"],
}


def find_parent_file(src_text, cls_name):
    """沿 extends 链找父类源文件（最多 3 层），返回 [(文件路径, 文本), ...]。"""
    chain = []
    seen = set()
    cur_text = src_text
    for _ in range(3):
        m = re.search(r'class\s+' + re.escape(cls_name) + r'\b[^{]*?extends\s+(\w+)', cur_text)
        if not m:
            break
        parent = m.group(1)
        if parent in seen:
            break
        seen.add(parent)
        found = None
        for sub in ("functional", "generating", ""):
            p = os.path.join(SRC, sub, parent + ".java") if sub else os.path.join(SRC, parent + ".java")
            if os.path.isfile(p):
                found = p
                break
        if not found:
            break
        chain.append(found)
        cur_text = read(found)
        cls_name = parent
    return chain


def extract_max_mana(texts):
    """从本类->父类依次找 getMaxMana 方法体，返回 (原始表达式, 是否找到)。"""
    for t in texts:
        m = _MAXMANA_RE.search(t)
        if m:
            body = re.sub(r'\breturn\b', '', m.group(1)).strip().rstrip(';').strip()
            if body:
                return body, True
    return None, False


def lang_name(lang, fid):
    for key in ("block.botania." + fid, "block.botania." + fid.replace("_", "")):
        if key in lang:
            return lang[key]
    return ""


def main():
    if not os.path.isdir(SRC):
        sys.exit("源码目录不存在: " + SRC)

    # 自检：源码目录里每个花类都应在 FLOWERS 表中登记
    on_disk = set()
    for sub in ("generating", "functional", ""):
        d = os.path.join(SRC, sub) if sub else SRC
        for fn in sorted(os.listdir(d)):
            if fn.endswith("BlockEntity.java") and os.path.isfile(os.path.join(d, fn)):
                on_disk.add(os.path.join(sub, fn) if sub else fn)
    uncovered = on_disk - {s["file"] for s in FLOWERS}
    if uncovered:
        print("!! 源码中未覆盖的类:", sorted(uncovered))

    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)
    loonium_cost = 35000
    if os.path.isfile(LOONIUM_CONF):
        m = re.search(r'DEFAULT_COST\s*=\s*(\d+)', read(LOONIUM_CONF))
        if m:
            loonium_cost = int(m.group(1))

    out = {"generating": [], "functional": [], "other": []}
    md_rows = []
    unsolved = []

    for spec in FLOWERS:
        path = os.path.join(SRC, spec["file"])
        if not os.path.isfile(path):
            sys.exit("缺少源文件: " + path)
        text = read(path)
        consts = parse_consts(text)
        parents = find_parent_file(text, spec["cls"])
        for p in parents:
            for k, v in parse_consts(read(p)).items():
                consts.setdefault(k, v)
        consts["DEFAULT_COST"] = str(loonium_cost)

        # getMaxMana 返回值：本类优先，其次沿继承链；含函数调用时用配置里的派生值兜底
        texts = [text] + [read(p) for p in parents]
        body, has_maxmana = extract_max_mana(texts)
        max_mana = None
        if body:
            body = body.replace("LooniumStructureConfiguration.DEFAULT_COST", "DEFAULT_COST")
            if body == "getCost()":
                body = "COST"  # Orechid 家族：上限即合成成本
            max_mana = eval_numeric(body, consts)
        if max_mana is None and spec.get("max_mana_override") is not None:
            max_mana = spec["max_mana_override"]

        item = {"id": spec["id"], "class": spec["cls"]}
        for field, expr in spec["fields"].items():
            val = eval_numeric(expr, consts)
            if val is None:
                unsolved.append("%s.%s (%s)" % (spec["id"], field, expr))
                item[field] = expr
            else:
                item[field] = val
        if spec["cat"] == "generating":
            item["condition"] = spec["condition"]
        elif spec["cat"] == "functional":
            item["effect"] = spec["effect"]
        else:
            item["effect"] = spec["effect"]

        notes = spec["notes"]
        if max_mana is not None:
            if "getMaxMana()" not in notes:
                notes += "；getMaxMana()=%d" % max_mana
        elif has_maxmana:
            notes += "；getMaxMana() 返回函数调用结果，无法用常量直接求值（详见源码）"
            unsolved.append("%s.getMaxMana" % spec["id"])
        item["notes"] = notes

        # 自检：notes 里手写的 getMaxMana 值应与自动求值一致
        written = re.search(r'getMaxMana\(\)\s*=\s*(\d+)', notes)
        if written and max_mana is not None and int(written.group(1)) != max_mana:
            print("!! 数值不一致 %s: notes 写 %s，源码算得 %s"
                  % (spec["id"], written.group(1), max_mana))

        # 按目标格式重排字段顺序（缺少的字段自动省略）
        order = FIELD_ORDER[spec["cat"]]
        out[spec["cat"]].append({k: item[k] for k in order if k in item})

        cn = lang_name(lang, spec["id"]) or "—"
        effect = spec.get("condition") or spec.get("effect") or ""
        md_rows.append({
            "cat": {"generating": "产魔", "functional": "功能", "other": "其他"}[spec["cat"]],
            "id": spec["id"],
            "cn": cn,
            "effect": effect,
            "key": spec["key"],
        })

    os.makedirs(OUT_DIR, exist_ok=True)
    jpath = os.path.join(OUT_DIR, "flowers.json")
    with open(jpath, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write("\n")

    mpath = os.path.join(OUT_DIR, "flowers.md")
    with open(mpath, "w", encoding="utf-8") as f:
        total = sum(len(v) for v in out.values())
        f.write("# Botania 花 速查表\n\n")
        f.write("由 `tools/extract_flowers.py` 从 Botania 源码自动抽取 + 人工核对语义生成，"
                "共 **%d** 个类（产魔 %d / 功能 %d / 其他 %d）。\n"
                "数值全部取自源码常量，详见 `build/flowers.json` 的 notes 字段。\n\n"
                % (total, len(out["generating"]), len(out["functional"]), len(out["other"])))
        for cat in ("generating", "functional", "other"):
            title = {"generating": "产魔花", "functional": "功能花", "other": "其他"}[cat]
            f.write("## %s（%d）\n\n" % (title, len(out[cat])))
            f.write("| id | 中文名 | 产魔或效果 | 关键数值 |\n|---|---|---|---|\n")
            for row in md_rows:
                if row["cat"] != {"generating": "产魔", "functional": "功能", "other": "其他"}[cat]:
                    continue
                f.write("| `%s` | %s | %s | %s |\n"
                        % (row["id"], row["cn"], row["effect"], row["key"]))
            f.write("\n")
    print("写出:", jpath)
    print("写出:", mpath)
    if unsolved:
        print("未能自动求值的项（已在 notes 说明）:")
        for u in sorted(set(unsolved)):
            print("  -", u)


if __name__ == "__main__":
    main()
