# PlanYourTV 安装说明（给 AI 助手）

你在帮用户把 **PlanYourTV** 安装到 Android 电视上，并让它成为开机进入的桌面。PlanYourTV 是一个开源的 Android TV 儿童时间管理桌面，基于 Aerial，支持定时规则、单次计时、学习模式、答题解锁、本地题库，以及可选的多服务商 AI 出题（DeepSeek、GLM、Kimi 等）。

本文写给能在用户电脑上执行命令的 AI 助手，例如豆包电脑版 / 豆包工作版、WorkBuddy、千问电脑版、Claude Code、Codex、Cursor 或 Trae。手机版、网页版聊天只能把步骤告诉用户，不能执行命令；遇到这种情况，请直接让用户换到电脑版。

- 项目主页：https://github.com/heimi51168/planyourtv
- 官方下载：https://planyourtv.me/download/
- 支持系统：Android TV 5.0+（API 21+），不需要 root
- 版本：以 https://planyourtv.me/download/version.txt 为准；官方下载地址始终提供最新社区版本，不要使用任何文档里写死的版本号

## 0. 必须遵守的规则

1. **亲自在用户的电脑上运行命令**，不要只列步骤。每一步都看真实输出，不要猜。
2. **不要假装成功。** 最后必须完成「验收」，把原始输出贴给用户。没有看到输出，就不能说装好了。
3. **只安装 PlanYourTV。** APK 只从 `https://planyourtv.me/download/` 下载。不要下载、安装或推荐任何其他 App，包括应用商店、ADB 开启器、投屏工具、清理工具或“电视助手”。也不要从别的网站找 PlanYourTV 的 APK。
4. **不要卸载、停用或修改任何系统应用**，包括电视自带桌面。不要恢复出厂设置，不要删除电视上的文件。只能删除这次安装自己下载的临时 APK。
5. **重启电视前先征得用户同意。** 需要用户在电视上按遥控器时，明确告诉他按哪个键、选哪一项。
6. **失败就停下来。** 把命令和原始输出给用户看，不要自己换一个 APK、装一个工具或改系统设置硬闯。
7. **不要设置或询问用户的家长 PIN，也不要询问 AI 出题的 API Key。** 安装完成后让用户自己在电视上修改默认 PIN，并保存一次性恢复码；AI 出题的 Key 由家长自己在手机设置页填写。

## 1. 准备 adb

先运行：

```bash
adb version
```

有输出就跳到第 2 步。没有 adb 时，从 Google 官方下载 platform-tools：

- Windows（PowerShell）：

```powershell
cd $env:USERPROFILE
curl.exe -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
Expand-Archive -Force platform-tools.zip .
$env:Path = "$env:USERPROFILE\platform-tools;$env:Path"
adb version
```

- macOS：`brew install android-platform-tools`
- Linux：用系统包管理器安装 `adb` 或 `android-tools`

不要从第三方网站下载 adb 或“一键安装器”。

## 2. 连接电视

电脑和电视连接同一个局域网。把用户提供的电视 IP 记为 `<IP>`：

```bash
adb connect <IP>:5555
adb devices
```

`adb devices` 中这台电视后面必须是 `device`，不能是 `unauthorized` 或 `offline`。

- **`unauthorized`**：电视上弹出了「允许 USB 调试吗？」，请用户选「允许」并勾选「始终允许」，再运行一次 `adb devices`。
- **`failed to connect` / `Connection refused` / 超时**：电视没有打开 ADB，或 IP 不对。请用户按下面的品牌说明打开，再试。
- **只有「无线调试」、没有「USB 调试 / ADB 调试」开关（部分 Android 11+ 电视）**：在电视上打开 开发者选项 → 无线调试 → **使用配对码配对设备**，让用户提供配对码屏幕上的 `IP:端口`、6 位配对码和无线调试主页面的另一组 `IP:端口`，然后运行：
  ```bash
  adb pair <配对屏IP:端口> <配对码>
  adb connect <无线调试主页IP:端口>
  adb devices
  ```
  连上后可以再运行一次 `adb tcpip 5555`，以后直接用 `<IP>:5555`。

### 常见品牌打开 ADB 的方法

| 电视 | 怎么打开 |
|---|---|
| 小米 / Redmi | 设置 → 关于 → 连续按「产品型号」进入开发者模式；再到 设置 → 账号与安全，打开「ADB 调试」和「安装未知来源的应用」 |
| TCL / 雷鸟 | 设置 → 关于 → 本机信息，快速按遥控器「上、下、左、右」，在底部「更多」里打开 ADB |
| 索尼 / 原生 Android TV / Google TV | 设置 → 设备偏好设置 → 关于 → 连续按「内部版本号」7 次；再到开发者选项打开「USB 调试」或「网络调试」 |
| OPPO 电视 | 设置 → 系统 → 关于本机 → 设备型号，按「上、上、右、右」再确认；再到开发者选项打开「USB 调试」 |
| 海尔 / 模卡 | 按电视机身上的信号源键，再用遥控器按「左、左、上、右」进入工厂菜单，只打开「ADB 调试开关」，不要动其他选项 |

