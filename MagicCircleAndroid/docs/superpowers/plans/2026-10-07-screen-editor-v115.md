# v1.15 화면 편집기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이미지 레이어로 화면을 만들고, 모든 기존 마법진의 충전 정보를 편집하며, 1·3·5·7초 표시 시간을 선택합니다.

**Architecture:** 기존 갤러리·파일 가져오기·원자적 저장을 확장합니다. 새 Kotlin 편집기와 실제 화면이 같은 Canvas 합성을 사용하고, 기존 네이티브/WebView 마법진 위에는 사용자 지정 정보만 공통 View로 겹칩니다. 충전 세션의 준비 기한과 표시 기한은 네이티브 한 곳에서 관리합니다.

**Tech Stack:** 기존 Android Java/Kotlin, Custom View/Canvas, WebView, AtomicFile JSON, JUnit 및 기존 자체 계측/Playwright 검사. 새 외부 의존성 없음.

**Spec:** [승인된 설계](../specs/2026-10-07-screen-editor-v115-design-ko.md)

**상태:** 실행 계획 검토 대기. 체크박스가 비어 있는 작업은 아직 구현하거나 검증한 것이 아닙니다.

## Global Constraints

- 작업 루트: `C:\Users\jtn28\OneDrive\Documents\ChatGPT\New project\stellar-sanctuary-v112\MagicCircleAndroid`. 아래 파일 경로는 이 폴더 기준.
- 현재 브랜치 `codex/stellar-sanctuary-v112`, 문서 커밋 `dfffe56`. v1.14 코드가 미커밋 상태이므로 HEAD만 복제해 작업을 시작하지 않습니다.
- 기존 사용자 변경·업로드·탭·활성/비활성·선택 보존. main 병합·실기기 초기화·앱 삭제·개인 배경화면 변경 금지.
- 다음 APK v1.15 / versionCode 18 예정. 배포 직전 실제 최신 번호보다 증가시키고 기존 설치 서명 확인.
- minSdk 23 / compileSdk 37 / targetSdk 37 유지. 한국어·일본어·영어, 오프라인, 추가 종량제 비용 0원.
- JPG/JPEG·PNG·GIF, 최대 이미지 레이어 8개·GIF 레이어 2개(숨김 포함). 기존 파일별 20MiB/해상도/GIF 검증 유지.
- 좌표 0~1, 너비는 화면 짧은 변의 0.05~4배, 각도 [-180, 180), 원본 비율 유지, 좌우 반전.
- 디코딩/프레임 캐시 예산 `min(memoryClassMiB / 4, 48) MiB`. GIF 최대 30fps, 숨김/비가시 시 중단.
- 표시 시간 1000/3000/5000/7000ms, 기본 7000ms. 준비 제한 2000ms와 분리. 연결 해제 즉시 정리, 재연결 처음부터.
- 정보 배치는 디자인별, 시간은 공통. 편집하지 않은 디자인은 기존 정보/서체/장식 유지.
- 이미지 안에 구워진 글자·룬·정적 제목은 정보 편집 대상 아님. 새 내장 마법진 배경화면 지원 확대 없음.
- 코드 변경은 `apply_patch`, 그래프 갱신은 AST-only. 외부 API/유료 테스트/새 라이브러리 설치 없음.
- 문서 계획 승인 뒤에만 제품 코드 수정. 아래 테스트 예시는 새 테스트의 계약이며 지금 통과했다는 뜻이 아닙니다.

## Review Focus

1. 복구 초안에만 남은 이미지 삭제: 원본 참조를 보존하고 저장 작품을 빈 상태로 덮지 않아야 함 → Task 1.
2. 회전된 반전 이미지에서 손가락 선택/이동: 보이는 위치와 선택 영역이 같아야 함 → Task 2·4.
3. 비활성 도안·premium/U04의 특수 배치: 편집이 활성 상태를 바꾸거나 장식·작품 코드까지 숨기면 안 됨 → Task 3·4.
4. 이전 실행의 늦은 준비/타이머, 로딩이 긴 1초 선택: 새 실행 종료·검은 화면·표시 시간 증가가 없어야 함 → Task 5.
5. 라이브 선택기 취소·프로세스 종료·슬롯 적용 여부 불명: 현재 배경화면과 원본 스냅샷을 덮거나 삭제하면 안 됨 → Task 6.

## 파일 책임과 공통 계약

새 제품 파일은 아래 6개로 시작합니다. 구현 과정에서 실제 파일 크기가 감당하기 어려울 때만 책임에 맞춰 추가 분리하고, 사용처 없는 추상화는 만들지 않습니다.

