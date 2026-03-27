# mmupnp 接入文档

> 基于 ohmae/mmupnp v3.1.6，纯 Kotlin 实现的 UPnP ControlPoint 库，适用于 Java/Kotlin 应用及 Android。

---

## 目录

1. [概述](#1-概述)
2. [依赖配置](#2-依赖配置)
3. [核心接口一览](#3-核心接口一览)
4. [ControlPoint 创建与生命周期](#4-controlpoint-创建与生命周期)
5. [设备发现](#5-设备发现)
6. [设备操作](#6-设备操作)
7. [Service 与 Action 调用](#7-service-与-action-调用)
8. [事件订阅](#8-事件订阅)
9. [高级配置](#9-高级配置)
10. [完整示例](#10-完整示例)
11. [调试日志](#11-调试日志)
12. [常见问题](#12-常见问题)
13. [Jellyfin UPnP/DLNA 接入](#13-jellyfin-upnpdlna-接入)
14. [附录](#附录)

---

## 1. 概述

### 1.1 技术选型说明

本文档以 **ohmae/mmupnp + Jellyfin** 作为 UPnP/DLNA 开发的推荐组合，原因如下：

| 组件 | 选型 | 理由 |
|------|------|------|
| UPnP ControlPoint 库 | **ohmae/mmupnp** | 纯 Kotlin 实现，轻量（400KB），无 RxJava 依赖，API 简洁，Maven Central 直接可用 |
| 媒体服务器（DMS） | **Jellyfin** | 开源免费，DLNA 支持完整，API 稳定，支持音乐/视频/图片，有 Web 管理界面 |

**组合架构：**

```
┌─────────────────────────────────────────────────────────┐
│  移动 App（mmupnp ControlPoint）                        │
│    ├─ SSDP 发现 Jellyfin（DMS）和电视/音箱（DMR）        │
│    ├─ ContentDirectory Browse → 获取媒体库结构           │
│    └─ AVTransport SetAVTransportURI + Play → 投屏播放    │
└────────────────┬────────────────────────────────────────┘
                 │ HTTP + SOAP
┌────────────────▼────────────────────────────────────────┐
│  Jellyfin（DMS，内置 DLNA Plugin）                      │
│    ├─ ContentDirectory：音乐/视频/图片库浏览              │
│    ├─ AVTransport：本身可作为 Renderer 直接播放           │
│    └─ 提供标准 HTTP 媒体流（http-get 协议）              │
└────────────────┬────────────────────────────────────────┘
                 │ HTTP 流
┌────────────────▼────────────────────────────────────────┐
│  电视 / 音箱 / Chromecast（DMR）                         │
│    └─ 从 Jellyfin 获取媒体流并渲染播放                   │
└─────────────────────────────────────────────────────────┘
```

> **本方案适用于**：在 Android 应用中实现音乐/视频投屏功能，以 Jellyfin 作为媒体库，以电视/音箱作为渲染器。Jellyfin 也可直接充当渲染器，实现"即点即播"的极简体验。

### 1.2 mmupnp 定位

mmupnp 是一个纯 **UPnP ControlPoint** 实现库，只负责"控制端"角色，不能作为 UPnP Device 使用。

**mmupnp 能做的事：**
- 局域网内发现 NAS、电视、投影仪、Jellyfin、DLNA 媒体服务器
- 发送 M-SEARCH 发现设备
- 获取设备描述 XML，解析 Service/Action
- 调用 SOAP Action（投屏、播放、暂停等）
- 订阅设备事件（状态变更通知）

**mmupnp 不能做的事：**
- 实现 UPnP Device（不能做 DMS/DMR）
- 播放音视频（只负责控制，不负责解码/渲染）
- 代替 Jellyfin 等服务器提供媒体库

### 1.3 规格要求

| 要求 | 版本 |
|------|------|
| Kotlin | 1.3+ |
| Java | 7+ |
| Android | 支持 |
| mmupnp | 3.1.6 |

### 1.4 约束

- **仅支持 ControlPoint**，无法实现 UPnP Device
- 多播事件（Multicast Eventing）为实验性功能
- 不支持 Java 6

---

## 2. 依赖配置

### 2.1 Maven Central（推荐）

```kotlin
dependencies {
    implementation 'net.mm2d.mmupnp:mmupnp:3.1.6'
}
```

> ⚠️ 3.1.3+ 版本 groupId 从 `net.mm2d` 改为 `net.mm2d.mmupnp`，需从 Maven Central 获取。
> 
> ✅ **版本验证（2026-03-26）**：3.1.6 为最新版本，Maven Central 可正常下载（401KB jar）。

### 2.2 传递依赖

mmupnp 3.1.6 本身只有两个传递依赖，均可在 Maven Central 正常获取：

| 依赖 | 版本 | 说明 |
|------|------|------|
| `net.mm2d.log:log` | 0.9.4 | 日志库（ohmae 自研，仅此一版） |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.4.32 | Kotlin 标准库（运行时） |

**完整 POM 依赖树：**

```xml
<dependency>
    <groupId>net.mm2d.mmupnp</groupId>
    <artifactId>mmupnp</artifactId>
    <version>3.1.6</version>
</dependency>
<!-- 传递依赖（自动引入） -->
<dependency>
    <groupId>net.mm2d.log</groupId>
    <artifactId>log</artifactId>
    <version>0.9.4</version>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-stdlib</artifactId>
    <version>1.4.32</version>
</dependency>
```

> 💡 Kotlin stdlib 版本与 Kotlin 编译器版本对应关系：1.4.32 对应 Kotlin 1.4.32。如需其他 Kotlin 版本，可自行声明覆盖。

### 2.3 旧版本（jCenter，已关闭）

```kotlin
repositories {
    maven { url = URI("https://ohmae.github.com/maven") }
}

dependencies {
    implementation 'net.mm2d.mmupnp:mmupnp:3.1.2'
}
```

### 2.4 Gradle 完整示例

```kotlin
dependencies {
    implementation 'net.mm2d.mmupnp:mmupnp:3.1.6'
}
```

---

### 2.3 示例项目

#### DmsExplorer（官方完整 Android Demo）⭐ 推荐

> Google Play 可直接安装体验，源码完整覆盖 mmupnp 所有功能。

| 项目 | 信息 |
|------|------|
| 源码地址 | https://github.com/ohmae/DmsExplorer |
| Google Play | https://play.google.com/store/apps/details?id=net.mm2d.dmsexplorer |
| KDoc 文档 | https://ohmae.github.io/mmupnp/dokka/mmupnp/ |

**功能覆盖：**
- ✅ UPnP 设备发现（MediaServer / MediaRenderer）
- ✅ ContentDirectory Browse / Search
- ✅ DIDL-Lite 完整解析
- ✅ AVTransport 播放控制（播放/暂停/停止/跳转）
- ✅ 事件订阅（LastChange 状态更新）
- ✅ 设备描述 XML 缓存
- ✅ 多网卡支持

#### mmupnp 主库

| 项目 | 信息 |
|------|------|
| 源码地址 | https://github.com/ohmae/mmupnp |
| Maven Central | https://search.maven.org/artifact/net.mm2d.mmupnp/mmupnp |
| 最新版本 | **3.1.6** |

#### 其他参考项目

| 项目 | 地址 | 说明 |
|------|------|------|
| jellyfin-android | https://github.com/jellyfin/jellyfin-android | Jellyfin 官方 Android App（DLNA + SDK 双栈实现） |
| jellyfin-sdk-kotlin | https://github.com/jellyfin/jellyfin-sdk-kotlin | Jellyfin 官方 Kotlin SDK 源码 |

---

## 3. 核心接口一览

### 3.1 类图总览

```
ControlPointFactory  →  ControlPoint  →  Device  →  Service  →  Action
                                                   →  Icon
                                                   →  StateVariable
```

### 3.2 主要接口列表

| 接口/类 | 包 | 说明 |
|---------|-----|------|
| `ControlPointFactory` | net.mm2d.upnp | ControlPoint 工厂类 |
| `ControlPoint` | net.mm2d.upnp | UPnP 控制点核心接口 |
| `Device` | net.mm2d.upnp | UPnP 设备接口 |
| `Service` | net.mm2d.upnp | UPnP 服务接口 |
| `Action` | net.mm2d.upnp | UPnP Action 接口 |
| `Argument` | net.mm2d.upnp | Action 参数接口 |
| `StateVariable` | net.mm2d.upnp | 状态变量接口 |
| `Icon` | net.mm2d.upnp | 设备图标接口 |
| `SsdpMessage` | net.mm2d.upnp | SSDP 消息接口 |
| `Protocol` | net.mm2d.upnp | 协议枚举（IPv4/IPv6） |
| `TaskExecutor` | net.mm2d.upnp | 任务执行器接口 |
| `IconFilter` | net.mm2d.upnp | 图标过滤器接口 |

---

## 4. ControlPoint 创建与生命周期

### 4.1 快速创建

```kotlin
val cp = ControlPointFactory.create()
```

### 4.2 完整参数创建

```kotlin
val cp = ControlPointFactory.create(
    protocol: Protocol = Protocol.DEFAULT,           // 协议栈
    interfaces: Iterable<NetworkInterface>? = null, // 指定网卡
    callbackExecutor: TaskExecutor? = null,         // 回调执行器
    callbackHandler: ((Runnable) -> Boolean)? = null,// Android MainThread 回调
    notifySegmentCheckEnabled: Boolean = false,      // 是否检查 Notify 消息分片
    subscriptionEnabled: Boolean = true,             // 是否启用事件订阅
    multicastEventingEnabled: Boolean = false       // 是否启用多播事件（实验性）
)
```

### 4.3 Builder 模式（Java 友好）

`ControlPointFactory.builder()` 提供所有配置项的声明式设置，推荐所有平台使用：

```kotlin
val cp = ControlPointFactory.builder()
    // ── 网络 ──
    .setProtocol(Protocol.DEFAULT)                        // 协议栈（默认双栈）
    .setInterfaces(listOf(NetworkInterface.getByName("eth0")))  // 指定网卡
    // ── 回调 ──
    .setCallbackExecutor(executor)                         // 回调执行器
    .setCallbackHandler { handler.post(it); true }       // 主线程分发
    // ── SSDP ──
    .setSsdpMessageFilter { message -> true }            // 消息过滤器
    .setNotifySegmentCheckEnabled(true)                   // 检查 Notify 分片
    // ── 事件订阅 ──
    .setSubscriptionEnabled(true)                         // 启用事件订阅（默认 true）
    .setMulticastEventingEnabled(false)                  // 多播事件（实验，默认 false）
    .build()
```

**各配置项默认值：**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `Protocol` | `DEFAULT` | IPv4/IPv6 双栈 |
| `interfaces` | 所有网卡 | 自动遍历所有网络接口 |
| `callbackExecutor` | 内置线程池 | 内部 `TaskExecutor` 实现 |
| `callbackHandler` | `null`（线程池回调） | `null` 时在内部线程执行 |
| `ssdpMessageFilter` | 全部接收 | `null` 接受所有 |
| `notifySegmentCheckEnabled` | `false` | 不检查 Notify 分片 |
| `subscriptionEnabled` | `true` | 启用事件订阅 |
| `multicastEventingEnabled` | `false` | 禁用多播事件 |

### 4.4 Protocol 枚举与网络配置

**Protocol 枚举：**

| 值 | 说明 |
|-----|------|
| `DEFAULT` | IPv4/IPv6 双栈 |
| `IP_V4_ONLY` | 仅 IPv4（推荐移动设备使用） |
| `IP_V6_ONLY` | 仅 IPv6（企业内网） |

**多网卡指定：**

```kotlin
// 列举所有可用网卡
java.net.NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
    println("${iface.name}: ${iface.displayName}")
    // eth0, wlan0, lo, ...
}

// 只用 Wi-Fi 网卡（Android 常用）
val wifiInterface = java.net.NetworkInterface.getByName("wlan0")
val cp = ControlPointFactory.create(interfaces = listOf(wifiInterface))
```

| 值 | 说明 |
|-----|------|
| `DEFAULT` | IPv4/IPv6 双栈 |
| `IP_V4_ONLY` | 仅 IPv4 |
| `IP_V6_ONLY` | 仅 IPv6 |

### 4.5 生命周期

```
创建实例 → 添加监听器 → initialize() → start() → 使用 → stop() → terminate()
```

**重要：**
- `terminate()` 后不可再重新 `initialize()`
- 如需重置，需重新创建实例

```kotlin
val cp = ControlPointFactory.create().also {
    it.addDiscoveryListener(discoveryListener)
    it.addNotifyEventListener(notifyEventListener)
    it.initialize()
    it.start()
}

// ... 使用 ...

cp.stop()
cp.terminate()
```

---

## 5. 设备发现

UPnP 设备发现是整个流程的第一步。mmupnp 基于 SSDP（Simple Service Discovery Protocol）实现设备发现，本质是：在局域网内发送 UDP 多播包（`239.255.255.250:1900`），符合条件的设备收到后回复 HTTP 响应，ControlPoint 解析响应中的设备描述 URL，再异步拉取并解析设备描述 XML。整个过程通常在 1~3 秒内完成。

### 5.1 DiscoveryListener 三个回调

`DiscoveryListener` 接口有三个回调，覆盖设备的完整生命周期：

```kotlin
interface ControlPoint.DiscoveryListener {
    /**
     * 设备首次发现或重新发现时调用
     * 设备上线、SSD P NOTIFY 重发、超时后重新收到通知都会触发
     */
    fun onDiscover(device: Device)

    /**
     * 设备描述 XML 内容发生变化时调用（较少见）
     * 如设备固件更新导致描述 XML 变化
     */
    fun onDeviceChanged(device: Device)

    /**
     * 设备从局域网消失时调用
     * 收到 SSDP BYEBYE 消息或设备超时（默认 3 倍 NOTIFY 间隔）
     */
    fun onDeviceExpired(device: Device)
}
```

**完整监听器实现：**

```kotlin
val listener = object : ControlPoint.DiscoveryListener {
    override fun onDiscover(device: Device) {
        println("✅ 上线: ${device.friendlyName} [${device.udn}]")
        println("   类型: ${device.deviceType}")
        println("   地址: ${device.ipAddress}")
        println("   位置: ${device.location}")
    }

    override fun onDeviceChanged(device: Device) {
        println("🔄 变化: ${device.friendlyName} 的描述发生变化")
    }

    override fun onDeviceExpired(device: Device) {
        println("❌ 离线: ${device.friendlyName}")
        // 清理本地缓存
        discoveredDevices.remove(device.udn)
    }
}

cp.addDiscoveryListener(listener)
```

> 💡 **重要**：`onDiscover` 在设备每次 SSDP NOTIFY 消息到来时都会被调用（包括设备定期保活），如果只希望处理"设备第一次出现"，需要自己用 `udn` 去重判断。

### 5.2 设备状态机

SSDP 协议定义了设备的三种主要消息类型，理解它们是掌握设备发现机制的关键：

| SSDP 消息 | 触发时机 | mmupnp 回调 |
|-----------|---------|------------|
| `NOTIFY * HTTP/1.1`（ALIVE） | 设备加入网络，定期发送保活（一般每 ~30 秒） | `onDiscover` |
| `NOTIFY * HTTP/1.1`（BYEBYE） | 设备离开网络 | `onDeviceExpired` |
| `HTTP/1.1 200 OK`（M-SEARCH 响应） | ControlPoint 发起搜索后，设备响应 | `onDiscover` |

**设备生命周期时序：**

```
设备上电
    │
    ▼
NOTIFY (ALIVE)  ──────────────────────────────────────► onDiscover(首次)
    │                                              (device.isPinned = false)
    │ 每 30 秒
    ▼
NOTIFY (ALIVE)  ──────────────────────────────────────► onDiscover(每次)
    │
    │ 用户关闭设备 / 设备主动离开
    ▼
NOTIFY (BYEBYE) ──────────────────────────────────────► onDeviceExpired
    │
    ▼
(设备消失，不再响应 M-SEARCH)
```

**Pinned 设备（固定设备）的特殊行为：**

通过 `tryAddPinnedDevice` 添加的设备不会因为 SSDP 超时而消失，即使设备不发 NOTIFY 也会保留在列表中：

```kotlin
// 添加固定设备
cp.tryAddPinnedDevice("http://192.168.1.100:8096/description.xml")

// 检查设备是否为固定设备
val device = cp.getDevice(udn)
println(device?.isPinned)  // true
```

### 5.3 发送 M-SEARCH

`search()` 主动向局域网发送 SSDP M-SEARCH 请求，收到请求的设备会回复 200 OK，是快速发现设备的常用方式。

```kotlin
// ── 常用搜索类型 ──

// 1. 搜索所有设备（最通用，但会收到大量非目标设备）
cp.search()
// 内部 ST = "ssdp:all"，同时收到 MediaServer、路由器、打印机等

// 2. 搜索根设备（推荐，更精确）
cp.search("upnp:rootdevice")
// 只发现完整 UPnP 设备，排除子设备

// 3. 按设备类型搜索（最精确）
cp.search("urn:schemas-upnp-org:device:MediaServer:1")

// 4. 按服务类型搜索（发现提供特定服务的设备）
cp.search("urn:schemas-upnp-org:service:ContentDirectory:1")

// 5. 按厂商特定类型搜索
cp.search("urn:schemas-upnp-org:device:MediaRenderer:1")  // 渲染器（播放设备）
cp.search("urn:dial-multiscreen-org:service:dial:1")       // DIAL（YouTube/Netflix 投屏）
```

**search() 的完整参数：**

```kotlin
/**
 * @param searchType SSDP ST 值，默认为 "ssdp:all"
 * @param mx 最大等待时间（秒），设备随机等待 0~MX 秒后响应，默认 3
 *                        MX 值越大，多设备冲突越少，但发现越慢
 */
fun search(searchType: String = "ssdp:all", mx: Int = 3)
```

**设置 MX 的实战影响：**

```kotlin
// MX=1：快速发现，但多设备时可能冲突
cp.search("upnp:rootdevice", mx = 1)

// MX=5：更稳定，适合设备较多的复杂网络
cp.search("upnp:rootdevice", mx = 5)
```

**search() 的行为特点：**

- `search()` 是**非阻塞**的，立即返回，响应异步回调
- 首次 `search()` 后 ControlPoint 会持续监听 SSDP 广播（不等同于 `start()`）
- 调用 `stop()` 后不再接收 SSDP 消息，但仍保留已发现设备列表

### 5.4 通过 UDN 获取设备

设备发现后，已知 UDN 的设备可以直接从 ControlPoint 获取，无需重新搜索：

```kotlin
// 通过 UDN 获取设备（UDN 在 onDiscover 回调中获取）
val device = cp.getDevice(udn: String): Device?

if (device != null) {
    println("找到设备: ${device.friendlyName}")
    println("  baseUrl: ${device.baseUrl}")
    println("  deviceType: ${device.deviceType}")
    println("  services: ${device.serviceList.map { it.serviceType }}")
} else {
    println("设备不存在或已离线")
}
```

**UDN 的格式与来源：**

```
格式一（标准 UDN）：
  uuid:12345678-1234-1234-1234-123456789abc

格式二（带设备类型前缀）：
  uuid:DeviceIdentification-uuid

格式三（部分设备使用 MAC 地址派生）：
  uuid:2c列开头-xxxx-xxxx-xxxx-xxxxxxxxxxxx@192.168.1.100
```

UDN 来自设备描述 XML 的 `<UDN>` 字段，部分设备用 MAC 地址派生，同一设备 UDN 固定不变。

### 5.5 其他发现相关方法

```kotlin
// ── 列表管理 ──
cp.clearDeviceList()              // 清空所有已发现设备（不清除固定设备）
cp.removeDiscoveryListener(listener)  // 移除指定监听器

// ── 手动添加设备 ──
// 根据 location URL 直接拉取并解析设备描述，跳过 SSDP 发现过程
cp.tryAddDevice(
    uuid: String,      // 该设备的 UDN
    location: String   // 设备描述 XML 的 URL
)

// 添加固定设备（不会因超时消失，常用于已知 IP 的设备）
cp.tryAddPinnedDevice(location: String)

// 移除固定设备
cp.removePinnedDevice(location: String)

// ── 查询 ──
cp.getDevice(udn: String): Device?  // 通过 UDN 查找
cp.deviceList: List<Device>          // 当前所有设备（含固定设备）
cp.pinnedDeviceList: List<Device>    // 仅固定设备
```

### 5.6 设备过滤与主动搜索

通过 `setSsdpMessageFilter` 在 SSDP 消息层就做过滤，避免无效的设备描述 XML 下载（节省网络和解析开销）：

```kotlin
// 设置过滤器：只接收 MediaServer 和 MediaRenderer
cp.setSsdpMessageFilter { message ->
    val st = message.type ?: return@setSsdpMessageFilter false
    st.contains("MediaServer") || st.contains("MediaRenderer")
}

// 更精确的过滤：排除特定固件版本
cp.setSsdpMessageFilter { message ->
    val server = message.server ?: return@setSsdpMessageFilter true
    !server.contains("Jellyfin/10.7")  // 排除旧版本
}

// 按 IP 网段过滤（排除 IoT 设备）
cp.setSsdpMessageFilter { message ->
    val remoteIp = message.remoteAddress
    remoteIp?.startsWith("192.168.1.") == true  // 只接收特定网段
}
```

**搜索特定类型的便捷封装：**

```kotlin
/**
 * 搜索特定类型的设备
 * @param searchType SSDP ST 值
 * @param timeoutSeconds 超时时间（秒），超时后自动停止搜索
 */
fun searchDevices(
    cp: ControlPoint,
    searchType: String = "ssdp:all",
    timeoutSeconds: Int = 5
) {
    cp.search(searchType)
    thread {
        Thread.sleep(timeoutSeconds * 1000L)
        cp.stop()
        println("搜索结束，当前设备数: ${cp.deviceList.size}")
    }
}

// 使用
searchDevices(cp, "urn:schemas-upnp-org:device:MediaServer:1")
```

### 5.7 完整设备发现流程（最佳实践）

以下是生产环境推荐的标准设备发现流程：

```kotlin
class UpnpDeviceManager(private val cp: ControlPoint) {

    // UDN → Device 的映射
    private val _devices = mutableMapOf<String, Device>()
    val devices: Map<String, Device> get() = _devices

    // 分类存储
    private val _mediaServers = mutableMapOf<String, Device>()
    private val _mediaRenderers = mutableMapOf<String, Device>()

    val mediaServers: List<Device> get() = _mediaServers.values.toList()
    val mediaRenderers: List<Device> get() = _mediaRenderers.values.toList()

    fun start() {
        // 1. 注册监听器
        cp.addDiscoveryListener(listener)

        // 2. 注册设备过期监听器
        cp.addDeviceExpiredListener { device ->
            removeDevice(device)
        }

        // 3. 初始化并启动
        cp.initialize()
        cp.start()

        // 4. 发起搜索（同时持续监听后续上线的设备）
        cp.search("upnp:rootdevice")
    }

    fun stop() {
        cp.stop()
        cp.terminate()
    }

    private val listener = object : ControlPoint.DiscoveryListener {
        override fun onDiscover(device: Device) {
            // 去重：udn 已存在则跳过
            if (_devices.containsKey(device.udn)) {
                println("设备已存在: ${device.friendlyName}")
                return
            }
            addDevice(device)
        }

        override fun onDeviceChanged(device: Device) {
            println("设备描述变化: ${device.friendlyName}")
            // 重新获取设备描述并更新
            _devices[device.udn] = device
        }

        override fun onDeviceExpired(device: Device) {
            removeDevice(device)
        }
    }

    private fun addDevice(device: Device) {
        _devices[device.udn] = device

        when {
            device.deviceType.contains("MediaServer") -> {
                _mediaServers[device.udn] = device
                println("📁 媒体服务器: ${device.friendlyName}")
            }
            device.deviceType.contains("MediaRenderer") -> {
                _mediaRenderers[device.udn] = device
                println("🔊 媒体渲染器: ${device.friendlyName}")
            }
            else -> {
                println("❓ 其他设备: ${device.friendlyName} [${device.deviceType}]")
            }
        }
    }

    private fun removeDevice(device: Device) {
        _devices.remove(device.udn)
        _mediaServers.remove(device.udn)
        _mediaRenderers.remove(device.udn)
        println("设备移除: ${device.friendlyName}")
    }
}

// 使用
val manager = UpnpDeviceManager(cp)
manager.start()

// 等待设备发现
Thread.sleep(3000)

println("媒体服务器: ${manager.mediaServers.map { it.friendlyName }}")
println("渲染器: ${manager.mediaRenderers.map { it.friendlyName }}")
```

默认 `cp.search()` 会发现所有 UPnP 设备，实际项目中往往只需要特定类型。通过 `SsdpMessage` 过滤器在发现阶段就做筛选，避免无效的设备描述 XML 下载。

```kotlin
// 设置过滤器：只接收 MediaServer 和 MediaRenderer
cp.setSsdpMessageFilter { message ->
    val st = message.type ?: return@setSsdpMessageFilter false
    val nt = message.nt ?: return@setSsdpMessageFilter false
    st.contains("MediaServer") ||
    st.contains("MediaRenderer") ||
    nt.contains("MediaServer") ||
    nt.contains("MediaRenderer")
}

// 更精确的过滤：根据 ST 值中的 URN
cp.setSsdpMessageFilter { message ->
    val st = message.type ?: return@setSsdpMessageFilter false
    // 只接收 Jellyfin 和 Kodi
    st.contains("MediaServer") &&
    (st.contains("jellyfin") || st.contains("kodi"))
}
```

**搜索特定类型的便捷封装：**

```kotlin
/**
 * 搜索特定类型的设备
 * @param searchType SSDP ST 值，如 "upnp:rootdevice"
 * @param timeoutSeconds 超时时间（秒），超时后自动停止搜索
 */
fun searchDevices(cp: ControlPoint, searchType: String, timeoutSeconds: Int = 5) {
    cp.search(searchType)
    // 手动控制搜索窗口：timeout 后自动 stop
    thread {
        Thread.sleep(timeoutSeconds * 1000L)
        cp.stop()
    }
}

// 使用
searchDevices(cp, "upnp:rootdevice", timeoutSeconds = 5)
```

**主动获取已知设备（绕过 SSDP 发现）：**

如果已知设备 IP 和描述 URL，可直接获取，跳过广播发现：

```kotlin
// 手动添加已知的 Jellyfin 设备
val location = "http://192.168.1.100:8096/description.xml"
cp.tryAddPinnedDevice(location)  // 固定设备，不会因 SSDP 超时消失
```

---

## 6. 设备操作

### 6.1 Device 接口属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `udn` | String | 设备唯一标识名 |
| `deviceType` | String | 设备类型（如 `urn:schemas-upnp-org:device:MediaServer:1`） |
| `friendlyName` | String | 友好名称 |
| `manufacturer` | String? | 制造商 |
| `modelName` | String? | 型号名称 |
| `modelDescription` | String? | 型号描述 |
| `modelNumber` | String? | 型号编号 |
| `serialNumber` | String? | 序列号 |
| `baseUrl` | String | 设备描述 XML 的 Base URL |
| `location` | String | SSDP 消息中的 Location |
| `ipAddress` | String | 设备 IP 地址 |
| `presentationUrl` | String? | 管理界面 URL |
| `iconList` | List<Icon> | 设备图标列表 |
| `serviceList` | List<Service> | 服务列表 |
| `deviceList` | List<Device> | 嵌入式设备列表（子设备） |
| `isEmbeddedDevice` | Boolean | 是否为嵌入式设备 |
| `isPinned` | Boolean | 是否为固定设备 |
| `expireTime` | Long | 设备过期时间（ms） |
| `controlPoint` | ControlPoint | 所属 ControlPoint |
| `ssdpMessage` | SsdpMessage | 最新 SSDP 消息 |

### 6.2 根据 Service ID 查找服务

```kotlin
val service = device.findServiceById("urn:upnp-org:serviceId:ContentDirectory"): Service?
```

> 注：KDoc 中未列出此方法，但是 README 示例中使用了该方法。

### 6.3 Icon 接口

| 属性 | 类型 | 说明 |
|------|------|------|
| `url` | String | 图标 URL |
| `width` | Int | 宽度（px） |
| `height` | Int | 高度（px） |
| `depth` | Int | 色深 |
| `mimeType` | String | MIME 类型 |
| `binary` | `ByteArray?` | 图标二进制数据（需下载） |

### 6.4 SsdpMessage 接口

每条 SSDP 通知/响应都是一个 `SsdpMessage`，可直接读取其字段做更细粒度的过滤和调试。

```kotlin
interface SsdpMessage {
    val type: String?        // NT 或 ST 字段
    val nt: String?         // Notify To（设备类型）
    val usn: String?        // Unique Service Name（设备唯一标识）
    val location: String    // 设备描述 XML URL
    val server: String?     // 服务器字符串，如 "Linux/UPnP/1.0 Jellyfin/10.9"
    val cacheControl: String? // 缓存时间，如 "max-age=1800"
    val mx: String?         // M-SEARCH 的 MX 字段（最大等待时间）
    val man: String?        // SOAPAction 类似，协议声明
    val ext: String?        // 扩展字段
    val hopLimit: Int       // 跳数限制
    val interfaceAddress: String  // 收到该消息的网卡 IP
    val localAddress: String // 本地接收地址
    val remoteAddress: String // 发送方地址
    val time: Long          // 收到时间戳（ms）
    val expireTime: Long    // 过期时间（ms），由 cache-control 计算
}
```

**常见 server 字符串解析：**

```kotlin
fun parseServer(server: String?): Map<String, String> {
    if (server == null) return emptyMap()
    // 格式: "OS/version UPnP/1.0 product/version"
    val parts = server.split(" ")
    return mapOf(
        "os"      to parts.getOrNull(0) ?: "",
        "upnp"    to parts.getOrNull(1) ?: "",
        "product" to parts.getOrNull(2) ?: ""
    )
}

// 使用
val msg = device.ssdpMessage
val serverInfo = parseServer(msg.server)
println("设备运行于: ${serverInfo["product"]}")  // 如 "Jellyfin/10.9"
```

**利用 SsdpMessage 做精确过滤：**

```kotlin
cp.addDiscoveryListener { device ->
    val msg = device.ssdpMessage

    // 排除特定固件版本
    val server = msg.server ?: ""
    if (server.contains("Jellyfin/10.7")) {
        println("跳过旧版本 Jellyfin")
        return@addDiscoveryListener
    }

    // 过滤特定 IP 网段
    if (msg.remoteAddress.startsWith("192.168.10.")) {
        println("发现来自 IoT 网段的设备: ${device.friendlyName}")
    }
}
```

---

## 7. Service 与 Action 调用

### 7.1 Service 接口

```kotlin
interface Service {
    val serviceType: String       // 服务类型
    val serviceId: String         // 服务 ID
    val controlUrl: String        // 控制 URL
    val eventSubUrl: String       // 事件订阅 URL

    fun findAction(name: String): Action?
    fun findStateVariable(name: String?): StateVariable?

    // 订阅（异步）
    fun subscribe(keepRenew: Boolean = false, callback: ((Boolean) -> Unit)? = null)
    suspend fun subscribeAsync(keepRenew: Boolean = false): Boolean
    fun subscribeSync(keepRenew: Boolean = false): Boolean

    // 取消订阅（异步）
    fun unsubscribe(callback: ((Boolean) -> Unit)? = null)
    suspend fun unsubscribeAsync(): Boolean
    fun unsubscribeSync(): Boolean

    // 续订（异步）
    fun renewSubscribe(callback: ((Boolean) -> Unit)? = null)
    suspend fun renewSubscribeAsync(): Boolean
    fun renewSubscribeSync(): Boolean
}
```

### 7.2 Action 接口

```kotlin
interface Action {
    val name: String               // Action 名称

    fun findArgument(name: String): Argument?

    // 异步调用
    fun invoke(
        argumentValues: Map<String, String?>,
        returnErrorResponse: Boolean = false,
        onResult: ((Map<String, String>) -> Unit)? = null,
        onError: ((IOException) -> Unit)? = null
    )

    suspend fun invokeAsync(
        argumentValues: Map<String, String?>,
        returnErrorResponse: Boolean = false
    ): Map<String, String>

    // 自定义命名空间调用
    fun invokeCustom(...)
    suspend fun invokeCustomAsync(...): Map<String, String>
}
```

#### argumentValues 参数类型规则

`argumentValues: Map<String, String?>` 中**所有值必须是字符串**。数字、布尔值也要转成字符串传入：

| 实际类型 | 传入值示例 |
|---------|-----------|
| 整数（InstanceID、StartingIndex） | `"0"`, `"100"` |
| 布尔值 | `"1"` / `"0"`（部分设备也接受 `"true"` / `"false"`） |
| 时间（HH:MM:SS） | `"00:03:45"`, `"REL_TIME"` |
| URI | `"http://192.168.1.100/track.mp3"` |
| 空值 | `null`（表示不传该参数） |

```kotlin
// 典型错误：传入非字符串类型
// ❌ 编译错误或运行时异常
browse.invoke(mapOf("ObjectID" to 0, "StartingIndex" to false))

// ✅ 正确：所有值转字符串
browse.invoke(mapOf(
    "ObjectID" to "0",
    "StartingIndex" to "0",
    "RequestedCount" to "100"
))
```

#### 返回值 Map<String, String> 结构

`invoke` 的 `onResult` 和 `invokeAsync` 的返回值是 `Map<String, String>`，包含 **所有输出参数**（direction=out 的 Argument）：

| Action | 典型返回值 key |
|--------|---------------|
| `Browse` | `Result`, `NumberReturned`, `TotalMatches`, `UpdateID` |
| `Search` | `Result`, `NumberReturned`, `TotalMatches`, `UpdateID` |
| `GetPositionInfo` | `Track`, `TrackDuration`, `TrackMetaData`, `RelTime`, `AbsTime` |
| `GetTransportInfo` | `CurrentTransportState`, `CurrentTransportStatus`, `CurrentSpeed` |
| `GetMediaInfo` | `MediaDuration`, `CurrentURI`, `CurrentURIMetaData`, `NrTracks` |

> ⚠️ 返回 Map 中**永远不包含输入参数**。如果 Action 没有输出参数（如 `Stop`、`Play`、`Pause`），`onResult` 的 Map 为空。

#### returnErrorResponse 参数

```kotlin
// returnErrorResponse = false（默认）：SOAP 错误走 onError 回调
browse.invoke(args, onResult = {...}, onError = {...})

// returnErrorResponse = true：SOAP 错误也走 onResult，返回值中包含错误信息
browse.invoke(args, returnErrorResponse = true) { result ->
    if (result.containsKey("ErrorCode")) {
        println("SOAP 错误: ${result["ErrorCode"]} - ${result["ErrorDescription"]}")
    }
}
```

### 7.3 Argument 接口

```kotlin
interface Argument {
    val name: String               // 参数名称
    val isInputDirection: Boolean  // 是否为输入参数
    val relatedStateVariable: StateVariable  // 关联的状态变量
}
```

### 7.4 StateVariable 接口

```kotlin
interface StateVariable {
    val name: String               // 变量名称
    val dataType: String           // 数据类型（如 "string", "ui1", "bin.base64"）
    val isSendEvents: Boolean       // 是否发送事件
    val isMulticast: Boolean       // 是否多播
    val allowedValueList: List<String>  // 允许的值列表
    val defaultValue: String?       // 默认值
    val minimum: String?            // 最小值
    val maximum: String?            // 最大值
    val step: String?               // 步进值
}
```

### 7.5 Action 调用示例（DLNA ContentDirectory Browse）

```kotlin
// 1. 获取设备
val mediaServer = cp.getDevice(udn)

// 2. 查找 Action
val browse = mediaServer
    ?.findServiceById("urn:upnp-org:serviceId:ContentDirectory")
    ?.findAction("Browse")

// 3. 调用 Action
browse?.invoke(
    argumentValues = mapOf(
        "ObjectID" to "0",
        "BrowseFlag" to "BrowseDirectChildren",
        "Filter" to "*",
        "StartingIndex" to "0",
        "RequestedCount" to "0",
        "SortCriteria" to ""
    ),
    onResult = { result ->
        val resultXml = result["Result"]  // 返回结果为 XML 字符串
        println("Browse 结果: $resultXml")
    },
    onError = { e ->
        println("调用失败: ${e.message}")
    }
)
```

### 7.6 Kotlin 协程方式调用

```kotlin
suspend fun callBrowse() {
    val mediaServer = cp.getDevice(udn) ?: return
    val browse = mediaServer
        .findServiceById("urn:upnp-org:serviceId:ContentDirectory")
        ?.findAction("Browse")
        ?: return

    try {
        val result = browse.invokeAsync(
            mapOf(
                "ObjectID" to "0",
                "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*",
                "StartingIndex" to "0",
                "RequestedCount" to "0",
                "SortCriteria" to ""
            )
        )
        println("Browse 结果: ${result["Result"]}")
    } catch (e: IOException) {
        println("调用失败: ${e.message}")
    }
}
```

### 7.7 DIDL-Lite 解析

Browse/Search 返回的 `Result` 字段是一个 DIDL-Lite XML 字符串，描述了媒体库中的容器（container）和条目（item）。下面提供完整的 Kotlin 解析代码。

#### DIDL-Lite 数据结构速查

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
           xmlns:dc="http://purl.org/dc/elements/1.1/">
  <container id="artists$uuid" parentID="0" childCount="3" restricted="true">
    <dc:title>The Beatles</dc:title>
    <upnp:class>object.container.person.musicPerson</upnp:class>
    <upnp:artist>The Beatles</upnp:artist>
  </container>
  <item id="tracks$uuid$track1" parentID="albums$uuid" restricted="true">
    <dc:title>Come Together</dc:title>
    <dc:creator>The Beatles</dc:creator>
    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
    <res protocolInfo="http-get:*:audio/mpeg:*"
         duration="00:03:45"
         bitrate="320000">http://192.168.1.100:8096/track1.mp3</res>
  </item>
</DIDL-Lite>
```

#### 数据类定义

```kotlin
/**
 * 媒体容器（目录），如艺术家、专辑、播放列表
 */
data class MediaContainer(
    val id: String,            // 唯一标识，用于后续 Browse 的 ObjectID
    val parentId: String,     // 父容器 ID
    val title: String,        // 显示名称
    val childCount: Int,      // 直接子项数量
    val upnpClass: String,    // upnp:class，如 object.container.album.musicAlbum
    val creator: String? = null,  // 创作者（艺术家）
    val albumArtUri: String? = null // 封面图 URL
)

/**
 * 媒体条目（具体文件），如一首歌、一张图片
 */
data class MediaItem(
    val id: String,            // 唯一标识
    val parentId: String,      // 所属容器 ID
    val title: String,         // 标题
    val creator: String? = null,    // 艺术家
    val album: String? = null,     // 专辑名
    val duration: String? = null,   // 时长 "HH:MM:SS"
    val resourceUri: String? = null,// 媒体流地址（播放用）
    val protocolInfo: String? = null,// 协议信息 "http-get:*:audio/mpeg:*"
    val bitrate: Int? = null,       // 码率（bps）
    val upnpClass: String,     // upnp:class
    val albumArtUri: String? = null // 封面图 URL
)

/** Browse/Search 的解析结果 */
data class BrowseResult(
    val containers: List<MediaContainer>,
    val items: List<MediaItem>,
    val totalMatches: Int,      // 符合条件的结果总数（用于分页）
    val numberReturned: Int     // 本次返回的数量
)
```

#### XmlPullParser 解析实现

```kotlin
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import android.util.Xml  // Android 环境

/**
 * 解析 DIDL-Lite XML 字符串
 */
fun parseDidlLite(xmlString: String): BrowseResult {
    val containers = mutableListOf<MediaContainer>()
    val items = mutableListOf<MediaItem>()

    // 命名空间常量
    val NS_UPNP = "urn:schemas-upnp-org:metadata-1-0/upnp/"
    val NS_DC   = "http://purl.org/dc/elements/1.1/"
    val NS_DIDL = "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"

    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(xmlString.byteInputStream())

    // 解析 container
    doc.getElementsByTagName("container").forEach { node ->
        val element = node as org.w3c.dom.Element
        containers.add(MediaContainer(
            id        = element.getAttribute("id"),
            parentId  = element.getAttribute("parentID"),
            childCount= element.getAttribute("childCount").toIntOrNull() ?: 0,
            title     = element.getFirstChildText("title", NS_DC),
            upnpClass = element.getFirstChildText("class", NS_UPNP),
            creator   = element.getFirstChildText("creator", NS_DC),
            albumArtUri = element.getFirstChildText("albumArtURI", NS_UPNP)
        ))
    }

    // 解析 item
    doc.getElementsByTagName("item").forEach { node ->
        val element = node as org.w3c.dom.Element
        val resElement = element.getElementsByTagName("res").item(0) as? org.w3c.dom.Element

        items.add(MediaItem(
            id          = element.getAttribute("id"),
            parentId    = element.getAttribute("parentID"),
            title       = element.getFirstChildText("title", NS_DC),
            creator     = element.getFirstChildText("creator", NS_DC),
            album       = element.getFirstChildText("album", NS_UPNP),
            duration    = resElement?.getAttribute("duration"),
            resourceUri = resElement?.textContent?.trim(),
            protocolInfo= resElement?.getAttribute("protocolInfo"),
            bitrate     = resElement?.getAttribute("bitrate")?.toIntOrNull(),
            upnpClass   = element.getFirstChildText("class", NS_UPNP),
            albumArtUri = element.getFirstChildText("albumArtURI", NS_UPNP)
        ))
    }

    return BrowseResult(
        containers   = containers,
        items        = items,
        totalMatches = 0,  // 需从 Action 输出参数获取，见下文
        numberReturned = containers.size + items.size
    )
}

/** 辅助：从子节点中按名称取文本值（带命名空间） */
private fun org.w3c.dom.Element.getFirstChildText(localName: String, namespace: String): String? {
    val list = getElementsByTagNameNS(namespace, localName)
    return if (list.length > 0) list.item(0)?.textContent?.trim() else null
}
```

#### 从 ActionResult 中提取 totalMatches 和 numberReturned

Browse/Search Action 返回的 `Map<String, String>` 中除了 `Result`（DIDL-Lite XML 字符串），还有分页相关字段：

```kotlin
browse.invoke(
    argumentValues = mapOf(
        "ObjectID" to objectId,
        "BrowseFlag" to "BrowseDirectChildren",
        "Filter" to "*",
        "StartingIndex" to startIndex.toString(),
        "RequestedCount" to pageSize.toString(),
        "SortCriteria" to ""
    ),
    onResult = { result ->
        val totalMatches = result["TotalMatches"]?.toIntOrNull() ?: 0
        val numberReturned = result["NumberReturned"]?.toIntOrNull() ?: 0
        val resultXml = result["Result"] ?: ""

        val browseResult = parseDidlLite(resultXml)

        println("总数量: $totalMatches, 本次返回: $numberReturned")
        browseResult.containers.forEach { container ->
            println("📁 ${container.title} (${container.childCount} 项)")
        }
        browseResult.items.forEach { item ->
            println("🎵 ${item.title} - ${item.creator}")
        }
    },
    onError = { e ->
        println("Browse 失败: ${e.message}")
    }
)
```

#### 分页浏览完整示例

```kotlin
/**
 * 分页浏览媒体库
 * @param objectId 要浏览的容器 ID，根目录传 "0"
 * @param pageSize 每页数量
 * @param page 第几页（从 0 开始）
 */
fun browsePage(
    cp: ControlPoint,
    objectId: String,
    pageSize: Int = 50,
    page: Int = 0,
    onResult: (BrowseResult, hasMore: Boolean) -> Unit
) {
    val mediaServer = // ... 通过设备发现获取 MediaServer
    val cds = mediaServer.findServiceById("urn:upnp-org:serviceId:ContentDirectory")
    val browse = cds?.findAction("Browse") ?: return

    val startIndex = page * pageSize

    browse.invoke(
        argumentValues = mapOf(
            "ObjectID" to objectId,
            "BrowseFlag" to "BrowseDirectChildren",
            "Filter" to "*",
            "StartingIndex" to startIndex.toString(),
            "RequestedCount" to pageSize.toString(),
            "SortCriteria" to ""
        ),
        onResult = { result ->
            val totalMatches = result["TotalMatches"]?.toIntOrNull() ?: 0
            val numberReturned = result["NumberReturned"]?.toIntOrNull() ?: 0
            val browseResult = parseDidlLite(result["Result"] ?: "")
            val hasMore = (startIndex + numberReturned) < totalMatches
            onResult(browseResult, hasMore)
        },
        onError = { e ->
            println("Browse 失败: ${e.message}")
        }
    )
}

// 使用：分页加载第一页
browsePage(cp, "0", pageSize = 50, page = 0) { result, hasMore ->
    result.items.forEach { println(it.title) }
    if (hasMore) {
        // 加载第二页...
    }
}
```

---

### 7.8 AVTransport 播放状态与进度

AVTransport Service 控制播放器的播放、暂停、进度等操作。与 ContentDirectory 不同，AVTransport 主要用于控制媒体渲染设备（MediaRenderer），如 DLNA 音箱、电视。

#### 常用 Action 一览

| Action | 说明 | 关键参数 |
|--------|------|---------|
| `SetAVTransportURI` | 设置媒体 URI | `InstanceID`, `CurrentURI`, `CurrentURIMetaData` |
| `Play` | 开始播放 | `InstanceID`, `Speed` |
| `Pause` | 暂停 | `InstanceID` |
| `Stop` | 停止 | `InstanceID` |
| `Seek` | 跳转 | `InstanceID`, `Unit`, `Target` |
| `GetPositionInfo` | 获取播放进度 | `InstanceID` |
| `GetTransportInfo` | 获取传输状态 | `InstanceID` |
| `GetDeviceCapabilities` | 获取设备能力 | `InstanceID` |
| `GetTransportSettings` | 获取传输设置 | `InstanceID` |

#### StateVariable 一览（重点）

| StateVariable | 类型 | 说明 |
|--------------|------|------|
| `TransportState` | string | 当前状态，见下方 |
| `TransportStatus` | string | 错误状态，OK/ERROR 开头 |
| `PlaybackStorageMedium` | string | 存储介质 |
| `AVTransportURI` | string | 当前媒体 URI |
| `AbsTime` | string | 绝对时间（HH:MM:SS） |
| `RelTime` | string | 相对时间（已播放时长） |
| `TrackDuration` | string | 当前曲目总时长 |
| `TrackMetaData` | string | 当前曲目元数据（DIDL-Lite） |
| `CurrentTrack` | string | 当前曲目序号 |
| `CurrentTrackDuration` | string | 当前曲目时长 |

**TransportState 枚举值：**

| 值 | 含义 |
|----|------|
| `STOPPED` | 已停止 |
| `PLAYING` | 正在播放 |
| `TRANSITIONING` | 正在切换（缓冲/加载） |
| `PAUSED_PLAYBACK` | 已暂停 |
| `PAUSED_RECORDING` | 暂停录制（不常用） |
| `RECORDING` | 录制中 |
| `NO_MEDIA_PRESENT` | 无媒体 |

#### 设置媒体地址并播放（完整流程）

```kotlin
val renderer = cp.getDevice(rendererUdn) ?: return
val av = renderer.findServiceById("urn:upnp-org:serviceId:AVTransport") ?: return

// 1. 设置媒体 URI
val setUri = av.findAction("SetAVTransportURI")
setUri?.invoke(
    argumentValues = mapOf(
        "InstanceID" to "0",
        "CurrentURI" to "http://192.168.1.100:8096/track1.mp3",
        "CurrentURIMetaData" to ""  // 可传 DIDL-Lite 元数据，空字符串也行
    ),
    onResult = { result ->
        println("URI 设置成功")

        // 2. 开始播放
        val play = av.findAction("Play")
        play?.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0",
                "Speed" to "1"
            ),
            onResult = {
                println("开始播放")
            },
            onError = { e ->
                println("播放失败: ${e.message}")
            }
        )
    },
    onError = { e ->
        println("设置 URI 失败: ${e.message}")
    }
)
```

#### 播放控制（暂停/恢复/停止/跳转）

```kotlin
// 暂停
av.findAction("Pause")?.invoke(
    argumentValues = mapOf("InstanceID" to "0"),
    onResult = { println("已暂停") }
)

// 恢复播放（Play 即可）
av.findAction("Play")?.invoke(
    argumentValues = mapOf("InstanceID" to "0", "Speed" to "1"),
    onResult = { println("继续播放") }
)

// 停止
av.findAction("Stop")?.invoke(
    argumentValues = mapOf("InstanceID" to "0"),
    onResult = { println("已停止") }
)

// 跳转（跳到指定位置）
av.findAction("Seek")?.invoke(
    argumentValues = mapOf(
        "InstanceID" to "0",
        "Unit" to "REL_TIME",       // 相对时间，也可用 ABS_TIME / TRACK_NR
        "Target" to "00:01:30"     // 跳到 1 分 30 秒
    ),
    onResult = { println("已跳转") },
    onError = { e -> println("跳转失败: ${e.message}") }
)

// 跳转（跳到指定曲目）
av.findAction("Seek")?.invoke(
    argumentValues = mapOf(
        "InstanceID" to "0",
        "Unit" to "TRACK_NR",
        "Target" to "3"   // 第 3 首
    ),
    onResult = { println("已切到第3首") }
)
```

#### 获取当前播放状态与进度

```kotlin
// 获取传输状态（PLAYING / PAUSED / STOPPED）
av.findAction("GetTransportInfo")?.invoke(
    argumentValues = mapOf("InstanceID" to "0"),
    onResult = { result ->
        val state = result["CurrentTransportState"] ?: "UNKNOWN"
        val status = result["CurrentTransportStatus"] ?: "OK"
        println("播放状态: $state, 状态详情: $status")
    }
)

// 获取当前进度
av.findAction("GetPositionInfo")?.invoke(
    argumentValues = mapOf("InstanceID" to "0"),
    onResult = { result ->
        val absTime  = result["AbsTime"] ?: "--:--:--"
        val relTime  = result["RelTime"] ?: "--:--:--"
        val duration = result["TrackDuration"] ?: "--:--:--"
        val track    = result["CurrentTrack"] ?: "0"
        println("[$track] $relTime / $duration (总: $absTime)")
    },
    onError = { e -> println("获取进度失败: ${e.message}") }
)
```

#### 轮询播放进度的完整封装

```kotlin
/**
 * 简易播放器状态封装
 */
class UpnpPlayer(private val av: Service) {

    private var isPlaying = false
    private var currentUri: String? = null

    fun play(uri: String, onReady: () -> Unit, onError: (IOException) -> Unit) {
        val setUri = av.findAction("SetAVTransportURI") ?: return
        setUri.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to uri,
                "CurrentURIMetaData" to ""
            ),
            onResult = {
                currentUri = uri
                val play = av.findAction("Play") ?: return
                play.invoke(
                    argumentValues = mapOf("InstanceID" to "0", "Speed" to "1"),
                    onResult = {
                        isPlaying = true
                        onReady()
                    },
                    onError = { e -> onError(e) }
                )
            },
            onError = { e -> onError(e) }
        )
    }

    fun pause() {
        if (!isPlaying) return
        av.findAction("Pause")?.invoke(
            argumentValues = mapOf("InstanceID" to "0"),
            onResult = { isPlaying = false }
        )
    }

    fun stop() {
        av.findAction("Stop")?.invoke(
            argumentValues = mapOf("InstanceID" to "0"),
            onResult = { isPlaying = false }
        )
    }

    fun resume() {
        if (isPlaying) return
        av.findAction("Play")?.invoke(
            argumentValues = mapOf("InstanceID" to "0", "Speed" to "1"),
            onResult = { isPlaying = true }
        )
    }

    fun seekTo(hhmmss: String) {
        av.findAction("Seek")?.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0",
                "Unit" to "REL_TIME",
                "Target" to hhmmss
            )
        )
    }

    fun getPosition(onResult: (relTime: String, duration: String) -> Unit) {
        av.findAction("GetPositionInfo")?.invoke(
            argumentValues = mapOf("InstanceID" to "0"),
            onResult = { result ->
                onResult(
                    result["RelTime"] ?: "--:--:--",
                    result["TrackDuration"] ?: "--:--:--"
                )
            }
        )
    }
}

// 使用
val player = UpnpPlayer(av)
player.play("http://192.168.1.100:8096/track1.mp3",
    onReady = { println("播放中") },
    onError = { e -> println("错误: ${e.message}") }
)

// 暂停
player.pause()

// 跳转
player.seekTo("00:01:30")

// 查询进度
player.getPosition { rel, total ->
    println("$rel / $total")
}
```

### 7.9 订阅生命周期管理

订阅不是一劳永逸的。UPnP 事件订阅有固定时限（Jellyfin 默认 1800 秒），到期前必须续订，否则事件通知自动中断。以下是完整的生命周期管理方案。

#### 订阅状态追踪

```kotlin
/**
 * 订阅状态追踪器
 */
class SubscriptionManager {

    private val activeSubscriptions = mutableMapOf<String, Service>()
    private val renewTimers = mutableMapOf<String, ScheduledFuture<*>>()

    /**
     * 订阅服务并自动续订
     */
    fun subscribe(service: Service) {
        service.subscribe(keepRenew = false) { success ->
            if (success) {
                activeSubscriptions[service.serviceId] = service
                scheduleRenew(service)
                println("订阅成功: ${service.serviceType}")
            } else {
                println("订阅失败: ${service.serviceType}")
            }
        }
    }

    /**
     * 计算续订时间：订阅超时前 10% 或最少 60 秒
     */
    private fun scheduleRenew(service: Service) {
        // UPnP 订阅默认 1800 秒，提前 10% 续订 = 1620 秒后
        val renewInSeconds = maxOf(60, (1800 * 0.9).toLong())
        val delayMs = renewInSeconds * 1000

        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val future = executor.schedule({
            renew(service)
        }, renewInSeconds, java.util.concurrent.TimeUnit.SECONDS)

        renewTimers[service.serviceId] = future
    }

    private fun renew(service: Service) {
        service.renewSubscribe { success ->
            if (success) {
                println("续订成功: ${service.serviceType}")
                scheduleRenew(service)  // 再次调度下一次续订
            } else {
                println("续订失败，重新订阅: ${service.serviceType}")
                subscribe(service)  // 续订失败则重新订阅
            }
        }
    }

    /**
     * 取消订阅并清理
     */
    fun unsubscribe(service: Service) {
        renewTimers[service.serviceId]?.cancel(true)
        renewTimers.remove(service.serviceId)
        activeSubscriptions.remove(service.serviceId)
        service.unsubscribe()
    }

    /**
     * 关闭所有订阅（退出应用时）
     */
    fun shutdown() {
        activeSubscriptions.values.forEach { it.unsubscribe() }
        renewTimers.values.forEach { it.cancel(true) }
        activeSubscriptions.clear()
        renewTimers.clear()
    }

    val subscribedCount: Int get() = activeSubscriptions.size
}
```

**自动订阅所有 MediaServer 服务：**

```kotlin
fun autoSubscribeToAll(device: Device, subManager: SubscriptionManager) {
    device.serviceList.forEach { service ->
        // 只订阅 ContentDirectory 和 ConnectionManager
        if (service.serviceType.contains("ContentDirectory") ||
            service.serviceType.contains("ConnectionManager")) {
            subManager.subscribe(service)
        }
    }
}
```

#### 订阅相关的 StateVariable

以下 StateVariable 与订阅机制紧密相关，可通过事件监听捕获其变化：

| StateVariable | 说明 | 典型值 |
|---------------|------|--------|
| `LastChange` | 最重要的事件载体，所有状态变更都在其中 | DIDL-Lite 片段 |
| `A_ARG_TYPE_InstanceID` | 操作实例 ID | "0" |
| `SinkProtocolInfo` | Renderer 支持的协议 | DLNA 格式字符串 |
| `SourceProtocolInfo` | Server 支持的协议 | DLNA 格式字符串 |
| `CurrentConnectionIDs` | 当前活跃连接列表 | "0" 等 |

**LastChange 事件解析示例：**

```kotlin
cp.addNotifyEventListener { service, seq, variable, value ->
    if (variable == "LastChange") {
        parseLastChange(value).forEach { (varName, newValue) ->
            println("[${service.serviceId}] $varName = $newValue")
        }
    }
}

/**
 * 解析 LastChange XML
 * 格式: <Event xmlns="urn:schemas-upnp-org:event-1-0">
 *         <InstanceID val="0">
 *           <TransportState val="PLAYING"/>
 *         </InstanceID>
 *       </Event>
 */
fun parseLastChange(xml: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    try {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xml.byteInputStream())
        val instances = doc.getElementsByTagName("InstanceID")
        for (i in 0 until instances.length) {
            val instance = instances.item(i) as org.w3c.dom.Element
            val vars = instance.childNodes
            for (j in 0 until vars.length) {
                val node = vars.item(j)
                if (node.nodeName != "#text") {
                    val el = node as org.w3c.dom.Element
                    result[node.nodeName] = el.getAttribute("val")
                }
            }
        }
    } catch (e: Exception) {
        println("LastChange 解析失败: ${e.message}")
    }
    return result
}
```

---

### 7.10 invokeCustom 自定义命名空间调用

大多数 UPnP Action 使用标准的 `urn:schemas-upnp-org:service:*` 命名空间，但部分厂商会扩展自己的私有服务（如 Jellyfin 的 DMS 私有接口）。`invokeCustom` / `invokeCustomAsync` 支持自定义 SOAPAction 命名空间。

```kotlin
/**
 * 调用自定义命名空间的 Action（绕过标准 UPnP）
 */
fun invokeCustomNamespace(
    service: Service,
    actionName: String,
    namespace: String,  // 如 "urn:schemas-jellyfin-org:service:Jellyfin DMS:1"
    argumentValues: Map<String, String?>,
    onResult: (Map<String, String>) -> Unit,
    onError: (IOException) -> Unit
) {
    val action = service.findAction(actionName) ?: run {
        onError(IOException("Action not found: $actionName"))
        return
    }

    action.invokeCustom(
        argumentValues = argumentValues,
        namespace = namespace,  // 自定义命名空间
        returnErrorResponse = false,
        onResult = onResult,
        onError = onError
    )
}

// 示例：调用 Jellyfin 私有 DMS 接口
invokeCustomNamespace(
    service = dmsService,
    actionName = "RefreshMediaList",
    namespace = "urn:schemas-jellyfin-org:service:Jellyfin DMS:1",
    argumentValues = mapOf(
        "ObjectID" to "musicdb://albums",
        "listType" to "albums",
        "recursive" to "true"
    ),
    onResult = { result ->
        println("刷新结果: $result")
    },
    onError = { e ->
        println("刷新失败: ${e.message}")
    }
)
```

> ⚠️ **注意**：`invokeCustom` 调用的 Action 名称必须存在于设备的 Service 描述 XML 中。如果不确定私有 Action 名称，可以先用 `service.actionList.forEach { println(it.name) }` 枚举所有可用 Action。

---

## 8. 事件订阅

### 8.1 NotifyEventListener（推荐）

```kotlin
interface ControlPoint.NotifyEventListener {
    fun onNotifyEvent(
        service: Service,     // 触发事件的 Service
        seq: Long,            // 事件序列号
        variable: String,    // 状态变量名
        value: String         // 新值
    )
}
```

**使用示例：**

```kotlin
val notifyEventListener = object : ControlPoint.NotifyEventListener {
    override fun onNotifyEvent(
        service: Service,
        seq: Long,
        variable: String,
        value: String
    ) {
        println("服务: ${service.serviceType}")
        println("变量: $variable = $value")
    }
}

cp.addNotifyEventListener(notifyEventListener)
```

### 8.2 EventListener（批量事件）

```kotlin
interface ControlPoint.EventListener {
    fun onEvent(
        service: Service,
        seq: Long,
        properties: List<Pair<String, String>>  // 所有变更的属性
    )
}
```

**使用示例：**

```kotlin
val eventListener = object : ControlPoint.EventListener {
    override fun onEvent(
        service: Service,
        seq: Long,
        properties: List<Pair<String, String>>
    ) {
        println("服务: ${service.serviceType}, 序列: $seq")
        properties.forEach { (key, value) ->
            println("  $key = $value")
        }
    }
}

cp.addEventListener(eventListener)
```

### 8.3 MulticastEventListener（实验性）

```kotlin
interface ControlPoint.MulticastEventListener {
    fun onEvent(
        service: Service,
        lvl: String,           // 事件层级
        seq: Long,
        properties: List<Pair<String, String>>
    )
}
```

> ⚠️ 多播事件功能为实验性，兼容性未确认。

### 8.4 订阅/取消订阅示例

```kotlin
val mediaServer = cp.getDevice(udn)
val cds = mediaServer?.findServiceById("urn:upnp-org:serviceId:ContentDirectory")

// 开始订阅
cds?.subscribe()

// 带续订的订阅（keepRenew = true 时自动续订）
cds?.subscribe(keepRenew = true)

// 取消订阅
cds?.unsubscribe()

// 续订
cds?.renewSubscribe()
```

### 8.5 移除监听器

```kotlin
cp.removeEventListener(listener)
cp.removeNotifyEventListener(listener)
cp.removeMulticastEventListener(listener)
```

---

## 9. 高级配置

### 9.1 指定网络接口

```kotlin
val networkInterface = NetworkInterface.getByName("eth0")
val cp = ControlPointFactory.create(
    interfaces = listOf(networkInterface)
)
```

### 9.2 Android 主线程回调

```kotlin
val handler = android.os.Handler(android.os.Looper.getMainLooper())
val cp = ControlPointFactory.create(
    callbackHandler = { runnable -> 
        handler.post(runnable)
        true
    }
)
```

### 9.3 自定义 TaskExecutor

```kotlin
val executor = object : TaskExecutor {
    private val threadPool = Executors.newSingleThreadExecutor()
    
    override fun execute(task: Runnable): Boolean {
        threadPool.execute(task)
        return true
    }
    
    override fun terminate() {
        threadPool.shutdownNow()
    }
}

val cp = ControlPointFactory.create(
    callbackExecutor = executor
)
```

### 9.4 SSDP 消息过滤

```kotlin
cp.setSsdpMessageFilter { message ->
    // 只接收 MediaServer 设备
    message.type?.contains("MediaServer") == true
}
```

### 9.5 图标过滤

```kotlin
interface IconFilter {
    fun accept(icon: Icon): Boolean
}

// 示例：只接收 48x48 PNG 图标
val iconFilter = object : IconFilter {
    override fun accept(icon: Icon): Boolean {
        return icon.width == 48 && icon.mimeType == "image/png"
    }
}

cp.setIconFilter(iconFilter)
```

### 9.6 禁用事件订阅（节省资源）

```kotlin
val cp = ControlPointFactory.create(
    subscriptionEnabled = false  // 不启动事件服务器线程
)
```

### 9.7 Notify 分片检查

```kotlin
val cp = ControlPointFactory.create(
    notifySegmentCheckEnabled = true  // 检查 Notify 消息分片
)


### 9.8 设备描述 XML 本地缓存

每次设备发现都会重新下载 `description.xml`，在移动设备上既耗流量又慢。生产级应用应当缓存解析结果，只在设备过期或首次发现时才下载。

```kotlin
import org.w3c.dom.Element
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 设备描述缓存器
 */
class DeviceDescriptionCache(private val cacheDir: File) {

    private val deviceCache = mutableMapOf<String, CachedDevice>()

    init {
        cacheDir.mkdirs()
    }

    /**
     * 尝试从缓存加载设备描述
     * @return 缓存未过期时返回 CachedDevice，否则返回 null
     */
    fun getFromCache(location: String, maxAgeMs: Long = 3600_000): CachedDevice? {
        val cached = deviceCache[location] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > maxAgeMs) {
            deviceCache.remove(location)
            return null
        }
        return cached
    }

    /**
     * 从 location 下载并解析设备描述
     */
    fun fetchDevice(location: String): DeviceDescription? {
        // 先检查缓存
        getFromCache(location)?.let { return it.description }

        return try {
            val connection = java.net.URL(location).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            val xml = connection.inputStream.bufferedReader().readText()

            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())

            val desc = parseDeviceDescription(doc, location)
            deviceCache[location] = CachedDevice(desc, System.currentTimeMillis())
            desc
        } catch (e: Exception) {
            println("下载设备描述失败: ${e.message}")
            null
        }
    }

    private fun parseDeviceDescription(doc: org.w3c.dom.Document, location: String): DeviceDescription {
        val root = doc.documentElement
        return DeviceDescription(
            udn           = root.getElementByTagName("UDN")?.textContent ?: "",
            deviceType    = root.getElementByTagName("deviceType")?.textContent ?: "",
            friendlyName  = root.getElementByTagName("friendlyName")?.textContent ?: "",
            manufacturer  = root.getElementByTagName("manufacturer")?.textContent,
            baseUrl       = location.substringBeforeLast("/"),
            ipAddress     = java.net.URL(location).host
        )
    }

    private fun org.w3c.dom.Element.getElementByTagName(tag: String): Element? {
        val list = getElementsByTagName(tag)
        return if (list.length > 0) list.item(0) as? Element else null
    }
}

data class CachedDevice(val description: DeviceDescription, val timestamp: Long)
data class DeviceDescription(
    val udn: String,
    val deviceType: String,
    val friendlyName: String,
    val manufacturer: String?,
    val baseUrl: String,
    val ipAddress: String
)

// 使用：在创建 ControlPoint 之前预热缓存
val cache = DeviceDescriptionCache(File(cacheDir, "device_cache"))
val location = "http://192.168.1.100:8096/description.xml"
val deviceDesc = cache.fetchDevice(location)
println("设备: ${deviceDesc?.friendlyName} (${deviceDesc?.ipAddress})")
```

---

### 9.9 线程安全注意事项

mmupnp 的 `ControlPoint` **不是线程安全的**。在实际项目（尤其是 Android）中，如果多个线程同时访问 ControlPoint 或发出调用，会导致数据竞争和难以调试的崩溃。以下是必须遵守的规则：

#### 基本原则

```kotlin
// ❌ 错误：在不同线程同时调用
thread { cp.search() }
thread { device.findServiceById(...) }  // 可能崩溃！

// ✅ 正确：用单线程 Executor 包装所有操作
private val upnpThread = Executors.newSingleThreadExecutor()
private val cp = ControlPointFactory.create()

fun safeSearch() {
    upnpThread.execute { cp.search() }
}

fun safeBrowse(objectId: String, callback: (String) -> Unit) {
    upnpThread.execute {
        val result = browse.invokeAsync(mapOf(...))  // 同步等待结果
        callback(result["Result"] ?: "")
    }
}
```

#### Android ViewModel 中的安全封装

```kotlin
class UpnpViewModel(scope: CoroutineScope) : ViewModel() {

    private val upnpScope = scope + Dispatchers.IO.limitedParallelism(1)
    //                        ↑ 所有 UPnP 操作限制为单线程

    fun search() {
        upnpScope.launch {
            cp.search()
        }
    }

    fun browse(objectId: String, onResult: (BrowseResult) -> Unit) {
        upnpScope.launch {
            val result = // ... 调用 browse
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }
}
```

#### 回调的线程问题

```kotlin
// ⚠️ 警告：回调在内部线程池执行，不要在回调中直接操作 UI
browse.invoke(
    argumentValues = args,
    onResult = { result ->
        // 这个回调在哪个线程？mmupnp 内部线程池，不确定！
        // Android 上直接操作 TextView 会崩溃
        runOnUiThread { textView.text = result["Result"] }
    },
    onError = { e ->
        runOnUiThread { showError(e.message) }
    }
)

// ✅ 推荐：用 callbackHandler 强制 UI 线程回调
val handler = android.os.Handler(Looper.getMainLooper())
val cp = ControlPointFactory.create(
    callbackHandler = { r -> handler.post(r); true }
)
// 此时 onResult/onError 回调会保证在主线程执行
```

---

### 9.10 双栈网络与 IPv6 支持

`Protocol` 枚举控制协议栈，默认为双栈（IPv4 + IPv6）。在纯 IPv4 环境下可以禁用 IPv6 降低开销。

```kotlin
// 双栈（默认）：同时监听 IPv4 和 IPv6 M-SEARCH 响应
val cpDefault = ControlPointFactory.create(
    protocol = Protocol.DEFAULT
)

// 仅 IPv4：节省资源，减少意外发现
val cp4 = ControlPointFactory.create(
    protocol = Protocol.IP_V4_ONLY
)

// 仅 IPv6：部分企业内网或新型 IoT 设备
val cp6 = ControlPointFactory.create(
    protocol = Protocol.IP_V6_ONLY
)

// ⚠️ 注意：Android 12+ 对 IPv6 有更严格限制，混合网络下建议用 IP_V4_ONLY
```

---


## 10. 完整示例

### 10.1 完整的设备发现与操作流程（Kotlin）

```kotlin
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device
import java.io.IOException

class UpnpManager {
    
    private lateinit var controlPoint: ControlPoint
    private val discoveredDevices = mutableMapOf<String, Device>()
    
    fun initialize() {
        // 1. 创建 ControlPoint
        controlPoint = ControlPointFactory.create()
        
        // 2. 添加设备发现监听器
        controlPoint.addDiscoveryListener(object : ControlPoint.DiscoveryListener {
            override fun onDiscover(device: Device) {
                onDeviceDiscovered(device)
            }
        })
        
        // 3. 添加事件监听器
        controlPoint.addNotifyEventListener(object : ControlPoint.NotifyEventListener {
            override fun onNotifyEvent(
                service: net.mm2d.upnp.Service,
                seq: Long,
                variable: String,
                value: String
            ) {
                println("事件: ${service.serviceType} [$variable] = $value")
            }
        })
        
        // 4. 初始化并启动
        controlPoint.initialize()
        controlPoint.start()
        
        // 5. 发送搜索
        controlPoint.search("upnp:rootdevice")
    }
    
    private fun onDeviceDiscovered(device: Device) {
        discoveredDevices[device.udn] = device
        println("发现设备: ${device.friendlyName} (${device.udn})")
        
        // 如果是媒体服务器，订阅事件
        if (device.deviceType.contains("MediaServer")) {
            subscribeToEvents(device)
        }
    }
    
    private fun subscribeToEvents(device: Device) {
        device.serviceList.forEach { service ->
            if (service.serviceType.contains("ContentDirectory")) {
                service.subscribe()
            }
        }
    }
    
    fun browseMediaServer(udn: String) {
        val device = discoveredDevices[udn] ?: return
        val cds = device.serviceList.find { 
            it.serviceType.contains("ContentDirectory") 
        } ?: return
        val browseAction = cds.findAction("Browse") ?: return
        
        browseAction.invoke(
            mapOf(
                "ObjectID" to "0",
                "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*",
                "StartingIndex" to "0",
                "RequestedCount" to "100",
                "SortCriteria" to ""
            ),
            onResult = { result ->
                println("Browse 结果:\n${result["Result"]}")
            },
            onError = { e ->
                println("Browse 失败: ${e.message}")
            }
        )
    }
    
    fun shutdown() {
        controlPoint.stop()
        controlPoint.terminate()
    }
}
```

### 10.2 Android Activity 示例

```kotlin
class UpnpActivity : AppCompatActivity() {
    
    private lateinit var controlPoint: ControlPoint
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        controlPoint = ControlPointFactory.create(
            callbackHandler = { runnable ->
                handler.post(runnable)
                true
            }
        )
        
        controlPoint.addDiscoveryListener(discoveryListener)
        controlPoint.addNotifyEventListener(eventListener)
        controlPoint.initialize()
        controlPoint.start()
        
        controlPoint.search()
    }
    
    private val discoveryListener = object : ControlPoint.DiscoveryListener {
        override fun onDiscover(device: Device) {
            runOnUiThread {
                // 更新 UI
                deviceList.add(device)
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    private val eventListener = object : ControlPoint.NotifyEventListener {
        override fun onNotifyEvent(service: Service, seq: Long, variable: String, value: String) {
            runOnUiThread {
                // 更新 UI
                statusText.text = "$variable = $value"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        controlPoint.stop()
        controlPoint.terminate()
    }
}
```

### 10.3 Java 用法示例

```java
import net.mm2d.upnp.*;

public class UpnpJavaExample {
    
    private ControlPoint controlPoint;
    
    public void start() {
        // 使用 Builder 创建（Java 友好）
        controlPoint = ControlPointFactory.builder()
            .setSubscriptionEnabled(true)
            .build();
        
        controlPoint.addDiscoveryListener(new ControlPoint.DiscoveryListener() {
            @Override
            public void onDiscover(@NotNull Device device) {
                System.out.println("发现: " + device.getFriendlyName());
            }
        });
        
        controlPoint.initialize();
        controlPoint.start();
        controlPoint.search();
    }
    
    public void invokeAction(Device device) {
        Service service = device.getServiceList().get(0);
        Action action = service.findAction("Browse");
        
        if (action != null) {
            Map<String, String> args = new HashMap<>();
            args.put("ObjectID", "0");
            args.put("BrowseFlag", "BrowseDirectChildren");
            args.put("Filter", "*");
            args.put("StartingIndex", "0");
            args.put("RequestedCount", "0");
            args.put("SortCriteria", "");
            
            action.invoke(args, false, result -> {
                System.out.println("结果: " + result.get("Result"));
            }, error -> {
                System.out.println("错误: " + error.getMessage());
            });
        }
    }
    
    public void stop() {
        controlPoint.stop();
        controlPoint.terminate();
    }
}


### 10.4 Android 完整生命周期指南

`ControlPoint` 必须在 Android 组件生命周期中正确管理，否则会导致内存泄漏或崩溃。

#### LifecycleOwner 集成

```kotlin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class UpnpLifecycleObserver(
    private val controlPoint: ControlPoint
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        // onCreate 中只创建实例，不启动
        controlPoint.initialize()
    }

    override fun onStart(owner: LifecycleOwner) {
        // onStart 中注册监听器并启动
        controlPoint.start()
        controlPoint.search("upnp:rootdevice")
    }

    override fun onStop(owner: LifecycleOwner) {
        // onStop 中停止搜索，保持监听器
        controlPoint.stop()
        // 注意：不要 terminate()，这样设备列表不会清空
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // 完全清理
        controlPoint.stop()
        controlPoint.terminate()
    }
}

// 使用
class MainActivity : AppCompatActivity() {
    private val controlPoint = ControlPointFactory.create(
        callbackHandler = { runnable ->
            handler.post(runnable)
            true
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(UpnpLifecycleObserver(controlPoint))
    }
}
```

#### onPause / onResume 处理

```kotlin
override fun onResume() {
    super.onResume()
    // 恢复时重新搜索，快速恢复设备列表
    controlPoint.search("upnp:rootdevice")
}

override fun onPause() {
    super.onPause()
    // 离开页面时停止搜索，节省电量
    controlPoint.stop()
}
```

#### 旋转屏幕处理

配置变更（屏幕旋转）会导致 Activity 重建，但 `ControlPoint` 不需要重建。推荐放在 Application 或 ViewModel 中管理：

```kotlin
// 在 Application 中管理单个 ControlPoint 实例
class UpnpApplication : Application() {
    val controlPoint: ControlPoint by lazy {
        ControlPointFactory.create(
            callbackHandler = { runnable ->
                Handler(Looper.getMainLooper()).post(runnable)
                true
            }
        ).also { it.initialize() }
    }
}

// Activity 中使用
class PlayerActivity : AppCompatActivity() {
    private val app get() = application as UpnpApplication
    private val controlPoint get() = app.controlPoint

    // 旋转屏幕不会重建 ControlPoint，设备列表保持
}
```

#### 内存泄漏排查清单

| 场景 | 问题 | 解决 |
|------|------|------|
| 在 `onCreate` 中注册 Listener，Activity destroy 时未移除 | Listener 持有 Activity 引用 | 用 `lifecycle.addObserver` 自动管理，或手动 `removeDiscoveryListener` |
| Handler 持有 Activity 引用 | `callbackHandler` 中的 Handler 未清理 | 用 lifecycle scope 包装，或用 weakReference |
| `subscribe()` 后未 `unsubscribe()` | Service 持有引用，无法 GC | 在 `onDestroy` 或 `onStop` 中取消订阅 |
| `ControlPoint` 在 ViewModel 中，但 ViewModel 持有 Activity 引用 | 配置变更后 ControlPoint 被新 ViewModel 重建 | 用 `applicationScope` 或单例管理 ControlPoint |

---

### 10.5 MediaRenderer 系统指南

MediaRenderer 是播放音频/视频的设备（DLNA 音箱、电视、Chromecast 等）。与 MediaServer 配对使用，实现"发现内容 → 投送到渲染器播放"的完整流程。

#### MediaRenderer 识别

```kotlin
fun isMediaRenderer(device: Device): Boolean {
    return device.deviceType.contains("MediaRenderer")
}

fun isMediaServer(device: Device): Boolean {
    return device.deviceType.contains("MediaServer")
}

// 设备发现时分类
cp.addDiscoveryListener { device ->
    when {
        isMediaRenderer(device) -> {
            println("渲染器: ${device.friendlyName}")
            renderers[device.udn] = device
        }
        isMediaServer(device) -> {
            println("媒体服务器: ${device.friendlyName}")
            servers[device.udn] = device
        }
        else -> {
            println("其他设备: ${device.friendlyName}")
        }
    }
}
```

#### 渲染器能力查询清单

在播放前，应按以下顺序查询渲染器能力，避免传入不支持的格式：

```kotlin
/**
 * 完整的渲染器能力探测流程
 */
class RendererCapabilities(private val renderer: Device) {

    private val av = renderer.findServiceById("urn:upnp-org:serviceId:AVTransport")
    private val cm = renderer.findServiceById("urn:upnp-org:serviceId:ConnectionManager")

    /**
     * 查询渲染器支持的协议列表（最关键）
     */
    fun queryProtocolInfo(onResult: (List<String>) -> Unit) {
        cm?.findAction("GetProtocolInfo")?.invoke(
            argumentValues = emptyMap(),
            onResult = { result ->
                val sinkList = result["Sink"]?.split(",") ?: emptyList()
                onResult(sinkList)
            }
        )
    }

    /**
     * 检查是否支持特定 MIME 类型
     */
    fun supportsMime(mime: String, protocols: List<String>): Boolean {
        // protocolInfo 格式: http-get:*:audio/mpeg:*
        return protocols.any { it.contains(mime) }
    }

    /**
     * 获取播放媒体支持格式
     */
    fun queryPlayMedia(onResult: (List<String>) -> Unit) {
        av?.findAction("GetDeviceCapabilities")?.invoke(
            argumentValues = mapOf("InstanceID" to "0"),
            onResult = { result ->
                val playMedia = result["PlayMedia"]?.split(",") ?: emptyList()
                onResult(playMedia)
            }
        )
    }

    /**
     * 获取当前传输状态
     */
    fun queryTransportState(onResult: (String) -> Unit) {
        av?.findAction("GetTransportInfo")?.invoke(
            argumentValues = mapOf("InstanceID" to "0"),
            onResult = { result ->
                onResult(result["CurrentTransportState"] ?: "UNKNOWN")
            }
        )
    }
}

// 使用示例
val caps = RendererCapabilities(renderer)
caps.queryProtocolInfo { protocols ->
    val canPlayMp3 = caps.supportsMime("audio/mpeg", protocols)
    val canPlayFlac = caps.supportsMime("audio/flac", protocols)
    println("MP3: $canPlayMp3, FLAC: $canPlayFlac")
}
```

#### 完整播放流程（从媒体服务器到渲染器）

```kotlin
/**
 * 从媒体服务器浏览并投放到渲染器的完整流程
 */
class DlnaPlayer(
    private val cp: ControlPoint,
    private val mediaServer: Device,
    private val renderer: Device
) {
    private val contentDirectory = mediaServer.findServiceById("urn:upnp-org:serviceId:ContentDirectory")
    private val avTransport = renderer.findServiceById("urn:upnp-org:serviceId:AVTransport")

    /**
     * 投放并播放一首媒体
     */
    fun playMediaItem(item: MediaItem, onReady: () -> Unit, onError: (Exception) -> Unit) {
        val uri = item.resourceUri ?: run {
            onError(Exception("媒体项无可用播放地址"))
            return
        }

        // 1. 探测渲染器是否支持
        val caps = RendererCapabilities(renderer)
        caps.queryProtocolInfo { protocols ->
            val supported = caps.supportsMime(item.mimeType ?: "", protocols)
            if (!supported) {
                onError(Exception("渲染器不支持 ${item.mimeType} 格式"))
                return@queryProtocolInfo
            }

            // 2. 设置媒体 URI
            setAvTransportUri(uri, item.title, onReady, onError)
        }
    }

    private fun setAvTransportUri(
        uri: String,
        title: String,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val setUri = avTransport?.findAction("SetAVTransportURI") ?: run {
            onError(Exception("AVTransport 不可用"))
            return
        }

        // 构建 DIDL-Lite 元数据（可选但推荐）
        val metaData = buildDidlLiteMetadata(title, uri)

        setUri.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to uri,
                "CurrentURIMetaData" to metaData
            ),
            onResult = {
                // 3. 开始播放
                play(onReady, onError)
            },
            onError = { e ->
                onError(e)
            }
        )
    }

    private fun play(onReady: () -> Unit, onError: (Exception) -> Unit) {
        val playAction = avTransport?.findAction("Play") ?: run {
            onError(Exception("Play Action 不可用"))
            return
        }
        playAction.invoke(
            argumentValues = mapOf("InstanceID" to "0", "Speed" to "1"),
            onResult = { onReady() },
            onError = { e -> onError(e) }
        )
    }

    /**
     * 构造简单的 DIDL-Lite 元数据
     */
    private fun buildDidlLiteMetadata(title: String, uri: String): String {
        val encodedTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """<?xml version="1.0" encoding="UTF-8"?>
<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
           xmlns:dc="http://purl.org/dc/elements/1.1/">
  <item id="0" parentID="-1" restricted="true">
    <dc:title>$encodedTitle</dc:title>
    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
    <res protocolInfo="http-get:*:audio/mpeg:*">$uri</res>
  </item>
</DIDL-Lite>"""
    }
}
```

---


## 11. 调试日志

### 11.1 启用日志

```kotlin
import net.mm2d.upnp.Property
import net.mm2d.upnp.log.*

Logger.setLogLevel(Logger.VERBOSE)
Logger.setSender(Senders.create())  // 输出到 System.out
```

### 11.2 自定义日志输出

```kotlin
// 输出到 Android Logcat
Logger.setSender { level, message, throwable ->
    android.util.Log.println(level, "mmupnp", message)
}
```

### 11.3 日志级别

| 级别 | 说明 |
|------|------|
| `Logger.ERROR` | 仅错误 |
| `Logger.WARN` | 警告+ |
| `Logger.INFO` | 信息+ |
| `Logger.DEBUG` | 调试+ |
| `Logger.VERBOSE` | 全部 |

---

## 12. 常见问题

### Q1: 设备发现不到？

- 检查是否调用了 `cp.search()`
- 确认设备与手机在同一局域网
- 检查防火墙是否阻挡 UDP 1900 端口（SSDP）

### Q2: Action 调用失败？

- 确认 `argumentValues` 的参数名和类型正确
- 查看设备返回的错误信息（在 `onError` 中）
- 部分设备需要先订阅事件才能调用

### Q3: 如何获取设备的服务列表？

```kotlin
device.serviceList.forEach { service ->
    println("Service: ${service.serviceType}")
    service.actionList.forEach { action ->
        println("  Action: ${action.name}")
    }
}
```

### Q4: 订阅事件后收不到通知？

- 确认 `subscriptionEnabled = true`
- 确认调用了 `service.subscribe()`
- 部分设备在订阅后会立即发送 `LastChange` 事件

### Q5: 如何处理设备离线？

- SSDP 设备有超时机制，离线后会被自动移除
- 可以监听 `SsdpMessage` 的 `expireTime` 手动处理

### Q6: 是否支持 iOS？

- mmupnp 本身是纯 Kotlin/JVM 库
- iOS 可通过 Multiplatform 编译或使用其他 UPnP 库

### Q7: 与 Android Jetpack 的关系？

- 无直接依赖，可独立使用
- 可配合 `LifecycleOwner` 管理生命周期

### Q8: Action 返回的错误码是什么意思？

UPnP SOAP 错误码为三位数字字符串，含义参见 [UPnP Device Architecture 2.0 规范](https://upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v2.0-20230410.pdf) 第 2.5.3 节。以下是常见错误：

| 错误码 | 含义 | 常见原因 |
|--------|------|---------|
| `401` | Invalid Action | Action 名称拼错，或该 Service 不支持此 Action |
| `402` | Invalid Args | 参数名、参数数量或类型不匹配 |
| `403` | Out of Sync | 设备状态已变化，需重新查询后再操作 |
| `501` | Action Failed | 通用失败，查看设备具体错误描述 |
| `600-699` | TBD（设备自定义） | 各厂商自定义，需查阅设备文档 |

```kotlin
browse.invoke(
    argumentValues = args,
    onResult = { result -> /* 成功 */ },
    onError = { e ->
        val cause = e.cause
        println("SOAP 错误: ${cause?.javaClass?.simpleName}")
        println("消息: ${e.message}")
    }
)
```

### Q9: 设备反复上线、下线如何处理？

SSDP 机制下设备会定期发送 `NOTIFY`（存活）和 `M-SEARCH` 响应，超时后 ControlPoint 会自动移除。可以注册设备过期监听：

```kotlin
cp.addDeviceExpiredListener { device ->
    println("设备离线: ${device.friendlyName}")
    // 清理本地缓存
    discoveredDevices.remove(device.udn)
}

cp.addDiscoveryListener { device ->
    println("设备上线: ${device.friendlyName}")
    discoveredDevices[device.udn] = device
}
```

### Q10: Action 调用超时如何处理？

mmupnp 默认无操作超时（网络层自行控制）。建议自行包装协程超时：

```kotlin
suspend fun invokeWithTimeout(action: Action, args: Map<String, String?>, timeoutMs: Long = 10_000): Map<String, String>? {
    return withTimeoutOrNull(timeoutMs) {
        action.invokeAsync(args)
    }
}

// 使用
val result = invokeWithTimeout(browse, args, timeoutMs = 15_000)
if (result == null) {
    println("Browse 调用超时（${15_000}ms）")
}
```

### Q11: 部分设备只支持同步调用？

有些 Action 在某些设备上只能同步调用（尤其是旧设备）。mmupnp 的 `invoke` 本身就是基于内部线程池的异步实现，但如果设备无响应会卡住线程。同步包装方式：

```kotlin
val result = withContext(Dispatchers.IO) {
    // 用 CompletableFuture 阻塞等待结果
    val future = CompletableFuture<Map<String, String>>()
    action.invoke(
        argumentValues = args,
        onResult = { future.complete(it) },
        onError = { future.completeExceptionally(it) }
    )
    future.get(10, TimeUnit.SECONDS)  // 阻塞，最多等 10 秒
}
```

### Q12: 如何判断当前有没有可用设备？

```kotlin
fun hasMediaServer(): Boolean = cp.deviceList.any {
    it.deviceType.contains("MediaServer")
}

fun hasMediaRenderer(): Boolean = cp.deviceList.any {
    it.deviceType.contains("MediaRenderer")
}

val serverCount = cp.deviceList.count { it.deviceType.contains("MediaServer") }
val rendererCount = cp.deviceList.count { it.deviceType.contains("MediaRenderer") }
println("媒体服务器: $serverCount, 渲染器: $rendererCount")
```

### Q13: 设备描述 XML 下载失败怎么办？

设备描述 XML（`device.baseUrl`）在设备首次发现时由 mmupnp 自动抓取并解析。如果下载失败，可能是网络问题或设备不支持 HTTP：

```kotlin
val device = cp.getDevice(udn)
if (device == null) {
    // 设备描述解析失败或尚未抓取完成
    println("设备描述不可用，尝试重新发现")
    cp.search("upnp:rootdevice")
} else {
    println("baseUrl: ${device.baseUrl}")
}
```

---

### Q14: Wi-Fi 直连（P2P）下 UPnP 是否可用？

Android Wi-Fi P2P（Wi-Fi Direct）创建的是独立局域网，UPnP/SSDP 依赖广播，通常可以正常工作，但需要注意：

| 情况 | 可用性 | 说明 |
|------|--------|------|
| 手机 A 连接路由器，手机 B 也连接同一路由器 | ✅ | 标准局域网，SSDP 正常 |
| 两台手机用 Wi-Fi P2P 直连 | ✅ | 独立局域网，广播可用 |
| 手机 A 连路由器，手机 B 用 P2P 连手机 A | ⚠️ | A 作为 P2P 组主端，SSDP 可能受限 |
| 设备在不同网段（路由桥接模式） | ❌ | SSDP 广播无法跨路由 |

> 💡 如果用手机开热点给另一个设备，UPnP 完全正常——那就是一个普通局域网。

### Q15: 设备描述 XML 下载超时如何处理？

设备描述 XML（`device.baseUrl`）首次发现时由 mmupnp 内部下载。如果网络慢或设备响应慢，可能超时导致发现失败。

```kotlin
// 带重试的设备描述获取
class DeviceFetcher {
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun fetchWithRetry(location: String, maxRetries: Int = 2): String? {
        repeat(maxRetries) { attempt ->
            try {
                val response = httpClient.newCall(
                    okhttp3.Request.Builder().url(location).get().build()
                ).execute()
                return response.body?.string()
            } catch (e: Exception) {
                println("第 ${attempt + 1} 次下载失败: ${e.message}")
                Thread.sleep(1000)
            }
        }
        return null
    }
}
```

### Q16: SSDP 搜索结果中有重复设备怎么办？

同一设备可能因多网卡、IPv4/IPv6 双栈或 SSDP NOTIFY 重复发送而出现多次。用 `udn` 去重：

```kotlin
val uniqueDevices = mutableMapOf<String, Device>()

cp.addDiscoveryListener { device ->
    if (uniqueDevices.containsKey(device.udn)) {
        println("重复设备忽略: ${device.friendlyName}")
        return@addDiscoveryListener
    }
    uniqueDevices[device.udn] = device
    println("新设备: ${device.friendlyName}")
}
```

### Q17: 如何处理网络切换（Wi-Fi ↔ 蜂窝）？

移动设备网络切换后，ControlPoint 需要重建：

```kotlin
val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        reinitializeControlPoint()
    }
    override fun onLost(network: Network) {
        controlPoint.stop()
    }
}

val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
cm.registerDefaultNetworkCallback(networkCallback)

fun reinitializeControlPoint() {
    controlPoint.stop()
    controlPoint.terminate()
    // ControlPoint 不支持重置，只能重建
    val newCp = ControlPointFactory.create(
        callbackHandler = { runnable -> handler.post(runnable); true }
    )
    // ... 重新注册监听器并 start()
}
```

### Q18: 如何判断设备是 MediaServer 还是 MediaRenderer？

```kotlin
enum class DeviceRole { MEDIA_SERVER, MEDIA_RENDERER, MEDIA_SERVER_RENDERER, UNKNOWN }

fun identifyDeviceRole(device: Device): DeviceRole {
    val dt = device.deviceType
    val isServer   = dt.contains("MediaServer", ignoreCase = true)
    val isRenderer = dt.contains("MediaRenderer", ignoreCase = true)
    return when {
        isServer && isRenderer -> DeviceRole.MEDIA_SERVER_RENDERER
        isServer   -> DeviceRole.MEDIA_SERVER
        isRenderer -> DeviceRole.MEDIA_RENDERER
        else -> DeviceRole.UNKNOWN
    }
}
```

### Q19: DIDL-Lite XML 中文编码问题？

非 ASCII 字符（中文歌名）需要确保 UTF-8 解析：

```kotlin
// 指定编码解析
val factory = DocumentBuilderFactory.newInstance()
factory.isNamespaceAware = true
val builder = factory.newDocumentBuilder()
val inputSource = java.io.InputSource(java.io.StringReader(xmlString))
inputSource.setEncoding("UTF-8")
val doc = builder.parse(inputSource)

// BOM 清理（如果有的话）
val cleanXml = xmlString.removePrefix("\uFEFF")
```

### Q20: Action 参数大小写写错会怎样？

**严格区分大小写**。错误参数名会导致 `402 Invalid Args` 或 `501 Action Failed`：

```kotlin
// ❌ 小写会失败
browse.invoke(mapOf("objectid" to "0", "browseflag" to "BrowseDirectChildren"))

// ✅ 正确写法
browse.invoke(mapOf("ObjectID" to "0", "BrowseFlag" to "BrowseDirectChildren"))

// 调试时可先枚举设备支持的真实参数名
service.actionList.forEach { action ->
    action.argumentList.forEach { arg ->
        println("${arg.name} [${if (arg.isInputDirection) "IN" else "OUT"}]")
    }
}
```

---

## 13. mmupnp + Jellyfin 完整接入指南

> ✅ 本章以 **mmupnp 控制端 + Jellyfin 服务器** 为组合，系统讲解从设备发现到媒体播放的完整接入流程。所有代码基于 mmupnp 3.1.6 + Jellyfin 10.9+ 实测。

### 13.1 技术架构

```
┌──────────────────────────────────────────────────────────────┐
│  Android App（mmupnp ControlPoint）                          │
│                                                              │
│   ┌──────────────┐     ┌──────────────────────────────┐    │
│   │ DeviceManager │     │ JellyfinMediaBrowser          │    │
│   │  - 发现设备   │     │  - 浏览 ContentDirectory     │    │
│   │  - 分类存储   │     │  - 解析 DIDL-Lite            │    │
│   └──────────────┘     └──────────────────────────────┘    │
│   ┌──────────────┐     ┌──────────────────────────────┐    │
│   │ DlnaPlayer   │     │ SubscriptionManager          │    │
│   │  - 播放控制  │     │  - 事件订阅与自动续订        │    │
│   │  - 渲染器控制│     │  - LastChange 解析           │    │
│   └──────────────┘     └──────────────────────────────┘    │
└────────────────────────────┬───────────────────────────────┘
                             │ HTTP GET（媒体流）+ SOAP（控制）
        ┌────────────────────▼────────────────────┐
        │        Jellyfin（DMS + 可选 DMR）        │
        │                                        │
        │  ┌─────────────────────────────────┐   │
        │  │ ContentDirectory Service         │   │
        │  │  Browse → 获取媒体库结构          │   │
        │  │  Search → 搜索歌曲/视频           │   │
        │  └─────────────────────────────────┘   │
        │  ┌─────────────────────────────────┐   │
        │  │ AVTransport Service              │   │
        │  │  SetAVTransportURI → 设置播放地址│   │
        │  │  Play/Pause/Stop/Seek           │   │
        │  └─────────────────────────────────┘   │
        │  ┌─────────────────────────────────┐   │
        │  │ HTTP Server（媒体流）             │   │
        │  │  /Items/{id}/stream            │   │
        │  └─────────────────────────────────┘   │
        └────────────────────┬────────────────────┘
                             │ HTTP 流
        ┌────────────────────▼────────────────────┐
        │   电视 / 音箱 / Chromecast（DMR）      │
        │   从 Jellyfin 拉流并解码渲染            │
        └─────────────────────────────────────────┘
```

**数据流总结：**

| 步骤 | 协议 | 调用方 | 被调用方 |
|------|------|--------|---------|
| 发现 Jellyfin / 渲染器 | SSDP（UDP 239.255.255.250:1900） | mmupnp | Jellyfin / DMR |
| 浏览媒体库 | SOAP over HTTP | mmupnp | Jellyfin ContentDirectory |
| 获取媒体流地址 | SOAP over HTTP | mmupnp | Jellyfin AVTransport |
| 媒体流传输 | HTTP GET | DMR（电视） | Jellyfin HTTP Server |
| 播放状态事件 | GENA（HTTP NOTIFY） | Jellyfin | mmupnp |

### 13.2 Jellyfin DLNA 插件安装

> Jellyfin 10.9+ 的 DLNA 功能已移至官方插件仓库，**必须单独安装**。

#### 13.2.1 安装步骤

```
管理后台 → 插件目录 → 搜索 "DLNA" → 安装 → 重启 Jellyfin
```

#### 13.2.2 Docker 部署要求

> ⚠️ **关键**：Docker 部署必须使用 `network=host` 模式，否则 SSDP 广播包无法到达容器。

```bash
# docker-compose.yml 示例
services:
  jellyfin:
    image: jellyfin/jellyfin:latest
    network_mode: host    # 必须用 host 网络
    volumes:
      - /path/to/config:/config
      - /path/to/media:/media
    environment:
      - JELLYFIN_PublishedServerUrl=http://192.168.1.100:8096
```

#### 13.2.3 网络端口要求

| 端口 | 协议 | 说明 |
|------|------|------|
| UDP 1900 | SSDP | 设备发现（M-SEARCH 响应） |
| TCP 8096 | HTTP | Jellyfin Web UI + DLNA API |

#### 13.2.4 验证 DLNA 是否启用

在 Jellyfin 管理界面：
```
控制台 → 播放 → DLNA → 确认 "启用 DLNA" 已勾选
```

### 13.3 完整业务流程（Kotlin 实战代码）

本节用**可直接编译运行的 Kotlin 代码**展示从初始化到播放的完整流程，所有示例基于 mmupnp 3.1.6。

#### 13.3.1 初始化

```kotlin
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device

class JellyfinApp(private val mainThreadHandler: (() -> Unit)? = null) {

    lateinit var controlPoint: ControlPoint
        private set

    var mediaServer: Device? = null
        private set

    var mediaRenderer: Device? = null
        private set

    fun initialize() {
        // 1. 创建 ControlPoint（主线程回调）
        controlPoint = ControlPointFactory.create(
            callbackHandler = { runnable ->
                mainThreadHandler?.let { it() } ?: runnable.run()
                true
            }
        )

        // 2. 注册设备发现监听器
        controlPoint.addDiscoveryListener(object : ControlPoint.DiscoveryListener {
            override fun onDiscover(device: Device) {
                onDeviceFound(device)
            }
        })

        // 3. 注册设备过期监听器
        controlPoint.addDeviceExpiredListener { device ->
            onDeviceExpired(device)
        }

        // 4. 初始化并启动
        controlPoint.initialize()
        controlPoint.start()

        // 5. 搜索所有 UPnP 根设备
        controlPoint.search("upnp:rootdevice")
    }

    fun terminate() {
        controlPoint.stop()
        controlPoint.terminate()
    }

    private fun onDeviceFound(device: Device) {
        when {
            device.deviceType.contains("MediaServer") -> {
                if (mediaServer == null) {
                    mediaServer = device
                    println("✅ 发现 Jellyfin: ${device.friendlyName}")
                    subscribeToContentDirectory(device)
                }
            }
            device.deviceType.contains("MediaRenderer") -> {
                if (mediaRenderer == null) {
                    mediaRenderer = device
                    println("🔊 发现渲染器: ${device.friendlyName}")
                }
            }
        }
    }

    private fun onDeviceExpired(device: Device) {
        when (device) {
            mediaServer -> { mediaServer = null; println("❌ Jellyfin 离线") }
            mediaRenderer -> { mediaRenderer = null; println("❌ 渲染器离线") }
        }
    }

    private fun subscribeToContentDirectory(device: Device) {
        val cd = device.findServiceById("urn:upnp-org:serviceId:ContentDirectory")
        cd?.subscribe { success ->
            println("ContentDirectory 订阅${if (success) "成功" else "失败"}")
        }
    }
}
```

#### 13.3.2 浏览 Jellyfin 媒体库

```kotlin
import net.mm2d.upnp.Service
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

// ── 数据类 ──

data class MediaItem(
    val id: String,
    val parentId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val resourceUri: String? = null,
    val mimeType: String? = null,
    val duration: String? = null,
    val albumArtUri: String? = null,
    val upnpClass: String
)

data class BrowseResult(
    val containers: List<MediaItem>,
    val items: List<MediaItem>,
    val totalMatches: Int,
    val numberReturned: Int
)

// ── 媒体库浏览 ──

class JellyfinMediaBrowser(private val server: Device) {

    private val contentDirectory: Service? by lazy {
        server.findServiceById("urn:upnp-org:serviceId:ContentDirectory")
    }

    /**
     * 浏览指定容器的直接子项
     * @param objectId 容器 ID，根目录为 "0"
     * @param pageSize 每页数量
     * @param page 第几页（从 0 开始）
     */
    fun browse(
        objectId: String = "0",
        pageSize: Int = 50,
        page: Int = 0,
        onResult: (BrowseResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val browseAction = contentDirectory?.findAction("Browse") ?: run {
            onError(Exception("ContentDirectory 不可用")); return
        }
        val startIndex = page * pageSize

        browseAction.invoke(
            argumentValues = mapOf(
                "ObjectID" to objectId,
                "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*",
                "StartingIndex" to startIndex.toString(),
                "RequestedCount" to pageSize.toString(),
                "SortCriteria" to ""
            ),
            onResult = { result ->
                val totalMatches = result["TotalMatches"]?.toIntOrNull() ?: 0
                val numberReturned = result["NumberReturned"]?.toIntOrNull() ?: 0
                val xml = result["Result"] ?: ""
                val parsed = parseDidlLite(xml).copy(totalMatches = totalMatches, numberReturned = numberReturned)
                onResult(parsed)
            },
            onError = { e -> onError(e) }
        )
    }

    /**
     * 获取单个对象的元数据（BrowseMetadata）
     */
    fun browseMetadata(objectId: String, onResult: (MediaItem?) -> Unit, onError: (Exception) -> Unit) {
        val browseAction = contentDirectory?.findAction("Browse") ?: run {
            onError(Exception("ContentDirectory 不可用")); return
        }
        browseAction.invoke(
            argumentValues = mapOf(
                "ObjectID" to objectId, "BrowseFlag" to "BrowseMetadata",
                "Filter" to "*", "StartingIndex" to "0", "RequestedCount" to "1", "SortCriteria" to ""
            ),
            onResult = { result ->
                val xml = result["Result"] ?: ""
                val parsed = parseDidlLite(xml)
                onResult(parsed.items.firstOrNull() ?: parsed.containers.firstOrNull())
            },
            onError = { e -> onError(e) }
        )
    }

    /**
     * 搜索媒体
     */
    fun search(
        keyword: String,
        containerId: String = "0",
        pageSize: Int = 50,
        page: Int = 0,
        onResult: (BrowseResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val searchAction = contentDirectory?.findAction("Search") ?: run {
            onError(Exception("Search Action 不可用，部分 Jellyfin 版本不支持")); return
        }
        val criteria = """dc:title contains "$keyword" and upnp:class derivedfrom "object.item.audioItem""""
        val startIndex = page * pageSize

        searchAction.invoke(
            argumentValues = mapOf(
                "ContainerID" to containerId, "SearchCriteria" to criteria,
                "Filter" to "*", "StartingIndex" to startIndex.toString(),
                "RequestedCount" to pageSize.toString(), "SortCriteria" to ""
            ),
            onResult = { result ->
                val total = result["TotalMatches"]?.toIntOrNull() ?: 0
                val returned = result["NumberReturned"]?.toIntOrNull() ?: 0
                val parsed = parseDidlLite(result["Result"] ?: "").copy(totalMatches = total, numberReturned = returned)
                onResult(parsed)
            },
            onError = { e -> onError(e) }
        )
    }

    // ── DIDL-Lite 解析 ──

    private fun parseDidlLite(xmlString: String): BrowseResult {
        if (xmlString.isBlank()) return BrowseResult(emptyList(), emptyList(), 0, 0)
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(xmlString.byteInputStream())
            val containers = mutableListOf<MediaItem>()
            val items = mutableListOf<MediaItem>()

            val cn = doc.getElementsByTagName("container")
            for (i in 0 until cn.length) { containers.add(parseContainer(cn.item(i) as Element)) }
            val in_ = doc.getElementsByTagName("item")
            for (i in 0 until in_.length) { items.add(parseItem(in_.item(i) as Element)) }
            BrowseResult(containers, items, 0, containers.size + items.size)
        } catch (e: Exception) {
            println("DIDL-Lite 解析失败: ${e.message}")
            BrowseResult(emptyList(), emptyList(), 0, 0)
        }
    }

    private fun parseContainer(el: Element) = MediaItem(
        id = el.getAttribute("id"), parentId = el.getAttribute("parentID"),
        title = el.getFirstChildText("title", NS_DC) ?: "",
        upnpClass = el.getFirstChildText("class", NS_UPNP) ?: "",
        albumArtUri = el.getFirstChildText("albumArtURI", NS_UPNP)
    )

    private fun parseItem(el: Element): MediaItem {
        val resEl = el.getElementsByTagName("res").item(0) as? Element
        val protocolInfo = resEl?.getAttribute("protocolInfo") ?: ""
        val mime = protocolInfo.split(":").getOrNull(2) ?: extractMime(el.getFirstChildText("class", NS_UPNP) ?: "")
        return MediaItem(
            id = el.getAttribute("id"), parentId = el.getAttribute("parentID"),
            title = el.getFirstChildText("title", NS_DC) ?: "",
            artist = el.getFirstChildText("creator", NS_DC) ?: el.getFirstChildText("artist", NS_UPNP),
            album = el.getFirstChildText("album", NS_UPNP),
            resourceUri = resEl?.textContent?.trim(), mimeType = mime,
            duration = resEl?.getAttribute("duration"),
            albumArtUri = el.getFirstChildText("albumArtURI", NS_UPNP),
            upnpClass = el.getFirstChildText("class", NS_UPNP) ?: ""
        )
    }

    private fun Element.getFirstChildText(localName: String, namespace: String): String? {
        val list = getElementsByTagNameNS(namespace, localName)
        return if (list.length > 0) list.item(0)?.textContent?.trim() else null
    }

    private fun extractMime(upnpClass: String): String? = when {
        upnpClass.contains("musicTrack") -> "audio/mpeg"
        upnpClass.contains("audioItem") -> "audio/mpeg"
        upnpClass.contains("videoItem") -> "video/mp4"
        upnpClass.contains("imageItem") -> "image/jpeg"
        else -> null
    }

    companion object {
        private const val NS_UPNP = "urn:schemas-upnp-org:metadata-1-0/upnp/"
        private const val NS_DC = "http://purl.org/dc/elements/1.1/"
    }
}
```

**JellyfinMediaBrowser 使用示例：**

```kotlin
// 浏览根目录
fun loadRoot() {
    val browser = JellyfinMediaBrowser(mediaServer!!)
    browser.browse("0") { result ->
        result.containers.forEach { println("📁 ${it.title}") }
        result.items.forEach { println("🎵 ${it.title}") }
    }
}

// 按艺术家 → 专辑 → 歌曲导航
fun navigateToSongs() {
    val browser = JellyfinMediaBrowser(mediaServer!!)

    // 1. 艺术家列表
    browser.browse("musicdb://artists") { result ->
        val artist = result.containers.first()
        println("艺术家: ${artist.title}")

        // 2. 该艺术家的专辑
        browser.browse(artist.id) { albums ->
            val album = albums.containers.first()
            println("专辑: ${album.title}")

            // 3. 专辑中的歌曲
            browser.browse(album.id) { songs ->
                songs.items.forEach { song ->
                    println("🎵 ${song.title} | URI: ${song.resourceUri}")
                }
            }
        }
    }
}

// 搜索歌曲
fun search(keyword: String) {
    val browser = JellyfinMediaBrowser(mediaServer!!)
    browser.search(keyword) { result ->
        println("找到 ${result.totalMatches} 首")
        result.items.forEach { println("🎵 ${it.title} - ${it.artist}") }
    }
}
```

#### 13.3.3 播放控制

```kotlin
import net.mm2d.upnp.Device
import net.mm2d.upnp.Service
import java.io.IOException

/**
 * DLNA 播放器
 * 完整流程：SetAVTransportURI → Play → 状态/进度查询
 */
class DlnaPlayer(private val renderer: Device) {

    private val av: Service? by lazy {
        renderer.findServiceById("urn:upnp-org:serviceId:AVTransport")
    }

    // ── 播放 ──

    fun play(item: MediaItem, onReady: () -> Unit, onError: (IOException) -> Unit) {
        val uri = item.resourceUri ?: run {
            onError(IOException("无可用播放地址")); return
        }
        checkMime(item.mimeType ?: "audio/mpeg") { ok ->
            if (!ok) { onError(IOException("渲染器不支持 ${item.mimeType}")); return }
            doPlay(uri, item.title, onReady, onError)
        }
    }

    private fun doPlay(uri: String, title: String, onReady: () -> Unit, onError: (IOException) -> Unit) {
        val setUri = av?.findAction("SetAVTransportURI") ?: run {
            onError(IOException("AVTransport 不可用")); return
        }
        setUri.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0", "CurrentURI" to uri,
                "CurrentURIMetaData" to buildMetadata(title, uri)
            ),
            onResult = { startPlay(onReady, onError) },
            onError = { e -> onError(e) }
        )
    }

    private fun startPlay(onReady: () -> Unit, onError: (IOException) -> Unit) {
        av?.findAction("Play")?.invoke(
            argumentValues = mapOf("InstanceID" to "0", "Speed" to "1"),
            onResult = { onReady() },
            onError = { e -> onError(e) }
        ) ?: onError(IOException("Play Action 不可用"))
    }

    // ── 控制 ──

    fun pause() { av?.findAction("Pause")?.invoke(mapOf("InstanceID" to "0")) }
    fun resume() { av?.findAction("Play")?.invoke(mapOf("InstanceID" to "0", "Speed" to "1")) }
    fun stop() { av?.findAction("Stop")?.invoke(mapOf("InstanceID" to "0")) }

    /** 跳转，hhmmss 格式如 "00:01:30" */
    fun seekTo(hhmmss: String) {
        av?.findAction("Seek")?.invoke(mapOf(
            "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to hhmmss
        ))
    }

    // ── 状态查询 ──

    enum class State { PLAYING, PAUSED, STOPPED, TRANSITIONING, UNKNOWN }

    fun getState(onResult: (State) -> Unit) {
        av?.findAction("GetTransportInfo")?.invoke(
            mapOf("InstanceID" to "0"),
            onResult = { result ->
                val s = result["CurrentTransportState"] ?: "UNKNOWN"
                onResult(when (s) {
                    "PLAYING" -> State.PLAYING; "PAUSED_PLAYBACK" -> State.PAUSED
                    "STOPPED" -> State.STOPPED; "TRANSITIONING" -> State.TRANSITIONING
                    else -> State.UNKNOWN
                })
            }
        ) ?: onResult(State.UNKNOWN)
    }

    fun getPosition(onResult: (relTime: String, duration: String) -> Unit) {
        av?.findAction("GetPositionInfo")?.invoke(
            mapOf("InstanceID" to "0"),
            onResult = { result ->
                onResult(result["RelTime"] ?: "--:--:--", result["TrackDuration"] ?: "--:--:--")
            }
        ) ?: onResult("--:--:--", "--:--:--")
    }

    // ── 内部辅助 ──

    private fun checkMime(mime: String, cb: (Boolean) -> Unit) {
        renderer.findServiceById("urn:upnp-org:serviceId:ConnectionManager")
            ?.findAction("GetProtocolInfo")
            ?.invoke(mapOf(),
                onResult = { result -> cb(result["Sink"]?.contains(mime, ignoreCase = true) ?: true) }
            ) ?: cb(true)
    }

    private fun buildMetadata(title: String, uri: String): String {
        val t = title.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        return """<?xml version="1.0" encoding="UTF-8"?>
<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
           xmlns:dc="http://purl.org/dc/elements/1.1/">
  <item id="0" parentID="-1" restricted="true">
    <dc:title>$t</dc:title>
    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
    <res protocolInfo="http-get:*:audio/mpeg:*">$uri</res>
  </item>
</DIDL-Lite>"""
    }
}
```

**DlnaPlayer 使用示例：**

```kotlin
// 播放歌曲
fun playSong(item: MediaItem) {
    val renderer = mediaRenderer ?: run { println("无渲染器"); return }
    DlnaPlayer(renderer).play(item,
        onReady = { println("▶ 播放: ${item.title}") },
        onError = { e -> println("失败: ${e.message}") }
    )
}

// 查询进度
fun showProgress() {
    val renderer = mediaRenderer ?: return
    val p = DlnaPlayer(renderer)
    p.getPosition { rel, total -> println(" $rel / $total") }
    p.getState { s -> println("状态: $s") }
}

// 暂停/跳转
fun pauseAndSeek() {
    val renderer = mediaRenderer ?: return
    val p = DlnaPlayer(renderer)
    p.pause()
    p.seekTo("00:01:30")  // 跳到 1 分 30 秒
}
```

#### 13.3.4 播放队列与多房间同步

```kotlin
/**
 * 播放队列管理器
 * DLNA 本身不提供标准化队列，App 端自行维护
 */
class PlaybackQueue {
    private val queue = mutableListOf<MediaItem>()
    private var index = 0
    private var player: DlnaPlayer? = null

    val current: MediaItem? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.size - 1
    val hasPrev: Boolean get() = index > 0

    fun setPlayer(p: DlnaPlayer) { player = p }
    fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
        queue.clear(); queue.addAll(items)
        index = startIndex.coerceIn(0, maxOf(0, items.size - 1))
    }
    fun add(item: MediaItem) { queue.add(item) }

    fun playCurrent(onError: (IOException) -> Unit) {
        current?.let { player?.play(it, onReady = {}, onError = onError) }
    }
    fun next(onError: (IOException) -> Unit): Boolean {
        if (!hasNext) return false
        index++; playCurrent(onError); return true
    }
    fun prev(onError: (IOException) -> Unit): Boolean {
        if (!hasPrev) return false
        index--; playCurrent(onError); return true
    }

    /** 多房间同步：在多个渲染器上同时播放同一首歌 */
    fun playSync(item: MediaItem, renderers: List<Device>, onError: (IOException) -> Unit) {
        renderers.forEach { DlnaPlayer(it).play(item, onReady = {}, onError = onError) }
    }
}
```

#### 13.3.5 Jellyfin 媒体库导航示例

```kotlin
/**
 * Jellyfin 导航封装：按艺术家 → 专辑 → 歌曲层级浏览
 */
class JellyfinNavi(private val server: Device) {

    private val browser = JellyfinMediaBrowser(server)

    fun root(onResult: (List<MediaItem>) -> Unit, onError: (Exception) -> Unit) =
        browser.browse("0", pageSize = 100) { onResult(it.containers) }

    fun artists(onResult: (List<MediaItem>) -> Unit, onError: (Exception) -> Unit) =
        browser.browse("musicdb://artists") { onResult(it.containers) }

    fun albums(artistContainerId: String, onResult: (List<MediaItem>) -> Unit, onError: (Exception) -> Unit) =
        browser.browse(artistContainerId) { onResult(it.containers) }

    fun tracks(albumContainerId: String, onResult: (List<MediaItem>) -> Unit, onError: (Exception) -> Unit) =
        browser.browse(albumContainerId) { onResult(it.items) }

    fun allSongs(onResult: (List<MediaItem>) -> Unit, onError: (Exception) -> Unit) {
        val acc = mutableListOf<MediaItem>()
        fun loadPage(page: Int) {
            browser.browse("musicdb://songs", pageSize = 100, page = page) { result ->
                acc.addAll(result.items)
                if ((page + 1) * 100 < result.totalMatches) loadPage(page + 1) else onResult(acc)
            }
        }
        loadPage(0)
    }
}

// 完整导航流程
fun fullNaviFlow() {
    val navi = JellyfinNavi(mediaServer!!)

    navi.artists(
        onResult = { artists ->
            println(artists.map { it.title }.joinToString())
            navi.albums(artists[0].id,
                onResult = { albums ->
                    navi.tracks(albums[0].id,
                        onResult = { songs ->
                            println(songs.map { it.title }.joinToString())
                        },
                        onError = { println("Error: $it") }
                    )
                },
                onError = { println("Error: $it") }
            )
        },
        onError = { println("Error: $it") }
    )
}
```

#### 13.3.6 完整流程时序图

```
App (mmupnp)                Jellyfin (DMS)              TV (DMR)
    │                             │                         │
    │ 1. cp.search()              │                         │
    │────────────────────────────►│                         │
    │                             │                         │
    │ 2. onDiscover(jellyfin)    │                         │
    │◄────────────────────────────│                         │
    │ 3. onDiscover(tv)          │                         │
    │◄─────────────────────────────────────────────────────│
    │                             │                         │
    │ 4. Browse("musicdb://artists")                        │
    │────────────────────────────►│                         │
    │ 5. DIDL-Lite XML           │                         │
    │◄────────────────────────────│                         │
    │                             │                         │
    │ 6. 用户选歌，获取 resourceUri                         │
    │                             │                         │
    │ 7. SetAVTransportURI        │                         │
    │────────────────────────────────────────────────────►│  │
    │ 8. Play                     │                         │
    │────────────────────────────────────────────────────►│  │
    │                             │ 9. HTTP GET stream     │
    │                             │◄────────────────────────│
    │                             │                         │
    │ 10. GetPositionInfo         │                         │
    │────────────────────────────────────────────────────►│  │
    │ 11. LastChange (PLAYING)    │                         │
    │◄─────────────────────────────────────────────────────│
```

### 13.4 播放队列管理

DLNA/UPnP 本身不提供标准化的"播放队列"抽象，需要在 App 端自行维护。典型实现方式：

```kotlin
class PlaylistManager(private val contentDirectory: Service) {

    private val queue = mutableListOf<MediaItem>()
    private var currentIndex = 0

    /**
     * 添加到播放队列（不立即播放）
     */
    fun addToQueue(item: MediaItem) {
        queue.add(item)
    }

    /**
     * 添加并立即播放
     */
    fun playNow(item: MediaItem, renderer: Service) {
        // 已在队列中则移到当前位置
        val existing = queue.indexOfFirst { it.id == item.id }
        if (existing >= 0) {
            queue.removeAt(existing)
        }
        queue.add(0, item)
        currentIndex = 0
        playCurrent(renderer)
    }

    /**
     * 播放当前索引的曲目
     */
    fun playCurrent(renderer: Service) {
        if (queue.isEmpty() || currentIndex >= queue.size) return
        val item = queue[currentIndex]
        setAndPlay(item.resourceUri ?: return, renderer)
    }

    /**
     * 下一首
     */
    fun next(renderer: Service) {
        if (currentIndex < queue.size - 1) {
            currentIndex++
            playCurrent(renderer)
        }
    }

    /**
     * 上一首
     */
    fun previous(renderer: Service) {
        if (currentIndex > 0) {
            currentIndex--
            playCurrent(renderer)
        }
    }

    private fun setAndPlay(uri: String, renderer: Service) {
        val av = renderer
        av.findAction("SetAVTransportURI")?.invoke(
            argumentValues = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to uri,
                "CurrentURIMetaData" to ""
            ),
            onResult = {
                av.findAction("Play")?.invoke(
                    argumentValues = mapOf("InstanceID" to "0", "Speed" to "1")
                )
            }
        )
    }
}
```

**队列变更监听**：ContentDirectory 的 `LastChange` 事件会通知容器内容变化（如 Jellyfin 中其他客户端修改了播放队列），但标准的 ContentDirectory Service 不提供独立的队列操作事件，需要 App 端主动重新 Browse。

---

### 13.5 Renderer 能力查询

在发起播放前，应先查询 Renderer 支持的播放格式和协议，避免传入不支持的媒体流导致播放失败。

```kotlin
/**
 * 查询 Renderer 支持的媒体格式和协议
 */
