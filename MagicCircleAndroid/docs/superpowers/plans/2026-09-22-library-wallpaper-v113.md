# v1.13 마법진 관리·배경화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 43종과 사용자 탭을 안전하게 저장하고, 지정 도안을 개선하며 W03·R01 정지/라이브 배경화면을 제공한다.

**Architecture:** 기존 Java 서비스·WebView 갤러리·Kotlin Canvas를 보존한다. 도안 관리 상태는 기존 AtomicFile에 함께 저장한다. W03·R01은 공유 Canvas 렌더러를 앱 미리보기·충전·정지 출력·라이브 엔진에서 사용한다. 라이브 도안 두 개는 서로 다른 시스템 서비스 항목으로 등록하여 미리보기 취소가 현재 도안을 바꾸는 공유 설정 문제를 피한다.

**Tech Stack:** Java 17/Kotlin, Android Canvas/Path/WallpaperService/WallpaperManager, HTML/CSS/JS/SVG, 기존 JUnit 4.13.2·Playwright 검사, AGP 9.3.0/Gradle 9.5.0. 새 제품 라이브러리를 기본 전제로 추가하지 않는다.

**Spec:** `docs/superpowers/specs/2026-09-22-library-wallpaper-v113-design-ko.md` — 2026-09-22 사용자 승인.

## Global Constraints

- 다음 배포는 v1.13 / versionCode 16입니다. 중간 배포가 생기면 실제 최신 번호보다 증가시킵니다.
- minSdk 23, 기존 세 언어(한국어·일본어·영어), 오프라인 사용, 업로드 자료를 유지합니다.
- 충전 연결 때 최대 7초 재생, 분리하면 즉시 종료, 재연결하면 처음부터 재생하는 기능은 별도로 유지합니다.
- 추가 종량제 서비스, 유료 API, 계정/인증 설정 변경은 없습니다. 기존 파일 삭제와 main 병합도 하지 않습니다.
- 저해상도 참고 이미지를 확대하거나 회전시켜 완성품으로 사용하지 않습니다. 도형·룬·배경의 핵심 요소는 직접 그립니다.
- 배경화면 적용은 사용자 확인 이후에만 한다. 충전 이벤트에서 배경화면 API를 호출하지 않는다.
- 라이브 30fps 상한, 숨김/Surface 해제 시 렌더 중단. BlurMaskFilter, 강제 소프트웨어 View 레이어, 숨겨진 API를 쓰지 않는다.
- 잠금 전용 정지는 API 24 이상에서만 제공한다. 잠금 단독 라이브 지원은 시스템 선택 화면/실기기로 확인하며 홈으로 자동 대체하지 않는다.
- 기존 미추적 `gradle/gradle-daemon-jvm.properties`, `graphify-out/` 및 사용자 파일은 커밋하거나 삭제하지 않는다.

## Review Focus

1. **설정 이전 중 쓰기 실패/전원 중단:** 원본과 복구본을 보존하고 같은 초기 목록을 반복 적용하지 않는다 — Task 1의 재시작·실패 주입 검사.
2. **탭에 남은 비활성/삭제된 자료와 악성 문자열:** 비활성 소속은 보존하되 파일 삭제는 별개이며 탭 이름은 실행되지 않는다 — Task 2의 DOM·저장 검사.
3. **이미 적용된 라이브가 있는 상태에서 다른 도안 미리보기 후 취소:** 기존 엔진·도안·대상을 보존한다 — Task 8의 실제 두 서비스 전환/취소 검사.
4. **Surface 생성/숨김/회전/종료 순서가 빠르게 바뀜:** 한 번의 프레임 루프만 유지하고 파괴된 Surface에 그리지 않는다 — Task 8의 루프 상태 검사와 실기기 반복.
5. **100%·큰 글자·가로 화면·충전 재연결:** 실제 코어 중심 정렬과 새 실행/기한을 유지한다 — Task 3·9의 레이아웃 및 재연결 검사.

## 범위·작업 순서와 경로

이 계획은 승인 설계 하나의 통합 배포 계획이다. 목록 관리(1~2), 시각 개선(3~7), 배경화면/통합(8~9), 검증·배포(10)를 각각 실행·검토 가능한 단위로 구분한다. 새 기능은 뒤 단계 완료 전 사용자에게 배포하지 않는다.

저장소 루트: `C:/Users/jtn28/OneDrive/Documents/ChatGPT/New project/stellar-sanctuary-v112`.
이하 파일 경로와 명령의 기준은 그 안의 `MagicCircleAndroid`이다. Java/Kotlin 소스의 공통 접두사는 `app/src/main/java/com/yj/magiccircle/`, JVM 테스트는 `app/src/test/java/com/yj/magiccircle/`이다. 파일 표의 이 접두사를 실제 경로에 붙여 작업한다.

| 단계 | 변경 경계 | 주요 파일 |
|---|---|---|
| 1 | 정책·탭 저장·v1→v2 이전 | `ThemeSelection.java`, 새 `LibraryPolicy.java`, `MediaLibrary.java`, `LibraryPolicyTest.java`, Android 저장 검사 |
| 2 | 통합 갤러리·개별 활성·탭 UI | `MainActivity.java`, `gallery.html`, `gallery.js`, `i18n.js`, 세 언어 strings, `selftest/library-browser.test.cjs` |
| 3 | N01 중심 정렬 | `SanctuaryLayout.kt`, `ChargeStatusPanelRenderer.kt`, 기존 GeometryTest |
| 4 | 내부/전체 구조 개선 7종 | `direct-circles.js`, `direct-extra.js`, `circle-designs.js`, 시각 회귀 검사 |
| 5 | W 배경·F 외부 장식 | `collection_circle.html`, `direct-extra.js`, `direct-circles.js`, 시각 회귀 검사 |
| 6 | R01 네이티브 공통 경로 | 새 `WallpaperArtwork.kt`, `R01Renderer.kt`, `ArtworkGeometry.kt`, `ArtworkGeometryTest.kt` |
| 7 | W03 네이티브 정밀 도안 | 새 `W03Renderer.kt`, Task 6 공통 렌더러, 디버그 검수 화면 |
| 8 | 정지/라이브 적용 | 새 `MagicWallpaperService.kt`, `WallpaperController.kt`, `WallpaperPolicy.kt`, Manifest/XML, 적용 테스트 |
| 9 | 미리보기·충전 경로 일치 | `MainMagicChargeView.kt`, `MainActivity.java`, `ChargingAccessibilityService.java`, `WebViews.java`, 갤러리 |
| 10 | 버전·통합 검증·배포 | `app/build.gradle.kts`, `../.github/workflows/android-debug.yml`, README/검수 결과 |