| 새 파일 (`app/src/main/java/com/yj/magiccircle/`) | 책임 |
|---|---|
| `ScreenScene.kt` | 불변 구성·정보 배치·초안 모델과 순수 검증 |
| `LayeredSceneRenderer.kt` | 레이어 변환·합성·자원 수명, 실제 이미지 영역 선택 검사 |
| `ChargeInfoView.kt` | 배터리 정보 배치·그리기·접근성, 편집용 위치 변경 |
| `ChargingSceneView.kt` | 네이티브/WebView/레이어 화면의 공통 준비·시작·정리, 루트와 실제 렌더러 분리 |
| `ScreenEditorActivity.kt` | 편집 도구·파일 선택·저장/취소·초안 복구 |
| `ScreenEditorView.kt` | 미리보기와 드래그/확대·선택 손잡이 |

`MediaLibrary.java`가 JSON 읽기/쓰기와 마이그레이션을 계속 소유합니다. 모델에 파일 경로·WebView·Context를 저장하지 않습니다. 다음 타입은 `ScreenScene.kt`에 두며 Kotlin→Java 호출용 정적 검증은 `@JvmStatic`을 사용합니다.

```kotlin
enum class ScenePurpose { WALLPAPER, CHARGING }
enum class InfoField { BATTERY, STATUS, TEMPERATURE, HEALTH, CONNECTION, METER, MESSAGE }
data class ImageLayer(val id: String, val mediaId: String, val x: Float, val y: Float,
    val width: Float, val angle: Float, val flipX: Boolean, val visible: Boolean)
data class ScreenScene(val id: String, val name: String, val purpose: ScenePurpose,
    val layers: List<ImageLayer>)
data class InfoPlacement(val field: InfoField, val x: Float, val y: Float, val visible: Boolean)
data class EditorDraft(val key: String, val scene: ScreenScene?,
    val information: List<InfoPlacement>?)
```

정보는 모든 대상 ID에 대한 한 개의 `layouts` 맵에만 저장하고 작품 내부에 중복 저장하지 않습니다. `null`은 기존 디자인 기본값, 빈 목록은 모든 정보 숨김입니다. 저장 시 나열한 항목 외에는 꺼짐으로 해석합니다. 초안 key는 대상 ID이며, 신규 작품도 생성 시 `scene-UUID`를 부여해 초안과 저장 작품의 연결을 유지합니다.

## 실행 전 작업 폴더 보호

- [ ] `using-git-worktrees` 지침으로 현재 체크아웃/연결 작업 폴더를 확인하고 적합한 기존 작업 위치를 재사용합니다. 새 작업 폴더가 필요해도 미커밋 v1.14와 사용자 드로잉 코드를 빠뜨리지 않습니다.
- [ ] `git status`, diff, 파일 목록을 기록해 이번 변경과 기존 변경을 구분합니다. 기존 파일의 작업 전 사본은 Git에 넣지 않는 작업용 백업으로 보존합니다.
- [ ] 기존 unit/browser 검사를 실행하고 실패가 있으면 원인·기존 여부를 기록합니다. 이전 결과를 이번 실행의 성공 증거로 쓰지 않습니다.
- [ ] 원격에서도 같은 기반으로 빌드할 수 있도록 필요한 기존 v1.14 변경을 별도 검토한 기반 커밋으로 보존합니다. 사용자 드로잉·무관한 문서/이미지/도구 변경은 포함하지 않습니다. 출처가 불명확하거나 겹치는 변경은 임의 커밋하지 않고 사용자에게 확인합니다.
- [ ] 각 Task의 커밋은 해당 변경만 검토 후 수행합니다. `git add .`와 사용자 변경 덮어쓰기는 금지하며 기존 수정와 같은 hunk가 겹치면 무리하게 분리/되돌리지 말고 보고합니다.

## Task 1: 구성·저장·복구·삭제 보호

**Files:** Create `app/src/main/java/com/yj/magiccircle/ScreenScene.kt`, `app/src/test/java/com/yj/magiccircle/ScreenSceneTest.kt`, `app/src/androidTest/java/com/yj/magiccircle/SceneStorageChecks.kt`; Modify `app/src/main/java/com/yj/magiccircle/MediaLibrary.java`, `app/src/main/java/com/yj/magiccircle/ChargingTransition.java`, `app/src/androidTest/java/com/yj/magiccircle/V113Instrumentation.kt`.