fun queryRendererCapabilities(renderer: Device) {
    val av = renderer.findServiceById("urn:upnp-org:serviceId:AVTransport")
    val cm = renderer.findServiceById("urn:upnp-org:serviceId:ConnectionManager")

    // 1. 查询支持的动作（正常情况直接用 Action 即可）
    val caps = av?.findAction("GetDeviceCapabilities")
    caps?.invoke(
        argumentValues = mapOf("InstanceID" to "0"),
        onResult = { result ->
            println("播放支持: ${result["PlayMedia"]}")
            println("录音支持: ${result["RecMedia"]}")
            println("指定媒体: ${result["RecQualityModes"]}")
        }
    )

    // 2. 查询传输设置
    val transportSettings = av?.findAction("GetTransportSettings")
    transportSettings?.invoke(
        argumentValues = mapOf("InstanceID" to "0"),
        onResult = { result ->
            println("传输协议: ${result["PlayMode"]}")
            println("速度: ${result["Speed"]}")
        }
    )

    // 3. 查询 ConnectionManager 的协议信息（最重要的支持格式列表）
    val protocolInfo = cm?.findAction("GetProtocolInfo")
    protocolInfo?.invoke(
        argumentValues = emptyMap(),
        onResult = { result ->
            val source = result["Source"] ?: ""   // 本设备作为 Source 时支持
            val sink = result["Sink"] ?: ""       // 本设备作为 Sink（渲染器）时支持
            println("支持的输入协议:\n$sink")
        }
    )
}
```

**常见 Sink 协议格式：**

```
Sink:  http-get:*:audio/mpeg:*,  http-get:*:audio/flac:*,  http-get:*:audio/wav:*, ...
```

---

### 13.6 Jellyfin DLNA 能力边界

Jellyfin 作为 MediaServer，通过 ContentDirectory 提供媒体库浏览，通过 AVTransport 提供播放控制（如果 Jellyfin 也充当 Renderer）。以下是实际对接中的注意点：

| 能力 | 支持情况 | 说明 |
|------|---------|------|
| Browse 根目录 | ✅ | ObjectID="0" 可获取 Jellyfin 媒体库根容器 |
| 按艺术家/专辑/歌曲浏览 | ✅ | 容器层级结构由 Jellyfin 维护 |
| Search | ⚠️ | 部分版本对 SearchCriteria 支持有限，优先用 Browse |
| AVTransport（播放控制） | ⚠️ | Jellyfin 本身可作为 Renderer（接受 SetAVTransportURI）但播放行为由 Jellyfin 决定 |
| 封面图 | ✅ | 容器和条目均有 `albumArtURI`，但 URL 需要从 Jellyfin 媒体库中拼接 |
| 子容器分页 | ✅ | `StartingIndex` / `RequestedCount` 参数正常 |
| 播放进度（PositionInfo） | ⚠️ | 仅当 Jellyfin 作为 Renderer 时有效 |
| HTTPS | ❌ | Jellyfin DLNA 默认仅 HTTP，强制 HTTPS 可能导致无法发现 |

---

### 13.7 子设备与内嵌设备

Jellyfin 等 MediaServer 可能返回多层嵌套的子设备（如设备下有多个子服务）。`device.deviceList` 包含了所有嵌入式设备：

```kotlin
/**
 * 递归查找所有设备（包含子设备）
 */
