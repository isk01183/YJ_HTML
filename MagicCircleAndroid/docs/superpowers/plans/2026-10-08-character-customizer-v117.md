# v1.17 캐릭터 커스터마이징 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 외형·키·신체 비율·부위별 의상·RGB/HEX를 실제 편집하고 저장한 2.5D 캐릭터를 기존 정적/라이브 배경화면에 배치한다.

**Architecture:** 네이티브 편집 화면, 불변 캐릭터 설정, 직접 제작한 벡터 부품을 사용하는 공통 Canvas 렌더러를 추가한다. 캐릭터는 기존 ScreenScene의 선택적 전용 레이어 하나이며 일반 이미지 보관함과 분리한다. 적용 시점의 설정 복사본은 기존 배경화면 스냅샷에 포함한다.

**Tech Stack:** 기존 Kotlin/Java, Android Canvas/Path/Paint, View/SeekBar/EditText, AtomicFile, org.json, JUnit 및 기존 자체 Instrumentation. 새 외부 런타임 라이브러리·네트워크 서비스는 추가하지 않는다.

**Spec:** `docs/superpowers/specs/2026-10-07-character-customizer-design-ko.md` — 2026-10-08 승인.

상태: **실행 계획 검토 전 / 제품 코드 미수정**. 아래 파일 경로는 별도 표시가 없으면 `MagicCircleAndroid` 기준이다. 실행은 각 작업의 실패 테스트 → 최소 구현 → 통과 확인 → 해당 파일만 커밋 순서다.

## Global Constraints

- minSdk 23, targetSdk 37, Java 17 및 한국어/일본어/영어 유지. 실제 배포 직전 최신 번호를 확인하며 예정 버전은 v1.17 / versionCode 20.
- 기본 2.5D. 실제 3D는 후속 단계이며 준비 중으로 표시한다. 단일 이미지 변형을 실제 3D로 표시하지 않는다.
- 외형 2개 몸체, 헤어/얼굴/눈/눈썹/코/입 각각 3종. 장비 수와 체형 범위는 승인 설계의 표를 그대로 따른다.
- 캐릭터는 작품당 1개. 일반 이미지 최대 8개 / GIF 최대 2개를 유지한다. 캐릭터 내부 부품은 이미지 개수에 넣지 않는다.
- 이번 캐릭터 연결은 배경화면용. 기존 마법진, 충전 감지·재연결·1/3/5/7초, 사용자 데이터와 선택은 보존한다.
- 추가 종량제 비용 0원. 게임 자산 추출, API 키, 유료 자산·테스트, main 병합, 기존 파일 일괄 덮어쓰기 금지.
- 기존 사용자 변경: SanctuaryReviewActivity.kt, 루트 Gradle/Wrapper, UserMagicCircleRenderer 및 기타 미추적 파일을 이번 커밋에 포함하지 않는다.
- 검증은 명시적으로 확인한 에뮬레이터 serial을 사용한다. 실기기/사용자 배경화면 자동 변경, AVD 초기화, 앱 데이터 삭제 금지.

## Review Focus

1. HEX 부분 입력·빈 숫자 상태에서 회전/백그라운드 이동: 마지막 유효 설정을 잃지 않고 오류 입력을 몰래 적용하지 않아야 한다. 작업 1·3에서 검사.
2. 신체 최소/최대 조합과 손 흔들기: 관절 틈, 의상 관통, 장식 이탈, 바닥 이동을 막아야 한다. 작업 2에서 검사.
3. 배경화면에 사용한 캐릭터의 수정·삭제·복제: 기존 작품과 적용된 라이브 엔진이 독립 상태를 유지해야 한다. 작업 4에서 검사.
4. v1.16 저장자료·적용 스냅샷 업그레이드와 쓰기 실패: 기존 정상 데이터 및 원본 파일을 보존해야 한다. 작업 1·4에서 검사.
5. 화면 회전·가림·닫기·최소 SDK 경로: 렌더링 재개/정지와 자원 해제가 정확해야 하며 이전 콜백이 새 화면을 덮지 않아야 한다. 작업 2·3·5에서 검사.

## 작업 1 — 캐릭터 설정·검증·보관함

