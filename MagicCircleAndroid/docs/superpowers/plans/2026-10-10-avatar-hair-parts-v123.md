# 독립 헤어 파츠 선택 v1.23 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** E 기본/Hair02를 실제 독립 헤어 파츠로 등록하고, 사진 목록에서 고른 헤어만 교체하여 캐릭터와 배경화면에 저장한다. 향후 눈·입·얼굴형도 같은 선택 흐름을 사용한다.

**Architecture:** 기존 E VRM에서 헤어 소유 데이터만 분리한 뒤 Android의 파일/JSON 처리로 기준 E와 조합한다. 검증 완료된 결과를 기존 VRM 저장소·WebView 렌더러에 연결하고, 외형 v2에는 부위 선택을 별도로 기록한다. 기존 v1 저장본과 배경화면은 변환 없이 계속 읽는다.

**Tech Stack:** Kotlin/Java, Android Views, `org.json`, `AtomicFile`, `RandomAccessFile`, 기존 Three.js 0.180.0 / three-vrm 3.5.5, Node 내장 테스트, JUnit 4.13.2, 기존 Android instrumentation. 추가 제품 의존성 없음.

**Spec:** `../specs/2026-10-10-avatar-part-selection-design-ko.md` — 2026-10-10 사용자가 “진행해”로 문서 승인. 이 계획 자체는 아직 사용자 검토 전이다.

## Global Constraints

- “이번 구현 대상은 실제 자료가 있는 E 기본 헤어와 Hair02 두 종류다.” 앞/뒤머리는 하나의 스타일이며 다른 부위의 새 3D 자료는 만들지 않는다.
- “모양을 바꿔도 사용자가 지정한 색상은 유지한다.” 원본 색상 `null`과 지정 색상 대문자 `#RRGGBB`를 구별한다.
- “원본 이미지 바이트와 텍스처 해상도를 유지한다.” 사진 목록에만 작은 이미지를 사용한다.
- “기존 `profileVersion: 1` 캐릭터, 임시 저장, 이미지 합성 장면, 배경화면은 계속 읽는다.” 새 형식은 `profileVersion: 2`, `parts.hair`이며 기존 `hair`/`iris` 색상 키는 필수다.
- “두 완성 캐릭터를 동시에 GPU에 올려 전환하지 않는다.” 실패하면 마지막 정상 상태와 저장 자료를 유지한다.
- “사용자 모델·파츠·텍스처·개인 미리보기는 Git/APK/공개 Release에 포함하지 않는다.” 유료 API/외부 모델 업로드 금지.
- 기존 `minSdk=23`, `compileSdk=37`, `targetSdk=37`, `applicationId=com.yj.magiccircle` 유지. 배포 목표는 `versionName=1.23`, `versionCode=26`; 실행 시 더 최신 배포가 있으면 그보다 올린다.
- 기존 `codex/stellar-sanctuary-v112`에서 관련 변경만 다룬다. main 병합·기존 WIP 포함·원본 파일 삭제·개인 배경화면 덮어쓰기·기기 초기화 금지.
- 실행 시작 시 Git 상태와 스펙을 다시 확인한다. 현재 AGP/Gradle 변경, debug 화면, 과거 APK 삭제 및 untracked 파일은 이번 작업 소유가 아니다.
- Windows 로컬 명령은 PowerShell 기준이며 Gradle/Node/adb는 실제 설치 경로를 확인한다. 기기는 확인한 emulator serial을 반드시 지정한다.

## Review Focus

1. Body 안의 뒤머리와 피부가 섞인 자료: 헤어만 제거하고 보호 primitive·정점·텍스처를 유지해야 한다 → Task 1/2의 `bodyEmbeddedHairRoundTrip`.
2. 정상 파일명인데 내용이 변조되거나 donor 전체 모델인 자료: 해시·참조·기준 모델 검증 없이 등록/염색되면 안 된다 → Task 2/3의 `tamperedPartAndReceiptRejected`.
3. 첫 저장/회전/연속 선택 중 오래된 작업 완료: 마지막 선택만 확정되고 기존 초안과 revision을 잃지 않아야 한다 → Task 4의 `latestSelectionAndLifecycle`.
4. 기존 v1 작품이 살아 있는 상태에서 조합 모델 추가: 전역 선택·원래 작품·배경화면이 바뀌지 않아야 한다 → Task 3/5의 `derivedImportDoesNotSelect`와 `legacySnapshotStable`.
5. 저장 공간/메모리 부족 또는 중단된 쓰기: 품질 저하나 부분 게시 없이 실패하고 이전 결과를 다시 열 수 있어야 한다 → Task 2/3의 `boundedAssemblyAndAtomicFailure`.