电视 IP 一般在 设置 → 网络 → 已连接的网络 里。

**打不开 ADB 的机型**：老乐视、荣耀智慧屏、老康佳等，直接告诉用户不支持，不要反复尝试，也不要让用户安装第三方 ADB 开启器。

## 3. 检查 Android 版本

```bash
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
```

- 第一条是 **21 或更大**：继续安装完整版。
- 第一条是 **14–20**（Android 4.0–4.4）：当前社区源码不包含 Android 4.x 版本，停止并告诉用户不支持。
- 第一条小于 14：不支持。
- 没有输出或报错：先检查 `adb devices` 是否仍是 `device`，不要猜版本。

## 4. 下载并安装

官方下载地址始终指向最新社区版本。先读取最新版本号（输出形如 `vX.Y.ZZZ`，不要用任何文档里写死的版本号判断）：

```bash
curl -fsSL https://planyourtv.me/download/version.txt
```

只从官方地址下载：

```bash
curl -fL -o planyourtv-standard.apk https://planyourtv.me/download/planyourtv-standard.apk
adb install -r planyourtv-standard.apk
```

输出必须是 `Success`。如果固件拒绝安装桌面类 App，改用 guard 版本重试：

```bash
curl -fL -o planyourtv-guard.apk https://planyourtv.me/download/planyourtv-guard.apk
adb install -r planyourtv-guard.apk
```

不要修改 manifest，不要为了让安装成功去卸载系统桌面。

常见安装失败：

| 输出 | 处理 |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES` | 电视上已有另一个签名的 PlanYourTV。**不要直接卸载**：卸载会丢失规则、题库、壁纸和照片。先问用户是否接受丢失数据；用户同意后再 `adb uninstall com.planyourtv.tv`，然后重装。 |
| `INSTALL_FAILED_OLDER_SDK` | 电视低于 Android 5.0，停止。 |
| 固件提示不允许安装桌面类 App | 改用 `planyourtv-guard.apk`；仍失败就停止并贴出输出。 |
| `INSTALL_FAILED_USER_RESTRICTED` | 可能是厂商限制。停下来把输出给用户，不要自行改系统设置或安装其他工具。 |

安装后确认版本：

```bash
adb shell dumpsys package com.planyourtv.tv | grep versionName
```

把输出的 `versionName` 与前面 `version.txt` 的值对比（去掉开头的 `v`，guard 版本再去掉结尾的 `-guard`），两者应一致。不一致说明下载到了旧文件或 CDN 旧缓存：停下来重新下载并核对，不要继续，也不要假装成功。

## 5. 授予只有 ADB 能给的权限

逐条运行。某一条报错可以继续，但要记录下来：

```bash
adb shell pm grant com.planyourtv.tv android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.planyourtv.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.planyourtv.tv SYSTEM_ALERT_WINDOW allow
adb shell appops set com.planyourtv.tv WRITE_SETTINGS allow
adb shell appops set com.planyourtv.tv REQUEST_INSTALL_PACKAGES allow
adb shell appops set com.planyourtv.tv GET_USAGE_STATS allow
adb shell appops set com.planyourtv.tv android:get_usage_stats allow
adb shell appops set com.planyourtv.tv ACCESS_RESTRICTED_SETTINGS allow
adb shell cmd notification allow_listener com.planyourtv.tv/app.orchard.tv.guard.RevivalListener
```

这些权限的用途：

| 权限 | 用途 |
|---|---|
| `ACCESS_FINE_LOCATION` | 读取 Wi-Fi 名称用于显示；社区版本不上传位置 |
| `WRITE_SECURE_SETTINGS` | 固件修改安全设置后，让 Home Guard 保持启用 |
| `SYSTEM_ALERT_WINDOW` | 显示倒计时、开机恢复桌面 |
| `WRITE_SETTINGS` | 调整屏幕亮度 |
| `REQUEST_INSTALL_PACKAGES` | 用户主动在应用内下载并安装更新 |
| `GET_USAGE_STATS` | 在无障碍服务看不到前台应用时检测当前 App |
| `ACCESS_RESTRICTED_SETTINGS` | Android 13+ 从文件安装时允许启用无障碍服务 |
| 通知监听 | 固件强制停止后重新绑定守护 |

Android 6/7 没有 `cmd notification` 时，追加监听器，不要覆盖已有的服务：

```bash
LISTENER="com.planyourtv.tv/app.orchard.tv.guard.RevivalListener"
CURRENT=$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')
case "$CURRENT" in
  *"$LISTENER"*) ;;
  ""|null) adb shell settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) adb shell settings put secure enabled_notification_listeners "${CURRENT}:${LISTENER}" ;;
