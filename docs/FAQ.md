# FAQ / 常见问题

**Do I need to flash GammaOS? / 必须刷 GammaOS 吗？**

No. TwinGrid is primarily designed around stock Android firmware 1.18.
不需要，主要面向原厂 1.18 固件。

**Does it work on GammaOS? / 支持 GammaOS 吗？**

Not fully verified yet. 尚未完成兼容验证，欢迎提交实测信息。

**Does TwinGrid include DraStic or ROMs? / 包含模拟器或游戏吗？**

No. TwinGrid includes neither DraStic, ROMs nor BIOS. 均不附带。

**Where do game icons come from? / 游戏图标从哪里来？**

NDS banner icons are read directly from each ROM. 直接读取 ROM 内 banner 图标。

**Where do box arts come from? / 封面从哪里来？**

Downloaded on demand from configured online sources and cached locally.
The current routes use JSDMirror and jsDelivr for the same libretro-thumbnails
upstream. 按需联网下载并在本机缓存，不随安装包或仓库打包。

**Why does a game open DraStic but not load automatically? / 为什么只打开模拟器？**

Receiver versions and storage access differ. First confirm DraStic can manually
load the same file, then submit a compatibility report with versions, storage
location and steps. 模拟器版本和目录权限可能不同，请先确认手动加载正常，再按
兼容模板反馈。发出请求不等于游戏载入成功。

**Can I use folders? / 可以按文件夹浏览吗？**

Yes. Physical folder browsing and virtual metadata categories coexist.
可以，实际文件夹与资料类型分类共存。

**Does TwinGrid modify my ROMs? / 会改动 ROM 吗？**

No. Library organization reads ROMs and updates the frontend's own metadata;
it does not rename, move or rewrite ROM files. 整理只更新前端资料关联。

**Does TwinGrid modify my saves? / 会改动存档吗？**

The optional save folder is read for `.dsv` name matching, existence, size and
modification time. TwinGrid does not edit save content or convert savestates.
DraStic manages its own writes during play. 目录用于显示关联状态，模拟器游戏过程中
的存档写入由 DraStic 管理。切换游戏前请保存未保存进度。

**Can I update an older test build? / 旧版能覆盖安装吗？**

Yes, when it has the same Android package and signing certificate. Never
uninstall first to work around a mismatch. 同包同签名可覆盖，不同签名先核对来源。

**Is all metadata complete? / 双语资料完整吗？**

No. There are 1,360 edited bilingual works out of 3,417. Matching and editorial
facts can need correction. 尚有 2,057 个作品未达到完整双语覆盖，欢迎有来源的修正。
