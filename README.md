这是一个面向 Android、iOS、Web、Desktop（JVM）和 Server 的 Kotlin Multiplatform 项目。

构建前提与 CI 检查见 [开发说明](DEVELOPMENT.md)。

### 功能

- 使用 OpenStreetMap Nominatim 搜索地点，并在地图上显示结果。
- 查看地点详情、收藏地点并分享地点深链接。
- 比较多种行程方案，为步行、地铁、自行车、公共交通或驾车组合的单阶段或多阶段行程导航。
- 切换地图样式，下载可见区域以供离线查看。

首页以地图为主，底部可打开**收藏**和**离线**，右上角打开**设置**。搜索需要网络；搜索失败时可以重试，也可打开收藏或长按地图选择地点。设置支持跟随系统、浅色和深色外观，以及地图样式和图层开关。

规划行程时，打开地点详情，点击**导航**，使用当前位置或在地图上手动选择起点，再选择返回的方案。开始导航前选择**预览导航**或**沉浸导航**：预览展示路线总览与阶段清单；沉浸采用阶段接力布局，突出当前路段、下一阶段和行程信息。**模式在本次导航开始后锁定，结束后下次导航可重新选择。**

沉浸导航根据当前位置匹配当前路段，让路线前进方向与屏幕纵向平行、朝向屏幕上方；转弯后对齐新路段，不随手机指南针旋转。拖动地图可查看周边，点击**回到跟随**恢复跟随，也可临时使用北向上视角。等待定位或偏离路线时会提示，并保留已确认的路线视角。路线几何和离线时刻表不等于道路转向指令或实时到站信息，请留意实际道路与现场标识。

### 离线公交与地铁数据

Android 可在没有服务器的情况下本地运行公共交通规划：

1. 请 AI 助手使用项目 skill [bailongmap-transit](.agents/skills/bailongmap-transit/SKILL.md)，提供已经核实的交通数据源或 GTFS ZIP。
2. 助手整理 GTFS，并运行项目编译器生成 `.bltransit` 包。缺失的坐标或时刻表必须补充，不能编造。
3. 在应用中打开**离线 → 离线公共交通 → 导入公共交通包**（设置内也保留入口），选择包，审阅来源、日期和数量，再点击**确认导入**。
4. 导航在本地使用已安装时刻表，包括候车时间和同站换乘。导入会替换当前交通包，应用重启后仍保留；**移除公共交通包**会让规划恢复使用已有在线提供方。

不使用 AI 助手时，可通过 Python 3.10+ 构建（建议使用虚拟环境）：

```shell
python -m pip install -r tools/transit/requirements.txt
python tools/transit/build_transit.py region-gtfs.zip region.bltransit --name "Region" --source "Data provider and license" --start 20261101 --end 20261130
```

日期应与实际数据源一致。编译器检查引用、服务日历、坐标和时间，重新映射 ID，并生成带索引、版本和 SHA-256 清单的 SQLite 数据库。应用在允许安装前检查兼容性与完整性。导入失败或取消时保留已安装数据。此流程无需 OTP 二进制、OSM PBF 文件或服务器。

