# 7초 충전 접근성 오버레이 설계

## Goal

충전 단자가 연결되면 최대 7초 동안 오프라인 `magic_circle.html` 애니메이션을 표시하고 원래 화면으로 돌아간다. 플랫폼이 허용하면 삼성 잠금 화면 위에도 표시한다.

## Target devices

- Samsung Galaxy S26 Ultra, Android 16
- Samsung Galaxy Tab S11 Ultra, Android 16
- Samsung Galaxy Note20 Ultra as a secondary test device

## Architecture

Replace the DreamService integration with one user-enabled `AccessibilityService`. The service registers a runtime receiver for power connection changes, checks the current battery state when Android binds it, and owns one full-screen `TYPE_ACCESSIBILITY_OVERLAY` containing the existing WebView.

`MainActivity` becomes a small setup and preview screen. It displays whether the accessibility service is enabled and opens Android accessibility settings. It continues to reuse `WebViews.magicCircle()` for the preview.

## Charging flow

1. The user installs and opens the app.
2. The app explains why accessibility access is required and opens accessibility settings.
3. Android binds `ChargingAccessibilityService` after the user enables it.
4. The service registers for `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED` and checks the sticky battery state.
5. 연결 시 전체 화면 접근성 오버레이를 한 번 표시한다.
6. `0~1.8초` 연결 감지, `1.8~3초` 충전 실행, `3~5.8초` 마법진, `5.8~7초` 완료 및 종료 효과를 재생한다.
7. 7초가 되면 충전 중이어도 오버레이와 WebView를 제거한다.
8. 7초 전에 분리되면 즉시 제거한다.
9. 같은 연결 상태의 중복 방송은 다시 재생하지 않고, 분리 후 재연결할 때만 다시 재생한다.

## Display behavior

- The overlay fills the active display and follows orientation changes through the system window manager.
- It keeps the display on while visible but does not use deprecated wake-up APIs.
- It does not dismiss or bypass the device lock.
- Samsung secure system screens may suppress overlays; this must be verified on physical devices.

## Offline and privacy requirements

- All HTML and JavaScript remain packaged under `app/src/main/assets`.
- WebView network loads remain blocked.
- No Internet permission, analytics, server, account, foreground service, or third-party dependency is added.
- The accessibility service does not inspect window content or perform accessibility actions.

## Files

- Replace `MagicCircleDreamService.java` with `ChargingAccessibilityService.java`.
- Add a small pure-Java charging state controller so connect/disconnect idempotency can be unit tested.
- Update `MainActivity.java` for setup status and accessibility settings.
- Update `AndroidManifest.xml` to register the accessibility service and remove DreamService metadata.
- Replace `res/xml/dream.xml` with accessibility-service metadata.
- Update `README.md` with installation and Samsung testing instructions.

## Error handling

- Failure to add an overlay is logged and leaves the phone on its existing screen.
- Overlay removal is safe if no overlay exists.
- Service interruption destroys the WebView and unregisters the receiver.
- If accessibility access is disabled, the setup screen reports that charging animation is inactive.

## Verification

- Unit tests cover first connect, repeated connect, disconnect, and reconnect decisions.
- Run Gradle unit tests, lint, and debug APK assembly.
- Inspect the APK manifest to confirm the DreamService is gone, accessibility service is present, and no Internet permission exists.
- Physical-device acceptance test: connect and disconnect a cable while unlocked and locked on each target device, rotate the tablet, test airplane mode, and test reconnect after reboot.

## Acceptance criteria

- Enabling the accessibility service once activates charging detection without reopening the app.
- Connecting power shows one local magic-circle animation for at most seven seconds.
- Finishing the animation or disconnecting power exposes the prior screen.
- The animation works in airplane mode.
- The debug APK installs on all three target devices.