**Interfaces:**
- `SceneRules.validate(scene: ScreenScene, mimeById: Map<String,String>): Unit`, `validateInformation(items: List<InfoPlacement>): Unit`. 잘못된 구성은 예외로 거부.
- `ChargingTransition.durationMs(value: int): long`이 유효 시간 목록의 단일 기준. 잘못된 시간은 7000으로 복구하며 저장소/미리보기/서비스가 이 함수를 사용.
- `MediaLibrary.scene(String): ScreenScene?`, `scenes(): List<ScreenScene>`, `saveScene(ScreenScene,List<InfoPlacement>?): void`, `removeScene(String): void`.
- `chargeInfo(String): List<InfoPlacement>?`, `saveChargeInfo(String,List<InfoPlacement>?): void`, `durationMs(): int`, `setDurationMs(int): void`.
- `editorDraft(String): EditorDraft?`, `saveEditorDraft(EditorDraft): void`, `discardEditorDraft(String): void`.
- `leaseMedia(List<String>): java.io.Closeable`: 기존 자산에 대한 읽기 수명 보호. close는 멱등, 사용하는 동안 실제 삭제 차단. 디스크 잠금 중 디코딩하지 않음.

- [ ] **실패 검사 작성:** `ScreenSceneTest`에서 duration 입력 `[1000,3000,5000,7000]` 각각 동일값, `0/2000/Int.MAX_VALUE`는 7000을 단언. 9번째 레이어·3번째 GIF·중복 layer ID·없는 미디어 ID·NaN·무한수·범위 밖 좌표는 거부. 이름 1~40자/제어문자 금지 및 중복 InfoField 거부.

```kotlin
@Test fun durationPresetsAndFallback() {
    listOf(1000, 3000, 5000, 7000).forEach { assertEquals(it.toLong(), ChargingTransition.durationMs(it)) }
    listOf(0, 2000, Int.MAX_VALUE).forEach { assertEquals(7000L, ChargingTransition.durationMs(it)) }
}
```

- [ ] **RED 확인:** `./gradlew.bat :app:testDebugUnitTest --tests '*ScreenSceneTest' --offline --no-daemon` → 새 타입 미구현 또는 명시한 검증 실패. 환경 실패를 RED로 세지 않음.
- [ ] **최소 구현:** 기존 State에 scenes/layouts/drafts/duration 추가, 스키마 3으로 원자적 저장. v2 원본은 별도 `media-library.v2-recovery.json`, v1 복구 경로 유지. 현재 empty selection/hidden/tab/미디어 보존. 작품 ID는 기존 미디어 URL 검사와 분리. saveScene 성공 시 해당 초안만 지우고 실패 시 그대로 남김. removeScene은 탭 멤버·정보 설정을 같이 정리하고 선택된 작품 삭제 시 기존 next-visible/빈 선택 정책을 재사용. 일반 remove뿐 아니라 retryPendingDeletes에서도 초안/작품/활성 lease 참조 검사.
- [ ] **저장 검사 작성·실행:** `SceneStorageChecks.run(context)`를 기존 runner에 연결. v2→v3 원본 바이트 백업, 읽기/쓰기 재실행 후 동일 구성, 사용 중/초안 참조/lease 참조 삭제 차단, 해제 후 삭제, unknown schema/쓰기 실패/손상 파일 보존을 `check(...)`로 확인. 기존 테스트의 주입 가능한 임시 저장소를 사용하며 사용자 보관함은 만지지 않음.
- [ ] **GREEN 확인:** 위 unit 검사와 계측 공통 명령을 실행해 `V113_CHECKS_OK`. 커밋 후보 변경만 검토 후 `feat: persist layered scenes and editable charge information`.

## Task 2: 같은 코드로 미리보기·실제 레이어 합성

**Files:** Create `app/src/main/java/com/yj/magiccircle/LayeredSceneRenderer.kt`, `app/src/androidTest/java/com/yj/magiccircle/LayeredSceneChecks.kt`; Modify `app/src/main/java/com/yj/magiccircle/MediaWallpaperRenderer.kt`, `app/src/androidTest/java/com/yj/magiccircle/MediaWallpaperChecks.kt`, `app/src/androidTest/java/com/yj/magiccircle/V113Instrumentation.kt`.

**Interfaces:**
- `LayeredSceneRenderer(scene: ScreenScene, openMedia: (String)->InputStream, budgetBytes: Long): Closeable`.
- `prepare(width: Int,height: Int): Unit`, `draw(canvas: Canvas,elapsedMs: Long,animated: Boolean): Unit`, `hitTest(x: Float,y: Float): String?`, `close(): Unit`, 읽기 전용 `animated: Boolean`.
- 구성 변경은 기존 renderer를 안전하게 닫고 최신 스냅샷으로 교체. 드래그 중에는 캐시된 자산과 Matrix만 갱신할 수 있게 `updateLayers(layers: List<ImageLayer>): Unit` 제공.

