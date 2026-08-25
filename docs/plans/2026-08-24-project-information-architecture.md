# Project Information Architecture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current “学习/卡组” top-level information architecture with the Figma-defined “项目” flow while preserving real deck/card study behavior and adding material management.

**Architecture:** Keep the existing Navigation 3 multi-back-stack shell, but rename the Library root to Project and model a project as the parent of one or more decks. Add shared Compose primitives for the new Figma navigation, secondary header actions, segmented control, stat cards, and swipe actions; project/detail/material screens compose those primitives rather than duplicating visual rules. Server data remains authoritative: the project and material data contract must be implemented before a production build exposes write actions.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation 3, StateFlow/ViewModel, existing HTTP repository, Android document picker, JUnit 4 and Compose UI tests.

---

## Latest Figma baseline

Read on 2026-08-24 from file `AWBjEW3xdCFcKigwb56f7Y`:

- Bottom navigation: `568:2326`; order is **主页 / 项目 / 数据**, 370dp wide in the existing 402dp canvas system.
- Shared header: `209:2733`; it has first-level and second-level variants.
- Project root: `494:1447`; add-project and material-management entry actions.
- Project detail: card management `15:3030`, statistics `540:3778`, and segment-control variants `540:4273`.
- Add project `588:1922`; material management/import/text entry `531:3013`, `536:3395`, `493:1386`; deck overview `297:8521`.
- Typography baseline: `378:1764`, `378:1775`, `378:1805`, and `379:2014`. The shared MiSans/Google Sans Flex axes and role values remain valid; `378:1775` updates root-navigation Chinese labels to selected `630, 14/18, 0.6` and unselected `520, 14/18, 0.6`.

The Figma motion endpoint returned no exported keyframes for `568:2326` or `540:4273`. Implement only the user-specified interaction: selection indicator motion with the Figma “轻巧” intent and **500ms** duration, then visually approve it on device.

## Non-negotiable visual rules

- Preserve the 402dp design scale already used in the app and its edge-to-edge behavior.
- Global blue primary is `#489FFF`. On normal blue/neutral pages, second-level header action backgrounds use the new semantic token `#EBF4FF`.
- On a themed project/deck screen, the second-level header action uses that screen’s secondary theme surface (the same semantic token used by the Figma lower-left secondary action), rather than hard-coding blue.
- Project detail’s “数据统计 / 卡组管理” is two peer views, not a navigation stack; changing it retains the selected project and the selected range (`总览` / `今日`).
- Existing cards that advertise swipe-to-delete/edit retain the established swipe geometry and destructive confirmation behavior.

## Required product and backend decision gate

The current client only has flat `/decks`, `/decks/{id}/cards`, `/stats/dashboard`, and PDF-task routes. `DeckSummary` has no `projectId`, and the repository has no list/import/delete-material contract. Therefore the following must be agreed with the backend owner before production implementation:

1. Project API: list/create/update/delete project; project detail must return its decks and an explicit `project_id` for every deck.
2. Project statistics API: total and today values, reviewed count, mastery count/ratio, review-state distribution, and any type/category counts shown in Figma. Do not manufacture stats from unrelated global dashboard fields.
3. Material API: list/delete/import source material, material type/status/name, and its association to zero or more projects/decks. Define whether deleting a material is blocked when generated cards still reference it.
4. Migration rule for existing standalone decks: create a default “未归类项目”, put each existing deck into it, or leave it unassigned. This must be a product decision—not an app-only guess.

Until that contract is available, a separate **visual demo mode** may use local fixtures only; it must be labelled debug-only and must not change production API behavior.

### Confirmed domain decision (2026-08-24)

- The hierarchy is **Project → Deck → Card**. A project owns imported materials (PDF, Markdown, plain text, and future supported formats).
- A deck belongs to one project and selects explicit material scopes (file plus optional chapters/ranges). It never silently uses every project material.
- A generated card belongs only to its deck and may retain material/locator provenance; it does not duplicate `project_id`.
- Existing server decks are legacy standalone data. The client presents them in a per-device **未归类项目** until the server migration attaches them to a real project; IDs, cards, review state, and statistics are preserved.
- Frontend test mode uses the same project/material/deck/card shape as production, but is an in-memory visual fixture source. It makes no project or material network request.

