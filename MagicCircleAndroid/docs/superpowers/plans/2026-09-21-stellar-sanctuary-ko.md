# 별을 읽는 성역 v1.12 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: 승인한 실행 방식에 따라 superpowers:executing-plans 또는 superpowers:subagent-driven-development를 사용합니다. 체크 항목을 순서대로 실행합니다.

**Goal:** 첨부 이미지의 구조와 빛을 코드로 그리는 N01 충전 화면을 기존 앱에 연결하고, 실제 테스트와 GitHub Actions APK를 제공합니다.

**Architecture:** Java 기반 충전 감지·보관함은 보존합니다. N01만 Kotlin Custom View로 표시하며, Canvas 기하·룬과 AGSL/Canvas 우주 배경을 분리합니다. 기존 도안 및 업로드 파일의 WebView 경로는 유지합니다.

**Tech Stack:** Android Custom View/Canvas/Path/RuntimeShader, Kotlin(AGP 내장), 기존 AGP9.3.0/Gradle9.5.0/Java17, JUnit4 JVM 테스트, GitHub Actions.

**Spec:** `../specs/2026-09-21-stellar-sanctuary-design-ko.md`

## 공통 제약

- minSdk23, compileSdk/targetSdk37 유지. versionCode15/versionName1.12를 사용하되 배포 직전 기존 최댓값보다 높은지 확인합니다.
- PNG/JPG/WebP를 새 배경·은하·마법진으로 사용하지 않습니다. 참고 이미지와 검사 캡처는 앱 자산에 넣지 않습니다.
- BlurMaskFilter, software layer, 실험 API, 신규 네트워크 권한을 사용하지 않습니다.
- 도안 식별자는 `native-N01`, 화면 코드는 `N01`, 제목은 `별을 읽는 성역`입니다.
- 기존118종/55종, 업로드, 언어, 선택값, 숨김·복원·명시적 비활성 상태를 보존합니다.
- 최대7초·분리 즉시 종료·재연결 초기화를 유지합니다. 잠금 해제나 삼성 잠금화면 자체 교체는 하지 않습니다.
- 기하·궤도·코어는 고정. 금색48개 룬은180초/회전, 청색54개 룬은240초/역회전입니다.
- 원본 이미지의 품질과 동일하다는 판정은 빌드 통과와 분리합니다. 정량 근거 없는 일치율을 쓰지 않습니다.
- main 병합/사용자 파일 삭제/추가 종량제 비용/일반 설정의 비밀정보 기록은 하지 않습니다.

## 검토 중점

1. API23 또는 AGSL 초기화 실패에서도 네이티브 화면이 즉시 종료되지 않고 Canvas로 나타나야 합니다(작업3·5).
2. 잠긴 화면·반복 재연결·도중 분리에서 이전 콜백이 새 화면을 닫지 않아야 합니다(작업4).
3. 업데이트 설치 시 저장된 업로드 선택, 숨김 목록, 빈 선택값을 새 기본 도안으로 덮어쓰지 않아야 합니다(작업4).
4. 세 언어·100%·태블릿 가로/세로·글자 확대·카메라 구멍에서 원이 늘어나거나 글자가 잘리지 않아야 합니다(작업2·4·5).
5. GitHub APK의 서명·소스·버전과 원격 빌드 결과가 명확해야 합니다. 로컬 서명키를 공개하거나 기존 앱을 삭제해서 설치 오류를 숨기지 않습니다(작업6).

## 확인한 Android Studio 상태

2026-09-21 창을 실제로 확인했습니다. `MagicCircleCharging` 안에 `android-v1.11-work`가 열려 있고 `direct-circles.js`, `direct-extra.js`와 기존 Java 서비스가 보입니다. 실행 대상은 `samsung SM-S901N`입니다. IDE가 권하는 AGP 업그레이드는 이번 변경에 필요하지 않아 누르지 않았습니다.

