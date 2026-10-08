# VRM 캐릭터·이미지 배경화면 합성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 등록한 VRM을 기존 배경화면 편집기에서 선택하고 이미지와 조합·저장·재편집·라이브 적용한다. 최종 목표는 모바일 캐릭터 제작·수정·저장·배경화면 출력이며 이번 단계는 그 출력 기반이다.

**Architecture:** 기존 Canvas 이미지 캐시의 범위 그리기와 투명 VRM WebView를 하나의 합성 뷰에 배치한다. 작품은 불변 모델 ID와 배치만 저장하며, 배경화면은 별도 불변 적용본으로 실행한다. 향후 제작 데이터는 출력 모델과 분리하고 같은 모델 보관·합성 경로에 연결한다. 새 엔진이나 제작기용 가짜 API는 추가하지 않는다.

**Tech Stack:** 기존 Kotlin/Java Android Views, Canvas, WebView, three 0.180.0, three-vrm 3.5.5, esbuild 0.28.2, AtomicFile, JUnit 및 기존 Instrumentation.

**Spec:** `../specs/2026-10-08-vrm-scene-composition-v120-design-ko.md` (2026-10-08 최종 목표 조건 반영 승인).

## Global Constraints

- minSdk 23, compileSdk/targetSdk 37, Java 17 유지. 배포 목표 v1.20 / versionCode 23, 중간 배포가 있으면 그보다 높은 번호.
- 한 작품의 캐릭터는 2.5D 또는 VRM 중 1명. 이미지 8개/GIF 2개 한도. VRM 위치 ±35%, 크기 50–150%, 정면 고정.
- 한국어·일본어·영어 유지. VRM 합성은 라이브 적용만 제공하고 기존 작품의 정지/라이브 적용을 보존.
- 추가 유료 API·의존성·INTERNET 권한·외부 리소스·JS 네이티브 브리지를 추가하지 않는다. 개인 VRM/이미지를 Git·APK·CI에 넣지 않는다.
- 모델 ID는 검증된 출력 버전이다. 외부 파일명/URI 및 미래 제작기의 UI와 결합하지 않는다. VRM을 편집 원본으로 간주하지 않으며 제작기·파트 자산·`.vroid` 호환은 이번 범위가 아니다.
- 작업 브랜치 `codex/stellar-sanctuary-v112` 유지, main 병합 금지. 기존 사용자 수정/미추적 파일을 보존하고 이번 파일만 명시적으로 스테이징한다.
- 이 문서는 구현 계획이며 아직 제품 변경·테스트 성공·새 APK가 아니다. 실행 방식 확인 후 Task 1부터 시작한다.

## Review Focus

1. 모델 교체·화면 회전 중 늦게 도착한 콜백이 새 캐릭터를 덮거나 자원을 이중 해제하지 않는가 — Tasks 2, 4.
2. 숨긴 캐릭터와 GIF 조합에서 첫 프레임 대기 때문에 적용이 영원히 막히거나 숨긴 3D 렌더가 계속되지 않는가 — Tasks 2, 5.
3. 같은 ID의 이미지가 여러 위치에서 쓰이거나 투명 전경으로 쓰여도 표시 순서·투명도가 유지되고 메모리 예산을 넘기지 않는가 — Tasks 2, 6.
4. 적용 준비 중 프로세스 종료/저장 실패/두 화면의 중복 요청이 기존 적용본을 변경하지 않는가 — Tasks 3, 5.
5. 가져온 모델 누락 또는 향후 새 모델 버전 발행이 기존 작품 목록 전체를 깨뜨리거나 적용된 모델을 자동 교체하지 않는가 — Tasks 1, 5.

## 파일과 실행 기준

아래 경로는 별도 표기가 없으면 `MagicCircleAndroid/` 기준이다. `K=app/src/main/java/com/yj/magiccircle`, `T=app/src/test/java/com/yj/magiccircle`, `I=app/src/androidTest/java/com/yj/magiccircle`로 표기한다.

