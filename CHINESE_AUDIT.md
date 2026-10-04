# Fieldwatch 中文补齐审计与验收记录

日期：2026-10-04。仓库：smallclover/Fieldwatch；本地分支：codex/i18n；基础版本：1.1.18（28）。本轮只完成英文与简体中文，日语暂缓。本记录随中文基线一并本地提交，未推送或发布。

提交前已获取 origin 与 upstream 的远程更新：两者 main 均为 `2b22008a1ef542e7b12298ca647883f0a50ffc59`，与开发基础相同，没有需要合并的新提交。

## 实现结论

中文资源与应用生成的动态文字已按本轮清单补齐。匹配规则、目录原始内容、用户名称/备注、协议数据及交换格式保留原义。验收区分自动检查、模拟器页面测试、PDF 看图和手机听测；未实际覆盖的条件继续标为受限，不宣称全矩阵通过。

## 缺口与处理

| 场景及源文件 | 发现的问题 | 处理及证据 |
| --- | --- | --- |
| 新建识别特征；ui/FieldwatchViewModel.kt | 默认名称为 New Signature | 新建时生成中文“新建识别特征”；设备测试验证切换/重建/取消，不改旧名称 |
| 日期、时长、会话容量；domain/Sit.kt、data/SitStore.kt | 英文日期格式、单位及删除最旧会话提示 | 当前语言日期与中文时长/提示；领域测试与实际报告验证 |
| 实时信道/时长；ui/screen/LiveScreens.kt | ch、h/m/s 等应用文字残留 | 信道及秒/分/小时使用资源；MHz 等标准单位保留 |
| 接收强度范围；domain/Rssi.kt | 详情和 AI 导出残留 to；缺失值为 Not available | 显示值使用中文“至”“不可用”；数值筛选计算保持原行为 |
| CoD 解释；i18n/CodLabels.kt、domain/DeviceExplain.kt | 蓝牙类别标签含大量英文 | 显示层翻译已知类别、服务组合及未知类别前缀；原始 CodDecoder 分类与推断逻辑保留，测试验证 |
| 广播未知类型；domain/AdvPayloadDecoder.kt | type N 未抽取 | “类型 N”，原始类型字节不改 |
| 网络/缓存状态；i18n/TakText.kt、domain/DebriefReport.kt、data/LogStore.kt | Off 静态缓存、推送失败及选定位置错误残留 | 读取时按当前语言渲染；内部状态判断仍用原值 |
| 通知启动状态；radio/ScanService.kt | Starting radios… 未抽取 | 启动与稳定扫描提示统一本地化，频道沿用原 ID |
| 候选规则/逻辑；domain/SignatureCandidates.kt、识别特征编辑页 | mfg、AND/OR 为自有显示文字 | 厂商、且/或显示翻译；规则 ID 和机器值不改 |
| 内置解码编辑；ui/screen/DecodeFieldsScreen.kt、i18n/CatalogText.kt | 编辑页标签/枚举/备注英文；编辑标签可能改变字段 ID | 对原始值匹配的内置字段采用显示覆盖；标签编辑保留内置 ID；设备测试验证自定义内容和取消边界 |
| 草稿恢复；ui/FieldwatchViewModel.kt、ui/SavedDraft.kt | ViewModel 草稿门控未保存，进程恢复可能丢失编辑器 | SavedStateHandle 保存门控/草稿模型，Compose 保存编辑字段；旋转/语言测试及真实后台进程回收验证 |
| 未支持语言；i18n/AppLanguage.kt | 英文资源与其他语言日期/语音可能混用 | 未支持配置统一回退英文上下文；API 35 系统 LocaleManager 注入法语验证 |
| PDF 布局；domain/DebriefReport.kt、data/DebriefPdf.kt | 布局依据英文标题；关注卡长标题/超长备注可能越界 | 用结构枚举标识关注段；标题测量换行，超长卡拆为可分页块；改标题和极长备注样本实测 |
| 语音队列；alert/Alerter.kt、alert/VoiceOutput.kt | 缺失语言/初始化/发声失败提示不足；旧语言延时回调可能继续 | 失败提示、语言变化使旧队列票据失效、每次发声使用当前语言；3 项可控语音测试通过，真实听测单列 |
| 资源维护 | 5 个确认无用字符串 | 删除英中对应资源；保留仍用于路径图例的额外关注文字；显式资源绑定检查 |