Skill 现在区分“编译出文件”和“验证可用交付物”。它要求来源与许可证证据及已审阅的 GTFS 验证结果，报告源数据已填但应用省略的字段，并通过实际 Android 导入器和离线规划器运行声明的行程。完整离线交通交付还包括经过测试的 BRouter 道路分段。缺少验证的包标记为未验证；通过的样例只能证明对应行程，不能证明所有可能行程或源数据事实。无需 AI 助手也能使用的常规工具见[交付验证](DEVELOPMENT.md#交通交付验证)。

当前编译器生成版本 3 的包，应用也接受版本 1 和 2。支持公交和地铁、单一时区，覆盖最多 366 天、50,000 条站点或位置记录，以及 250,000 条相邻站连接。每站必须提供明确的到达与出发时间（支持小于 48:00:00 的跨夜时间）。保留日历例外、禁止上下客规则，以及车站、站台、入口和上车区域的层次关系。有向 pathway 使用其通行时间，或按 1.2 m/s 将步道和闸机长度换算为时间；其他 pathway 模式必须明确提供时间。有 pathway 图的车站从入口或明确可接近的站点进入，直接接驳线不能绕过该图。没有图时，同站站台换乘使用估算距离。

`frequencies.txt` 中 `exact_times=1` 会展开为独立的定时班次。对 `exact_times=0`，应用在到达上车站后预留完整班次间隔，检查服务窗口，并将结果标记为**间隔估算**，不编造固定发车相位。换乘类型 0–3，包括禁止换乘、最短时间规则和 route/trip 选择条件，遵循 GTFS 的具体性优先级；拒绝同优先级但含糊的规则。暂不支持类型 4/5（不下车续乘）、按需服务时间窗、连续上下客和多个运营机构时区，遇到这些输入会明确失败。压缩包最大 32 MiB，SQLite 数据库最大 128 MiB。

本地规划器搜索从查询时间起未来 24 小时内的上车机会（含换乘），支持跨午夜。上车后同一车辆可继续行驶超过该窗口，因此它不是行程总时长上限。定时服务返回最早到达方案，非定时班次间隔服务返回保守估算。最短换乘时间从原始下车时刻计算，包含经过中间节点的步行；未指定的换乘默认 120 秒。同一班次内继续乘坐不需要换乘余量。门到门规划沿道路网络规划接驳和离开路段，在两端分别检查步行 2 km 或骑行 5 km 内最近的两个可接近站点。这个有界搜索可能遗漏更好或更远的上车站。站内路径使用数据源提供的通行时间；无几何信息的路径及交通线路仍为示意线。

自行车 + 公共交通 + 自行车方案表示携带自有自行车，要求每个乘坐班次都具有 GTFS `bikes_allowed=1`。未知权限不等于允许。尚未实现自行车停放和共享单车交接。没有实时延误、票价或无障碍保证，也不使用 shapes。离线地图瓦片和在线地点搜索与时刻表路径规划相互独立。

未安装交通包时，仍可在构建时配置现有可选的 OpenTripPlanner GTFS GraphQL 端点：

```shell
./gradlew :app:androidApp:assembleDebug \
  -Pbailongmap.otpGraphQlUrl=https://your-otp.example/otp/gtfs/v1
```

驾车、骑行、步行和公共交通是独立选项。其他方式仍在计算时，已可用的路线会先显示；交通覆盖范围或日期不匹配在公共交通分类内显示。必须明确选择路线才开始导航。只有本地规划未找到公共交通路线时，才使用可选 OTP 端点。

### 离线道路

在**设置 → 离线道路 → 导入离线道路**中，一次选择所需的全部标准 BRouter `.rd5` 分段。可从链接的 [BRouter 分段目录](https://brouter.de/brouter/segments4/) 获取。导入会替换已安装道路区域。内嵌 BRouter 引擎在设备上计算驾车、骑行和步行路线，包括公共交通接驳。下载的地图瓦片只用于显示，不能代替路径规划数据。完整离线规划同时需要覆盖行程的道路分段和交通包。

离线道路路线不可用时，可使用同一出行方式的在线 OSRM 服务，结果标记为**在线道路规划**。驾车使用公共 OSRM 演示服务；步行和骑行使用 [FOSSGIS routing](https://routing.openstreetmap.de/about.html)，共享至少一秒的请求发起间隔。这些公共服务有使用限制，不保证可用性。鸣谢：© OpenStreetMap contributors、OSRM、FOSSGIS 和 BRouter。搜索和地图下载仍是独立网络服务。

* [/app/iosApp](./app/iosApp/iosApp) 包含 iOS 应用。即使通过 Compose Multiplatform 共享 UI，iOS 应用仍需要此入口。项目的 SwiftUI 代码也应放在这里。

* [/app/shared](./app/shared/src) 存放 Compose Multiplatform 应用间共享的代码，包含以下子目录：
  - [commonMain](./app/shared/src/commonMain/kotlin) 存放所有 target 通用的代码。
  - 其他目录中的 Kotlin 代码只针对目录名所示的平台编译。例如，Kotlin 应用的 iOS 部分调用 Apple CoreCrypto 时，应放在 [iosMain](./app/shared/src/iosMain/kotlin)；修改 Desktop（JVM）专用部分时，应放在 [jvmMain](./app/shared/src/jvmMain/kotlin)。

* [/core](./core/src) 存放项目所有 target 之间共享的代码。最重要的子目录是 [commonMain](./core/src/commonMain/kotlin)，也可以按需在平台专用目录中添加代码。

* [/server](./server/src/main/kotlin) 存放 Ktor 服务器应用。

### 运行应用

使用 IDE 工具栏运行控件提供的配置，也可以使用以下命令和选项：

- Android 应用（需要 Android 13 / API 33 或更新版本）：`./gradlew :app:androidApp:assembleDebug`。
- 桌面应用：
  - 热重载：`./gradlew :app:desktopApp:hotRun --auto`。
  - 常规运行：`./gradlew :app:desktopApp:run`。
- 服务器：`./gradlew :server:run`。
- Web 应用：
  - Wasm target（速度更快，面向现代浏览器）：`./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`。
  - JS target（速度较慢，支持旧浏览器）：`./gradlew :app:webApp:jsBrowserDevelopmentRun`。
- iOS 应用：在 Xcode 中打开 [/app/iosApp](./app/iosApp) 并运行。

### 运行测试

使用 IDE 编辑器行号栏的运行按钮，或执行 Gradle 任务：

- Android 测试：`./gradlew :app:shared:testAndroidHostTest`。
- 服务器测试：`./gradlew :server:test`。

导航端到端测试的环境配置和命令见[开发说明](DEVELOPMENT.md)。

---

进一步了解 [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)、[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform) 和 [Kotlin/Wasm](https://kotl.in/wasm/)……

欢迎在公开 Slack 频道 [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web) 反馈 Compose/Web 和 Kotlin/Wasm 的使用体验。遇到问题时，请在 [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP) 报告。