의존 순서: 1→2, 3~5는 저장/UI와 독립 검토 가능, 6→7→8, 1·2·6·7→9, 모두 통과 후 10. 공유 파일을 건드리는 구현 작업은 동시에 실행하지 않는다. 계획 단계의 읽기 전용 조사만 병렬 진행했다.

## 공통 실행 환경과 커밋 규칙

현재 이미 격리된 worktree이므로 실행 시작 때 `using-git-worktrees`로 확인하되 새 체크아웃을 무조건 만들지 않는다. 실행 전 Git 상태·HEAD와 계획 기준을 비교한다. 다음은 PowerShell 세션에만 적용하며 사용자 환경 설정 파일을 덮어쓰지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\jtn28\AppData\Local\Android\Sdk'
$env:NODE_PATH = 'C:\Users\jtn28\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
$env:MAGIC_CIRCLE_REFERENCE_DIR = 'C:\Users\jtn28\OneDrive\Documents\ChatGPT\New project\magic-circle-master\reference'
$node = 'C:\Program Files\nodejs\node.exe'
$adb = 'C:\Users\jtn28\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$git = 'C:\Users\jtn28\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe'
& $git status --short --branch
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

기준 검사 결과를 먼저 기록한다. 공유 Gradle 캐시/Git 공통 디렉터리 권한이 필요하면 해당 명령만 승인 요청한다. 키·토큰은 출력하지 않는다. 각 Task의 실패 테스트가 실제로 실패하는지 먼저 확인하고, 통과 후 그 Task의 파일만 명시해서 커밋한다. `git add .`는 사용하지 않는다. 코드 변경 후 비용 없는 AST `graphify update .`를 수행하고 생성 그래프는 제품 커밋에 넣지 않는다.

---

### Task 1: 활성 정책·탭 저장·안전한 설정 이전

**Files:** 기존 `ThemeSelection.java`, `MediaLibrary.java`; 새 `LibraryPolicy.java`, `LibraryPolicyTest.java`; 실제 Android 저장 검사용 `app/src/androidTest/java/com/yj/magiccircle/LibraryStorageChecks.kt`와 `V113Instrumentation.kt`, 필요한 test runner 설정만 `app/build.gradle.kts`에 추가.

**Interfaces:**
- `ThemeSelection.V113_ACTIVE`: 수정 불가능한 `Set<String>` 43개. 기존 `IDS` 174개를 줄이지 않는다.
- `LibraryPolicy.hiddenForRevision(int revision, Set<String> saved): Set<String>` — revision 0만 allowlist의 여집합 적용, 1은 사용자 상태 복사.
- `LibraryPolicy.selectionAfterMigration(String saved, Set<String> validMediaIds): String` — 빈 선택 보존, 활성/유효 업로드 보존, 제외된 내장 선택은 N01.
- `LibraryPolicy.tabName(String raw, Collection<String> otherNames): String` — NFC 정규화·trim·1~40 Unicode 코드포인트·중복 검증. 정규화된 이름을 대소문자 구분하여 비교한다. 이름 변경 시 자기 이름은 otherNames에서 제외한다. 실패는 IllegalArgumentException.
- `MediaLibrary.setEnabled(String id, boolean enabled)`, `createTab(String name): String`, `renameTab(String id,String name)`, `deleteTab(String id)`, `setTabMember(String tab,String theme,boolean member)` — 기존과 같이 IOException을 알림. 생성 ID는 Android에서 UUID로 만든다.
- `galleryState()`의 기존 필드에 `tabs: [{id,name,members}]`, `activationRevision:1`, `migrationNotice`를 추가한다. `hidden`은 내장 ID만 유지하고 업로드 삭제는 기존 흐름을 쓴다.

- [ ] **1. 실패하는 JVM 정책 테스트 작성.** 위 시그니처를 만들기 전에 아래 테스트와 선택 빈 값/업로드 유지 테스트를 추가한다.

```java
@Test public void activationIsAppliedOnlyOnce() {
    Set<String> first = LibraryPolicy.hiddenForRevision(0, Collections.emptySet());
    assertEquals(43, ThemeSelection.V113_ACTIVE.size());
    assertEquals(131, first.size());
    assertTrue(first.contains("ref-C03"));
    assertFalse(first.contains("raphael"));
    first.remove("ref-C03");
    assertFalse(LibraryPolicy.hiddenForRevision(1, first).contains("ref-C03"));
    assertEquals("native-N01", LibraryPolicy.selectionAfterMigration("classic", Collections.emptySet()));
    assertEquals("", LibraryPolicy.selectionAfterMigration("", Collections.emptySet()));
}
```

- [ ] **2. RED 확인:** `.\gradlew.bat testDebugUnitTest --tests '*LibraryPolicyTest'` — 새 정책 미구현으로 실패해야 한다. Java 컴파일 설정 문제와 기대 실패를 구분한다.
- [ ] **3. 최소 정책 구현.** allowlist는 승인 설계 그대로 기존 저장 ID로 옮긴다. 복사본을 반환하여 테스트/호출자가 원본을 변경하지 않게 한다.

```java
static Set<String> hiddenForRevision(int revision, Set<String> saved) {
    if (revision == 1) return new LinkedHashSet<>(saved);
    if (revision != 0) throw new IllegalArgumentException("Unknown activation revision");
    Set<String> result = new LinkedHashSet<>(ThemeSelection.IDS);
    result.removeAll(ThemeSelection.V113_ACTIVE);
    return result;
}
```

