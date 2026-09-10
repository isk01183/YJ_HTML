# 7초 충전 애니메이션 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 충전 연결 시 최대 7초짜리 단계형 애니메이션을 한 번 재생하고 원래 화면으로 복귀한다.

**Architecture:** 기존 `AccessibilityService`와 `WebViews.magicCircle()`을 재사용한다. 순수 Java 상태 전이로 연결·완료·분리를 관리하고 Android 메인 스레드의 지연 실행으로 7초 뒤 오버레이를 제거한다.

**Tech Stack:** Java 17, Android AccessibilityService, WebView, HTML, CSS, SVG

**Spec:** `docs/superpowers/specs/2026-09-10-charging-accessibility-overlay-design.md`

## 전역 제약

- 전체 표시 시간은 최대 7초다.
- 인터넷 권한이나 새 의존성을 추가하지 않는다.
- 분리하면 7초 전이라도 즉시 종료한다.
- 같은 연결 상태에서는 중복 재생하지 않는다.

---

### 작업 1: 재생 상태 전이

**Files:**
- Modify: `selftest/com/yj/magiccircle/ChargingTransitionSelfTest.java`
- Modify: `app/src/main/java/com/yj/magiccircle/ChargingTransition.java`

**Interfaces:**
- Consumes: 현재 `State`와 `Event.CONNECT`, `Event.FINISH`, `Event.DISCONNECT`
- Produces: 다음 `State.DISCONNECTED`, `State.PLAYING`, `State.COMPLETE`

- [x] 테스트를 새 상태 전이에 맞게 작성하고 컴파일 실패를 확인한다.
- [x] 최소 상태 전이를 구현하고 독립 실행형 테스트 통과를 확인한다.

### 작업 2: 7초 생명주기와 화면 연출

**Files:**
- Modify: `app/src/main/java/com/yj/magiccircle/ChargingAccessibilityService.java`
- Modify: `app/src/main/assets/magic_circle.html`

**Interfaces:**
- Consumes: 충전 방송과 7초 완료 콜백
- Produces: 연결당 한 번 표시되고 최대 7초 뒤 제거되는 오버레이

- [x] `PLAYING` 진입 시 표시와 7초 종료 예약을 수행한다.
- [x] `PLAYING` 이탈 시 예약을 취소하고 오버레이를 제거한다.
- [x] HTML/CSS/SVG를 네 단계 타임라인으로 교체한다.

### 작업 3: 전체 검증과 배포 준비

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: 완성된 소스와 로컬 자산
- Produces: 설치 가능한 디버그 APK

- [x] 상태 테스트, Gradle 테스트, lint, APK 빌드를 실행한다.
- [x] APK에 서비스와 로컬 자산이 포함되고 인터넷 권한이 없는지 확인한다.
- [ ] diff를 검토한 뒤 커밋하고 GitHub 분기에 푸시한다.
