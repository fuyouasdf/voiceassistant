# UI 统一实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 MainActivity 为标准，统一所有页面的 UI 风格为暗色现代设计

**Architecture:** 更新 themes.xml 统一样式 → 修改核心页面布局 → 修改播放相关页面 → 修改辅助组件

**Tech Stack:** Android XML Layouts, Material3 Components, Kotlin

---

## 文件变更映射

### 样式资源 (2 文件)
- `android/app/src/main/res/values/themes.xml` - 添加 TextInputLayout/Chip 样式
- `android/app/src/main/res/values/dimens.xml` - 确认尺寸常量

### P0 核心页面 (3 文件)
- `android/app/src/main/res/layout/activity_settings.xml`
- `android/app/src/main/res/layout/activity_now_playing.xml`
- `android/app/src/main/res/layout/item_song.xml`

### P1 播放相关 (4 文件)
- `android/app/src/main/res/layout/activity_model_download.xml`
- `android/app/src/main/res/layout/activity_jellyfin_browse.xml`
- `android/app/src/main/res/layout/activity_playlist.xml`
- `android/app/src/main/res/layout/activity_playlist_list.xml`

### P2 Fragment 及辅助 (7 文件)
- `android/app/src/main/res/layout/fragment_mini_player.xml`
- `android/app/src/main/res/layout/fragment_queue.xml`
- `android/app/src/main/res/layout/item_album.xml`
- `android/app/src/main/res/layout/item_playlist.xml`
- `android/app/src/main/res/layout/item_queue_song.xml`
- `android/app/src/main/res/layout/item_message.xml`
- `android/app/src/main/res/layout/item_loading.xml`

---

## Task 1: 更新 themes.xml 统一样式

**Files:**
- Modify: `android/app/src/main/res/values/themes.xml`

- [ ] **Step 1: 添加 TextInputLayout 统一样式**

在 `themes.xml` 的 `Widget.VoiceAssistant.Button.Icon` 样式后添加：

```xml
    <!-- TextInputLayout 统一样式 -->
    <style name="Widget.VoiceAssistant.TextInputLayout" parent="Widget.Material3.TextInputLayout.FilledBox">
        <item name="boxBackgroundColor">@color/surface</item>
        <item name="boxCornerRadiusTopStart">16dp</item>
        <item name="boxCornerRadiusTopEnd">16dp</item>
        <item name="boxCornerRadiusBottomStart">16dp</item>
        <item name="boxCornerRadiusBottomEnd">16dp</item>
        <item name="hintTextColor">@color/text_secondary</item>
        <item name="android:textColor">@color/text_primary</item>
        <item name="boxStrokeColor">@color/text_tertiary</item>
    </style>

    <!-- Chip 统一样式 -->
    <style name="Widget.VoiceAssistant.Chip" parent="Widget.Material3.Chip.Assist">
        <item name="chipBackgroundColor">@color/surface</item>
        <item name="chipCornerRadius">20dp</item>
        <item name="android:textColor">@color/text_secondary</item>
        <item name="chipIconTint">@color/primary</item>
        <item name="chipStrokeWidth">0dp</item>
    </style>

    <!-- 列表项卡片样式 -->
    <style name="Widget.VoiceAssistant.Card.ListItem">
        <item name="cardBackgroundColor">@color/surface</item>
        <item name="cardCornerRadius">16dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeWidth">0dp</item>
    </style>
```

- [ ] **Step 2: 验证 themes.xml 语法**

编译检查：
```bash
cd android && ./gradlew :app:compileDebugKotlin --dry-run
```

---

## Task 2: 统一 item_song.xml 列表项

**Files:**
- Modify: `android/app/src/main/res/layout/item_song.xml`

- [ ] **Step 1: 重写 item_song.xml**