- [ ] **4. 스키마를 확장.** `version:2, activationRevision:1, tabs:[...]`를 기존 media/hidden/selected/pendingDeletes와 한 AtomicFile에 저장한다. 기존 synchronized `MediaLibrary.get()`의 생성자 안에서 읽기→검증→백업→이전→인스턴스 공개 순서를 완료한다. 접근성 서비스가 Activity보다 먼저 시작해도 같은 결과여야 하므로 Activity 전용 비동기 초기화를 만들지 않는다. 크기 상한 1MiB를 유지하고 초기화 지연을 측정한다. v1은 원본을 읽어 검증한 뒤 `media-library.v1-recovery.json`에 일회성 원자 백업하고 v2를 쓴다. AtomicFile의 일시 `.bak`를 영구 복구본으로 착각하지 않는다. 원본 .bak만 남은 경우 `openRead()`로 복구된 바이트를 백업한다. 기존 영구 복구본이 다르면 덮어쓰지 않고 이전을 중단한다. 새 설치에는 존재하지 않는 v1 백업을 만들지 않는다.
- [ ] **5. 오류 경로 구현.** 상태 복사본으로 새 JSON을 만들고 직렬화된 바이트까지 검증한 뒤 저장한다. 교체 전 실패만 failWrite로 복구한다. finishWrite 뒤 재읽기 실패는 이미 교체되었을 수 있으므로 '이전 상태로 복원 성공'으로 처리하지 않는다. 재읽기로 실제 상태를 확인하거나 readable=false로 쓰기를 막고 자료와 복구본을 보존한다. 탭 ID/소속 중복, 알 수 없는 내장 ID, 잘못된 버전/자료, 1MiB 초과는 거부한다. `retryPendingDeletes`, import, select도 새 필드를 빠뜨리지 않도록 save의 모든 호출부를 수정한다. 업로드 삭제 시에만 해당 UUID의 탭 소속을 제거한다. 마이그레이션 실패를 새 설치로 처리하지 않는다. 확인 불가능 상태에서는 pendingDeletes를 실행하지 않는다.
- [ ] **6. 실제 저장 RED→GREEN 검사 추가.** 앱 실제 저장소가 아닌 instrumentation 전용 임시 디렉터리를 쓰는 package-private MediaLibrary 생성 경로를 제공한다. v1→v2→재로드, 서비스부터 초기화, v2 재활성 보존, 손상/미래 버전, 백업 실패, JSON 크기 초과를 검사한다. 교체 전 쓰기 실패에서는 이전 메모리/원본 바이트 보존, 교체 후 확인 실패에서는 쓰기 금지/파일 보존을 각각 검사한다. 저장 함수에 거대한 추상화 대신 테스트 생성자에서 디렉터리와 Android AtomicFile을 주입하고, 검사에서만 AtomicFile의 startWrite/openRead 실패를 재현한다. production get은 일반 AtomicFile만 생성한다.

```kotlin
// LibraryStorageChecks.run(context): Unit 안에서 사용할 실제 Android 저장 검사.
// ctor: MediaLibrary(Context, File storageRoot, String legacySelected)
val root = java.io.File(context.cacheDir, "v113-storage-" + java.util.UUID.randomUUID()).apply { mkdirs() }
val original = """{"version":1,"media":[],"hidden":[],"selected":"classic","pendingDeletes":[]}"""
java.io.File(root, "media-library.json").writeText(original)
val first = MediaLibrary(context, root, "classic")
check(first.selected() == "native-N01")
first.setEnabled("ref-C03", true)
check(MediaLibrary(context, root, "classic").available("ref-C03"))
check(java.io.File(root, "media-library.v1-recovery.json").readText() == original)
```

- [ ] **7. Android 검사는 실제 Android에서 실행.** V113Instrumentation은 Android 기본 Instrumentation을 상속하고 별도 test APK에만 둔다. defaultConfig의 testInstrumentationRunner는 `com.yj.magiccircle.V113Instrumentation`이다. JVM의 Android stubs를 가짜 정상값으로 만드는 옵션이나 Robolectric 새 의존성은 추가하지 않는다. 아래 runner로 예외 없는 실행만 합격시킨다.

```kotlin
class V113Instrumentation : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = android.os.Bundle()
        try {
            LibraryStorageChecks.run(targetContext)
            result.putString("stream", "V113_CHECKS_OK")
            finish(android.app.Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "V113_CHECKS_FAILED: " + error.javaClass.simpleName)
            finish(android.app.Activity.RESULT_CANCELED, result)
        }
    }
}
```

