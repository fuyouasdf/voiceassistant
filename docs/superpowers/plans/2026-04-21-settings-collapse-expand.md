# 设置页面收缩/展开 + 移除唤醒词配置 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
1. 为设置页面的三个板块添加收缩/展开按钮
2. 移除 Voice Settings 中的唤醒词配置选项

**Architecture:**
- 收缩/展开：在 Section Header 添加图标按钮，点击切换 Card visibility
- 移除唤醒词：删除 Wake Words 相关的 UI 组件和相关 Kotlin 代码

**Tech Stack:** Android XML Layouts, Kotlin

---

## 文件变更映射

### 布局文件
- `android/app/src/main/res/layout/activity_settings.xml` - 添加展开/收缩按钮 + 移除唤醒词配置

### Kotlin 文件
- `android/app/src/main/java/com/voiceassistant/app/ui/settings/SettingsActivity.kt` - 移除唤醒词相关代码

### Drawable 文件
- `android/app/src/main/res/drawable/ic_arrow_up.xml` - 新建
- `android/app/src/main/res/drawable/ic_arrow_down.xml` - 新建

---

## Task 1: 修改 activity_settings.xml

**Files:**
- Modify: `android/app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: 为 Music Service Section Header 添加展开/收缩按钮**

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

- [ ] **Step 2: 为 Music Service Card 添加 ID**

找到:
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

- [ ] **Step 3: 为 AI Service Section Header 添加展开/收缩按钮**

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

找到:
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

- [ ] **Step 5: 为 Voice Settings Section Header 添加展开/收缩按钮**

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

找到:
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

- [ ] **Step 7: 移除 Wake Words 配置相关组件**

找到 (第 550-597 行左右，"/TTS Pitch" 部分之后):
```xml
                <!-- Wake Words Management -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/settings_wake_words"
                    android:textColor="@color/text_primary"
                    android:textSize="16sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/settings_wake_words_hint"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnTestKws"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="@string/settings_test_kws"
                    style="@style/Widget.VoiceAssistant.Button.Ghost" />

                <TextView
                    android:id="@+id/tvKwsStatus"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:visibility="gone" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rvWakeWords"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:nestedScrollingEnabled="false" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnAddWakeWord"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="@string/settings_wake_words_add"
                    style="@style/Widget.VoiceAssistant.Button.Ghost" />

```

删除这些组件。

- [ ] **Step 8: 创建 ic_arrow_up.xml**

创建文件 `android/app/src/main/res/drawable/ic_arrow_up.xml`:
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

- [ ] **Step 9: 创建 ic_arrow_down.xml**

创建文件 `android/app/src/main/res/drawable/ic_arrow_down.xml`:
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

- [ ] **Step 10: 编译验证**

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android && ./gradlew :app:assembleDebug
```

---

## Task 2: 修改 SettingsActivity.kt

**Files:**
- Modify: `android/app/src/main/java/com/voiceassistant/app/ui/settings/SettingsActivity.kt`

- [ ] **Step 1: 移除 Wake Words 相关的声明**

找到并删除以下声明 (第 96-98 行):
```kotlin
    private lateinit var rvWakeWords: RecyclerView
    private lateinit var btnAddWakeWord: MaterialButton
    private lateinit var btnTestKws: MaterialButton
    private lateinit var tvKwsStatus: TextView
```

同时删除第 107-108 行:
```kotlin
    private var wakeWordsList = mutableListOf<WakeWord>()
    private val supportedWakeWords: Set<String> by lazy { loadSupportedWakeWords() }
```

以及第 108 行:
```kotlin
    private lateinit var wakeWordAdapter: WakeWordAdapter
```

添加新的声明，在 `btnSave` 之后添加:
```kotlin
    // Section Collapse
    private lateinit var cardMusicService: MaterialCardView
    private lateinit var cardAiService: MaterialCardView
    private lateinit var cardVoiceSettings: MaterialCardView
    private lateinit var btnCollapseMusic: ImageButton
    private lateinit var btnCollapseAi: ImageButton
    private lateinit var btnCollapseVoice: ImageButton
```

添加新的 import:
```kotlin
import android.widget.ImageButton
import com.google.android.material.card.MaterialCardView
```

- [ ] **Step 2: 移除 initViews() 中 Wake Words 相关的初始化**

