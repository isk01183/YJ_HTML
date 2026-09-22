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