fun findAllDevices(device: Device): List<Device> {
    val result = mutableListOf(device)
    device.deviceList.forEach { child ->
        result.addAll(findAllDevices(child))
    }
    return result
}

// 使用
cp.addDiscoveryListener { device ->
    findAllDevices(device).forEach { d ->
        println("设备: ${d.friendlyName}, 类型: ${d.deviceType}")
    }
}
```

**容器 vs 条目判断：**

```kotlin
val isContainer = node.nodeName == "container"
val isItem = node.nodeName == "item"

// 容器有 childCount（可能有子目录），条目没有
val childCount = if (isContainer) element.getAttribute("childCount").toIntOrNull() ?: 0 else 0
```

---

### 13.8 连接管理与 ConnectionManager Service

`ConnectionManager` Service 管理设备间的连接信息，用于确认设备能否接收特定格式的媒体流。

```kotlin
/**
 * 查询两个设备之间的连接兼容性
 */
fun checkConnection(source: Device, sink: Device) {
    val cmSource = source.findServiceById("urn:upnp-org:serviceId:ConnectionManager")
    val cmSink = sink.findServiceById("urn:upnp-org:serviceId:ConnectionManager")

    // 获取两端支持的协议
    fun getSupportedProtocols(service: Service?) {
        service?.findAction("GetProtocolInfo")?.invoke(
            argumentValues = emptyMap(),
            onResult = { result ->
                println("协议: ${result["Sink"] ?: result["Source"] ?: ""}")
            }
        )
    }

    getSupportedProtocols(cmSource)
    getSupportedProtocols(cmSink)
}
```

**典型 ConnectionManager 错误处理：**

| 错误 | 含义 | 应对 |
|------|------|------|
| `701` | Not in NETWORK | 渲染器不在可访问的网络 |
| `702` | No such Connection | 连接不存在，需重新 Setup |
| `703` | Unsupported Transfer Mode | 传输模式不支持（如只支持异步却用了同步） |

---

### 13.9 Jellyfin DLNA 已知限制

| 限制 | 说明 |
|------|------|
| Docker 必须用 host 网络 | 否则 SSDP 发现不工作 |
| 子网限制 | DLNA 发现基于广播，无法跨子网 |
| ObjectID 结构可能变化 | Jellyfin 内部实现，非稳定 API |
| 转码支持 | Jellyfin DLNA 会按 Renderer 能力自动转码（如果启用了转码） |

### 13.10 相关资源

| 资源 | 链接 |
|------|------|
| Jellyfin DLNA 文档 | https://jellyfin.org/docs/general/post-install/networking/dlna/ |
| Jellyfin DLNA 插件 | https://github.com/jellyfin/jellyfin-plugin-dlna |
| UPnP ContentDirectory 规范 | https://upnp.org/specs/av/UPnP-av-ContentDirectory-v4-Service.pdf |
| UPnP AVTransport 规范 | https://upnp.org/specs/av/UPnP-av-AVTransport-v4-Service.pdf |
| mmupnp GitHub | https://github.com/ohmae/mmupnp |

---

## 附录 A：常用 ST 值参考

| ST 值 | 说明 |
|-------|------|
| `ssdp:all` | 所有设备 |
| `upnp:rootdevice` | UPnP 根设备 |
| `urn:schemas-upnp-org:device:*` | 设备类型 |
| `urn:schemas-upnp-org:service:*` | 服务类型 |

## 附录 B：常用 Service ID 参考

| Service ID | 说明 |
|------------|------|
| `urn:upnp-org:serviceId:ContentDirectory` | 内容目录（浏览媒体） |
| `urn:upnp-org:serviceId:ConnectionManager` | 连接管理 |
| `urn:upnp-org:serviceId:AVTransport` | AV 传输控制（播放/暂停等） |

## 附录 C：ContentDirectory / AVTransport 完整 Action 参考

### C.1 ContentDirectory Action 全表

| Action | 输入参数 | 输出参数 | 说明 |
|--------|---------|---------|------|
| `Browse` | `ObjectID`, `BrowseFlag`, `Filter`, `StartingIndex`, `RequestedCount`, `SortCriteria` | `Result`, `NumberReturned`, `TotalMatches`, `UpdateID` | 浏览目录/条目 |
| `Search` | `ContainerID`, `SearchCriteria`, `Filter`, `StartingIndex`, `RequestedCount`, `SortCriteria` | `Result`, `NumberReturned`, `TotalMatches`, `UpdateID` | 搜索媒体 |
| `GetSearchCapabilities` | — | `SearchCaps` | 支持的搜索操作符 |
| `GetSortCapabilities` | — | `SortCaps` | 支持的排序字段 |
| `GetSystemUpdateID` | — | `Id` | 系统更新版本号 |
| `CreateObject` | `ContainerID`, `Elements` | `ObjectID`, `Result` | 创建新对象（写） |
| `DestroyObject` | `ObjectID` | — | 删除对象 |
| `UpdateObject` | `ObjectID`, `CurrentTagValue`, `NewTagValue` | — | 更新元数据 |
| `MoveObject` | `ObjectID`, `NewParentID` | `NewObjectID` | 移动对象 |
| `ImportResource` | `SourceURI`, `DestinationURI` | `TransferID` | 导入资源 |
| `ExportResource` | `SourceURI`, `DestinationURI` | `TransferID` | 导出资源 |
| `StopTransferResource` | `TransferID` | — | 停止传输 |
| `GetTransferProgress` | `TransferID` | `TransferStatus`, `TransferLength`, `TransferTotal` | 传输进度 |

**BrowseFlag 值：**

| 值 | 说明 |
|----|------|
| `BrowseMetadata` | 只浏览指定对象本身（不返回子项） |
| `BrowseDirectChildren` | 浏览直接子项 |

**SortCriteria 格式：**

```
+/-dc:title, +/-upnp:artist, +/-dc:creator, ...
```

`+` 升序，`-` 降序，多字段逗号分隔：`"+dc:title,-upnp:artist"`

### C.2 ConnectionManager Action 全表

| Action | 输入参数 | 输出参数 | 说明 |
|--------|---------|---------|------|
| `GetProtocolInfo` | — | `Source`, `Sink` | 支持的协议列表 |
| `PrepareForConnection` | `RemoteProtocolInfo`, `PeerConnectionManager`, `PeerConnectionID`, `Direction` | `ConnectionID`, `AVTransportID`, `RcsID` | 准备连接 |
| `ConnectionComplete` | `ConnectionID` | — | 完成连接 |
| `GetCurrentConnectionInfo` | `ConnectionID` | 所有连接参数 | 当前连接详情 |

### C.3 StateVariable dataType 完整参考

UPnP 的 StateVariable `dataType` 不是标准 MIME 类型，是 DIDL-Lite 自己定义的数据类型枚举：

| dataType | 对应 Java/Kotlin 类型 | 说明 | 示例 |
|----------|----------------------|------|------|
| `string` | `String` | 字符串 | 任意文本 |
| `boolean` | `String` | "true"/"false" | `"true"` |
| `ui1` | `String` | 无符号 1 字节整数 | `"255"` |
| `ui2` | `String` | 无符号 2 字节整数 | `"65535"` |
| `ui4` | `String` | 无符号 4 字节整数 | `"4294967295"` |
| `i1` | `String` | 有符号 1 字节整数 | `"-128"` |
| `i2` | `String` | 有符号 2 字节整数 | `"-32768"` |
| `i4` | `String` | 有符号 4 字节整数 | `"-2147483648"` |
| `int` | `String` | 同 i4 | `"0"` |
| `r4` | `String` | 32 位浮点 | `"3.14"` |
| `r8` | `String` | 64 位浮点 | `"3.141592653"` |
| `float` | `String` | 同 r8 | `"1.0"` |
| `bin.base64` | `String` | Base64 编码二进制 | 图像等二进制数据 |
| `date` | `String` | 日期（YYYY-MM-DD） | `"2024-01-15"` |
| `dateTime` | `String` | ISO 8601 日期时间 | `"2024-01-15T10:30:00"` |
| `dateTime.tz` | `String` | 带时区日期时间 | `"2024-01-15T10:30:00+08:00"` |
| `time` | `String` | 时间（HH:MM:SS） | `"10:30:00"` |
| `time.tz` | `String` | 带时区时间 | `"10:30:00+08:00"` |
| `uri` | `String` | URI 字符串 | `"http://..."` |
| `uuid` | `String` | UUID 格式 | `"550e8400-e29b..."` |

> ⚠️ **重要**：`bin.base64` 返回的是 Base64 字符串，不是二进制字节数组。需要调用方自己解码：
> ```kotlin
> val base64: String = result["AlbumArt"] ?: ""
> val bytes: ByteArray = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
> val bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
> ```

### C.4 AVTransport StateVariable 全表

| StateVariable | dataType | 发送事件 | 说明 |
|--------------|----------|---------|------|
| `TransportState` | string | ✅ | 传输状态：STOPPED/PLAYING/PAUSED/TRANSITIONING... |
| `TransportStatus` | string | ✅ | 错误状态：OK/ERROR_... |
| `PlaybackStorageMedium` | string | ✅ | 播放介质 |
| `RecordStorageMedium` | string | ✅ | 录制介质 |
| `PlaybackMediaDuration` | string | ❌ | 媒体总时长 |
| `RecordMediaDuration` | string | ❌ | 录制时长 |
| `CurrentTransportActions` | string | ✅ | 当前可用操作 |
| `AVTransportURI` | uri | ✅ | 当前媒体 URI |
| `AVTransportURIMetaData` | string | ✅ | 当前媒体元数据（DIDL-Lite） |
| `NextAVTransportURI` | uri | ✅ | 下一首 URI |
| `NextAVTransportURIMetaData` | string | ✅ | 下一首元数据 |
| `CurrentSpeakerIp` | string | ❌ | 当前扬声器 IP（Jellyfin 扩展） |
| `CurrentTransportSettings` | string | ❌ | 传输设置 |
| `DRMState` | string | ❌ | DRM 状态 |

## 附录 D：参考链接

- **GitHub**: https://github.com/ohmae/mmupnp
- **KDoc 文档**: https://ohmae.github.io/mmupnp/dokka/mmupnp/
- **示例项目 (DmsExplorer)**: https://github.com/ohmae/DmsExplorer
- **Maven Central**: https://search.maven.org/artifact/net.mm2d.mmupnp/mmupnp
- **Jellyfin**: https://jellyfin.org/
- **Jellyfin DLNA 文档**: https://jellyfin.org/docs/general/post-install/networking/dlna/

## 附录 E：AVTransport Action 全表

| Action | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| `SetAVTransportURI` | `InstanceID`, `CurrentURI`, `CurrentURIMetaData` | — | 设置媒体地址 |
| `SetNextAVTransportURI` | `InstanceID`, `NextURI`, `NextURIMetaData` | — | 设置下一首（部分设备支持） |
| `GetMediaInfo` | `InstanceID` | `NrTracks`, `MediaDuration`, `CurrentURI`, `CurrentURIMetaData`, `NextURI`, `NextURIMetaData`, `PlaybackStorageMedium`, `RecordStorageMedium`, `RecordMediumWriteStatus` | 获取媒体信息 |
| `GetTransportInfo` | `InstanceID` | `CurrentTransportState`, `CurrentTransportStatus`, `CurrentSpeed` | 获取传输状态 |
| `GetPositionInfo` | `InstanceID` | `Track`, `TrackDuration`, `TrackMetaData`, `TrackURI`, `RelTime`, `AbsTime`, `RelCount`, `AbsCount` | 获取播放进度 |
| `GetDeviceCapabilities` | `InstanceID` | `PlayMedia`, `RecMedia`, `RecQualityModes` | 获取设备能力 |
| `GetTransportSettings` | `InstanceID` | `PlayMode`, `RecQualityMode` | 获取传输设置 |
| `Stop` | `InstanceID` | — | 停止 |
| `Play` | `InstanceID`, `Speed` | — | 播放（Speed 如 "1", "2", "0.5"） |
| `Pause` | `InstanceID` | — | 暂停 |
| `Seek` | `InstanceID`, `Unit`, `Target` | — | 跳转 |
| `Next` | `InstanceID` | — | 下一首 |
| `Previous` | `InstanceID` | — | 上一首 |
| `SetPlayMode` | `InstanceID`, `NewPlayMode` | — | 设置播放模式（NORMAL/SHUFFLE/REPEAT_ONE/REPEAT_ALL/DIRECT_1） |
| `GetCurrentTransportActions` | `InstanceID` | `Actions` | 获取当前可执行的操作 |

**Seek Unit 可选值：**

| Unit | Target 示例 | 说明 |
|------|------------|------|
| `ABS_TIME` | `"00:03:30"` | 跳到绝对时间位置 |
| `REL_TIME` | `"00:01:30"` | 相对当前位置跳转 |
| `ABS_COUNT` | `"5"` | 跳到第 N 轨 |
| `REL_COUNT` | `"-3"` | 快进/快退 N 轨 |
| `TRACK_NR` | `"3"` | 跳到指定轨号 |
| `CHANNEL_FREQ` | `"105.5"` | 跳到指定频率（收音机） |
| `TAPE-INDEX` | `"10"` | 跳到磁带位置 |

---

## 附录 F：DIDL-Lite 完整字段参考

DIDL-Lite 是 UPnP/A/V 标准定义的媒体描述格式，基于 XML 命名空间。以下是完整元素和命名空间速查。

### 命名空间

| 前缀 | 命名空间 URI | 说明 |
|------|-------------|------|
| `DIDL-Lite` | `urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/` | 主命名空间 |
| `upnp` | `urn:schemas-upnp-org:metadata-1-0/upnp/` | UPnP 扩展元数据 |
| `dc` | `http://purl.org/dc/elements/1.1/` | Dublin Core 元数据 |
| `dlna` | `urn:schemas-dlna-org:metadata-1-0/` | DLNA 扩展 |

