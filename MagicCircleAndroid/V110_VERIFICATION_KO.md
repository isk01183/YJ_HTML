# v1.10 검증 기록

## 실제 변경

- 55개 기존 도안 + 첨부 118개 = 기본 선택 항목 173개. 동일 모티브의 변형 포함.
- 카탈로그와 Android ID 목록의 번호 일치, 한국어·일본어·영어 이름, 번호 검색, 숨기기·복원 지원.
- 원본 마스터는 보존. 앱용 SVGZ는 닫힌 벡터 경로이며 PNG/JPG를 내부에 삽입하지 않음.
- 썸네일은 실제 앱용 벡터에서 렌더한 320×320 PNG. 충전 시에는 SVGZ를 압축 해제해 SVG로 표시.
- SVG는 페이지 로딩과 함께 요청하고 네이티브 재생 시작 지시를 기다림. 준비 시간·애니메이션 모두 원래 연결 시점의 최대 7초 안에서 처리.
- 어두운 RGB 8개 농도층은 보존하고 나머지는 인접층을 합친 20단계 프로필을 사용. 모든 유지 경로의 좌표는 동일하며 C03/R01은 원래 정밀 벡터를 그대로 유지. 압축은 표시 해상도를 낮추는 방식이 아님.
- 반복 시작 호출로 새 파일을 요청하거나 재생 시간을 연장하지 않음. 분리·재연결 서비스 로직은 보존.
- 이전 모든 항목을 숨긴 무선택 상태가 새 도안 추가 때문에 자동 해제되는 회귀 수정.

## 검사 방법

Java 자체 테스트 5개: `ChargingTransitionSelfTest`, `CollectionCatalogSelfTest`, `LanguageSelectionSelfTest`, `MediaValidationSelfTest`, `ThemeSelectionSelfTest`.

브라우저/정적 검사: `circle-designs.test.js`, `new-circle-designs.test.js`, `languages.test.js`, `circle-browser.test.js`, `languages-browser.test.js`, `media-browser.test.js`, `collection.test.cjs`, `collection-browser.test.cjs`, `art-quality.test.cjs`, `vector-pack.test.cjs`, `collection-assets.test.cjs`.

`collection-assets.test.cjs`는 최종 118개 압축 파일과 SHA-256, 닫힌 경로, 이미지 미삽입, 썸네일 크기를 확인하고, 오프라인 Edge에서 실제 SVG를 모두 표시해 화면을 캡처합니다. 가짜 단일 원을 반환하는 UI 단위 검사와 구분됩니다. Android의 자체 WebView를 실행하는 검사는 아닙니다.

빌드 명령: `gradlew.bat --offline test lintDebug assembleDebug`. Gradle의 JUnit 대상은 `NO-SOURCE`이므로 위 Java 검사를 별도로 수행합니다.

## 2026-09-15 최종 PC 검사 결과

| 항목 | 결과 |
|---|---|
| Java 자체 검사 5종 / 기존·추가 브라우저 검사 | 통과 |
| 최종 SVGZ 118개 / PNG 썸네일 118개 / 해시 | 통과 |
| 실제 118개 SVG 화면 캡처 | 모두 표시됨; 480×1040 CSS, DPR 3 |
| 가장 오래 걸린 실제 첫 화면 | W04 3,257ms; W03 2,778ms; 모두 PC 7초 이내 |
| 배포 APK 파일 크기 | 217,172,937 bytes (약 217MB) |
| 버전 | 1.10 / versionCode 13 |
| Android Gradle 빌드 / lint | 성공 / 오류·경고 0건 |
| APK 구성 | 접근성 충전 서비스 포함; INTERNET 권한·DreamService 없음 |
| APK 서명 | v1.9와 같은 인증서; 검증 성공 |

Android 빌드 도구 `aapt dump badging`의 시스템 아이콘 해석 경고는 기존 v1.9에서도 동일하게 재현됩니다. 아이콘은 기존 `@android:drawable/ic_menu_view` 참조이며 이번에 수정하지 않았습니다. 버전·서비스는 `aapt dump xmltree`로 별도 확인했습니다. APK 검증기가 출력한 AGP 메타데이터 서명 경고도 기록하며, 실제 APK 인증서 검증은 성공했습니다.

실제 Git 작업 폴더에서 `--offline --rerun-tasks test lintDebug assembleDebug`로 다시 빌드했습니다. 임시 작업본과 APK 전체 해시는 ZIP 항목 순서·배치 때문에 달랐지만, 내부 265개 파일의 압축 해제 후 SHA-256은 전부 같았고 서명 인증서도 일치했습니다. 배포 파일 SHA-256: `EF9B7A35DA831A4322A0173766DEFC733B757A56772EAD0D5419DB7E8862DFD5`.

## 남아 있는 한계

- A 그룹의 제목이 가린 선·룬과 세계수의 완전한 개체별 레이어 분리는 미완료. 정확한 원작 벡터 복원이나 전 도안의 초정밀 수작업 완성을 주장하지 않음.
- 앱용 농도층 정리로 색의 경계가 원본 마스터와 다를 수 있음. 참고 원본 자체의 저해상도·발광·가려짐도 남음.
- 현재 `adb devices -l`에 연결된 기기가 없음. Galaxy S26/Note20/태블릿의 실제 WebView·백그라운드 절전·잠금 화면·충전선 재연결은 이번 버전에서 미검증.
- 새 권한, 유료 API, 외부 라이브러리, 인터넷 접근을 추가하지 않음. 기존 서명으로 업데이트하며 앱 데이터 초기화·main 병합을 하지 않음.

## 휴대폰에서 확인할 순서

1. GitHub Release의 `MagicCircleCharging-v1.10-debug.apk`를 내려받아 기존 앱 위에 업데이트합니다.
2. 앱의 `첨부 도안 · 118` 탭에서 C03을 고르고 `이 마법진 적용`을 누릅니다.
3. 미리보기 후 앱을 닫고 충전선을 연결 → 분리 → 재연결을 3회 확인합니다.
4. W03 같은 큰 도안, 잠금 상태, 비행기 모드에서도 확인합니다.
5. 문제가 있으면 도안 번호, 기기 이름, 첫 연결/재연결 여부를 알려 주세요. 접근성 권한을 임의로 자동 승인하거나 잠금을 우회하지 않습니다.