- `K/ScreenScene.kt`: 선택적 VRM 레이어·검증·JSON 호환성. `K/VrmPlacement.kt`: 화면 좌표 변환.
- `K/LayeredSceneRenderer.kt`: 기존 단독 그리기는 유지하고 합성용 이미지 범위 그리기 제공.
- 새 `K/VrmSceneView.kt`: 아래 이미지/투명 VRM/위 이미지의 단일 합성 뷰와 자원 소유권.
- `K/VrmWebView.kt`, `vrm-viewer/viewer.js`, `app/src/main/assets/vrm-preview/viewer.css`: 합성 모드만 투명 처리. 번들은 npm build로 생성한다.
- `K/VrmWallpaperStore.kt`: v1/v2 적용본 및 준비→시스템 결과까지의 요청 보호. `K/VrmWallpaperService.kt`: 합성 뷰를 기존 엔진 생명주기에 연결.
- `K/ScreenEditorActivity.kt`, `K/ScreenEditorView.kt`: 캐릭터 선택·배치·저장·미리보기. `K/WallpaperController.kt`, `K/VrmPreviewActivity.kt`: 적용 요청 및 결과 연결.
- `K/MediaLibrary.java`, `K/WallpaperArtwork.kt`, `K/WebViews.java`, `app/src/main/assets/gallery.js`, `app/src/main/assets/gallery.html`, 기존 지역화 자산: 작품 목록·누락 안내·정지 출력 차단에 필요한 부분만 수정.
- 기존 단위/Android/웹 검사 확장, `I/VrmSceneChecks.kt` 추가. 버전과 저장소 루트 `.github/workflows/android-debug.yml`은 마지막 배포 작업에서 갱신.

작업 시작 시 Git 상태와 실제 API를 다시 확인한다. 계획에 없는 상충 변경은 덮어쓰지 않는다. 아래 Gradle 명령은 Android 프로젝트 디렉터리에서, npm 명령은 `vrm-viewer/`에서 실행한다. 테스트용 입력은 임시 저장소를 사용하고 실제 작품을 삭제하지 않는다.

## Task 1: 불변 모델 참조를 가진 작품 데이터

**Files:** Modify `K/ScreenScene.kt`, `K/VrmPlacement.kt`; Test `T/ScreenSceneTest.kt`, `T/VrmPlacementTest.kt`, `I/SceneStorageChecks.kt`.

**Interfaces:**
- 추가: `VrmSceneLayer(modelId: String, placement: VrmPlacement = VrmPlacement(), visible: Boolean = true, beforeImage: Int = 0)`.
- `ScreenScene` 생성자 맨 뒤에 `vrm: VrmSceneLayer? = null`을 추가한다. 기존 위치 인수와 2.5D 데이터는 유지한다.
- 추가: `VrmPlacement.screenX(): Float`, `screenY(): Float`, `withScreenPosition(x: Float, y: Float): VrmPlacement`; 화면 좌표 = `.5f + offset`, 입력은 화면 0.15–0.85로 제한한다. x 오른쪽/y 아래쪽이 양수다.
- 기존 `SceneRules.validate(scene, mimeById)`, `SceneData.sceneJson/readScene`를 확장한다. 구조 검증은 디스크의 모델 존재 여부를 검사하지 않는다.

- [ ] 실패 검사 `vrmScenePreservesVersionReference`를 추가한다: `modelId="a".repeat(64)`와 `VrmPlacement(.2f,-.1f,1.2f,false)`가 있는 WALLPAPER는 통과, CHARGING/2.5D와 동시 설정/잘못된 ID/범위 밖·NaN 배치/삽입 인덱스 초과는 거절. 기존 이미지 8개/GIF 2개 한도 유지.
- [ ] `placementUsesScreenCoordinates` 검사: 기본값의 화면 x/y는 `.5f`, `.withScreenPosition(.7f,.4f)` 결과는 허용 오차 내 `.2f,-.1f`; 가장자리 제한과 왕복 변환을 확인한다.
- [ ] `SceneStorageChecks`에 이전 필드 누락 JSON→`vrm=null`, VRM 작품/임시 저장 왕복, 서로 다른 모델 ID의 두 작품이 독립인 경우를 추가한다. 실제 모델 파일이 없어도 구조상 유효한 작품 목록 전체는 읽을 수 있어야 한다.
  핵심 단언(기존 fixture에 적용):
  ```kotlin
  val p = VrmPlacement().withScreenPosition(.7f, .4f)
  assertEquals(.2f, p.x, .00001f)
  assertEquals(-.1f, p.y, .00001f)
  val s = ScreenScene("scene-$media", "작품", ScenePurpose.WALLPAPER,
      emptyList(), vrm = VrmSceneLayer("a".repeat(64), p))
  SceneRules.validate(s, emptyMap())
  rejected { SceneRules.validate(s.copy(purpose=ScenePurpose.CHARGING), emptyMap()) }
  // Android JSON 검사에서 실행:
  check(SceneData.readScene(SceneData.sceneJson(s)) == s)
  check(SceneData.readScene(SceneData.sceneJson(s).apply { remove("vrm") }).vrm == null)
  ```