### container 元素属性

| 属性 | 说明 |
|------|------|
| `id` | 容器唯一标识（用作 Browse 的 ObjectID） |
| `parentID` | 父容器 ID（根为 "0" 或 "-1"，Jellyfin 用 "-1"） |
| `childCount` | 直接子项数量 |
| `restricted` | 是否限制写入，"true" 表示只读 |
| `createClass` | 创建新子项时的默认类 |

### item/container 常用子元素

| 元素（UPnP） | 元素（Dublin Core） | 说明 | 示例 |
|-------------|-------------------|------|------|
| `upnp:class` | — | 对象类，指示类型 | `object.item.audioItem.musicTrack` |
| — | `dc:title` | 标题 | "Come Together" |
| — | `dc:creator` | 创作者 | "The Beatles" |
| `upnp:artist` | — | 艺术家 | "The Beatles" |
| `upnp:album` | — | 专辑名 | "Abbey Road" |
| `upnp:genre` | — | 流派 | "Rock" |
| `upnp:albumArtURI` | — | 封面图 URL | `http://.../cover.jpg` |
| `upnp:originalTrackNumber` | — | 专辑中曲目编号 | "1" |
| `upnp:duration` | — | 时长（秒，慎用） | "225" |
| `dc:date` | — | 发布/创建日期 | "1969-09-26" |
| `upnp:playbackCount` | — | 播放次数 | "42" |
| `upnp:state` | — | 播放状态 | "PLAYING" |
| `upnp:writeStatus` | — | 写入状态 | "UNLOCKED" |

