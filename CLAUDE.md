# CLAUDE.md — shanka_app 项目记忆

> 本文件是项目的**权威记忆**，每次会话自动加载，跨所有对话框共享。
> UI 细节约束见 [AGENTS.md](AGENTS.md)（Material 3 / Navigation 3 / edge-to-edge / styles 规则），两者配合使用。
> 内容由 Claude 于 2026-08-12 基于 codex 记忆 + 实际代码核验整理，已修正过时信息。

## 项目概览

闪卡（flashcard）App。Android 前端在 `Front/`（Gradle 根），Compose Material 3。代码主 UI 在 `Front/app/src/main/java/com/qiuzhao/flashcards/`。视觉体系为 Figma 派生的 **402dp** 尺度；字体与图标的唯一规范见 `docs/design-system/qiuzhao-flashcards/FONT_LIBRARY.md`。

## 构建环境与工作流（非显然，重要）

- WSL 内**没有 Linux JDK**，直接 `./gradlew :app:assembleDebug` 会报 `JAVA_HOME is not set`。
- 可行绕法：把 `Front` 复制到 Windows 侧 `/mnt/c/Users/admin/Documents/ChatGPT/闪卡app/`，再运行
  `cmd.exe /c "set JAVA_HOME=C:\Users\admin\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2 && call gradlew.bat :app:assembleDebug --console=plain"`。
- `testDebugUnitTest` 报 ClassNotFoundException 而编译产物 .class 存在 → 多为该临时 Windows 环境问题，非代码问题。

## Figma 设计 → 代码（Figma 读取方式，非显然）

- 默认优先用 Codex 应用内 Figma 连接器工具（`get_design_context` / `get_screenshot` / `use_figma`）。
- 当会话是**非 Codex 原生模型**、或连接器“插件安装失败”时，这些工具不会被注入；改用
  **Figma REST API + Personal Access Token（PAT，`figd_...`，权限 File content: read）**，
  读取节点树、导出节点渲染图，再严格按 Figma + 真机比对。完整流程见
  `docs/design-system/qiuzhao-flashcards/FIGMA_IMPLEMENTATION_WORKFLOW.md`。
- 已知节点：`493:1386` = `资料管理-导入/编辑资料-导入文本`（全局蓝白页，不继承项目主题），
  实现在 `ui/ProjectScreen.kt` 的 `ProjectTextEditorScreen` / `ProjectTextField`。

## 演示 / 真实边界（防误判）

- `PdfSmartCardsFlow` 是 **demo-only**，其"完成卡片"是 `PdfSampleCards`，不入库。
- DeepSeek key 校验、AI 改写是**本地演示**，不持久化、不发送 key。
- 首页 goal/streak 与数据图表部分是**显示用固定数据**。
- 交付评估必须区分 sample/formal 卡片、review/free practice、real/demo 数据。

## 当前前端架构（2026-08-12 现状）

- **Navigation 3**（`androidx.navigation3:1.0.0`），类型安全 `@Serializable AppRoute : NavKey`，共 15 个目的地，见 [ui/navigation/AppRoute.kt](Front/app/src/main/java/com/qiuzhao/flashcards/ui/navigation/AppRoute.kt)。无 navigation-compose2 / NavHost / NavController 残留。
- 三底栏（Home/Library/Data）**独立返回栈**，Home 常驻栈底、非 Home 根页返回 Home、仅 Home 根页退出应用，见 [ui/navigation/NavigationState.kt](Front/app/src/main/java/com/qiuzhao/flashcards/ui/navigation/NavigationState.kt)。
- **Edge-to-edge**：MainActivity `enableEdgeToEdge()` + Manifest `adjustResize` + `imePadding()`。
- **MD3E 动效底座**：`ui/motion/AppMotion.kt` 用 **stable API** 复刻 Material emphasized 缓动；页面级 `fadeIn/fadeOut`，核心卡片翻转等用统一强调弹簧。**刻意不用 alpha MotionScheme API**。
- **共享元素 / 连续容器转场尚未实现**。计划：Home 卡组卡片 → `AppRoute.Deck(id)` 详情页顶部概览容器，用 `SharedTransitionLayout` + deckId 共享 key。
- 主 UI 仍在 `ui/Screens.kt`（约 5273 行），尚未按功能拆分。

## 数据层

- `data/FlashcardData.kt`：Room 实体 `DeckEntity`/`FlashcardEntity`/`ReviewStateEntity`/`ReviewHistoryEntity`。
- `ReviewScheduler` 是 FlashcardData.kt 内的 `object`（**不是独立文件**）：`Rating = AGAIN/HARD/GOOD`，间隔 10min/1d/3d/7d；AGAIN 归零，HARD/GOOD 推进 `intervalStep`。
- `data/ImportParser.kt`：识别 `Q/A`、`问题/答案`、Markdown 标题、段落分块。

## PRD（docs/PRD/）

- [PRD_V2_1.md](docs/PRD/PRD_V2_1.md)：FR-01~FR-18、AC-01~AC-08。v2.1 硬规则：恰好 3 张样卡不入库；Schema 是唯一准入、Rubric 只观察；生成支持批量/游标/幂等 ID/断点续传；改写失败保留旧卡；日志不得含 API key/完整 PDF/完整 prompt。
- [产品文档_傻瓜版.md](docs/PRD/产品文档_傻瓜版.md)：面向设计师的大白话文档。
- **未决决策 D-01~D-04**（决定前不要擅自实现）：D-01 判断题结构化 schema；D-02 账号 vs 匿名设备数据归属；D-03 DeepSeek key 传输/存储边界；D-04 单卡真实改写是否纳入 v2.1。

## 用户偏好（行为准则）

- 要"安装包"→ 交付**可直接下载、已验证构建的 APK**，不只是源码改动。
- "只做前端表现，不实现后端能力" → 保持 PDF/API/AI 行为本地、mock。
- "参考现有 App 已形成的视觉风格" → **窄改动 + 真机验证**，不重设计（除非明确要求重做）。
- 涉及数据模型/认证/安全/范围冲突时，**说明冲突并提问**，不静默解决。