- [ ] `./gradlew.bat testDebugUnitTest --tests com.yj.magiccircle.ScreenSceneTest --tests com.yj.magiccircle.VrmPlacementTest`로 신규 타입/검증이 없어서 실패함을 확인한다. JSON 왕복 검사는 기존 Android runner에서 별도로 실행한다.
- [ ] 위 타입과 변환/JSON 처리만 구현한다. 저장 배치는 유한·정상 범위를 검증하고 런타임 UI 입력 제한과 구분한다. 저장된 모델 참조를 전역 `selected()`로 치환하지 않는다.
- [ ] 같은 단위 검사와 `SceneStorageChecks` 포함 전체 Android 기본 검사를 실행해 통과시킨 후 이 작업 파일만 `feat: store versioned VRM scene layers`로 커밋한다.

## Task 2: 실제 3D와 이미지의 공유 합성 뷰

**Files:** Modify `K/LayeredSceneRenderer.kt`, `K/VrmWebView.kt`, `K/VrmRenderHealth.kt`, `vrm-viewer/viewer.js`, `app/src/main/assets/vrm-preview/viewer.css`; Create `K/VrmSceneView.kt`, `I/VrmSceneChecks.kt`; Test `I/LayeredSceneChecks.kt`, `I/V113Instrumentation.kt`, `T/VrmRenderHealthTest.kt`.

**Interfaces:**
- 추가: `LayeredSceneRenderer.drawImages(canvas: Canvas, elapsedMs: Long, animated: Boolean, from: Int, until: Int, clear: Boolean)`; `[from,until)`이며 범위를 검증한다. 기존 `draw`는 그대로 유지한다.
- 추가: `VrmWebView.create(context: Context, openModel: ()->InputStream?, onFailure: (VrmFailure)->Unit, transparent: Boolean = false, onPageFinished: (WebView)->Unit): WebView`; 마지막 콜백은 유지하여 기존 trailing lambda 호출을 보존한다. `url(language,hasModel,wallpaper,composition: Boolean = false): String`도 추가한다.
- 추가: `VrmSceneView(context: Context, scene: ScreenScene, openModel: () -> InputStream?, openMedia: (String) -> InputStream, onReady: () -> Unit, onFailure: (VrmFailure) -> Unit) : FrameLayout, AutoCloseable`.
- 합성 뷰의 호출 계약: `val webView: WebView?`, `val ready: Boolean`, `updateScene(scene: ScreenScene)`, `setActive(active: Boolean)`, `close(crashed: Boolean)`, `override close()`; close는 반복 호출 안전. 이미지/모델 ID 구조 변경은 호스트를 새로 만들고 위치·크기·순서만 바뀌면 기존 인스턴스를 갱신한다.
- `VrmFailure.MEDIA`를 추가해 이미지 준비 오류를 구분하며 health의 자동 재시도 불가 오류에 넣는다.

- [ ] `LayeredSceneChecks`에 검은 배경+붉은 사각형+투명 PNG 두 범위 합성 검사를 추가한다. `clear=false`인 전경은 기존 픽셀을 지우지 않아야 하며, 같은 이미지 참조를 여러 레이어에 써도 변환별 결과가 맞아야 한다. 열기 콜백 수로 앞/뒤 그리기마다 다시 디코딩하지 않음을 확인한다.
- [ ] `VrmSceneChecks`와 runner의 `checks=vrm-scene`를 추가한다. 초기 ready=false, 정상 그림 첫 프레임 후 true, 손상 이미지 실패, 닫힌 뒤 늦은 준비 콜백 무시, 숨긴 VRM에서 프레임 대기 없음/JS 루프 중지, MEDIA 재시도 중지의 실행 가능한 검사를 작성한다. 임시 Android 자산/합성 가짜 콜백을 사용하고 개인 VRM을 테스트 소스에 넣지 않는다.
  `rangeKeepsUnderlyingPixels`와 `mediaFailureDoesNotRetry`의 핵심 단언:
  ```kotlin
  val before = target.getPixel(0, 0)
  renderer.drawImages(Canvas(target), 0, false, 0, 0, false)
  check(target.getPixel(0, 0) == before)
  renderer.drawImages(Canvas(target), 0, false, 0, 1, true)
  check(target.getPixel(120, 160) == Color.RED)
  assertNull(VrmRenderHealth().onFailure(VrmFailure.MEDIA))
  ```
