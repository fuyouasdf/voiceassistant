# 临时播放列表功能设计

## 背景

当用户通过语音或点击播放歌手歌曲/播放列表时，自动生成一个临时播放列表用于持续播放，避免播完一首就停止的问题。

## 核心场景

| 触发场景 | 数据来源 | 播放行为 |
|----------|----------|----------|
| 用户语音"播放周杰伦的歌" | Jellyfin 搜索该歌手的歌曲 | 生成临时列表并播放 |
| 用户点击播放列表中的歌曲 | 当前播放列表全部歌曲 | 复制为临时列表并播放 |

## 详细设计

### 1. MusicPlayer 新增方法

```kotlin
// 生成临时播放列表并播放
suspend fun playAsTempPlaylist(
    songs: List<MusicItem>,
    startIndex: Int = 0,
    source: QueueSource
)

// 当前是否在临时播放列表模式
val isTempPlaylistActive: Boolean

// 切回原播放列表
fun restoreOriginalPlaylist()
```

### 2. 新增状态

```kotlin
data class TempPlaylistState(
    val isActive: Boolean = false,
    val originalQueue: List<MusicItem> = emptyList(),
    val originalIndex: Int = -1,
    val originalRepeatMode: RepeatMode = RepeatMode.OFF
)
```

### 3. 触发时机与操作

| 场景 | 触发条件 | 操作 |
|------|----------|------|
| 播放列表点击 | `QueueSource == PLAYLIST` | 保存原队列 → 生成临时列表 |
| 语音播放歌手 | 搜索到多个结果 | 直接生成临时列表 |
| 临时列表播完 | `currentIndex >= playlist.size - 1` | 循环播放（利用 RepeatMode.LIST） |

### 4. 数据流

```
用户触发播放
    ↓
检测来源（播放列表 or 歌手搜索）
    ↓
MusicPlayer.playAsTempPlaylist(songs, source)
    ↓
保存当前队列到 TempPlaylistState.originalQueue
    ↓
替换当前播放队列为 songs
    ↓
开始播放，isTempPlaylistActive = true
    ↓
播完最后一首 → RepeatMode.LIST 循环从头播放
```

### 5. 切回原列表

用户手动点击原列表歌曲时，调用 `restoreOriginalPlaylist()` 恢复原队列和播放位置。

## 实现位置

- **MusicPlayer.kt**: 新增 `playAsTempPlaylist`、`isTempPlaylistActive`、`restoreOriginalPlaylist`
- **IntentExecutor.kt**: 歌手搜索播放逻辑改为调用 `playAsTempPlaylist`
- **PlaylistViewModel.kt**: 点击播放逻辑改为调用 `playAsTempPlaylist`

## 后续扩展

- Fallback 到艺术家歌曲（当相似歌曲为空时）
- 临时列表播完后自动获取更多相似歌曲
