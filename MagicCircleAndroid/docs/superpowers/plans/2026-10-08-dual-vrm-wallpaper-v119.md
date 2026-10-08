# 두 VRM 캐릭터와 라이브 배경화면 v1.19 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이전 VRM 1.0과 새 VRM 0.x 캐릭터를 함께 보관하고, 선택한 한 캐릭터를 원래 외형의 실시간 배경화면으로 적용한다.

**Architecture:** 기존 오프라인 미리보기·검증·저장소를 확장한다. 별도 VRM WallpaperService가 엔진별 WebView → Presentation → VirtualDisplay로 그리며, 기존 업로드 배경화면의 불변 적용본/슬롯 패턴을 따른다. 충전·마법진 Canvas 경로와 개인 원본은 건드리지 않는다.

**Tech Stack:** Kotlin/Android Custom Activity 및 WallpaperService, Android AtomicFile, three-vrm 3.5.5 / three.js 0.180.0, 기존 esbuild 0.28.2, JUnit/자체 instrumentation/Node test.

**Spec:** `../specs/2026-10-08-vrm-wallpaper-v119-design-ko.md` — 실행자는 이 계획과 설계를 모두 읽는다.

## Global Constraints

- minSdk 23 유지. 배포 버전 1.19 / versionCode 22(중간 배포가 있으면 더 높은 번호).
- 브랜치 `codex/stellar-sanctuary-v112` 유지, main 병합 금지. 시작 시 git status와 기존 변경을 재확인한다.
- 개인 VRM은 noBackupFilesDir에만 보관하며 Git·공개 APK·CI artifact에 포함하지 않는다. 두 입력 원본의 설계 문서 SHA-256을 끝에 재검증한다.
- 외부 리소스/임의 file·content 접근/JS 네이티브 브리지/INTERNET 권한/새 3D 엔진/유료 API 추가 금지.
- 파일 64MiB, 이미지 64개, 개별 이미지 4096, 기하 데이터 128MiB 유지. 총 텍스처 상한 40×1024²픽셀은 실측 검증 후에만 배포한다.
- 기본값 중앙·전신·깜박임 켜짐. 위치는 화면 폭/높이의 −35%~+35%, 크기는 기본 전신의 50–150%. 유한하지 않은 값은 해당 기본값으로 복구한다.
- VRM 전용 슬롯 3개. 홈/잠금 적용은 시스템 선택 UI로만 확정하며 미리보기 취소는 현재 배경을 변경하지 않는다.
- 로딩 제한은 누적 보이는 시간 45초. 일시적 오류 재시도 간격 1초·3초, 최대 2회. 숨김/종료 중 재시도 금지. 30초 정상 렌더·명시적 재시도·새 적용본에서만 실패 횟수 초기화.
- 30fps 상한부터 측정. 화질 자동 저하·다른 모델 자동 전환 금지. 한국어/일본어/영어 유지.
- 사용자 수정 중인 SanctuaryReviewActivity.kt, 루트 build.gradle.kts, gradle-wrapper.properties, UserMagicCircleRenderer 관련 파일을 덮어쓰거나 함께 커밋하지 않는다.
- 각 작업은 관련 파일만 명시적으로 stage/commit. 전체 git add 금지. 제품 코드 변경 후 Graphify AST-only 갱신.

## Review Focus

1. 서로 다른 두 `test.vrm` 및 같은 내용 재가져오기: 덮어쓰기 없이 공존/중복 제거 → Task 2 저장소 검사.
2. 잘못된 확장·뼈·압축 이미지/픽셀 경계: 로더에 맡겨 통과시키지 않고 기존 모델 보존 → Task 1 입력 검사.
3. 가져오기 도중 취소/저장 실패 및 구버전 이전 재실행: 기존 파일/목록 보존, 중복 이전 없음 → Task 2 실패 검사.
4. 홈/잠금/시스템 미리보기 엔진 동시 존재 및 취소: 적용본과 자원 소유권 분리 → Task 4 슬롯/엔진 검사.
5. 숨김 중 로드/오류, 오래된 콜백, 화면 꺼짐 반복: 무한 재시도·숨은 렌더·새 엔진 파괴 없음 → Task 3 가시성 및 Task 4 복구 검사.

## 파일 경계와 실행 기준

