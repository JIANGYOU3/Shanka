# 秋招刷题闪卡｜字库与字重

本项目使用的字库均为可变字体（VF）。所有数值为字体轴的真实 `wght` 值，而不是 Android/CSS 的近似名称。英文、数字、百分比、标点使用 Google Sans Flex；中文使用 MiSans VF。

## MiSans VF（中文）

| 样式名 | `wght` | 项目令牌 |
| --- | ---: | --- |
| Thin | 150 | `AppFonts.MiSansThin` |
| ExtraLight | 200 | `AppFonts.MiSansExtraLight` |
| Light | 250 | `AppFonts.MiSansLight` |
| Normal | 305 | `AppFonts.MiSansNormal` |
| Regular | 330 | `AppFonts.MiSansRegular` |
| Medium | 380 | `AppFonts.MiSansMedium` |
| Demibold | 450 | `AppFonts.MiSansDemibold` |
| Semibold | 520 | `AppFonts.MiSansSemibold` |
| Bold | 630 | `AppFonts.MiSansBold` |
| Heavy | 700 | `AppFonts.MiSansHeavy` |

## Google Sans Flex（英文与数字）

| 使用场景 | `wght` | `ROND` | `wdth` | `GRAD` | 项目令牌 |
| --- | ---: | ---: | ---: | ---: | --- |
| 常规 | 默认 | 100 | 100 | 0 | `AppFonts.GoogleSansFlex` |
| Semibold | 520 | 100 | 100 | 0 | `AppFonts.GoogleSansFlexSemibold` |
| Bold | 700 | 100 | 100 | 0 | `AppFonts.GoogleSansFlexBold` |
| ExtraBold | 800 | 100 | 100 | 0 | `AppFonts.GoogleSansFlexExtraBold` |

## 本次卡片组件的精确规则

| 元素 | 字库与轴 | 字号/行高 | 字距 |
| --- | --- | --- | ---: |
| 卡组标题 | MiSans VF Bold 630 | 20/24dp | 0 |
| 高优先级 | MiSans VF Semibold 520 | 16/20dp | 0 |
| 徽标上行数字 | Google Sans Flex ExtraBold 800, ROND 100 | 16/16dp | 0.6dp |
| 徽标下行中文 | MiSans VF Bold 630 | 16/16dp | 0.4dp |
| 徽标下行英文 | Google Sans Flex ExtraBold 800, ROND 100 | 16/16dp | 0.4dp |
| 进度 | MiSans VF Semibold 520 | 16/20dp | 0 |
| 百分比 | Google Sans Flex Bold 700, ROND 100, GRAD 0 | 24/28dp | 0 |
| 继续复习 | MiSans VF Bold 630 | 20/20dp | 0 |

## 图标字库

图标使用 `Material Symbols Rounded`，`FILL=1`、`wght=400`、`GRAD=200`、`opsz=24`。仅三个主界面左上角的 settings 图标例外使用 `FILL=0`。

字体文件：

- `Front/app/src/main/res/font/misans_vf.ttf`
- `Front/app/src/main/res/font/google_sans_flex.ttf`
- `Front/app/src/main/res/font/material_symbols_rounded.ttf`
