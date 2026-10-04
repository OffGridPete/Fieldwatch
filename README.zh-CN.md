# Fieldwatch 中文版

[English](README.md) | 简体中文

Fieldwatch 是一款 Android 无线信号观察工具，通过手机接收周围的 Wi-Fi 接入点信息和蓝牙低功耗（BLE）广播，帮助你查看、筛选、识别和记录附近的信号源。基础扫描和识别可以离线使用，无需账号或额外硬件，也没有 Fieldwatch 后端服务器。

本仓库是 [smallclover/Fieldwatch](https://github.com/smallclover/Fieldwatch)，基于 [OffGridPete/Fieldwatch](https://github.com/OffGridPete/Fieldwatch) 维护，已加入简体中文支持。原作者为 Off Grid Pete LLC。当前代码基于原项目 **1.1.18**，应用包名仍为 `app.fieldwatch`。

## 功能

- 查看 Wi-Fi 接入点和 BLE 广播，观察信号强度、广播信息及变化。
- 使用筛选条件和预设整理列表，按内置识别特征解释可能的设备类型。
- 新建、编辑、导入和导出识别特征，保留自定义规则。
- 为信号源命名、添加关注，按设置使用通知、蜂鸣和语音告警。
- 保存监测会话，查看路径和会话对比，导出文本及 PDF 报告。
- 生成供外部 AI 工具使用的分析文本；是否分享、分享给谁由你决定。
- 切换简体中文、English 或跟随系统。

设备类型和行为解释属于推测。手机能收到什么，取决于设备广播、距离、手机硬件、Android 扫描限制和厂商省电策略；识别特征命中不等于确认设备身份。

## 中文支持

打开应用的 **设置 → 语言 → 简体中文** 即可切换。也可以选择 **English** 或 **跟随系统**；系统语言未受支持时使用英文。日语暂未加入。

中文覆盖主要页面、设置、权限说明、内置识别特征和解码说明、通知、语音、分享文本、会话报告及 PDF。中文语音需要手机的文字转语音（TTS）引擎提供中文语音，可在设置里点击 **测试告警** 检查。

设备实际广播的名称、品牌和协议标识，以及你输入的名称、备注和自定义规则，会保留原内容。切换语言不会把保存的数据整体翻译或改写。

## 安装到手机

需要 **Android 10 或更新版本**，以及支持 Wi-Fi 和 BLE 的手机。

**当前仓库的 `dist/Fieldwatch.apk` 是从原项目继承的安装包，没有随本次中文代码更新。它不能用于验证本仓库最新的中文功能。** `dist/` 中的独立说明卡和 PDF 用户手册也沿用原项目文档，尚未完成中文翻译。

要体验当前中文版，请按下文从源码构建，使用生成的 `app/build/outputs/apk/debug/app-debug.apk`。后续发布的安装包可查看本仓库的 [Releases 页面](https://github.com/smallclover/Fieldwatch/releases)，以发布说明为准。

安装有两种方式：

1. 将构建出的 APK 复制到手机，通过文件管理器打开，按 Android 提示允许该文件管理器安装应用并完成确认。
2. 开启手机开发者选项和 USB 调试，用 USB 连接电脑，在手机上允许调试后，通过 ADB 安装。

在项目根目录运行以下命令；ADB 需已加入 `PATH`：

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

如果同时连接了手机和模拟器，请明确选择目标设备。将下例的 `设备序列号` 替换为 `adb devices` 列出的实际值：

```powershell
adb -s '设备序列号' install -r .\app\build\outputs\apk\debug\app-debug.apk
```

首次启动时阅读应用说明，按系统提示授予扫描所需的位置、附近设备及通知等权限。权限入口和要求因 Android 版本而异；请同时检查手机的蓝牙、Wi-Fi 和系统定位开关。

**升级与签名：** 本分支与原作者版本使用相同包名，但你自行构建的 APK 通常使用不同签名，可能无法覆盖原作者签名的安装包。同一签名的版本可以使用 `-r` 覆盖更新。如果提示签名不兼容，先在应用设置中导出识别特征和设置，并另行保存需要的日志、会话和报告，再决定是否卸载旧版。卸载会清除应用数据，识别特征和设置导出也不等于备份全部日志及位置记录。

## 从源码构建

### 环境要求

| 工具 | 当前项目要求 |
| --- | --- |
| JDK | 17 |
| Android SDK | Android 15 / API 35 平台，以及 SDK Platform-Tools、Build-Tools |
| Gradle | 使用仓库自带 Wrapper，版本 8.11.1 |
| Android Gradle Plugin | 8.7.3，由项目配置管理 |
| Kotlin | 2.0.21，由项目配置管理 |
| Node.js | 仅维护国际化资源绑定时需要，普通 APK 构建不需要 |

可以使用 Android Studio 打开项目，也可以使用 Android SDK 命令行工具构建。首次构建需要联网下载 Gradle 和项目依赖。

### Windows 示例：工具和缓存放在 E 盘

以下是路径示例，假设 JDK 和 SDK 已安装在对应目录。按你的实际位置调整；环境变量只对当前 PowerShell 会话生效。

```powershell
git clone https://github.com/smallclover/Fieldwatch.git E:\programing\Fieldwatch
cd E:\programing\Fieldwatch

New-Item -ItemType Directory -Force E:\gradle-home, E:\Android\User, E:\Android\User\avd, E:\Android\Temp | Out-Null
$env:JAVA_HOME = 'E:\Java\jdk-17'
$env:ANDROID_HOME = 'E:\Android\Sdk'
$env:ANDROID_USER_HOME = 'E:\Android\User'
$env:ANDROID_AVD_HOME = 'E:\Android\User\avd'
$env:GRADLE_USER_HOME = 'E:\gradle-home'
$env:TEMP = 'E:\Android\Temp'
$env:TMP = 'E:\Android\Temp'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat assembleDebug --console=plain
```

已有仓库可直接进入目录，跳过克隆步骤。如果 Gradle 无法找到 SDK，可在根目录创建或编辑 `local.properties`，加入下列配置；该文件存放本机配置，不提交到 Git：

```properties
sdk.dir=E:/Android/Sdk
```

构建成功后，安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。macOS / Linux 使用 `./gradlew assembleDebug`，并配置对应的 JDK 和 SDK 路径。

本项目的普通 `debug` 构建启用了代码混淆及资源压缩，并关闭了 `debuggable`，可用于手机功能验证。需要调试和设备自动测试时使用独立的 `i18nQa` 构建，其包名为 `app.fieldwatch.i18nqa`。

正式发布使用 `assembleRelease`，并需要配置自己的发布签名。构建脚本从本机 `local.properties` 读取 `FIELDWATCH_STORE_FILE`、`FIELDWATCH_STORE_PASSWORD`、`FIELDWATCH_KEY_ALIAS` 和 `FIELDWATCH_KEY_PASSWORD`。妥善保管签名密钥，后续覆盖更新需要使用相同签名；密钥和密码不应提交到仓库。

## 测试与检查

在项目根目录运行单元测试和 Android Lint：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug --console=plain
```

报告位置：

- 单元测试：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint：`app/build/reports/lint-results-debug.html`

连接测试模拟器或专用测试设备后，可运行隔离构建的设备测试：

```powershell
.\gradlew.bat connectedI18nQaAndroidTest --console=plain
```

设备测试使用独立应用包和虚构数据。只连接预期的测试设备，避免对日常使用的手机进行无意的测试安装。

2026-10-04 的中文基线验证包括：460 项单元测试通过、6 项 API 35 设备测试通过、Lint 0 错误（仍有 56 项警告）、18 份英中 PDF 共 70 页的渲染检查，以及 Android 10 实机中文页面和告警听测。这是该次基线的记录，后续修改仍需重新验证。完整记录及尚未覆盖的场景见 [中文审计记录](CHINESE_AUDIT.md)。

## 参与维护

本分支的问题和建议请提交到 [smallclover/Fieldwatch Issues](https://github.com/smallclover/Fieldwatch/issues)。原项目及其更新记录可查看 [上游仓库](https://github.com/OffGridPete/Fieldwatch) 和 [CHANGELOG.md](CHANGELOG.md)；现有变更日志沿用原项目记录，本次中文工作的详情见下表。

| 文档 / 目录 | 内容 |
| --- | --- |
| [I18N_PLAN.md](I18N_PLAN.md) | 国际化方案 |
| [CHINESE_COMPLETION_PLAN.md](CHINESE_COMPLETION_PLAN.md) | 中文补齐与验收计划 |
| [CHINESE_AUDIT.md](CHINESE_AUDIT.md) | 验证证据与已知限制 |
| [I18N_STATUS.md](I18N_STATUS.md) | 中文基线实施与手机验证记录 |
| `app/src/main/res/values/` | 英文默认资源 |
| `app/src/main/res/values-b+zh+Hans/` | 简体中文资源 |
| `app/src/main/java/app/fieldwatch/i18n/` | 语言切换、显示翻译层与资源绑定 |
| `app/src/test/`、`app/src/androidTest/` | 单元测试与设备测试 |

新增界面文字时补齐英中字符串资源；新增领域说明或内置内容时，还需更新显式资源绑定：

```powershell
node tools/update-i18n-resources.mjs
node tools/update-i18n-resources.mjs --check
git diff --check
```

资源绑定由脚本生成，用于确保代码混淆和资源压缩后仍能正确显示。译文用于显示，匹配规则、稳定 ID、协议值和导入导出格式应保持兼容。

## 能力边界与隐私

Fieldwatch 观察 Android 提供的 Wi-Fi 扫描结果和 BLE 广播，不提供 Wi-Fi 客户端抓包、802.11 监听模式、经典蓝牙发现、蜂窝信号扫描或测向。设备未广播、休眠、超出范围或不被系统扫描接口暴露时，可能不会出现；信号强弱也不能直接代表精确距离。

位置标记记录的是**手机在接收时的位置**，不是被观察设备的位置。“同行”“可能尾随”等分析是线索，不能作为确认身份或行为的结论。

记录保存在手机上，但你主动使用分享、日志导出、报告或 AI 文本导出时，可能包含完整 MAC 地址、备注及位置。隐私模式主要控制部分显示和输出，不会从原始日志中删除已记录的完整坐标。

在线地名与地图功能会访问系统地理编码服务或地图服务；从 GitHub 更新内置目录也需要联网，目前仍获取原作者仓库的数据。配置 TAK 推送后，数据会发送到你指定的目标。基础离线扫描不要求这些联网功能。

## 许可证与致谢

原项目版权：Copyright (c) 2026 Off Grid Pete LLC。

项目源代码采用 [MIT License](LICENSE)，本分支保留原始许可证和版权声明。修改、再分发时应继续保留适用的版权和许可文本。软件按原许可证“按原样”提供，不保证发现或正确识别任何特定设备。

AndroidX、Kotlin 等依赖及离线编号表有各自的许可或使用条款，详见 [NOTICE](NOTICE)。本 README 为中文使用说明，不替代原始许可证文本。