esac
```

## 6. 启动并编译

```bash
adb shell am start -W -n com.planyourtv.tv/app.orchard.tv.MainActivity
sleep 5
adb shell cmd package compile -m speed -f com.planyourtv.tv
```

最后一条让应用在电视上更流畅，可能要几十秒。不要跳过。

## 7. 设为默认桌面

1. 请用户按遥控器主页键。
2. 系统询问「使用哪个应用」时，选择 **PlanYourTV**，再选「始终」或「总是」。
3. 如果固件仍然回到自带桌面，请用户在电视的 **无障碍 / 辅助功能** 设置里启用 PlanYourTV 的 **Home Guard**。不要停用自带桌面。
4. 如果电视的无障碍设置里没有入口，可以让用户先打开 PlanYourTV 的家长中心，按屏幕提示启用桌面守护。

在用户明确同意的情况下，也可以用 ADB 把 Home Guard 追加到现有无障碍服务列表（**不要覆盖其他服务**）：

```bash
GUARD="com.planyourtv.tv/app.orchard.tv.guard.HomeGuardService"
CURRENT=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$CURRENT" in
  *"$GUARD"*) ;;
  ""|null) adb shell settings put secure enabled_accessibility_services "$GUARD" ;;
  *) adb shell settings put secure enabled_accessibility_services "'$CURRENT:$GUARD'" ;;
esac
adb shell settings put secure accessibility_enabled 1
```

然后确认服务真的绑定，而不只是写进了设置：

```bash
adb shell dumpsys accessibility | grep -i -E 'PlanYourTV|HomeGuardService'
```

## 8. 验收（必须做）

原样运行并贴出输出：

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys window | grep mCurrentFocus
adb shell dumpsys package com.planyourtv.tv | grep versionName
```

Windows 上把 `grep` 换成 `findstr`。

检查：

- `resolve-activity` 返回的桌面是 `com.planyourtv.tv/...`，或用户实际选择的其他桌面（如果是 guard 版本，可能仍显示原桌面，需要 Home Guard 接管）。
- `mCurrentFocus` 在按主页键后包含 `com.planyourtv.tv`，或用户已确认看到 PlanYourTV 桌面。
- `versionName` 与 https://planyourtv.me/download/version.txt 一致（去掉开头的 `v`，guard 版本再去掉结尾的 `-guard`）。
- `enabled_accessibility_services` 包含 Home Guard（如果启用了）。

没有这些输出，不要说「安装成功」。

## 9. 安装完成后提醒用户

1. 在电视上打开 **PlanYourTV → 家长中心**，把默认 PIN `123456` 改掉。
2. 首次开启会显示一次性恢复码，让用户拍照保存；恢复码丢失后只能卸载重装。
3. 在家长中心设置工作日/周末时段、每日额度、单次上限和休息时间。
4. 勾选可以看的娱乐 App，以及学习 App（学习 App 不计时）。
5. 在手机设置页配置题库和答题规则。AI 出题是可选的，支持 DeepSeek、GLM、Kimi、通义千问、豆包等 OpenAI 兼容服务商预设：家长选择服务商后，到自己账号的控制台创建 API Key，在手机设置页原样粘贴并点「测试连接」。不同服务商的密钥格式不同，按控制台提供的内容填写即可；不要向家长索要 Key，也不要把它写进日志或报告。
6. 安装完成后关闭无线 ADB，或至少不要把它暴露到公网。

## 10. 还原 / 卸载

先把默认桌面切回电视自带桌面，再运行：

```bash
adb shell am force-stop com.planyourtv.tv
adb shell pm uninstall com.planyourtv.tv
```

卸载会删除本机规则、题库、壁纸和照片。不要删除或停用电视自带桌面；不要动其他无障碍或通知监听服务。

## 11. 常见问题

- **Home Guard 启用了但不生效**：部分 TCL / 海信固件限制侧载无障碍服务；让用户在电视的安全、自启动、无障碍设置里允许 PlanYourTV，再启动一次。不要停用自带桌面。
- **按主页键仍回自带桌面**：确认无障碍服务已绑定；检查是否被固件清理。不要用卸载系统桌面的方式解决。
- **安装后很卡**：确认装的是 release APK，并执行第 6 步的 `cmd package compile`；debug 包不适合日常使用。
- **远程管理入口找不到**：社区构建默认关闭并隐藏远程管理，这是预期行为；本地模式功能完整。
- **无法打开手机设置页**：确认手机和电视在同一 Wi-Fi，且 PlanYourTV 正在运行；页面只监听局域网，不要做端口转发。

## 12. 不要做的事

- 不要安装应用商店、ADB 开启器、投屏或清理工具。
- 不要从非官方地址下载 PlanYourTV APK。
- 不要卸载、停用或恢复出厂设置任何系统应用。
- 不要删除电视上不属于本次安装的文件。
- 不要把 5555 端口暴露到公网。
- 不要在未征得用户同意时重启电视。
- 不要把用户的 PIN、恢复码、电视 IP 或家庭信息写进报告、日志或公开 Issue。