- [ ] `assembleDebugAndroidTest`와 새 route를 실행해 미구현된 동작이 실패함을 확인한다.
- [ ] 이미지 캐시 한 개와 2개 범위 Canvas 뷰를 재사용하고 그 사이 투명 WebView를 둔다. 같은 GIF 시간값을 전달한다. decode는 작업 스레드, UI 교체는 메인 스레드+세대 검사로 처리한다. 숨김 전환 시 해당 준비/표시 캐시를 안전하게 갱신한다.
- [ ] 합성 모드에만 HTML/WebView 투명 배경·바닥 장식 숨김을 적용한다. WebView 허용 URL 목록/파일 접근 정책은 변경하지 않는다. 유효 모델 파일을 확인하고, 표시할 때만 VRM 프레임을 준비한다. 업데이트 중 적용 가능 여부를 다시 false로 만들고 첫 프레임 이후 회복한다.
- [ ] `npm test`, `npm run build`, 관련 Gradle 검사 및 `vrm-scene`를 통과시킨다. 두 개인 모델은 이미 가져온 에뮬레이터에서만 실제 렌더해 확인한다. 투명 합성이 실패하면 여기서 원인을 해결하고 캡처로 대체하지 않는다. 변경 파일을 `feat: composite live VRM with native image layers`로 커밋한다.

## Task 3: 불변 적용본과 겹치지 않는 적용 요청

**Files:** Modify `K/VrmWallpaperStore.kt`; Test `I/VrmWallpaperChecks.kt`.

**Interfaces:**
- 기존 `Snapshot(generation, modelId, placement)` 뒤에 `scene: ScreenScene? = null`, 내부 파일 디렉터리를 추가하고 `open(id: String): InputStream`을 제공한다.
- 기존 `stageFiles(root,slot,modelId,placement)` 보존. 추가 `stageFiles(root: File, slot: String, scene: ScreenScene, open: (String)->InputStream): Snapshot`.
- 추가 `beginApplication(context: Context): String`, `stage(context: Context, request: String, modelId: String, placement: VrmPlacement): String`, `stage(context: Context, request: String, scene: ScreenScene): String`, `markLaunched(context: Context, request: String)`, `finishApplication(context: Context, request: String)`.
- 추가 `pendingApplication(context: Context): PendingApplication?`; 값은 `request: String`, `slot: String?`, `generation: String?`, `launched: Boolean`. 작업 잠금과 원자적 예약 기록은 `VrmWallpaperStore` 안에서만 관리한다.

- [ ] 기존 `vrm-wallpaper` 검사에 v1 단독 JSON 호환과 v2 합성본 왕복을 추가한다. 원본 이미지/작품 변경 후에도 적용본 픽셀·순서·모델 ID가 같고, 복사 도중 예외/중단 시 이전 슬롯 JSON과 바이트가 같아야 한다.
- [ ] 신규 요청 A 뒤 B는 거절, A 종료 후 B 허용, 오래된 A 콜백이 B 예약을 해제하지 못함, 새 store 읽기에서 pending 보존, 홈/잠금 보호 슬롯 비선택을 검사한다. 테스트 전용 임시 root를 사용해 실제 적용 상태에 영향이 없게 한다.
  `reservationRejectsConcurrentAndStaleOwners`의 단언(임시 noBackupFilesDir를 제공하는 테스트 Context 사용):
  ```kotlin
  val a = VrmWallpaperStore.beginApplication(context)
  check(runCatching { VrmWallpaperStore.beginApplication(context) }.isFailure)
  VrmWallpaperStore.finishApplication(context, a)
  val b = VrmWallpaperStore.beginApplication(context)
  VrmWallpaperStore.finishApplication(context, a)
  check(VrmWallpaperStore.pendingApplication(context)?.request == b)
  VrmWallpaperStore.finishApplication(context, b)
  ```