승인된 연결 기기에 호환 서명의 앱/test APK를 모두 설치한 뒤 실행한다. 서명 불일치 시 삭제하지 않고 중단한다. 연결 기기가 없으면 미검증으로 남긴다. 실제 앱 자료를 테스트용으로 교체하지 않는다.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Test APK build failed' }
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
$checkResult = & $adb shell am instrument -w com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation
if ($LASTEXITCODE -ne 0 -or ($checkResult -join "`n") -notmatch 'V113_CHECKS_OK' -or ($checkResult -join "`n") -match 'V113_CHECKS_FAILED|INSTRUMENTATION_FAILED') { throw 'Android checks failed' }
```
- [ ] **8. GREEN와 커밋:** 정책 테스트·실제 저장 검사·기존 media 검사를 실행하고 `feat: persist curated library and custom tabs`로 이 단계 파일만 커밋한다.

### Task 2: 통합 선택·비활성 관리·사용자 탭

**Files:** `MainActivity.java`, assets의 `gallery.html`, `gallery.js`, `i18n.js`, res의 기존 3개 언어 strings.xml, 새 `selftest/library-browser.test.cjs`, 기존 `selftest/media-browser.test.js`, `selftest/languages-browser.test.js`.

**Interfaces:** Task 1의 galleryState와 mutation 메서드를 소비한다. URL 동작은 `enable?theme=...`, `disable?theme=...`, `tab-create?name=...`, `tab-rename?id=...&name=...`, `tab-delete?id=...`, `tab-member?id=...&theme=...&member=0|1`로 고정한다. 앱 쪽에서 매개변수 이름/중복/개수를 정확히 검사한다. 기존 select/preview/import/delete/language는 유지한다.

- [ ] **1. Playwright 실패 테스트.** 기존 로컬 파일·오프라인 브라우저 검사를 재사용한다. 아래는 새 테스트의 핵심이며 catalog로 hidden을 계산한다.

```js
const builtins = await page.evaluate(() => [nativeDesign,...CircleDesigns.list,...ReferenceDesigns.list]);
const activeIds = ('N01 C11 C12 C15 C16 C21 C22 C23 C26 C28 C30 W01 W02 W03 W04 W05 F01 F02 F03 F04 F05 A14 R01 crimson-abyss healing-wings snowflake sakura-seal spirit-bloom chronos-gears fate-compass twilight-balance moon raphael layered premium basic blue gold silver violet cyan core minimal').split(' ').map(id=>id==='N01'?'native-N01':/^[A-Z]\d\d$/.test(id)?'ref-'+id:id);
const tabs = [{id:'11111111-1111-4111-8111-111111111111', name:'<b>내 도안</b>', members:['ref-C03','ref-W03']}];
await page.evaluate(state => window.setGalleryState(state), {
  selected:'native-N01', language:'ko', enabled:true, media:[], readable:true,
  hidden:builtins.map(x=>x.id).filter(id=>!activeIds.includes(id)), tabs, activationRevision:1
});
assert.equal(await page.locator('#design-grid .card:visible').count(),43);
assert.equal(await page.locator('[data-tab-id] b').count(),0);
await page.locator('[data-tab-id="'+tabs[0].id+'"]').click();
assert.equal(await page.locator('#design-grid .card:visible').count(),1);
await page.locator('#manage-inactive').click();
assert.equal(await page.locator('#inactive-grid .card:visible').count(),131);
```

- [ ] **2. RED 확인:** `& $node selftest/library-browser.test.cjs` — 통합/비활성 UI가 없어 실패해야 한다.
- [ ] **3. UI 구현.** 고정 분류 탭 대신 전체+사용자 탭+추가 버튼을 렌더한다. 이름은 `textContent`, 버튼은 최소 48dp 상당 터치 영역, dialog에 닫기/초점 복원/접근성 이름을 제공한다. 비활성 화면은 코드·번역된 이름 검색과 별도 그리드의 정적 미리보기/개별 활성화를 제공하며 비활성 상태를 충전 선택으로 저장하지 않는다. 탭 삭제에는 '도안은 삭제되지 않음' 확인을 표시한다. 기존 서비스 상태/언어/업로드 진입과 모든 카드·상세의 식별 코드를 유지한다.
- [ ] **4. Android 동작 연결.** `MainActivity.handleGalleryLink()`의 현재 available 체크보다 앞에서 enable와 tab 동작을 분기한다. 그렇지 않으면 비활성 ID를 다시 활성화할 수 없다. 모든 저장은 기존 `changeLibrary()` 직렬 실행 경로를 재사용한다. 알 수 없는 action, theme 중복, 잘못된 member, 임의 URI/경로는 거부한다. UI의 임시 표시를 저장 성공으로 단정하지 않고 Android가 돌려준 상태로 다시 그린다.

```js
const button = document.createElement('button');
button.dataset.tabId = tab.id;
button.textContent = tab.name;
button.addEventListener('click', () => { activeGroup = tab.id; render(); });
```

- [ ] **5. GREEN 확인.** 새 검사에 빈 탭, 중복 이름, 40/41 코드포인트, 이름 변경, 다중 소속, 비활성→재활성, 마지막 활성 끄기, 저장 busy, 한/일/영, 업로드 삭제/숨김 구분을 추가한다. 기존 테스트의 고정 uploads/collection 탭 기대값은 새 사용자 탭 흐름으로 바꾸되 업로드 표시/보안 검사를 삭제하지 않는다.
- [ ] **6. 커밋:** `feat: unify gallery and manage inactive designs`.

### Task 3: N01 배터리 그룹의 실제 중심 정렬

**Files:** `SanctuaryLayout.kt`, `ChargeStatusPanelRenderer.kt`, 기존 `SanctuaryGeometryTest.kt`, debug `SanctuaryReviewActivity.kt`의 검수 케이스만 확장.

**Interfaces:** `SanctuaryLayout.centeredBaseline(ascent:Float, descent:Float, centerY:Float):Float`. 숫자/%의 측정된 그룹 폭을 유지하고 공유 baseline은 실제 glyph bounds의 수직 중앙이 CENTER_Y가 되도록 정한다. 기존 CORE_RADIUS=142, CENTER=(432,718)을 임의 변경하지 않는다.

- [ ] **1. 실패 테스트:** `assertEquals(748f, SanctuaryLayout.centeredBaseline(-80f,20f,718f),0f)`와 조합 그룹의 좌우 여백 동등 검사를 추가한다. 현재 고정 baseline 754가 실제 숫자 중심과 다른지 디버그 glyph bounds를 표시해 확인한다.
- [ ] **2. RED:** `.\gradlew.bat testDebugUnitTest --tests '*SanctuaryGeometryTest'`.
- [ ] **3. 구현:** pure 계산은 `centerY - (ascent + descent) / 2f`. 실제 숫자는 Paint.getTextBounds 결과를 쓰고 %가 글자 높이 기준을 바꾸지 않게 숫자 baseline에 맞춘다. 숫자+%의 가로 그룹은 기존 측정 방식으로 함께 중심 정렬한다. 숫자 높이 변경 시 구분선/상태 문구가 코어를 벗어나거나 숫자에 겹치지 않도록 같은 배치 함수에서 계산한다.
- [ ] **4. GREEN/시각 검사:** 0/9/69/100/미상, 글자 크기 1.0/1.3/2.0, 세 언어, 휴대폰 세로·가로/태블릿에서 코어 중심 십자와 실제 글자 bounds를 비교한다. debug 표시가 release에 포함되지 않게 한다.
- [ ] **5. 커밋:** `fix: center battery typography in N01 core`.

### Task 4: 내부 구조 7종 정밀 정리

**Files:** assets의 `direct-circles.js`(C11/C12/C21/C26/C30), `direct-extra.js`(A14), `circle-designs.js`(crimson-abyss), 새 `selftest/v113-art.test.cjs`, 기존 관련 디자인 검사.

**Interfaces:** 기존 `DirectCircles.svg(code)`, `DirectCircleExtra.svg(code)`, `CircleDesigns.svg(id,variant)`의 호출 형식을 유지한다. 신규 공통 렌더러로 나머지 도안을 전부 교체하지 않는다.

- [ ] **1. 도안별 기준을 고정.** 로컬 reference에서 해당 코드 원본을 열어 이전판과 1:1 배율 비교한다. C11은 다중 빨간 링, C12는 녹색 식물 계약 문양, C21은 대칭 계약 모티브, C26은 여덟 방향 수레바퀴, C30은 중심과 주변 소형 문장, A14는 시간의 톱니, crimson은 팔방 가시 봉인의 정체성을 기록한다. 코드에 없는 디테일을 완료됐다고 기록하지 않는다.
- [ ] **2. 실패 검사 추가.** 실제 중복된 기하 subpath·닫히지 않은 도형·접점 좌표를 코드별로 특정해 회귀 테스트로 고정한다. 렌더 노드 수가 많다는 것만으로 합격시키지 않는다. 기본 보안/경로 검사는 다음과 같다.

```js
const path = require('node:path');
require(path.resolve(__dirname,'../app/src/main/assets/direct-extra.js'));
require(path.resolve(__dirname,'../app/src/main/assets/direct-circles.js'));
for (const code of ['C11','C12','C21','C26','C30','A14']) {
  const svg = DirectCircles.svg(code);
  assert.doesNotMatch(svg, /<image\b|NaN|Infinity/);
  assert.match(svg, /data-fixed/);
}
// 기존 Playwright page에 SVG를 렌더하여 실제 닫힌 꽃잎 접점을 검사한다.
await page.setContent(DirectCircles.svg('C12'));
const petals = await page.locator('[data-petal]').evaluateAll(nodes=>nodes.map(n=>n.getAttribute('d')));
assert.ok(petals.length > 0);
assert.ok(petals.every(d=>/^M90 90\b/.test(d) && /90 90Z$/.test(d)));
```

- [ ] **3. RED 확인:** `& $node selftest/v113-art.test.cjs`. 중복/접점 회귀 항목이 이전판에서 실패해야 한다. 기본 보안 검사만 이미 통과하면 수정할 실제 시각 결함을 검사에 추가한 뒤 시작한다.
- [ ] **4. 도안별 구현.** C11/C30의 중심에 겹쳐 그린 동일 선을 합치고 보조선 광량을 낮춘다. C12의 꽃잎과 가지는 같은 접점 좌표를 공유한다. C26 방사 도형과 축 장식은 하나의 좌표에서 이어지게 한다. C21의 뿔/얼굴, A14의 시계/기어는 원본 대비 비율과 대칭을 맞춘다. crimson의 겹친 별/로제트/가시를 역할별 레이어로 나누고 closed Path의 stroke-linejoin을 일관되게 한다. 각 도안 수정 직후 이전/수정 캡처를 남기고 다음 도안으로 넘어간다.
- [ ] **5. GREEN/정적 비교:** 새 검사와 `direct-app.test.cjs`, `circle-designs.test.js`, `direct-extra.test.cjs`를 실행한다. 완성 정적 프레임과 7초 재생에서 팔망성·육망성 정지 및 이음새를 확대 확인한다. 원본과 남은 차이를 코드별 기록한다.
- [ ] **6. 커밋:** `refine: clean selected circle geometry`.

### Task 5: W 배경의 화면 연결·F 외부 부유물 제거

**Files:** `collection_circle.html`, `direct-circles.js`, `direct-extra.js`, `selftest/v113-art.test.cjs`, `selftest/direct-app.test.cjs`.

**Interfaces:** SVG 기본 호출은 보존한다. `DirectCircles.svg(code,{surface:'charge'})`, `DirectCircleExtra.svg(code,{surface:'charge'})`를 추가하고 전자의 전달 분기에서도 options를 넘긴다. 전 화면 충전 배경을 따로 그릴 때만 도안의 불투명 직사각형 바탕을 생략한다. 미리보기/기존 기본 호출은 기존 바탕을 유지한다. F의 particles/atmosphere 내용은 빈 상태여야 하지만 ornaments 전체를 제거하지 않는다(내부 여섯 노드도 들어 있음).

- [ ] **1. 실패 테스트:** F01~05에서 `[data-outline="particles"]`와 `[data-outline="atmosphere"]` 내용이 빈 문자열인지, `[data-petal]`이 6개이며 `[data-core-kind]`가 empty 3/diamond 3인지 실제 SVG DOM으로 검사한다. ornaments의 getBBox가 x/y 20~180 안에 들어가는지 확인하여 남은 외부 파편도 검출한다. 광원 복제 레이어에는 data 속성이 없어 실제 선 레이어만 검사한다. W01~05의 charge 모드에서 바깥 불투명 사각형이 없어야 하고 SVG 바깥도 같은 배경이 채워져야 한다.
- [ ] **2. RED:** `& $node selftest/v113-art.test.cjs` — 기존 F particles/atmosphere 및 W 불투명 바탕이 실패해야 한다.
- [ ] **3. 구현.** sixfold의 내부 노드를 보존하고 외곽 효과/파편 생성만 F 계열에서 차단한다. `collection_circle.html` 공통 motes도 F에서는 만들지 않는다. W 배경을 `.scene`의 제한된 artwork 박스가 아닌 viewport 전체 레이어로 이동하고 도형은 균일 fit으로 배치한다. 충전 코드/설명은 안전 영역에 올리되 나무나 핵심 링을 가리지 않는다.

```js
const floating = id[0] === 'F' ? '' : s.effects;
const dust = !dusty || id[0] === 'F' ? '' : particles(id,id[0] === 'G' ? 170 : 70);
// 기존 drawLayer('atmosphere', floating), drawLayer('particles', dust)에 전달한다.
```

sixfold()의 여섯 내부 node 반복문 뒤에 있는 다섯 외부 장식 분기는 각각 `if(k===1)`부터 `if(k===5)`까지 `if(alt && k===1)`부터 `if(alt && k===5)`로 바꾼다. F01 결정, F02 잎, F03 파편, F04 잎/꽃, F05 행성/별자리가 ornaments에 남지 않게 하고 G 계열은 그대로 보존한다.

- [ ] **4. GREEN/시각 확인:** 360×800, 412×915, 915×412, 800×1280, 1280×800에서 가장자리 색·잘림·배경 연속성을 확인한다. CSS opacity/time 제어를 재사용하여 같은 정적 프레임을 찍고, 이미지 편집으로 결함을 가리지 않는다. W03은 Task 7 네이티브 화면에서도 같은 기준으로 다시 확인한다.
- [ ] **5. 커밋:** `refine: blend W backgrounds and remove F debris`.

### Task 6: R01 Canvas 렌더러와 공유 렌더링 계약

**Files:** 새 `R01Renderer.kt`, `WallpaperArtwork.kt`, `ArtworkGeometry.kt`, `ArtworkGeometryTest.kt`; debug 검수 화면 연결.

**Interfaces:**
- `WallpaperArtwork(themeId:String)`: 최종 W03/R01만 받음. `prepare(width:Int,height:Int)`, `draw(canvas:Canvas,elapsedMs:Long,animated:Boolean)`, `close()`를 제공. static은 elapsed=0/animated=false, 라이브는 시간 기반. 현재 단계는 R01만 허용하고 다음 단계에서 실제 W03 구현을 등록한다. 빈 W03 renderer나 가짜 성공을 만들지 않으며 사용자 UI는 Task 8에서 연결한다.
- `R01Renderer`와 `W03Renderer`는 각각 `prepare(width,height)`, `draw(canvas,elapsedMs,animated)`, `close()`를 구현한다. 별도 추상 인터페이스 없이 WallpaperArtwork의 명시적 when 분기로 호출한다.
- `ArtworkGeometry.fit(width:Int,height:Int,designWidth:Float,designHeight:Float): Frame(scale:Float,left:Float,top:Float)`는 순수 계산. 0 이하 화면은 그리지 않는다.

- [ ] **1. 실패 테스트:** 1024 정사각 도안이 긴 화면과 가로 화면에서 동일 x/y 배율을 쓰는지, R01 꽃잎 6개 회전 각도(0/60/120/180/240/300), empty/diamond 교대, 외곽 반지름 [440,420,390,372], 중심(512,512)을 검사한다. 기하 상수는 실제 Path 생성에서도 같은 값을 사용한다.
- [ ] **2. RED:** `.\gradlew.bat testDebugUnitTest --tests '*ArtworkGeometryTest'`.
- [ ] **3. 기본 Path 구현.** SVG r01()의 실제 곡선 좌표를 옮긴다. 샘플 이미지 추적이나 범용 SVG 파서를 만들지 않는다. 꽃잎 하나를 닫힌 Path로 만들고 변환은 Canvas.save/rotate/restore로 반복한다.

```kotlin
val petal = android.graphics.Path().apply {
    moveTo(62f,0f); cubicTo(126f,-60f,196f,-100f,249f,-88f)
    cubicTo(297f,-78f,338f,-34f,366f,0f)
    cubicTo(322f,46f,281f,92f,245f,87f)
    cubicTo(182f,86f,111f,36f,62f,0f); close()
}
```

- [ ] **4. 레이어 이식.** 외곽 링, 76개 외곽 룬/30개 내부 문자, scaffold, petals, 6 nodes, 중심 코어를 각각 캐시한다. 그라데이션·광륜은 Canvas Shader/다중 선으로 생성하고 필터 기반 glow 복제와 원래 선을 혼동해 선을 이중 계산하지 않는다. live에서는 문자/외곽 링만 매우 느리게 움직이고 꽃잎/노드/중심은 고정한다.
- [ ] **5. GREEN/비교:** JVM 기하 테스트와 디버그 화면에서 1024 기준 캡처·실제 기기 캡처를 비교한다. hardware Canvas와 Bitmap Canvas(static) 모두에서 선·광량이 나오는지 검사한다. prepare를 20회 재호출해 크기 변화 후 메모리가 계속 늘지 않는지 확인한다.
- [ ] **6. 커밋:** `feat: render R01 with reusable Canvas paths`.

### Task 7: W03의 네이티브 정밀 이식

**Files:** 새 `W03Renderer.kt`; `WallpaperArtwork.kt`, `ArtworkGeometry.kt`, `ArtworkGeometryTest.kt`, debug 검수 화면.

**Interfaces:** Task 6 렌더링 계약을 소비한다. 설계 좌표는 1000×1778, 주요 링 중심 (500,831)을 기존 w03()에서 재확인한다. 전체 Canvas를 직사각 이미지처럼 중앙에 축소 배치하지 않고 배경과 핵심 구도를 분리한다.

- [ ] **1. 실패 테스트:** W03이 wallpaper dispatcher에서 허용되고, 도안 fit의 x/y 배율이 같으며 핵심 링이 화면 안에 놓이는지 검사한다. 30분 상당 elapsed에서도 나무/코어/근원 축 위치가 변하지 않는지 검사한다. 임의 숫자의 '디테일 개수'를 합격 기준으로 쓰지 않는다.
- [ ] **2. RED:** `.\gradlew.bat testDebugUnitTest --tests '*ArtworkGeometryTest'`와 디버그 W03 미리보기(미구현 화면은 합격 아님).
- [ ] **3. 정적 구조 이식.** w03()의 줄기→가지→잎 부착점→뿌리, 링·룬, 상하 문장/달, 코너 잎, 하단 행성 순으로 각각 옮긴다. 뿌리/가지 끝과 잎 시작점을 공유하고 난수는 고정 seed를 사용한다. 먼저 구조 비교를 통과시킨다.
- [ ] **4. 배경/광원 이식.** SVG의 turbulence/displacement/blur를 저장 이미지로 굳히지 않는다. 기존 CosmicBackgroundRenderer의 코드 생성 노이즈/캐시 패턴을 참고하여 W03의 청색 성운과 얇은 금빛 광륜을 직접 그린다. 배경은 viewport 전체를 채우고 나무 핵심은 균일 fit한다. Shader 미지원에서도 같은 필수 구조·색 계층이 유지되게 한다.

```kotlin
// WallpaperArtwork의 생성 시점에만 결정한다. 프레임마다 새 renderer를 만들지 않는다.
private val w03 = if (themeId == "ref-W03") W03Renderer() else null
private val r01 = if (themeId == "ref-R01") R01Renderer() else null
// draw(): 해당 renderer만 호출. 잘못된 themeId는 생성자에서 거부한다.
```

- [ ] **5. GREEN/정밀 검수:** 원본·기존 SVG·네이티브 정적 화면을 같은 구도로 비교한다. 코너와 링 외부 배경 경계, 잎/뿌리 접점, 광원 과포화, 긴 휴대폰/태블릿 잘림을 각각 기록한다. 미세 움직임은 구조 검수 후 추가하고 정적 출력과 라이브 첫 프레임을 비교한다. 광원 차이를 숨긴 채 '완벽 재현'이라고 보고하지 않는다.
- [ ] **6. 커밋:** `feat: render W03 garden with native layered paths`.

### Task 8: 정지·라이브 적용과 생명주기

**Files:** 새 `WallpaperPolicy.kt`, `WallpaperController.kt`, `MagicWallpaperService.kt`, `WallpaperPolicyTest.kt`, `app/src/main/res/xml/magic_wallpaper.xml`; Manifest, 3개 언어 strings, MainActivity, gallery; Android 검사에 `WallpaperChecks.kt` 추가.

**Interfaces:**
- `WallpaperPolicy.supportsStill(sdk:Int,target:String):Boolean`, `allowedTheme(id:String):Boolean`, `shouldRender(visible:Boolean,surfaceReady:Boolean,destroyed:Boolean):Boolean`, `confirmed(requested:String,actual:String?):Boolean` — 순수 함수.
- `WallpaperController(Activity)`: `show(theme:String,target:String)`, `onActivityResult(requestCode:Int,resultCode:Int,data:Intent?):Boolean`. show에서 정지/라이브·미리보기·확인을 제공하고 실제 적용은 최종 확인 뒤에만 실행한다.
- `MagicWallpaperService`는 공유 engine을 갖고 `W03WallpaperService`와 `R01WallpaperService` 두 작은 하위 클래스를 같은 파일에 둔다. 각 컴포넌트는 고정 themeId만 사용하며 현재 충전 선택값을 읽지 않는다.
- 각 engine은 자신의 WallpaperArtwork와 프레임 예약을 소유한다. static 렌더러와 mutable Paint/Path 인스턴스를 다른 스레드에서 공유하지 않는다.

- [ ] **1. 실패 테스트 추가.**

```kotlin
@Test fun wallpaperLimitsAndLifecycle() {
    assertFalse(WallpaperPolicy.supportsStill(23,"lock"))
    assertTrue(WallpaperPolicy.supportsStill(24,"lock"))
    assertFalse(WallpaperPolicy.allowedTheme("native-N01"))
    assertTrue(WallpaperPolicy.allowedTheme("ref-W03"))
    assertFalse(WallpaperPolicy.shouldRender(false,true,false))
    assertFalse(WallpaperPolicy.shouldRender(true,false,false))
    assertFalse(WallpaperPolicy.shouldRender(true,true,true))
    assertFalse(WallpaperPolicy.confirmed("R01",null))
    assertFalse(WallpaperPolicy.confirmed("R01","W03"))
}
```

- [ ] **2. RED:** `.\gradlew.bat testDebugUnitTest --tests '*WallpaperPolicyTest'`.
- [ ] **3. 정지 적용 구현.** 첫 화면의 '배경화면 변경'/'잠금화면 변경' 버튼에서 target=home/lock을 고정하고 W03/R01→정지/라이브→미리보기→확인 순서로 진입한다. Android 쪽도 allowedTheme와 MediaLibrary.available를 검사하고 target은 home/lock 두 값만 허용한다. 비활성화로 이미 적용된 시스템 화면을 변경하지 않는다. 실제 viewport를 균일 축소하여 최대 6,000,000 pixels의 ARGB 결과를 배경 작업에서 생성한다. static 출력에는 배터리와 충전 상태를 넣지 않는다. SET_WALLPAPER만 추가하고 INTERNET/저장소 전체 접근 권한은 추가하지 않는다. API24+는 `setBitmap(bitmap,null,false,FLAG_SYSTEM 또는 FLAG_LOCK)`, API23은 home만 기존 overload를 사용한다. isWallpaperSupported를 확인하고 isSetWallpaperAllowed는 API24+에서만 호출한다. IOException/SecurityException/메모리 부족을 사용자 오류로 처리한다. 적용 실패를 라이브나 다른 대상으로 자동 재시도하지 않는다.
- [ ] **4. 두 라이브 항목 등록.** 서비스는 exported=true이되 `android.permission.BIND_WALLPAPER`로 보호한다. XML은 wallpaper metadata를 사용한다. 시스템에는 다음 공식 intent로 들어가며 비공개 target extra를 발명하지 않는다.

```kotlin
val component = android.content.ComponentName(activity,
    if (theme == "ref-W03") W03WallpaperService::class.java else R01WallpaperService::class.java)