- [ ] **실패 검사 작성:** 앱 코드로 만든 비대칭 2색 PNG/투명 PNG와 검증된 기존 GIF fixture 사용. 중앙 두 점 색으로 순서·투명도·90도 회전·좌우반전 확인, 숨김 레이어 제외, 변환된 이미지 내부 hitTest는 올바른 ID/외부는 null, GIF 0ms/중간 프레임 상이·다음 실행 0ms 동일 단언.
  검사 함수 `transformedLayerHitAndPixels()`는 320×320 출력의 중심에 id="upper"인 160×80 이미지를 놓고 `check(renderer.hitTest(160f,160f)=="upper")`, `check(renderer.hitTest(0f,0f)==null)`을 단언합니다. 반전 전후 `(120,160)`과 `(200,160)`의 빨강/파랑 색이 뒤바뀌는지도 확인합니다.
- [ ] **RED 확인:** `LayeredSceneChecks.run()` runner 연결 후 계측 공통 명령 → 새 renderer 미구현/그리기 비교 실패.
- [ ] **최소 구현:** MediaWallpaperRenderer의 전체 검정 지우기와 중앙 채우기를 선택 가능한 기본 동작으로 분리해 기존 단일 이미지 호출은 그대로 유지. 새 합성은 배경을 한 번만 지우고 layer별 save/이동/회전/반전/크기/draw/restore. 같은 Matrix의 역변환으로 hitTest. EXIF 방향을 반영한 폭/높이 재사용.
- [ ] **경계 검사 추가:** 최대 8개/2 GIF 구성, 작은 budget으로 강제 샘플링·실패, 숨김 GIF 미디코딩, prepare 반복·close 두 번·중간 디코딩 실패 시 모든 자원 정리. 다른 레이어의 Paint/Matrix가 다음 레이어에 새지 않는 픽셀 검사.
- [ ] **GREEN 확인:** 기존 `MediaWallpaperChecks`와 새 계측 통과, 이미지 fixture 파일을 제품 자산으로 추가하지 않음. 커밋 `feat: compose transformed image layers with bounded memory`.

## Task 3: 전체 마법진의 정보 분리와 공통 화면 호스트

**Files:** Create `app/src/main/java/com/yj/magiccircle/ChargeInfoView.kt`, `app/src/main/java/com/yj/magiccircle/ChargingSceneView.kt`, `app/src/androidTest/java/com/yj/magiccircle/ChargeInfoChecks.kt`, `selftest/charge-info-browser.test.cjs`; Modify `app/src/main/java/com/yj/magiccircle/MainMagicChargeView.kt`, `ChargeStatusPanelRenderer.kt`, `WebViews.java`, `app/src/main/assets/{magic_circle,theme_circle,collection_circle,media_circle}.html`, `app/src/androidTest/java/com/yj/magiccircle/V113Instrumentation.kt`.

**Interfaces:**
- `ChargeInfoView(context: Context)`; `setInformation(List<InfoPlacement>): Unit`, `update(snapshot: ChargeSnapshot,language: String,progress: Float): Unit`, `placementAt(x: Float,y: Float): InfoField?`.
- `ChargeInfoView.defaultInformation(themeId: String): List<InfoPlacement>` 정적 메서드는 저장 설정이 없는 편집기의 초깃값. BATTERY/STATUS 등 원래 있는 항목만 기본 표시하며 알려진 모든 theme ID를 처리. 실제 기본 화면은 null 설정으로 원래 렌더러를 그대로 사용.
- `ChargingSceneView(context: Context, themeId: String, information: List<InfoPlacement>?) : FrameLayout, Closeable`; `prepare(onReady: Runnable,onFailure: Runnable): Unit`, `start(startUptimeMs: Long,deadlineUptimeMs: Long): Unit`, `showEditorFrame(): Unit`, `setInformation(List<InfoPlacement>?): Unit`, `close(): Unit`.
- host가 실제 native/WebView/합성 renderer와 정보 View를 보유. 루트와 렌더러 동일성은 별도 검사. scene ID는 Task 1로 조회하고 Task 2로 합성, 기존 native ID/웹 ID는 기존 렌더러 재사용.
- 웹 계약: `window.prepareChargingAnimation(): boolean`은 자산 준비 여부만 보고, `window.startChargingAnimation(remainingMs): boolean`만 재생 시작. `window.showChargeEditorFrame()`은 정적 편집 프레임. 쿼리 `editableInfo=1`일 때만 원래 동적 정보 숨김.