- [ ] `vrm-wallpaper`를 실행해 신규 API/보호 동작 실패를 확인한다.
- [ ] v2에서 UUID별 폴더에 검증된 이미지 ID만 복사하고 실제 파일 MIME/제한/scene.vrm과 루트 모델 정보 일치를 검사한다. JSON 읽기는 기존 v1 8KiB 제한을 유지하고 v2는 64KiB 이내로 제한한다. 파일 복사가 모두 끝난 뒤 슬롯 JSON을 AtomicFile로 발행한다. 이전 세대 폴더는 삭제하지 않는다.
- [ ] 요청을 작업 제출 전에 예약하고 슬롯 선택도 복사 전에 기록한다. 회전/onDestroy만으로 launched 예약을 해제하지 않는다. 진행 중 작업이 끝나기 전에 취소 토큰으로 슬롯을 풀지 않는다. 손상된 예약 기록은 조용히 초기화하지 않는다.
- [ ] 복구 계약: 생성 전(prepared snapshot 없음) 중단은 명시적 취소로 예약만 해제하고 기존 슬롯 유지. 준비된 적용본은 **이전 적용 계속**으로 동일 슬롯/세대를 시스템 미리보기에 다시 열고 결과 수신 후 해제한다. 다른 요청을 자동 실행하지 않는다. 읽기/대상 확인 실패는 안내 후 보호 상태를 유지한다.
- [ ] 같은 route를 통과시켜 `feat: snapshot composed wallpapers with application reservations`로 커밋한다. 이 단계까지 새로운 UI 진입점은 활성화하지 않는다.

## Task 4: 목록 선택·배치·저장·전체 미리보기

**Files:** Modify `K/ScreenEditorActivity.kt`, `K/ScreenEditorView.kt`, `K/MediaLibrary.java`, `K/WallpaperArtwork.kt`, `K/WebViews.java`, `app/src/main/assets/gallery.js`, `app/src/main/assets/gallery.html`, 기존 `app/src/main/assets/i18n.js`; Test `I/VrmSceneChecks.kt`, `I/EditorGestureChecks.kt`, `I/EditorMediaChecks.kt`.

**Interfaces:**
- 편집기에 `selectedVrm: Boolean` 및 `modifyVrm(change: (VrmSceneLayer)->VrmSceneLayer)` 추가. 이미지/정보/2.5D 선택과 상호 배타적이다.
- 모델 목록은 기존 `VrmModelStore.entries()`/`openModel(id)`를 사용하며 선택 자체가 `store.select`/`savePlacement`를 호출하지 않는다.
- VRM 작품은 Task 2 합성 뷰, 다른 작품은 기존 `ChargingSceneView`/`WallpaperScenePreview` 사용. 구조 비교에 모델 ID와 이미지 구조를 포함한다.

- [ ] `vrm-scene` route에 모델 A/B 구분·교체 시 작품 배치 유지, 저장/재실행, 모델 누락 시 다른 작품 열기, 제스처 대상 고정, 원본 캐릭터 설정 불변을 검사한다. 기존 media116/gesture116 회귀 검사는 유지한다.
  `editingPlacementDoesNotChangeLibrarySelection`의 핵심 단언(메인 스레드에서 준비된 editor/store fixture 사용):
  ```kotlin
  val selectedBefore = store.selected()
  val imagesBefore = editor.currentDraft().scene!!.layers
  editor.selectedVrm = true
  editor.modifyVrm { it.copy(placement=it.placement.withScreenPosition(.7f,.4f)) }
  check(editor.currentDraft().scene!!.layers == imagesBefore)
  check(kotlin.math.abs(editor.currentDraft().scene!!.vrm!!.placement.screenX() - .7f) < .0001f)
  check(store.selected() == selectedBefore)
  ```
