# Changelog · 2026-11-11

> **Project status: Early Alpha**
> Previous: [Changelog 2026-11-10](CHANGELOG-2026-11-10-EN.md)

---

## Overview

This round pushed the **main progression** forward significantly:

1. **Terrasteel line fully playable** — agglomeration plate multiblock, gear stats, special tool abilities
2. **Alfheim portal** — 5×5 frame, both orientations, open/closed states, trade by dropping items in
3. **Spark system** — entity-based, installable on pools/altars/plates, circular-radius relay
4. **Runic altar flow rewritten** — matching upstream's "insert → auto-infuse → add livingrock → wand"

---

## 1. Terrasteel line

### Terrestrial Agglomeration Plate

**Structure** (derived from the official lexicon):

```
y=-59:         centre = agglomeration plate
y=-60:  livingrock  lapis    livingrock
        lapis       livingrock lapis
        livingrock  lapis    livingrock
        └─ 5 livingrock (4 corners + centre) + 4 lapis ─┘
```

**Flow**:

```
① Drop on top: manasteel ingot + mana diamond + mana pearl
② Mana must arrive via sparks (a directly adjacent pool does NOT count)
③ 500,000 mana + all three materials → right-click to craft → terrasteel ingot
```

### Gear stats (matching upstream `TERRASTEEL(2300, 9, 4, 4, 26)`)

| Item | Durability | Enchantability | Mining speed |
|---|---|---|---|
| Terra Shatterer (pickaxe) | 2300 | 26 | 9.0 |
| Terra Truncator (axe) | 2300 | 26 | 9.0 |
| Terra Blade (sword) | 2300 | 26 | — |
| Terrasteel armour | 330/480/450/390 | 26 | — |

### Special tool abilities

| Tool | Ability | Trigger |
|---|---|---|
| **Terra Shatterer** | Mines 3×3×3 at once | Sneak + break |
| **Terra Truncator** | Fells a whole tree (max 128 blocks) | Break a log |
| **Terra Blade** | Fires a beam, 9 damage within 12 blocks | Right-click |

### Recipes added

**Terrasteel ingot ↔ nugget**: crafting table decomposition (1 ingot = 9 nuggets).

---

## 2. Alfheim portal

### Structure (official 5×5 vertical plane, 11 livingwood blocks)

```
y=-56:  .   livingwood  glimmering  livingwood  .
y=-57:  livingwood  .   .   .   livingwood
y=-58:  glimmering  .   .   .   glimmering
y=-59:  livingwood  .   .   .   livingwood
y=-60:  .   livingwood  [core]  livingwood  .
```

**All four corners plus the two bottom corners are empty** — 11 livingwood blocks in total.

**Within 5 blocks you also need**:

```
2 × [mana pool + natural pylon directly on top]
(the pylon sits on the pool and draws from it — this is how upstream feeds the portal)
```

### Both orientations supported

Frames aligned **east–west** or **north–south** are both detected:

```java
int alongX = countFrameInPlane(true);    // extends along x
int alongZ = countFrameInPlane(false);   // extends along z
return Math.max(alongX, alongZ);
```

### Open / closed states

| Action | Effect |
|---|---|
| Wand right-click (closed) | Spends 200,000 mana → core brightens (activated model) + green particles fill the 3×3 |
| Wand right-click (open) | Closes: core darkens + particles stop + **mana drain stops** |
| Right-click with any item | Status only, **no trade** |

### Continuous drain

```java
// 250 mana every 10 ticks (≈500/s)
if (!open) return;              // closed = no drain
if (maintainTick % 10 != 0) return;
// only counts pools with a pylon directly above
if (drained < need) → auto-close + smoke + sound
```

200,000 mana lasts about **6.7 minutes**.

### Trading

**The only way to trade**: drop an item into the empty 3×3 interior.

```java
// scans dropped items every 10 ticks
if (portal == null || !portal.isOpen()) continue;   // no trade while closed
match elven_trade → consume 1 → eject the result + green particles
```

**All 14 trades work** (including tag-based ones):

```
manasteel_ingots (tag) → elementium_ingot
mana_diamond_gems → dragonstone
livingwood → dreamwood
mana_glass → elf_glass
mana_pearl → pixie_dust
...
```

---

## 3. Spark system

**Form**: an `item_display` entity hovering 1.35 blocks above its host.

**Hosts** (hand `botania:spark`, right-click the host):

```
mana pool / runic altar / agglomeration plate
```

**Relay rules**:

| Item | Value |
|---|---|
| Range | **circular, radius 16** |
| Direction | source (pool with mana) → sink (altar/plate that needs mana) |
| Particles | a single END_ROD beam, **only when mana actually moves** |

**Removing a spark**: breaking the host block makes the spark **drop as an item** (there is no separate "remove spark" action).

**Persistence**: `setPersistent(true)` — survives restarts.

---

## 4. Runic altar flow rewritten

**Now matches upstream's 4 steps**:

```
① Insert recipe materials (right-click the altar holding them)
② Mana infuses automatically (spreaders aimed at it / sparks)
③ Once full, right-click the altar holding livingrock
④ Wand right-click to ignite → output
```

**Key changes**:

- **No longer auto-crafts** when mana is full (it waits for the livingrock)
- **Igniting no longer pulls mana from a nearby pool** — it must use the altar's own stored mana
- Livingrock is consumed; state is persisted

**Stage messages**:

| Situation | Message |
|---|---|
| Livingrock with incomplete materials | "Materials incomplete — insert the recipe materials first" |
| Livingrock before mana is full | "Mana not full: 3000 / 5200" |
| Igniting with full mana but no livingrock | "No livingrock placed yet" |
| Everything ready | "Rune complete!" |

---

## 5. Model and collision fixes

### Three pylons (mana / natura / gaia)

**Previously used the `sugar_cane` carrier** — sugar cane is not a full block and has **no collision**.

**Switched to leaves carriers**, now full-block collision:

| Pylon | Carrier |
|---|---|
| mana_pylon | `oak_leaves[distance=2]` |
| natura_pylon | `birch_leaves[distance=4]` |
| gaia_pylon | `spruce_leaves[distance=5]` |

### Leaves carriers and transparency

**Problem**: a leaf block's `distance` property also controls **render transparency**:

| distance | Effect |
|---|---|
| 1 | **almost fully transparent** (outermost leaves) |
| 2–6 | progressive |
| 7 | fully opaque (the default for player-placed leaves) |

**Previously** `distance=1~6` with `persistent=false` → naturally generated trees had their outer ring turned transparent.

**Fix**: every carrier now uses `persistent=true` + `distance=2~6` — combinations a player cannot place and worldgen never produces.

### Alfheim portal core model

**Previously** a block model on a leaves carrier, which **does not render**.

**Now uses `entity_renderer`**:

```yaml
closed:
  entity_renderer:
    item: botania:render/alfheim_portal
open:
  entity_renderer:
    item: botania:render/alfheim_portal_activated
```

### New render items

```
render/apothecary_default_water    (apothecary with water)
render/mana_pool_full              (filled mana pool)
render/alfheim_portal              (portal core, closed)
render/alfheim_portal_activated    (portal core, open)
render/terra_plate                 (agglomeration plate)
```

### Block display names (Jade)

Added `settings.name` to the apothecary and agglomeration plate:

```yaml
name: '<lang:botania.block.apothecary_default>'
```

Jade used to show "Swamp Apothecary" because the block had no `name` and fell back to the wrong default key.

---

## 6. Other fixes

**Apothecary recipe**: material changed from `cobbled_deepslate` back to **`cobblestone`**.

**Wand getting a mana tag**: `setStored` had no type check and wrote mana PDC onto the wand. Now guarded by `isManasteel`.

**Agglomeration plate hardness**: synced to upstream `strength(3, 10)` + `SoundType.METAL`.

**Runic altar hardness**: synced to upstream (`copy(livingrock)` = 2/10).

**Spark state persistence**: `ManaPoolController` had no `saveExtra`/`loadExtra` at all, so `hasSpark` was lost on restart. Added.

---

## 7. Current state

```
600+ blocks / 1500+ items / 610+ recipes / 0 startup issues
```

**Progression**:

```
✅ wild flower → petals → apothecary → generating flowers → spreaders → pools
✅ mana infusion (158 recipes) / runic altar (17 recipes)
✅ manasteel gear → agglomeration plate → terrasteel gear (stats + abilities)
✅ Alfheim portal (structure + states + 14 trades)
✅ Spark system (3 host types + 16-block circular relay)
⚠️ Gaia Guardian (boss) — not implemented
⚠️ Some intermediate crafting chains — partially missing
```

---

## AI involvement

This project is developed with heavy AI assistance. Plugin source, CraftEngine config generators,
asset/recipe extraction scripts, and both language versions of the docs are AI-drafted, then
debugged and verified by a human on a live test server.

All upstream assets remain the property of **Vazkii and the Botania contributors**. This project
only ports the gameplay from Forge (mod) to Paper (plugin).