- [ ] **실패 검사 작성:** 순회 대상은 하드코딩된 개수가 아니라 `ThemeSelection.IDS`. 모든 ID를 편집 가능 경로로 분류. N01/W03/R01/classic/premium/U04/일반 theme/collection/media/custom 대표에서 사용자 지정 항목 한 번만 표시되고, null 설정은 기존 표시 유지 단언.
  browser 검사 `customInformationKeepsArtwork()`에서 `.editable-info` 상태의 원래 `[data-level]`이 모두 비표시이고 `#review-code`가 있는 페이지는 여전히 표시됨을 `assert.equal(...)`로 확인합니다. 계측 `hiddenFieldsAreNotSpoken()`는 빈 정보 목록 적용 뒤 `check(infoView.contentDescription.isNullOrEmpty())`를 단언합니다. 제품에서도 숨김 모드 CSS 클래스는 `.editable-info`로 통일합니다.
- [ ] **RED 확인:** 새 browser 검사와 `ChargeInfoChecks.run(context)` 계측에서 숨김/정적 프레임/호스트 계약 실패 확인.
- [ ] **최소 구현:** N01 패널은 정적 제목·장식과 동적 항목만 분리. 웹 `.readout` 전체가 아니라 잔량/상태/메트릭/막대/메시지 요소에 식별자를 붙여 숨김. 모양·색·서체는 기존 화면 팔레트를 기본값으로 사용. native-N01, premium, ref-U04는 잔량 기본 중앙; W03/R01·일반 테마는 현재 배치 기준으로 정보 초깃값 구성. 그림 자체는 이동하지 않음.
- [ ] **데이터/접근성 검사:** 실제 ChargeSnapshot을 사용해 0%·100%·알 수 없음·충전 대기, ko/ja/en, 글꼴 2배에서도 안전 영역 안에 배치. 숨긴 필드의 화면/스크린리더 설명 모두 없음. 해제·닫기 뒤 수신기와 WebView 리소스 누수 없음.
- [ ] **GREEN 확인:** 기존 browser 6종, 새 browser, 기존 패널 계측 및 새 계측 통과. 커밋 `feat: edit charging information across every design`.

## Task 4: 홈 진입·이미지/정보 편집·저장된 작품 목록

**Files:** Create `app/src/main/java/com/yj/magiccircle/ScreenEditorActivity.kt`, `ScreenEditorView.kt`, `app/src/androidTest/java/com/yj/magiccircle/ScreenEditorChecks.kt`, `selftest/editor-gallery.test.cjs`; Modify `app/src/main/java/com/yj/magiccircle/MainActivity.java`, `MediaLibrary.java`, `WebViews.java`, `app/src/main/assets/gallery.html`, `gallery.js`, `i18n.js`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ko/strings.xml`, `app/src/main/res/values-ja/strings.xml`, `app/src/main/AndroidManifest.xml`, `app/src/androidTest/java/com/yj/magiccircle/V113Instrumentation.kt`.

**Interfaces:**
- 내부 전용 Activity, `android:exported=false`. Intent extras: `scenePurpose` = WALLPAPER/CHARGING(신규) 또는 `themeId`(기존 편집). 임의 파일 경로/URL을 받지 않음.
- `ScreenEditorView(context: Context)`; `setDraft(EditorDraft): Unit`, `currentDraft(): EditorDraft`, `setOnDraftChanged(listener: (EditorDraft)->Unit): Unit`, `close(): Unit`.
- gallery 링크 `magiccircle://create?purpose=wallpaper|charging`, `magiccircle://edit?theme=<검증된 ID>`, `magiccircle://duration?ms=1000|3000|5000|7000`. MainActivity에서 각 파라미터 재검증.
- Task 1 API로 저장/초안 유지. 신규 가져오기는 `importDocument(uri,false)`를 재사용하되 추가된 ID를 안전하게 반환/확인하도록 결과 계약 확장. 비동기 완료 시 편집 세대가 바뀌었다면 현재 레이어에 잘못 추가하지 않음.

- [ ] **실패 검사 작성:** 홈 버튼 3개, 용도 선택 2개, 기존/비활성 도안에 정보 편집 진입, 조작 후 selection/enabled가 불변, 저장 작품이 올바른 용도 목록에 나타남을 `editor-gallery.test.cjs`로 확인. 기존 홈 버튼 수 2개 assertion은 새 요구에 맞게 변경.
  `homeOpensEditorWithoutApplying()`에서 `assert.equal(await page.locator('#home-screen button').count(),3)`와 `magiccircle://create?purpose=charging` 요청을 검사합니다. 계측 `draftSurvivesRecreation()`는 `check(restoredDraft == savedDraft)`, `check(library.selected() == beforeSelection)`으로 검증합니다.