val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
    .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
activity.startActivityForResult(intent, 31)
```

- [ ] **5. 취소/확인 처리.** 홈/잠금 대상은 시스템 선택 화면에서 사용자가 확인하게 한다. 잠금 단독을 선택할 수 없으면 취소하고 정지로 돌아갈 수 있게 안내한다. 시스템 반환만으로 성공 판정하지 않고 API34+ `getWallpaperInfo(which)`, 구버전 home `getWallpaperInfo()`로 요청 컴포넌트를 대조한다. W03WallpaperService 컴포넌트는 W03, R01WallpaperService는 R01, 나머지는 null로 변환하여 confirmed에 전달한다. 값이 null/권한 제한/다른 대상이면 '확인 필요'다. 기존과 같은 컴포넌트의 미리보기 취소는 '기존 적용 유지'로 표시하고 새 적용 성공이라고 표시하지 않는다. 기존 라이브 엔진이 다른 도안 preview 상태를 읽지 않으므로 취소가 기존 도안을 바꾸지 않는다. 적용 기록은 충전 선택과 별도 SharedPreferences에 저장하고 실제 확인된 성공에서만 갱신한다. 이 기록을 live 도안 선택의 입력으로 사용하지 않는다.
- [ ] **6. Surface 루프 구현.** 엔진별 HandlerThread 한 개에서 prepare/draw를 직렬화하고 main callback은 크기/표시 상태를 전달한다. visible=false, onSurfaceDestroyed, onDestroy는 예약 취소, 세대값 증가로 오래된 작업 무효화, 종료 후 thread 정리를 수행한다. 최대 30fps 간격으로 재예약하고 이미 pending인 frame을 중복 등록하지 않는다. API26+ SurfaceHolder.lockHardwareCanvas, 이전은 lockCanvas를 사용하며 잠금 성공 시 finally에서 unlockCanvasAndPost를 호출한다. Surface 소멸 예외는 루프를 멈추고 새 surface 이벤트를 기다린다. WallpaperService에서 wake lock/화면 켜기/터치 가로채기/배터리 polling을 하지 않는다.
- [ ] **7. GREEN/실기기 확인.** 새 `WallpaperChecks.run(context:Context):Unit`은 두 렌더러를 Bitmap Canvas에 그려 예외/빈 출력, prepare 반복, close 중복을 검사하며 시스템 배경화면은 바꾸지 않는다. Task 1 runner의 LibraryStorageChecks 다음에 호출한다. 순수 정책 검사, W03 적용→R01 preview 취소→W03 유지, 반대 경우, 앱 회전/복귀, home/lock 결과 대조, 숨김/Surface 반복 20회, 재부팅 후 복원, 서비스 전환 중 crash를 확인한다. 기기 선택/최종 시스템 적용/접근성 승인은 사용자가 진행한다. 확인할 수 없는 조합은 미검증으로 기록한다.
- [ ] **8. 커밋:** `feat: add W03 and R01 still and live wallpapers`.

### Task 9: 동일 도안을 갤러리·미리보기·충전에 연결

**Files:** `ThemeSelection.java`, `MainMagicChargeView.kt`, `MainActivity.java`, `ChargingAccessibilityService.java`, `WebViews.java`, `WallpaperArtwork.kt`, gallery.js, 기존 SanctuaryIntegrationTest, Android WallpaperChecks.

**Interfaces:** `ThemeSelection.isNative(String id)`는 N01/W03/R01만 true. `MainMagicChargeView @JvmOverloads constructor(context:Context, themeId:String="native-N01")`로 확장하고 기존 start/stop을 유지한다. N01 렌더링은 보존하며 W03/R01 분기에서 WallpaperArtwork를 사용한다. WallpaperArtwork의 코드 생성 썸네일은 앱 내부 고정 endpoint 두 개에서만 전달한다.

- [ ] **1. 실패 테스트:** native 세 ID와 moon/ref-F01의 WebView 구분, native CONNECT→NATIVE_READY→DISCONNECT 10회, 완료 중 새 CONNECT, 오래된 run callback 무효화 검사를 작성한다. Native 변경을 이유로 7초 타이머를 추가하거나 늘리지 않는다.
- [ ] **2. RED:** `.\gradlew.bat testDebugUnitTest --tests '*SanctuaryIntegrationTest'`.
- [ ] **3. 모든 호출부 연결.** MainActivity.showPreview와 ChargingAccessibilityService.showOverlay가 선택 theme을 한 번 캡처하여 같은 native 판정/생성자를 사용한다. MainMagicChargeView는 W03/R01 배경을 그릴 때 N01 배경·패널을 덧그리지 않고 충전용 코드/배터리 문구만 별도로 올린다. view detach/stop에서 receiver와 예약 frame을 idempotent하게 정리한다. 미리보기 종료·오류 경로의 cleanup도 빠뜨리지 않는다.

```java
String theme = WebViews.selectedTheme(this);
boolean nativeTheme = ThemeSelection.isNative(theme);
View view = nativeTheme ? new MainMagicChargeView(this, theme) : WebViews.magicCircle(this);
// 기존 start(), NATIVE_READY 및 finally/stop 경로는 그대로 공유한다.
```

- [ ] **4. 네이티브 썸네일 연결.** `https://appassets.androidplatform.net/generated/ref-W03.png`와 ref-R01.png만 LocalClient가 처리한다. URL 검증은 host/scheme/path/query/fragment를 정확히 대조한다. 같은 Canvas 코드로 최대 512×910 이미지 두 개만 생성·캐시하여 반환한다. 외부 참조 이미지를 쓰지 않고 네트워크 fallback도 없다. 갤러리 hero/card는 이 endpoint를 쓰고 실제 preview는 native로 연다. 브라우저 단독 검토에서는 기존 SVG를 '브라우저 참고 미리보기'로 명시한다.
- [ ] **5. GREEN/통합 검사:** JVM/브라우저+Android 실제 선택 상태 유지, native/웹 도안 번갈아 연결, 앱 전면/백그라운드 재연결 각각 10회, 재생 중 분리, 화면 잠금/꺼짐, 비행기 모드, 라이브와 충전 동시 실행을 검사한다. 접근성 권한이 없으면 사용자에게 허용을 요청하고 자동으로 켜지 않는다. ADB 배터리 모의 상태만으로 실제 USB 재연결 검증을 대신하지 않는다.
- [ ] **6. 커밋:** `feat: share native artwork across charging and wallpaper previews`.