**Files:** 새 `app/src/main/java/com/yj/magiccircle/CharacterDefinition.kt`, `CharacterStore.kt`; 새 `app/src/test/java/com/yj/magiccircle/CharacterRulesTest.kt`; 새 `app/src/androidTest/java/com/yj/magiccircle/CharacterStorageChecks.kt`; 수정 `V113Instrumentation.kt`(같은 androidTest 폴더).

**Interfaces:**

- `CharacterDefinition`: `id`, `name`, `appearance: CharacterAppearance`, `body: BodyProportions`, `outfit: Map<OutfitSlot,String>`, `colors: Map<String,Int>`, `motion: CharacterMotion`, `artworkVersion: Int=1`. 모두 불변 값이며 외부에서 받은 Map은 복사한다.
- `CharacterAppearance`: 문자열 ID `bodyType, hair, face, eyes, brows, nose, mouth`. `BodyProportions`: Float `heightCm, head, shoulders, torso, arms, legs, torsoWidth, armWidth, legWidth`; 비율 기본 1f.
- `OutfitSlot`: HALO, HEAD, TORSO, GLOVES, SHOES, WINGS. `CharacterMotion`: STILL, IDLE, WAVE.
- `CharacterRules.defaults(id: String, name: String): CharacterDefinition`, `validate(value: CharacterDefinition): Unit`, `toJson(value): JSONObject`, `fromJson(json: JSONObject): CharacterDefinition`.
- `CharacterColors.parseRgb(r: String,g: String,b: String): Int?`, `parseHex(text: String): Int?`, `hex(argb: Int): String`. 24비트 입력을 불투명 ARGB로 변환; HEX 출력은 대문자 `#RRGGBB`.
- `CharacterStore.get(context: Context): CharacterStore`, 테스트 생성자 `CharacterStore(root: File)`. `list(): List<CharacterDefinition>`, `find(id: String): CharacterDefinition?`, `draft(id: String): CharacterDefinition?`, `save(value)`, `saveDraft(value)`, `discardDraft(id)`, `delete(id)`; 오류는 IOException/검증 오류로 호출자에게 전달. 복제는 새 UUID를 가진 설정을 save한다.

- [ ] **실패 테스트 작성:** 아래 핵심 단위 검증 및 계측 저장 검사를 작성하고 러너에 `character-storage` 선택 분기를 등록한다.

```kotlin
assertEquals(0xff5096dc.toInt(), CharacterColors.parseRgb("80", "150", "220"))
assertEquals("#5096DC", CharacterColors.hex(0xff5096dc.toInt()))
assertNull(CharacterColors.parseHex("#50"))
assertNull(CharacterColors.parseRgb("256", "0", "0"))
// validate(defaults)는 통과; heightCm=119/201/NaN, head=.79f/1.21f는 거부.
// read-after-save == saved; draft 변경 뒤 find(id)는 기존 저장본과 동일.
```

- [ ] `./gradlew.bat testDebugUnitTest --tests '*CharacterRulesTest'`를 실행해 새 심벌 부재로 실패하는지 확인한다. 단순 문법 오타 실패는 고친다.
- [ ] 정의·허용 ID 목록·색상 변환·저장 구현. 몸체 `feminine/masculine`, 헤어 `bob/long/tied`, 얼굴 `round/oval/soft-square`, 눈 `round/almond/soft`, 눈썹 `soft/straight/arched`, 코 `small/soft/defined`, 입 `soft/smile/straight`를 쓴다. 장비는 `halo: none/ring/star`, `head: none/star/ribbon`, `torso: tunic/coat/robe`, `gloves: none/short/long`, `shoes: boots/flats`, `wings: none/feather/fairy`. 필요한 색 키는 `skin/hair/eyes`와 각 슬롯의 `.base/.accent`; 튜닉 기본색 #5096DC, 붉은 단발·자주색 눈을 기본으로 한다.
- [ ] `noBackupFilesDir/characters/`의 버전 1 JSON과 마지막 정상 복구 사본을 AtomicFile로 저장한다. 미지 버전·손상은 읽기 실패로 표시하고 파일을 덮지 않는다. 저장·초안·삭제는 하나의 동기화 경로로 처리하고 UI에서는 작업 스레드에서 호출한다. 이름 1–40자, UUID 및 모든 비율 범위·색 키를 검사한다.
- [ ] 단위 테스트 통과 후 `CharacterStorageChecks`에서 JSON 왕복, 새 인스턴스 복원, 복제 독립성, 초안/저장본 분리, 손상 파일 보존, AtomicFile 실패 주입 시 이전 파일 유지 확인. 기기 전용 임시 테스트 폴더만 사용한다.
- [ ] 변경 파일만 명시적으로 커밋: `feat: add validated offline character presets`.

