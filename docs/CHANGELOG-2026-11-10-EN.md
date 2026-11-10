# Changelog · 2026-11-10

> **Project status: Early Alpha**
> Previous: [Changelog 2026-11-09](CHANGELOG-2026-11-09-EN.md)

---

## Overview

This round focused on three things:

1. **Carrier conflicts** — placing vanilla blocks no longer turns them into Botania blocks
2. **Model rendering** — core blocks now render via entity display, with collision and interaction working
3. **Lexicon content** — fully Chinese, fixed recipes, restored crafting grid layouts

---

## 1. Carrier scheme overhaul (biggest change)

### Background

CraftEngine custom blocks must sit on a **vanilla block state** ("carrier"). Using glass as the carrier caused:

- Placing vanilla glass → became a mana pool
- Placing cobblestone walls → became a mana pylon
- Solid carriers caused neighbor face culling → "transparent holes"

### Final scheme

**Leaves carrier + empty model override + entity rendering**

```yaml
botania:apothecary_default:
  state:
    # ① carrier: natural leaves state (player-placed leaves are persistent=true, won't match)
    state: "minecraft:birch_leaves[distance=6,persistent=false,waterlogged=false]"
    # ② empty model: hides the leaves texture
    model: botania:block/empty
    # ③ entity rendering: model comes from item_display
    entity_renderer:
      type: item_display
      item: botania:render/apothecary_default
      display_transform: none
```

Satisfies all requirements at once:

| Requirement | Implementation |
|---|---|
| Full-block collision | Leaves is a full block |
| Invisible carrier | `model: botania:block/empty` (`elements: []`) |
| Has model | `entity_renderer` |
| Doesn't occupy common blocks | Leaves `persistent=false` only occurs from natural generation |

**All 22 core blocks use this scheme.** State allocation: 7 leaf types × 4 distances = 28 unique states.

### Spreader facing fix

The `item_display` model's default orientation is 180° off from the block's `facing`:

| Block facing | Model rotation |
|---|---|
| north | `0,180,0` |
| east | `0,90,0` |
| south | `0,0,0` |
| west | `0,270,0` |

---

## 2. Petal apothecary simplification

**Verification**: upstream apothecary recipes do **not** depend on apothecary type — recipe data only contains `botania:petal_apothecary/xxx` (the recipe type name), with no material requirement.

**One apothecary can craft all 44 flowers.**

| Item | Before | After |
|---|---|---|
| Apothecary blocks | 12 materials + water variant | **2** (`default` + water) |
| Apothecary items | 12 | **1** |
| Apothecary recipes | 12 | **1** (all output the stone version) |
| Total recipes | 621 | **610** |

Removed 11 variants: `desert` / `forest` / `fungal` / `mesa` / `mountain` / `plains` / `swamp` / `taiga` / `livingrock` / `mossy` / `deepslate`

---

## 3. Floating flowers removed

| Location | Removed |
|---|---|
| Blocks | 69 (16 colours + floating functional flowers) |
| Items | 172 |
| Lang keys | 16 |
| Recipes | 68 (including `laputa_shard` which depended on the floating flower tag) |

Block count 656 → **587**.

---

## 4. 3D models extracted from Java code

Some blocks (pylons, hourglass, bellows, brewery) define their geometry in `client/model/*.java`, while the resource pack only contains a `builtin/entity` placeholder.

Added `tools/extract_java_models.py`, which parses `CubeListBuilder.addBox()` + `texOffs()` + `PartPose.offset()` and converts them to standard models:

| Block | Cuboids extracted |
|---|---|
| `mana_pylon` | 7 |
| `natura_pylon` | 11 |
| `gaia_pylon` | 11 |
| `bellows` | 2 |
| `brewery` | 3 |
| `hourglass` | failed (different model format) |

**Critical gotcha**: UVs must be normalised to **0–16 space**, otherwise 26.1+ clients report "out-of-range UV, cannot bake" and render black/purple:

```python
uv_scale_x = 16.0 / tex_width
uv_scale_y = 16.0 / tex_height
```

600 warnings before the fix, 0 after.

---

## 5. Black/purple texture fixes

8 blocks use code rendering upstream (`parent: minecraft:builtin/entity`) with textures in `textures/model/` rather than `textures/item/`:

Teru Teru Bozu, mana pylon, natura pylon, gaia pylon, hourglass, bellows, brewery, avatar

Added the 8 missing `textures/model/` textures and switched those items to normal icon models.

---

## 6. Lexicon fixes

| Issue | Fix |
|---|---|
| Untranslated item names | 279 id → Chinese name |
| Untranslated tags | 117 + 46 + 7 (`petals/gray` → gray petal, `wool_carpets` → any wool carpet) |
| Untranslated vanilla items | 8 (`minecraft:bone` → bone) |
| **Category icons all showed as books** | 11 categories + 231 entries had `icon` fields translated to Chinese; reverted to IDs |
| Duplicate materials | 122 lines merged into `×N` |
| **Crafting grids broken** | 29 grids were wrongly merged into `×N`; expanded back to full 3×3 |
| `?` in recipes | 3 (leftover from multi-choice ingredients) |
| Missing recipe notices | 35 `(recipe data not found: xxx)` → `(not implemented in this port)` |
| Functional flower descriptions | 24 entries appended with "this port: cost and interval" |
| Unimplemented systems | 4 chapters + 18 entries marked "not implemented in this port" |

---

## 7. Recipes and acquisition

**Redstone root**: upstream is "redstone + (fern **or** grass)"; only fern existed. Added "redstone + short grass".

**Living root**: previously had **no acquisition method at all**. Implemented upstream's Garden of Glass soil-turning:

```
Empty hand + sneak + right-click dirt / grass block / podzol / mycelium / coarse dirt / rooted dirt
→ becomes a "root" block
→ breaking it drops 4 living roots
```

The `botania:root` block also had no loot table; added.

---

## 8. Spreader relay chain detection

**New behaviour**: relaying only happens when the downstream chain eventually reaches a real receiver (mana pool / runic altar). Otherwise it:

- does not transfer mana
- does not emit particles
- **blocks the ray** (does not pass through to find a pool further back)

```
A → B (nothing behind B)     → A doesn't transfer, no particles
A → B → pool                 → relays all the way, particles on A and B
A → B → C → pool             → two-stage relay
```

Recursion depth limited to 8 (loop protection).

---

## 9. Current state

```
Blocks 587 / Items 1528 / Recipes 610 / Startup issues 0
World: superflat (old world backed up as Server/world_backup_*)
```

**Implemented**: mystical flower worldgen, petals, petal apothecary (44 recipes), 10 generating flowers
(endoflame burns continuously), 20 functional flowers (costs/intervals aligned with upstream),
mana spreader (directional, 16-block scan, relay, adjacent pool extraction, particles),
mana pool (empty/full appearances, can float), 158 infusion recipes, 17 runic altar recipes,
pure daisy, manasteel tools/armour, Wand of the Forest, and a fully Chinese in-game lexicon (990+ pages).

**Not implemented**: 5 functional flowers (teleport, immobilise, dungeon, anti-magic, mute),
terrasteel line, Alfheim portal, precise decorative block collision, and unique behaviours for
elven / gaia / redstone spreaders.

---

## AI involvement

This project is developed with heavy AI assistance. Plugin source, CraftEngine config generators,
asset/recipe extraction scripts, and both language versions of the docs are AI-drafted, then
debugged and verified by a human on a live test server.

All upstream assets remain the property of **Vazkii and the Botania contributors**. This project
only ports the gameplay from Forge (mod) to Paper (plugin).
