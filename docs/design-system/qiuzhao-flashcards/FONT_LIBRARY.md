# 闪卡 App 字体与图标设计系统

唯一设计来源：Figma `378:1764`、`378:1775`、`378:1805`、`379:2014`（另含 `427:4182` 登录主标题）。实现入口为 `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppTheme.kt` 与 `Components.kt`；业务页面不得自行组装字体族或字重。

## 字体文件与轴

| 脚本 | 文件 | 字重 | 固定轴 |
| --- | --- | --- | --- |
| 中文 | `misans_vf.ttf` | 150, 200, 250, 305, 330, 380, 450, 520, 630, 700 | `wght` |
| 英文、数字、空格、符号、标点 | `google_sans_flex.ttf` | 100–900（每 100 一档） | `ROND=100`、`wdth=100`、`GRAD=0`、`wght` |

没有双语回退字体族，也不得由 Android 合成字重。混排按逐字符连续段处理：Han 字符使用 MiSans；其余字符全部使用 Google Sans Flex。

## 文本角色

| 角色 | 中文（MiSans） | 英文/数字（Google Sans Flex） |
| --- | --- | --- |
| PageTitle | Semibold 520, 24/32, 0 | Bold 700, 24/30, 0 |
| AuthHeroTitle | Semibold 520, 32/32, 0 | Bold 700, 32/32, 0 |
| SectionTitle | Bold 630, 20/27, 0 | Bold 700, 20/20, 0 |
| CardTitle | Bold 630, 18/24, 0 | Bold 700, 18/18, 0 |
| CardSubtitle | Semibold 520, 16/21, 0 | Semibold 600, 16/16, 0 |
| Body | Medium 380, 20/27, 0 | Regular 400, 20/27, 0 |
| Supporting | Medium 380, 18/24, 0 | Medium 500, 18/24, 0 |
| Label | Bold 630, 16/21, 0.6 | ExtraBold 800, 16/20, 0.4 |
| MetricXSmall | Bold 630, 20/28, 0 | Bold 700, 20/28, 0 |
| MetricSmall | Bold 630, 24/24, 0.6 | ExtraBold 800, 24/24, 0.6 |
| MetricMedium | Bold 630, 40/40, -0.6 | Bold 700, 40/40, -0.6 |
| MetricLarge | Bold 630, 48/48, 0 | Bold 700, 48/48, 0 |

字号/行高/字距单位均为 dp（字距在 Compose 中以等值 sp 传入）。混排行高取中英文两套规范中较大的值，避免裁切。Figma 展示名 `MetricMeduim` 在代码中统一为 `MetricMedium`，数值不变。`AuthHeroTitle` 是登录/注册主标题专用语义（Figma `427:4182`），当前登录页主标题“欢迎使用，请登录”已使用该角色。

## 导航栏专用文字

Figma `378:1775` 额外定义了当前三个根导航的中文标签；它们不是通用 `Label`，必须使用 `navigationBarLabelTextStyle`，且不能继续沿用历史的 14/16、selected 700 规则。

| 状态 | 中文（MiSans） | 当前英文规范 |
| --- | --- | --- |
| 选中 | Bold 630, 14/18, 0.6 | 未在此 Figma 节点定义；根导航文案保持中文 |
| 未选中 | Semibold 520, 14/18, 0.6 | 未在此 Figma 节点定义；根导航文案保持中文 |

按钮、徽标和操作文字统一使用 `Label`：中文 Bold 630；英文/数字 ExtraBold 800。因此按钮不会采用较细的默认 Material 标签字重。

## Compose 使用规则

- 展示文案使用 `AppText(text, role, ...)`。
- 实时输入使用 `appInputTextStyle(role, ...)` 与 `rememberBilingualInputTransformation(role, ...)`；该变换保持 `OffsetMapping.Identity`，不改变原文、光标、选区或输入法组合范围。
- 根导航标签使用 `navigationBarLabelTextStyle(selected, designScale)`；其字体 token 来自 Figma `378:1775`。
- 仅 `MaterialSymbol` 可以直接设置字体族。

## 图标

图标唯一体系为 `material_symbols_rounded.ttf`：`FILL=1`、`wght=400`、`GRAD=200`（Emphasis）、`opsz=24`。头像等位图资源不属于图标字体。

只有首页、学习、数据三个主页面中各自的 settings 控件可使用 `FILL=0`；其他图标均为默认填充态。不得新增 SVG、Outlined、Sharp 或其他图标体系。