## 작업 폴더와 파일 계약

현재 기준 소스는 `android-v1.11-work`입니다. 실제 원격 저장소의 Android 모듈 경로는 `MagicCircleAndroid`입니다. 이하 앱 상대 경로는 두 위치에서 동일합니다. 새 Kotlin 파일은 `app/src/main/java/com/yj/magiccircle/`에 두며 package는 `com.yj.magiccircle`입니다. 실수로 첨부 코드의 `com.example.chargingapp`을 사용하지 않습니다.

| 파일 | 책임·공개 접점 |
|---|---|
| `ChargeSnapshot.kt` | 배터리 순수 변환. `ChargeSnapshot.fromRaw(level:Int?, scale:Int?, temperatureTenths:Int?, health:Int?, status:Int?, plugged:Int?):ChargeSnapshot` |
| `SanctuaryLayout.kt` | `Frame(scale:Float,left:Float,top:Float)`, `fit(width:Int,height:Int):Frame`, `goldAngle(elapsed:Long):Float`, `blueAngle(elapsed:Long):Float` |
| `MagicCircleRenderer.kt` | 생성 시 도형 캐시. `draw(canvas:Canvas,goldAngle:Float,blueAngle:Float)`; 코어 안의 동적 글자는 패널이 그립니다. |
| `CosmicBackgroundRenderer.kt` | `prepare(width:Int,height:Int)`, `draw(canvas:Canvas,elapsed:Long,forceCanvas:Boolean)`; 신규 API는 내부 API33 경로로 격리합니다. |
| `ChargeStatusPanelRenderer.kt` | `update(snapshot:ChargeSnapshot,language:String,fontScale:Float)`, `draw(canvas:Canvas)`, `description():String`; 글자 측정·문자열 생성은 update에 한정합니다. |
| `MainMagicChargeView.kt` | `MainMagicChargeView(context:Context)`, `start()`, `stop()`; 배터리 수신·화면 수명주기와 렌더러 조합. Java에서 바로 호출합니다. |
| 기존 `MainActivity.java` | N01 미리보기에는 MainMagicChargeView, 나머지는 기존 WebView |
| 기존 `ChargingAccessibilityService.java` | N01 네이티브 오버레이와 기존 WebView 정리/재시작 경로 분기 |
| 기존 `ThemeSelection.java`, `MediaLibrary.java` | N01 허용, 새 설치 기본 선택, 업데이트 선택 보존 |
| 기존 `gallery.js`, `i18n.js` | N01 메타데이터·검색·네이티브 미리보기 안내. 기존118종 렌더러 변경 없음 |
| `.github/workflows/android-debug.yml` | 저장소 루트의 무료 조건부 수동 CI 빌드·검사·APK artifact |

## 작업1 — 최신 소스를 보존하고 빌드 기반 확보

**파일:** 원격 작업 트리의 `MagicCircleAndroid/`, 현재 `android-v1.11-work/`, `app/build.gradle.kts`, 본 설계·계획 문서.

- [ ] `using-git-worktrees` 지침으로 기존 저장소의 격리 작업 위치를 마련합니다. 원본 작업 트리의 미추적 파일과 main은 건드리지 않습니다. 원격 추적 정보가 오래됐으면 fetch만 하고 자동 병합하지 않습니다.

```powershell
git -C 'C:/Claude_Workspace/GitHub/YJ_HTML/.worktrees/charging-overlay' status --short --branch
git -C 'C:/Claude_Workspace/GitHub/YJ_HTML/.worktrees/charging-overlay' log -4 --oneline
git -C 'C:/Claude_Workspace/GitHub/YJ_HTML/.worktrees/charging-overlay' diff --name-only
```

