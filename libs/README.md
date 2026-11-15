# 本地构建依赖

第三方模组 JAR 不提交到仓库，也不会打包进本模组。请从各模组的官方发布页面取得 Minecraft 1.21.1 / NeoForge 对应版本，并按下表重命名放在本目录。

| 文件名 | 当前开发版本 / 来源 |
| --- | --- |
| `create.jar` | Create 6.0.10 |
| `sable.jar` | Sable 2.0.6 |
| `veil.jar` | Veil 4.3.2 |
| `drivebywire.jar` | DriveByWire 0.3.0 |
| `drivebysable.jar` | DriveBySable 2.0.4 |
| `simurail.jar` | Simurail，模组元数据版本 `0.1.0+mc1.21.1+f608fa8`，本地分发文件为 `createsimurailadditions-1.1.1.jar` |
| `electroenergetics.jar` | Create Electro Energetics 1.21.1-1.1.4 |
| `registrate.jar` | 从所用 Create JAR 的 `META-INF/jarjar/` 提取 Registrate |
| `flywheel.jar` | 从所用 Create JAR 提取 Flywheel 1.0.6 |
| `ponder.jar` | 从所用 Create JAR 提取 Ponder 1.0.82 |
| `sable-companion.jar` | 从所用 Sable JAR 提取 companion 1.6.0 |
| `sable-rapier.jar` | 从所用 Sable JAR 提取 rapier |

编译需要两套线缆后端的 API；游戏运行时 DriveByWire 和 DriveBySable 安装任意一个即可，两者同时安装时优先使用 DriveBySable。客户端与服务端应使用相同后端。

运行时依赖要求见 `src/main/templates/META-INF/neoforge.mods.toml`，可选兼容模组在其中标为 optional。本地文件依赖不会由 Gradle 自动下载。

在项目根目录执行 `./gradlew build` 构建，执行 `./gradlew -I tools/check-controls.gradle checkCabControls` 运行21组回归检查。需要 JDK 21。专用服务端测试工具还需将运行依赖装入各自隔离测试实例的 `mods/`，不应针对玩家存档运行。