## 작업 2 — 실제 벡터 부품·체형·동작 렌더러

**Files:** 새 `app/src/main/java/com/yj/magiccircle/CharacterGeometry.kt`, `CharacterParts.kt`, `CharacterRenderer.kt`; 새 `app/src/test/java/com/yj/magiccircle/CharacterGeometryTest.kt`; 새 `app/src/androidTest/java/com/yj/magiccircle/CharacterRenderingChecks.kt`; 러너에 `character-render` 등록.

**Interfaces:** 작업 1의 설정을 소비한다. `CharacterGeometry.measure(body: BodyProportions): BodyFrame`은 정수리/발바닥/목/양쪽 어깨·팔꿈치·손목·골반·무릎·발목 좌표와 몸체 경계를 반환한다. 순수 Kotlin 계산이며 Canvas 객체를 포함하지 않는다. `CharacterRenderer(definition: CharacterDefinition)`은 `update(definition)`, `prepare(width: Int,height: Int)`, `draw(canvas: Canvas,elapsedMs: Long,animated: Boolean)`, `close()`와 `animated: Boolean`을 제공한다. canonical 출력은 투명 배경이며 200cm 공통 기준의 전신 영역을 사용한다. 몸체와 의상은 같은 BodyFrame을 참조한다.

- [ ] **실패 테스트 작성:** 각 체형 파라미터 최소/기본/최대와 교차 극단 조합에서 `soleY-crownY == heightCm`(오차 .001f), 좌우 발바닥 높이 동일, 모든 좌표 유한, 바뀌지 않은 설정 불변을 검사한다. 계측 검사에는 닫힌 경로, 투명 배경, 모든 허용 부품 ID의 렌더링, 프레임 차이 검사를 추가한다.
- [ ] `./gradlew.bat testDebugUnitTest --tests '*CharacterGeometryTest'`로 실패를 확인한다.
- [ ] 관절·비율 계산과 캐시된 벡터 부품 제작. 곡선 선화, 피부/머리/직물 기본색·그림자·하이라이트, 눈동자와 표정 부품을 분리한다. 부품 접점에 겹침 여유를 주고 머리/상완/전완/손/허벅지/종아리/발 변환을 공유한다. 각 ID는 실제로 구별되는 모양이어야 한다. 세부 장비 수·형태는 작업 1 목록을 사용한다.
- [ ] STILL은 정적, IDLE은 약한 호흡·눈 깜박임, WAVE는 어깨·팔꿈치·손과 소매·장갑을 함께 움직인다. 시간 0의 animated=false/true는 같은 초기 포즈. 코드 내부에서 화면 배경이나 타이머를 소유하지 않고 호출자가 생명주기를 제어한다. Paint/Path/Shader는 인스턴스별 캐시, 단순 틸트는 제한된 2.5D로만 제공한다.
- [ ] 계측에서 `static(t=0)==static(t=5000)`, `live(t=0)==static(t=0)`, 동작별 후속 프레임 차이, update 뒤 다른 렌더러 불변, close 반복 안전을 검사한다. 전신·얼굴·장비·최소/최대 체형 캡처를 실제로 열어 기준 시안과 비교하고 관절 틈/직선 인형/부품 누락을 수정한다. 픽셀 차이만으로 품질 합격을 선언하지 않는다.
- [ ] 관련 테스트 통과 및 비교 캡처 후 해당 파일만 커밋: `feat: render layered customizable fantasy characters`.

## 작업 3 — 관리·커스터마이징 실사용 화면

