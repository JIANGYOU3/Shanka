# 闪卡 App 当前 UI 规范

> 更新日期：2026-08-26。用于跨平台和跨模型交接。
>
> 本文记录当前已实现且已在实体机验证的规则。新的用户指令和对应 Figma
> 节点优先级最高；本文件与代码冲突时，以代码与单测为准，并在同一变更中
> 更新本文。

## 读取顺序

新会话开始时，按以下顺序读取：

1. `AGENTS.md`：实现与实体机验收硬约束。
2. 本文：当前视觉语义与交接规则。
3. `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppTheme.kt`：颜色、圆角和字体 token 的实现真源。
4. `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckTheme.kt`：项目/卡组色彩映射与进度条语义。
5. `Front/app/src/test/java/com/qiuzhao/flashcards/ui/AppColorSystemTest.kt`：必须保持通过的色彩与卡片层级断言。
6. `FONT_LIBRARY.md`：字体和图标的完整规范。

`MASTER.md` 是历史设计说明，其中的旧色值、32dp 主圆角、旧卡片交替规则和旧
进度轨道说明均不再是当前约束；不得用它覆盖本文或代码中的更新规则。

## Figma 与实现原则

- 视觉真源始终是用户提供的 Figma 节点；不得用通用 Material 样式替代已明确的 Figma 数值。
- 画布基准是 402dp。页面使用既有的 `screenWidth / 402f` 缩放规则。
- Android 根目录是 `Front/`；应用为 Kotlin + Jetpack Compose Material 3。
- 每次 UI 改动必须构建、安装到连接的实体机并用截图核对；不要用模拟器替代实体机验收。

## 基础颜色

- 基础页面背景 / 基础卡片：`#FFFFFF`
- 深色文字与 icon：`#CC000000`（黑色 80%）
- 浅色文字与 icon：`#E6FFFFFF`（白色 90%）
- 根导航栏：`#425161`
- 警示：`#BD3F3F`；强警示 / 高优先级：`#D23535`

每个色系均按六阶固定命名：`Background / Surface / Primary-Secondary / Primary / Primary-Strong / Ink`。

| 色系 | Background | Surface | Primary-Secondary | Primary | Primary-Strong | Ink |
| --- | --- | --- | --- | --- | --- | --- |
| 蓝色（品牌） | `#EEF4FA` | `#CCE6FF` | `#B0D7FF` | `#389DFF` | `#0063C4` | `#003C7A` |
| 紫色 | `#F3F3FF` | `#E4E4FF` | `#C8C8FF` | `#716FDD` | `#3836B7` | `#38387A` |
| 绿色 | `#EAF4E5` | `#D6EEC9` | `#B6DCA6` | `#65AA56` | `#278B00` | `#1F5225` |
| 粉色 | `#F9EFF3` | `#F8DBE3` | `#FFBFD3` | `#F16692` | `#AA0047` | `#730022` |
| 橙色 | `#FAF2EB` | `#FBE7C8` | `#F4DDBA` | `#E48A4A` | `#D15700` | `#642A00` |

不要在 UI 页面散落写 Hex；通过 `AppColors`、`DeckTheme` 或语义 token 使用颜色。

## 卡组、项目与统计卡层级

- 卡组的色系跟随其所属项目；只有未关联项目的旧卡组才回退到卡组自身主题。
- 纯白页面（主页、项目页、数据页）上的卡组/项目卡片外层使用该色系的 `Background`。
- 色系 `Background` 页面（项目详情）上的卡组卡片外层使用该色系的 `Surface`。
- 卡组卡内部的计数徽标和进度面板使用与外层相邻的可见层级：白色页面卡的内部使用 `Surface`；色系背景页面卡的内部使用 `Background`。
- 数据页的四张统计卡，在纯白页面使用各自色系的 `Surface`；它们置于色系背景页面时使用基础白色。
- 进度条由两个独立的圆角段组成：已完成段为 `Primary`，剩余轨道为 `Primary-Secondary`。例如紫色为 `#716FDD / #C8C8FF`；禁止用 `Background` 作为该轨道。
- 进度百分比使用 `Primary-Strong`，主题重点文字使用 `Ink`。

当前相关 Figma 节点包括 `184:616`（主页）、`494:1447`（项目页）、`19:621`（数据页）、`258:6804` / `645:2168`（卡组进度组件）与 `604:2810`（问题类型卡）。

## 圆角与裁切

- 大卡片、主卡片、根导航栏和项目页切换器：`36dp`（`AppShapeRadius`）。
- 内嵌面板、普通按钮和选择容器：`24dp`（`AppNestedShapeRadius` / `AppButtonShapeRadius`）。
- 图标圆盘、进度条：`9999dp`。
- 每个固定头部下的全页滚动内容视口统一用 `24dp` 裁切（`AppScrollableContentClipRadius`）。这一规则适用于主页、项目页、数据页、项目详情、卡组详情和同类固定层页面；不要让滚动内容绘制进固定头部或底部操作层。

## 文字与图标

- 中文采用 MiSans VF，拉丁字母、数字、空白和标点采用 Google Sans Flex；混排使用既有 `MixedLanguageText` / `AppText`，不要自行建立系统回退字体链。
- 字重、字号、行高和字距来自 `AppTypographyTokens` 与 `FONT_LIBRARY.md`。
- 图标只使用 Material Symbols Rounded 既有规范：默认 `FILL=1`、`wght=400`、`GRAD=200`、`opsz=24`；仅已有明确例外可使用描边态。

## 验证与交接

每次改动至少执行：

1. `:app:testDebugUnitTest --tests com.qiuzhao.flashcards.ui.AppColorSystemTest`
2. `:app:assembleDebug`
3. 将 APK 安装到连接的实体机，检查目标页面的安全区、裁切、颜色、圆角、文字折行及交互。

该仓库已配置 GitHub 远端。跨平台时，新的模型只能看到已提交并推送到远端的源代码和文档；它不会自动继承本地聊天记录、未提交改动、设备截图、密钥或 `.codex` 本机配置。
