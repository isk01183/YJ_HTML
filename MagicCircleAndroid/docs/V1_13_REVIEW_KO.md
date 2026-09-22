# v1.13 로컬 검증 기록

## 배포 상태

- 앱 버전: **1.13 / versionCode 16**, minSdk 23, targetSdk 37.
- 목록: **174개 선택 항목**, 기본 활성 **43개**, 비활성 **131개**, 첨부 도안과 썸네일 각 **118개**.
- 언어와 연결: 한국어·일본어·영어를 오프라인으로 유지한다. 최종 APK에는 `INTERNET` 권한이 없다.
- 로컬 업데이트 파일: [`MagicCircleCharging-v1.13-local-update.apk`](../releases/MagicCircleCharging-v1.13-local-update.apk), 20,992,754 bytes, SHA-256 `9b427eee4b99f426d1edda7a659d18804e095d0c3b6608844a453ef42700b9f5`.
- 로컬 APK 인증서 SHA-256은 기존 설치된 v1.12와 같은 `a821c9377aed06c149b006d35f84c9977b459648c09d91886399f83470aad7a4`이다. 이는 서명 호환성 검사이며 실제 업데이트 설치 성공을 뜻하지 않는다.
- 공개 CI와 prerelease 게시·원격 재다운로드는 이 로컬 준비 단계에서 실행하지 않았다. CI가 만드는 `MagicCircleCharging-v1.13-debug.apk`는 임시 디버그 서명일 수 있으므로 위 로컬 업데이트 APK와 구분한다.

## 코드별 검토

| 코드 | 구현·회귀 보호 | 남은 원본 대비 차이 또는 제한 |
|---|---|---|
| N01 | 실제 glyph bounds로 숫자와 `%`를 한 그룹으로 중앙 정렬하고 divider/status 배치를 공유 계산으로 통합했다. | 실제 기기의 폰트 rasterization과 S26 Ultra/Tab S11 Ultra 화면은 미검증이다. |
| C11 | 보조선 밝기를 낮추고 눈·창·매듭 구조를 유지했다. | 중복 crisp 경로는 증명되지 않아 삭제하지 않았다. 미세 매듭과 안쪽 장식은 단순화가 남는다. |
| C12 | 큰 꽃잎과 뿌리 잎을 `(90,90)`, 위 잎을 `(90,65)`에 정확히 연결했다. | 원본의 촘촘한 유기적 중심과 교차 잎맥은 단순화되어 있다. |
| C21 | 좌우 대칭을 유지하며 얼굴·눈·이마 장식을 좁히고 뿔/귀 접점을 정리했다. | 유기적 골격선과 미세 룬은 원본보다 단순하다. |
| C26 | 네 축 bridge/ornament 연결을 유지하고 중복 축선·하단 다이아몬드·겹침을 정리했다. | 원본의 추가 wheel division과 작은 내부 글자는 없다. |
| C30 | 반경 49/16의 실제 중복 정사각형 두 개를 제거하고 위성 인장을 약화했다. | 정확한 룬 띠와 interlace는 근사다. |
| A14 | 기계부 비율을 줄이고 닫힌 24-tooth rim과 중심 고정 시곗바늘을 사용한다. | rim은 새로 작성한 형상이며 원본의 작은 기계 문양·glyph는 단순화되어 있다. |
| crimson-abyss | 여덟 tip/thorn과 고정 구조를 보존하고 opacity 시간만 애니메이션한다. | 기존 scale entrance는 제거되어 예전보다 등장 동작이 덜 역동적이다. |
| F01 | 바깥 particle/atmosphere/ornament를 제거하고 중심 도안을 유지했다. | 기존의 희미한 어두운 정사각 fill은 남는다. |
| F02 | 바깥 debris를 제거하고 내부 도형을 유지했다. | 동일한 F 계열 정사각 fill 제한이 남는다. |
| F03 | 바깥 debris를 제거하고 내부 도형을 유지했다. | 동일한 F 계열 정사각 fill 제한이 남는다. |
| F04 | 바깥 debris를 제거하고 내부 도형을 유지했다. | 동일한 F 계열 정사각 fill 제한이 남는다. |
| F05 | 바깥 debris를 제거하고 내부 도형을 유지했다. | 동일한 F 계열 정사각 fill 제한이 남는다. |
| W01 | SVG 비율은 유지하고 charge viewport 전체에 배경/mist를 이어 붙였다. | 브라우저 비교 결과이며 Android 하드웨어 출력은 아니다. |
| W02 | SVG 비율은 유지하고 charge viewport 전체에 배경/mist를 이어 붙였다. | 브라우저 비교 결과이며 Android 하드웨어 출력은 아니다. |
| W03 | 나무·뿌리·ring·rune·장식 좌표를 네이티브 Canvas 경로로 옮기고 preview/charging/wallpaper가 공유한다. | Canvas cloudlet은 SVG turbulence/displacement/filter의 근사이며, antialiasing·접합·halo는 하드웨어에서 보정하지 못했다. |
| W04 | SVG 비율은 유지하고 charge viewport 전체에 배경/mist를 이어 붙였다. | 브라우저 비교 결과이며 Android 하드웨어 출력은 아니다. |
| W05 | SVG 비율은 유지하고 charge viewport 전체에 배경/mist를 이어 붙였다. | 브라우저 비교 결과이며 Android 하드웨어 출력은 아니다. |
| R01 | petal, scaffold, rune, 교대 empty/diamond node와 중심을 캐시된 네이티브 Canvas Path로 옮겨 세 제품 경로가 공유한다. | native multistroke halo는 SVG Gaussian blur의 근사이며 실제 화면에서 밝기·부드러움을 보정하지 못했다. |