- [ ] **RED 확인:** `node selftest/editor-gallery.test.cjs` 및 새 계측의 저장/복원 검사가 실패하는지 확인.
- [ ] **최소 구현:** 네이티브 버튼/슬라이더/숫자 입력으로 이미지 이동·비율 고정 크기·각도·좌우반전·순서·숨김·제거, 공통 정보 이동·켜기/끄기·기본 복원 제공. 드래그/확대 제스처와 `가운데로`를 연결. 정보 편집은 Task 3 정적 호스트 위에 터치 가능한 정보 View를 사용. 새 이미지 작품은 Task 2와 동일 변환을 사용.
- [ ] **목록 연결:** galleryState에 작품의 ID/이름/용도/구성 해시를 추가하고 기존 탭·선택·삭제 흐름에 연결. 새 작품 썸네일은 Task 2의 0ms 합성을 짧은 변 320px로 생성하며 전체 출력은 최대 320×640px로 제한. WebViews의 `https://appassets.androidplatform.net/scene-thumbnails/<검증된 scene-ID>` 경로만 허용하고 구성 변경 시 캐시를 무효화. 캐시 실패는 작품을 삭제하지 않고 이름/일반 아이콘으로 표시.
- [ ] **저장 안전 검사:** 회전·Activity 재생성·프로세스 복구는 초안으로 복원. 뒤로 가기 저장/버리기/계속 편집, 저장 실패 후 초안 유지, 가져오기 취소/invalid URI, 읽기 실패·손상 파일이 기존 작품을 지우지 않는지 검사. 사용자 탭·숨김 설정 불변.
- [ ] **시각 검사:** 가상 폰에서 3레이어 겹침/반전/회전/저장 후 재편집, 모든 정보 off/on 확인. 가상 태블릿에서 세로/가로·스크린리더 설명·48dp 조작 영역 확인. 편집 도구가 실제 미리보기에 섞이지 않음.
- [ ] **GREEN 확인:** 관련 browser·unit·계측 통과 및 두 화면 캡처 직접 확인. 커밋 `feat: add home screen creation and layer editing`.

## Task 5: 표시 시간과 재연결 수명 통일

**Files:** Modify `app/src/main/java/com/yj/magiccircle/ChargingTransition.java`, `ChargingAccessibilityService.java`, `ChargingSceneView.kt`, `MainActivity.java`, `WebViews.java`, `app/src/main/assets/{magic_circle,theme_circle,collection_circle,media_circle}.html`, `gallery.js`, `app/src/test/java/com/yj/magiccircle/SanctuaryIntegrationTest.java`, `selftest/com/yj/magiccircle/ChargingTransitionSelfTest.java`, `selftest/device-charging-overlay.ps1`; Create `selftest/charging-duration-browser.test.cjs`.

**Interfaces:**
- 기존 `acceptsCallback(...)`과 Task 1의 `ChargingTransition.durationMs(int): long` 재사용. `prepareDeadline(long connectedAt): long`, `displayDeadline(long startedAt,long durationMs): long` 추가. 준비 기한은 +2000.
- 서비스와 미리보기 모두 Task 3 host 사용. 한 실행의 `runId/themeId/duration/정보 스냅샷`은 시작 후 변경하지 않음. 준비 확인 뒤 now/deadline 한 번만 결정해서 host.start와 네이티브 종료 타이머가 공유.
- `device-charging-overlay.ps1`에 필수 `-Serial` 및 `-DurationMs` 인자. emulator serial인지 검증하고 실제 폰에는 모의 배터리 명령을 보내지 않음.