- [ ] 새 UI 검사를 실행해 목록/VRM 편집 진입이 없어 실패함을 확인한다.
- [ ] 캐릭터 목록에 2.5D/VRM을 구분해 표시하고 기존 가져오기 화면 왕복 후 목록을 새로 읽는다. 이름·짧은 ID·버전을 보여준다. VRM 이동/크기/눈 깜박임/숨김/순서/제거를 연결하고 잘못된 입력은 제한한다. 이미지 개수가 줄면 삽입 위치도 정상 범위로 맞춘다.
- [ ] 임시 저장/저장은 기존 경로를 재사용한다. 전체 미리보기 중 편집기 렌더 자원을 해제하고 복귀 시 최신 임시 저장 상태로 재생성한다. 실패·뒤로 가기·언어 변경·회전에서도 기존 저장 작품을 덮어쓰지 않는다.
- [ ] 작품 목록은 VRM 합성 작품을 명확히 표시하고 **미리보기**를 제공한다. Canvas 썸네일 URL을 VRM 작품에 보내 캐릭터 없는 결과를 보여주지 않는다. `WallpaperArtwork`의 정지 출력도 VRM 작품이면 명시적으로 거절한다. 실제 미리보기는 편집기의 공용 전체 미리보기 경로를 재사용한다.
- [ ] `vrm-scene`, `character-scene`, `media116`, `gesture116` 및 기본 Android 검사를 통과시키고 전화/태블릿 캡처를 직접 확인한다. `feat: choose VRM characters in wallpaper editor`로 커밋한다.

## Task 5: 시스템 적용·엔진·예약 복구 연결

**Files:** Modify `K/WallpaperController.kt`, `K/VrmWallpaperService.kt`, `K/VrmPreviewActivity.kt`, `K/MainActivity.java`, 필요한 `res/values*/strings.xml`; Test `I/VrmWallpaperChecks.kt`, `I/VrmSceneChecks.kt`, `T/VrmRenderHealthTest.kt`.

**Interfaces:**
- `WallpaperController.show(theme,target)`는 VRM 작품만 공용 합성 미리보기→준비 확인→Task 3의 예약/적용으로 라우팅한다. 기존 Activity result 번호와 저장 상태는 유지한다.
- 실제 컴포넌트 확인에 `VrmWallpaperStore.key(context,component)`를 포함한다. 단독 VRM 화면도 같은 예약 API를 사용한다.
- 서비스는 `snapshot.scene==null`일 때 기존 경로, 합성본일 때 `VrmSceneView`를 Presentation에 넣는다. 두 경로 모두 기존 health/epoch/Surface 소유권을 유지한다.

- [ ] `checks=vrm-scene-live` route 추가: 렌더 준비 전 적용 불가, 합성 미리보기 취소 후 기존 배경 유지, 홈/잠금 모델·이미지 분리, 작품 편집·삭제 후 적용본 유지, v1 단독 적용과 교대로 동작함을 검사한다. 시스템 적용 조작은 에뮬레이터 전용으로 제한한다.
- [ ] 예약 상태를 저장한 Activity 재생성, 복사 중 종료, 시스템 미리보기 중 프로세스 종료 뒤 동일 세대 계속, 오래된 결과 토큰 무시를 검사한다. 실패 증거를 먼저 확보한다.
  `cancelKeepsAppliedGeneration`의 핵심 단언은 실제 시스템 취소 조작 전후를 비교한다:
  ```kotlin
  val before = VrmWallpaperStore.snapshot(context, appliedSlot)
  // 새 후보 시스템 미리보기를 연 뒤 취소하고 Activity 결과를 기다린다.
  check(VrmWallpaperStore.snapshot(context, appliedSlot) == before)
  check(VrmWallpaperStore.pendingApplication(context) == null)
  ```
- [ ] 준비→미리보기→결과 사이 요청 토큰을 보존하고 명확한 준비/실패/완료/미확인 안내를 제공한다. **이전 적용 계속**은 기존 pending을 읽어 같은 컴포넌트를 열며 새 stage를 하지 않는다. 유효 적용본 없는 준비 중단은 작업이 멈춘 것을 확인한 뒤 취소한다.
- [ ] 합성 호스트의 활성 상태와 GIF 예약 갱신을 엔진 visibility에 묶는다. 숨긴 VRM에는 VRM 프레임 health 검사를 요구하지 않되 이미지/GIF는 표시한다. 실제 WebView 종료 시 crashed close, 모든 폐기 콜백에 epoch 검사를 유지한다. 수동 재시도·45초 가시 로딩 제한·최대 2회 재시도 정책을 보존한다.
- [ ] 새 라이브 route와 기존 `vrm-wallpaper`, `vrm-preview`, `vrm-preview-render`, `vrm-wallpaper-live -e seconds 180`을 통과시킨다. 합성 투명도·움직임을 실제 화면으로 확인한 뒤 `feat: apply composed VRM live wallpapers safely`로 커밋한다.