完整替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="24dp"
    android:layout_marginVertical="6dp"
    app:cardBackgroundColor="@color/surface"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="16dp"
        android:background="?attr/selectableItemBackground">

        <!-- 封面图 -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="48dp"
            android:layout_height="48dp"
            app:cardCornerRadius="8dp"
            app:cardElevation="0dp"
            app:strokeWidth="0dp">

            <ImageView
                android:id="@+id/ivCover"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@color/surface_elevated"
                android:scaleType="centerCrop"
                tools:src="@drawable/ic_music" />
        </com.google.android.material.card.MaterialCardView>

        <!-- 歌曲信息 -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvTitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="@style/TextAppearance.VoiceAssistant.Body"
                android:textSize="14sp"
                android:maxLines="1"
                android:ellipsize="end"
                tools:text="歌曲名" />

            <TextView
                android:id="@+id/tvArtist"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:textAppearance="@style/TextAppearance.VoiceAssistant.Caption"
                android:textSize="12sp"
                android:maxLines="1"
                android:ellipsize="end"
                tools:text="艺术家" />
        </LinearLayout>

        <!-- 时长 -->
        <TextView
            android:id="@+id/tvDuration"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:textAppearance="@style/TextAppearance.VoiceAssistant.Caption"
            android:textSize="12sp"
            tools:text="3:45" />

        <!-- 更多按钮 -->
        <ImageButton
            android:id="@+id/btnMore"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:layout_marginStart="4dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_more"
            android:contentDescription="更多"
            app:tint="@color/text_tertiary" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: 确保 ic_more drawable 存在**

检查 `android/app/src/main/res/drawable/ic_more.xml` 是否存在，如不存在使用系统图标替代。

- [ ] **Step 3: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 3: 统一 activity_settings.xml

**Files:**
- Modify: `android/app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: 修改根布局为 ScrollView + LinearLayout 结构**

将 `androidx.constraintlayout.widget.ConstraintLayout` 改为：

```xml
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootScrollView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:fillViewport="true">

    <LinearLayout
        android:id="@+id/contentLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="24dp"
        android:paddingTop="24dp"
        android:paddingBottom="32dp">
```

- [ ] **Step 2: 修改 Title 样式**

将 `android:textStyle="bold"` 替换为 `android:textAppearance="@style/TextAppearance.VoiceAssistant.Title.Large"`

- [ ] **Step 3: 统一卡片圆角为 20dp，添加 stroke**

将所有设置卡片的 `app:cardCornerRadius="12dp"` 改为 `20dp`，添加 `app:strokeColor="@color/surface_elevated" app:strokeWidth="1dp"`

- [ ] **Step 4: 替换 TextInputLayout 样式**

将所有 `style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"` 替换为 `style="@style/Widget.VoiceAssistant.TextInputLayout"`

- [ ] **Step 5: 统一按钮样式**

将 `style="@style/Widget.MaterialComponents.Button.OutlinedButton"` 替换为 `style="@style/Widget.VoiceAssistant.Button.Secondary"`

将 `style="@style/Widget.MaterialComponents.Button.TextButton"` 替换为 `style="@style/Widget.VoiceAssistant.Button.Ghost"`

- [ ] **Step 6: 统一保存按钮**

将 `app:cornerRadius="12dp"` 改为使用 `style="@style/Widget.VoiceAssistant.Button.Primary"`

- [ ] **Step 7: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 4: 统一 activity_now_playing.xml

**Files:**
- Modify: `android/app/src/main/res/layout/activity_now_playing.xml`

- [ ] **Step 1: 修改页面根布局边距**

将外层 `androidx.core.widget.NestedScrollView` 的 `android:paddingHorizontal` 移除，改为内部约束布局的 `android:layout_marginHorizontal="24dp"`

- [ ] **Step 2: 统一卡片圆角为 20dp/28dp**

- topBar: `bg_player_surface` 保持，但修改内边距
- coverCard: 圆角从 28dp 保持一致
- infoCard: 圆角从 24dp 改为 20dp
- progressCard: 圆角从 24dp 改为 20dp
- controlsCard: 圆角从 28dp 改为 20dp
- lyricsCard: 圆角从 24dp 改为 20dp

- [ ] **Step 3: 统一文字样式**

将 `<TextView ... android:textStyle="bold" />` 中的粗体替换为使用 `android:textAppearance="@style/TextAppearance.VoiceAssistant.Body"`

- [ ] **Step 4: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 5: 统一 activity_model_download.xml

**Files:**
- Modify: `android/app/src/main/res/layout/activity_model_download.xml`

- [ ] **Step 1: 统一根布局边距**

将 ConstraintLayout 内的元素 `app:layout_constraintWidth_max="360dp"` 保持，但修改整体水平边距为 24dp

- [ ] **Step 2: 统一 progressCard 圆角为 20dp**

将 `app:cardCornerRadius="16dp"` 改为 `20dp`

