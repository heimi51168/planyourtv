# PlanYourTV

基于 Aerial 桌面构建的开源 Android TV 电视端家长控制。

PlanYourTV 增加了本地儿童模式：App 规则、学习与娱乐时长、休息时段、屏幕倒计时、家长密码和临时解锁。本地家长控制不需要账号、不需要服务器，也不需要连接云服务。

[English](README.md) · [安装](docs/INSTALL.md) · [从源码构建](docs/BUILDING.md) · [架构](docs/ARCHITECTURE.md)

> **项目状态：** 首个公开源码版本。当前社区基线为 `1.85.001`，对应上游 Aerial 1.85。本项目只在小米电视 `standard` 配置上做过真机验证；其他机型沿用 Aerial 上游的支持范围，本项目未做验证。

## 功能

- **本地儿童模式**：App 白名单、学习与娱乐时长、休息时段、每日限额。
- **灵活计时**：按规则自动开始，或手动开始一次会话；电视上显示剩余时间。
- **家长控制**：六位家长密码、电视端家长中心、临时解锁。
- **Home Guard**：在厂商固件强占 Home 键的电视上保住桌面入口。
- **手机局域网设置页**：App 运行期间电视自己提供设置页，不需要账号。
- **五种界面语言**：英语、简体中文、俄语、乌克兰语、哈萨克语。
- **无广告、无统计 SDK**：社区源码不包含广告或行为分析 SDK。

## 开源了什么

- Android TV App：本地规则、密码、锁屏和会话计时。
- 可选托管模式使用的通用客户端代码。
- 在本仓库中构建 `standard` 和 `guard` APK 所需的构建文件。

## 暂未开源

- 远程控制由独立的托管家长端和服务提供。服务端和家长端源码不在本仓库。
- 设备协议尚未公开，后续可能变化。本版本不承诺可以自托管。

社区构建默认只启用本地模式：不内置任何后端地址，远程入口隐藏。官方构建可在编译时用 `-PtvApiBase=<endpoint>` 显式开启。

## 安装

安装 release APK：

```bash
adb install -r PlanYourTV-standard-release.apk
```

安装后从电视的 App 列表打开 PlanYourTV，在 Android 询问时选择它作为主屏幕。`guard` 版本用于拒绝安装声明 Home 入口的固件的电视；完整的 ADB 配置、权限、Home Guard 与卸载步骤见 [docs/INSTALL.md](docs/INSTALL.md)。

直接使用 `adb install` 不会授予 Home Guard、亮度和开机恢复所需的 ADB 权限。App 仍可打开，但部分桌面接管功能需要按安装指南继续执行命令。

## 支持机型

| 机型 | 状态 |
|---|---|
| 小米电视，`standard` 版本 | 本项目真机验证 |
| Aerial 上游支持的其他机型 | 沿用上游支持；本项目未验证 |
| `guard` 版本 | 沿用 Aerial 上游支持；本项目未验证 |
| Android 4.x（Aerial Lite） | 社区源码快照不包含 |

请只在自己拥有或获授权管理的电视上安装。替换主屏幕的 App 如果被卸载或配置错误，可能让电视暂时没有原来的桌面；安装前请确认还能通过 ADB 连接电视。

## 隐私

本地家长控制不需要账号，也不会上传规则或 App 列表。App 会为了天气、每日壁纸和检查更新发起可选的网络请求，运行期间还会在局域网内提供设置页。完整清单见 [PRIVACY.md](PRIVACY.md)。

## 安全

PlanYourTV 是一个通过 ADB 获得额外能力的桌面：无障碍服务、通知监听和安全设置写入。它是家长控制的辅助工具，不是防止电视所有者或有 ADB 权限的人绕过系统的安全边界。开启儿童模式前请先修改默认密码。漏洞上报和加固建议见 [SECURITY.md](SECURITY.md)。

## 法律声明

PlanYourTV 是基于 [Aerial](https://github.com/pengchujin/aerial-tv) 的独立项目，与 Apple、小米、TCL 或 Aerial 项目没有隶属、背书或合作关系。

社区源码快照不包含 Aerial 的 Apple 航拍影片目录，因此不提供航拍壁纸和航拍屏保；桌面使用内置壁纸或 Bing 每日壁纸，屏保回落到照片或时钟。天气数据来自 Open-Meteo，城市数据来自 GeoNames，每日壁纸来自 Bing。

## 许可证

MIT。Aerial 的版权声明和第三方声明保留在 [LICENSE](LICENSE)、[NOTICE](NOTICE) 和 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 参与贡献

欢迎提交 Issue 和 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。报告中不要包含电视 IP、ADB 密钥、签名文件、账号凭据或第三方媒体文件。