## Task 0: Synchronize the four Figma typography baselines

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppTheme.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Components.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Chrome.kt`
- Modify: `Front/app/src/test/java/com/qiuzhao/flashcards/ui/TypographySystemTest.kt`
- Modify: `docs/design-system/qiuzhao-flashcards/FONT_LIBRARY.md`
- Modify: `docs/design-system/qiuzhao-flashcards/MASTER.md`

**Step 1: Add a failing typography regression test.** Assert Figma `378:1775` has selected `FigmaTextSpec(14, 18, .6, 630)` and unselected `FigmaTextSpec(14, 18, .6, 520)`.

**Step 2: Centralize the new navigation-label tokens.** Keep MiSans and Google Sans Flex axes from `378:1764`/`378:1805` unchanged; expose the Chinese-only navigation specs from `AppTypographyTokens` and a shared Compose `TextStyle` helper.

**Step 3: Replace the local bottom-navigation override.** `BottomNavItem` must use the shared helper, removing the obsolete selected Heavy 700 and 14/16 values.

**Step 4: Update the two design-system documents.** Record the Figma node IDs, exact selected/unselected values, and that no Latin navigation-label token was provided.

**Step 5: Verify the regression suite.**

Run: `cd Front; ./gradlew :app:testDebugUnitTest --tests com.qiuzhao.flashcards.ui.TypographySystemTest`

Expected: PASS.

## Task 1: Lock the domain contract and fixtures — completed 2026-08-24

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/data/remote/RemoteFlashcards.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppViewModel.kt`
- Create: `Front/app/src/test/java/com/qiuzhao/flashcards/data/ProjectContractTest.kt`

**Step 1: Write failing parser/fixture tests.** Cover a project containing two decks, an unassigned legacy deck, today-vs-total project metrics, and material states.

**Step 2: Add transport/domain models.** Add `ProjectSummary`, `ProjectDetail`, `ProjectStatistics`, `ProjectStatisticsRange`, `MaterialSummary`, explicit deck material scopes, and nullable `projectId` to `DeckSummary`. Parsers are compatibility-only until the server exposes the endpoints.

**Step 3: Add repository and ViewModel flows.** Mirror existing error handling and front-end fixture switching; add refresh/create/update/delete/import operations only for confirmed endpoints.

**Step 4: Run unit tests.**

Run: `cd Front; ./gradlew testDebugUnitTest --tests '*ProjectContractTest'`

Expected: PASS.

## Task 2: Build the shared Figma component layer — completed 2026-08-24

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Chrome.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Components.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckTheme.kt`
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectComponents.kt`
- Create: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/ProjectComponentsTest.kt`

**Step 1: Write Compose tests.** Assert the accessible labels, selected semantics, and content description for the bottom navigation, second-level header back/action buttons, and the two-option segment control.

**Step 2: Add semantic tokens.** Extend the existing `DeckTheme` rather than scattering `Color(...)`: `headerSecondaryAction` is `#EBF4FF` for azure/neutral and the relevant themed secondary surface for violet/mint/coral/amber.

**Step 3: Implement reusable components.** Extract `AppBottomNavigation`, `ScreenTopInformationBar` variant styling, `ProjectSectionSwitcher`, `ProjectMetricCard`, and the existing swipe action container into composables with state hoisted to callers.

**Step 4: Implement motion in one place.** Use `animateDpAsState`/`animateColorAsState` for the selection pill and 500ms `tween` spec. Keep tab content state stable; animate the indicator, not a re-created screen.

**Step 5: Run UI tests.**

Run: `cd Front; ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.qiuzhao.flashcards.ui.ProjectComponentsTest`

Expected: PASS on the emulator/device. The test APK compiles successfully; direct `am instrument` execution on the connected phone remains blocked in the device's test-runner process, so this step is additionally covered by manual on-device UI inspection until the runner issue is isolated.

## Task 3: Migrate root navigation from 学习 to 项目 — completed 2026-08-24

**Files:**
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/navigation/AppRoute.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/navigation/NavigationState.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/Chrome.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/LibraryScreen.kt` (rename/move to `ProjectScreen.kt` only when all imports are updated)
- Create: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/navigation/ProjectNavigationTest.kt`

**Step 1: Write navigation tests.** Verify the root order is Home → Project → Data; each root retains its own Navigation 3 back stack; back from a project subpage returns to Project.

**Step 2: Add typed routes.** Replace `Library` with `Project` and add routes for project detail, project creation, material management, material import, and text import. Use IDs in typed route arguments; do not pass models or mutable UI state through routes.

**Step 3: Update the root bar.** Replace label/icon “学习” with “项目”; retain the existing 402dp scaling and bottom safe-area treatment.

**Step 4: Wire entries in `FlashcardsApp`.** Each entry obtains state from `AppViewModel` and passes narrow callbacks, matching the existing Navigation 3 pattern.

**Step 5: Run navigation tests and a manual back-stack check.**

Run: `cd Front; ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.qiuzhao.flashcards.ui.navigation.ProjectNavigationTest`

Expected: PASS; root-tab switching does not discard scroll, tab, or detail state.

