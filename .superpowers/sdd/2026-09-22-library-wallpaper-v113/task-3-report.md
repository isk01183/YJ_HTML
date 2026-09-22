# Task 3 report — N01 numeric centering

## Implemented

- Added `SanctuaryLayout.centeredBaseline(ascent, descent, centerY)` using `centerY - (ascent + descent) / 2f`.
- Kept the measured number + `%` width as one centered horizontal group through the pure `centeredLeft` calculation.
- `ChargeStatusPanelRenderer` now obtains the number's actual glyph bounds with `Paint.getTextBounds`, centers those bounds on `CENTER_Y`, and shares that baseline with `%` without letting `%` change the height reference.
- Moved the core divider into the same battery-placement function. Its Y position follows the centered number bottom with an 8 px minimum clearance; the status begins 19 px below it. The existing 111 px height cap keeps both inside the 142 px core.
- Expanded the debug-only review route to 0/9/69/100/unknown, font scales 1.0/1.3/2.0, all three languages, and existing portrait/landscape/tablet formats. It overlays the actual number/% glyph bounds and the core center cross. No review overlay was added to main/release sources.
- Kept `CENTER=(432,718)` and `CORE_RADIUS=142` unchanged.

## TDD evidence

### RED

Command:

`./gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest'`

Result: expected failure, exit 1. `compileDebugUnitTestKotlin` reported unresolved references for `SanctuaryLayout.centeredBaseline` and `SanctuaryLayout.centeredLeft`; the tests preceded both production functions.

The failing tests assert the required exact baseline (`748f` for `-80f, 20f, 718f`) and equal left/right margins for the combined number/suffix/gap width.

### GREEN

Focused command:

`./gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest'`

Result: `BUILD SUCCESSFUL` (4 s), exit 0.

Full verification command:

`./gradlew.bat testDebugUnitTest lintDebug assembleDebug --offline`

Result: `BUILD SUCCESSFUL` (9 s), exit 0. Parsed JUnit XML: 22 tests, 0 failures, 0 errors, 0 skipped. Lint report: `No issues found.` Debug APK assembled successfully.

`git diff --check` reported no whitespace errors (only the repository's existing LF-to-CRLF checkout warnings).

`graphify update .` completed after the source changes: 769 nodes, 1410 edges, 50 communities. `graphify-out/` remains untracked and excluded from the commit as required.

## Files changed

- `MagicCircleAndroid/app/src/main/java/com/yj/magiccircle/SanctuaryLayout.kt`
- `MagicCircleAndroid/app/src/main/java/com/yj/magiccircle/ChargeStatusPanelRenderer.kt`
- `MagicCircleAndroid/app/src/test/java/com/yj/magiccircle/SanctuaryGeometryTest.kt`
- `MagicCircleAndroid/app/src/debug/java/com/yj/magiccircle/SanctuaryReviewActivity.kt`
- `.superpowers/sdd/2026-09-22-library-wallpaper-v113/task-3-report.md`

## Self-review

- Completeness: actual number bounds, shared suffix baseline, combined horizontal centering, divider/status clearance, required debug cases, and debug-only overlays are present.
- Scope: no dependency, renderer interface hierarchy, production debug API, core geometry change, device mutation, publishing, or main merge was added.
- Mutation check: changing the baseline midpoint formula or the group-left half-width formula fails the new pure geometry tests.
- Ordering/lifecycle: the dynamic divider remains behind text, is rebuilt only on `update`, and uses the existing cached `Picture` draw path.

## Remaining verification / concerns

- S22 installation and visual capture remain pending the already-requested user consent. Therefore no hardware glyph-bounds capture, Canvas-versus-AGSL comparison, or real-device portrait/landscape/tablet visual pass is claimed here.
- No AVD exists, and no emulator/device state was changed. The debug inspection route is ready for the later approved runtime pass.

## Review fix round 1

- Removed the duplicated battery sizing/placement implementation from `SanctuaryReviewActivity`. The debug overlay now reads the exact number and suffix `TextRun` instances created and drawn by `ChargeStatusPanelRenderer`, so its glyph rectangles cannot drift from production sizing or placement.
- Added the pure `SanctuaryLayout.BatteryPlacement` result and one `batteryPlacement` routine used for measured group X positions, the actual-glyph baseline, divider clearance, and status top.
- Replaced the algebra-only group test with a focused placement-result test covering measured number/suffix/gap positions, the required 748 baseline, 8 px glyph-to-divider clearance, 19 px divider-to-status placement, equal group margins, and containment inside the core.

### Fix RED

`./gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest'`

Expected failure, exit 1: `SanctuaryLayout.batteryPlacement` was unresolved before the shared result/routine existed.

### Fix GREEN and verification

- Focused: `./gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest'` — `BUILD SUCCESSFUL` (4 s), exit 0.
- Full: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug --offline` — `BUILD SUCCESSFUL` (6 s), exit 0.
- Parsed JUnit XML: 22 tests, 0 failures, 0 errors, 0 skipped. Lint: `No issues found.` Debug APK assembled.
- `git diff --check`: no whitespace errors; only existing LF-to-CRLF checkout warnings.
- `graphify update .`: completed after the fix with 772 nodes, 1417 edges, and 51 communities; generated graph files remain excluded from the commit.

### Fix self-review

- One measured production layout now drives both drawing and debug inspection; no factory, interface, dependency, or debug overlay was moved into the release source set.
- No device installation or other device mutation was performed. The hardware visual matrix remains the same explicit pending item above.

## Review fix round 2

- Replaced the `lateinit` battery layout with a nullable value and guarded both the production draw path and debug overlay. A temporarily absent layout now preserves the renderer's former safe empty-draw behavior instead of throwing `UninitializedPropertyAccessException`.
- Added `ChargeStatusPanelChecks`, an instrumentation regression that clears the layout, draws the renderer onto a real bitmap-backed Android `Canvas`, and requires the draw to return normally. The existing instrumentation runner invokes it after the storage checks.

### Verification

Command:

`./gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest' assembleDebug assembleDebugAndroidTest --offline`

Result: `BUILD SUCCESSFUL` (3 s), exit 0. The focused JVM geometry tests passed, and both the debug app APK and instrumentation APK compiled and assembled. `javap` confirmed the Kotlin backing field remains named `batteryLayout`, matching the regression's reflection target. `git diff --check` reported no whitespace errors beyond existing LF-to-CRLF checkout warnings.

The Android Canvas regression could not be executed without installing updated APKs; device mutation remains prohibited pending the existing user consent. Against the previous code, setting the backing field to null reaches the unguarded `lateinit` getter and throws; the new nullable draw guard is the minimal production change. No device result is claimed.

`graphify update .` completed after the source change with 775 nodes, 1420 edges, and 51 communities; generated graph files remain excluded from the commit.