---

## 경로·호출 관계

이하 경로는 `MagicCircleAndroid/` 기준이다. `main`, `unit`, `device`는 각각 `app/src/main/java/com/yj/magiccircle/`, `app/src/test/java/com/yj/magiccircle/`, `app/src/androidTest/java/com/yj/magiccircle/`의 약기다.

| 파일 | 책임 |
|---|---|
| 새 `main/VrmHairPart.kt` | 헤어 전용 문서·바이너리 형식, 연결 참조 검증 |
| 새 `main/VrmHairAssembly.kt` | 제한된 E 자료의 소유권 판정, 추출/재조합/보호 지문 검사 |
| 새 `main/VrmHairPartStore.kt` | 앱 내부 파츠 등록, 검증 영수증, 조합 결과 캐시 |
| 기존 `main/VrmAvatarDefinition.kt`, `VrmAvatarStore.kt` | v1/v2 외형과 기존 저장·초안·revision 계약 |
| 기존 `main/VrmModelStore.kt` | 선택 부작용 없는 파생 모델 게시; 원래 가져오기 유지 |
| 기존 `main/VrmAvatarActivity.kt` | 사진 목록/색상, 선택 완료·취소·복구 |
| 기존 `vrm-viewer/avatar-dye.js`, `viewer.js`, `main/VrmWebView.kt` | 검증된 v2 결과의 색상/초기 렌더링 |
| 기존 `main/ScreenScene.kt`, `VrmSceneView.kt`, `VrmSceneDialog.kt` | 저장 당시 외형과 실제 모델 일치, 다른 모델이면 뷰 재생성 |
| 기존 `main/MediaLibrary.java`, `UploadedWallpaperStore.kt`, `VrmWallpaperStore.kt`, `VrmWallpaperService.kt` | 이미지 합성과 배경화면의 기존 불변 외형 보존 |

### 결정한 파츠 저장 형식

앱 내부 전용 디렉터리 `noBackupFilesDir/vrm-hair-parts/parts/<partId>/`에 `part.json`과 `part.bin`을 저장한다. 사용자에게 ZIP을 풀게 하거나 별도 파일 두 개를 수동 선택하게 하지 않는다. 기존 VRM 가져오기로 등록한 자료에서 앱이 추출한다.

- `part.json`: `schemaVersion=1`, `kind="hair"`, `styleId`(`e-original`/`e-hair02`), `baseModelId`, `sourceModelId`, 보호/헤어 지문, 필요한 geometry/material/texture/skin/node/spring tables, anchor 참조, BIN 길이/해시.
- `part.bin`: 참조되는 헤어 정점/인덱스/바인드 행렬/원본 인코딩 이미지 바이트만 포함한다. 얼굴·몸·옷 geometry나 원본 VRM 통째 복사 금지.
- 노드 참조는 로컬 헤어 노드와 기준 모델의 canonical node path anchor를 구별한다. 기준 뼈·충돌체는 복사하지 않고 참조한다. Body에 있던 헤어는 대상 mesh의 경로와 primitive 자료로 기록한다.
- skin은 실제 양의 weight가 사용하는 joint 및 skeleton 연결을 보존한다. 0-weight slot은 안전한 joint로 정규화한다. 공통 뼈에 대한 inverse-bind 연결은 기준과 비교한다.
- `partId = SHA-256(part.json 실제 UTF-8 바이트 || part.bin 실제 바이트)`; JSON 안에는 자기 자신의 partId를 넣지 않는다. 게시 후 바이트를 바꾸지 않는다.
- JSON ≤4 MiB, JSON+BIN ≤64 MiB, JSON 깊이 ≤64, 노드 계층 깊이 ≤128, decoded geometry ≤128 MiB, 비교 작업량 ≤16×1024×1024 scalar. 기존 검사기의 배열/확장/재질 allowlist 제한도 유지한다. URI·외부 버퍼·범위 초과·중복/순환 노드·미지원 형식은 거부한다.
- 새 임시 디렉터리의 두 파일을 fsync·검증한 뒤 게시한다. 폴더명이 사용자 경로가 되지 않도록 64자 소문자 hex만 허용한다. 중단된 임시 파일만 정리하며 게시된 자료를 자동 삭제하지 않는다.