**Implemented outcome:** `AppRoute.Project` retains the former `Library` serial
name so an already-saved Navigation 3 state restores safely after update. The
legacy deck list is intentionally the temporary Project-root content until Task
4 replaces it with Figma `494:1447`. Unit tests cover root order and Project
stack retention; the connected-phone check confirmed Project → card-group
overview → system Back returns to Project. The navigation selection pill uses a
500ms GPU layer translation, avoiding a per-frame layout pass during root-tab
content switching.

## Task 4: Implement the Project root and project creation flow

**Files:**
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectScreen.kt`
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectCreateScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppViewModel.kt`
- Create: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/ProjectScreenTest.kt`

**Step 1: Write UI tests.** Cover empty and populated project root states, opening add project, form validation, save success/failure, and opening material management.

**Step 2: Implement the Figma root.** Use `494:1447`: title/entry actions, project cards, and existing swipe/delete affordance. A project card opens its detail route.

**Step 3: Implement add project.** Use `588:1922`, including its header, title/theme input, and selected-source summary. Scope the submission to confirmed backend fields only.

**Step 4: Verify IME/insets.** Use the existing `adjustResize` manifest setup and one consistent insets strategy; field containers must remain visible when the keyboard opens.

**Step 5: Run UI tests and compare at 402dp.**

## Task 5: Implement Project detail: statistics and card-group management

**Files:**
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectDetailScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/DeckScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppViewModel.kt`
- Create: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/ProjectDetailScreenTest.kt`

**Step 1: Write tests.** Verify default Statistics tab, switching to Deck Management, today/total selection, project identity retained after switching, and card-group item click opens its deck overview.

**Step 2: Implement the shared project header and switcher.** Match `540:4273`; the segment is in-screen state, saved with `rememberSaveable` keyed by project ID.

**Step 3: Implement statistics.** Match `540:3778`: blue overview card, “总览 / 今日” toggle, metric cards, and review-progress distribution. Render an explicit empty/unavailable state rather than fabricated values.

**Step 4: Implement card-group management.** Match `15:3030`: deck rows are children of the current project; reuse current deck editing/deletion calls only where the backend preserves project membership.

**Step 5: Rework `DeckScreen` as the card-group overview.** Match `297:8521`, reuse the same data-card layout as project statistics, and preserve links to card list/add/import/study.

**Step 6: Run UI and data-flow tests.**

## Task 6: Implement material management and import

**Files:**
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/MaterialManagementScreen.kt`
- Create: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/MaterialImportScreen.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/PdfMaker.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/ui/AppViewModel.kt`
- Modify: `Front/app/src/main/java/com/qiuzhao/flashcards/data/remote/RemoteFlashcards.kt`
- Create: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/MaterialImportTest.kt`

**Step 1: Write tests.** Cover empty/list states, swipe delete confirmation, file-picker launch/result, text-title/body validation, import failure, and return to materials list.

**Step 2: Implement materials list.** Match `531:3013`; expose app-wide imported materials, metadata/status, import action, and the established swipe-delete style.

**Step 3: Implement import choices.** Match `536:3395`; file choice launches `ActivityResultContracts.OpenDocument`, text choice opens `493:1386`.

**Step 4: Reuse PDF infrastructure deliberately.** Existing `PdfMaker` uploads and generates cards, while the new material flow manages sources. Share URI handling and error mapping, but do not treat a PDF as a deck until the server confirms the project/deck association.

**Step 5: Verify keyboard, permissions, and process recreation.** Retain URI access where required and keep draft text/save state recoverable.

## Task 7: Validate motion, visual parity, accessibility, and regression safety

**Files:**
- Modify as needed only in files above.
- Add: `Front/app/src/androidTest/java/com/qiuzhao/flashcards/ui/ProjectFlowRegressionTest.kt`

**Step 1: Build a visual test fixture.** One blue and one violet project, each with multiple decks; at least one material in every supported state.

**Step 2: Verify all flows manually.** Home → Project → detail tabs → deck overview → cards; Project → add; Project → materials → file/text import; root navigation and system back at every depth.

**Step 3: Verify accessibility.** Touch targets, screen-reader labels, selected states, destructive-action announcement/confirmation, contrast, and no reliance on color alone.

**Step 4: Verify 402dp layout and edge-to-edge behavior.** Inspect status and navigation bar overlap, list end padding, bottom actions, and keyboard behavior in light and dark themes.

**Step 5: Perform final build.**

Run: `cd Front; ./gradlew :app:assembleDebug :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

## Suggested delivery sequence

1. **Foundation PR:** Tasks 1–3 (contract, shared components/tokens, navigation rename).
2. **Core project PR:** Tasks 4–5 (project root/detail/deck overview).
3. **Material PR:** Task 6 (material list and import).
4. **Polish PR:** Task 7 (motion, Figma screenshot review, regression).

This order makes the new information architecture visible early, avoids temporary duplicate navigation, and prevents UI implementation from masking missing backend data.