源码候选审计包括页面、领域说明、告警、服务、报告、导出及稀少错误分支。人工复核后的保留项：Fieldwatch/作者/厂商和产品品牌；MIT 与 NOTICE 原文；设备广播名、SSID、用户文字；MAC/UUID/OUI/CoD/RSSI/GPS/TAK/CoT/Wi-Fi/BLE/IEEE 等协议标识及标准登记名称；解码类型/字节序/十六进制；dBm、MHz、m、km 等单位；UTC/ISO 机器时间；日志标签、URL、JSON/CSV 键与稳定 ID。原文变化或用户修改不满足内置原文校验时，显示原值，以避免错误覆盖。

## 测试设施与回归

- E 盘 JDK 17、Android SDK、Gradle 缓存、临时目录继续使用已有配置。API 35 AVD 位于 E 盘，以 headless/WHPX 运行。
- 新增独立 i18nQa 构建：app.fieldwatch.i18nqa，可调试，不覆盖手机 app.fieldwatch。仅该构建提供 QA Activity，测试时扫描、在线查询和告警关闭。
- 正常 Debug 保留 app.fieldwatch、现有签名、1.1.18/28，以及 R8 混淆与资源压缩。普通 APK manifest 已检查，未包含 QA Activity，未启用 debuggable。
- 最终全量 JVM 回归：39 个测试类、460 项，失败/错误/跳过均 0。API 35 设备测试：6 项通过，34.355 秒。Lint：0 错误、56 警告；没有添加忽略基线。
- 中文字符串：2,902 条；数量资源：3 组。领域/内置显式绑定：2,114 项。英中覆盖与格式参数检查、绑定工具和 git diff --check 通过。

剩余 Lint 警告涉及既有图标、API 判断、排版建议及 application 配置上下文缓存；未为中文工作扩大无关修复。SDK 工具 XML 版本差异仍有构建提示，未阻止构建。

## PDF 与文本验收

使用真实 Android DebriefReport、SitDiff 和 DebriefPdf.write，在隔离应用内生成虚构样本，再通过正常调试访问取回本地。没有导出手机真实监测数据，也没有发送给联系人、云盘、WPS 或外部 AI。

| 每种语言样本 | 页数 | 检查 |
| --- | --- | --- |
| empty / ordinary / decoded | 2 / 2 / 2 | 空数据、普通信号源、DULT 字段/枚举/说明 |
| long / many-path / retitled | 5 / 5 / 5 | 长中文与混排、16 个模拟信号源、修改关注段标题仍保持卡片结构 |
| compare | 3 | 双会话列表、路径和柱状图 |
| overflow | 7 | 极长关注标题和 220 次重复备注，末尾 QA END OF NOTE 保留 |
| transit | 4 | 实际包含停留和途经的路径，图表与文字说明对应 |

18 份 PDF，共 70 页；全部以 110 dpi 渲染，并在 26 张联系表中逐页看图。未发现中文缺字、重叠、正文越界或页脚覆盖。极端长备注可以跨页，分页为保留完整内容而可能留下空白。正文文本提取仅用于辅助核对末尾标记；没有用文本检查替代视觉验收。

英中详情分享及 AI 导出使用相同虚构数据。中文 AI 提示明确要求简体中文回答，保留标识符与观察备注原文；解码内容、假设语气和免责声明已检查。没有为了验收请求外部 AI。

本地可复查中间结果：`.gradle/i18n/pdfs/`、`.gradle/i18n/pdf-render/manifest.json`、逐页 PNG 与联系表；均在忽略目录，测试可以重新生成。

## 设备矩阵

