# 更新日志

[English](CHANGELOG_EN.md)

## 1.0.0 正式版 · 2026-11-27

[下载发行包](https://github.com/BaiLanYaQWQ/CE-Botania/releases/tag/v1.0.0)

- 103 种盆栽使用原版花盆碰撞。51 种台阶和 51 种楼梯支持半层、叠放或转角，以及对应的含水状态。
- 3 种玻璃板、2 种栅栏、2 种栅栏门和 33 种墙支持相邻连接与拆除更新。栅栏门支持手动和红石开关。已有区块加载后会恢复连接。
- 修正三种魔法水晶、精灵门和泰拉凝聚板的碰撞。四种魔力池使用上游的内凹池形，四种发射器的六个朝向使用居中的 12×12×12 主体。池和发射器保留树叶载体，原版客户端仍按整格预测碰撞。
- 包含双戒指饰品栏、磁化指环与弱磁兰保护。加入隔离 Leaves 测试服的碰撞、放置和连接回归检查。

### 部署

使用 Java 25、Leaves 26.3 或兼容的 Paper 26.3，以及 CraftEngine 26.9.2。停服并备份世界及旧包，移走旧 CeBotania JAR，将总包中的新版 JAR 放入 `plugins/`。将内层 `CE-Botania-pack.zip` 解压至 `plugins/CraftEngine/resources/botania/`，使该目录直接包含 `pack.yml`。

在 CraftEngine 的 `config.yml` 中设置 `misc.delay-configuration-load: true`。本项目测试服使用 `block.serverside-blocks: 18000`，如有其他资源包需额外预留容量。重启后接受更新的客户端资源包。
