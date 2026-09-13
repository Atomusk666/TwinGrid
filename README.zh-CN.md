# TwinGrid

为 Anbernic RG DS 设计的任天堂 DS 双屏前端。

TwinGrid 运行在原厂 Android 系统上，用适合双屏和按键的界面整理 NDS
游戏库，让 RG DS 更接近一台专用的 DS 掌机。**不需要刷机。**

[English](README.md) · [下载测试版](https://github.com/Atomusk666/TwinGrid/releases) · [反馈问题](https://github.com/Atomusk666/TwinGrid/issues/new/choose)

首个公开测试版为 **0.9.0 / code135**，在 GitHub 标记为 Pre-release。
保留原有版本号、包名和签名，不将测试版改称 Stable。

## 能做什么

- 为 RG DS 上下屏分别设计的 DS / DS Lite 风格像素界面。
- 按文件夹、类型、搜索、收藏和最近记录浏览游戏。
- 直接读取 ROM 内的 NDS banner 图标。
- 随包离线中英文资料，自动匹配类型与简介。
- 联网匹配封面、按需下载并缓存在本机。
- 在适配的环境中直接启动 DraStic 游戏。
- 支持手柄按键和触摸，可从系统设置中选择默认主页。

## 实机截图

以下均为 **code135** 实机截图，中英文各三张：首页、游戏库「全部」、游戏库「类型」。
每张图片按上屏在上、下屏在下完整拼接，原始分辨率为 640 × 960，不裁切、不缩放、不修图。

| 页面 | 中文 | English |
| --- | --- | --- |
| Home / 首页 | ![Home / 首页 — 中文](assets/screenshots/home-zh.png) | ![Home / 首页 — English](assets/screenshots/home-en.png) |
| Library — All / 游戏库 · 全部 | ![Library — All / 游戏库 · 全部 — 中文](assets/screenshots/library-all-zh.png) | ![Library — All / 游戏库 · 全部 — English](assets/screenshots/library-all-en.png) |
| Library — Types / 游戏库 · 类型 | ![Library — Types / 游戏库 · 类型 — 中文](assets/screenshots/library-genres-zh.png) | ![Library — Types / 游戏库 · 类型 — English](assets/screenshots/library-genres-en.png) |

[截图来源与校验记录](assets/screenshots/README.md)

## 适用系统

已测试目标是 **Anbernic RG DS、原厂 1.18 固件、Android 14 / API 34**。
code135 已完成覆盖安装、数据保护和中文类型页专项检查；完整核心与启动
回归来自 code134。本次没有把旧测试改写成 code135 全量复测。

**GammaOS 尚未完成兼容验证。** 同为 Android 并不保证双屏、存储访问或
DraStic 行为一致。欢迎提交测试报告；Gamma Nano 原生运行器尚未接入。
其他设备也不在已验证范围内。

## 下载与第一次使用

1. 在 [GitHub Releases](https://github.com/Atomusk666/TwinGrid/releases) 下载 `TwinGrid-0.9.0-code135.apk`。
2. 在 RG DS 上安装并打开 TwinGrid。
3. 在系统目录选择器中选择自己的 NDS 游戏文件夹。
4. 存档目录可选：需要显示存档关联状态时，再选择 DraStic 的存档文件夹。
5. 等待扫描和自动整理；进入游戏库后即可浏览。
6. 如需直启，先完成 DraStic 自身的初次配置，确认它能手动打开同一个游戏。

TwinGrid **不附带 ROM、BIOS 或 DraStic**。离线资料已经放在 APK 内，
不需要额外下载资料包。

官方 APK 保留 `com.rgds.ultimate.shell` 包名和现有项目证书。使用相同证书
签名的旧测试版可覆盖安装，保留应用数据和目录授权。遇到签名不兼容时，
请先核对安装包来源，**不要先卸载旧版**；卸载可能丢失设置和 SAF 授权。
开发者用自己密钥签名的包无法覆盖官方版。

## 游戏库与自动整理

“文件夹”显示实际目录；“类型”按资料分类，两者可以同时使用。
搜索支持名称、别名和拼音检索，收藏与最近记录用于快速返回游戏。
自动整理会关联资料与封面，不会为了分类改名、移动或删除 ROM。

离线目录有 7,701 条发行记录、3,417 个作品，其中 1,360 个作品有编辑整理的
中英文文字，仍有 2,057 个作品未达到完整双语覆盖。汉化版、修改版和名称
特殊的文件可能需要手动核对；资料经过编辑自审，不等于逐条实玩验证。

可选存档目录仅用于按 `.dsv` 文件名关联，读取是否存在、大小和修改时间。
TwinGrid 不编辑存档正文、不转换即时存档；游戏运行时 DraStic 自己可能写入
存档。更换游戏前请先在游戏内保存，未保存的进度不会由前端自动保留。

## 封面怎么来

封面从固定版本的 libretro-thumbnails 索引按需获取，当前使用 JSDMirror，
失败时尝试 jsDelivr。两条线路共享同一上游图库，不是两个独立图库，也不保证
所有地区或网络始终可用。下载的游戏封面缓存在本机，不作为独立资源随仓库或
APK 打包；文档截图中可见示例游戏库内的封面。

## DraStic 直启与限制

TwinGrid 不附带 DraStic。当前实测的是原厂固件上的特定 Android DraStic APK，
具体身份见[兼容说明](docs/COMPATIBILITY.md)。

不同版本可能正常载入，也可能需要一次手动设置、只能打开模拟器菜单，
或者不符合当前适配器要求。满足基本条件的未知 Android 版本可能出现试用
确认，但这不代表已验证兼容。启动时可能重建模拟器任务；请先保存游戏。

“已发送启动请求”“模拟器在前台”和“目标游戏确实载入”是不同结果。
只打开 DraStic 而未载入游戏时，请提交兼容报告，说明：TwinGrid 版本、固件、
DraStic 版本、原厂内置还是自行安装、ROM 位于 SD 还是内置存储、同一文件
能否在 DraStic 手动打开、影响所有还是部分游戏，以及复现步骤。

## 隐私与数据安全

无需 TwinGrid 账号，也没有项目自建的遥测后端。离线浏览使用本地资料；
联网封面与在线资料查询会访问第三方服务，对方可见网络 IP 和请求内容。
本地诊断可能包含目录、游戏标识等信息，导出后请检查、脱敏再分享。
**请勿在 Issue 上传 ROM、存档、模拟器安装包、密钥或完整设备日志。**

[常见问题](docs/FAQ.md) · [公开发布审计](docs/PUBLIC_RELEASE_AUDIT.md) · [安全反馈](SECURITY.md)

## 构建与参与

Java 源码在 `app/src`，资源在 `app/res`，固定依赖在 `app/libs`，离线目录登记
在 `catalog`。项目使用 PowerShell 构建，未使用 Gradle。

准备 PowerShell 7、JDK 17、Python 3、Android SDK platform 34 和 build-tools
36.0.0，设置 `JAVA_HOME`、`ANDROID_SDK_ROOT` 后执行：

```powershell
pwsh scripts/build_v02.ps1 -VersionCode 135 -VersionName 0.9.0
```

公共构建默认生成未签名 APK，无需持有维护者私钥。签名及完整步骤见
[BUILDING](docs/BUILDING.md)。问题、兼容报告和功能建议可使用仓库的 Issue 模板；
贡献前请阅读 [CONTRIBUTING](CONTRIBUTING.md)。

TwinGrid 自有源码、原创 UI 与文档使用 [MIT](LICENSE)，第三方字体、依赖及
资料分别遵循[各自许可](THIRD_PARTY_NOTICES.md)，不统一改成 MIT。

TwinGrid 的开发阶段曾使用 RGDS Shell 这个名称，Android 包名为升级兼容而保留。
本项目与 Nintendo、Anbernic、DraStic 无隶属或官方授权背书关系。
