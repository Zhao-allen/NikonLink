# NikonLink / 尼康相机无线传输助手

Android app for wireless transfer of photos/videos from Nikon cameras via WiFi (PTP-IP), with BLE activation.
安卓应用，通过 WiFi (PTP-IP) + 蓝牙 BLE 激活，实现尼康相机照片/视频的无线传输。

---

## Target Cameras / 支持的相机

| Series 系列 | Models 型号 |
|-------------|-------------|
| Z Series Mirrorless / Z系列无反 | Z9, Z8, Z6III, Zf, Z50II, Z50, Z7II, Z6II, **Z5** |
| DSLR / 单反 | D780, D850, D6 |

Android 9+ (API 28). Tested on Xiaomi K80 (Android 14) with Nikon Z5.
已在小米 K80 (Android 14) + 尼康 Z5 上测试。

---

## How It Works / 工作原理

Nikon Z-series cameras require a **BLE handshake to activate the WiFi PTP-IP service**. The app handles this automatically:
尼康 Z 系列相机需要 **蓝牙握手来激活 WiFi PTP-IP 服务**。应用会自动完成这个流程：

```
手机连相机WiFi → BLE扫描发现相机 → GATT连接(无需PIN) → PTP-IP激活 → WiFi高速传输
Phone → Camera WiFi → BLE Scan → GATT Connect (no PIN) → PTP-IP Active → Transfer
```

If BLE is unavailable, falls back to direct PTP-IP on port 15740.
如果蓝牙不可用，自动回退到直连 PTP-IP (端口 15740)。

---

## Connecting Your Camera / 连接相机

### Quick Start / 快速开始

1. **相机**: Menu → 连接到智能设备 → Wi-Fi 连接 → 建立 Wi-Fi 连接
2. **相机屏幕** 显示 SSID (如 `CCCC_7642966`) 和密码
3. **手机**: 设置 → Wi-Fi → 连接相机热点
4. **打开 NikonLink** → 点击 "CONNECT TO CAMERA"
5. 等待自动 BLE 激活 + PTP-IP 握手 (最多 10 秒)

### Status / 状态说明

| 状态 Status | 含义 Meaning |
|-------------|-------------|
| "Connect to Camera WiFi" | 手机未连相机WiFi / Phone not on camera network |
| "On Camera WiFi: XXX" (绿/Green) | 已连上相机WiFi，可以连接 |
| "Activating Camera" | 正在扫描BLE / BLE scanning |
| "Activating PTP-IP Service" | 正在蓝牙GATT连接 / GATT connecting |
| "Connected" (绿/Green) | 已连接—切换到Browse标签浏览文件 |

---

## Architecture / 架构

```
NikonLink/
├── app/                    壳工程 (MainActivity, CameraFragment)
├── core-common/            共享类型 (ConnectionState, FileType, Result)
├── core-data/              Room数据库 + DataStore 设置
├── feature-connection/     BLE/GATT + WiFi + PTP-IP + ConnectionManager
├── feature-browser/        文件浏览 (目录树, 缩略图, 多选)
├── feature-transfer/       传输队列 + WorkManager 后台下载
└── feature-remote/         遥控拍摄 (预留/placeholder)
```

**技术栈 / Stack**: Kotlin · XML ViewBinding · Hilt DI · Coroutines + Flow · Room · WorkManager · Coil · OkHttp

**架构模式**: MVVM + Clean Architecture (UI → Domain → Data 单向依赖)

---

## Key Components / 核心组件

| Component 组件 | File 文件 | Purpose 功能 |
|---------------|-----------|-------------|
| ConnectionManager | `feature-connection/.../ConnectionManager.kt` | BLE+WiFi 状态机编排 |
| PtpIpClient | `feature-connection/.../PtpIpClient.kt` | PTP-IP over TCP (端口 15740) |
| BleGattManager | `feature-connection/.../BleGattManager.kt` | BLE直连 (无需PIN配对) |
| CameraFragment | `app/.../ui/camera/CameraFragment.kt` | 相机连接界面 |
| BrowserFragment | `feature-browser/.../BrowserFragment.kt` | 文件浏览界面 |
| TransferWorker | `feature-transfer/.../TransferWorker.kt` | WorkManager 后台传输 |

---

## Build / 编译

### Prerequisites / 环境要求
- Android Studio 2025+ 或 JDK 17 + Android SDK 34
- Gradle 8.4 (wrapper 已包含)

### Commands / 命令
```bash
# 编译 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.nikonlink/.MainActivity
```

---

## Current Status / 当前进度

| Feature 功能 | Status 状态 |
|-------------|-------------|
| BLE 相机发现 / BLE camera discovery | ✅ 可用 |
| BLE GATT 连接 (免PIN) / BLE GATT (no PIN) | ✅ 可用 |
| PTP-IP 握手 / PTP-IP handshake | ⚠️ 调试中 — Z5协议包格式待确认 |
| 文件浏览界面 / File browsing UI | ✅ 就绪 |
| 文件传输 (WorkManager) / File transfer | ✅ 就绪 |
| 遥控拍摄 / Remote camera control | 🔜 计划中 |

---

## Debug Record / 调试记录

在小米 K80 + 尼康 Z5 上调试的关键发现：

1. **Z5 的 BLE 必须用 TRANSPORT_LE 模式**：系统配对 (createBond) 会提示 PIN 码错误。绕过系统配对，直接用 GATT 连接可解决。
2. **BLE 广告有时间窗口**：相机仅在配对模式下广播约 60-120 秒。应用加 10 秒扫描超时避免卡死。
3. **PTP-IP 端口 15740 依赖 BLE 激活**：相机 WiFi 开着但端口不响应，需 BLE GATT 连接后才能激活。
4. **SnapBridge UUID 过滤太严**：去掉 UUID 过滤扫描所有 BLE 设备，通过名称匹配相机。

---

## Known Issues / 已知问题

- **PTP-IP 握手协议**: Z5 接受 15740 端口的 TCP 连接，但不响应当前的 init 包格式。待确认尼康 Z 系列的正确 PTP-IP 握手协议。
- **BLE 广播窗口**: 相机仅配对模式时广播 ~60-120 秒。应用已设 10 秒扫描超时 + 自动回退。
- **WiFi 依赖**: 传输期间手机必须保持在相机 WiFi 热点上。

---

## License

MIT