제품 등록 허용 source는 E 기본 `ef6513de66aee3ab78b105e53b2e72c5d92834fc2a49c542221c08ec9f0811d0`, Hair02 원본 `f4df98833a830f84c6f8bcdb90701e86420bc2fe369e7936fff1cf3b0971573e`, 기존 검증 조합 `c1853aa3c22b5c3b58ba8f819c4b2c4e7bd318aeeefaa9739f7c9f3bb205a708`이다. 기준은 항상 E 기본이다. 두 Hair02 source에서 동일 파츠가 나와도 내용 해시가 다르면 별도 등록 자료로 취급하되 목록에서는 한 스타일로 표시하고, 새 선택에는 원본 Hair02 출처를 우선한다. 기존 저장 ID는 바꾸지 않는다.

## 실행 명령 기준

`MagicCircleAndroid/`에서 `npm --prefix vrm-viewer test`, `npm --prefix vrm-viewer run build`, `.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`를 사용한다. SDK/의존성이 없는 경우 부족한 항목을 확인하고 과금 없는 공식 경로만 사용한다.

아래 기기 검사는 `adb -s <확인한-emulator-serial> shell am instrument -w -e checks <검사명> com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation`으로 실행한다. 성공은 출력 `<검사명> OK`와 `INSTRUMENTATION_CODE: -1`을 모두 확인한다. 실행 없이 통과했다고 기록하지 않는다.

## Task 1: 헤어 파츠 형식과 비교 기준 고정

**Files:** Create `vrm-viewer/tools/hair-part.mjs`, `tools/hair-part.test.mjs`, `tools/export-hair-part-fixtures.mjs`, `app/src/androidTest/assets/hair-parts/`의 합성 fixture. Modify `tools/compare-hair-source.mjs`, `tools/assemble-hair.test.mjs`는 실제 사용 joint 비교가 필요한 최소 부분만. 모든 `tools/`는 `vrm-viewer/` 하위다.

**Interfaces:**
- `extractHairPart(sourceBytes: Uint8Array, baseBytes: Uint8Array) -> {json: Uint8Array, bin: Uint8Array, partId: string}`
- `inspectHairPart(json: Uint8Array, bin: Uint8Array) -> {partId, styleId, baseModelId, sourceModelId, protectedDigest, hairDigest, protectedPrimitiveCount, anchors, imageHashes}`; 입력 모두 검증. count는 알려진 보호 재질 primitive 수, anchors는 정렬한 기준 node path 목록, imageHashes는 참조 이미지 SHA-256 목록이다.
- 기존 `readHairSource`, `hairOwnership`, `signatures`, `assembleHair`를 기준으로 사용한다. 출력은 Node에서도 Android와 같은 파츠 형식이다.

- [ ] **RED 테스트 작성.** 기존 `hairRigFixture()`에서 피부+뒤머리 혼합, 헤어 전용 뼈, 귀·꼬리 spring, 공통 collider, 공유 PNG 및 얼굴 morph를 가진 합성 자료를 만든다. `bodyEmbeddedHairRoundTrip`: 검사 결과에 `assert.equal(part.protectedPrimitiveCount, 0)`, `assert.deepEqual(part.anchors, expectedAnchors)`, `assert.deepEqual(part.imageHashes, expectedImageHashes)`를 적용하고 0-weight 잘못된 joint가 보호 뼈를 건드리지 않음을 검증한다. 같은 스타일의 원본→원본 추출도 지원하며 기존 `candidate` 조건에 막히지 않게 테스트한다.
- [ ] `node --test tools/hair-part.test.mjs`를 실행하여 아직 없는 추출/검사 기능으로 실패하는지 확인한다.
- [ ] 위 인터페이스를 구현한다. 구조 추출 함수는 합성 fixture도 검사할 수 있게 하되 앱의 등록 경계는 알려진 3개 전체 해시만 허용한다. fixture 우회 플래그를 제품 등록 API에 만들지 않는다. skin 정규화 때문에 비교 기준을 조정한다면 양의 weight와 inverse-bind·spring 지문 보호를 유지하며 전체 joint 개수만으로 동등하다고 판단하지 않는다.
- [ ] `assert.throws`로 외부 URI, out-of-range/overflow, 잘린 BIN, 과도한 JSON 중첩, 혼합 보호 spring, 얼굴 변경 donor, 잘못된 헤어 지문, 재질 이름만 복제한 미지원 모델을 거절하는 테스트를 추가한다.
- [ ] `node --test tools/hair-part.test.mjs tools/assemble-hair.test.mjs`를 통과시킨다. exporter로 합성 fixture와 예상 의미 지문을 Android test assets에 저장하고 재생성 바이트 동일성을 확인한다. 개인 E 자료의 실제 추출/비교 보고서는 Git 밖에만 보관한다.
- [ ] 이 Task의 도구·합성 테스트 자료만 커밋한다: `test: define independent hair part contract`.

