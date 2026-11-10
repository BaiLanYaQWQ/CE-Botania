# CeBotania — Recreating Botania on CraftEngine

> **⚠️ Early Alpha**
>
> This is a **work-in-progress** port. Content is incomplete, APIs will change, and bugs are
> expected. The core loop works — *mystical flowers → petals → petal apothecary → generating
> flowers → mana spreader → mana pool → mana infusion / runic altar* — but functional flowers,
> tools, trinkets and the elven line are still being filled in.
> **Not recommended for production servers yet.**

**Language**: [中文](README.md) | English (this page)

📌 **Latest update**: [Changelog 2026-11-10](docs/CHANGELOG-2026-11-10-EN.md) · [中文更新日志](docs/更新日志-2026-11-10.md)

📚 Previous: [2026-11-09](docs/CHANGELOG-2026-11-09-EN.md)

---

## A note on AI involvement

This project is **developed with heavy AI assistance**. The plugin source, CraftEngine config
generators, asset/recipe extraction scripts, and both language versions of the docs are
AI-drafted, then debugged and verified by a human on a live test server.

All upstream assets (textures, models, recipe numbers) remain the property of
**Vazkii and the Botania contributors**. This project only ports the gameplay from
Forge (mod) to Paper (plugin) and claims no rights over the original assets.

---

## Where the port stands

> In one line: **the full main line is playable — from a wild flower to runes, manasteel gear and the terrasteel prerequisite.**

| Feature | Status | Notes |
|---|---|---|
| Mystical flower worldgen | ✅ | 16 colours, scattered on grass in new chunks |
| Breaking flowers drops petals | ✅ | 2–3 petals of matching colour; shears drop the whole flower |
| Petal apothecary | ✅ | Fill with water → insert → output drops; **44 recipes**, one apothecary crafts them all |
| Generating flowers | ✅ | **10 types**; Endoflame now burns continuously (one coal = 800 mana over 40 s) |
| Functional flowers | ✅ | **20 types**, costs and intervals aligned with upstream |
| Mana spreader | ✅ | Directional, 16-block scan, **relay chains**, adjacent pool extraction, one mana orb every 3 s |
| Mana pool | ✅ | Empty/full appearances, can float, extractable by spreaders |
| Mana infusion | ✅ | **158 recipes** |
| Runic altar | ✅ | **17 recipes**, inserted items orbit, auto-crafts when mana is full |
| Pure daisy | ✅ | 3×3×1 area, 40-tick delay per conversion |
| Manasteel tools & armour | ✅ | Values match upstream, mana repairs durability |
| Wand of the Forest | ✅ | Inspect mana, show flower info, bind flowers, rotate spreaders |
| **Lexicon** | ✅ | **990+ pages, fully Chinese**, with recipes, crafting grids and implementation status |
| Terrasteel / Alfheim portal | ⚠️ | Data extracted, behaviour not implemented |
| Decorative block collision | ⚠️ | Slabs/walls/fences still full-block |

**Content**: 587 blocks / 1528 items / 610 crafting recipes / 61 tag groups

**Still missing**: 5 functional flowers (teleport, immobilise, dungeon, anti-magic, mute), terrasteel,
Alfheim portal, and unique behaviours for elven / gaia / redstone spreaders.


## Requirements

```
Server:  Leaves 26.3 (or any Paper 26.3 fork)
Plugin:  CraftEngine 26.9.2
Client:  Minecraft 26.3 — resource pack only, no mod needed
Java:    25
```

---

## Installation

1. Drop `CE-植物魔法-x.x.x.jar` into `plugins/`
2. Drop the `botania` pack folder into `plugins/CraftEngine/resources/botania/`
3. Start the server once — CraftEngine generates the resource pack
4. Accept the resource pack on the client

**After editing the pack, always:**

```bash
rm -f plugins/CraftEngine/cache/*.json
cp -r pack plugins/CraftEngine/resources/botania
# then in game / console:
ce reload all
```

Restarting the server alone is **not** enough — CraftEngine caches the packed resource files.

---

## Commands

| Command | Description |
|---|---|
| `/botania selfcheck` | Print registration counts and a quick health check |
| `/botania give <item>` | Give a Botania item |
| `/botania inspect <x> <y> <z>` | Dump block entity state (mana, flower type, altar contents) |
| `/botania place <block> <x> <y> <z>` | Place a custom block |
| `/botania manaset <x> <y> <z> <amount>` | Set mana on a pool / spreader / altar |
| `/botania altaradd <x> <y> <z> <item>` | Insert one item into a runic altar |
| `/botania altarclear <x> <y> <z>` | Clear a runic altar |
| `/botania rotate <x> <y> <z>` | Rotate a mana spreader 90° |
| `/botania recipe <item>` | Show how an item is infused |

---

## Repository layout

```
pack/       CraftEngine resource pack (blocks, items, recipes, lang)
plugin/     Java plugin source (Gradle, JDK 25)
tools/      Python generators — extract assets/recipes and emit CE configs
docs/       Changelog and testing notes
```

Everything under `pack/configuration/` and `pack/resourcepack/` is **generated** by the scripts in
`tools/`. Edit the generators, not the generated YAML.

---

## Credits

- **Botania** by Vazkii and contributors — original mod, assets and balance values
- **CraftEngine** by XiaoMoMi — the plugin framework this port is built on

This project is a fan port and is not affiliated with either.