**Files:** 새 `app/src/main/java/com/yj/magiccircle/CharacterActivity.kt`, `CharacterPreviewView.kt`; 수정 `MainActivity.java`, `app/src/main/AndroidManifest.xml`, `app/src/main/assets/gallery.html`, `gallery.js`, `i18n.js`; 새 `app/src/androidTest/java/com/yj/magiccircle/CharacterEditorChecks.kt`; 러너에 `character-editor` 등록.

**Interfaces:** `CharacterActivity`는 추가 Intent 없음이면 관리 목록, `characterId`가 있으면 해당 캐릭터 편집. 존재하지 않는 ID는 오류 후 목록. `CharacterPreviewView.setCharacter(value: CharacterDefinition)`, `fitToView()`, `resetView()`; 핀치는 보기 배율만 조절하고 설정을 변경하지 않는다. 작업 1 저장소와 작업 2 렌더러를 사용한다. 배경화면 진입은 작업 4가 정의하는 `characterId` Intent로 연결한다.

- [ ] **실패 계측 작성:** `CharacterEditorChecks`에 생성/수정/복제/이름 변경/삭제 취소, body·headgear·hair 독립 변경, HEX 부분 입력/취소, view-only 핀치, 회전·재실행 초안 복원을 작성한다. 기존 `checkOnMain`으로 UI 작업을 실행한다. `character-editor` 실행 실패를 먼저 기록한다.
- [ ] 홈에 `캐릭터 만들기` 진입을 추가하고 exported=false Activity에서 목록과 편집을 제공한다. 새 작성은 임시 ID, 명시 저장 전 목록에 완성본으로 나타나지 않는다. 부품 교체·색상·체형·동작 탭은 모두 실제 렌더러에 연결하고 3D는 준비 중으로 안내한다.
- [ ] 48dp 이상 버튼/SeekBar와 숫자 입력, 고정 발 위치와 키 눈금, 별도 보기 조절·체형 초기화 구현. 색상 패널은 유효한 RGB/HEX만 미리보기에 반영하고 잘못된 입력을 자동 잘라내어 저장하지 않는다. cancel은 원래 색을 복원한다. 입력 중 문자열과 선택 탭/스크롤/보기 상태는 회전 때 보존한다.
- [ ] 이름·체형 등 마지막 유효 초안을 저장하고 저장/버리기/계속 편집을 구분한다. lifecycle stop에서 프레임 중지, 재개 때 단일 루프 재시작, close에서 콜백 제거. 늦은 저장/썸네일 응답에는 현재 캐릭터/화면 세대 검사를 한다. UI 저장 실패는 기존 데이터 유지와 오류 표시로 처리한다.
- [ ] 휴대전화와 태블릿에서 폰트 확대·키보드·3개 언어·전체 부품과 슬롯을 실제 조작한다. 취소/초기화가 다른 슬롯의 선택·색을 바꾸지 않는지 확인한다. 단위/계측 통과 후 해당 파일만 커밋: `feat: add character wardrobe and body editor`.

## 작업 4 — 전용 레이어·배경화면·구버전 데이터 호환

**Files:** 수정 `app/src/main/java/com/yj/magiccircle/ScreenScene.kt`, `MediaLibrary.java`, `LayeredSceneRenderer.kt`, `ScreenEditorView.kt`, `ScreenEditorActivity.kt`, `ChargingSceneView.kt`, `WallpaperArtwork.kt`, `UploadedWallpaperStore.kt`, `app/src/main/assets/gallery.js`; 수정 `app/src/test/java/com/yj/magiccircle/ScreenSceneTest.kt`; 새 `app/src/androidTest/java/com/yj/magiccircle/CharacterSceneChecks.kt`; 기존 `SceneStorageChecks.kt`, `SceneWallpaperChecks.kt`, `EditorGestureChecks.kt`와 `selftest/wallpaper-browser.test.cjs` 확장; 러너에 `character-scene` 등록.

**Interfaces:**