W03/R01의 보존된 SVG 페이지는 제품의 충전 renderer가 아니라 브라우저/참고 비교기다. geometry, 7초 종료, viewport, 오류와 시각 계약 검사는 그대로 유지한다.

## 로컬 검사 결과

| 검사 | 실제 결과 |
|---|---|
| JVM unit | 41 tests, failures 0, errors 0, skipped 0 |
| Android build | `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` 성공 |
| JavaScript/browser | 16개 selftest 모두 성공. 118개 실제 SVG 렌더, 174개 catalog, 43/131 활성 정책, 세 언어, offline, 7초, wallpaper UI 포함 |
| PowerShell | device diagnostics와 가짜 ADB device checker 성공. checker는 설치본의 1.13 이름과 code16을 각각 거부 조건으로 검사한다. `magic-circle-design.ps1`은 SVG 브라우저/참고 비교기로 성공 |
| APK assets | contour archive 없음, 공유 asset이 source와 동일, 썸네일 118개 |
| 실제 APK manifest | debug/release 모두 1.13/code16, min23/target37, `INTERNET` 없음. W03/R01 service 모두 `android.permission.BIND_WALLPAPER`로 보호 |
| release 분리 | release APK manifest/dex에 `SanctuaryReviewActivity`와 `V113Instrumentation` 없음; release `DebugReview`는 동작 없는 stub |
| 서명 | local-update APK V1/V2 검증 성공, 기존 v1.12와 인증서 SHA-256 일치 |

필수 version metadata 검사는 먼저 1.13/code16 기대값으로 바꾼 뒤 기존 1.12/code15 APK를 거부하는 RED를 확인했다. `sanctuary-device-check`도 1.13 fixture가 기존 1.12 기본값 때문에 실패하는 RED 뒤 기본값을 갱신했다. Windows HTML 검사는 기존 authored 값인 `spacingAndGlyphs`와 실행 중 세 언어 `runeVerse` 주입을 검사하도록 바로잡았으며, seal·octagram·join·orbit 검사는 삭제하지 않았다.

## 경고와 미검증 범위

- Java deprecated API 컴파일 안내는 기존 기준 경고다. JDK 25 `apksigner`의 native-access 미래 제한 및 `META-INF` app-metadata 안내도 서명 실패가 아니며 숨기거나 APK를 변조하지 않았다.
- 연결된 Galaxy S22(SM-S901N)는 읽기 전용으로만 확인했다. 사용자 설치 승인이 없으므로 설치/업데이트, instrumentation, 앱 실행, 접근성·배경화면 설정 변경을 하지 않았다.
- API 23 기기/에뮬레이터, S26 Ultra, Tab S11 Ultra, 잠금 전용 live 선택, USB 재연결, 20회 show/hide/회전 후 메모리, 충전 동시 frame/memory, 숨김 renderer 정지와 30fps 상한은 미검증이다.
- W03/R01 static/live frame-zero 동일성, 30분 후 고정 geometry, resize/cache/heap과 hardware Bitmap 검사는 runner에 준비되어 컴파일됐지만 실행하지 않았다.
- 공개 GitHub Actions 성공, prerelease URL, CI APK 서명·SHA-256, 원격 다운로드 파일의 재해시는 부모의 최종 전체 branch review와 게시 뒤 확인한다.

## 결정 기록

1. C11에는 증명된 중복 경로가 없어 geometry를 보존하고 보조선 luminance만 조정했다. 잘못 판단했다면 겹침이 남을 수 있어 이후 특정 경로를 다시 비교해야 한다.
2. crimson-only animation은 고정 geometry 요구를 우선해 opacity만 유지하고 예전 scale entrance를 제거했다. 사용자가 기존 등장감을 원하면 시각 검토 후 crimson에만 별도 동작을 복원한다.