아래 경로는 별도 표기 없으면 `MagicCircleAndroid/` 기준이다. `K`는 `app/src/main/java/com/yj/magiccircle/`, `T`는 `app/src/test/java/com/yj/magiccircle/`, `I`는 `app/src/androidTest/java/com/yj/magiccircle/`의 정확한 접두어다.

- 저장/검증: 기존 `K/VrmModelStore.kt`, 새 순수 값 타입 `K/VrmPlacement.kt`.
- 공통 WebView/JS: 새 `K/VrmWebView.kt`, 기존 `vrm-viewer/viewer.js`, `camera.js`, `camera.test.js`, 새 `viewer-state.js`, `viewer-state.test.js`, 기존 package.json 및 번들 `app/src/main/assets/vrm-preview/viewer.js`.
- 배경화면: 새 `K/VrmWallpaperStore.kt`, `K/VrmWallpaperService.kt`, `K/VrmRenderHealth.kt`, `app/src/main/res/xml/vrm_wallpaper.xml`, 기존 Manifest/지역화 strings.xml.
- 앱 UI: 기존 `K/VrmPreviewActivity.kt`, `K/CharacterActivity.kt`, `K/MainActivity.java`, `app/src/main/assets/gallery.html`, `gallery.js`, `i18n.js` 및 VRM 미리보기 index.html/viewer.css.
- 테스트: 기존 `I/VrmPreviewChecks.kt`, `I/V113Instrumentation.kt`; 새 `I/VrmWallpaperChecks.kt`, `T/VrmPlacementTest.kt`, `T/VrmRenderHealthTest.kt`.
- 배포: 기존 `app/build.gradle.kts`, 저장소 루트 `.github/workflows/android-debug.yml`; 새 `docs/V1_19_KO.md`에 실제 결과 기록.

명령은 Android 프로젝트에서 실행한다. JAVA_HOME은 Android Studio jbr, ADB는 Android SDK platform-tools의 adb.exe를 사용한다. 디바이스 serial은 매 실행 전 조회하여 명시하고, 실제 삼성 기기의 USB 승인이 없으면 에뮬레이터에서 계속한다. 새 테스트 프레임워크는 설치하지 않는다.

## Task 1: 두 VRM 형식을 안전하게 받아들이기

**Files:** Modify `K/VrmModelStore.kt`; Test `I/VrmPreviewChecks.kt`.

**Interfaces:** 기존 `VrmModelStore(root: File)`, `importModel(input: InputStream)`, `openModel(): InputStream?`, `hasModel(): Boolean` 유지. 같은 파일에 `enum class VrmFormat { V0, V1 }` 추가, private `validate(file: File): VrmFormat`으로 형식 반환. `MAX_TEXTURE_PIXELS = 40L * 1024 * 1024`.

- [ ] **Red:** 기존 synthetic GLB 생성 함수를 사용하여 `VRM`/`specVersion=0.0`/배열 humanBones의 정상 fixture를 추가한다. `check(runCatching { import(v0) }.isSuccess)` 및 VRM1 기존 성공을 검사한다. 잘못된 `VRMC_vrm/specVersion=0.0` 거절은 유지한다. 확장 양쪽 존재, 모르는 버전, 중복 뼈 이름/노드, 필수 뼈 누락, 인덱스 범위, 외부 URI 거절과 이전 파일 바이트 보존을 추가한다. 이미지 합 40×1024²는 허용, +1은 거절; 4097/65개/파일 64MiB+1도 거절한다.
- [ ] **Fail 확인:** `./gradlew.bat assembleDebug assembleDebugAndroidTest` 후 에뮬레이터에 두 APK를 `adb -s SERIAL install -r`로 설치하고 `adb -s SERIAL shell am instrument -w -e checks vrm-preview com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation`. 신규 VRM0 또는 합 픽셀 검사에서 `V113_CHECKS_FAILED`여야 한다.
- [ ] **구현:** 공통 파서/범위 검사 안에서 확장별 메타·필수 뼈만 분기한다. VRM0는 title/author/licenseName, VRM1은 name/authors/licenseUrl을 검증한다. 잘못된 자료를 무시하거나 알 수 없는 버전을 VRM0으로 간주하지 않는다. 총 픽셀 누적은 Long으로 계산하며 나머지 한도는 유지한다.
- [ ] **Green:** 위 build/install/instrumentation을 다시 실행하여 `vrm-preview OK`, `INSTRUMENTATION_CODE: -1` 확인. 실제 개인 모델 렌더 성공은 Task 6 전까지 주장하지 않는다.
- [ ] **Commit:** 위 두 파일만 stage, `feat: validate embedded VRM 0 and 1 models`.