- `ScreenScene` 마지막 인자로 `character: CharacterLayer?=null` 추가. `CharacterLayer(definition: CharacterDefinition,x: Float,y: Float,width: Float,angle: Float,flipX: Boolean,visible: Boolean,beforeImage: Int)`는 독립 설정 사본을 가진다. 0≤beforeImage≤이미지 수, 0은 모든 이미지 뒤/이미지 수는 모두 앞. 변환 범위는 기존 이미지와 같다.
- `LayeredSceneRenderer.updateCharacter(value: CharacterLayer?)`; 기존 updateLayers/draw 경로는 보존. 새 합성은 beforeImage 위치에서 같은 CharacterRenderer를 사용한다.
- `ScreenEditorView.modifyCharacter(block: (CharacterLayer)->CharacterLayer)`, `selectedCharacter: Boolean`; 이미지/정보/캐릭터는 동시에 하나만 선택된다. 제스처 시작 시 선택 대상을 고정한다. `ChargingSceneView.updateCharacter(value)`는 편집용 렌더러에 전달할 뿐 충전 수신기나 타이머를 변경하지 않는다.
- `ScreenEditorActivity`의 `characterId` Intent: 기존 `themeId`가 있으면 WALLPAPER 작품에 추가/명시적 교체, 없으면 새로운 WALLPAPER 초안 생성. 저장본 캐릭터만 읽어 복사하며 삭제/누락이면 기존 작품을 유지하고 오류를 표시한다. 작품의 복사본과 보관함을 자동 동기화하지 않는다.

- [ ] **실패 테스트 작성:** 구버전 character 필드 없는 JSON은 null, 캐릭터가 있는 CHARGING 작품은 거부, 8개 이미지+1개 캐릭터는 허용, beforeImage 범위/NaN/미지 부품 버전은 거부. 계측은 캐릭터만 있는 배경화면(이미지 0개), 이미지 앞/뒤 합성, 선택 고정 두 손가락 제스처, 복사본 독립성, 저장 오류를 검사한다.
- [ ] `./gradlew.bat testDebugUnitTest --tests '*ScreenSceneTest'`와 관련 계측의 실패를 확인한다.
- [ ] 선택적 캐릭터 직렬화와 검증 추가. `MediaLibrary` schema를 4로 올리고 v1/v2/v3를 읽는다. 현재 readV2의 `version==3` 조건을 정확히 3 또는 4로 확장해 editor 자료를 잃지 않게 한다. v3 원본을 복구 파일로 보존하고 새 데이터 검증/쓰기 성공 후에만 메모리 상태를 발행한다.
- [ ] 기존 일반 이미지 ID/lease/삭제 검사는 그대로 두고 캐릭터 설정을 미디어 ID로 취급하지 않는다. 구조 감지는 캐릭터 추가/제거/부품 갱신을 포함하되 위치 편집마다 renderer를 재생성하지 않는다. 이미지 추가/제거/순서 변경 시 beforeImage가 유효하도록 보정한다. 새 설정 가져오기는 사용자가 선택했을 때만 실행한다. gallery.js의 작품 썸네일 캐시 키에는 기존 layers뿐 아니라 character도 포함하고, 캐릭터만 수정한 같은 작품 ID의 썸네일 URL이 달라지는 브라우저 검사를 추가한다.
- [ ] 배경화면 스냅샷을 버전 2로 쓰고 기존 버전 1/단일 파일 슬롯도 계속 읽는다. character 정의와 artworkVersion을 스냅샷 안에 저장하고 부품 v1 렌더링 정의를 향후 업데이트에서도 보존한다. 이미지가 없는 캐릭터 작품도 외부 파일을 요구하지 않게 한다. 사본 적용 뒤 보관함 수정/삭제가 엔진 출력에 영향 없는지 확인한다.
- [ ] 배경화면 작품의 편집 미리보기는 충전 duration 제한 없이 닫기 버튼까지 유지되게 구분한다. 기존 ChargingSceneView의 deadline을 길게 늘리는 우회 대신 WallpaperArtwork를 사용하는 배경화면 전용 미리보기 생명주기로 처리한다. 실제 적용은 기존 WallpaperController를 재사용하고 사용자 확인 없이 호출하지 않는다.
- [ ] 관련 단위/전체 계측을 통과시키고 기존 이미지·GIF·마법진·충전 단계 기능 회귀 검사. 변경 파일만 커밋: `feat: place independent character layers in wallpapers`.