- [ ] **실패 검사 작성:** `assertEquals(12000L,prepareDeadline(10000L))`; `assertEquals(13000L,displayDeadline(10000L,3000L))`. 4개 시간에 대해 같은 공식 확인. 전 실행 콜백을 보낸 뒤 새 host/새 run은 계속 살아있음, 분리 중 준비완료는 재생 금지.
- [ ] **RED 확인:** 기존 SanctuaryIntegrationTest와 standalone SelfTest의 7초 고정 assertion을 명시적 새 계약으로 갱신하고 신규 시간/늦은 이벤트 검사가 실패하는지 확인. 예전 재연결 검사를 삭제하지 않음.
- [ ] **최소 구현:** fixed connect+7000 타이머를 준비/표시 기한으로 분리. 준비 중 루트는 기존 화면을 가리지 않되 WebView 준비를 `GONE`/visual callback에 묶지 않음. 성공하면 명시적으로 시작/표시, 실패·timeout·화면 off·detach 때 close. 모든 callback은 run과 해당 renderer 둘 다 검사.
- [ ] **웹 검사 추가:** prepare만 호출하면 `running` 없음, 로드 완료 후에도 명시적 start 전에는 재생 안 함. start 한 번만, 1초/3초/5초/7초마다 입장/단계/퇴장만 맞춰지고 무한 룬 회전 playbackRate는 1. GIF는 원래 속도. 기존 전체 `getAnimations()` 속도 일괄 변경은 유한 연출만 대상으로 수정.
- [ ] **기기 시간 검사:** 에뮬레이터 10회 재연결·도중 분리·느린/실패 로드·이전 timeout 주입. 기존 1.5초 고정 대기 방식 대신 started/closed run 로그와 elapsed 시간으로 판정. 단위 검사는 정확한 fake time, 기기 검사는 종료 지연 250ms 초과/조기 종료를 실패로 보고 실제 지연 값을 기록. `finally`에서 모의 배터리를 reset.
- [ ] **GREEN 확인:** unit, standalone, browser, 지정 가상기기 검사 모두 통과. 커밋 `fix: honor selectable charging durations across reconnects`.

## Task 6: 레이어 작품의 정지/라이브 배경화면 적용

**Files:** Modify `app/src/main/java/com/yj/magiccircle/WallpaperArtwork.kt`, `WallpaperController.kt`, `WallpaperPolicy.kt`, `UploadedWallpaperStore.kt`, `MagicWallpaperService.kt`, `app/src/test/java/com/yj/magiccircle/WallpaperPolicyTest.kt`, `selftest/wallpaper-browser.test.cjs`; Create `app/src/androidTest/java/com/yj/magiccircle/SceneWallpaperChecks.kt`; Modify `app/src/androidTest/java/com/yj/magiccircle/V113Instrumentation.kt`.

**Interfaces:**
- WallpaperArtwork가 `scene-UUID`를 Task 1로 읽고 Task 2 renderer를 사용. 기존 prepare/draw/close 계약 유지.
- 기존 `UploadedWallpaperStore.stage(context,id): String`이 wallpaper-purpose scene ID도 받으며 동일 slot key 반환. 슬롯 안의 세대별 자산과 원자적 manifest를 사용하되 기존 단일 이미지 슬롯도 읽음.
- WallpaperController는 scene 목적/존재 여부를 검사하고 기존 시스템 chooser 및 still 적용 경로 재사용. 저장만으로는 적용하지 않음.

- [ ] **실패 검사 작성:** home/lock + scene 허용, charging-purpose scene 배경 적용 거부, 기존 W03/R01/UUID 정책 유지. 저장 작품 변경·보관함 삭제 뒤 적용 스냅샷 동일, 두 used slot 불변, candidate stage 실패/취소/unknown outcome에서 활성 slot byte hash 불변을 단언.
  `cancelledCandidatePreservesActiveSlots()`는 테스트용 저장소에서 used slot 두 개의 SHA256을 기록한 뒤 미사용 slot stage·취소를 수행하고 `check(beforeHashes == afterHashes)`를 단언합니다. `snapshotDoesNotFollowEdits()`는 동일한 시각 0ms로 그린 적용본의 픽셀이 원본 작품 수정 전후 같은지 확인합니다.
- [ ] **RED 확인:** 새 policy 검사와 SceneWallpaperChecks 계측이 현재 단일 파일 전용 경로에서 실패하는지 확인.
- [ ] **최소 구현:** lease로 stage 중 미디어 삭제를 막고 모든 파일 준비 후 하나의 manifest를 확정. 이전 live engine이 참조하는 세대는 덮어쓰거나 정리하지 않음. 명확히 미사용으로 확인된 후보만 후속 정리. lock 상태 확인 불가 시 보수적으로 보존.
- [ ] **기기 검증:** 지정 가상 폰에서 still→취소→live→취소/적용→작품 수정 후 적용 화면 불변 확인. GIF 첫 프레임 still 설명과 live 원래 속도 반복 확인. 숨겨진 wallpaper engine은 프레임 예약 없음, surface 재생성 뒤 재개.
- [ ] **GREEN 확인:** 기존 WallpaperChecks/MediaWallpaperChecks와 새 계측·policy/browser 통과. 커밋 `feat: apply layered scenes as static and live wallpapers`.

## Task 7: 통합 검증·버전·인수인계

**Files:** Modify `app/build.gradle.kts`, `docs/EMULATOR_KO.md`, 필요 시 기존 `../.github/workflows/android-debug.yml`; Create `docs/V1_15_KO.md`, `docs/v115-*.png` 검증 캡처. 제품 변경 범위를 넘어 설정·계정·플러그인은 수정하지 않음.

