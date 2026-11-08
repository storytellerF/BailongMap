# 开发

使用 JDK 21 和 Android SDK Platform 37.1 构建项目。更新后的 Compose 依赖要求 compile SDK 37.1；最低和目标 SDK 仍分别为 33 和 37。

运行 `./gradlew check --no-daemon` 执行 CI 使用的检查。Windows 使用 `gradlew.bat` 代替 `./gradlew`。

Material3 迁移回归测试通过 Robolectric 运行，无需设备：`./gradlew :app:shared:testAndroidHostTest --tests '*MaterialMigrationTest'`。测试覆盖各地图面板初始独立隐藏状态、完全展开、关闭与重开、搜索结果选择和清空搜索词。三个地图面板共用 `rememberMapSheetState`，只允许 Hidden 和 Expanded。

调查 Gradle 兼容性警告时，运行 `./gradlew help --no-daemon --no-configuration-cache --warning-mode all -Dorg.gradle.deprecation.trace=true`（PowerShell 中将整个 `-D...` 参数加引号）。在 Gradle 9.8.1 下，AGP 9.3.1 内部调用已弃用的 `Configuration.setVisible`；Kotlin 2.4.21 的 Native 工具链在 `KotlinNativeBundleArtifactFormat` 中初始化已弃用的 `KotlinNativeBundleArtifactsTypes` 属性。这些问题分别需要在 Gradle 11 和 10 前由上游插件修复；保持警告可见，并在更新插件时复查。通用警告建议升级 Kotlin 到 2.1，但本项目已使用更新版本，因此此建议不能解决问题。

无需设备或 Appium 服务器时，可运行 `./gradlew :appiumTests:compileTestKotlin --no-daemon` 验证 Appium 测试编译。Appium Java Client 11 的设备测试要求 Appium server 3 或更新版本。

`PATH` 中有 `adb`、已连接 Android 设备且 Appium 服务器已启动时，运行 `./scripts/appium-test.sh -PappiumTags=navigation` 执行导航端到端测试。设置 `ANDROID_UDID` 时脚本使用该值，否则使用 `adb devices` 报告的第一个已连接设备。脚本先安装 debug APK，再运行测试，并向两次 Gradle 调用都转发命令行参数。安装失败立即停止，否则返回测试命令退出码。脚本不需要 AI 插件或设备锁辅助程序；AI 工具在外部管理共享设备锁。

运行 `./scripts/appium-test.sh -PappiumTags=map-ui` 执行原生地图启动和底部面板回归测试。测试验证面板初始隐藏、通过返回操作和拖动关闭设置及离线面板，以及重开地点详情。`APP_PACKAGE` 和 `APP_ACTIVITY` 可指向独立安装的测试构建；Activity 命名空间与包名不同时，使用完整限定名。对已安装构建，运行 `:appiumTests:test -PappiumTags=map-ui` 并添加 `-x :app:androidApp:installDebug`，跳过覆盖安装。

导航测试通过 ADB 注入模拟位置，选择步行—地铁—自行车三阶段方案，在换乘点推进，最后恢复真实位置。测试通过 `adb reverse` 使用本地地图样式和 OpenTripPlanner 夹具，不依赖互联网。