## Task 2: 두 모델 보관·선택·이전 및 모델별 설정

**Files:** Modify `K/VrmModelStore.kt`, `I/VrmPreviewChecks.kt`; Create `K/VrmPlacement.kt`, `T/VrmPlacementTest.kt`.

**Interfaces:**
- `data class VrmPlacement(val x: Float=0f, val y: Float=0f, val scale: Float=1f, val blink: Boolean=true)`; `normalized(): VrmPlacement`.
- `data class VrmEntry(val id: String, val name: String, val format: VrmFormat, val sizeBytes: Long, val placement: VrmPlacement)`는 VrmModelStore.kt에 둔다.
- 저장소 `importModel(input: InputStream, displayName: String="test.vrm"): VrmEntry`, `entries(): List<VrmEntry>`, `selected(): VrmEntry?`, `select(id: String)`, `rename(id: String, name: String)`, `savePlacement(id: String, placement: VrmPlacement)`, `openModel(id: String): InputStream?`. 기존 인자 없는 openModel/hasModel은 선택 모델을 읽는 호환 래퍼로 유지한다.

- [ ] **Red:** synthetic v0/v1 두 내용을 모두 `test.vrm` 이름으로 import하고 다음을 검사한다. `check(a.id != b.id && store.entries().size == 2)`; `check(store.importModel(v0Again,"test.vrm").id == a.id)`; A→B→A 선택 후 `VrmModelStore(root)` 재생성 시 선택/목록/이름/설정 동일. malformed/읽기 실패/interrupt 및 index를 저장할 수 없는 독립 임시 fixture는 기존 선택·파일을 보존해야 한다. legacy model.vrm 이전을 두 번 호출해도 1개이며 원본 바이트가 남아야 한다.
- [ ] **Red 값 검사:** `assertEquals(VrmPlacement(), VrmPlacement(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).normalized())`; 범위 밖 x/y/scale은 −.35..+.35/.5..1.5로 clamp, blink=false는 유지. 실행 `./gradlew.bat testDebugUnitTest --tests '*VrmPlacementTest'` 및 Task 1 instrumentation; 새 API/동작 부재로 실패 확인.
- [ ] **구현:** SHA-256 소문자 64자리 ID만 파일 경로로 사용한다. 기존 root 아래 `models/<id>.vrm`와 AtomicFile `index.json` 사용. 입력 스트림은 기존 worker에서 bounded copy/검증/hash 후 확정하고 마지막에 index를 publish한다. 이름은 UI용 1~80자로 제한하며 경로로 사용하지 않는다. 같은 hash 재가져오기는 파일·기존 사용자 이름·설정을 보존한다. select/import는 배경 적용본을 바꾸지 않는다.
- [ ] **이전 구현:** index 파일이 존재하지 않고 legacy 파일이 있을 때만 원본을 읽어 새 저장본/index 생성. 원본 삭제 없음. 이후 index가 있으면 자동 재이전하지 않는다. 손상 index는 빈 목록으로 덮어쓰지 말고 복구 가능한 오류를 반환한다. 중단 후 남은 미참조 모델은 목록에 자동 노출하지 않는다.
- [ ] **Green:** 값 단위 테스트와 `vrm-preview` 검사 통과. 기존 검사는 단일 model.vrm 교체 가정 대신 openModel/선택 ID를 통해 확인하도록 갱신하되 검증 사례는 삭제하지 않는다.
- [ ] **Commit:** 위 네 파일만 stage, `feat: keep private VRM characters independently`.

## Task 3: 공통 안전한 뷰어와 두 버전 렌더/설정

**Files:** Create `K/VrmWebView.kt`, `vrm-viewer/viewer-state.js`, `viewer-state.test.js`; Modify `K/VrmPreviewActivity.kt`, 기존 viewer.js/camera.js/camera.test.js/package.json, `app/src/main/assets/vrm-preview/{index.html,viewer.css,viewer.js}`, `I/VrmPreviewChecks.kt`.