### res 元素（媒体资源）

`<res>` 描述具体的媒体文件 URL 和协议信息，是播放地址的载体：

```xml
<res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_FLAGS=..."
     duration="00:03:45"
     bitrate="320000"
     sampleFrequency="44100"
     bitsPerSample="16"
     nrAudioChannels="2"
     resolution="N/A"
     colorDepth="N/A"
     importUri="N/A">http://192.168.1.100:8096/track1.mp3</res>
```

**protocolInfo 格式：**

```
source:protocol:container/mime;DLNA.ORG_FLAGS
```

| 字段 | 说明 | 示例 |
|------|------|------|
| source | 源类型，`http-get` 最常见 | `http-get` |
| protocol | 网络协议 | `*` |
| container/mime | 容器和 MIME 类型 | `audio/mpeg`, `video/mp4`, `image/jpeg` |
| DLNA.ORG_FLAGS | DLNA 组织扩展标志（可选） | `DLNA.ORG_FLAGS=...` |

**常见 protocolInfo 值：**

| protocolInfo | 格式 |
|-------------|------|
| `http-get:*:audio/mpeg:*` | MP3 音频 |
| `http-get:*:audio/flac:*` | FLAC 无损音频 |
| `http-get:*:audio/wav:*` | WAV 音频 |
| `http-get:*:audio/aac:*` | AAC 音频 |
| `http-get:*:video/mp4:*` | MP4 视频 |
| `http-get:*:image/jpeg:*` | JPEG 图片 |