## Task 2: Android의 실제 헤어 추출·재조합

**Files:** Create `main/VrmHairPart.kt`, `main/VrmHairAssembly.kt`, `device/VrmHairAssemblyChecks.kt`; Modify `main/VrmModelStore.kt`의 private 구조 검사 재사용 부분, `device/V113Instrumentation.kt`. Node 원본 코드의 검사 규칙은 Task 1 기준으로 유지한다.

**Interfaces:**
- `data class VrmHairPart(val id: String, val styleId: String, val baseModelId: String, val sourceModelId: String, val directory: File)`
- `data class VrmHairAssemblyResult(val file: File, val modelId: String, val partId: String, val protectedDigest: String, val hairDigest: String)`
- `VrmHairAssembly.extract(base: File, source: File, outputDirectory: File, budgetBytes: Long): VrmHairPart`
- `VrmHairAssembly.compose(base: File, part: VrmHairPart, output: File, budgetBytes: Long): VrmHairAssemblyResult`
- `VrmHairPartCodec.read(directory: File): VrmHairPart`; 모듈 내부에서 manifest/BIN 검증 후 읽는다. Android `org.json`이 필요한 검사는 실제 instrumentation에서 실행한다.

- [ ] **RED 검사 작성.** `vrm-hair-assembly`를 runner에 등록한다. 합성 fixture로 `bodyEmbeddedHairRoundTrip`의 `check(protectedBefore == protectedAfter)`, `check(hairAfter == donorHair)`, `check(imageBytesBefore.contentEquals(imageBytesAfter))`를 고정한다. 뒤머리 누락, 사용 joint/행렬/표정/공통 collider와 귀·꼬리 spring 보존을 각각 확인한다.
- [ ] 테스트 APK 빌드·확인된 emulator 설치 후 `checks=vrm-hair-assembly`가 의도한 미구현 검사로 실패함을 확인한다.
- [ ] extractor/assembler를 구현한다. `RandomAccessFile`과 bounded primitive 배열로 필요한 범위만 읽고 인코딩 이미지는 32 KiB 복사 버퍼로 전달한다. 기존 GLB 검증을 호출 가능한 내부 함수로 추출할 경우 모든 기존 import 검사를 그대로 보존한다. 입출력 원본은 읽기 전용이며 output은 새 임시 파일만 허용한다.
- [ ] 노드·accessor·material·texture·skin 참조를 재배치하고 Body는 보호 primitive를 남긴 채 헤어만 교체한다. 기준의 표정/first-person/VRM metadata/보호 spring을 유지하고 헤어 spring만 바꾼다. 생성 결과의 구조 및 의미 지문 검증 전에는 등록하지 않는다.
- [ ] `boundedAssemblyAndAtomicFailure`: `budgetBytes=0`이면 출력 게시 전 거절, geometry/JSON 한도 초과·중단 플래그·쓰기 실패에 이전 파일 불변을 검사한다. 예상 working set에는 두 JSON의 객체 여유, 최대 활성 primitive의 index/attribute 배열 및 복사 버퍼를 포함한다. 전부의 텍스처 디코딩이나 전체 accessor 무제한 캐싱을 하지 않는다.
- [ ] 같은 검사를 PASS로 만든 뒤 실제 E 2종 결과를 로컬로 회수하여 Task 1의 Node 의미 검사와 대조한다. 원본 hash·해상도·보호 지문을 기록하고 private 결과는 커밋하지 않는다. 관련 소스와 합성 fixture만 `feat: assemble independent hair parts on Android`로 커밋한다.