### Task 10: 버전 증가·전체 검증·다운로드 APK

**Files:** `app/build.gradle.kts`, `../.github/workflows/android-debug.yml`, README, 새 `docs/V1_13_REVIEW_KO.md`; 기존 테스트는 실제 변경 동작에 해당하는 기대값만 수정한다.

**Interfaces:** 배포 버전 1.13/code16과 CI metadata 검사가 일치한다. 이미 설치된 앱의 서명과 호환되는 로컬 APK와 CI 임시 서명 APK를 구분한다.

- [ ] **1. 배포 검사부터 갱신해 RED 확인.** metadata의 기대 버전을 1.13/16으로 정하고 기존1.12 APK로 실패하는지 확인한다. 앱 versionName/versionCode와 CI asset/checksum/tag/title 값에 남은1.12 참조를 함께 갱신한다. 과거 문서/릴리즈 기록의 버전은 일괄 치환하지 않는다.
- [ ] **2. 전체 검사 실행.**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Android verification failed' }
Get-ChildItem selftest -File | Where-Object Name -Match '\.test\.(cjs|js)$' | ForEach-Object {
    & $node $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Browser/design test failed: $($_.Name)" }
}
& .\selftest\review-apk.test.ps1
if (!$?) { throw 'APK packaging check failed' }
Get-FileHash -Algorithm SHA256 'app\build\outputs\apk\debug\app-debug.apk'
```

- [ ] **3. 실패를 분류하고 원인을 수정.** Windows의 magic-circle-design.ps1에는 기존 HTML 기대값 차이가 알려져 있으므로 해당 검사도 실행해 이번 회귀인지 구분한다. Assertions를 단순 삭제하거나 항상 통과하게 만들지 않는다. 새도안 Canvas로 옮겨 더 이상 제품 경로가 아닌 SVG 검사는 비교용 검사로 명시한다. 공개 CI에서 unit/lint/assemble 성공만으로 전체 시각·기기검증 완료라고 하지 않는다.
- [ ] **4. APK/성능 검증.** min23/target37/version16/1.13, INTERNET 부재, 두 BIND_WALLPAPER 서비스 보호, release에 debug/test 진입점 부재, stale asset 부재를 실제 APK로 확인한다. 지원 기기/API23 에뮬레이터가 없으면 해당 항목은 미검증이다. 연결 기기 모델을 조회하고 사용자 승인 후 호환 서명 APK를 `adb install -r`로만 설치한다. 서명 불일치 때문에 기존 앱을 삭제하지 않는다.
- [ ] **5. 실기기 검수표 작성.** 20회 라이브 show/hide/회전 뒤 안정화 메모리, 충전과 동시 실행의 프레임/메모리, 30fps 상한과 숨김 렌더 정지, 저장 오류, 새 설치/업데이트를 기록한다. S26 Ultra/Tab S11 Ultra를 직접 검사하지 못했다면 다른 모델 결과로 대신 합격시키지 않는다. 비교 자료는 `evidence/v113/`에 보관하고 개인정보 포함 화면·로그는 Git에 올리지 않는다.
- [ ] **6. 검토·커밋 후 게시.** 요구사항별 실제 증거를 바탕으로 branch review를 수행한다. 표준 공개 GitHub Actions만 사용하고 workflow의 정확한 브랜치 제한을 유지한다. 성공 run을 확인한 뒤 고유 prerelease와 모바일 다운로드 링크를 검증한다. 기존 설치용 APK는 로컬 서명으로 별도 이름을 붙이며 개인 키를 CI/Git에 업로드하지 않는다. 원격에서 다시 받은 APK의 SHA-256까지 확인한다. main 병합은 하지 않는다.
- [ ] **7. 최종 보고:** 다운로드 링크, 버전, 실제 검사한 기기, 활성/비활성 개수, 원본 대비 남은 시각 차이, 미검증 기능을 한국어로 짧게 제공한다. `docs/V1_13_REVIEW_KO.md`에는 코드별 상세 표와 테스트 실패/제약을 남긴다.

## 계획 자체 검토·실행 인계

- 승인 설계 1~4절 → Task 1~2, 5절 → Task 6~9, 6절 → Task 3~7, 7절 → 각 파일/인터페이스, 8절 → Task 10.
- 현재 단계에서는 이 문서와 설계 승인 상태만 수정한다. 위 체크박스는 실제 구현/검증을 마칠 때만 체크한다.
- 권장 실행 방식: **단계별 에이전트 구현·독립 검토**. 저장 이전과 시스템 배경화면처럼 잘못 적용하면 사용자 상태에 영향을 주는 경계가 있어 단계별 확인 가치가 크다. 서로 다른 도안의 읽기 전용 비교는 병렬화하되 같은 JS/Java 파일 편집은 직렬화한다.
- 이전에 요청한 단계별 에이전트 방식을 유지한다. 사용자가 이 구현 계획이 요청을 반영했는지 검토한 뒤 실행하며, 실행 방식을 다시 고르도록 요구하지 않는다.

## 공식 자료

- [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager): 정지 대상 API24+, live 시스템 preview intent, API34 대상별 현재 컴포넌트 확인.
- [WallpaperService.Engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine): visible/Surface 생명주기, isPreview, API34 wallpaper flags.

Context7 검색 결과가 WallpaperService와 무관한 View 문서를 반환하여, 위 Android 공식 원문에서 필요한 API를 직접 확인했다. 플랫폼의 문서화된 기능과 삼성 기기별 UI 지원 범위는 별도로 검증한다.
