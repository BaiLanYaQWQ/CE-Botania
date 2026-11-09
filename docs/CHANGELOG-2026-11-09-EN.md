# Changelog · 2026-11-09

> **Project status: Early Alpha**
> Content is still being filled in. APIs and numbers may change at any time.
> Not recommended for production servers yet.

---

## A note on AI involvement

This project is **developed with heavy AI assistance**, and we want to be explicit about it:

| Area | How AI is involved |
|---|---|
| Code | Plugin source, CraftEngine config generators and asset extraction scripts are AI-written, then reviewed and debugged by a human |
| Assets | Item/block textures and models are extracted from the upstream Botania repository by AI-generated scripts |
| Recipes | Petal apothecary, mana infusion, runic altar and elven trade recipes are auto-extracted from upstream datagen |
| Docs | Both the Chinese and English docs are AI-drafted |
| Playtesting | Performed by a human on a test server, who reports back |

**All upstream assets remain the property of Vazkii and the Botania contributors.**
This project only ports the gameplay from Forge (mod) to Paper (plugin) and does not claim any
rights over the original assets.

If you spot an issue caused by AI-generated code, please open an issue.

---

## What changed in this update

### 1. Functional flowers — 20 new

Functional flowers spend mana to do work. **Without a nearby mana pool they do nothing at all**
(no free effects). Costs and intervals are aligned with the upstream source.

| Flower | Effect | Cost | Interval (ticks) |
|---|---|---|---|
| Agricarnation | Bonemeal-like growth for nearby crops | 5 | 200 |
| Fallen Kanade | Heals nearby players and passive mobs | 120 | 1 |
| Jaded Amaranthus | Plants Botania mystical flowers nearby | 100 | 30 |
| Hopperhock | Pulls dropped items into nearby containers | 3 | 5 |
| Exoflame | Keeps nearby furnaces fuelled | 300 | 2 |
| Bellethorn | Damages nearby hostile mobs | 8 | 10 |
| Dreadthorn | Damages nearby undead | 8 | 10 |
| Hyacidus | Poisons nearby hostile mobs | 20 | 10 |
| Medumone | Slows nearby hostile mobs | 8 | 10 |
| Tigerseye | Knocks nearby mobs away | 70 | 10 |
| Orechid | Turns stone into ore (8 weighted results) | 17500 | 100 |
| Orechid Ignem | Turns netherrack into nether ore | 20000 | 100 |
| Rannuncarpus | Re-plants dropped blocks nearby | 8 | 10 |
| Clayconia | Breaks sand, drops a clay ball | 80 | 5 |
| Daffomill | Blows dropped items away | 6 | 4 |
| Pollidisiac | Makes two animals enter love mode | 12 | 10 |
| Jiyuulia | Levitates nearby hostile mobs | 6 | 10 |
| Tangleberrie | Drags nearby mobs toward the flower | 6 | 10 |
| Heisei Dream | Weakens and slows nearby hostile mobs | 100 | 10 |
| Marimorphosis | Turns stone into 8 metamorphic variants | 12000 | 100 |

**Important corrections**

- **Clayconia** — upstream removes the sand block and drops a single clay ball; it does *not*
  convert sand into a clay block. Now matches upstream.
- **Jaded Amaranthus** — upstream plants Botania's own mystical flowers (which drop mystical
  petals when broken), not vanilla flowers. The previous implementation used the Bukkit registry,
  which cannot see CraftEngine custom blocks, so it degraded into oak logs. Now uses
  `CraftEngineBlocks.place()`.
- **Seeds only on farmland** — Rannuncarpus now checks the block below; wheat seeds, carrots,
  potatoes, beetroot seeds and melon/pumpkin seeds are only planted on farmland.

### 2. Mana-generating flowers

- **Endoflame now burns continuously.** Previously it consumed one item and instantly produced
  600 mana, which drained an entire chest overnight. Now follows upstream:
  ```
  burn ticks = min(32000, fuel burn time) / 2
  produces 1 mana per tick, consumes the next item only after the current one is finished
  ```
  One coal = 1600 ticks → burns for 800 ticks (40 s) → produces 800 mana.
  This makes hopper-fed automation viable.

### 3. Mana spreader

| Item | Change |
|---|---|
| Scan distance | 12 → **16 blocks** (matches upstream feel) |
| Relay | **New**: a spreader can hand mana to another spreader, enabling long chains |
| Adjacent pool | **New**: pulls mana directly from a mana pool touching any of its six faces |
| Particles | Continuous beam replaced by **one mana orb every 3 seconds**, 1 s travel, soul-fire burst on impact |

### 4. Runic altar

- **Inserted items now orbit above the altar** as `ItemDisplay` entities, updating every 2 ticks
- **Withdrawing is now sneak-gated**: bare hand shows status only, sneak + bare hand returns the last item
- **Fixed floating items remaining after a successful craft**
- **Fixed a stack-eating bug**: inserting while holding a stack consumed the whole stack; now clones and decrements by exactly 1

### 5. Petal apothecary

- Withdrawal also sneak-gated
- Same stack-eating fix

### 6. Recipes and data

- **Orechid recipe restored.** Upstream wraps it in `botania:gog_alternation`, which the extractor
  skipped. Petal apothecary recipes are now **44**.
- **Fixed lost tag data.** The generator pointed at a temp directory, so 61 tag groups became 0,
  breaking every `#botania:petals/gray`-style recipe. Fixed.
- Current totals: **219 recipes** (44 apothecary / 158 infusion / 17 runic), 61 tag groups.

### 7. Other fixes

| Issue | Cause | Fix |
|---|---|---|
| No sound when breaking blocks | `sounds` was emitted twice (duplicate keys) | Deduplicated; sounds now follow stone/wood/grass material |
| Flowers had wrong sounds | Not configured | Now uses vanilla flower `block.grass.*` |
| Wand showed no flower info | Missing logic branches | Added descriptions for all 20 functional flowers, merged into one action bar line |
| Functional flowers worked without a mana pool | Effect ran first, mana deducted after | Now checks for a pool first; no pool means no action |
| Shears dropped petals from mystical flowers | No tool check | Shears now drop the whole flower |
| Stack size conflicts | 4 items were marked unstackable but recipes output >1 | Removed from the unstackable list |
| Wand was stackable | Not configured | 110 wands and trinkets are now unstackable |

---

## Known gaps

- 5 functional flowers still missing: `spectranthemum` (item teleport), `vinculotus` (entity
  teleport blocking), `loonium` (dungeon loot), `solegnolia` (anti-magic), `bergamute` (sound dampening)
- Terrasteel line (Terrestrial Agglomeration Plate) not implemented
- Alfheim portal not implemented
- Decorative blocks (slabs/walls/fences) still use full-block collision
- Elven / Gaia / Redstone spreaders have no unique behaviour yet

---

## Test environment

```
Server:  Leaves 26.3
Plugin:  CraftEngine 26.9.2
Client:  Minecraft 26.3 (resource pack only — no mod required)
```

Temporary test plot: starting at `(100, 70, 100)`, 6 per row, 4 blocks apart.
