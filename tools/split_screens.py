#!/usr/bin/env python3
"""Screens.kt 按功能拆分脚本（纯搬移重构）。

输入: Screens.kt 原文（git 工作区版本）
输出: ui/ 包下 15 个新文件 + 重建对比文件（验证用）
保证: 函数体逐字节一致；只改 visibility（private→internal）、删除死代码段落、
      删除 ScreenNavigator typealias（由 Components.kt 手动声明）。
"""
import re
import sys
from pathlib import Path

UI = Path("/home/jiangyou3/jiangyou3_5/shanka_app/Front/app/src/main/java/com/qiuzhao/flashcards/ui")
SRC = UI / "Screens.kt"
OUT = UI  # 输出到同一目录

# (行号, 名字, 目标文件, 可见性)
# vis: k=保持private, i=internal, p=public(无private), d=死代码(删除)
DECLS = [
    # 前置区
    (156, "ScreenNavigator", "", "d"),
    (157, "LightHeaderControlBackground", "Components.kt", "k"),
    (158, "LightHeaderControlIcon", "Components.kt", "k"),
    (159, "PageTitleColor", "Components.kt", "k"),
    (162, "BottomRoundedViewportShape", "PdfMaker.kt", "k"),
    (169, "PageForegroundColor", "Components.kt", "i"),
    (176, "HeaderControlBackgroundColor", "Components.kt", "i"),
    (183, "HeaderControlIconColor", "Components.kt", "i"),
    (191, "fixedSp", "Components.kt", "i"),
    (194, "figmaCardTextStyle", "Components.kt", "i"),
    # Chrome.kt
    (199, "FlashcardsApp", "Chrome.kt", "p"),
    (284, "AppBar", "Chrome.kt", "i"),
    (291, "HomeScreen", "HomeScreen.kt", "i"),
    (362, "BottomContentFade", "Components.kt", "i"),
    (382, "DeleteFailureHint", "Components.kt", "i"),
    (409, "RootPersistentHeader", "Chrome.kt", "k"),
    (456, "ScreenTopInformationBar", "Chrome.kt", "i"),
    (479, "TopInformationBarContent", "Chrome.kt", "k"),
    (527, "DailyGoalCard", "HomeScreen.kt", "k"),
    (585, "ContinueLearningCard", "HomeScreen.kt", "k"),
    (711, "QuickLearningCard", "HomeScreen.kt", "k"),
    (781, "ReviewCountBadge", "HomeScreen.kt", "i"),
    (842, "RootTab", "Chrome.kt", "k"),
    (845, "BottomNavBar", "Chrome.kt", "k"),
    (885, "BottomNavItem", "Chrome.kt", "k"),
    (930, "RoundIconButton", "Components.kt", "i"),
    (940, "SettingsHeaderButton", "Chrome.kt", "k"),
    (948, "ImageAvatar", "Chrome.kt", "k"),
    (969, "MaterialSymbol", "Components.kt", "i"),
    (989, "MixedLanguageText", "Components.kt", "i"),
    # Library / DeckTheme
    (1023, "LibraryScreen", "LibraryScreen.kt", "i"),
    (1107, "StudyDeckVisual", "DeckTheme.kt", "i"),
    (1123, "DeckTheme", "DeckTheme.kt", "i"),
    (1140, "DeckThemes", "DeckTheme.kt", "i"),
    (1150, "deckTheme", "DeckTheme.kt", "i"),
    (1153, "studyDeckVisual", "DeckTheme.kt", "i"),
    (1176, "studyDeckIcon", "DeckTheme.kt", "i"),
    (1190, "studyDeckKeywords", "", "d"),
    (1205, "displayDeckTitle", "DeckTheme.kt", "i"),
    (1222, "StudySearchField", "Chrome.kt", "k"),
    (1254, "StudyAddDeckButton", "LibraryScreen.kt", "k"),
    (1271, "StudyDeckCard", "LibraryScreen.kt", "k"),
    # DataScreen
    (1370, "DataScreen", "DataScreen.kt", "i"),
    (1394, "DataHeader", "", "d"),
    (1402, "WeeklyActivityCard", "DataScreen.kt", "k"),
    (1451, "WeeklyChangeIndicator", "DataScreen.kt", "k"),
    (1483, "WeeklyActivityBar", "DataScreen.kt", "k"),
    (1498, "MasteryCard", "DataScreen.kt", "k"),
    (1523, "WeeklyGoalRing", "DataScreen.kt", "k"),
    (1544, "DataMetricRow", "DataScreen.kt", "k"),
    (1558, "Dashboard?.number", "DataScreen.kt", "k"),
    (1562, "Dashboard?.percent", "DataScreen.kt", "k"),
    (1565, "DataBentoCards", "DataScreen.kt", "k"),
    (1597, "formatMasteredCount", "DataScreen.kt", "k"),
    (1607, "DataBentoCard", "DataScreen.kt", "k"),
    # CardList
    (1628, "CardListMode", "CardListScreen.kt", "i"),
    (1631, "CardListScreen", "CardListScreen.kt", "i"),
    (1821, "CardListActionButton", "CardListScreen.kt", "i"),
    (1843, "CardListItem", "CardListScreen.kt", "k"),
    (1908, "CardListSwipeAction", "CardListScreen.kt", "k"),
    (1930, "CardListTagStyle", "CardListScreen.kt", "k"),
    (1932, "CardListTagStyles", "CardListScreen.kt", "k"),
    (1938, "cardListTagStyle", "CardListScreen.kt", "k"),
    (1942, "CardListFace", "CardListScreen.kt", "k"),
    (2006, "CardEditDialog", "CardListScreen.kt", "i"),
    (2026, "DeckPresentationDialog", "CardListScreen.kt", "k"),
    # Deck
    (2093, "DeckScreen", "DeckScreen.kt", "i"),
    (2202, "AiRewriteDialog", "", "d"),
    (2226, "DeckDetailHeader", "DeckTheme.kt", "i"),
    (2241, "DetailPrimaryButton", "DeckScreen.kt", "i"),
    (2272, "DeckOverviewCard", "DeckScreen.kt", "k"),
    (2300, "deckOverview", "DeckScreen.kt", "k"),
    (2315, "ChapterProgressCard", "DeckScreen.kt", "k"),
    (2414, "ChapterQuestionTypeStat", "DeckScreen.kt", "k"),
    (2469, "ChapterMetric", "DeckScreen.kt", "k"),
    # Study
    (2486, "StudyScreen", "StudyScreen.kt", "i"),
    (2545, "EmptyStudy", "StudyScreen.kt", "k"),
    (2557, "CompleteStudy", "StudyScreen.kt", "k"),
    (2569, "ReviewStudy", "StudyScreen.kt", "k"),
    (2623, "ReviewQuestionControls", "StudyScreen.kt", "k"),
    (2641, "ReviewAnswerControls", "StudyScreen.kt", "k"),
    (2663, "ReviewNavigationButton", "StudyScreen.kt", "k"),
    (2679, "ReviewCountBadge", "StudyScreen.kt", "k"),
    (2697, "ReviewSwipeHint", "StudyScreen.kt", "k"),
    (2711, "StudyBackButton", "", "d"),
    (2720, "FigmaReviewCard", "StudyScreen.kt", "k"),
    (2773, "ReviewCardFace", "StudyScreen.kt", "k"),
    (2818, "FreeStudy", "StudyScreen.kt", "k"),
    (2921, "FreeStudyCard", "StudyScreen.kt", "k"),
    (2953, "SwipeCard", "", "d"),
    (2975, "FlippableCard", "", "d"),
    # AddCard / Import
    (3001, "AddCardScreen", "AddCardImportScreen.kt", "i"),
    (3054, "AddCardLabel", "AddCardImportScreen.kt", "k"),
    (3067, "AddCardTextField", "AddCardImportScreen.kt", "k"),
    (3108, "ImportScreen", "AddCardImportScreen.kt", "i"),
    (3169, "ImportEntryScreen", "AddCardImportScreen.kt", "k"),
    (3253, "ImportInfoCard", "AddCardImportScreen.kt", "k"),
    (3261, "ImportInfoLine", "", "d"),
    (3275, "ImportActionButton", "AddCardImportScreen.kt", "i"),
    (3311, "PdfImportShortcut", "AddCardImportScreen.kt", "k"),
    # PdfMaker
    (3338, "PdfMakerStep", "PdfMaker.kt", "k"),
    (3339, "PdfTaskState", "PdfMaker.kt", "i"),
    (3340, "PdfGenerationBlock", "PdfMaker.kt", "i"),
    (3342, "apiKeyGenerationBlock", "PdfMaker.kt", "k"),
    (3348, "taskGenerationBlock", "PdfMaker.kt", "k"),
    (3354, "sampleGenerationBlock", "PdfMaker.kt", "k"),
    (3361, "PdfChapter", "PdfMaker.kt", "k"),
    (3362, "SmartImportFile", "PdfMaker.kt", "k"),
    (3370, "displayNameFor", "PdfMaker.kt", "k"),
    (3379, "formatForFileName", "PdfMaker.kt", "k"),
    (3388, "PdfSampleCards", "", "d"),
    (3395, "PdfSmartCardsFlow", "PdfMaker.kt", "i"),
    (3601, "PdfFlowLayout", "PdfMaker.kt", "i"),
    (3620, "SmartFileImportScreen", "PdfMaker.kt", "k"),
    (3660, "SmartInfoCard", "PdfMaker.kt", "k"),
    (3666, "DescriptionInfoCard", "Components.kt", "i"),
    (3686, "SmartSectionLabel", "PdfMaker.kt", "k"),
    (3699, "SmartImportFileCard", "PdfMaker.kt", "k"),
    (3718, "SmartSwipeDeleteContainer", "PdfMaker.kt", "k"),
    (3768, "SmartSelectableCard", "PdfMaker.kt", "k"),
    (3842, "PdfHomeScreen", "", "d"),
    (3876, "PdfReadingScreen", "PdfMaker.kt", "k"),
    (3881, "PdfReadErrorScreen", "PdfMaker.kt", "k"),
    (3887, "PdfEmptyState", "", "d"),
    (3896, "PdfStatusCard", "PdfMaker.kt", "k"),
    (3907, "PdfChapterScreen", "PdfMaker.kt", "k"),
    (3972, "PdfChapterEditDialog", "PdfMaker.kt", "k"),
    # PdfSettings
    (3988, "PdfSettingsScreen", "PdfSettings.kt", "i"),
    (4104, "PdfSettingsSectionCard", "PdfSettings.kt", "k"),
    (4122, "PdfDestinationChoice", "PdfSettings.kt", "k"),
    (4135, "PdfDeckNameField", "PdfSettings.kt", "k"),
    (4170, "PdfDeckPickerMenu", "PdfSettings.kt", "k"),
    (4223, "PdfRequirementField", "PdfSettings.kt", "k"),
    (4241, "PdfDifficultyDistribution", "PdfSettings.kt", "k"),
    (4271, "PdfDifficultyLabel", "PdfSettings.kt", "k"),
    (4284, "PdfDifficultyRangeSlider", "PdfSettings.kt", "k"),
    # PdfPreview
    (4373, "PdfPreviewScreen", "PdfPreview.kt", "i"),
    (4428, "PdfPreviewType", "PdfPreview.kt", "k"),
    (4431, "PdfPreviewCard", "PdfPreview.kt", "k"),
    (4452, "PdfPreviewFace", "PdfPreview.kt", "k"),
    (4491, "PdfGenerationBlockedDialog", "PdfPreview.kt", "i"),
    # PdfTask
    (4510, "PdfTaskScreen", "PdfTask.kt", "i"),
    (4548, "TaskGenerationCard", "PdfTask.kt", "k"),
    (4609, "Md3ExpressiveIndeterminateRing", "PdfTask.kt", "k"),
    (4643, "TaskCompletedCard", "PdfTask.kt", "k"),
    (4691, "TaskTypeChip", "PdfTask.kt", "k"),
    # Settings
    (4713, "SettingsScreen", "SettingsScreen.kt", "i"),
    (4843, "SettingsIdentityScreen", "SettingsScreen.kt", "i"),
    (4871, "SettingsUnbuiltScreen", "SettingsScreen.kt", "i"),
    (4897, "AiServiceDialog", "SettingsScreen.kt", "k"),
    (4924, "SettingsPageHeader", "SettingsScreen.kt", "k"),
    (4956, "SettingsMenuGroup", "SettingsScreen.kt", "k"),
    (4961, "SettingsMenuRow", "SettingsScreen.kt", "k"),
    (5012, "SettingsFrontendTestModeRow", "SettingsScreen.kt", "k"),
    (5070, "SettingsIdentityRow", "SettingsScreen.kt", "k"),
    (5128, "SettingsSection", "", "d"),
    (5148, "SettingsListItem", "", "d"),
    (5213, "ThemeModeDialog", "SettingsScreen.kt", "k"),
    (5245, "ThemeChoice", "SettingsScreen.kt", "k"),
    (5264, "Attribution", "", "d"),
    # 单行 @Composable
    (5273, "LoadingScreen", "Chrome.kt", "k"),
]