## Task 3: 선택 부작용 없는 파츠 등록과 v1/v2 저장 연결

**Files:** Create `main/VrmHairPartStore.kt`, `device/VrmHairPartStoreChecks.kt`; Modify `main/VrmModelStore.kt`, `VrmAvatarDefinition.kt`, `VrmAvatarStore.kt`, `unit/VrmAvatarRulesTest.kt`, `device/VrmAvatarStorageChecks.kt`, `V113Instrumentation.kt`.

**Interfaces:**
- `VrmModelStore.registerDerived(input: InputStream, displayName: String): VrmEntry`: 기존 private 검증/복사 공유, `selected` 불변. `importModel`은 기존 동작 유지.
- `VrmHairPartStore.get(context: Context): VrmHairPartStore`; 테스트용 생성자 `(root: File, models: VrmModelStore)`.
- `prepare(budgetBytes: Long): List<VrmHairPart>`: 기존 등록 모델 중 검증된 E 3개 hash만 찾아 미등록 파츠 준비; 없으면 빈/부분 목록.
- `compose(partId: String, budgetBytes: Long): VrmHairReceipt`: 등록된 part 검증 → Task 2 → 파생 모델 게시 → 영수증 게시.
- `data class VrmHairReceipt(val modelId: String, val baseModelId: String, val partId: String, val styleId: String, val assemblerVersion: Int, val protectedDigest: String, val hairDigest: String)`
- `resolve(appearance: VrmAvatarAppearance): VrmHairReceipt?`: v1은 null; v2는 matching receipt/파츠/출력 해시 검증에 성공해야 반환, 없거나 손상되면 실패.
- 영수증은 `noBackupFilesDir/vrm-hair-parts/receipts/<modelId>-<partId>.json`에 저장한다. `modelId`가 같아도 출처/파츠 ID가 다른 경우 덮어쓰지 않는다. 루트 index는 `AtomicFile`로 관리한다. 새 조합 자료는 일반 VRM 보관함에 노출하지 않도록 기존 `VrmEntry`에 기본값 false의 `derived` 표시를 추가하고 UI 목록에서만 제외한다. 내부 ID 조회와 기존 가져온 자료의 분류는 유지한다.
- `VrmAvatarAppearance`의 기존 인자를 유지하고 마지막에 `parts: Map<String,String> = emptyMap()` 추가. v2 JSON은 `parts.hair` 필수이며 `hairId`는 `styleId`와 일치하는 기존 명칭으로 유지한다. 아직 지원하지 않는 `eyes/mouth/face`나 알 수 없는 키는 거절한다. 변경 시 map을 복사하여 불변 snapshot을 유지한다.

- [ ] **RED unit 테스트 작성.** `colorsRemainIndependent`는 `assertEquals(before.dye, after.dye)`, `v1StillValid`는 기존 Kotlin 외형 값의 검증 통과, `v2RequiresPart`는 빈/알 수 없는 parts와 잘못된 model hash를 거절하도록 한다. `profileVersion=2` 일괄 거절 테스트를 이 조건으로 교체한다. JSON round-trip은 아래 instrumentation에서 검사한다.
- [ ] `.\gradlew.bat --offline testDebugUnitTest --tests '*VrmAvatarRulesTest'`에서 의도한 FAIL 확인.
- [ ] 위 API와 v2 codec/검증을 구현한다. 파일 크기·입력 해시 확인 및 atomic 게시 재사용. 캐시 key는 기준 hash+partId+assemblerVersion이며 색상은 제외한다. `registerDerived`는 일반 VRM 선택을 바꾸지 않는다. 기존 일반 모델과 같은 해시가 나오면 일반 항목을 내부용으로 재분류하지 않는다. 기존 파일이 같은 크기라도 전체 해시가 다르면 재사용하지 않는다.
- [ ] `vrm-hair-storage` instrumentation: `derivedImportDoesNotSelect`, `tamperedPartAndReceiptRejected`, v1/v2 JSON round-trip 동일, 중복 등록 동일 결과, source 누락 안내, 영수증 게시 전 강제 실패, 저장/초안 revision 충돌 및 이전 bytes 보존을 테스트한다. 기준 E가 등록되지 않은 빈 보관함의 조합 요청은 게시 전에 거절하여 기존 selected/index 불변조건을 유지한다. AtomicFile 실패 주입은 기존 저장 검사 패턴을 사용한다.
- [ ] 현재 VrmModelStore에는 모델 삭제/lease API가 없으므로 새 자동 GC를 도입하지 않는다. 게시된 원본·파츠·파생 모델·영수증은 `noBackupFilesDir`에 보존하고 앱 cache clear의 대상이 되지 않게 한다. 현재 작업이 만든 미게시 temp와 사진 캐시만 정리한다. 이미지 lease가 VRM을 보호한다고 가정하지 않는다.
- [ ] unit 검사와 `checks=vrm-hair-storage`, `checks=vrm-avatar-storage`를 PASS로 확인하고 관련 파일만 `feat: persist compatible avatar part selections`로 커밋한다.

