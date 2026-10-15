# SamsungDuo 开发文档

面向二次开发与问题定位。用户向的安装与设置说明见 [README](../README.md)。

## 项目构成

| 项 | 值 |
|---|---|
| `applicationId` / `namespace` | `com.jsj.samsungduo` |
| 模块入口 | `com.jsj.samsungduo.SamsungDuoModule`（`META-INF/xposed/java_init.list`） |
| Application | `com.jsj.samsungduo.SamsungDuoApp`（`AndroidManifest.xml`） |
| Xposed API | libxposed 102，静态作用域，作用域 `com.android.systemui` |
| 构建 | Gradle 9.6 + AGP 9.4.1 + Kotlin 2.3.21 + Compose + Miuix 0.9.4，compileSdk 37 / targetSdk 36 / minSdk 29 |

## 源码结构

```
app/src/main/java/com/jsj/samsungduo/
├─ SamsungDuoModule.java   入口（extends XposedModule，按 java_init.list 实例化）
├─ SamsungDuoApp.kt        Application：持有 XposedService，供设置页读写远程偏好
├─ TrioHooks.java          所有 hook：状态栏/锁屏/下拉三处就绪回调 + 取色
├─ TrioOverlay.java        叠加层：挂树抢位子 / 独立窗口两条路，陪衬与平移
├─ TrioState.java          取数：电池广播、信号、Wi-Fi、温度、取色与去抖
├─ TrioRenderer.java       绘制：圆环/矩形两种排布，环内环外各槽位
├─ TrioGeometry.java       几何常量与公式（120x120 设计单位，全部来自实机实测）
├─ TrioAppearance.java     把用户设置解析成"这一帧画什么"，UI 与渲染共用一个真源
├─ TrioSettings.java       配置快照：读侧 clamp、Bundle 序列化、与设置页互通
├─ TrioConfig.java         配置快照的持有者 + 变更广播
├─ TrioPreviewView.java    设置页顶部的实时预览格
├─ ShadeDiagnostics.java   下拉面板结构 dump（只读，定位挂载点用）
├─ Refl.java               反射小工具
├─ Prefs.java              所有偏好键名、默认值、上下限（唯一真源）
└─ ui/                     设置页（Compose + Miuix）
   ├─ MainActivity.kt      导航与四个分组
   ├─ SettingsScreen.kt    全部设置项与关于页（含更新卡片）
   ├─ SettingsRepository.kt 写入漏斗：本地 commit → 推远程偏好 → 广播
   ├─ UpdateController.kt  在线更新：检查 / 下载 / 安装
   └─ RestartController.kt  root 重启系统界面
```

## 构建

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat :app:assembleRelease --offline
```

`--offline` 能用是因为 `.tools/maven/` 里 vendored 了 libxposed 的制品（该目录跟着仓库走）。
签名用 `.tools/debug.keystore`（口令 `android`）—— 与已装机版本同一把钥匙，所以能原地覆盖升级。

版本号由 tag 注入，本地构建走 `app/build.gradle.kts` 里 checked-in 的默认值：

```powershell
.\gradlew.bat :app:assembleRelease --offline -PsamsungduoVersionName=9.5 -PsamsungduoVersionCode=95
```

## 实现要点

### 三处挂载，都靠"抢位子"

状态栏 / 锁屏 / 下拉各有一个 `TrioOverlay` 实例（`sLiveMap` 以 root View 为键）。
优先挂进系统图标那一行（`system_icons` / `hover_system_icons_container`）参与排布，
挂不上才退回 2012 独立小窗口。

- **硬护栏**：父容器类名命中 `StatusIconContainer` 直接拒绝挂载 —— 它的 onMeasure/onLayout
  只认自己的子 View 类型，强行挂进去会崩 SystemUI。
- **右缘钉死**：实机几何 dump 显示我们这一组是行里最后一组子 View、行的右缘恒等于屏幕右缘。
  所以「整组左右平移」必须走两条路：margin（推/放原生图标）+ translationX（平移整组），
  只改一条要么原生图标动而我们不动，要么我们直接压到原生图标上。

### 取色：实时读宿主树

系统暗色调度器把回调推到状态栏那棵树，锁屏/下拉基本收不到，所以不做回调归因，
而是每帧从自己所在的那棵树上找探针（`StatusBarIconView` / `BatteryMeterView` / `NetspeedView`）
读它当前的 tint —— 天然做到"下拉取下拉、状态栏取状态栏、锁屏取锁屏"。
探针带缓存 + 500ms 限频重搜。

### 信号格数与快充

- 格数走隐藏 API `SignalStrength.getVendorLevel()`（0–5 级），失败再退 `getLevel()`。
- 快充读广播 extra `charger_type`，≥3 为超级快充三档，点亮琥珀闪电。

### 超过 100% 的尺寸

关着「独立窗口绘制」时三合一挂在状态栏行里，放大只能逐层放开 `clipChildren`，
而 `clipChildren` 对 **Android 窗口边界**无效 —— 所以最大 100%。
开着时是独立小窗口，可以把窗口自身加高，于是能到 130%（再大就被窗口边界裁）。

### 防烧屏

双正弦（周期取非整数比，利萨如轨迹）+ `setTranslationX/Y`，只挪绘制位置、不重新排布。
关掉防烧屏后每秒循环会停（v74 省电），所以「整组平移」的基准位移放在 `onDraw` 里补。

## 观测

无 adb 时靠 `/sdcard/SamsungDuo_debug.log`（追加写）。关键日志前缀：`overlay:`、
`geometry `、`tint:`、`sigcfg`。新增前缀要同步加进 `SamsungDuoModule.isCritical()` 白名单，
否则会被 100ms 限流静默吃掉。

## 发布

见 [README](../README.md#发布与在线更新)。发版脚本 `release.ps1` 会构建、用 aapt2 回读
APK 里的版本号核对、打 tag、建正式 Release 并上传 APK。