- [ ] v1.11 대비 파일 해시와 diff를 비교합니다. `app/src`, Gradle 설정, selftest 중 변경된 제품 관련 파일만 검토해 격리 저장소에 반영합니다. `build`, `.gradle`, `.idea`, `local.properties`, 첨부파일, 자격증명, 기존 APK를 통째로 복사/추적하지 않습니다. 동일 파일은 복사하지 않습니다.
- [ ] 변경 전 검사를 실행하고 실패가 있으면 이름을 기록합니다. 이전 NO-SOURCE 결과는 구분합니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
node selftest/direct-extra.test.cjs
node selftest/collection.test.cjs
node selftest/languages.test.js
```

- [ ] AGP9 내장 Kotlin을 사용합니다. 별도 `org.jetbrains.kotlin.android` 플러그인을 덧붙이지 않습니다. 기존 Java17 타깃을 그대로 사용합니다. JVM 테스트용 의존성만 앱 모듈에 추가합니다.

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] `git diff --check`와 소스 비교 후 v1.11 보존분/설계·계획을 별도 커밋합니다. 아직 push와 Actions 실행은 하지 않습니다.

## 작업2 — 배치·배터리·회전의 단위 테스트부터 작성

**파일:** `ChargeSnapshot.kt`, `SanctuaryLayout.kt`, `app/src/test/java/com/yj/magiccircle/SanctuaryModelTest.kt`.

**배터리 출력 계약:** `percent:Int?`, `temperatureC:Float?`, `health:Int?`, `status:Int?`, `plugged:Int?`. 실제 표시 문구는 패널에서 지역화합니다. Android 숫자 상수2/3/4/5를 건강과 충전 상태 사이에 혼용하지 않습니다.

- [ ] 다음 실제 경계값 테스트를 먼저 작성합니다. 클래스 부재로 처음 컴파일되지 않으면 최소 선언을 만든 뒤 핵심 수치 검사가 실제로 실패하는 것을 확인하고 계산을 구현합니다.

```kotlin
@Test fun measuredDataDoesNotInventSampleValues() {
    val absent = ChargeSnapshot.fromRaw(null, null, null, null, null, null)
    assertNull(absent.percent)
    assertNull(absent.temperatureC)
    assertNull(ChargeSnapshot.fromRaw(4, 0, null, null, null, null).percent)
    assertNull(ChargeSnapshot.fromRaw(101, 100, null, null, null, null).percent)
    assertEquals(69, ChargeSnapshot.fromRaw(69, 100, 325, 2, 3, 0).percent)
    assertEquals(0f, ChargeSnapshot.fromRaw(0, 100, 0, 2, 3, 0).temperatureC!!, 0f)
}
@Test fun circleUsesReferenceProportionsAndUniformScale() {
    val f = SanctuaryLayout.fit(864, 1536)
    assertEquals(1f, f.scale, 0.0001f)
    assertEquals(0f, f.left, 0.0001f)
    val landscape = SanctuaryLayout.fit(1600, 900)
    assertEquals(900f / 1536f, landscape.scale, 0.0001f)
    assertTrue(landscape.left >= 0f)
    assertEquals(0f, SanctuaryLayout.fit(0, 0).scale, 0f)
}
@Test fun onlyRuneAnglesAdvanceAndRestartAtZero() {
    assertEquals(2f, SanctuaryLayout.goldAngle(1000), 0.0001f)
    assertEquals(-1.5f, SanctuaryLayout.blueAngle(1000), 0.0001f)
    assertEquals(0f, SanctuaryLayout.goldAngle(180000), 0.0001f)
    assertEquals(0f, SanctuaryLayout.goldAngle(0), 0f)
}
```

- [ ] `./gradlew testDebugUnitTest --tests '*SanctuaryModelTest'`로 실패를 확인합니다.
- [ ] 순수 계산을 구현합니다. 디자인 좌표는864×1536, 원 중심432/718, 외곽 반지름402, 코어 반지름142를 초기값으로 고정합니다. 화면 inset은 View가 제외한 후 fit에 전달합니다.

```kotlin
fun fit(width: Int, height: Int): Frame {
    val s = minOf(width.coerceAtLeast(0) / 864f, height.coerceAtLeast(0) / 1536f)
    return Frame(s, (width - 864f * s) / 2f, (height - 1536f * s) / 2f)
}
fun goldAngle(elapsed: Long) = (elapsed.coerceAtLeast(0) % 180000L) * (360f / 180000f)
fun blueAngle(elapsed: Long) = -(elapsed.coerceAtLeast(0) % 240000L) * (360f / 240000f)
```

- [ ] `fromRaw`는 level과 scale의 누락·범위를 먼저 검사하고 나누기 전에 Float로 변환합니다. 온도가 null이면 알 수 없음, 0이면 0.0°C입니다. health/status/plugged는 서로 다른 필드에 보관합니다. 추가 테스트에 음수, 잘못된 scale, 100%, Int.MAX_VALUE, 알 수 없는 상태 값을 포함합니다.
- [ ] 전체 `testDebugUnitTest`를 다시 실행하고 통과한 순수 모델과 테스트를 커밋합니다.

## 작업3 — 원본의 도형·은하·빛을 직접 그리기

아래 항목은 각각 확인할 그리기 단계입니다. 전체를 단순한 점 배열로 대신하지 않습니다.

**파일:** `MagicCircleRenderer.kt`, `CosmicBackgroundRenderer.kt`, `ChargeStatusPanelRenderer.kt`, `app/src/test/java/com/yj/magiccircle/SanctuaryGeometryTest.kt`.

**계약:** 각 렌더러는 위 파일 계약을 따릅니다. 고정864×1536 좌표의 경로와 glyph Path 배열을 한 번 만들고 View가 화면 크기에 맞게 Canvas를 변환합니다.

- [ ] 실제 렌더러가 소비할 좌표 배열에 대한 테스트를 먼저 추가합니다. 골드48/블루54, 궤도4/노드8, 중심의 동일 좌표, 별 경로의 마지막-첫 접점 일치를 검사합니다. 검사용 가짜 개수 상수를 따로 반환하지 않습니다. `SanctuaryLayout`에 일반 FloatArray 생성 함수 `polygon(radius:Float, vertices:Int, step:Int, phase:Float):FloatArray`를 두고 그 결과를 drawPath에 그대로 사용합니다.

```kotlin
@Test fun closedTriangleReturnsToItsActualStart() {
    val points = SanctuaryLayout.polygon(300f, 3, 1, -90f)
    assertEquals(8, points.size)
    assertEquals(points[0], points[points.size - 2], 0f)
    assertEquals(points[1], points[points.size - 1], 0f)
}
```

- [ ] 실패 후 polygon을 `vertices+1`개의 점으로 구현하고 마지막 점은 첫 두 Float를 그대로 복사합니다. 육망성은 동일 반지름의 두 닫힌 삼각형, 팔망성은 두 닫힌 사각형으로 만듭니다. 궤도는 하나의 타원 RectF를0/45/90/135도로 회전해4개 경로로 캐시합니다. 노드는 각 타원의 실제 끝점에서 구합니다.
- [ ] 금빛 링402/396/388과 내부 청빛 링302/320 주변에 원본과 같은 여백을 둡니다. 외곽 룬 반지름365, 안쪽 룬 반지름281을 초기값으로 사용하고 비교 캡처에서 조정합니다. 글꼴 대신 룬24개 이상을 선분·곡선 Path로 만들고 각 링 둘레의 위치·접선 방향으로 미리 변환합니다. 초승달은 어두운 원으로 덮어 지우지 말고 닫힌 초승달 Path로 그립니다.
- [ ] 기본 광원은 캐시한 Paint와 여러 폭의 스트로크, 별의 가는 중심 Path, RadialGradient로 표현합니다. 다음처럼 넓고 약한 빛에서 좁고 밝은 선으로 중첩합니다. Paint/색 배열은 생성자 필드에 둡니다.

```kotlin
for (i in glowWidths.indices) {
    ringPaint.strokeWidth = glowWidths[i]
    ringPaint.alpha = glowAlphas[i]
    canvas.drawPath(cachedRingPath, ringPaint)
}
```

- [ ] Canvas 은하를 먼저 완성합니다. 고정seed의4~5개 나선 팔을 연속 cubic/line Path로 구성하고, 팔 안쪽의 따뜻한 성운·바깥쪽 청색 성운·어두운 먼지 띠를 별도 경로로 겹칩니다. 경로 위에 밀도 차이가 있는 별무리를 캐시합니다. 모서리4개는 중심(118,162)/(768,231)/(104,1275)/(786,1300), 반지름180~240, 기울기·가림 정도를 각각 달리합니다. 중심은 여러 RadialGradient와 밝은 작은 별로 표현합니다.
- [ ] 지원 기기에서는 배경 성운·나선 질감을 AGSL로 보강합니다. 반지름r과 각도a의 `sin(a * 4 - log(r + 0.025) * 8)` 나선 마스크에 여러 크기의 value-noise를 곱해 팔·암흑 띠를 분리하고, 중앙 밝기와 파란 외곽 색을 혼합합니다. 반복 횟수를 고정하고 매 프레임 새 Random/Path/Shader를 만들지 않습니다. RuntimeShader 생성은 API33 내부 클래스로 격리하고 실패 시 미리 준비한 Canvas 배경으로 전환합니다.
- [ ] 코어는 어두운 원 내부에 별먼지·청색 성운, 경계에 cyan 광륜과 얇은 금색 링을 그립니다. 원본의 숫자69/32.5°C를 production 기본값으로 쓰지 않습니다.
- [ ] 패널은 제목/부제/하단 문구의 간격을 측정하고 `%`를 숫자 오른쪽에 실제 폭을 계산해 배치합니다. 100%, 상태 문자열3언어, fontScale1/1.3/2에서 영역 폭보다 커지면 글자 크기를 제한하거나 정보 블록을 두 줄로 배치합니다. TalkBack용 전체 상태 설명을 갱신합니다.
- [ ] geometry 단위 테스트, `testDebugUnitTest`, lint를 통과시킨 뒤 커밋합니다. 코드상 요소 개수 검사는 시각 품질 통과를 의미하지 않으므로 작업5에서 이미지 비교를 별도로 수행합니다.

## 작업4 — 같은 네이티브 화면을 미리보기와 충전에 연결

**파일:** `MainMagicChargeView.kt`, 기존 `MainActivity.java`, `ChargingAccessibilityService.java`, `ChargingTransition.java`, `ThemeSelection.java`, `MediaLibrary.java`, `gallery.js`, `i18n.js`, `app/src/test/java/com/yj/magiccircle/SanctuaryIntegrationTest.java`.

- [ ] 먼저 N01 허용·기존선택·명시적 빈선택과 네이티브 준비 이벤트의 테스트를 작성합니다. 아래 함수를 ThemeSelection에 추가할 계약으로 정하고 먼저 실패를 확인합니다.

```java
@Test public void installDefaultNeverOverridesExistingChoice() {
    assertEquals("native-N01", ThemeSelection.initialSelection(false, null));
    assertEquals("moon", ThemeSelection.initialSelection(true, "moon"));
    assertEquals("", ThemeSelection.nextVisible("", ThemeSelection.IDS, java.util.Collections.emptySet()));
    assertTrue(ThemeSelection.isValid("native-N01"));
    assertEquals(174, new java.util.HashSet<>(ThemeSelection.IDS).size());
}
@Test public void repeatedNativeRunsDoNotRemainComplete() {
    ChargingTransition.State s = ChargingTransition.State.COMPLETE;
    for (int i = 0; i < 10; i++) {
        s = ChargingTransition.next(s, ChargingTransition.Event.CONNECT);
        s = ChargingTransition.next(s, ChargingTransition.Event.NATIVE_READY);
        assertEquals(ChargingTransition.State.PLAYING, s);
        s = ChargingTransition.next(s, ChargingTransition.Event.DISCONNECT);
        assertEquals(ChargingTransition.State.DISCONNECTED, s);
    }
}
```

- [ ] IDS에 native-N01을 추가하고 기존 173개의 상대 순서는 유지합니다. `initialSelection`은 레거시 선택 키가 없으면 native-N01, 있으면 기존 normalize 결과를 반환합니다. MediaLibrary는 초기값 설정 후 기존 manifest를 우선해서 읽습니다. 손상된 manifest가 발견되면 데이터를 덮어쓰지 않는 보호를 유지합니다.
- [ ] `NATIVE_READY`는 LOADING에서 PLAYING으로 전환합니다. 연결·분리·기한 종료는 기존 전환을 유지하고 네이티브 화면에 가짜 PAGE_READY/JS_STARTED 이벤트를 보내지 않습니다.
- [ ] `MainMagicChargeView.start()`는 elapsedRealtime 기준 시작 시간을 초기화하고 배터리 sticky Intent로 상태를 갱신합니다. 보이는 동안에만 프레임을 예약합니다. `stop()`은 중복 호출에 안전하게 만들고 callback과 receiver를 해제합니다. attach/detach·visibility 변화에서도 반복 작업이 남지 않도록 합니다. 시스템 애니메이션 배율이 0이면 정적 화면으로 표시합니다.
- [ ] `onDraw` 순서는 전체 배경, 안전 영역을 제외한 설계 좌표의 균일 변환, 고정 마법진, 회전 룬 두 층, 코어 글자·패널입니다. 각 renderer는 한 번만 생성하며 시계로 구하는 회전각 외의 기하 데이터를 매 프레임 다시 만들지 않습니다.
- [ ] `MainActivity.showPreview` 시작에서 native-N01을 판별해 같은 Dialog/닫기 버튼/7초 종료와 MainMagicChargeView를 사용합니다. 다른 도안은 기존 메서드로 처리합니다. dismiss 시에는 반드시 start의 예약·등록을 해제합니다.
- [ ] 서비스의 overlay 타입을 View로 바꾸고 WebView 전용 start/load/destroy는 `instanceof WebView`로 분기합니다. N01은 새 View를 addView한 뒤 start하고 NATIVE_READY로 전환합니다. addView 성공 뒤 예외가 발생해도 removeView하도록 정리 경로를 통일합니다. 이전 runId/View의 callback은 무시합니다.
- [ ] gallery 맨 앞에 N01 정보를 표시합니다. N01 카드에는 작은 코드 그리기 아이콘과 ‘네이티브 화면은 앱의 미리보기에서 확인’ 안내를 각 언어로 제공합니다. 별도의 웹 구현을 완성 화면이라고 보여주지 않습니다. 선택/미리보기 링크는 기존 magiccircle 스키마를 사용합니다. 사용자 문서는 한국어로 유지합니다.
- [ ] 자체 테스트의 개수는 173에서 174로 바꾸면서 ‘이전 173 ID가 모두 포함됨’의 집합 비교를 남깁니다. 숫자만 늘리고 기존 검사를 지우지 않습니다. 기존 55종, 118 SVG, 업로드 자료의 미리보기 경로를 각각 확인합니다.
- [ ] 전체 JVM 테스트와 기존 selftest를 실행하고 커밋합니다.

## 작업5 — Android Studio와 기기에서 원본 비교·안정성 검증

**파일:** `selftest/sanctuary-device-check.ps1`, `docs/V1_12_REVIEW_KO.md`, 검사용 이미지/로그는 `evidence/v112/`(앱 미포함).

- [ ] 버전을1.12/code15 이상으로 증가시키고 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

- [ ] Android Studio에서는 새 제품 파일과 실제 작업 경로가 일치하는지 확인합니다. 프로젝트를 옮겨 열어야 할 경우 열린 파일의 미저장 변경을 먼저 확인합니다. AGP 업그레이드·자동 리팩터링을 누르지 않습니다.
- [ ] `adb devices -l`로 기기를 다시 선택합니다. 설치된 앱 버전과 인증서가 맞을 때만 `adb -s <확인한 기기> install -r <빌드 APK>`로 데이터를 보존하여 설치합니다. 서명 오류가 나면 삭제하지 않고 중단합니다.
- [ ] 실제 앱에서 N01 선택→미리보기와 충전 오버레이를 캡처합니다. 비교용69% 등 고정값은 debug 전용 검사 Activity/테스트 경로에만 넣고 release/main 입력 경로에 테스트용 intent를 열어 두지 않습니다. 강제 Canvas 선택도 debug 전용 경로에 둡니다. 구현 시 파일은 `app/src/debug/java/com/yj/magiccircle/SanctuaryReviewActivity.kt`와 `app/src/debug/AndroidManifest.xml`입니다.
- [ ] 디버그 검사 Activity는 외부 노출을 최소화하여 exported=false로 두고 앱 내부 debug 전용 진입으로 열도록 합니다. 실제 사용자 화면·서비스 데이터에는 고정값을 주입하지 않습니다.
- [ ] 864×1536 기준으로 실제 렌더 캡처와 원본을 나란히 두고 제목/원/코어/하단 위치를 확인합니다. 선 접점, 팔망성 고정, 룬 숫자, 두 링 반대 회전, 청백/금빛의 분리, 네 은하의 질감·밀도를 확대 점검합니다. 첫 그림이 선명하지 않으면 기능 완료로 포장하지 말고 renderer로 돌아가 조정합니다.
- [ ] `sanctuary-device-check.ps1`는 기기1개 확인, 패키지versionName확인, process PID와 crash log 검사, gfxinfo/meminfo 수집만 기본 수행합니다. 잠금 해제·접근성 설정 자동 승인·battery service 모의 조작을 하지 않습니다. 케이블10회 재연결은 사용자와 협력하여 실제 수행하고 로그의run별 attach/dismiss를 대조합니다. 모의 테스트를 실제 케이블 검사로 표시하지 않습니다.
- [ ] API23 장치/에뮬레이터가 있으면 Canvas 화면과 최소SDK 실행을 확인하고, API33 이상에서는 AGSL/강제Canvas를 각각 확인합니다. 사용할 수 없는 기종은 미검증으로 보고합니다. 현재 S22 결과와 S26/Tab S11 결과를 섞지 않습니다.
- [ ] 100%/0%/알 수 없음/온도0도, 세 언어, 태블릿 가로와 세로, 글자 배율 확대, 화면꺼짐/켜짐/중간 분리를 검사합니다. 참조 도안118종과업로드GIF 재생도 회귀 검증합니다.
- [ ] 실제 측정 장치/해상도/renderer/첫 프레임 시간/프레임 지연/메모리/남은 시각 차이를 한국어 결과표에 기록합니다. 검사 스크립트와 문서만 커밋하고 개인 기기 ID·로그 전체를 공개하지 않습니다.

## 작업6 — GitHub 빌드와 APK 제공

**파일:** 저장소루트 `.github/workflows/android-debug.yml`, `MagicCircleAndroid/docs/V1_12_REVIEW_KO.md`, 필요한 `gradle/wrapper/gradle-wrapper.properties` checksum.

- [ ] 배포할 diff에 자격증명·local.properties·기기로그·서명키·원본 첨부파일이 없는지 검사합니다. `git diff --check`를 통과시킵니다.
- [ ] GitHub 저장소가 공개이고 표준 GitHub-hosted Linux runner의 무료 조건을 충족하는지 공식 정보와 저장소 설정으로 확인합니다. 유료 실행 가능성이 있으면 workflow 실행을 멈추고 사용자에게 알립니다. 유료 runner/새 API키/별도 결제는 생성하지 않습니다.
- [ ] workflow는 `workflow_dispatch`로만 시작하도록 하여 push만으로 비용 가능 작업이 실행되지 않게 합니다. 권한은 `contents: read`, 실행 시간 상한은 20분, 동시 실행은 브랜치당 1개입니다. Actions 공식 저장소에서 확인한 태그를 커밋 SHA로 고정합니다. 공식 checkout/setup-java/setup-gradle/upload-artifact를 사용하고 Wrapper 검증을 비활성화하지 않습니다.
- [ ] 작업 디렉터리는 `MagicCircleAndroid`, JDK는 17로 지정하고 sdkmanager로 `platforms;android-37`을 설치합니다. 빌드 핵심 명령은 다음과 같습니다.

```sh
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