## Task 6: 회귀·성능·배포 검증

**Files:** Modify `app/build.gradle.kts`, 저장소 루트 `.github/workflows/android-debug.yml`; Create `docs/V1_20_KO.md`; 생성된 VRM 번들/테스트 보고서 검증. 개인 모델·캡처·로컬 서명키는 커밋하지 않는다.

- [ ] 앱/Actions 버전 기대값을 1.20/23으로 바꾸는 검사를 먼저 준비하고 현재 1.19/22가 그 검사에서 실패하는지 확인한다. 앱 버전, CI 검증, APK 파일명·태그·릴리스 제목을 함께 갱신한다.
  빌드 메타데이터 검사:
  ```powershell
  $apkMetadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
  if (-not ($apkMetadata.elements | Where-Object { $_.versionCode -eq 23 -and $_.versionName -eq '1.20' })) { throw 'Unexpected APK version' }
  ```
- [ ] 다음 로컬 명령을 실행한다: `npm test`, `npm run build`, `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. 기대: 웹/단위 검사 실패 0, lint 오류 0, BUILD SUCCESSFUL, 버전 1.20/23 APK.
- [ ] Android CLI로 현재 기기와 실행 화면을 확인한다. 이미 사용한 `emulator-5554`(전화), `emulator-5556`(태블릿)가 실제 연결돼 있는지 먼저 확인하고 일치하는 serial만 쓴다. `adb -s <확인한 serial> shell am instrument -w -e checks vrm-scene com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation` 및 `vrm-scene-live`/기존 routes 실행. 기대: 각 `<route> OK`, 기본 전체 검사 `V113_CHECKS_OK`, 실패 로그 없음. 새 에뮬레이터 생성·초기화는 하지 않는다.
- [ ] 두 모델 각각 JPG/투명 PNG/GIF의 앞뒤 조합과 이미지 8개/GIF 2개 상한을 실측한다. 5분 표시와 홈↔설정 10회, 화면 꺼짐/복귀, 회전, WebGL 컨텍스트 손실/렌더 프로세스 종료를 확인한다. 캡처 후 직접 열어 비교하고 호스트/WebView 프로세스 PSS와 관찰 fps, 오류를 별도 기록한다. 모델별 메모리/발열은 에뮬레이터 결과와 실기기 미확인을 구별한다.
- [ ] `graphify update .` AST-only 갱신, 변경 파일 diff/비밀·개인 자산 혼입 확인, 전체 리뷰를 수행한다. 기존 사용자 수정 파일이 본 커밋에 없음을 확인한다. `release: verify VRM scene composition v1.20`으로 관련 파일만 커밋한다.
- [ ] 기존 공개 브랜치에 push하고 해당 HEAD의 Actions 성공을 확인한다. 기존 로컬 서명과 일치하는 업데이트 APK를 동일 릴리스에 올리고 공개 다운로드 파일을 다시 받아 버전·서명·SHA-256을 비교한다. 실패 시 다운로드 완료로 보고하지 않는다. 사용자에게 휴대폰용 직접 다운로드 링크, 조합 화면 진입 경로, 검증/미확인 항목을 간단히 제공한다.

## 자체 검토 및 실행 선택

- 설계 범위와 Tasks 1–6의 대응을 확인했다. 데이터/출력 분리는 Task 1, 선택·조합은 Tasks 2/4, 안전한 적용은 Tasks 3/5, 호환·품질·배포는 Task 6에서 검사한다.
- 미래 제작기는 이번에 구현하지 않는다. 동일한 입력 스트림 등록·모델 ID·렌더 경로를 유지하고 편집 파라미터를 배경화면에 섞지 않는 것이 이번 단계의 확장성 기준이다.
- 실행 방식 추천은 **현재 세션에서 순차 구현 + 마지막 독립 리뷰**다. 변경들이 동일 데이터·합성·예약 계약에 강하게 의존하므로 인계 비용을 줄이면서 전체 검토를 유지한다. 대안은 **작업별 구현 에이전트 + 단계별 독립 리뷰**이며 검토 횟수와 토큰 사용량이 더 많다.
- 사용자에게 계획 검토와 실행 방식을 확인받은 뒤 착수한다. 이 체크리스트가 비어 있는 상태에서는 구현·검증 완료라고 보고하지 않는다.