删除 (约第 174-186 行):
```kotlin
        rvWakeWords = findViewById(R.id.rvWakeWords)
        btnAddWakeWord = findViewById(R.id.btnAddWakeWord)
        btnTestKws = findViewById(R.id.btnTestKws)
        tvKwsStatus = findViewById(R.id.tvKwsStatus)

        // Initialize wake words RecyclerView
        wakeWordAdapter = WakeWordAdapter(
            wakeWords = wakeWordsList,
            onEdit = { position, wakeWord -> showEditWakeWordDialog(position, wakeWord) },
            onDelete = { position -> wakeWordAdapter.removeAt(position) }
        )
        rvWakeWords.layoutManager = LinearLayoutManager(this)
        rvWakeWords.adapter = wakeWordAdapter
```

在 `btnSave = findViewById(R.id.btnSave)` 之后添加:
```kotlin
        // Section Collapse
        cardMusicService = findViewById(R.id.cardMusicService)
        cardAiService = findViewById(R.id.cardAiService)
        cardVoiceSettings = findViewById(R.id.cardVoiceSettings)
        btnCollapseMusic = findViewById(R.id.btnCollapseMusic)
        btnCollapseAi = findViewById(R.id.btnCollapseAi)
        btnCollapseVoice = findViewById(R.id.btnCollapseVoice)
```

- [ ] **Step 3: 移除 setupListeners() 中 Wake Words 相关的 listener**

删除:
```kotlin
        btnAddWakeWord.setOnClickListener {
            showAddWakeWordDialog()
        }

        btnTestKws.setOnClickListener {
            showKwsDiagnostics()
        }
```

在 `btnSave.setOnClickListener` 之前添加收缩/展开监听:
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

- [ ] **Step 4: 移除 loadSettings() 中 Wake Words 相关的加载代码**

删除 (约第 304-317 行):
```kotlin
            // Load Wake Words
            val storedWords = settingsRepository.getWakeWords()
            wakeWordsList.clear()
            storedWords.forEach { line ->
                val parts = line.split(":", limit = 2)
                wakeWordsList.add(WakeWord(
                    keyword = parts[0].trim(),
                    response = parts.getOrNull(1)?.trim() ?: "我在"
                ))
            }
            wakeWordAdapter.notifyDataSetChanged()

            // Show initial KWS diagnostics
            showKwsDiagnostics()
```

- [ ] **Step 5: 删除 Wake Words 相关的 Dialog 方法**

删除:
```kotlin
    private fun showAddWakeWordDialog() { ... }
    private fun showEditWakeWordDialog(position: Int, wakeWord: WakeWord) { ... }
    private fun isSupportedWakeWord(keyword: String): Boolean { ... }
    private fun showUnsupportedWakeWordMessage(keyword: String) { ... }
    private fun loadSupportedWakeWords(): Set<String> { ... }
    private fun showKwsDiagnostics() { ... }
```

- [ ] **Step 6: 移除 saveSettings() 中 Wake Words 相关的保存代码**

删除 (约第 627-639 行):
```kotlin
                val unsupportedWords = wakeWordsList
                    .map { it.keyword }
                    .filterNot { isSupportedWakeWord(it) }
                if (unsupportedWords.isNotEmpty()) {
                    val unsupported = unsupportedWords.joinToString("、")
                    showUnsupportedWakeWordMessage(unsupported)
                    return@launch
                }

                // Save Wake Words
                val wakeWordsLines = wakeWordsList.map { "${it.keyword}:${it.response}" }
                settingsRepository.setWakeWords(wakeWordsLines)
```

同时删除:
```kotlin
                // Reload KWS with new wake words
                voicePipeline.reloadWakeWords(wakeWordsList)
```

- [ ] **Step 7: 添加 toggleSection 方法**

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

- [ ] **Step 8: 编译验证**

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android && ./gradlew :app:assembleDebug
```

---

## Task 3: Git 提交

```bash
cd C:/Users/qweqwe/AndroidStudioProjects/voice-assistant && git add -A && git commit -m "feat(ui): 设置页面添加收缩/展开功能，移除唤醒词配置

- 每个设置板块添加展开/收缩按钮
- 点击按钮切换内容区域显示/隐藏
- 移除唤醒词配置选项（Wake Words Management）
- 保留唤醒灵敏度配置

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验收标准

- [ ] Music Service 板块有展开/收缩按钮
- [ ] AI Service 板块有展开/收缩按钮
- [ ] Voice Settings 板块有展开/收缩按钮
- [ ] 点击按钮能正确切换显示/隐藏
- [ ] Wake Words Management 已移除
- [ ] 编译通过