- [ ] APK 업로드는 위 명령 성공 시에만 하고, 파일 누락 시 workflow를 실패시킵니다. 이름은 `MagicCircleCharging-v1.12-debug`, 경로는 `MagicCircleAndroid/app/build/outputs/apk/debug/app-debug.apk`, `retention-days: 1`로 설정합니다. 테스트·lint 보고서는 별도의 artifact로 짧게 보관합니다. 무료 조건 확인 없이 자동 연장/유료 저장 공간을 켜지 않습니다.
- [ ] 기존 작업 브랜치의 원격 변경을 확인하고 fast-forward 가능한 정상 push만 합니다. 강제 push/main 병합은 하지 않습니다. Actions를 수동 실행하고 결과가 success인지 확인합니다. 실패하면 실제 로그 원인을 확인하고 수정 커밋 후 재실행합니다. 비용 조건이 바뀌면 멈춥니다.
- [ ] 완료한 커밋 링크, 실행 링크, APK artifact 링크, 버전과 SHA-256, 로컬/CI 서명 차이를 기록합니다. CI의 임시 debug 서명은 기존 설치 업데이트에 쓸 수 없을 수 있으므로 로컬 동일 서명 APK와 혼동하지 않습니다. 기존 서명키를 repository에 넣거나 앱 삭제를 강요하지 않습니다.
- [ ] 마지막으로 `graphify update .`를 AST-only로 실행하고 최신 변경과 결과를 요약합니다. 생성 그래프 전체를 무조건 커밋하지 않습니다.

