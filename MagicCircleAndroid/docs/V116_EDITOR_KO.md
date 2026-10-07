# v1.16 편집기 수정

버전: 1.16 / 설치 번호: 19. 기존 앱을 삭제하지 않고 같은 서명 APK로 업데이트합니다.

## 사용 방법

- 첫 화면 → 화면 직접 만들기 → 배경화면 또는 충전 애니메이션.
- 편집기의 **파일 추가**로 가져온 이미지는 편집 재료로만 저장됩니다. 완성 작품을 저장해야 일반 선택 목록에 나옵니다. 기존 일반 파일 업로드 기능은 그대로입니다.
- 레이어 목록에서 대상을 선택한 뒤 화면을 드래그하면 그 레이어만 움직입니다. 두 손가락으로 확대·축소하고 비틀면 회전합니다. 다른 레이어는 목록에서 선택하세요.
- 충전 정보의 **단계별 문구 편집**에서 연결 감지·충전 시작·완료 문구를 각각 바꿉니다. 단계당 120자까지 가능하며 빈 문구는 숨깁니다. 연결·충전·완료 버튼으로 해당 단계를 확인한 뒤 저장하세요.
- **기본 문구**는 앱 언어의 기본 문구로 되돌립니다. 사용자가 작성한 문구는 언어를 바꿔도 그대로 유지됩니다.
- 기존에 보관함에 들어간 편집 재료는 자동 삭제하지 않습니다. 해당 이미지의 **편집 재료로 숨기기**를 사용하세요. 파일과 기존 작품은 보존되고, 편집기의 보관함에서 계속 추가할 수 있습니다. 숨긴 이미지가 단독 충전 선택이었다면 다른 활성 도안으로 바뀝니다.

## 수정 범위와 원인

- MediaLibrary: 기존 `importDocument(uri,false)`의 false는 자동 선택만 막고 보관함 등록은 막지 않았습니다. 새 `editorOnly` 플래그와 전용 가져오기 경로를 추가했습니다. 기존 항목은 일반 업로드로 유지합니다.
- ScreenEditorView: 터치 시작 때마다 다른 이미지를 다시 선택하던 동작을 제거했습니다. 손가락 ID를 추적하고 손가락 추가·분리 때 기준 좌표를 다시 잡아 갑작스러운 이동을 막습니다.
- ScreenScene / ChargeInfoView / ScreenEditorActivity: 단계 문구를 정보 배치와 함께 저장·초안 복원·미리보기합니다. 문구는 코드가 아닌 일반 텍스트로 표시합니다.
- ChargingSceneView / charge-runtime.js: 기본 마법진과 사용자 작품 모두 요청한 단계의 미리보기 진행률을 사용합니다.
- MainActivity / WallpaperController / gallery: 편집 재료 숨김 확인창, 일반 목록 제외 및 단독 적용 차단을 추가했습니다. 작품 속 레이어 렌더링은 허용합니다.

## 검증 방법

`gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`

에뮬레이터 설치 후 `adb -s <serial> shell am instrument -w com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation`.

추가 계측 검사: EditorMediaChecks(목록 분리·파일/참조 보존·저장 실패), EditorGestureChecks(선택 고정·회전·포인터 전환), StageMessageChecks(문구 저장·복원·초기화·빈 문구·여러 줄 표시).

브라우저 검사: editor-gallery.test.cjs, charge-info-browser.test.cjs 및 기존 관련 검사. 실행에는 기존 개발 환경의 Playwright를 사용하며 앱 의존성은 추가하지 않았습니다.

실제 Samsung One UI의 충전·잠금화면 및 손가락 체감은 에뮬레이터만으로 보장할 수 없습니다. 기기에서 재확인이 필요합니다. 개인 설정·기존 작품·기존 업로드 파일을 일괄 삭제하지 않습니다.

## 2026-10-07 확인 결과

- 배포 소스(Gradle 9.5 / AGP 9.3): 단위 테스트 45개, 실패 0. 린트 오류 0·경고 8. APK와 계측 APK 빌드 성공.
- Android 16 / API 36 태블릿 에뮬레이터에서 배포 APK 설치 후 전체 계측 `V113_CHECKS_OK`. 화면에서 단계 문구 적용·이미지 추가·작품 저장 확인. 새 이미지의 `editorOnly=true`, 기존 업로드의 `false` 보존 확인.
- 관련 브라우저 검사 9종과 번역 검사 통과. 추가 코드 리뷰의 단계 미리보기/여러 줄 표시 지적을 반영하고 회귀 검사를 추가했습니다.
- 실제 USB 기기는 `unauthorized`여서 설치·실기기 성공을 주장하지 않습니다.
- Graphify AST 갱신 완료. Kotlin 4개 파일의 그래프 파서 경고는 남았지만 실제 Kotlin/Gradle 컴파일은 통과했습니다. 유료 의미 분석은 사용하지 않았습니다.
- 이번 변경과 무관한 사용자 그리기 코드·Gradle/AGP 변경은 보존하고 배포 소스에서 제외했습니다.
