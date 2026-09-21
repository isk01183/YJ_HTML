# 직접 그리기 대표 도안과 재연결 진단 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. 원본과 다른 결과를 완료로 표시하지 않는다.

**Goal:** 전체 118종의 새 제작 기준을 대표 3종으로 검증하고 연결된 S26 Ultra 재연결 실패의 실제 원인을 찾아 수정한다.

**Architecture:** 참고 이미지 표시 경로와 코드 그림 경로를 분리한다. 새 정밀 도안은 SVG DOM을 직접 생성하고 각 역할별 레이어를 독립 제어한다. 현재 Android 충전 서비스는 증거 기반 최소 수정만 한다.

**Tech Stack:** 기존 Java Android/WebView, vanilla HTML/CSS/JS/SVG, Node assert, 설치된 Playwright/Edge.

**Spec:** ../specs/2026-09-15-direct-circles-design.md

## Global Constraints
- 원본 앱과 자료 삭제 금지. main 병합 금지. API/네트워크 이미지/새 의존성 금지.
- 118종 전체 목표이며 이번 품질 확인은 C03/R01/W03. 다른 115종을 완료로 표시하지 않는다.
- 원·룬·기하·코어·궤도·입자는 의미별 개별 DOM. bitmap/자동 색상 윤곽 사용 금지.
- 최대 7000ms, 큰 별과 나무 줄기는 고정, 모션 축소, 원 종횡비 보존.
- 기존 55종·3개 언어·업로드·숨김/복원·오프라인은 유지한다.

### Task 1: 대표 도안의 직접 기하와 비교 화면
**Files:** 새 design-studies/direct-v111/{index.html,direct-circles.js,direct-circles.css,direct-circles.test.cjs,README_KO.md}.
**Interfaces:** `DirectCircles.ids` = ['C03','R01','W03']; `DirectCircles.svg(id)`는 정적 inline SVG 문자열; `DirectCircles.mount(element,id)`는 새 DOM 설치 후 `{play(),stop()}` 반환. 전체 목록 118개 대비 상태를 3종 검토/115종 대기로 표시한다.
- [ ] 실사용 SVG 파싱 후 circle/path/독립 레이어 존재와 외부 리소스 없음, 같은 id는 동일 정적 경로, 닫힌 기하 검사. `node design-studies/direct-v111/direct-circles.test.cjs`를 기능 미구현 상태에서 실패시킨다.
- [ ] C03 수치 기하/명확한 룬 획을 재사용하되 원래 픽셀 윤곽 JSON은 읽지 않는다. R01 여섯 cubic 부채꼴과 교대 빈/다이아 코어를 직접 구성한다. W03 지배 가지·잎·달·꽃·주변 고리를 수동 path로 그린다.
- [ ] JS DOM 생성은 `<circle>`, `<path>`, `<g>` 등만 사용한다. 눈에 보이는 윤곽과 glow를 따로 둔다. CSS keyframes/경과 시간으로 독립적인 선 그리기·빛·고리·입자를 구현한다.
- [ ] 비교 화면은 원본과 생성 도안을 같은 비율로 나란히 두고 정적/재생/정지·레이어 보기 및 알려진 차이를 한국어로 표시한다. 원본은 참조란에서만 쓰고 새 그림 DOM에는 이미지/외부 font/리소스를 넣지 않는다.
- [ ] 412×915 및 태블릿 1280×800, 확대 캡처에서 가장자리/닫힌 윤곽/0ms/3500ms/7000ms 및 재생 재시작을 확인한다. 실패 테스트→통과 로그를 남긴다.
- [ ] 대표 도안 결과를 독립 리뷰 후 사용자에게 비교 화면을 보여준다. 승인 전 기존 118종 등록을 일괄 교체하지 않는다.

### Task 2: S26 Ultra 재연결 원인 검증과 최소 수정
**Files:** 진단 evidence/, 원인이 확인되면 ChargingAccessibilityService.java/ChargingTransition.java 또는 WebViews.java 중 실제 원인 파일만 수정. 대응 selftest.
**Interfaces:** 기존 `startChargingAnimation(remainingMs)`와 7000ms 연결 시점 예산 유지.
- [ ] ADB 장치 목록을 확인한 후에만 모델/버전/접근성 상태와 MagicCircleCharging 태그를 읽는다. 현재 MTP만 존재하면 USB 디버깅 허용 단계에서 기다린다.
- [ ] 앱을 열지 않은 상태에서 실제 분리→연결을 사용자에게 요청하고 연결 이벤트/서비스 바인딩/renderer 시작/종료를 확인한다. 장치가 사라지는 USB 구간은 재인식 후 누적 로그를 읽는다.
- [ ] 로그로 입증된 원인을 재현하는 작은 실패 테스트를 먼저 만든다. 등록 누락, renderer 실패, 캐시 지연 등 원인이 다르면 같은 우회책을 임의 적용하지 않는다.
- [ ] 최소 수정을 적용하고 관련 Java selftest와 전체 Gradle test/lintDebug/assembleDebug를 실행한다. USB 디버깅으로 업데이트 설치 시 사용자 데이터 보존한다.
- [ ] 동일 기기에서 백그라운드/화면 켜짐/잠금 상태를 구분해 최소 3회 재연결 확인. 못 한 범위는 미검증으로 기록한다.

### Task 3: 검증 및 다음 제작 목록
**Files:** design-studies/direct-v111/README_KO.md 및 evidence/ 결과.
- [ ] 원본 앱이 변경되지 않았는지 git diff/status로 확인하고 실기기 수정은 원본 백업 후 필요한 파일만 반영한다.
- [ ] 도안 완성/검토 대기/미작업 수를 정확히 보고하고 대표 3종 확인을 받는다. 후속은 C/A/B/E/U의 개별 기하, F/G 장식, W 나머지의 순서로 각 원본과 비교한다.
- [ ] 배포본만 버전 증가 및 서명/포함 자산/링크 검증. 미완성 118종을 완성 APK로 게시하지 않는다.
