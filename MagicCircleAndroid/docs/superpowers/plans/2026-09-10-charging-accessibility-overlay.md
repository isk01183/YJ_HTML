# Charging Accessibility Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the charging DreamService with a user-enabled accessibility overlay that shows the existing offline magic-circle animation while power is connected and removes it on disconnect.

**Architecture:** `ChargingAccessibilityService` owns a runtime power receiver and one `TYPE_ACCESSIBILITY_OVERLAY`. A tiny pure-Java transition function decides whether to show, hide, or do nothing; the existing `WebViews.magicCircle()` remains the single renderer.

**Tech Stack:** Java 17, Android SDK 23-37, Android AccessibilityService, WindowManager, WebView, Gradle

**Spec:** `docs/superpowers/specs/2026-09-10-charging-accessibility-overlay-design.md`

## Global Constraints

- Target Samsung Galaxy S26 Ultra and Galaxy Tab S11 Ultra on Android 16; secondary test target is Galaxy Note20 Ultra.
- Keep HTML and JavaScript packaged under `app/src/main/assets` and block network loads.
- Add no Internet permission, analytics, server, account, foreground service, or third-party dependency.
- Do not dismiss or bypass the device lock.
- Use no deprecated forced-wake API.
- Keep at most one overlay visible and make repeated power broadcasts idempotent.

---

### Task 1: Charging transition decision

**Files:**
- Create: `selftest/com/yj/magiccircle/ChargingTransitionSelfTest.java`
- Create: `app/src/main/java/com/yj/magiccircle/ChargingTransition.java`

**Interfaces:**
- Consumes: two booleans: whether the overlay is visible and whether the device is charging.
- Produces: `ChargingTransition.Action next(boolean visible, boolean charging)` returning `SHOW`, `HIDE`, or `NONE`.

- [ ] **Step 1: Write the failing self-test**

```java
package com.yj.magiccircle;

public final class ChargingTransitionSelfTest {
    public static void main(String[] args) {
        check(ChargingTransition.Action.SHOW, false, true);
        check(ChargingTransition.Action.NONE, true, true);
        check(ChargingTransition.Action.HIDE, true, false);
        check(ChargingTransition.Action.NONE, false, false);
    }

    private static void check(ChargingTransition.Action expected, boolean visible, boolean charging) {
        ChargingTransition.Action actual = ChargingTransition.next(visible, charging);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
New-Item -ItemType Directory -Force app\build\selftest | Out-Null
javac -d app\build\selftest selftest\com\yj\magiccircle\ChargingTransitionSelfTest.java
```

Expected: compilation fails because `ChargingTransition` does not exist.

- [ ] **Step 3: Add the minimal transition function**

```java
package com.yj.magiccircle;

final class ChargingTransition {
    enum Action { SHOW, HIDE, NONE }

    private ChargingTransition() {}

    static Action next(boolean visible, boolean charging) {
        if (visible == charging) return Action.NONE;
        return charging ? Action.SHOW : Action.HIDE;
    }
}
```

- [ ] **Step 4: Run the self-test to verify GREEN**

Run:

```powershell
javac -d app\build\selftest app\src\main\java\com\yj\magiccircle\ChargingTransition.java selftest\com\yj\magiccircle\ChargingTransitionSelfTest.java
java -ea -cp app\build\selftest com.yj.magiccircle.ChargingTransitionSelfTest
```

Expected: exit code 0 with no output.

- [ ] **Step 5: Commit the transition and check**

```powershell
git add app/src/main/java/com/yj/magiccircle/ChargingTransition.java selftest/com/yj/magiccircle/ChargingTransitionSelfTest.java
git commit -m "test: define charging overlay transitions"
```

---

### Task 2: Accessibility charging overlay service

**Files:**
- Create: `app/src/main/java/com/yj/magiccircle/ChargingAccessibilityService.java`
- Delete: `app/src/main/java/com/yj/magiccircle/MagicCircleDreamService.java`
- Create: `app/src/main/res/xml/accessibility_service.xml`
- Delete: `app/src/main/res/xml/dream.xml`
- Create: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`, sticky `ACTION_BATTERY_CHANGED`, and `WebViews.magicCircle(Context)`.
- Produces: exactly one full-screen `TYPE_ACCESSIBILITY_OVERLAY` while charging and no overlay while disconnected.

- [ ] **Step 1: Register the new service and resources**

Use this manifest service declaration and remove the DreamService declaration:

```xml
<service
    android:name=".ChargingAccessibilityService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service" />
</service>
```

Create `accessibility_service.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="false" />
```

Create `strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">마법진 충전 애니메이션</string>
    <string name="accessibility_service_description">충전 연결 중에만 오프라인 마법진 애니메이션을 표시합니다.</string>
</resources>
```

- [ ] **Step 2: Implement the Android service glue**

Create `ChargingAccessibilityService` with these behaviors:

```java
package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;

