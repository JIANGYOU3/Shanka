# Deck Summary Refresh Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the old deck overview with Figma node `297:8521`, apply the refreshed semantic colours, and make every deck view inherit its owning project's theme.

**Architecture:** Extract the new Figma cards into reusable Compose components that receive a resolved `DeckTheme` and real deck progress. Resolve that theme from `projectId` in deck and card-list flows; retain legacy deck colours only as the existing fallback for unassigned decks. The editor becomes name-only and describes the inherited project theme rather than exposing deck colour controls.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation 3, JUnit 4, Android instrumentation screenshots on the connected physical phone.

---

### Task 1: Extend the semantic colour roles

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppTheme.kt`
- Test: `Front/app/src/test/java/com/qiuzhao/flashcards/ui/AppColorSystemTest.kt`

1. Add Figma warning secondary and ink tokens from node `654:3950` while preserving the existing primary/strong API used by destructive actions.
2. Add a focused assertion for all four warning semantic values.

### Task 2: Build reusable deck-summary cards

**Files:**
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckSummaryComponents.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckScreen.kt`

1. Implement Figma `297:8521`'s 225dp learning-data card, 101dp type row, two 175dp metric cards, and 277dp weekly review card.
2. Preserve the 402dp geometry, 16dp outer rhythm, 32dp radii, typography, status colours, and separate rounded bar segments.
3. Remove the old synopsis/progress cards and inline delete control; keep the Figma bottom edit and review actions.

### Task 3: Enforce project-owned deck colour

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/CardListScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppViewModel.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/data/remote/RemoteFlashcards.kt`

1. Resolve deck and card-list themes with the current projects flow.
2. Change the card-group editor to rename-only; clearly state its project-derived theme and remove all selectable deck swatches.
3. Keep the backend contract name PATCH unchanged and never mutate `themeKey` through that editor.

### Task 4: Verify and physically review

**Files:**
- Modify only if checks expose a defect.

1. Run focused colour tests and `:app:assembleDebug`.
2. Install only on the connected physical phone.
3. Capture the frontend-test deck summary and its edit sheet. Compare it against Figma node `297:8521` for safe areas, 402dp geometry, colours, label wrapping, and edit/review actions.
4. Correct all observed differences before delivery.
