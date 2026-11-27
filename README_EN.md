# CeBotania — Recreating Botania on CraftEngine

> **⚠️ Early Alpha**
>
> This is a **work-in-progress** port. Content is incomplete, APIs will change, and bugs are
> expected. You can progress from mystical flowers through mana infusion, runes,
> terrasteel and Alfheim trading. The Gaia Guardian and some tools, trinkets and
> decorative interactions remain unfinished.
> **Not recommended for production servers yet.**

**Language**: [中文](README.md) | English (this page)

📌 **Published changelog**: [Changelog 2026-11-11](docs/CHANGELOG-2026-11-11-EN.md) · [中文更新日志](docs/更新日志-2026-11-11.md)

📚 Previous: [2026-11-10](docs/CHANGELOG-2026-11-10-EN.md) · [2026-11-09](docs/CHANGELOG-2026-11-09-EN.md)

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

> Progress from a wild flower to runes, manasteel gear, terrasteel and Alfheim trading.

| Feature | Status | Notes |
|---|---|---|
| Mystical flower worldgen | ✅ | 16 colours, scattered on grass in new chunks |
| Breaking flowers drops petals | ✅ | 2–3 petals of matching colour; shears drop the whole flower |
| Petal apothecary | ✅ | Fill with water → insert → output drops; **44 recipes**, one apothecary crafts them all |
| Generating flowers | ✅ | **10 types**; Endoflame now burns continuously (one coal = 800 mana over 40 s) |
| Functional flowers | ✅ | **20 types**, costs and intervals aligned with upstream |
| Mana spreader | ✅ | Six directions, 16-block scan, relay chains, adjacent pool extraction and four variants with distinct mana parameters |
| Mana pool | ✅ | Empty/full appearances, can float, extractable by spreaders |
| Mana infusion | ✅ | **158 recipes** |
| Runic altar | ✅ | **17 recipes**, insert ingredients, supply mana, add livingrock and finish with the Wand of the Forest |
| Pure daisy | ✅ | 3×3×1 area, 40-tick delay per conversion |
| Manasteel tools & armour | ✅ | Values match upstream, mana repairs durability |
| Wand of the Forest | ✅ | Inspect mana, show flower info, bind flowers, rotate spreaders |
| **Lexicon** | ✅ | **990+ pages, fully Chinese**, with recipes, crafting grids and implementation status |
| **Terrasteel line** | ✅ | Agglomeration plate + three tools (3×3 mine / tree felling / beam) |
| **Alfheim portal** | ✅ | 11-block livingwood frame (both orientations) + states + 14 trades |
| **Spark system** | ✅ | Installs on pools/altars/plates, 16-block radius, particles only on transfer |
| Decorative block collision | ⚠️ | Slabs/walls/fences still full-block |

**Distribution contents** include 610 block definitions, 1,356 item definitions (including render items), and 611 CraftEngine recipes. The plugin also loads 61 tag groups.

**Still missing**: Gaia Guardian (boss fight), precise decorative block collision, pixie entities
(trades currently swap directly), and the mana prism.


## Progression pipeline

```
Mystical flower --break--> petal --+--> petal apothecary (fill with water)
                                   |            |
                                   |            v
                                   |      functional / generating flowers
                                   |            |
                                   |          generate
                                   |            v
                                   |     mana spreader --> mana pool
                                   |                          |
                    +--------------+                          +--------------+
                    v                                                        v
              mana infusion                                        spark (16-block circle relay)
        manasteel / mana diamond / mana pearl                           |
                    |                                                    v
                    v                                          runic altar --> runes
        agglomeration plate --> terrasteel ingot                        |
                    |                                                    v
                    v                                          terrasteel tools & armour
              Alfheim portal --> elementium / dreamwood / elf glass
```

> **Note**: only **mystical flowers / shiny flowers / double flowers / mushrooms** drop petals.
> Vanilla flowers do **not** drop Botania petals.

## Build and distribution

Use JDK 25, Leaves 26.3 or a compatible Paper 26.3 server, and CraftEngine 26.9.2.
Minecraft 26.3 clients connect with the server resource pack.