| 项目 | 结论与范围 |
| --- | --- |
| 中文/English/跟随系统 | API 35 测试通过；手机 Android 10 第一轮已有切换及重启偏好证据，最终包中文页面补验通过 |
| Android 13+ 系统语言入口同步 | API 35 LocaleManager 与 AppCompat 双向同步测试通过；实际系统设置入口完整人工点击流程尚未覆盖 |
| 未支持语言 | 法语应用配置回退英文资源/日期/语音上下文通过；未修改用户手机系统语言 |
| 新建特征、预设、会话命名 | 实际页面旋转重建和语言切换保持未保存字段；取消不提交，通过 |
| 解码标签、枚举及备注 | 实际内置 DULT 编辑、重建、语言切换、取消后原始记录完全相等，通过 |
| 只改内置数字字段 | minPeers 改为 7 保存，名称/说明/解码原始值保持，随后还原 QA 记录，通过 |
| 真实后台进程回收 | API 35，进程 6255 → 回收 → 6516，New SignatureQAProcess 草稿恢复；config.json 不含 QAProcess，已取消，通过 |
| 其他编辑器全部进程回收组合 | 受限：没有逐一测完设备命名、解码、预设、会话的所有回收组合 |
| 小屏与较大字体 | 模拟器 360dp、1.3 倍字体，中文设置入口/按钮/说明换行正常；原 1080×2400/420dpi/1.0 已恢复；未覆盖所有页面与无障碍实际朗读 |
| 中文夜间模式 | 模拟器设置页检查，临时开关已恢复；完整页面矩阵受限 |
| 手动停止后切换语言 | Android 10 第一轮通过；扫描/冻结/真实关注告警全部组合仍受限 |
| TTS 可用、失败及缺失 | 可控替身验证语言负返回不发声、成功/失败/异常及旧回调失效；最终手机点击“测试告警”，用户确认“有蜂鸣，也听到清晰中文”。真实关注命中组句及播放中切换的完整实际听测未覆盖 |
| 通知 | 最终包实测“Fieldwatch 扫描中”“11 个 Wi-Fi · 18 个 BLE · 2 个已识别”及“停止”；关注告警/已有频道设置完整矩阵未覆盖 |
| 蓝牙关闭、系统挂起、扫描失败 | 文案与保护逻辑已检查；未逐个制造全部真实硬件/系统异常 |

## 最终包与手机

手机为华为 HMA_AL00，Android 10 / API 29。最终普通包于 2026-10-04 18:27:30 覆盖安装成功；读取安装后的公开 APK 文件计算 SHA-256，与本地最终文件完全一致。用户在手机自行完成华为验证，没有提供密码。实际测试告警已触发，用户确认“有蜂鸣，也听到清晰中文”。这是测试短句听测证据，不替代所有真实关注命中及语言变化组合。

最终包中文设置、报告、新建名称“新建识别特征”、详情范围“-90 至 -30 dBm”及扫描通知实测通过。新建草稿已取消，没有保存规则、会话或修改设备备注。详情推断中的原始匹配名称（例如 Microsoft Device）作为匹配证据原文保留，对应目录说明采用中文显示覆盖。全过程不卸载、不清数据、不导出真实报告；原有告警/蜂鸣/语音开关均为开启，未修改；临时类别展开已收起，充电常亮恢复原值 0。

最终普通 APK：`app/build/outputs/apk/debug/app-debug.apk`；2026-10-04 18:12:42 生成，11,819,438 字节；SHA-256：`EC4BB1134674B9C31BDCC9CB51BD71AEF149BF4C42E3FC4F1C962631F699D3A1`。最后一处文字修复后构建/单元/Lint 通过，相关报告设备测试 1 项复验通过（9.244 秒）；详情和 AI 样本已重新取回，18 份 PDF 校验值均未变化，因此已有 70 页视觉证据仍对应最终报告内容。

## 重现方式

```powershell
# 使用已配置的 E 盘开发环境；普通包与隔离包分别生成。
.\gradlew.bat assembleDebug assembleI18nQa assembleI18nQaAndroidTest testDebugUnitTest lintDebug --console=plain --no-daemon
node tools/update-i18n-resources.mjs --check
git diff --check
# 明确使用隔离模拟器，避免装到真实手机同包名应用。
E:\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/i18nQa/app-i18nQa.apk
E:\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/androidTest/i18nQa/app-i18nQa-androidTest.apk
E:\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell am instrument -w app.fieldwatch.i18nqa.test/androidx.test.runner.AndroidJUnitRunner
```

设备测试源：`app/src/androidTest/java/app/fieldwatch/i18n/ChineseDeviceTest.kt`；测试入口：`app/src/i18nQa/`；单元报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。