- [ ] **Step 3: 统一按钮样式**

将 `app:icon="@android:drawable/ic_media_play"` 改为自定义 drawable 或保持（按钮样式由 style 控制）

- [ ] **Step 4: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 6: 统一 activity_jellyfin_browse.xml 和 activity_playlist.xml

**Files:**
- Modify: `android/app/src/main/res/layout/activity_jellyfin_browse.xml`
- Modify: `android/app/src/main/res/layout/activity_playlist.xml`

- [ ] **Step 1: 检查 jellyfin_browse 布局**

读取文件，确认当前布局结构

- [ ] **Step 2: 统一边距为 24dp**

将页面内所有硬编码的 16dp 边距改为 24dp

- [ ] **Step 3: 统一卡片圆角**

将 `app:cardCornerRadius` 统一为 20dp

- [ ] **Step 4: 检查 playlist 布局并统一**

读取 `activity_playlist.xml`，执行相同统一操作

- [ ] **Step 5: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 7: 统一 activity_playlist_list.xml 和其他播放页面

**Files:**
- Modify: `android/app/src/main/res/layout/activity_playlist_list.xml`
- Modify: `android/app/src/main/res/layout/activity_queue.xml`
- Modify: `android/app/src/main/res/layout/activity_lyrics_fullscreen.xml`

- [ ] **Step 1: 统一 playlist_list 布局**

读取文件，将边距统一为 24dp，卡片圆角统一为 20dp

- [ ] **Step 2: 统一 queue 布局**

读取文件，执行相同统一操作

- [ ] **Step 3: 统一 lyrics_fullscreen 布局**

读取文件，执行相同统一操作

- [ ] **Step 4: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 8: 统一 Fragment 和 Item 组件

**Files:**
- Modify: `android/app/src/main/res/layout/fragment_mini_player.xml`
- Modify: `android/app/src/main/res/layout/fragment_queue.xml`
- Modify: `android/app/src/main/res/layout/item_album.xml`
- Modify: `android/app/src/main/res/layout/item_playlist.xml`
- Modify: `android/app/src/main/res/layout/item_queue_song.xml`
- Modify: `android/app/src/main/res/layout/item_message.xml`
- Modify: `android/app/src/main/res/layout/item_loading.xml`

- [ ] **Step 1: 统一 mini_player**

读取文件，检查当前按钮背景是否使用 `bg_icon_button`，如不是则统一

- [ ] **Step 2: 统一 fragment_queue**

读取文件，确认列表项使用 Card 包裹，圆角 16dp

- [ ] **Step 3: 统一 album item**

读取文件，将封面圆角统一为 12dp（适合方形封面）

- [ ] **Step 4: 统一其他 item**

批量处理 item_playlist, item_queue_song, item_message, item_loading，统一边距和圆角

- [ ] **Step 5: 编译验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

---

## Task 9: 最终验收

- [ ] **Step 1: 运行完整编译**

```bash
cd android && ./gradlew clean assembleDebug
```

- [ ] **Step 2: 检查所有布局文件边距**

确认所有 `android:layout_marginHorizontal` 或 `android:paddingHorizontal` 为 24dp（列表项内边距 16dp 除外）

- [ ] **Step 3: 检查所有卡片圆角**

确认所有 MaterialCardView 的 `app:cardCornerRadius` 为 16dp 或 20dp 或 28dp，无其他值

- [ ] **Step 4: 检查文字样式**

确认所有 TextView 使用 `android:textAppearance` 而非直接 `android:textColor`/`android:textStyle`

- [ ] **Step 5: Git 提交**

```bash
git add -A && git commit -m "feat(ui): 统一所有页面 UI 风格

- 统一边距为 24dp
- 统一卡片圆角为 20dp
- 统一 TextInputLayout 为 Material3 FilledBox
- 统一按钮样式
- 统一文字样式使用 TextAppearance

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验收标准

- [ ] `themes.xml` 包含 TextInputLayout/Chip 统一样式
- [ ] 所有页面水平边距统一为 24dp
- [ ] 所有卡片圆角为 16dp/20dp/28dp（无其他值）
- [ ] 所有 TextInputLayout 使用 `Widget.VoiceAssistant.TextInputLayout`
- [ ] 所有按钮使用定义好的 style
- [ ] 编译通过，无布局错误