## Task 4: 사진형 선택 화면과 안전한 미리보기

**Files:** Modify `main/VrmAvatarActivity.kt`, `VrmWebView.kt`, `vrm-viewer/avatar-dye.js`, `avatar-dye.test.js`, `viewer.js`, `app/src/main/assets/vrm-preview/viewer.js`; Create `device/VrmHairPartEditorChecks.kt`; Modify `device/VrmAvatarEditorChecks.kt`, `VrmAvatarSaveChecks.kt`, `V113Instrumentation.kt`.

**Interfaces:**
- Native `VrmWebView.create`의 기본 호환을 유지하고 `initialPartReceipt: () -> VrmHairReceipt? = {null}`를 추가한다. 고정 로컬 `/vrm-preview/part-profile.json`만 제공한다. 외부 파일 접근/네트워크 정책은 그대로다.
- JS `normalizeAppearance(value)`는 v1/v2 구조를 검사한다. `avatarProfile(modelId, receipt = null)`는 v1 고정 해시 또는 native에서 검증한 v2 receipt의 정확한 기준/모델/파츠/style 조합만 허용한다. `bindAvatarDye`도 이 검증된 profile을 사용한다.
- v2 초기 표시 전에 appearance+receipt가 일치해야 한다. 이전 세대 응답은 적용하지 않으며 `info.appearance`에는 실제 표시한 외형만 기록한다. 현재 WebView에서 다른 `modelId`를 제자리 변경하는 금지는 유지한다.
- Activity 선택 entrypoint는 `selectHairPart(partId: String)`이며 Task 3의 compose/resolve를 사용한다. 기존 단일 IO executor를 재사용하고 작업 세대 번호로 취소/오래된 결과를 무시한다.

- [ ] **RED JS 테스트 작성.** native receipt 없는 v2, 다른 modelId/partId/styleId, donor 전체모델에 대한 `assert.throws`; v1 호환 및 `assert.equal(after.hair, before.hair)`, `assert.equal(after.iris, before.iris)`로 독립 색상 값을 검사한다. `npm --prefix vrm-viewer test`로 실패 확인.
- [ ] 외형 정규화와 정확한 재질 목록/홍채 마스크 적용을 갱신한다. 준비된 source profile을 공유하되 임의 재질명/임의 해시를 승인하는 우회는 만들지 않는다. `npm --prefix vrm-viewer test`를 PASS로 만든 뒤 bundle을 재생성한다.
- [ ] **RED 기기 검사 작성.** `vrm-hair-editor`에 헤어 2종 사진 선택, 금색 테두리+선택 접근성 표시, hair/RGB/HEX와 eyes 독립성, 기본 헤어 복귀, 사용 불가 부위 안내, 다른 저장 캐릭터 불변을 넣고 의도한 실패 확인.
- [ ] 화면을 구현한다. 모바일 세로 배치는 미리보기→부위→헤어 사진→색상, 넓은 화면은 목록/색상을 나란히 놓는다. 눈 탭은 기존 홍채 편집을 제공하고 모양 준비 상태를 별도로 표시한다. 입/얼굴형은 준비 상태 설명만 제공한다. 고정 한국어/일본어/영어 선택 규칙을 유지한다.
- [ ] 사진은 고정 얼굴 카메라/조명/원본 색상으로 192×224 렌더링하고 partId+기준 hash+사진 버전으로 캐시한다. 한 renderer에서 직렬 생성하며 기존 편집 상태/색상을 복원한다. 사용자 선택이 시작되면 사진 생성보다 우선한다. 실제 사진이 없으면 준비 중 상태를 표시하며 다른 스타일의 사진을 재사용하지 않는다.
- [ ] 선택 중에는 현재 정상 외형과 후보를 분리한다. 최신 후보가 실제 frame/appearance 준비 검사를 통과한 뒤만 외형을 확정하고 초안을 저장한다. 저장 중 모든 입력 잠금, 잘못된 HEX의 전환 차단, 회전/복귀 시 저장 revision 재확인을 유지한다. 로딩 실패 시 마지막 정상 모델로 되돌리고 저장/초안은 건드리지 않는다.
- [ ] `latestSelectionAndLifecycle`: 지연된 A→B→A 완료 순서, 연속 20회 교환, 선택 중 회전/뒤로 가기/정지·복귀, 실패 후 재시도, 첫 저장 draft 정리, 저장 중 입력을 검사한다. 두 renderer를 동시에 유지하지 않고 원본 texture dimensions가 불변인지 확인한다. `checks=vrm-hair-editor`와 기존 `vrm-avatar-save`의 identity/busy/rotation/resume 모드를 PASS로 확인한다.
- [ ] phone/tablet 화면 캡처를 직접 열어 실제 헤어·선택 표시·색상·전신/얼굴 구도를 확인한다. 테스트 결과와 관련 소스만 `feat: select hair parts from portrait gallery`로 커밋한다.