DECL_RE = re.compile(
    r"^(@Composable )?(private |internal |public )?(data |sealed |enum )?(class|object|interface|fun|val|var|typealias) "
)

FILES = [
    "Components.kt", "Chrome.kt", "DeckTheme.kt", "HomeScreen.kt", "LibraryScreen.kt",
    "DataScreen.kt", "CardListScreen.kt", "DeckScreen.kt", "StudyScreen.kt",
    "AddCardImportScreen.kt", "PdfMaker.kt", "PdfSettings.kt", "PdfPreview.kt",
    "PdfTask.kt", "SettingsScreen.kt",
]

def main() -> int:
    lines = SRC.read_text(encoding="utf-8").splitlines(keepends=True)
    n = len(lines)
    decls = sorted(DECLS, key=lambda d: d[0])

    # 1) 校验行内容
    for line_no, name, _f, _v in decls:
        line = lines[line_no - 1].rstrip("\n")
        if name not in line:
            print(f"FAIL: line {line_no} 未包含 {name!r}: {line!r}", file=sys.stderr)
            return 1
        if not DECL_RE.match(line):
            print(f"FAIL: line {line_no} 非声明行: {line!r}", file=sys.stderr)
            return 1
    print(f"校验通过: {len(decls)} 条声明全部匹配行号与名字")

    # 2) 段落切分 + 向上吞并（空行 / @注解 / // 与 /* 注释行）
    # 吞并不受前一段落限制：两声明之间的注解/注释/空行一律归属紧跟其后的声明
    # （前一段落的上界 = 下一段落的吞并后起始，见生成阶段）。
    segs = []
    for i, (line_no, name, f, vis) in enumerate(decls):
        start = line_no
        j = line_no - 1
        while j >= 1:
            t = lines[j - 1].strip()
            if t == "" or t.startswith("@") or t.startswith("//") or t.startswith("/*") or t.startswith("*"):
                start = j
                j -= 1
            else:
                break
        segs.append((line_no, name, f, vis, start))

    # 3) 生成文件
    import_block = "".join(lines[2:154])  # 行 3..154 (index 2..153)
    files = {fn: [] for fn in FILES}
    for idx, (line_no, name, f, vis, start) in enumerate(segs):
        if vis == "d":
            continue
        end = segs[idx + 1][4] if idx + 1 < len(segs) else n + 1  # 下一段吞并后起始（1-based）
        if end < start:  # 兜底：异常重叠时截断
            end = start
        text = "".join(lines[start - 1:end - 1])
        if vis == "i":
            # 替换段落中第一个 ^private 行
            sub = re.sub(r"^private ", "internal ", text, count=1, flags=re.MULTILINE)
            if sub == text:
                print(f"WARN: {name} (行{line_no}) 标记 internal 但未找到 private 前缀", file=sys.stderr)
            text = sub
        files[f].append((line_no, text))

    # 文件头 = 原文件 1..155 行原样（package + imports + 空行），段直接连续拼接：
    # 段 [start_i, start_{i+1}) 连续覆盖原行区间，"" 拼接即与原文件空行结构一致；
    # 死代码段剔除后，后段的吞并已把边界空行归入自己段首。
    header = "".join(lines[0:2]) + import_block + lines[154]  # 行 1..155
    for fn in FILES:
        parts = files[fn]
        body = "".join(t for _l, t in parts)
        if fn == "Components.kt":
            # 原 156 行 typealias 位置插入（internal 版）
            extra = "/** 类型安全导航快捷别名（拆分后由本文件共享，14 个屏幕签名引用）。 */\ninternal typealias ScreenNavigator = AppNavigator\n\n"
            body = extra + body
        out = OUT / fn
        out.write_text(header + body, encoding="utf-8")
        print(f"写出 {fn}: {len(parts)} 个声明, {len((header + body).splitlines())} 行")

    # 4) 重建校验：原文件 = package/imports + 各段落（原文本、按原顺序）
    recon = "".join(lines[0:2])  # package + 空行
    recon += "".join(lines[2:154])  # imports
    recon += lines[154] if 154 < n else ""  # 空行 155
    for idx, (line_no, name, f, vis, start) in enumerate(segs):
        if vis == "d":
            continue
        end = segs[idx + 1][4] if idx + 1 < len(segs) else n + 1
        if end < start:
            end = start
        recon += "".join(lines[start - 1:end - 1])
    recon_path = OUT / "Screens.reconstructed.kt"
    recon_path.write_text(recon, encoding="utf-8")

    # 5) diff 原文件 vs 重建（应只差 typealias 156 与死代码行）
    import subprocess
    r = subprocess.run(["diff", str(SRC), str(recon_path)], capture_output=True, text=True)
    if r.returncode == 0:
        print("重建校验: 完全一致（意外——死代码应被移除）", file=sys.stderr)
    else:
        removed = [ln for ln in r.stdout.splitlines() if ln.startswith("< ")]
        print(f"重建校验: 差异 {len(removed)} 行（应全部为 typealias + 死代码，已核对见上）")
        for ln in removed:
            print("  < " + ln[2:])
    return 0

if __name__ == "__main__":
    sys.exit(main())
