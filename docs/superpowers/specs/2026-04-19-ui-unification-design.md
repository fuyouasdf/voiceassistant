# UI 统一设计规范

## 概述

以 MainActivity 为标准，统一所有页面的 UI 风格为暗色现代感设计。

**参考页面**: MainActivity (`activity_main.xml`)
**目标**: 全局一致性，统一的卡片、按钮、输入框、边距、图标风格

---

## 1. 布局架构规范

### 页面容器
- 使用 `LinearLayout` 垂直排列作为页面根容器
- 替代 `ConstraintLayout` / `NestedScrollView` 作为根布局

### 边距规范
- **水平边距统一 24dp**（所有页面）
- 列表项内边距 16dp

### 页面结构
```
LinearLayout (vertical)
├── StatusBarArea (顶部状态区，24dp 边距)
├── ContentArea (weight=1，占用剩余空间)
├── MiniPlayerContainer (可选)
└── InputArea (底部输入区，24dp 边距 + 底部留白)
```

---

## 2. 卡片系统

### 全局统一卡片样式
```xml
<style name="Widget.VoiceAssistant.Card" parent="Widget.Material3.CardView.Filled">
    <item name="cardBackgroundColor">@color/surface</item>
    <item name="cardCornerRadius">20dp</item>
    <item name="cardElevation">0dp</item>
    <item name="strokeWidth">0dp</item>
</style>

<style name="Widget.VoiceAssistant.Card.Large" parent="Widget.VoiceAssistant.Card">
    <item name="cardCornerRadius">28dp</item>
</style>

<style name="Widget.VoiceAssistant.Card.Outlined" parent="Widget.VoiceAssistant.Card">
    <item name="cardCornerRadius">20dp</item>
    <item name="strokeColor">@color/surface_elevated</item>
    <item name="strokeWidth">1dp</item>
</style>
```

### 卡片使用场景
| 场景 | 卡片样式 | 圆角 |
|------|----------|------|
| 页面主卡片 | Card.Large | 28dp |
| 设置项/列表项包装 | Card | 20dp |
| 输入框包装 | Card | 20dp |
| 列表项内层 | 不用 Card | - |

---

## 3. 文字输入框统一

### 统一使用 Material3 FilledBox
```xml
<style name="Widget.VoiceAssistant.TextInputLayout" parent="Widget.Material3.TextInputLayout.FilledBox">
    <item name="boxBackgroundColor">@color/surface</item>
    <item name="boxCornerRadiusTopStart">16dp</item>
    <item name="boxCornerRadiusTopEnd">16dp</item>
    <item name="boxCornerRadiusBottomStart">16dp</item>
    <item name="boxCornerRadiusBottomEnd">16dp</item>
    <item name="hintTextColor">@color/text_secondary</item>
    <item name="android:textColor">@color/text_primary</item>
</style>
```

### 使用示例
```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.VoiceAssistant.TextInputLayout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/hint">

    <com.google.android.material.textfield.TextInputEditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        android:textColor="@color/text_primary" />
</com.google.android.material.textfield.TextInputLayout>
```

---

## 4. 按钮系统

### Primary 按钮
```xml
<style name="Widget.VoiceAssistant.Button.Primary" parent="Widget.Material3.Button">
    <item name="android:backgroundTint">@color/primary</item>
    <item name="android:textColor">@color/on_primary</item>
    <item name="cornerRadius">30dp</item>
    <item name="android:textAllCaps">false</item>
</style>
```

### Secondary 按钮
```xml
<style name="Widget.VoiceAssistant.Button.Secondary" parent="Widget.Material3.Button.OutlinedButton">
    <item name="strokeColor">@color/primary</item>
    <item name="strokeWidth">2dp</item>
    <item name="android:textColor">@color/primary</item>
    <item name="cornerRadius">30dp</item>
</style>
```

### Icon 按钮
```xml
<ImageButton
    android:layout_width="@dimen/icon_button_size"
    android:layout_height="@dimen/icon_button_size"
    android:background="@drawable/bg_icon_button"
    android:src="@drawable/ic_xxx"
    app:tint="@color/text_secondary" />
```

### Chip 按钮
```xml
<com.google.android.material.chip.Chip
    style="@style/Widget.VoiceAssistant.Chip"
    android:layout_width="wrap_content"
    android:layout_height="40dp"
    android:text="Label"
    app:chipBackgroundColor="@color/surface"
    app:chipIconTint="@color/primary" />
```