**Interfaces:**
- `enum class VrmFailure { RENDERER, CONTEXT, PAGE, MODEL, TIMEOUT }`는 VrmWebView.kt에 둔다. `VrmWebView.create(context: Context, openModel: () -> InputStream?, onFailure: (VrmFailure) -> Unit): WebView`; `url(language: String, hasModel: Boolean, wallpaper: Boolean): String`; `configure(view: WebView, placement: VrmPlacement)`.
- JS `window.vrmPreview`의 pause/resume/dispose/info 유지, `configure({x,y,scale,blink})` 추가. info는 state/frames/triangles/materials와 `metaVersion`, `failure`를 제공한다. `viewer-state.js`의 `shouldRender(hostActive, documentVisible, ready, disposed): boolean`, `normalizePlacement(value): {x,y,scale,blink}`를 실제 루프가 사용한다.
- camera.js 기존 fitDistance 유지, `placementFrame(width,height,depth,aspect,fov,placement): {distance,offsetX,offsetY}` 추가. offset은 투영된 viewport 크기 기준, 양의 y는 화면 아래로 이동한다.

- [ ] **Red:** npm test 스크립트를 `node --test camera.test.js viewer-state.test.js`로 확장. Node에서 `shouldRender(false,true,true,false) === false`, hidden/notReady/disposed도 false 검사. normalizePlacement의 기본값·한도를 Kotlin과 동일하게 검사하고 camera test에 portrait/landscape에서 35% 위치와 scale .5/1.5의 투영비를 검사한다. `npm test --prefix vrm-viewer`가 새 export 부재로 실패해야 한다.
- [ ] **렌더 수정:** metaVersion 0/1만 허용하고 `rotateVRM0(vrm)`을 로드당 한 번 적용한다. 기존 material/조명/최적화를 보존한다. 중앙 전신 기준 배율과 화면 상대 위치를 적용하고 wallpaper 모드에서는 카메라 조작·페이지 버튼·caption을 숨긴다. 앱의 전신/얼굴/자세 버튼은 유지한다. pause가 hostActive=false를 고정하고 visibilitychange는 이를 뒤집지 않게 한다. dispose는 늦게 끝난 load 결과까지 자원을 해제한다. combineMorphs는 실측 필요성이 없다면 추가하지 않는다.
- [ ] **네이티브 공유:** Activity의 기존 URL 검증/리소스 응답을 VrmWebView로 이동하고 두 호스트가 동일하게 사용한다. 리소스는 현재 허용된 local index/viewer.js/viewer.css/model.vrm뿐, 선택된 ID를 캡처한 openModel 사용. no-store, 외부 탐색/하위 요청 차단과 renderer 종료 처리를 유지한다. 쿼리/설정은 Uri.Builder·JSON 직렬화하며 사용자 문자열 JS 연결 금지.
- [ ] **Green:** `npm test --prefix vrm-viewer`와 `npm run build --prefix vrm-viewer`; build/install 후 `checks vrm-preview-screen`으로 기존 악성 URL 차단 및 죽은 WebView 재생성 확인. 렌더 검사의 motion 기본값 기대를 blink=true에 맞춰 변경한다. 모든 WebView 조작은 메인 스레드.
- [ ] **Commit:** 위 소스·테스트·생성 bundle만 stage, `feat: render both VRM formats with shared offline controls`.

## Task 4: 불변 적용본과 안전한 라이브 배경화면 엔진

**Files:** Create `K/VrmWallpaperStore.kt`, `K/VrmWallpaperService.kt`, `K/VrmRenderHealth.kt`, `T/VrmRenderHealthTest.kt`, `I/VrmWallpaperChecks.kt`, `app/src/main/res/xml/vrm_wallpaper.xml`; Modify Manifest, `app/src/main/res/{values,values-ko,values-ja}/strings.xml`, `I/V113Instrumentation.kt`.

