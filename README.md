<div align="center">

# PlanYourTV

**把 Android TV 变成一台会管时间的儿童电视桌面。**

开源 · 无广告 · 本地优先 · 基于 Aerial

[下载 APK](https://planyourtv.me/download/planyourtv-standard.apk) ·
[让 AI 帮你安装](#让-ai-帮你安装) ·
[安装文档](docs/INSTALL.md) ·
[常见问题](docs/FAQ.md) ·
[English](#english)

<a href="https://planyourtv.me/download/planyourtv-standard.apk"><img alt="下载 APK" src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD-APK-34a853?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="https://github.com/heimi51168/planyourtv/blob/main/LICENSE"><img alt="许可证 MIT" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>
<img alt="Android TV 5.0+" src="https://img.shields.io/badge/Android%20TV-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white">
<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7f52ff?style=flat-square&logo=kotlin&logoColor=white">
<img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack-Compose-4285f4?style=flat-square&logo=jetpackcompose&logoColor=white">
<img alt="基于 Aerial 1.93" src="https://img.shields.io/badge/%E5%9F%BA%E4%BA%8E-Aerial%201.93-1f6feb?style=flat-square">

</div>

PlanYourTV 是一个**桌面级儿童时间管理 App**：它替代电视自带桌面，开机进入，按 Home 回到，把「什么时候能看、看多久、看什么」变成电视自己执行的规则。孩子到点不会只看到一个冷冰冰的「已锁定」，而是可以**答题赚时间**；家长不用每天守在电视前重复同一句话。

它适合想给**小米、TCL、索尼等 Android TV** 设置儿童模式、管理屏幕时间、限制娱乐 App 的家庭。它不是定时器，也不是监控软件：不录屏、不读聊天、不定位，只在本机记录用了多久、用了哪个 App。

> 常见搜索：Android TV 儿童模式、小米电视儿童锁、TCL 电视时间管理、电视屏幕时间、儿童电视桌面、答题解锁电视、Aerial 儿童桌面。

> **当前社区源码版本：`1.93.000`（versionCode 131000），基于 Aerial 1.93。** 小米电视 `standard` 已由本项目真机验证；其他机型和 `guard` 版本沿用 Aerial 上游的支持范围，本项目未逐一验证。

## 项目速览

| 项目 | 内容 |
|---|---|
| 名称 | PlanYourTV（中文可称「计划电视」） |
| 类型 | Android TV 桌面 / 儿童时间管理 / 家长控制 |
| 平台 | Android TV 5.0+（API 21+）；Android 4.x 不支持；不需要 root |
| 包名 | `com.planyourtv.tv` |
| 当前版本 | `1.93.000` / 131000 |
| 上游基线 | [Aerial](https://github.com/pengchujin/aerial-tv) 1.93（MIT） |
| 许可证 | MIT |
| 下载 | [standard APK](https://planyourtv.me/download/planyourtv-standard.apk) · [guard APK](https://planyourtv.me/download/planyourtv-guard.apk) |
| 数据 | 本地优先；不需要账号；本地模式不依赖服务器 |
| 界面语言 | 简体中文、英语、俄语、乌克兰语、哈萨克语 |

## 为什么是 PlanYourTV

- **规则自己执行。** 工作日/周末时段、每日额度、单次上限、休息间隔，设一次就生效；到点自动锁屏。
- **只对指定的 App 开放。** 家长勾选哪些娱乐 App 可以看；计时会话期间，其他 App 会被桌面拦回。
- **学习 App 不计时。** 学习分组单独放行，看学习 App 不扣时长、不占额度。
- **桌面级，不是一个开关。** 它替换的是电视桌面本身，开机和按 Home 都会回到 PlanYourTV；孩子不能靠「退出这个 App」绕过（但它不是防破解的安全边界，见[安全说明](#隐私与安全)）。
- **孩子有正向出口。** 时间用完弹出答题，答对赚取观看时长；答错也会显示解析，不是单纯惩罚。
- **本地优先。** 不需要注册账号，不需要云服务；AI 出题由家长自己的模型服务提供。
- **没有广告和推荐流。** 社区源码不含广告或行为统计 SDK。

## 功能

### 1. 定时规则与单次计时

- **工作日 / 周末两套规则**：分别设置每天可以使用的时间段；时段之外电视保持锁定。
- **每日额度**：每天最多看多少分钟，用完即止。
- **单次上限**：一次连续观看最多多少分钟，到点自动结束。
- **休息间隔**：一次会话结束后强制休息一段时间，避免连续刷屏。
- **自动开始 / 家长确认**：自动模式按规则开始；手动模式由家长在锁屏输入家长 PIN 后开始。切换自动 / 手动模式前会弹窗确认，说明计时规则会怎样变化。
- **单次计时**：家长可以在锁屏或家长中心手动开始一段 **5–120 分钟**的观看时间（默认 20 分钟，5 分钟一档），适合周末临时加时。
- **看得见的倒计时**：剩余时间、休息倒计时、下一次可用时段直接显示在电视上。

### 2. App 级计时范围

- 只把家长选定的**娱乐 App**放进计时范围；会话期间打开其他 App，Home Guard 会回到 PlanYourTV。
- **学习 App 单独分组**：学习 App **不计时、不占每日额度**；孩子从锁屏进入学习模式后使用，学习模式期间只显示学习 App。
- 计时只针对家长选定的娱乐 App；不需要为了管控去卸载系统应用。
- 计时会话与手动会话共用同一套锁定、休息和倒计时状态。

### 3. 儿童 / 学习模式：只留学习 App

- 一键进入学习模式，电视上**只显示家长勾选的学习 App**，其他 App 全部隐藏。
- 学习模式**不计时、不扣额度、不需要 PIN**，适合写作业、上网课。
- 退出学习模式回到锁屏，不会自动开始新的计时。
- 待机或关机后学习模式自动退出，下次开机仍按正常规则执行。

### 4. 弹窗答题，答对赚时间

时间用完不是死锁，而是给孩子一个「自己挣时间」的出口：

- 锁屏上弹出答题挑战；开始前可以选择答题范围，确认面板会写明年级、学科、学期、教材、范围和「单选」。
- 每轮 **5–50 道题（5 道一档，默认 5 道）**，每题默认 **60 秒**；家长可以设计最多 **6 个答题关卡**，孩子在电视上滚动选择。
- **难度计分**：简单 / 中等 / 困难分别设置答对奖励和答错扣分，结算按净得分计算；可以设置最低解锁分钟和单轮奖励上限。
- 每题作答后立即显示对错和解析；全部题目答完后统一结算，奖励时长直接进入下一次观看。
- 答对反馈、语音提示和大号金色时间，让答题更像游戏而不是惩罚；不再需要配置答案展示时长。
- 题目、答案和答题进度只保存在电视上，不会上传。

### 5. 本地题库 + AI 出题 + 错题管理

- **内置 4,900 道题**：1–9 年级上册期中必考，覆盖语文、数学、英语、科学，以及初中道德与法治、历史、地理、生物、物理、化学，开箱即用。
- **手动导入题库**：家长在手机设置页上传 `.json` / `.txt` 文件或直接粘贴题目，可选择已有标签或新建标签，预览重复题目和剩余容量后导入；只接受单选题。题目和标签只保存在电视上，导入的标签可以直接作为自定义关卡。
- **按范围出题**：可按 1–9 年级、学期、学科、教材版本、真实单元 / 期中 / 期末 / 复习范围筛选，避免题目超纲；教材目录来自国家中小学智慧教育平台（109 本教材、765 个节点、673 个单元）。
- **AI 批量出题（可选）**：家长在手机设置页配置自己的 OpenAI 兼容模型服务（默认 DeepSeek 接口，Key 需家长自行到 DeepSeek 开放平台创建），AI 按真实单元标题和课时范围生成 20 / 30 / 50 道题，完成后**自动加入电视本地题库**。
- **密钥只在电视上**：API Key 保存在电视端，手机页面通过电视代理调用模型；请求只包含学科、年级、难度、数量等出题参数，不包含家庭信息。不配置 AI 也能使用内置题库和手动题库。
- **题库管理**：查看未使用、已回答、本轮中、答对、答错、旧记录和过期题目；支持去重、容量管理和批量清理。
- **错题本**：最近答错的题排在前面，显示答错次数和作答日期；可以单独清空错题，保留其他题目。

### 6. 家长控制与桌面体验

- **六位家长 PIN**：首次开启会生成一次性恢复码，请拍照保存；忘记 PIN 可用恢复码重置。默认 PIN 会显示风险提示，请先修改。
- **临时解锁**：家长可以短时间接管电视，家长使用时间不计入孩子的额度。
- **电视端家长中心**：在电视上直接调整规则、学习 App、计时和答题设置。
- **自动更新（可选）**：支持静默安装的电视可在家长中心开启自动更新；新版本会在后台下载，并在其他 App 停留一段时间后安装，设置和题库保留。其他电视仍可在控制中心手动下载安装。
- **手机局域网设置页**：电视运行时，同一 Wi-Fi 下的手机可以打开设置页，管理规则、题库、错题、壁纸和 App 布局；不需要账号，也不经过公网。
- **Aerial 桌面体验**：App 网格、文件夹、壁纸、屏保、控制中心、HDMI 信号源和按键声音都保留；支持 `standard` 和 `guard` 两种构建。控制中心的「信号源设置」可以选择要显示的信号源、是否随 HDMI-CEC 设备开机自动切换，以及开机时进入哪个信号源。
- **Home Guard**：在固件强行把 Home 键交给自带桌面的电视上，通过无障碍服务守住 PlanYourTV 的入口。
- **五种界面语言**：简体中文、英语、俄语、乌克兰语、哈萨克语。

### 7. 远程管理（路线图）

> 通过手机在外网管理电视的**远程管理**目前是路线图功能。社区构建默认关闭并隐藏入口；本地规则、学习模式、计时和答题不依赖它，功能完整。远程服务端和手机家长端源码不在本仓库。

## 兼容性：与 Aerial 保持一致

PlanYourTV 保留 Aerial 的设备适配层和桌面架构，**设备兼容范围与 Aerial 保持一致**。Aerial 支持的 Android TV 5.0+ 设备，通常也可以运行 PlanYourTV；具体机型仍以 Aerial 上游的适配记录为准。项目只发布 Android 5.0+ 版本，Android 4.x 不支持，也不再提供 Lite APK。

| 设备 / 版本 | 状态 |
|---|---|
| 小米电视，`standard` 版本 | 本项目真机验证 |
| Aerial 上游支持的其他 Android TV 5.0+ 设备 | 沿用 Aerial 支持范围；本项目未逐一验证 |
| `guard` 版本（固件拒绝桌面类 App 时使用） | 沿用 Aerial；本项目未真机验证 |
| LG webOS、三星 Tizen、鸿蒙智慧屏 | 不是 Android TV，不支持 |

安装前请确认电视运行 **Android 5.0 或更高版本**，并且你能通过 ADB 连接电视。先装 `standard`；如果固件拒绝桌面类 App，再换 `guard`。安装器会在这类已知拒绝场景自动重试 guard。替换桌面有风险：请保留电视自带桌面，不要卸载或停用系统应用。

## 安装

### 让 AI 帮你安装

**推荐方式。** 把下面**整段**复制给能操作电脑的 AI 助手，例如豆包电脑版 / 豆包工作版、WorkBuddy、千问电脑版、Claude Code、Codex 或 Trae。手机版、网页版聊天只能给步骤，不能执行命令。

```text
你是一个能在电脑上执行命令的 AI 助手。请帮我把 PlanYourTV（开源 Android TV 儿童时间管理桌面）安装到我的电视，并设为默认桌面。

电视 IP：<电视IP>
电视品牌/型号：<品牌型号；不知道就留空>

请严格按下面的步骤做，并把每一步的真实命令输出保留下来：

1. 运行 adb version。没有 adb 时，从 https://developer.android.com/tools/releases/platform-tools 下载 Google platform-tools，解压后使用其中的 adb。
2. 运行 adb connect <电视IP>:5555 和 adb devices，确认电视状态是 device。电视没有开 ADB 时，告诉我按遥控器怎么开。
3. 运行 adb shell getprop ro.build.version.sdk。如果小于 21，停止并告诉我 PlanYourTV 需要 Android 5.0 或更高版本，Android 4.x 不支持。最后把命令的原始输出贴给我。
4. 安装前检查其他第三方桌面：PlanYourTV 本身就是桌面，但 Android 允许同时安装多个桌面，只有默认桌面会在按 Home 时打开。运行 adb shell pm list packages -3 和 adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT 列出第三方 HOME 应用；默认保留它们并继续安装，不要自动卸载。装好后把 PlanYourTV 设为默认桌面。只有我明确要求清理时，才卸载同时出现在上述第三方包列表和 HOME 查询结果里的包（排除 com.planyourtv.tv）：先告诉我卸载会删除该应用的数据，我确认后运行 adb shell pm uninstall <包名>，再用 adb shell pm path <包名> 确认没有输出。不要卸载电视自带桌面或任何系统应用。
5. 如果 SDK 是 21 或更大，推荐从 https://planyourtv.me/download/planyourtv-standard.apk 下载 APK。安装推荐用电脑；其他安装方式不禁止，本项目未逐一验证。
6. 运行 adb install -r planyourtv-standard.apk。如果固件拒绝安装桌面类 App，改用 https://planyourtv.me/download/planyourtv-guard.apk 重试；不要修改 manifest，不要卸载或停用电视自带的桌面。已知的安装拒绝由官方安装器自动处理，手动安装时只需按这条回退。
7. 安装成功后，依次运行：

adb shell pm grant com.planyourtv.tv android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.planyourtv.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.planyourtv.tv SYSTEM_ALERT_WINDOW allow
adb shell appops set com.planyourtv.tv WRITE_SETTINGS allow
adb shell appops set com.planyourtv.tv REQUEST_INSTALL_PACKAGES allow
adb shell appops set com.planyourtv.tv GET_USAGE_STATS allow
adb shell appops set com.planyourtv.tv android:get_usage_stats allow
adb shell appops set com.planyourtv.tv ACCESS_RESTRICTED_SETTINGS allow
adb shell cmd notification allow_listener com.planyourtv.tv/app.orchard.tv.guard.RevivalListener
adb shell am start -W -n com.planyourtv.tv/app.orchard.tv.MainActivity

某一条权限命令报错可以继续，但要记录下来。不要覆盖或删除其他无障碍/通知监听服务。
8. 等 5 秒，运行 adb shell cmd package compile -m speed -f com.planyourtv.tv。
9. 请我按遥控器主页键，在系统询问时选择 PlanYourTV。如果固件仍然回到自带桌面，请我在电视的无障碍设置里启用 PlanYourTV 的 Home Guard；不要停用自带桌面。
10. 验收，并把原始输出贴给我：

adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys window | grep mCurrentFocus
adb shell dumpsys package com.planyourtv.tv | grep versionName

Windows 上把 grep 换成 findstr。
11. 最后提醒我：在电视上打开家长中心，把默认 PIN 123456 改掉，并保存好一次性恢复码。不要替我设置 PIN。

禁止：卸载或停用任何系统应用；恢复出厂设置；删除电视上的文件；把 5555 端口暴露到公网；重启电视前必须先问我。任何一步失败，停下来把命令和原始输出贴给我，由我决定下一步。
```

安装完成后，电视上打开 PlanYourTV → 家长中心 → 设置规则 → 修改默认 PIN。完整的 AI 安装说明书（品牌 ADB 开关、权限含义、验收、排错和卸载）见 [docs/AGENT-INSTALL.md](docs/AGENT-INSTALL.md)；人工步骤、Home Guard 和屏保设置见 [docs/INSTALL.md](docs/INSTALL.md)。

### 手动安装（macOS / Linux / Windows）

1. 电视打开 ADB 调试，电脑安装 Android platform-tools。
2. 连接电视：`adb connect <电视IP>:5555`，确认 `adb devices` 显示 `device`。
3. 下载 [planyourtv-standard.apk](https://planyourtv.me/download/planyourtv-standard.apk)。
4. 安装并授权：

```bash
adb install -r planyourtv-standard.apk
adb shell pm grant com.planyourtv.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.planyourtv.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -W -n com.planyourtv.tv/app.orchard.tv.MainActivity
```

完整命令（包括通知监听、Home Guard、屏保和 TCL/创维等机型的特殊处理）见 [docs/INSTALL.md](docs/INSTALL.md)。

### 从源码构建

```bash
git clone https://github.com/heimi51168/planyourtv.git
cd planyourtv
./gradlew :app:assembleStandardRelease
```

需要 JDK 17 和 Android SDK。社区源码不需要签名密钥也能编译 debug 包；release 签名配置见 [docs/BUILDING.md](docs/BUILDING.md)。

## 升级与卸载

- **升级**：电视上出现「有新版本」时，按提示下载并安装；支持静默安装的电视可以在家长中心开启自动更新。设置和题库都会保留，也可以重新运行安装步骤覆盖安装。
- **卸载**：先把默认桌面切回电视自带桌面，再运行 `adb uninstall com.planyourtv.tv`。卸载会删除本机规则、题库、壁纸和照片；用安装器清理选项明确卸载的其他第三方桌面不会自动装回。
- **还原**：不要把电视自带桌面卸载或停用；它是你随时退回原桌面的保险。

## 隐私与安全

- 本地模式不需要账号，不上传规则、App 列表或答题记录。
- 可选网络请求只有：检查更新、天气、必应每日壁纸，以及局域网内的手机设置页。
- AI 出题需要家长自行配置模型服务；API Key 只保存在电视上，出题请求不包含家庭信息。
- 社区源码不含广告、统计或崩溃上报 SDK。
- 这是一个通过 ADB 获得无障碍、通知监听和安全设置权限的桌面应用。它是家长控制的**辅助工具**，不是防止电视所有者或拥有 ADB 权限的人绕过系统的安全边界。请修改默认 PIN、保管好恢复码，并关闭不用的无线 ADB。

完整说明见 [PRIVACY.md](PRIVACY.md) 和 [SECURITY.md](SECURITY.md)。

## 常见问题

**PlanYourTV 和 Aerial 是什么关系？**

PlanYourTV 是基于 [Aerial](https://github.com/pengchujin/aerial-tv)（MIT）的独立项目，保留了 Aerial 的桌面和设备适配层；它不是 Aerial 官方项目，也不代表 Aerial 上游。

**需要 root 吗？**

不需要。安装和授权通过电脑 ADB 完成，电视不需要 root。

**孩子能不能绕过？**

它能拦住日常的「退出 App、按 Home、换 App」，但不是防破解系统。拥有 ADB、root 或设备所有者权限的人仍然可以绕过。请把它当作规则执行工具，而不是安全边界。

**AI 出题必须联网吗？**

只有使用 AI 出题时需要联网，并且要由家长配置自己的 OpenAI 兼容模型服务（默认 DeepSeek，Key 需家长自行到 [DeepSeek 开放平台](https://platform.deepseek.com)创建）。内置 4,900 道题和手动题库完全离线可用。

**支持哪些电视？**

Android TV 5.0+，兼容范围与 Aerial 保持一致。小米 `standard` 已真机验证；其他机型未逐一验证。Android 4.x 不支持。LG webOS、三星 Tizen 和鸿蒙智慧屏不支持。

更多问题见 [docs/FAQ.md](docs/FAQ.md)。

## 给 AI / 搜索引擎

- [llms.txt](llms.txt)：项目事实、官方链接、功能与安装的机器可读摘要。
- [docs/AGENT-INSTALL.md](docs/AGENT-INSTALL.md)：给能执行命令的 AI 助手的完整安装说明。
- [docs/FAQ.md](docs/FAQ.md)：安装、兼容、隐私和使用的详细问答。

## 参与贡献

欢迎提交 Issue 和 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。报告中不要包含电视 IP、ADB 密钥、签名文件、账号凭据或孩子的个人信息。

## 致谢

PlanYourTV 的桌面、Home Guard、设备适配层和整体架构来自 [Aerial](https://github.com/pengchujin/aerial-tv)。感谢作者 [pengchujin](https://github.com/pengchujin) 与 Aerial 贡献者以 MIT 协议开源这项工作，让 PlanYourTV 能在成熟的 Android TV 桌面基础上专注做儿童时间管理。

其他开源依赖和素材的许可信息见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 许可证与声明

MIT。Aerial 的版权声明和第三方声明保留在 [LICENSE](LICENSE)、[NOTICE](NOTICE) 和 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

PlanYourTV 与 Apple、小米、TCL、索尼或 Aerial 项目没有隶属、背书或合作关系。社区源码快照不包含 Aerial 的 Apple 航拍影片目录，因此不提供航拍壁纸和航拍屏保；桌面使用内置壁纸或必应每日壁纸，屏保回落到照片或时钟。

---

## English

**PlanYourTV is an open-source, desktop-level parental-control launcher for Android TV.** It replaces the TV home screen and enforces screen-time rules at the launcher level: scheduled windows, daily and per-session limits, rest breaks, an app allowlist, a study mode, and a quiz challenge that lets kids earn extra viewing time.

Built on [Aerial](https://github.com/pengchujin/aerial-tv) 1.93 (MIT). Current community source: **`1.93.000`** / 131000. Xiaomi `standard` is verified on a real TV; other devices follow Aerial's upstream support and are not verified by this project.

### Highlights

- **Rules that run themselves** — weekday/weekend windows, daily quota, per-session cap, rest breaks, automatic lock screen and countdown.
- **App-level scope** — only the entertainment apps selected by the parent are allowed during a timed session; learning apps are never timed and are available through Study mode on the lock screen.
- **Study mode** — shows only the configured learning apps; no timer, no quota, no PIN.
- **Quiz unlock** — the child picks an answer scope, then answers 5–50 questions (5-question steps, default 5; 60 seconds each). Easy / medium / hard questions carry separate rewards and deductions; a settable minimum net score gates the unlock and a per-round cap limits the payout. Parents can design up to six named levels, and every answer gets feedback and an explanation.
- **Local question bank, manual import + AI generation (optional)** — 4,900 built-in first-semester midterm questions for grades 1–9 across Chinese, math, English, science and junior-high subjects. A parent can also import a `.json` / `.txt` file or paste single-choice questions on the phone page, choose or create a tag, and preview duplicates and free space; imported tags can become custom levels. AI generation targets real textbook units (109 books, 765 nodes, 673 units) and supports 20 / 30 / 50-question batches through the parent's own OpenAI-compatible provider (DeepSeek by default; the parent creates the key); the API key stays on the TV.
- **Question bank and wrong-answer management** — filters, deduplication, capacity management, wrong-answer review and selective clearing.
- **Parent controls** — six-digit PIN, one-time recovery code, temporary parent override, TV parent center, and a LAN phone settings page. Switching between automatic and manual modes requires an explicit confirmation that explains how the timing rules change.
- **Updates and inputs** — optional background auto update on TVs that allow silent installs, keeping settings and question bank, plus configurable input visibility, HDMI-CEC switching and boot input in Control Center.
- **Aerial desktop experience** — app grid, folders, wallpapers, screensaver, control center, HDMI sources, Home Guard, `standard`/`guard` flavors and five UI languages.
- **Local-first privacy** — no account, no advertising SDK, no analytics SDK; optional update, weather and wallpaper requests only.

Remote management is a roadmap feature: the community build hides it and does not install the hosted adapters. Local parental control is complete without it.

### Install

The easiest path is to give the Chinese prompt in [让 AI 帮你安装](#让-ai-帮你安装) to a desktop AI agent (Doubao for desktop, WorkBuddy, Qwen for desktop, Claude Code, Codex or Trae). It downloads only the official APK, installs it over ADB, grants permissions, sets Home Guard if needed, and verifies the result.

Manual install:

```bash
adb connect <TV-IP>:5555
adb install -r planyourtv-standard.apk
adb shell pm grant com.planyourtv.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.planyourtv.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -W -n com.planyourtv.tv/app.orchard.tv.MainActivity
```

Download: [planyourtv-standard.apk](https://planyourtv.me/download/planyourtv-standard.apk) · [planyourtv-guard.apk](https://planyourtv.me/download/planyourtv-guard.apk) when the firmware refuses a home-screen app · Full guide: [docs/INSTALL.md](docs/INSTALL.md)

### Compatibility

Android TV 5.0+ (API 21+), no root. Device support follows Aerial; only Xiaomi `standard` has been verified by this project. The `guard` flavor is for firmware that refuses a sideloaded home activity. Android 4.x is not supported. Public releases contain only the `standard` and `guard` APKs. LG webOS, Samsung Tizen and HarmonyOS TVs are not supported.

### Acknowledgements

PlanYourTV's launcher, Home Guard, device adaptation layer and overall architecture are built on [Aerial](https://github.com/pengchujin/aerial-tv). Thank you to [pengchujin](https://github.com/pengchujin) and the Aerial contributors for releasing their work under the MIT license, which lets PlanYourTV focus on parental controls on top of a mature Android TV launcher.

Licenses for other open-source dependencies and assets are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

### License

MIT. Aerial's copyright and third-party notices are preserved in [LICENSE](LICENSE), [NOTICE](NOTICE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