---

## 5. 列表项统一 (item_*.xml)

### 统一结构
```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="@color/surface"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    android:layout_marginHorizontal="24dp"
    android:layout_marginVertical="6dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="16dp">

        <!-- 封面 -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="48dp"
            android:layout_height="48dp"
            app:cardCornerRadius="8dp"
            app:cardElevation="0dp">
            <ImageView ... />
        </com.google.android.material.card.MaterialCardView>

        <!-- 文字信息 -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:orientation="vertical">

            <TextView
                android:textAppearance="@style/TextAppearance.VoiceAssistant.Body"
                ... />
            <TextView
                android:textAppearance="@style/TextAppearance.VoiceAssistant.Caption"
                ... />
        </LinearLayout>

        <!-- 图标按钮 -->
        <ImageButton
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            app:tint="@color/text_tertiary" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### 封面圆角
- 列表项封面统一 8dp 圆角

---

## 6. 文字样式

### 统一样式定义
```xml
<!-- 标题 -->
<style name="TextAppearance.VoiceAssistant.Title" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:fontFamily">sans-serif-medium</item>
</style>

<!-- 正文 -->
<style name="TextAppearance.VoiceAssistant.Body" parent="TextAppearance.Material3.BodyLarge">
    <item name="android:textColor">@color/text_primary</item>
</style>

<!-- 副标题 -->
<style name="TextAppearance.VoiceAssistant.Body.Secondary">
    <item name="android:textColor">@color/text_secondary</item>
</style>

<!-- 标签/小字 -->
<style name="TextAppearance.VoiceAssistant.Caption" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:textColor">@color/text_tertiary</item>
</style>
```

---

## 7. 图标规范

### 图标来源
- 优先使用自定义 SVG drawable
- 统一 stroke width (2dp)
- 统一 tint 颜色：`@color/text_secondary` 或 `@color/text_tertiary`

### 图标 tint 规则
| 场景 | 颜色 |
|------|------|
| 常规图标 | text_secondary |
| 激活/选中状态 | primary |
| 禁用状态 | text_disabled |
| 状态指示 | status_online / status_offline |

---

## 8. 需要修改的文件清单

### P0 - 核心页面（必须修改）
| 文件 | 主要修改 |
|------|----------|
| `activity_settings.xml` | 24dp 边距、Material3 TextInputLayout、统一卡片圆角 |
| `activity_now_playing.xml` | 统一边距、卡片圆角、文字样式 |
| `item_song.xml` | 圆角 16dp、封面 8dp、背景 Card |

### P1 - 播放相关页面
| 文件 | 主要修改 |
|------|----------|
| `activity_model_download.xml` | 边距、进度条样式、按钮样式 |
| `activity_jellyfin_browse.xml` | 统一边距、卡片样式 |
| `activity_playlist.xml` | 统一边距和卡片 |
| `activity_playlist_list.xml` | 统一边距和卡片 |

### P2 - Fragment 及辅助
| 文件 | 主要修改 |
|------|----------|
| `fragment_now_playing.xml` | 复用 activity 标准 |
| `fragment_mini_player.xml` | 统一按钮样式 |
| `fragment_queue.xml` | 列表项统一样式 |
| `item_album.xml` | 封面圆角、卡片样式 |
| `item_playlist.xml` | 卡片圆角、文字样式 |
| `item_queue_song.xml` | 卡片圆角、拖拽图标 |

### 样式/资源文件
| 文件 | 修改 |
|------|------|
| `themes.xml` | 新增 TextInputLayout/Chip 样式 |
| `dimens.xml` | 确认/补充尺寸常量 |

---

## 9. 实施顺序

1. **更新 themes.xml** - 添加缺失的统一样式
2. **修改 P0 页面** - SettingsActivity, NowPlayingActivity, item_song
3. **修改 P1 页面** - ModelDownload, JellyfinBrowse, Playlist 等
4. **修改 P2 页面/组件** - Fragments, 其他 item
5. **编译验证** - 确保没有布局错误

---

## 10. 验收标准

- [ ] 所有页面水平边距统一为 24dp
- [ ] 所有卡片圆角统一为 20dp（大卡片 28dp）
- [ ] 所有文字输入框使用 Material3 FilledBox 风格
- [ ] 所有按钮样式使用定义好的 style
- [ ] 图标统一使用自定义 drawable + tint
- [ ] 文字样式使用 TextAppearance 而非直接属性
- [ ] 编译通过，无布局错误