**Interfaces:**
- `VrmWallpaperStore.stage(context: Context, modelId: String, placement: VrmPlacement): String`, `component(context: Context, slot: String): ComponentName`, `snapshot(context: Context, slot: String): Snapshot`; `Snapshot(generation: String, modelId: String, placement: VrmPlacement)` 값 타입. model ID로 Task 2 파일을 읽는다.
- 같은 store 파일의 `stageFiles(root: File, slot: String, modelId: String, placement: VrmPlacement): Snapshot`, `snapshot(root: File, slot: String): Snapshot`, `freeSlot(protectedSlots: Set<String>): String?`는 실제 개인 모델/UI 없이 저장/슬롯을 검사하는 instrumentation 경계다. key는 `vrm-slot-0`~`vrm-slot-2`만 허용.
- `VrmWallpaperStore.key(context: Context, component: ComponentName?): String?`, `retry(context: Context, slot: String, generation: String)` 추가. retry는 private SharedPreferences `vrm-retry`에 slot별 generation+UUID 요청을 기록하며, 같은 프로세스의 엔진 listener가 자기 generation과 일치할 때만 reset한다. 외부 broadcast나 exported Activity를 추가하지 않는다.
- `VrmWallpaperService : WallpaperService`에 `protected open val slot: String`; 같은 파일의 Service0/1/2가 각 slot을 지정한다. 실제 상태는 Engine별 소유이며 전역 WebView 없음.
- `VrmRenderHealth`: `setVisible(visible: Boolean, nowMs: Long)`, `newAttempt(nowMs: Long)`, `sample(ready: Boolean, frames: Long, nowMs: Long): VrmFailure?`, `onFailure(kind: VrmFailure): Long?`(재시도 지연 또는 null), `reset(nowMs: Long)`. 순수 시간 정책이며 WebView/Context를 보유하지 않는다. 새 attempt는 실패 횟수를 초기화하지 않는다.

- [ ] **Red:** health tests에서 visible 44,999ms는 timeout 없음, 숨김 60초 제외, 누적 45,000ms에서 TIMEOUT. 일시 실패 지연 1000/3000/null, MODEL/PAGE는 null. off/on만으로 재시도 회수 초기화 안 됨; 29,999ms 정상에는 회수 유지, 연속 30,000ms 프레임 진행/명시 reset은 초기화. `./gradlew.bat testDebugUnitTest --tests '*VrmRenderHealthTest'` 실패 확인.
- [ ] **Red 저장/동시성:** `VrmWallpaperChecks.run(context)`에 모든 보호 슬롯이면 null, 서로 다른 두 Snapshot은 독립, 슬롯 갱신 후 이미 캡처한 Snapshot 불변, 불법 key/ID 거절 검사 추가. 이전 generation 재시도 요청이 새 엔진에 영향을 주지 않는 검사도 추가. runner에 `checks=vrm-wallpaper` 등록, 기존 기본 VRM 검사 누락을 감안하여 이후 명령에서 명시 실행한다.
- [ ] **슬롯 구현:** noBackupFilesDir/vrm-wallpapers에 UUID generation 포함 JSON을 AtomicFile로 저장한다. stage는 존재하는 검증 모델만 참조하며 파일을 재복사하지 않는다. API34+ 홈/잠금 컴포넌트 모두 보호. 구 API/조회 예외는 기존 published slot 전부 보호하며 빈 슬롯 없으면 IOException. 서비스는 stage 완료 후에만 enable한다.
- [ ] **엔진 구현:** probe의 검증된 Presentation/VirtualDisplay 구조를 참고하되 production에 probe 패키지/개인 파일/디버그 메뉴를 복사하지 않는다. 엔진 생성 시 snapshot을 고정하여 Surface 재생성 때 다시 읽지 않고 공유 helper로 자기 WebView를 생성한다. generation guard로 늦은 load/evaluate/renderer 콜백 무시. onVisibilityChanged로 native INVISIBLE+pause와 JS pause를 함께 적용한다. onSurfaceDestroyed/onDestroy는 예약 작업→WebView→Presentation→VirtualDisplay 순으로 정리하며 framework Surface는 release하지 않는다. 엔진 폐기 시 retry listener도 해제한다.
- [ ] **복구 구현:** JS info 샘플과 onRenderProcessGone으로 오류 종류를 구분한다. health 정책의 지연은 유효 surface+visible 때만 예약하고 숨겨지면 취소/복귀 시 남은 재시도 상태를 사용한다. MODEL/PAGE 또는 회수 소진은 멈추고 앱의 재시도/모델 선택 안내를 표시한다. 설정 Activity 재시도는 현재 snapshot을 유지하며 명시 reset으로 새 renderer만 만든다. 네이티브 오류 화면은 최소 안내만, 자동 대체 캐릭터 없음.
- [ ] **Green:** unit test 및 build/install 후 `checks vrm-wallpaper` 통과. instrumentation에 old generation callback이 새 WebView를 해제하지 않는 검사를 넣는다. 실제 GPU/시스템 복구는 Task 6에서 별도 검증한다.
- [ ] **Commit:** 위 파일만 stage, `feat: add isolated VRM live wallpaper engines`.

