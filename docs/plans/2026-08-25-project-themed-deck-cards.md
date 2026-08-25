# Project-Themed Deck Cards Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the Figma deck/project card a reusable Compose component whose deck colours inherit from its owning project, while preserving the home-only "继续复习" action.

**Architecture:** Keep the existing five Figma `DeckTheme` families, but resolve a deck's presentation theme from its `projectId` whenever its project is available. A compact card API owns the top metadata row, count badge, two-segment rounded progress bar, and optional action. The caller selects a tinted or white outer-card variant; the white variant still uses the same project family for every nested surface and the completed progress segment.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation 3, JUnit 4, Android instrumentation screenshots on the connected physical phone.

---

### Task 1: Make colour inheritance and alternating card variants testable

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckTheme.kt`
- Modify: `Front/app/src/test/java/com/qiuzhao/flashcards/ui/AppColorSystemTest.kt`

1. Add a project-aware deck-theme resolver that falls back to legacy deck theme data only when no owning project exists.
2. Add a `Tinted`/`White` card variant and deterministic odd/even selector.
3. Test that a deck inside a violet project uses violet even if its legacy deck field is different, and that white-card nested colours remain in the violet family.

### Task 2: Extract the Figma 257:6634 card structure

**Files:**
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectThemedCard.kt`

1. Implement the Figma hierarchy: 56dp icon surface, title/priority, count pill, nested progress panel, and optional 61dp continue button.
2. Make the progress bar exactly two separately rounded sibling rectangles (completed + remaining); do not replace it with an overlaid Material progress indicator.
3. Reuse existing Material symbols only where they visibly match the Figma glyphs and reuse existing typography helpers.

### Task 3: Replace the three screen-local card implementations

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Chrome.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/HomeScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectDetailScreen.kt`

1. Pass projects to Home so the surfaced deck resolves its parent-project theme.
2. Keep Home's optional 61dp “继续复习” button and study navigation.
3. Render project-root cards in each project theme and card-group rows as tinted, white, tinted, white… based on their index.
4. Keep all tap targets and the established route behavior unchanged.

### Task 4: Verify and physically review

**Files:**
- Modify only if the checks expose a defect.

1. Run the focused unit tests and `:app:assembleDebug`.
2. Install the built APK only on the connected physical phone.
3. Capture Home, Project, and Project-detail/card-management screenshots. Compare safe areas, 402dp geometry, card alternation, project-derived colour use, and two-segment progress bars to Figma nodes `184:616`, `494:1447`, `15:3030`, and component `257:6634`.
4. Correct every observed mismatch before delivery.