- [ ] **검증 체크리스트 실행:** 설계 §9의 11개 항목을 관련 Task 결과와 연결하고 누락을 검사. 활성/비활성 전체 도안 ID 편집 가능 검사는 자동화, 대표 렌더러 가족은 실제 화면 검증. 미측정 삼성 성능/잠금 동작은 확인 완료라고 하지 않음.
- [ ] **버전 증가:** 최신 배포 상태 확인 후 versionName 1.15 / versionCode 18 이상. 동일 applicationId와 기존 서명으로 업데이트 설치 가능 여부 확인.
- [ ] **전체 검사/빌드:** 아래 공통 명령으로 새 로그를 수집. API36 가상 폰·태블릿을 하나씩 실행해 앱 home/editor/저장작품/정보편집/충전/배경화면을 캡처하고 즉시 열어 확인. AndroidRuntime crash/ANR·재생 후 열린 리소스 누적 없음 확인.
- [ ] **그래프/독립 검토:** AST-only `graphify update .`, 소스 diff·사양·검사 결과에 대해 새 리뷰어의 최종 검토. 지적 수정 후 관련 검사를 재실행. 그래프 경고는 숨기지 않음.
- [ ] **문서/커밋:** 한국어 사용 방법, 변경 파일, 기기/API, APK 버전·SHA256·서명, 실제/미확인 결과 기록. 현 작업 변경만 커밋하고 main 미병합.
- [ ] **배포 인수인계:** 소스/빌드가 모두 검증된 뒤에만 기존 GitHub 브랜치·release 배포 권한 범위 내 업로드. 원격 코드와 APK가 다른 경우 이를 숨기지 않음. 실제 APK URL의 다운로드/파일 해시를 확인 후 링크 제공. 별도 유료 GitHub Actions 실행량·새 청구 설정은 활성화하지 않음.

## 공통 검증 명령과 성공 기준

실행 당시 `adb devices -l`로 **해당 AVD의 serial**을 확인합니다. 아래 예시의 `emulator-5554`를 확인 없이 그대로 사용하지 않습니다. 가상 폰과 태블릿은 하나씩 실행하고 실기기는 건드리지 않습니다.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon
# 성공: BUILD SUCCESSFUL, 테스트 실패 0개, 새 lint 오류 0개.
# Node는 설치된 런타임을 사용; 현재 셸에서만 기존 모듈 경로를 지정.
$env:NODE_PATH='C:\Users\jtn28\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
node selftest/library-browser.test.cjs
node selftest/wallpaper-browser.test.cjs
node selftest/languages-browser.test.js
node selftest/circle-browser.test.js
node selftest/collection-browser.test.cjs
node selftest/media-browser.test.js
node selftest/charge-info-browser.test.cjs
node selftest/editor-gallery.test.cjs
node selftest/charging-duration-browser.test.cjs
# 성공: 각 프로세스 exit 0. 각 명령 직후 실패하면 다음 단계/배포 중지.

$androidSdk='C:\Users\jtn28\AppData\Local\Android\Sdk'
& "$androidSdk\platform-tools\adb.exe" devices -l
# 아래는 확인한 virtual serial 예시. 테스트 APK까지 새 버전으로 설치.
& "$androidSdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& "$androidSdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& "$androidSdk\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation
# 성공: V113_CHECKS_OK. runner 이름은 기존 호환성 때문에 유지.
```

Task 5의 standalone 검사는 기존 Android Studio JBR의 `javac`/`java -ea`로 실행합니다. 자동화 스크립트에서 외부 프로세스마다 `$LASTEXITCODE`를 확인하며 마지막 명령만 성공했다고 전체 성공으로 처리하지 않습니다. 스크린샷은 Android CLI 사용 지침대로 캡처 직후 직접 열어 확인합니다. 비행기 모드/오프라인과 삼성 One UI 실기기 결과를 구분합니다.

## 실행 방식 선택

이 계획은 데이터·호스트·편집 화면의 계약이 이어지므로 **주 에이전트가 순서대로 구현 + 마지막 독립 검토**를 추천합니다. 단계마다 새 구현/검토 에이전트를 만드는 방식보다 문맥 전달과 토큰 소비가 적습니다. 별도 읽기 전용 점검이 필요할 때만 병렬 작업을 사용합니다.

대안은 **단계별 구현 에이전트 + 단계별 검토**입니다. 단계마다 독립 검토가 있지만 토큰 소비가 더 큽니다. 사용자에게 계획 검토와 두 방식 중 선택을 받은 뒤 실행합니다.