## Task 5: 캐릭터 선택·설정·시스템 적용 화면

**Files:** Modify `K/VrmPreviewActivity.kt`, `K/CharacterActivity.kt`, `K/MainActivity.java`, gallery.html/gallery.js/i18n.js, `I/VrmPreviewChecks.kt`, `I/VrmWallpaperChecks.kt`.

**Interfaces:** Activity는 Task 2 저장소/설정, Task 3 renderer, Task 4 stage/component를 사용한다. 새 gallery 주소는 query/path/fragment 없는 `magiccircle://vrm`. 적용 중 재시도 버튼은 시스템에서 확인한 자기 서비스 component를 key로 변환한 뒤 해당 snapshot generation으로 Task 4 retry를 호출한다. 복수 적용 대상은 각각 표시하며 미확인 대상의 성공을 추측하지 않는다. VrmPreviewActivity는 non-exported를 유지한다.

- [ ] **Red UI:** `VrmPreviewChecks.screen()`에서 태그 `vrm-models`, `vrm-rename`, `vrm-settings`, `vrm-apply`를 찾고 loading/error에서 apply=false 검사. 설정 UI의 `vrm-x`, `vrm-y`, `vrm-scale`, `vrm-blink`, `vrm-reset` 값 및 저장/복원 검사. 기존 URL 차단/닫힌 Activity의 callback 검사는 유지. `checks vrm-preview-screen`에서 새 UI 부재로 실패 확인.
- [ ] **화면 구현:** 기존 Activity 하나에 모델 목록 선택, 이름 변경 dialog, 위치/크기 SeekBar와 깜박임 Switch, 수치 표시/초기화 제공. 같은 이름에는 짧은 ID, 형식과 크기를 표시한다. 실시간 썸네일 WebView는 만들지 않는다. import는 기존 단일 executor/cancellation을 유지하고 worker 완료 후 lifecycle 확인. 모든 버튼 접근성 이름/48dp 터치 영역 유지, 한/일/영 문구 제공.
- [ ] **적용 구현:** 모델 ready 및 저장 완료에만 apply 허용. immutable stage 후 ACTION_CHANGE_LIVE_WALLPAPER + EXTRA_LIVE_WALLPAPER_COMPONENT를 사용한다. picker 취소/예외에는 기존 applied 표시를 바꾸지 않는다. 돌아왔을 때 실제 시스템 component가 일치하는 대상만 확인 완료로 표시한다. lock 미확인 시 성공 추측 금지. 기존 WallpaperController/Canvas 선택 정책은 수정하지 않는다.
- [ ] **진입점 구현:** 캐릭터 관리의 preview-only 설명을 실제 지원 범위로 변경. gallery wallpaper-screen에 VRM 관리 버튼을 추가하고 기존 native/busy 및 MainActivity exact URI guard를 통과할 때만 Activity 열기. 기존 업로드/충전 버튼 흐름 보존.
- [ ] **Green:** build/install 후 `checks vrm-preview-screen`, `checks vrm-wallpaper`, 기존 전체 runner 실행. 양 방향 화면에서 overflow/슬라이더 수치/언어를 실제 캡처로 확인한다. 실제 모델 선택·취소·적용은 Task 6.
- [ ] **Commit:** 위 파일만 stage, `feat: select and place VRM characters as wallpaper`.

## Task 6: 실제 두 파일 검증·성능 측정·버전과 다운로드 제공

**Files:** Modify app/build.gradle.kts, 저장소 `.github/workflows/android-debug.yml`; Create docs/V1_19_KO.md. 발견된 결함은 해당 소유 task 파일/회귀 검사에서 원인 수정한다.

**Interfaces:** v1.19 APK는 개인 모델 없이 SAF import를 제공한다. CI는 기존 public 저장소의 무료 Actions/Release 경로만 사용한다. 변경 전 staging/diff를 확인하고 사용자 수정 파일은 제외한다.