From the repository root, run the Gradle wrapper and package the CraftEngine resource directory.

```bash
(cd plugin && ./gradlew build)
python3 -c 'from shutil import make_archive; make_archive("plugin/build/libs/CE-Botania-pack", "zip", "pack")'
```

Distribute `plugin/build/libs/CE-植物魔法-0.1.0.jar` with the matching
`plugin/build/libs/CE-Botania-pack.zip`. The local distribution archive at
`dist/ce-botania-latest/CE-Botania-0.1.0.zip` contains deployment instructions, the plugin JAR
and the pack ZIP. The alpha packs under `dist/` are historical releases.

## Installation

1. Stop the server, place the new JAR in `plugins/` and move the old CeBotania JAR out of that directory.
2. Back up and replace `plugins/CraftEngine/resources/botania/`. Extract the ZIP there, or copy the contents of `pack/` into it. The target directory should directly contain `pack.yml`, `configuration/`, `resourcepack/` and `blueprint/`.
3. In CraftEngine's `config.yml`, enable delayed configuration loading so CeBotania can register its block behaviours first.

   ```yaml
   misc:
     delay-configuration-load: true
   ```

4. Clear stale resource cache entries (`*.json` and `*.bin`) from `plugins/CraftEngine/cache/`, start the server and accept its updated resource pack. Use `/ce reload all` for pack-only changes. Restart for plugin JAR or block behaviour changes.

## Acceptance tests

Run from the repository root with an initialized Leaves 26.3 and CraftEngine 26.9.2
test server template.

```bash
python3 tools/acceptance/run.py --server-template /path/to/Server
```

The runner builds the plugin and an independent probe, then starts an isolated copy with
the current pack. It checks both Alfheim portal orientations, trade boundaries, mystical
flowers in fresh chunks, and spark transfers across chunk and distance boundaries and after
chunk reload. The server log and production JAR SHA-256 are saved under
`plugin/build/acceptance/run-*/`. These scenarios passed on an isolated Leaves server on
2026-11-27. [Acceptance runner documentation](tools/acceptance/README.md)

The distribution built from `89df10b` also passed player interaction checks. A Leaves test player received three livingwood from `/botania give livingwood 3`; the same command left the inventory empty without permission. A Minecraft 26.3 protocol client received subcommand and item ID suggestions and used the Forest Wand granted by the command to open and close portals on both axes. Each opening consumed 200,000 mana and filled nine gate cells. All nine returned to air on closing.

## Baubles (development build)

With an empty cursor, right-click the chestplate slot in your survival inventory to open the nine-slot menu. This also works with a chestplate equipped. Players can use `/baubles` or `/饰品` instead. Click a supported ring in your inventory to equip it in an empty ring slot. Click an equipped ring to take it back; it stays equipped if your inventory is full.

The two slots support the Ring of Magnetization and its greater variant. Their attraction boxes extend 6 and 16 blocks along each axis. The stronger effect applies when both rings are equipped. Sneaking suspends attraction, and dropping an item pauses it for 100 ticks. Rings persist with the player and follow `keepInventory` on death.

The Solegnolia protects a sphere extending five blocks from its center. The Petite Solegnolia protects one block. A wearer inside this area stops attracting items, and items inside are protected from rings outside. Power the flower with redstone to suspend this protection.

Craft the basic ring with one magnetizing lens and four manasteel ingots. Combine it with one terrasteel ingot in a shapeless recipe to upgrade it.

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
tools/      Python generators and the isolated server acceptance runner
docs/       Changelog and testing notes
```

A clean checkout contains the assets and translation snapshots required to rebuild the items, blocks,
categories, translations, and X-axis Alfheim gate model. Run these commands offline with Python 3.10
or later.

```bash
python3 tools/gen_content.py
python3 tools/gen_core_blocks.py
```

Add `--check` to either command to compare its generated files byte for byte without writing them.
Use `python3 tools/fetch_lang.py --update-content` when updating the upstream item and block names.

---

## Credits

- **Botania** by Vazkii and contributors — original mod, assets and balance values
- **CraftEngine** by XiaoMoMi — the plugin framework this port is built on

This project is a fan port and is not affiliated with either.