## Task 5: 저장 캐릭터·이미지 합성·배경화면 회귀 보존

**Files:** Modify `main/ScreenScene.kt`, `VrmSceneView.kt`, `VrmSceneDialog.kt`, `VrmWallpaperStore.kt`, `VrmWallpaperService.kt`, `MediaLibrary.java`, `UploadedWallpaperStore.kt` 중 실제 v2 전달/검증이 필요한 호출자; Modify `device/VrmAvatarSceneChecks.kt`, `VrmAvatarStorageChecks.kt`, `V113Instrumentation.kt`. 수정 전 모든 `VrmWebView.create`, `VrmAvatarRules`, `updateScene`, `openModel` 호출자를 검색한다.

**Interfaces:** 기존 `VrmAvatarStore.save(value, expectedRevision)`, `MediaLibrary.saveScene(scene, info)`, `UploadedWallpaperStore.stage`/snapshot 저장 구조를 재사용한다. 장면의 avatar는 전체 외형 복사본이다. `modelId` 변화는 VrmSceneView 재생성, 같은 modelId의 위치/색상 변화만 기존 update 경로를 사용한다. v2 renderer 생성 시 Task 3의 `resolve` 결과를 Task 4의 WebView callback에 전달한다.

- [ ] **RED `legacySnapshotStable` 검사 작성.** v1 캐릭터/초안/이미지 작품/배경화면 metadata bytes를 저장해 두고 v2 헤어 생성·저장 뒤 `check(before.contentEquals(after))`를 요구한다. v2 작품은 저장 뒤 원본 캐릭터의 헤어/색상을 바꿔도 `check(savedScene.vrm!!.avatar == originalSnapshot)`이어야 한다.
- [ ] `checks=vrm-avatar-scene`에서 v2를 전달하지 못하는 실패를 확인한다.
- [ ] snapshot/renderer 전달과 저장 전 검증을 연결한다. 사용자가 작품에서 캐릭터를 다시 선택할 때만 새 외형을 복사한다. 누락된 모델·영수증은 오류로 안내하고 다른 기본 캐릭터로 대체하지 않는다. 기존 null appearance/일반 VRM 흐름도 그대로 동작해야 한다.
- [ ] 저장소의 기존 모델 선택과 배경화면 home/lock/pending 상태를 검사 전후 비교한다. 기존 이미지 generation/lease는 유지하고 VRM 보호를 대신한다고 해석하지 않는다. 실제 개인 시스템 배경화면은 덮어쓰지 않고 별도 snapshot/미리보기 경로로 검증한다.
- [ ] 앱 재시작 후 `vrm-avatar-scene-reopen`의 실제 fixture 경로를 전달하여 헤어/색상/이미지 배치 일치를 확인한다. 삭제/누락된 v2 proof에 대해 안전하게 실패하고 v1 작품은 여전히 열리는지 검사한다. 관련 변경만 `feat: retain part selections in wallpaper snapshots`로 커밋한다.