## 작업 5 — 품질 확인·버전 증가·모바일 배포

**Files:** 수정 `app/build.gradle.kts`, 저장소 루트 `.github/workflows/android-debug.yml`; 새 `docs/V1_17_KO.md` 및 검증 캡처. 필요 시 앞 단계의 실제 실패 원인이 있는 파일만 수정한다.

- [ ] 시작 전 기준 commit/작업트리/빌드 환경을 다시 기록한다. 기존 미커밋 AGP/Wrapper 변경을 포함해 배포하지 않는다. 현재 로컬 환경과 커밋 환경이 다르면 테스트 결과를 분리하고 같은 소스의 CI 검증으로 확정한다.
- [ ] app 버전과 workflow의 metadata 기대값·파일명·tag·제목을 함께 `20/1.17`로 올린다. `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`가 성공해야 한다.
- [ ] 설치는 Android CLI로 확인한 에뮬레이터 하나씩 실행한다. `android run --device=<serial> --apks=<검증한 APK>`; 테스트 APK 설치 후 `adb -s <serial> shell am instrument -w -e checks character-storage com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation`을 실행한다. character-render/editor/scene와 선택 옵션 없는 전체 러너도 실행하여 성공 출력을 확인한다. 기기 UI 조작 전에 android-cli의 interact/journeys 참조를 읽는다.
- [ ] API 36 Phone/Tablet에서 화면 캡처, 실제 설정 변경, 정적·라이브 미리보기, 백그라운드 정지·복귀를 확인한다. 가능하면 API 23도 검사하되 설치돼 있지 않으면 검증 범위를 명시한다. 실제 삼성 기기의 잠금화면 정책을 에뮬레이터로 증명했다고 하지 않는다.
- [ ] 기준 이미지와 전신/얼굴/체형/의상/배경화면 캡처를 나란히 비교하고 차이점을 보고서에 남긴다. 눈 크기·단발 윤곽·튜닉 디테일·음영·관절·의상 맞춤 중 부족한 부분을 구체적으로 수정한다. 부위별 변경 없는 버튼, 스냅샷 대신 시안 표시, 실제 3D 표기는 배포 실패 조건이다.
- [ ] `graphify update .`를 AST-only로 실행해 코드 그래프를 갱신한다. 유료 외부 모델 환경변수나 semantic 호출을 사용하지 않는다. diff 검사와 독립 최종 코드 리뷰를 거치고 필요한 파일만 커밋한다.
- [ ] 기존 공개 저장소/작업 브랜치인지 재확인 후 push하고 GitHub Actions 성공을 확인한다. main은 병합하지 않는다. CI는 단위 테스트/lint/APK만 수행하므로 계측 결과와 구분한다.
- [ ] 같은 소스의 로컬 호환 서명 APK를 기존 v1.16 인증서 지문·packageId·versionCode와 비교한다. 서명 불일치를 앱 삭제로 해결하지 않는다. 자격증명/키는 읽어 출력하거나 Git/CI에 넣지 않는다.
- [ ] 검증한 로컬 업데이트 APK와 SHA-256을 해당 prerelease에 추가하고 공개 파일을 다시 받아 해시와 버전을 비교한다. 모바일용 직접 APK 링크, 확인한 기능/기기, 남은 차이, 실제 3D 미포함을 한국어로 짧게 보고한다.

## 자체 점검과 실행 방식

설계의 외형·체형·의상·색상·동작은 작업 1–3, 관리·보존·배경화면은 작업 1·4, 호환·성능·모바일 배포는 작업 4–5가 담당한다. 데이터 계약은 이 문서의 Interfaces를 공유하며, 저장소 근거와 다른 발견이 있으면 변경 이유부터 보고한다. 이 문서는 구현 완료가 아니다.

추천은 **현재 에이전트 직접 구현 + 마지막 독립 리뷰**다. 데이터·렌더러·편집기가 같은 계약에 의존하므로 반복 인수인계를 줄여 토큰을 절약한다. 사용자가 원하면 단계별 구현/리뷰 에이전트 방식으로 변경할 수 있다. 실행 계획 및 실행 방식 확인 후 작업 1부터 시작한다.