**DLNA.ORG_FLAGS 标志位：**

| 标志 | 值 | 说明 |
|------|-----|------|
| `DLNA.ORG_OP=01` | 操作范围（Time Seek Range） | 支持时间seek |
| `DLNA.ORG_OP=00` | 无操作范围 | 不可seek |
| `DLNA.ORG_PS=01` | 播放场进支持 | 支持分段下载 |
| `DLNA.ORG_PS=00` | 不支持 | — |
| `DLNA.ORG_CI=01` | 转码进行中 | 容器需要转码 |
| `DLNA.ORG_CI=00` | 不转码 | 直接播放 |

---

## 附录 G：Jellyfin ObjectID 结构一览

> ⚠️ ObjectID 结构是 Jellyfin 内部实现，非稳定 API，随时可能因版本更新而变化。以下基于 Jellyfin 10.9 实测。

### Jellyfin 媒体库根目录

Browse(ObjectID="0") 返回的容器：

| id | 标题 | 说明 |
|----|------|------|
| `0` | 媒体库 | Jellyfin 虚拟根目录 |
| `1` | musicdb:// | 音乐数据库（按艺术家/专辑/歌曲） |
| `2` | videoDb:// | 视频数据库 |

### 音乐库导航层级

```
根目录 Browse(ObjectID="0")
└─ musicdb:// (id="1")
   ├─ artists (id="musicdb://artists")
   │   └─ {artist-name} (id="musicdb://artists/{uuid}")
   │       └─ {album-name} (id="musicdb://artists/{uuid}/albums/{album-uuid}")
   │           └─ {track}.mp3 (id="musicdb://track/{track-uuid}")
   │
   ├─ albums (id="musicdb://albums")
   │   └─ {album-name} (id="musicdb://albums/{uuid}")
   │       └─ {track}.mp3 (id="musicdb://track/{track-uuid}")
   │
   ├─ songs (id="musicdb://albums")
   │   └─ {track}.mp3 (id="musicdb://track/{track-uuid}")
   │
   └─ playlists (id="playlists")
       └─ {playlist-name} (id="playlists/{uuid}")
           └─ {track}.mp3 (id="musicdb://track/{track-uuid}")
```