## Task 6: v1.23 검증·APK 배포

**Files:** Modify `app/build.gradle.kts`, `../.github/workflows/android-debug.yml`; Create `docs/V1_23_KO.md`. 다운로드용 APK/checksum은 `releases/`에 두되 개인 자료와 서명 키는 포함하지 않는다.

- [ ] 현재 배포 버전을 읽고 목표 `1.23 / 26` 또는 그보다 높은 값을 확정한다. Gradle 값과 workflow의 metadata assert, APK/checksum 파일명, tag/title을 함께 갱신한다.
- [ ] `npm --prefix vrm-viewer test`, `npm --prefix vrm-viewer run build`, `.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 실행. 보고서의 실제 test 수·failure/error·lint 결과를 확인한다.
- [ ] `adb devices -l`로 serial을 재확인하고 API36 phone/tablet에 기존 앱 위 업데이트 설치한다. 새 `vrm-hair-assembly`, `vrm-hair-storage`, `vrm-hair-editor` 및 기존 `vrm-avatar-storage`, `vrm-avatar-save`, `vrm-avatar-scene`, `vrm-avatar-scene-reopen`을 모두 실행한다. 앱 로딩 테스트만으로 파츠 기능 합격을 대신하지 않는다.
- [ ] 실제 E 정면/측면/뒤쪽과 고개 움직임·깜빡임·헤어 물리를 캡처하여 확인한다. 원본 SHA·텍스처 해상도·개인 배경 보존·메모리/오류 로그를 기록한다. 시스템 배경화면 실제 적용과 Samsung 실기기에서 확인하지 않은 항목은 따로 명시한다.
- [ ] `graphify update .`를 저장소 루트에서 AST-only로 실행한다. 생성 그래프를 무심코 stage하지 않는다. 독립 에이전트의 전체 변경 검토를 받고 실제 오류를 수정/재검증한다.
- [ ] 관련 변경만 기존 작업 브랜치에 커밋·게시한다. 공개 저장소/무료 Actions 조건을 재확인한다. 배포 권한/서명키가 없거나 과금 가능성이 생기면 그 단계에서 요청하고 다른 검증은 끝낸다. main은 병합하지 않는다.
- [ ] `.github/workflows/android-debug.yml`의 정확한 소스 SHA 빌드 성공을 확인하고 CI APK를 검증한다. 기존 설치에 맞는 로컬 서명 업데이트 APK는 CI의 비서명 payload와 일치하는지 확인한 뒤 별도 Release asset으로 제공한다. APK archive에 `.vrm`, `.vroid`, private part/texture/capture/key가 없음을 확인한다.
- [ ] 게시 URL을 익명으로 다시 내려받아 HTTP 성공, APK hash·버전·서명을 확인한다. 검증된 모바일 직접 다운로드 링크, 실제 편집 화면, 지원하는 파츠 2종과 필요한 로컬 모델 준비 방법을 한국어로 보고한다. 눈·입·얼굴형 교체까지 구현됐다고 보고하지 않는다.

## 계획 자체 검토

- 스펙 1–3의 부위 독립성·사진 목록·색상·준비 상태는 Task 3/4, 스펙 4의 실제 분리는 Task 1/2/3에 대응한다.
- 스펙 5의 v1 호환·v2 parts·저장 당시 복사본은 Task 3/5에 대응한다. v1 형식을 미리 일괄 덮어쓰지 않는다.
- 스펙 6의 원본 화질·실패 복구·메모리·허용 목록·개인 자료 보호는 Task 1–6의 검사에 배정했다. Android JSON 검사는 JVM mock 메서드로 성공을 가장하지 않고 기기에서 실행한다.
- Review Focus 5개에 대응 테스트가 있으며 인터페이스의 `partId`/`styleId`/`modelId`를 구분했다. 생성 모델 ID를 기존 두 해시에 억지로 맞추지 않는다.
- 새 모델 게시가 전역 선택을 바꾸는 기존 부작용과 VRM lease 부재를 확인했다. 선택 없는 등록을 추가하고 새 자동 GC는 만들지 않는다.
- 이 문서는 계획이며 위 체크박스는 미실행 상태다. 사용자 계획 확인 및 실행 방식 선택 전에는 제품 코드를 변경하지 않는다.
