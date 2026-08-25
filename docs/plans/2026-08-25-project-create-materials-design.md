# Project Creation Materials Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reproduce Figma project-creation and text-material screens, with a draft project theme applied to all surfaces, system file selection, and editable/deletable swipe cards.

**Architecture:** Keep creation-only materials in Compose saveable state because the production project/material write API is intentionally unavailable. Reuse the existing five `DeckTheme` colour families and Navigation 3 graph; add a project-text draft route so the text editor returns a completed draft to the creation screen. Implement the Figma card geometries as private Compose components in `ProjectScreen.kt`, using the shared `MaterialSymbol` renderer only for glyphs that visibly match the Figma material icons.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation 3, Activity Result API, JUnit 4, Android instrumentation screenshots on the connected physical phone.

---

### Task 1: Add a typed draft-text navigation result

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/navigation/AppRoute.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Chrome.kt`

1. Add a project text-draft route with stable title/content fields.
2. Render a Figma 493:1386 editor that has its 402dp header, two fields, fade, and fixed completion action.
3. On completion, replace the editor with the creation screen and expose the draft as a new managed text card.

### Task 2: Rebuild the project creation page from Figma tokens

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectScreen.kt`

1. Reorder sections to Figma 588:1922: explanation, name, theme chooser, add-study-materials, then managed materials.
2. Apply the selected `DeckTheme` to every creation surface, header control, action card, input background, management panels, format pill, and completion button.
3. Retain Figma dimensions: 16dp page gutters, 16dp section rhythm, 20/24dp padding, 24/32dp corner radii, and 56/80/104dp card dimensions.

### Task 3: Implement material actions and cards

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectScreen.kt`
- Test: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/ProjectComponentsTest.kt`

1. Use `ActivityResultContracts.OpenDocument` with PDF, text/plain, and markdown MIME types for the Figma 602:2600 file action.
2. Resolve selected file display names from `OpenableColumns` and append a local draft file card to “管理已添加的学习资料”.
3. Build Figma 645:2699 file and 648:2819 text cards; right-swipe reveals delete, and text cards also reveal edit. Deleting removes only that draft; editing returns to the text editor.
4. Add focused Compose tests for theme selection accessibility and draft-card actions.

### Task 4: Verify, deploy, and visually compare

**Files:**
- Modify only if visual review exposes a defect.

1. Run focused instrumentation/unit tests and `:app:assembleDebug`.
2. Install only on the connected physical device, navigate to creation, select each theme, add a real document, and create/edit/delete a text card.
3. Capture screenshots and compare them to Figma nodes `588:1922`, `602:2600`, `602:2572`, `645:2699`, `648:2819`, and `493:1386` for safe areas, wrapping, geometry, colours, gestures, and clipping.
4. Correct every observed deviation before handoff.