MapLibre Compose 使用 0.20.0。Android 应用通过 `maplibre-compose-runtime-opengl-android` 打包匹配的 OpenGL 运行时。仅有共享 API 依赖只会提供 JNI 桥接，缺少运行时会在启动时针对 `libmaplibre-native-c.so` 抛出 `UnsatisfiedLinkError`。按 [MapLibre 配置指南](https://maplibre.org/maplibre-compose/getting-started/) 保持 API 和运行时版本一致。相机命令使用 `CameraUpdate` 和 `CameraFit`，位置标记使用 `LocationIndicatorLayer`。离线 UI 观察 `OfflineStorage.state`，区分初始化加载、就绪和失败。`OfflineHost` 在注入的串行后台调度器上管理离线操作；页面释放时关闭它，并按生命周期收集状态和副作用。离线存储仅提供原生 API；共享 UI 当前针对 Android 构建。

JS、Wasm 和 Kotlin Web 工具链依赖安装共用一个并发用户数为一的 Gradle build service。Windows 上并发安装时，Yarn Classic 的网络 mutex 可能在没有生成 `build/wasm/yarn.lock` 的情况下返回成功。此 service 只串行化依赖安装；编译和测试仍可并行，lockfile 验证继续启用。内置包管理器能够可靠处理并发安装后移除此限制。

## 离线交通包

可选项目 skill 位于 `.agents/skills/bailongmap-transit`，普通编译器和应用流程无需 AI 工具。维护的 schema 位于 `tools/transit/schema.sql`，由 `build_transit.py` 使用。版本 1、2 和 3 的包都是仅包含 `manifest.json` 和 `network.sqlite` 的 ZIP 文件。SQLite application ID 为 1112298578，当前 schema user version 为 3。清单包含格式 `bailongmap-transit`、版本、数据库 SHA-256、名称、来源、时区、YYYYMMDD 格式起止日期，以及站点、线路和连接数量。日期按服务逐日展开；连接存储整数 ID 和从服务日起点算起的秒数。Route mode 为 GTFS 1（地铁）或 3（公交）；pickup/dropoff 0 表示允许，1 表示禁止。

Android 导入器限制压缩及展开字节数，验证摘要和元数据，检查 SQLite 完整性，验证受支持的 schema 和数据，并准备私有数据库。确认操作原子替换当前文件。导入或发布失败时保留旧文件。应用启动时恢复当前包并删除废弃导入文件；选择器使用 Android 文档提供方，因此无需外部存储权限。

`TransitHost` 在注入的串行后台调度器上管理导入 UI 状态和副作用。仓库文件及 SQLite 工作使用 Dispatchers.IO。依赖时间的最早到达搜索在 Dispatchers.Default 上运行，带有取消检查和有界工作预算。本地服务日起点取时区本地正午减 12 小时，遵循 GTFS 夏令时语义。合并最多前两个服务日、当天和次日的连接，形成 24 小时出发窗口，覆盖夏令时切换附近延伸的跨夜班次。乘车展开会跟随车辆经过禁止上下客站点。搜索标签在步行期间保留源班次和原始下车节点及时间，防止通过中间节点绕过限定 route/trip 的换乘规则。Pareto 标签同时保留到达时间和换乘截止时间的支配关系。输入限制见 README。

验证命令（从检出根目录运行，使用交通工具虚拟环境）：

```shell
python -m venv tools/transit/.venv
tools/transit/.venv/bin/python -m pip install -r tools/transit/requirements.txt
tools/transit/.venv/bin/python -m unittest discover -s tools/transit/tests
./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug
```

仅在环境尚不存在时创建。Windows 用 `tools/transit/.venv/Scripts/python.exe` 代替 `tools/transit/.venv/bin/python`，用 `gradlew.bat` 代替 `./gradlew`。下文交通工具也使用此环境的 Python；显式指定路径时无需激活。导入器集成夹具为合成数据，不是真实导航数据。修改编译器或测试输入后，运行 `python tools/transit/generate_test_fixture.py` 重新生成维护的二进制。其权威输入为 `tools/transit/tests/test_build_transit.py` 中的 `fixture()`。宿主测试覆盖审阅与确认、并发和取消导入；路径规划测试覆盖换乘时间、同车续乘和上车限制。Android 测试使用编译器输出，验证导入、持久化、完整性拒绝和本地换乘规划。

手动设备验证时，通过 skill 构建当前有效、许可证合规的区域包，打开 Settings → 离线公共交通，在文档选择器中选择并确认，在运营时间内规划站点间行程。重启后关闭网络，使用此前已可用的目的地重复验证。上述宿主测试无需设备或服务器。

版本 2 添加站点 `kind/parent/access`、班次 `source_trip`，以及带索引的班次间隔窗口、有向步行路径和换乘规则表。精确定时间隔由编译器展开；非定时间隔保留为窗口和模板班次，不进入定时出发查询。运行时在到达或窗口开始后预留完整班次间隔，保留模板行驶与停站偏移，并检查推导出的首站发车时间早于窗口结束。若窗口末期的上车无法容纳保守估算，可能拒绝该次上车；不承诺实时到达。选择条件优先级和歧义规则拒绝遵循 GTFS Schedule 参考。冻结的 `legacy-v1.bltransit` 夹具保留旧编译器输出用于兼容性，不能用当前编译器重新生成；`legacy-v2.bltransit` 同样如此。

合成设备包（不是真实导航数据）：

```shell
python tools/transit/generate_demo.py build/transit-demo.bltransit --date 20261102
python tools/transit/generate_demo.py build/transit-complex-demo.bltransit --date 20261102 --complex
```

使用设备当前 Asia/Shanghai 日期，或省略 `--date` 使用当天。两个包都明确标记仅供测试，覆盖该日期前一天到后七天。复杂版本包含非定时间隔地铁、定时间隔公交、车站／入口／站台层次、双向 pathway 和 240 秒最短站台换乘。已知测试路线为 39.9030,116.4074 → 39.9454,116.4074。使用测试设备模拟位置，并在 Android 文档选择器中选择 `.bltransit` 文件。测试时隔离真实应用数据。`generate_test_fixture.py` 重新生成当前基础和复杂宿主测试夹具；固定日期是有意设置的。

版本 3 添加 GTFS 班次 `bikes_allowed`（0 未知、1 允许、2 禁止），在班次间隔展开时保留。旧包权限为未知。正式门到门查询向 `LocalTransitRouter` 提供道路接驳路段；直接时刻表 API 保留带标签的直线估算用于诊断。接驳时长决定能否赶上班次，也参与到达时间优化。站内换乘使用包中的 GTFS pathway 图。

## 道路规划与行程方案

`JourneyOptionsPlanner` 以独立超时和取消机制运行各提供方，结果立即更新对应分类。即使只有一个结果，UI 也要求明确选择。新请求取消旧请求，并在发布前检查代次。`RoadJourneyPlanner` 优先使用本地 BRouter，再使用同一方式的在线 OSRM 提供方。FOSSGIS 步行与骑行请求共享至少一秒的发起间隔。可选的自有自行车搜索失败或超时时，`TransitJourneyPlanner` 保留步行结果。该可选搜索与步行并行，自请求开始起有 12 秒预算，包含自行车权限检查；不能在步行结果较慢时额外再等这段时间。携带自行车要求每一班次都明确允许，不推断停放或租赁行为。

Android 依赖 `app/shared/libs/brouter-1.7.10-ro.jar` 来自 [BRouter v1.7.10](https://github.com/abrensch/brouter/releases/tag/v1.7.10) 上游仅含路径规划功能的 Java 11 二进制。SHA-256：`598b647f2384a22a43495de7aee44f31e037062ec9c317e3bcc58cf799dbd972`。旁边的 MIT 许可证及内置 `assets/brouter` lookup 和 profile 来自同一 tag。更新时应一起更新并重复导入与路径规划测试。Profile 为 `car-fast`、`trekking` 和 `shortest`（步行）。`doRun` 在 IO 协程 worker 上执行，不启动 BRouter 的 Thread 子类。取消会请求引擎终止，并在释放数据锁前等待 worker 结束。道路操作串行执行。导入验证完整性并原子发布不可变代次；失败保留当前数据，成功替换后移除旧代次。

`tools/roads/generate_test_fixture.py` 是微型道路夹具的权威来源：完全合成的方形道路，带仅供步行的对角线和附近不连通道路，用于测试途经点重新匹配。它使用上游 map-creator 流程。重新生成要求 Java 11+，以及包含 v1.7.10 all.jar、release osmosis/protobuf JAR 和上游 `misc/profiles2/{lookups.dat,all.brf,trekking.brf,softaccess.brf}` 的外部目录：

```shell
python tools/roads/generate_test_fixture.py /path/to/upstream-build
```

生成的 `E100_N0.rd5` 是人工测试数据。`OfflineRoadsTest` 验证导入、持久化及内嵌引擎的三个 profile，包括汽车和自行车不能使用仅供步行的捷径。方案测试覆盖渐进结果、提供方隔离和取消；交通测试覆盖接驳成本和自行车权限。冻结的旧版交通夹具不得重新生成。

## 交通交付验证

`tools/transit/verify_package.py` 使用当前编译器重新编译所声明的 GTFS 来源，核对交付的 v3 清单与数据库、SQLite 完整性、预期使用日期，以及可从道路到达的站点。构建和验证使用相同 Python/SQLite 环境；编译器或数据库版本不同可能需要重建包。`feed_audit.py` 报告已有数据但被省略的列和表，包括源时间的估算标记和车辆 block 语义。这是受支持子集的审计，不是完整 GTFS 合规验证，也不验证源数据事实。正式交付使用本地 MobilityData Canonical GTFS Validator，并审阅来源证据。

```shell
python tools/transit/verify_package.py region.bltransit --source region.zip --as-of YYYYMMDD --report delivery.report.json
python tools/transit/run_acceptance.py /path/to/delivery/acceptance.json
python tools/transit/verify_package.py region.bltransit --source region.zip --as-of YYYYMMDD --acceptance /path/to/delivery/acceptance.json --require-routing --require-offline --report delivery.report.json
```

首条命令报告 `artifact_verified_only`。最后一条要求新近且匹配的验收证据；`--require-offline` 还要求使用所提供道路数据成功执行一个 `TRANSIT_WALK` 用例。仅验证时刻表时，使用 `TIMETABLE` 用例并省略该参数。报告列出实际检查的范围和方式。通过报告只能证明已声明样例。

外部验收 JSON 包含 `package`、可选 `roads` 和非空 `cases`。路径相对于该文件解析。每个用例提供 `mode`、ISO-8601 格式 `departure`、以纬度／经度数组表示的 `origin`/`destination`、`expect`（`route` 或 `unavailable`）和 `max_seconds`；正向用例还要声明精确路段 `modes`，以及可选的 `routes` 必需线路名前缀和 `min_seconds`。模式为 `TIMETABLE`、`TRANSIT_WALK`、`TRANSIT_BICYCLE`、`CAR`、`BICYCLE`、`WALK`。道路模式需要 `.rd5` 文件。为所宣传线路、方向和日历或时间窗边界使用有来源依据的用例；存在相应约束时，包含换乘和自行车限制。

`run_acceptance.py` 在子测试环境中设置 `BAILONGMAP_TRANSIT_ACCEPTANCE`，只重跑 `TransitDeliveryAcceptanceTest`。这是独立于 AI 工具的普通项目测试接口。测试导入真实文件，使用固定出发时钟恢复 repository，并调用 Android SQLite、BRouter 和本地交通规划器，不使用在线回退。成功时在规范旁写入 `<spec-stem>.result.json`，包含输入哈希、方式和用例数量。失败时删除过时证据，日志保存在外部规范旁。未设置该环境变量时，常规套件跳过此测试。报告属于宿主测试证据，不是设备导入证据。不要对同一检出目录并发运行多个 Gradle 构建。