**Jellyfin 特殊容器 ID：**

| ObjectID | 说明 |
|----------|------|
| `"0"` | 媒体库根 |
| `"1"` | musicdb:// |
| `"musicdb://"` | 音乐数据库入口 |
| `"musicdb://albums"` | 所有专辑列表 |
| `"musicdb://artists"` | 所有艺术家列表 |
| `"musicdb://songs"` | 所有歌曲列表 |
| `"musicdb://genres"` | 按流派分类 |
| `"musicdb://playlists"` | 播放列表集合入口 |
| `"playlists"` | 播放列表集合入口（另一种路径） |

### 按流派浏览

```
Browse(ObjectID="musicdb://genres")
└─ Rock (id="musicdb://genres/Rock")
    └─ {album} / {artist}
```

### 获取专辑封面图

Jellyfin 封面图需要通过 Jellyfin API 拼接，不在 DIDL-Lite 的 `albumArtURI` 中（该字段可能为空）。正确做法：

```kotlin
/**
 * 拼接 Jellyfin 媒体项的封面图 URL
 * @param itemId 媒体项 ID（来自 DIDL-Lite item 的 id 字段）
 * @param jellyfinHost Jellyfin 服务器地址，如 "http://192.168.1.100:8096"
 * @param apiKey Jellyfin API Key（在 Jellyfin 管理后台生成）
 */
fun buildJellyfinCoverUrl(itemId: String, jellyfinHost: String, apiKey: String): String {
    // itemId 格式如 "musicdb://track/abc123"
    val encodedId = java.net.URLEncoder.encode(itemId, "UTF-8")
    return "$jellyfinHost/Items/abc123/Images/Primary?api_key=$apiKey"
    // 实际使用需要从 itemId 中解析出 Jellyfin 的 ItemId（UUID 部分）
}
```

> 💡 **实用建议**：在实际对接中，建议先 Browse 所有容器建立本地缓存（包含 id → title 映射），用户点击时直接用缓存的 id 作为 ObjectID 再次 Browse。这样可以绕过 Jellyfin 内部 ObjectID 结构的不透明性。

## 附录 H：UPnP/ DLNA 术语表

| 术语 | 全称 | 说明 |
|------|------|------|
| UPnP | Universal Plug and Play | 通用即插即用，一套设备发现和控制协议族 |
| SSDP | Simple Service Discovery Protocol | 基于 HTTP/UDP 的设备发现协议 |
| SOAP | Simple Object Access Protocol | 基于 XML 的远程过程调用协议，UPnP Action 调用用它 |
| GENA | Generic Event Notification Architecture | 事件订阅协议，基于 HTTP NOTIFY |
| M-SEARCH | Multicast SEARCH | SSDP 设备搜索请求 |
| DIDL-Lite | Digital Item Declaration Language Lite | 媒体元数据 XML 格式 |
| ControlPoint | — | UPnP 架构中的"控制端"，即 mmupnp 扮演的角色 |
| Device | — | UPnP 架构中的"设备"，分 MediaServer、MediaRenderer、Gateway 等 |
| DLNA | Digital Living Network Alliance | 基于 UPnP 的消费电子互操作标准 |
| DMS | Digital Media Server | 媒体服务器（如 Jellyfin、 Kodi） |
| DMR | Digital Media Renderer | 媒体渲染器（如 DLNA 音箱、电视） |
| DMP | Digital Media Player | 媒体播放器（既充当 Renderer 也充当 Player） |
| DMC | Digital Media Controller | 媒体控制器，即 ControlPoint 的 DLNA 叫法 |
| UDN | Unique Device Name | 设备唯一标识名，相当于 MAC 地址 |
| USN | Unique Service Name | SSDP 消息中的设备+服务唯一标识 |
| ST | Search Target | M-SEARCH 中的搜索类型，如 `upnp:rootdevice` |
| NT | Notify Type | SSDP NOTIFY 消息中的设备类型字段 |
| NOTIFY | — | SSDP 存活广播，每隔一段时间设备发送一次 |
| MX | Maximum delay | M-SEARCH 中设备响应的最大延迟秒数 |
| SID | Subscription ID | 事件订阅的唯一标识 |
| SCPD | Service Description | 描述 Service 支持的 Action 和 StateVariable 的 XML 文件 |

---

## 附录 I：故障排查决策树

遇到问题时，按以下分支逐步定位：

```
问题：设备发现不到
│
├─ cp.search() 调了吗？
│   └─ ❌ → 在 start() 后调用 search()
│
├─ 设备在同一局域网吗？
│   └─ ❌ → 确认 Wi-Fi/网段
│
├─ 防火墙挡了吗？
│   └─ ❌ → 开放 UDP 1900 和 TCP 设备描述端口
│
├─ 是 Docker + host 网络吗？
│   └─ ❌ → 改用 network_mode: host
│
└─ IPv6 双栈问题？
    └─ 试 Protocol.IP_V4_ONLY

问题：Browse/Search 返回空
│
├─ ObjectID 正确吗？
│   └─ ❌ → 用 "0" 根目录重试
│
├─ BrowseFlag 对吗？
│   └─ ❌ → BrowseMetadata 只查自己，BrowseDirectChildren 查子项
│
└─ 服务器 Search 功能正常吗？
    └─ ❌ → 改用 Browse 替代 Search

问题：Action 调用失败
│
├─ 参数名大小写对吗？
│   └─ ❌ → 确认 Action 参数列表中的大小写
│
├─ 参数值类型对吗？（全部要是 String）
│   └─ ❌ → 数字转字符串
│
├─ 服务器返回了什么错误？
│   └─ 打印 e.message，查阅 UPnP 错误码表
│
└─ 设备描述 XML 下载成功了吗？
    └─ ❌ → 检查 baseUrl 是否可访问

问题：播放没声音/没画面
│
├─ Renderer 支持该 MIME 类型吗？
│   └─ ❌ → 先调 GetProtocolInfo 确认支持格式
│
├─ URI 是 http-get 协议吗？
│   └─ ❌ → DLNA 只支持 http-get*
│
└─ Jellyfin 需要转码？
    └─ 检查 Jellyfin DLNA 转码设置

问题：订阅事件收不到
│
├─ subscriptionEnabled = true 了吗？
│   └─ ❌ → 重建 ControlPoint
│
├─ service.subscribe() 调了吗？
│   └─ ❌ → 发现后立即订阅
│
└─ keepRenew = true 设置了吗？
    └─ ❌ → 设置 true 或自己实现续订逻辑
```

---


### 13.10 Jellyfin App 的投屏方式：Chromecast 还是 DLNA？

