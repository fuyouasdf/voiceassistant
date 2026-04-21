# 设置页面收缩/展开功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为设置页面的三个板块（音乐服务、AI服务、语音设置）添加收缩/展开按钮，便于用户快速访问常用设置

**Architecture:** 在每个 Section Header 添加可点击的展开/收缩图标按钮，点击时切换内容区域的 visibility。状态默认全部展开。

**Tech Stack:** Android XML Layouts, View Animation, Kotlin

---

## 文件变更映射

### 需要修改的文件
- `android/app/src/main/res/layout/activity_settings.xml` - 添加展开/收缩图标按钮
- `android/app/src/main/java/com/voiceassistant/app/ui/settings/SettingsActivity.kt` - 添加展开/收缩逻辑

---

## Task 1: 修改 activity_settings.xml 布局

**Files:**
- Modify: `android/app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: 为 Music Service Section 添加展开/收缩按钮**

在 `settings_category_music` TextView 同一行添加一个 ImageButton：

找到 (第 29-37 行):
```xml
        <!-- Music Service Section -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="@string/settings_category_music"
            android:textColor="@color/secondary"
            android:textSize="14sp"
            android:textStyle="bold" />
```

替换为:
```xml
        <!-- Music Service Section Header -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/settings_category_music"
                android:textColor="@color/secondary"
                android:textSize="14sp"
                android:textStyle="bold" />

            <ImageButton
                android:id="@+id/btnCollapseMusic"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="收缩/展开"
                android:src="@drawable/ic_arrow_up"
                app:tint="@color/text_secondary" />
        </LinearLayout>
```

- [ ] **Step 2: 为 Music Service Card 添加 ID 并设置初始状态**

找到 Music Service Card (第 39 行开始):
```xml
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
```

替换为:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardMusicService"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
```

- [ ] **Step 3: 为 AI Service Section 添加展开/收缩按钮**

找到 (第 152-161 行):
```xml
        <!-- AI Service Section -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="@string/settings_category_ai"
            android:textColor="@color/secondary"
            android:textSize="14sp"
            android:textStyle="bold" />
```

替换为:
```xml
        <!-- AI Service Section Header -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/settings_category_ai"
                android:textColor="@color/secondary"
                android:textSize="14sp"
                android:textStyle="bold" />

            <ImageButton
                android:id="@+id/btnCollapseAi"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="收缩/展开"
                android:src="@drawable/ic_arrow_up"
                app:tint="@color/text_secondary" />
        </LinearLayout>
```

- [ ] **Step 4: 为 AI Service Card 添加 ID**

找到 AI Service Card (第 162 行开始):
```xml
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
```

替换为:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardAiService"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
```

- [ ] **Step 5: 为 Voice Settings Section 添加展开/收缩按钮**

找到 (第 393-401 行):
```xml
        <!-- Voice Settings Section -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="@string/settings_category_voice"
            android:textColor="@color/secondary"
            android:textSize="14sp"
            android:textStyle="bold" />
```

替换为:
```xml
        <!-- Voice Settings Section Header -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/settings_category_voice"
                android:textColor="@color/secondary"
                android:textSize="14sp"
                android:textStyle="bold" />

            <ImageButton
                android:id="@+id/btnCollapseVoice"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="收缩/展开"
                android:src="@drawable/ic_arrow_up"
                app:tint="@color/text_secondary" />
        </LinearLayout>
```

- [ ] **Step 6: 为 Voice Settings Card 添加 ID**

找到 Voice Settings Card (第 403 行开始):
```xml
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
```

替换为:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardVoiceSettings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
```

- [ ] **Step 7: 检查 ic_arrow_up drawable 是否存在**

检查文件：`C:\Users\qweqwe\AndroidStudioProjects\voice-assistant\android\app\src\main\res\drawable\ic_arrow_up.xml`

如果不存在，创建：
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z"/>
</vector>
```

- [ ] **Step 8: 编译验证**

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android && ./gradlew :app:assembleDebug
```

---

## Task 2: 修改 SettingsActivity.kt 添加收缩/展开逻辑

**Files:**
- Modify: `android/app/src/main/java/com/voiceassistant/app/ui/settings/SettingsActivity.kt`

- [ ] **Step 1: 添加 Card 和 Button 的声明**

在现有声明区域添加 (第 101 行后):
```kotlin
    // Section Collapse
    private lateinit var cardMusicService: MaterialCardView
    private lateinit var cardAiService: MaterialCardView
    private lateinit var cardVoiceSettings: MaterialCardView
    private lateinit var btnCollapseMusic: ImageButton
    private lateinit var btnCollapseAi: ImageButton
    private lateinit var btnCollapseVoice: ImageButton
```

添加 import:
```kotlin
import android.widget.ImageButton
import com.google.android.material.card.MaterialCardView
```

- [ ] **Step 2: 在 initViews() 中初始化新增的 views**

在 `btnSave = findViewById(R.id.btnSave)` 后添加:
```kotlin
        // Section Collapse
        cardMusicService = findViewById(R.id.cardMusicService)
        cardAiService = findViewById(R.id.cardAiService)
        cardVoiceSettings = findViewById(R.id.cardVoiceSettings)
        btnCollapseMusic = findViewById(R.id.btnCollapseMusic)
        btnCollapseAi = findViewById(R.id.btnCollapseAi)
        btnCollapseVoice = findViewById(R.id.btnCollapseVoice)
```

- [ ] **Step 3: 在 setupListeners() 中添加收缩/展开点击事件**

在 `btnSave.setOnClickListener` 之前添加:
```kotlin
        // Section collapse/expand listeners
        btnCollapseMusic.setOnClickListener {
            toggleSection(cardMusicService, btnCollapseMusic)
        }
        btnCollapseAi.setOnClickListener {
            toggleSection(cardAiService, btnCollapseAi)
        }
        btnCollapseVoice.setOnClickListener {
            toggleSection(cardVoiceSettings, btnCollapseVoice)
        }
```

- [ ] **Step 4: 添加 toggleSection 方法**

在 `setupListeners()` 方法之后添加:
```kotlin
    private fun toggleSection(card: MaterialCardView, button: ImageButton) {
        if (card.visibility == View.VISIBLE) {
            // Collapse
            card.visibility = View.GONE
            button.setImageResource(R.drawable.ic_arrow_down)
        } else {
            // Expand
            card.visibility = View.VISIBLE
            button.setImageResource(R.drawable.ic_arrow_up)
        }
    }
```

- [ ] **Step 5: 创建 ic_arrow_down drawable**

检查文件：`C:\Users\qweqwe\AndroidStudioProjects\voice-assistant\android\app\src\main\res\drawable\ic_arrow_down.xml`

如果不存在，创建：
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6z"/>
</vector>
```

- [ ] **Step 6: 编译验证**

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android && ./gradlew :app:assembleDebug
```

---

## Task 3: Git 提交

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant && git add -A && git commit -m "feat(ui): 设置页面添加收缩/展开功能

- 每个设置板块添加展开/收缩按钮
- 点击按钮切换内容区域显示/隐藏
- 默认全部展开

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验收标准

- [ ] Music Service 板块有展开/收缩按钮
- [ ] AI Service 板块有展开/收缩按钮
- [ ] Voice Settings 板块有展开/收缩按钮
- [ ] 点击按钮能正确切换显示/隐藏
- [ ] 按钮图标正确切换（向上/向下箭头）
- [ ] 编译通过