- [ ] **실제 입력 검증:** 설계에 기재된 A/B를 에뮬레이터 Download에 서로 구분되는 테스트 파일명으로 복사하고 시스템 SAF로 각각 가져온다. 앱 목록 이름은 둘 다 test.vrm으로 설정하여 실제 중복 이름 선택을 확인한다. `checks vrm-preview-render`는 선택 모델마다 별도 실행하고 ready/metaVersion/기하/프레임을 확인한다. A→B→A와 앱 재실행 후 공존·원래 외형 유지. 전신/얼굴/정면/양팔/깜박임을 캡처·짧은 녹화로 직접 본다.
- [ ] **실제 배경 검증:** 각 모델을 phone/tablet 에뮬레이터에서 시스템 UI로 적용. 위치 극값/크기 .5·1·1.5/깜박임 off·on/회전 확인. A 적용 후 B picker 취소에도 A 유지, 홈 A+잠금 B+새 미리보기 엔진 공존 확인. OS가 특정 조합을 제공하지 않으면 store/engine 검사와 실제 미지원 결과를 구분한다.
- [ ] **생명주기 검증:** 홈↔Settings, screen off/on, 앱 최근 목록 제거, 반복 A/B 전환, renderer 종료 및 WEBGL_lose_context fault injection을 에뮬레이터에서 수행. callback 직접 호출 검사와 실제 종료 시험을 구분한다. 재시도 1초/3초/상한, 숨김 frames 정지, Surface 재생성 뒤 회복, 파괴된 엔진/VirtualDisplay 잔존 여부를 로그로 확인한다.
- [ ] **성능 검증:** 모델별 안정화 후 5분 및 10회 전환에서 앱+WebView renderer PSS와 프레임을 기록한다. peak/안정값/회수 상태를 보고하고 GPU/시스템 비용은 별도임을 명시한다. OOM/context 반복 실패 또는 매 전환의 지속 증가면 배포 보류·원인 수정; 조용한 텍스처 축소는 하지 않는다. 물리 삼성 기기는 authorized일 때만 별도 확인, 결과를 에뮬레이터와 혼동하지 않는다.
- [ ] **버전/CI 수정:** app versionName 1.19/versionCode 22를 설정하고 workflow metadata 기대값/릴리스 asset 이름을 함께 변경. Node 검사에 viewer-state.test.js를 포함하고 기존 unit/lint/APK 작업 유지. 기존 로컬 사용자 Gradle 변경은 stage하지 않는다.
- [ ] **최종 검증:** `npm test --prefix vrm-viewer`, `npm run build --prefix vrm-viewer`, `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. 다시 설치하여 vrm-preview / vrm-preview-screen / vrm-preview-render(각 모델) / vrm-wallpaper 및 전체 runner 실행. 원본 SHA-256 일치, APK archive에 .vrm 없음, permission INTERNET 없음 확인. 실패가 하나라도 있으면 성공으로 기록하지 않는다.
- [ ] **검토와 커밋:** 제품 코드 Graphify AST-only update, 전체 diff 보안/자원/사용자 변경 분리 검토. Native 선택 시 여기서 새 독립 리뷰어가 전체 작업을 검토한다. docs/V1_19_KO.md에 기기/API·화면·메모리/프레임·성공/미확인 구분 기록 후 본 작업 파일만 commit: `release: verify dual VRM wallpaper v1.19`.
- [ ] **공개 배포:** 기존 branch push 후 해당 commit의 Actions 성공을 확인한다. CI debug 서명과 별도로 기존 앱과 호환되는 로컬 update APK를 버전/서명/SHA-256 검증 후 동일 Release에 올린다. 공개 asset을 다시 다운로드해 hash를 대조한다. 개인 모델이나 모델 포함 화면/기록을 공개 artifact에 넣지 않는다. 최종 보고는 실제 Release의 직접 APK 링크와 두 모델/실기기 미확인 사항만 간결히 제공한다.

## 자체 검토 및 실행 승인

설계 1–4→Task 1/3, 5→Task 2, 6→Task 3/5, 7–8→Task 4/5, 9→Task 6, 10→파일 경계로 대응한다. 선택/적용 분리, 기존 파일 보존, 언어, 시스템 승인, 가시성/재시도 값과 형식별 실패 검사를 각 소유 작업에 배치했다.

권장 실행은 **Native**: 주 에이전트가 공유 인터페이스를 따라 구현하고 마지막에 독립 리뷰어가 전체를 확인한다. 대안은 **Subagent-driven**: 각 작업마다 새 구현 에이전트와 리뷰어를 배치한다. 이 문서는 계획이며 구현/두 모델 실동작/새 APK 배포 완료를 뜻하지 않는다. 사용자 계획 검토와 실행 방식 선택 뒤 구현을 시작한다.