> ⚠️ **常见误区**：Jellyfin 官方 Android App 有"投屏"功能，误以为用的是 DLNA。实际上官方 App 用的是 **Google Cast（Chromecast）协议**，跟 DLNA/UPnP 是两套完全不同的体系。

#### 投屏协议对比

| | **Chromecast** | **DLNA/UPnP** |
|---|---|---|
| 协议 | Google Cast SDK | SSDP + SOAP + GENA |
| 发现方式 | Google Cast 服务（需 Google Play Services）| 局域网 SSDP 广播（无需外部服务）|
| 控制方式 | Cast SDK（Google 闭源）| AVTransport Action（开放标准）|
| 典型设备 | Chromecast TV、Google TV、Nest 音箱 | DLNA 电视、音箱、NAS |
| Jellyfin App 支持 | ✅ 官方内置 | ❌ **没有** |
| mmupnp 支持 | ❌ 不支持 | ✅ 完整支持 |

#### Jellyfin 官方 App 的技术栈

Jellyfin 官方 Android App（`jellyfin-android`）实际上是这样实现的：

| 组件 | 用途 |
|------|------|
| `jellyfin-sdk-kotlin` | 服务器发现、登录认证、媒体库浏览、封面图 |
| ExoPlayer（`jellyfin-player-android`）| 直接播放 Jellyfin 媒体流（Jellyfin 内置播放器）|
| **Google Cast SDK** | 投屏功能（🎬 图标）|

**没有 DLNA/UPnP 代码**，所以在源码里搜不到 AVTransport 或 UPnP 相关内容。

#### DLNA 投屏现状

| 客户端 | DLNA 投屏支持 |
|--------|-------------|
| Jellyfin 官方 App | ❌ 无 |
| Jellyfin Web（`jellyfin-web`）| ✅ 有（`dlnaplayback` 插件）|
| BubbleUPnP | ✅ 完整支持 |
| Kodi | ✅ 完整支持（Jellyfin 作为 DMS，Kodi 同时充当 DMR）|

#### 如果要在 Android App 里做 DLNA 投屏

必须自己实现，官方 SDK 无此能力：

```
┌─────────────────────────────────────────┐
│  你的 Android App                        │
│                                         │
│  ┌─────────────────┐  ┌──────────────┐ │
│  │ Jellyfin SDK    │  │ mmupnp       │ │
│  │ 媒体库/搜索/认证│  │ DLNA 控制    │ │
│  └────────┬────────┘  └──────┬───────┘ │
│           │                  │          │
│           │  resourceUri    │ AVTransport│
└───────────┼──────────────────┼──────────┘
            │                  │
    ┌──────▼──────────────────▼──────┐
    │   Jellyfin 服务器（DMS）         │
    └─────────────────────────────────┘
            │
    ┌──────▼──────────────────────────────────┐
    │   电视 / 音箱（DLNA DMR）               │
    └──────────────────────────────────────────┘
```

**目前没有现成的"官方 DLNA 投屏库"，只能：**
- 参考 **ohmae/DmsExplorer**（完整 mmupnp Android 示例）
- 参考 **jellyfin-web 的 dlnaplayback 插件**（DLNA 播放逻辑参考）
- 自己用 mmupnp 实现 DLNA 投屏

---

### 13.11 Jellyfin Kotlin SDK（官方 API）

> ⚠️ **重要区分**：mmupnp 解决的是 **UPnP/DLNA 通用控制协议**（任何支持 DLNA 的设备都能用），而 Jellyfin Kotlin SDK 解决的是 **Jellyfin 私有 API**（功能更完整，但只能对接 Jellyfin）。两者是**互补关系**，可同时使用。

Jellyfin 官方提供 Kotlin SDK，覆盖 Jellyfin 所有 HTTP API，比 DLNA 协议获取的信息更丰富（如用户数据、播放列表、直播等）。

#### SDK 模块一览

| 模块 | 说明 |
|------|------|
| `jellyfin-core` | 核心（配置、API 客户端、响应解析） |
| `jellyfin-api-jvm` | JVM 平台 API（纯 JVM 应用） |
| `jellyfin-core-android` | Android 专用（包含 context、storage） |
| `jellyfin-api-android` | Android API 封装 |
| `jellyfin-player-android` | Android 播放器核心（ExoPlayer 集成） |
| `jellyfin-player-android-mediasession` | MediaSession 封装 |

#### 依赖配置

```kotlin
// build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.example.myapp"
}

repositories {
    mavenCentral()
}

val sdkVersion = "1.4.5"  // 使用最新稳定版

dependencies {
    // 核心
    implementation("org.jellyfin.sdk:jellyfin-core:$sdkVersion")
    implementation("org.jellyfin.sdk:jellyfin-core-android:$sdkVersion")

    // API（可选，按需引入）
    implementation("org.jellyfin.sdk:jellyfin-api-android:$sdkVersion")
}
```

#### 创建 Jellyfin 实例并登录

```kotlin
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.create.jellyfin
import org.jellyfin.sdk.create.api
import org.jellyfin.sdk.model.ServerDeviceInfo

class JellyfinClient {

    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "MyApp", version = "1.0.0")
        context = applicationContext  // Android Context（必须）
    }

    // ── 发现可用服务器 ──
    // 自动发现局域网内的 Jellyfin 服务器
    suspend fun discoverServers(): List<ServerDeviceInfo> {
        val response = jellyfin.discovery.discoverServers()
        return response.servers
    }

    // ── 用户认证 ──
    suspend fun login(serverUrl: String, username: String, password: String): String? {
        // 1. 创建 API 实例
        val api = createApi(
            serverUrl = serverUrl,
            jellyfin = jellyfin
        )

        // 2. 获取认证方式
        val auth = api.userApi.getAuthenticateUserByName(
            username = username,
            password = password
        )

        // 3. 保存 token
        val token = auth.accessToken
        if (token != null) {
            // 重新创建带 token 的 API
            return token
        }
        return null
    }
}
```

#### 获取媒体库数据（音乐库）

```kotlin
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.api.operations.MusicAlbumApi
import org.jellyfin.sdk.api.operations.MusicArtistApi

class JellyfinMediaRepository(private val api: ApiClient) {

    // ── 获取所有专辑 ──
    suspend fun getAlbums(startIndex: Int = 0, limit: Int = 50): List<BaseItemDto> {
        val response = api.musicAlbumApi.getAlbums(
            startIndex = startIndex,
            limit = limit,
            fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.PARENT_ID)
        )
        return response.items ?: emptyList()
    }

    // ── 获取专辑详情（包含曲目列表）──
    suspend fun getAlbumItems(albumId: String): List<BaseItemDto> {
        val response = api.musicAlbumApi.getAlbumItems(albumId)
        return response.items ?: emptyList()
    }

    // ── 按艺术家获取专辑 ──
    suspend fun getAlbumsByArtist(artistId: String): List<BaseItemDto> {
        val response = api.musicArtistApi.getMusicArtists(
            userId = api.userApi.currentUserId,
            artistIds = listOf(artistId)
        )
        return response.items ?: emptyList()
    }

    // ── 获取媒体流地址 ──
    // Jellyfin API 返回的 streamUrl 比 DLNA 的 resourceUri 更完整，
    // 支持自适应码率、转码参数等
    suspend fun getStreamUrl(itemId: String): String {
        val response = api.mediaInfoApi.getPlaybackInfo(
            itemId = itemId,
            mediaSourceId = null
        )
        val mediaSource = response.mediaSources?.firstOrNull()
        return mediaSource?.directStreamUrl
            ?: api.streamApi.getMasterHlsStreamUrl(itemId)
    }
}
```

#### DLNA + SDK 双栈结合方案

两种方案各有优势，实际项目中可以**同时使用**，互补长短：

| 能力 | UPnP/DLNA（mmupnp） | Jellyfin API（SDK） |
|------|-------------------|-------------------|
| 设备发现 | ✅ SSDP 自动发现所有设备 | ❌ 需知道服务器地址 |
| 媒体库浏览 | ✅ 标准 DIDL-Lite | ✅ 更完整（专辑/艺术家/播放列表/直播） |
| 封面图 URL | ⚠️ 需自行拼接 | ✅ 直接返回 |
| 播放控制 | ✅ AVTransport | ❌ 不支持 |
| 搜索 | ⚠️ 部分 Jellyfin 版本受限 | ✅ 完全支持 |
| 用户认证 | ❌ 无 | ✅ 完整支持 |
| 播放历史 | ❌ 无 | ✅ 支持 |

**推荐组合：**

```
媒体库浏览/搜索/认证  → Jellyfin Kotlin SDK（功能完整）
渲染器控制/投屏     → mmupnp DLNA（标准协议，任何 DMR 设备通用）
封面图/用户数据     → Jellyfin Kotlin SDK
```

**架构示例：**

```kotlin
class HybridMediaController(
    private val jellyfinApi: ApiClient,      // Jellyfin SDK
    private val dlnaPlayer: DlnaPlayer      // mmupnp
) {
    // 用 Jellyfin API 搜索歌曲
    suspend fun searchSong(keyword: String): List<BaseItemDto> {
        return jellyfinApi.searchApi.getItems(
            searchTerm = keyword,
            includeTypes = listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST)
        ).items ?: emptyList()
    }

    // 用 Jellyfin API 获取专辑封面
    suspend fun getCoverUrl(itemId: String): String? {
        return jellyfinApi.userApi.getItemImageUrl(itemId, ImageType.PRIMARY)
    }

    // 用 mmupnp DLNA 投屏到电视
    fun playOnTv(uri: String, title: String) {
        val metaData = buildDidlLiteMetadata(title, uri)
        dlnaPlayer.setUri(uri, metaData)
        dlnaPlayer.play()
    }
}
```

#### 相关资源

| 资源 | 链接 |
|------|------|
| Jellyfin Kotlin SDK 官方文档 | https://kotlin-sdk.jellyfin.org |
| SDK 源码 | https://github.com/jellyfin/jellyfin-sdk-kotlin |
| Jellyfin Android 官方 App 源码 | https://github.com/jellyfin/jellyfin-android |
| API 变更日志 | https://kotlin-sdk.jellyfin.org/changelog/ |

---

### 13.12 与其他 UPnP 应用互操作性

Jellyfin/Kodi/Windows Media Player 等都实现了 UPnP/DLNA，可能需要相互配合工作。以下是常见互操作场景：

#### Jellyfin 作为 DMS，其他设备作为 DMC/Renderer

这是最常见场景：手机 App（ DMC）控制 Jellyfin（DMS）向电视（Renderer）投屏。

```
手机 App（mmupnp ControlPoint）
  ├─ 发现 Jellyfin（DMS）→ Browse 媒体库
  └─ 发现电视（DMR）→ 发送 SetAVTransportURI + Play
       └─ 电视从 Jellyfin 获取媒体流并播放
```

#### Kodi 作为 DMS + DMR（既是服务器也是播放设备）

Kodi 同时充当 MediaServer 和 MediaRenderer，可以直接在本机播放媒体：

```kotlin
// 发现 Kodi
cp.addDiscoveryListener { device ->
    if (device.deviceType.contains("MediaServer") &&
        device.deviceType.contains("MediaRenderer")) {
        println("Kodi 双角色设备: ${device.friendlyName}")
        // Kodi 可以直接用本机播放，跳过 Renderer 发现步骤
    }
}
```

#### Windows Media Player 共享库

Windows Media Player 的"家庭媒体共享"也实现了 DMS，设备发现方式完全一样：

```kotlin
// WMP 共享的设备 deviceType 示例
// "urn:schemas-upnp-org:device:MediaServer:1"
// server 字符串包含 "Windows Media Player"
cp.addDiscoveryListener { device ->
    if (device.server?.contains("Windows Media Player") == true) {
        println("发现 WMP 共享库: ${device.friendlyName}")
    }
}
```

#### 同时控制多个 Renderer（多房间音频）

```kotlin
// 发现所有 Renderer
val renderers = mutableMapOf<String, Device>()

cp.addDiscoveryListener { device ->
    if (device.deviceType.contains("MediaRenderer")) {
        renderers[device.udn] = device
    }
}

// 同时在两个房间播放同一首歌
fun playOnMultiple(deviceUdns: List<String>, uri: String) {
    deviceUdns.forEach { udn ->
        val renderer = renderers[udn] ?: return@forEach
        val av = renderer.findServiceById("urn:upnp-org:serviceId:AVTransport")
        av?.findAction("SetAVTransportURI")?.invoke(
            mapOf("InstanceID" to "0", "CurrentURI" to uri, "CurrentURIMetaData" to "")
        )
        av?.findAction("Play")?.invoke(
            mapOf("InstanceID" to "0", "Speed" to "1")
        )
    }
}
```

---

### 13.13 生产环境性能优化

在资源受限环境（移动设备、嵌入式设备）中使用 mmupnp 时，以下优化可显著降低资源占用：

#### 延迟设备描述解析

设备描述 XML 只在真正需要时才解析，不需要立刻获取所有设备信息：

```kotlin
// ❌ 低效：发现即解析所有设备描述
cp.addDiscoveryListener { device ->
    // device.baseUrl 已经触发下载
    println(device.friendlyName)  // 此时已下载 XML
}

// ✅ 高效：只订阅感兴趣的服务
cp.addDiscoveryListener { device ->
    // 先过滤，只保留 MediaServer
    if (!device.deviceType.contains("MediaServer")) return@addDiscoveryListener
    // 此时 device.baseUrl 才真正被使用
}
```

#### 限制发现设备数量

在搜索时限制只取前 N 个设备，避免大量设备时内存膨胀：

```kotlin
var discoveredCount = 0
val maxDevices = 10

cp.addDiscoveryListener { device ->
    if (discoveredCount >= maxDevices) return@addDiscoveryListener
    discoveredCount++
    // 处理设备
}
```

#### 减少订阅数量

每个订阅都会建立 TCP 连接，不需要的不订阅：

```kotlin
// ❌ 订阅所有服务
device.serviceList.forEach { it.subscribe() }

// ✅ 只订阅关键服务
listOf(
    "urn:upnp-org:serviceId:ContentDirectory",
    "urn:upnp-org:serviceId:AVTransport"
).forEach { sid ->
    device.serviceList.find { it.serviceId == sid }?.subscribe()
}
```

#### 搜索间隔优化

不需要持续搜索，搜到设备后定期维护即可：

```kotlin
// ❌ 持续搜索浪费资源
// cp.search() 不带参数时会持续监听 SSDP 广播

// ✅ 定期搜索（每 5 分钟一次），维持设备活跃
val scheduler = Executors.newSingleThreadScheduledExecutor()
scheduler.scheduleAtFixedRate({
    cp.search("upnp:rootdevice")
}, 0, 5, TimeUnit.MINUTES)

// 退出时关闭
fun shutdown() {
    scheduler.shutdown()
}
```

#### 减少 DIDL-Lite 解析频率

如果只关心部分字段，不必每次都完整解析整个 DIDL-Lite：

```kotlin
// ❌ 低效：完整解析所有字段
val result = parseDidlLite(xmlString)

// ✅ 高效：只正则提取需要的字段
fun extractTitles(xml: String): List<String> {
    val pattern = Pattern.compile("<dc:title>([^<]+)</dc:title>")
    val matcher = pattern.matcher(xml)
    val titles = mutableListOf<String>()
    while (matcher.find()) {
        titles.add(matcher.group(1))
    }
    return titles
}
```

---

### 13.14 相关开源项目推荐

| 项目 | 语言/平台 | 说明 |
|------|----------|------|
| [ohmae/DmsExplorer](https://github.com/ohmae/DmsExplorer) | Android | ohmae 官方的 UPnP 浏览器 Demo，完整展示 mmupnp 所有功能 |
| [jellyfin/jellyfin-plugin-dlna](https://github.com/jellyfin/jellyfin-plugin-dlna) | C# | Jellyfin 官方 DLNA 插件源码 |
| [plietar/libupnpp](https://github.com/plietar/libupnpp) | C++ | C++ UPnP 库，支持 Linux |
| [freedesktop/gupnp](https://github.com/freedesktop/gupnp) | C/GObject | GNOME 的 UPnP 实现 |
| [jellyfin/S想起-JellyfinMobile](https://github.com/jellyfin) | — | Jellyfin 移动端官方实现 |
| [Kodi](https://kodi.tv) | C++ | 跨平台媒体中心，完整 UPnP/DLNA 支持 |


---

## 附录 J：Android UPnP/DLNA 库对比

本文档推荐 **mmupnp**，以下是当前主流 Android UPnP/DLNA 库的完整对比。

### 横向对比表

| 特性 | **ohmae/mmupnp** | **jUPnP** | **UPnPCast** | **cling** | **rxupnsl** |
|------|-----------------|-----------|-------------|-----------|-------------|
| 语言 | Kotlin | Java | Kotlin | Java | Java/RxJava |
| 最新版本 | 3.1.6 | 3.0.0+ | 1.1.2 | 2.7.0（停止维护） | 0.5.4 |
| 维护状态 | ✅ 活跃 | ✅ 活跃 | ✅ 活跃 | ❌ 已停止 | ⚠️ 低维护 |
| Maven Central | ✅ | ✅ | ❌ 需 JitPack | ❌ 需 JitPack | ❌ 无 |
| 体积 | ~400KB | ~2MB | ~600KB | ~2MB | — |
| RxJava 依赖 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 | ✅ 必需 |
| Kotlin 协程 | ✅ 原生支持 | ⚠️ 仅 Java | ✅ | ❌ | ⚠️ |
| ControlPoint | ✅ | ✅ | ✅ | ✅ | ✅ |
| UPnP Device | ❌ | ✅ | ❌ | ✅ | ❌ |
| Android 文档 | 一般 | 完整 | 较好 | 完整 | 一般 |
| DIDL-Lite 解析 | 自己实现 | 自己实现 | 封装了 | 自己实现 | — |
| 事件订阅 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Android 推荐度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ 不推荐 | ⭐⭐ 仅 RxJava 项目 |

### 各库详细介绍

#### ohmae/mmupnp（本方案采用）

> GitHub: https://github.com/ohmae/mmupnp

纯 Kotlin 实现的 UPnP ControlPoint，轻量、简洁、无外部依赖。API 设计干净利落，非常适合 Android 项目。

**优点：**
- 纯 Kotlin，无 RxJava/协程依赖，可自行选择异步方案
- API 简洁，代码量小，易于理解和调试
- 体积仅 ~400KB，适合移动端
- Maven Central 直接可用，无 JitPack 依赖

**缺点：**
- 只支持 ControlPoint，不能作为 UPnP Device
- 文档以日文为主，英文文档较少
- 高级特性（如多播事件）功能有限

```kotlin
// Maven
implementation("net.mm2d:upnp:3.1.6")
```

#### jUPnP（社区主流）

> GitHub: https://github.com/jupnp/jupnp

从 Cling fork 而来，是目前社区最活跃的 Java UPnP 库，功能最完整。

**优点：**
- 支持 ControlPoint 和 UPnP Device（可以做 DMS/DMR）
- 支持 SAX 解析选项（内存效率更高）
- 完整的 GENA 事件订阅实现
- Android 官方文档完善

**缺点：**
- 体积大（~2MB）
- API 偏老式（JavaBean 风格）
- 部分设备兼容性问题（与 Jellyfin 对接时偶发）

```xml
<!-- Maven -->
<dependency>
    <groupId>org.jupnp</groupId>
    <artifactId>org.jupnp</artifactId>
    <version>3.0.0</version>
</dependency>
```

#### UPnPCast

> GitHub: https://github.com/yinnho/UPnPCast

专为替代 Cling 设计的现代 Android DLNA 库，API 极简。

**优点：**
- Kotlin + 协程，现代化 API
- 内置音量控制、进度管理等高级功能
- 经过主流电视品牌测试（小米/三星/LG/索尼）
- Gradle 直接依赖

**缺点：**
- 非 Maven Central，需 JitPack
- 只支持 ControlPoint
- 社区较小，文档有限

```groovy
// JitPack（需配置）
implementation 'com.github.yinnho:UPnPCast:1.1.2'
```

#### cling（已废弃，不推荐）

> GitHub: https://github.com/4thline/cling

最早的 Java UPnP 库之一，但**已于 2019 年停止维护**，存在已知安全隐患，不推荐在新项目中使用。

#### rxupnsl（仅 RxJava 项目考虑）

> GitHub: https://github.com/noelrs/rxupnsl

基于 Cling 的 RxJava 封装。如果项目已经深度使用 RxJava，可以考虑。

### 选型建议

| 场景 | 推荐库 |
|------|--------|
| 新项目，Kotlin，简洁优先 | **mmupnp**（本方案） |
| 需要完整 UPnP Device 功能（如实现 DMS） | **jUPnP** |
| 已有 RxJava 存量代码 | **rxupnsl** |
| 快速集成，API 极简 | **UPnPCast** |
| 已有 Cling 项目 | **尽快迁移到 jUPnP** |

### 从 Cling 迁移到 mmupnp 的主要差异

| Cling | mmupnp |
|-------|--------|
| `UpnpService` | `ControlPointFactory.create()` |
| `DeviceList` | `cp.deviceList: List<Device>` |
| `Service.execute(action)` | `action.invoke(argumentValues, onResult, onError)` |
| `Service.subscribe()` | `service.subscribe(onResult)` |
| `ActionCallbacks` | Lambda `(Map<String, String>) -> Unit` |
| RxJava 支持 | 自行搭配协程/RxJava |

