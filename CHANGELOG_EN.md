# Changelog

[中文](CHANGELOG.md)

## 1.0.0 · November 27, 2026

[Download the release](https://github.com/BaiLanYaQWQ/CE-Botania/releases/tag/v1.0.0)

- All 103 potted plants use the vanilla flower pot collision shape. The 51 slab and 51 stair variants support their respective half-block, stacking, corner and waterlogged states.
- Three panes, two fences, two fence gates and 33 walls update when neighbours are placed or removed. Gates respond to player interaction and redstone. Existing connections are restored when saved chunks load.
- Corrected collision for the three pylons, the Alfheim portal and the Terrestrial Agglomeration Plate. The four mana pools have hollow basins on the server. All six orientations of the four bare spreaders use a centred 12×12×12-pixel cube. Vanilla clients still predict full-block collision for pools and spreaders because they use leaf carriers.
- Includes two ring slots, the Ring of Magnetization and protection from the Solegnolia. The disposable Leaves server now checks placement, connections and collision shapes.

### Installation

Use Java 25, Leaves 26.3 or a compatible Paper 26.3 server, and CraftEngine 26.9.2. Stop the server and back up the world and existing pack. Replace the old CeBotania JAR in `plugins/` with the JAR from the release ZIP. Extract the inner `CE-Botania-pack.zip` into `plugins/CraftEngine/resources/botania/` so that it directly contains `pack.yml`.

Set `misc.delay-configuration-load: true` in CraftEngine’s `config.yml`. The test server uses `block.serverside-blocks: 18000`. Allow additional capacity for other packs. Restart the server and accept the updated client resource pack.
