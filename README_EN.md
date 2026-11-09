# CeBotania — Recreating Botania on CraftEngine

> **⚠️ Early Alpha**
>
> This is a **work-in-progress** port. Content is incomplete, APIs will change, and bugs are
> expected. The core loop works — *mystical flowers → petals → petal apothecary → generating
> flowers → mana spreader → mana pool → mana infusion / runic altar* — but functional flowers,
> tools, trinkets and the elven line are still being filled in.
> **Not recommended for production servers yet.**

**Language**: [中文](README.md) | English (this page)

📌 **Latest update**: [Changelog 2026-11-09](docs/CHANGELOG-2026-11-09-EN.md) · [中文更新日志](docs/更新日志-2026-11-09.md)

---

## A note on AI involvement

This project is **developed with heavy AI assistance**. The plugin source, CraftEngine config
generators, asset/recipe extraction scripts, and both language versions of the docs are
AI-drafted, then debugged and verified by a human on a live test server.

All upstream assets (textures, models, recipe numbers) remain the property of
**Vazkii and the Botania contributors**. This project only ports the gameplay from
Forge (mod) to Paper (plugin) and claims no rights over the original assets.

---

## What works today

| Feature | Status | Notes |
|---|---|---|
| 16 mystical flower colours, world generation | ✅ | 0–3 flowers per chunk on grass |
| Breaking flowers drops petals | ✅ | 2–3 petals of the matching colour; shears drop the whole flower |
| Petal apothecary | ✅ | Fill with water, insert petals/seeds, flower drops out when the recipe matches |
| 44 petal apothecary recipes | ✅ | Extracted from upstream datagen, supports tags like `#botania:petals/lime` |
| Generating flowers | ✅ | Endoflame (continuous burn), Hydroangeas, Thermalily, Entropinnyum, Kekimurus, Gourmaryllis and more |
| Functional flowers | ✅ | 20 implemented — see the changelog for the full table |
| Mana spreader | ✅ | Directional, 16-block scan, relays to other spreaders, pulls from adjacent pools |
| Mana pool | ✅ | With/without mana appearance, can float in mid-air |
| Mana infusion | ✅ | 158 recipes, catalyst checks included |
| Runic altar | ✅ | 17 recipes, orbiting item display, auto-crafts when mana is full |
| Pure daisy | ✅ | 3×3×1 area, 40-tick delay per conversion |
| Manasteel tools & armour | ✅ | Values match upstream; mana repairs durability |
| Wand of the Forest | ✅ | Inspect mana, select flowers, rotate spreaders |
| Terrasteel / Alfheim portal | ⚠️ | Data extracted, behaviour not implemented |
| Slabs / walls / fences | ⚠️ | Still full-block collision |

---

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
