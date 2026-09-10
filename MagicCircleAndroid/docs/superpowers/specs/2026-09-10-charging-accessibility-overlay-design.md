# Charging Accessibility Overlay Design

## Goal

Show the existing offline `magic_circle.html` animation automatically while a charger is connected, including above the Samsung lock screen when the platform permits it, and remove it immediately when charging ends.

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
5. Connected or already charging: add one full-screen accessibility overlay containing the local animation.
6. Disconnected: remove the overlay and destroy its WebView, revealing the existing lock screen or foreground app.
7. Repeated broadcasts are idempotent: at most one overlay exists.

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
- Connecting power shows one local magic-circle animation.
- Disconnecting power removes it immediately and exposes the prior screen.
- The animation works in airplane mode.
- The debug APK installs on all three target devices.