public final class ChargingAccessibilityService extends AccessibilityService {
    private static final String TAG = "MagicCircleCharging";
    private WindowManager windows;
    private WebView overlay;
    private boolean receiverRegistered;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) applyCharging(true);
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) applyCharging(false);
        }
    };

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, filter, RECEIVER_EXPORTED);
            else registerReceiver(powerReceiver, filter);
            receiverRegistered = true;
        }
        applyCharging(isCharging());
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void applyCharging(boolean charging) {
        switch (ChargingTransition.next(overlay != null, charging)) {
            case SHOW -> showOverlay();
            case HIDE -> hideOverlay();
            case NONE -> { }
        }
    }

    private void showOverlay() {
        WebView view = WebViews.magicCircle(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windows.addView(view, params);
            overlay = view;
        } catch (RuntimeException error) {
            view.destroy();
            Log.e(TAG, "Unable to show charging overlay", error);
        }
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            windows.removeView(overlay);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to remove charging overlay", error);
        }
        overlay.destroy();
        overlay = null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        hideOverlay();
        if (receiverRegistered) unregisterReceiver(powerReceiver);
        receiverRegistered = false;
        super.onDestroy();
    }
}
```

- [ ] **Step 3: Delete DreamService files and compile**

Run:

```powershell
.\gradlew.bat compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Re-run the charging transition self-test**

Run:

```powershell
javac -d app\build\selftest app\src\main\java\com\yj\magiccircle\ChargingTransition.java selftest\com\yj\magiccircle\ChargingTransitionSelfTest.java
java -ea -cp app\build\selftest com.yj.magiccircle.ChargingTransitionSelfTest
```

Expected: exit code 0.

- [ ] **Step 5: Commit the service replacement**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/yj/magiccircle app/src/main/res
git commit -m "feat: show magic circle on charging overlay"
```

---

### Task 3: One-time setup screen

**Files:**
- Modify: `app/src/main/java/com/yj/magiccircle/MainActivity.java`

**Interfaces:**
- Consumes: Android's enabled accessibility-service list and `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- Produces: a preview, an enabled/disabled status label, Samsung restricted-setting guidance, and a button opening system settings.

- [ ] **Step 1: Replace Dream settings UI with accessibility setup UI**

Replace the activity with this setup and preview implementation:

```java
package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        root.addView(WebViews.magicCircle(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setPadding(24, 16, 24, 16);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button settings = new Button(this);
        settings.setText("접근성 서비스 설정 열기");
        settings.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        status.setText(isServiceEnabled()
                ? "충전 애니메이션: 켜짐"
                : "충전 애니메이션: 꺼짐\n목록이 비활성화되면 앱 정보 > 오른쪽 위 메뉴 > 제한된 설정 허용을 먼저 선택하세요.");
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (getPackageName().equals(service.packageName)
                    && ChargingAccessibilityService.class.getName().equals(service.name)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: Compile the setup screen**

Run:

```powershell
.\gradlew.bat compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit the setup screen**

```powershell
git add app/src/main/java/com/yj/magiccircle/MainActivity.java
git commit -m "feat: add charging animation setup screen"
```

---

### Task 4: Documentation and APK verification

**Files:**
- Modify: `README.md`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: the completed manifest, service, setup screen, and offline assets.
- Produces: installation instructions and a verified debug APK.

- [ ] **Step 1: Document Samsung sideload setup and physical tests**

Replace the README usage section with these exact instructions while retaining the Gradle build command:

```markdown
## 설치 및 최초 설정

1. `app-debug.apk`를 설치하고 앱을 한 번 실행합니다.
2. **접근성 서비스 설정 열기**를 누릅니다.
3. **마법진 충전 애니메이션**을 선택하고 허용합니다.
4. 사이드로드 보안 때문에 항목이 비활성화되면 앱 정보 화면의 오른쪽 위 메뉴에서 **제한된 설정 허용**을 먼저 선택합니다.

## 실기기 테스트

- 잠금 해제 상태에서 케이블 연결/해제
- 잠금 상태에서 케이블 연결/해제
- 비행기 모드에서 연결/해제
- Galaxy Tab 화면 회전 후 연결/해제
- 재부팅 후 접근성 서비스가 유지되는지 확인

Android 보안 화면이나 제조사 정책이 접근성 오버레이를 제한하는 경우 해당 화면 위에는 애니메이션이 표시되지 않을 수 있습니다.
```

- [ ] **Step 2: Run complete automated verification**

Run:

```powershell
javac -d app\build\selftest app\src\main\java\com\yj\magiccircle\ChargingTransition.java selftest\com\yj\magiccircle\ChargingTransitionSelfTest.java
java -ea -cp app\build\selftest com.yj.magiccircle.ChargingTransitionSelfTest
.\gradlew.bat test lintDebug assembleDebug
```

Expected: self-test exits 0, Gradle reports `BUILD SUCCESSFUL`, and the APK exists.

- [ ] **Step 3: Inspect the packaged manifest and assets**

Run the installed Android SDK `aapt2` and JDK `jar` tools:

```powershell
$apk = 'app\build\outputs\apk\debug\app-debug.apk'
$aapt2 = 'C:\Users\jtn28\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
& $aapt2 dump xmltree --file AndroidManifest.xml $apk
& 'C:\Program Files\Android\Android Studio\jbr\bin\jar.exe' tf $apk
```

Confirm from their output:

- `ChargingAccessibilityService` is declared.
- `MagicCircleDreamService` and `android.service.dreams.DreamService` are absent.
- `android.permission.INTERNET` is absent.
- `assets/magic_circle.html` and all three local JavaScript files are packaged.

- [ ] **Step 4: Review the final diff and commit documentation**

Run:

```powershell
git diff --check
git status --short
git add README.md
git commit -m "docs: explain charging animation setup"
```

- [ ] **Step 5: Report the hardware-test boundary**

State explicitly that compilation and APK inspection are local verification, while Samsung lock-screen overlay behavior requires the user's physical cable tests on the three named devices.