## 자체 검토

- 배경·타이포·외곽·내부 룬·기하·궤도·코어·하단: 작업2/3/5에서 구현과 실제 화면을 나눠 검증합니다.
- 애니메이션·7초·잠금·재연결·오프라인: 작업4/5에서 검증합니다.
- Kotlin·버전·실제unit test·Actions·APK·Git: 작업1/2/5/6입니다.
- 신규 설치와 업데이트 선택 보존, 기기 제약, 비용·서명 문제는 별도 검토 기준으로 남깁니다.
- 현재는 계획 문서이며 본 계획의 체크 표시가 없는 항목은 실행 완료가 아닙니다.

## 실행 방식 승인

추천: **직접 구현**. 렌더러·기기 수명주기·그림 비교를 한 세션에서 이어가고 끝에 독립 검토를 받는 방식입니다. 작업별 에이전트 분할보다 반복 컨텍스트 비용이 적고 화면 보정의 연속성을 유지하기 쉽습니다.

대안: **단계별 에이전트 구현·검토**. 작업별 별도 구현자와 검토자를 사용해 독립 검토 횟수는 늘지만 사용량도 더 듭니다.

계획을 확인하고 실행 방식을 선택한 뒤 제품 코드 변경을 시작합니다.

공식참고: [AGP 내장 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [Custom View 최적화](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view), [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl), [Gradle Actions](https://github.com/gradle/actions), [APK artifact](https://github.com/actions/upload-artifact).
