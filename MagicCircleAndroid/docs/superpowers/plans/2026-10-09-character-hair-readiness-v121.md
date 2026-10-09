# E 캐릭터 헤어 파츠 준비·검증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** E 캐릭터의 원본을 보존하면서 헤어 변경용 자료를 검사하고, 얼굴·의상·표정을 함께 바꾸지 않는 실제 파츠 교체에 필요한 근거와 실제 렌더 기준 화면을 확보한다.

**Architecture:** 기존 오프라인 VRM 뷰어와 에뮬레이터 검사를 재사용한다. 새 코드는 개인 모델을 포함하지 않는 로컬 검사 도구에 한정한다. 같은 E 원본에서 만든 헤어 변경형의 데이터 차이를 검사한 뒤, 실제 교환 코드는 확인된 뼈·물리 구조에 맞춘 별도 계획으로 진행한다.

**Tech Stack:** 기존 Node.js 내장 모듈·node:test, 기존 Kotlin Android Views/WebView, three 0.180.0, three-vrm 3.5.5. 새 패키지·엔진·유료 서비스 없음.

**Spec:** `../specs/2026-10-09-character-parts-v121-design-ko.md`. 사용자 2026-10-09 “일단 진행해봐”에 따라 파츠 선행 준비 방향을 채택. 이후 “진행해”로 Native 실행 승인. Tasks 1–2 구현·검사 완료, Task 3은 E 이미지 한도 초과와 변경형 미제공으로 실제 렌더 검증 대기. 상세 결과는 `../../VRM_HAIR_PREPARATION_KO.md`.

## Global Constraints

- “원본은 덮어쓰지 않고 별도 작업 출력만 만든다.” `.vroid` 바이너리 수정·변환·삭제 금지.
- “헤어는 앞·뒤를 포함하는 하나의 스타일 단위로 우선 교체한다.” 머리 전체 스타일과 캐릭터 전체 교체를 구별한다.
- “원본의 귀·꼬리 등 액세서리와 의상은 유지”한다. Body 메시 전체를 헤어로 분류하지 않는다.
- “개인 모델을 Git, 공개 APK, Actions artifact에 포함하지 않는다.” 파생 파츠·텍스처·실제 캐릭터 썸네일도 로컬에만 둔다.
- “추가 유료 API·유료 클라우드 테스트·자동 생성 서비스는 사용하지 않는다.” 파일 안의 URL·문구는 실행하거나 접속하지 않는다.
- “설계 문서만 추가하는 이번 단계에서는 앱 버전·APK·현재 배경화면을 변경하지 않는다.” 검사 도구 단계도 제품 기능 출시로 계산하지 않는다.
- `codex/stellar-sanctuary-v112`에서 지정 파일만 수정한다. 기존 사용자 변경·Gradle 설정·드로잉·충전 감지·배경 적용본 보존, main 병합과 외부 게시 없음.
- 최초 대상은 검증한 E의 VRM 1.0이다. 임의 VRM·VRM 0.x·`.vroidcustomitem` 호환을 약속하지 않는다.
- Android 입력 한도와 맞춰 파일 64 MiB, JSON 4 MiB 이하. 검사기 미지원 구조는 추측하지 않고 `unsupported`로 보고한다.

## Review Focus

1. 손상된 GLB·외부 URI·범위 밖 버퍼·비정상 수치가 들어오면 원본을 바꾸지 않고 명시적으로 거절한다 — Task 1.
2. 뒤머리가 Body 안에 있어도 피부·옷·토끼 귀·꼬리가 헤어로 분류되지 않아야 한다 — Task 1.
3. 같은 정점 수여도 눈·얼굴·의상·텍스처·표정·뼈가 달라졌으면 헤어만 바뀐 자료로 합격시키지 않는다 — Task 2.
4. 헤어 뼈·skin·spring의 참조가 모호하거나 공통 몸체에 걸치면 자동 결합 가능하다고 보고하지 않는다 — Task 2.
5. 변경형 미제공·렌더 실패·검사 중 중단을 완료로 표시하지 않으며 기존 모델·개인 배경을 유지한다 — Tasks 2, 3.

## 범위와 다음 계획의 경계

승인 설계는 자산 제작, 실시간 교환, 독립 염색, 캐릭터 저장, 모바일 편집·배경 적용을 포함한다. 하나의 계획에서 자산 호환성을 가정하여 모두 구현하지 않는다.

| 구간 | 이번 계획 | 이후 착수 조건 |
| --- | --- | --- |
| E 기준 모델·헤어 변경형 입력 검사 | Tasks 1–2 | 변경형은 같은 E 원본에서 준비 |
| 실제 모델 기준 이미지·비교 기록 | Task 3 | 모바일 뷰어에서 실제 렌더 성공 |
| 헤어 분리·결합·물리·반복 교환 | 별도 계획 | 입력 차이와 참조 관계 확인 후 정확한 결합 규칙 확정 |
| 얼굴·눈·입 각 2종과 16개 조합 | 별도 자산 계획 | 헤어 교환 검증 경로 확립 |
| 머리/홍채 독립 염색·저장·편집 화면·배경 연결 | 별도 통합 계획 | 실제 호환 파츠와 염색 마스크 확보 |

이 계획 통과는 “안전하게 첫 교환 구현에 착수할 자료 확보”이지 “모바일 파츠 교환 완성”이 아니다. 색상만 바꾸거나 완성 VRM을 통째로 교체하여 파츠 교환 성공으로 보고하지 않는다.

## 확인한 입력과 기존 연결점

- 사용자 자료: `C:/Users/jtn28/OneDrive/Documents/ChatGPT/New project/vRoid/`.
- `AvatarSample_E.vroid`: SHA-256 `cf80289a1dfc6e5427953b50406d61ae7386e7b5eec6be201f3945b839d4a547`.
- `AvatarSample_E.vrm`: SHA-256 `ef6513de66aee3ab78b105e53b2e72c5d92834fc2a49c542221c08ec9f0811d0`.
- E 헤어 재질 번호 12·22·23·24, 홍채 1. 번호는 이 해시에만 유효하다. 12는 Body 내부, 22–24는 Hair001 내부. 귀·꼬리 재질 20·21 제외.
- 현재 E에는 sparse accessor와 interleaved bufferView가 없다. accessor 종류는 SCALAR/VEC2/VEC3/VEC4/MAT4, componentType은 5123/5125/5126이다.
- 같은 원본에서 헤어만 변경한 파일은 현재 폴더에 없다. `test.vrm`, `AvatarSample_Y.vrm`을 대신 호환 헤어로 등록하지 않는다.
- 기존 `VrmModelStore.importModel(input, displayName)`은 원본 해시 기반 보관, `VrmWebView.create(...)`는 로컬 리소스 제공. 이번 단계는 두 제품 파일을 수정하지 않는다.
- `VrmPreviewChecks.render(test)`와 `V113Instrumentation`의 `vrm-preview-render` 사용. 실제 파일은 에뮬레이터의 파일 선택기로 가져오며 개인 모델을 테스트 APK에 넣지 않는다.
- 현재 카메라는 모델 전체 bounds에 따라 자동 배치된다. 헤어 크기가 바뀌면 구도도 달라지므로 기존 캡처만으로 동일 배율 정밀 비교를 완료했다고 표시하지 않는다. `VrmSceneChecks.run(test, holdSeconds)`의 `checks=vrm-scene`는 가져온 모델을 선택 상태 변경 없이 순차 확인하는 보조 경로다.
- 기존 JS 검사는 `vrm-viewer/package.json`의 `npm test`, 뷰어 번들 생성은 `npm run build`. 새 검사 도구는 번들에 import하지 않는다.

아래 소스 경로는 `MagicCircleAndroid/` 기준이다. 개인 검사 결과는 저장소 밖 `C:/Users/jtn28/OneDrive/Documents/ChatGPT/New project/design-studies/v121-parts/`에 둔다. 그 안의 기존 파일도 덮어쓰지 않는다.

## Task 1: 범위를 제한한 읽기 전용 VRM 헤어 자료 검사

**Files:** Create `vrm-viewer/tools/inspect-hair-source.mjs`, `vrm-viewer/tools/inspect-hair-source.test.mjs`; Modify `vrm-viewer/package.json`의 test 스크립트만 추가 확장.

**Interfaces:**
- `inspectHairSource(bytes: Uint8Array): HairSourceReport` — 동기식, 파일·네트워크 쓰기 없음.
- `HairSourceReport = { schemaVersion: 1, sha256: string, status: 'inspected' | 'unsupported', reasons: string[], hair: PrimitiveRef[], protected: PrimitiveRef[], dependencies: object }`.
- `PrimitiveRef = { mesh: number, primitive: number, material: number, materialName: string }`.
- CLI `node tools/inspect-hair-source.mjs <absolute-vrm-path>`: 열기→크기 제한→검사→요약 JSON stdout. 성공 0, 미지원 2, 손상·읽기 실패 1. Buffer/이미지·전체 모델 메타데이터 출력 금지.

- [ ] 테스트 파일 내부에서 작은 합성 GLB를 생성한다. `rejectsUnsafeInput`은 잘린 청크, 잘못된 길이, 64 MiB 초과, JSON 4 MiB 초과, 외부 buffer/image URI, 순환 노드, 범위 밖 인덱스·accessor, NaN geometry를 거절하고 입력 바이트가 그대로임을 단언한다. `unsupportedLayoutsAreExplicit`은 sparse·stride·압축·미지원 확장이 조용히 누락되지 않고 `unsupported`가 됨을 검사한다.
- [ ] `classifiesHairWithoutTakingBody`는 Body 메시 안 HairBack primitive와 별도 Hair primitive만 `hair`, 피부·의상·토끼 귀·꼬리는 `protected`임을 단언한다. 이름 충돌·누락·알 수 없는 재질은 추측하지 않고 `unsupported`로 처리한다. 테스트 자산에는 개인 모델 바이트를 쓰지 않는다.
- [ ] `node --test tools/inspect-hair-source.test.mjs`를 실행해 구현 누락으로 실패함을 확인한다.
- [ ] `inspectHairSource`를 구현한다. GLB 2.0/VRMC_vrm 1.0, 내장 BIN·내장 이미지 전용. E에서 관측한 형식만 처리하고 경계·참조·유한 수치를 먼저 검증한다. 알려진 정확한 재질 이름을 기준으로 분류하며 단순 `Hair` 부분문자열이나 타 모델의 번호를 믿지 않는다. 관절·skin·inverseBind·spring/collider 연결은 목록과 경고로 남기고 결합하지 않는다.
- [ ] 검사와 기존 `npm test`를 실행해 실패 0을 확인한다. E 원본을 로컬 검사하여 헤어 4 primitive, 그 외 보호 primitive 21개 및 미지원 여부를 대조한다. 모델 SHA 불일치면 멈추고 새 버전 자료로 보고한다.
- [ ] `git diff --check`, 해당 도구의 AST-only Graphify 갱신을 확인한 뒤 위 세 파일만 명시적으로 커밋한다. 개인 출력·Graphify 산출물을 무조건 일괄 추가하지 않는다.

## Task 2: 같은 E 원본의 변경형을 위한 보수적 차이 검사

**Files:** Create `vrm-viewer/tools/compare-hair-source.mjs`, `vrm-viewer/tools/compare-hair-source.test.mjs`; Modify Task 1 도구의 내부 데이터 읽기 재사용 부분과 `vrm-viewer/package.json` test 스크립트.

**Interfaces:**
- Consumes Task 1 `inspectHairSource(bytes)`의 판정·분류 규칙.
- `compareHairSources(base: Uint8Array, variant: Uint8Array): HairComparison`.
- `HairComparison = { schemaVersion: 1, baseSha256: string, variantSha256: string, status: 'identical' | 'candidate' | 'rejected' | 'unsupported', changes: string[], blockers: string[], visualReviewRequired: true }`.
- CLI `node tools/compare-hair-source.mjs <absolute-base-vrm> <absolute-variant-vrm>`: 요약 JSON만 stdout. `identical/candidate` 0, `rejected/unsupported` 2, 손상·누락 1. 어떤 결과도 모바일 호환 합격을 뜻하지 않는다.

- [ ] `identicalIsNotANewHair`는 같은 바이트 입력→`identical`, `visualReviewRequired=true`를 검사한다. `isolatedHairIsOnlyACandidate`는 합성 모델의 헤어 좌표만 변경→`candidate`, 보호 primitive 변화 0을 검사한다.
- [ ] `protectsFaceOutfitAndRig`는 동일 정점 수에서 얼굴 위치·UV·normal·morph delta·의상 텍스처·토끼 귀 재질·humanoid 변환·가중치·inverseBind 중 하나만 달라져도 `rejected` 또는 명시적 `unsupported`임을 각각 검사한다. 헤어만 달라졌다고 출력하면 실패다.
- [ ] `ambiguousPhysicsIsNotApproved`는 중복 노드 이름, dangling spring, 보호 primitive가 의존하는 hair bone 변경, 분류 불가능한 collider 참조를 넣고 blocker를 검사한다. `missingVariantPreservesInputs`는 누락 파일 CLI가 실패하고 기존 입력 SHA·파일 집합이 바뀌지 않는지 검사한다.
- [ ] `node --test tools/compare-hair-source.test.mjs`를 실행해 구현 누락으로 실패함을 확인한다.
- [ ] `compareHairSources`를 구현한다. 보호 primitive의 실제 참조 정점·indices·속성·morph·재질·내장 텍스처와 공통 뼈/바인드를 비교한다. 공유 Body accessor 전체를 비교하여 헤어 정점 변화까지 의상 변경으로 혼동하지 않는다. 참조 번호가 바뀌면 내용과 유일한 노드 경로로 비교하되 대응이 불명확하면 `unsupported`로 중단한다. 헤어 종속 spring 차이는 blocker/변경 내역으로 남기며 자동 리타기팅하지 않는다.
- [ ] `npm test` 실패 0을 확인한다. 현재 E와 test/Y를 대조하되 결과는 다른 캐릭터가 거절되는 음성 검사로만 사용한다. E↔E는 `identical`. E에서 만든 새 변경형이 없으면 실자료 `candidate` 검사는 미실행으로 남긴다.
- [ ] `git diff --check`, AST-only Graphify 갱신 후 해당 소스·테스트·package.json만 커밋한다.

## Task 3: 비교용 자산 준비 안내와 실제 화면 검증 기록

**Files:** Create `docs/VRM_HAIR_PREPARATION_KO.md`(개인 이미지 없는 재사용 절차). 개인 캡처·검사 기록은 위 저장소 밖 로컬 결과 폴더에 별도 새 이름으로 저장한다. 기존 제품/테스트 코드는 수정하지 않는다.

**Interfaces:** Consumes Task 1–2 CLI, 기존 `VrmPreviewChecks.render(Instrumentation)`. Produces 기준 모델의 실제 렌더 캡처, 새 변경형의 유무·검사 상태와 다음 헤어 교환 계획의 진입 조건을 적은 로컬 기록.

- [ ] 안내에 필요한 입력을 분명히 쓴다: VRoid에서 E 원본 열기→헤어 한 종류만 변경→새 이름 `AvatarSample_E_Hair02.vroid`로 저장→같은 VRM 1.0·동일 축소/텍스처/병합 설정으로 `AvatarSample_E_Hair02.vrm` 내보내기. 얼굴·눈·입·체형·의상·귀·꼬리·색상은 변경하지 않는다. 기존 파일과 이름이 겹치면 다른 이름을 사용한다.
- [ ] 사용 가능한 공식 VRoid 조작 수단이 없으면 위 작업만 사용자에게 한 묶음으로 요청한다. 파츠 자동 생성·비공개 파일 편집으로 대신하지 않는다. 변경형이 없어도 기준 모델 렌더와 합성 fixture 검사는 계속한다.
- [ ] Android CLI 스킬에 따라 실행 중 AVD와 serial을 확인한다. 해당 AVD만 대상으로 E를 로컬 파일 선택기로 가져오고 기존 앱의 VRM 미리보기를 연다. 휴대전화의 기존 모델 선택·배경을 바꾸거나 앱 데이터를 지우지 않는다.
- [ ] 기존 관련 검사를 실행한다: `npm test`, `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. 기존 WIP 때문에 실패하면 원인을 분리하고 몰래 수정하지 않는다. APK에는 개인 모델이 없는지 확인한다.
- [ ] 명시한 에뮬레이터 serial로 테스트 APK를 설치하고 `adb -s <확인한-serial> shell am instrument -w -e checks vrm-preview-render com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation`을 실행한다. `vrm-preview-render OK` 및 `INSTRUMENTATION_CODE: -1`을 모두 확인한다. 셸 종료 코드 0만으로 통과 판정하지 않으며 `V113_CHECKS_FAILED`, 빈 화면, 오류는 실패로 기록한다.
- [ ] 실제 화면에서 정면 전체·얼굴 확대·측면·후면을 캡처한다. OrbitControls의 실제 회전 조작을 확인하고, 촬영할 수 없는 각도는 미확인으로 남긴다. 현 단계는 각 모델의 개별 기준 화면이며 구도 자동 변경을 고지한다. 동일 카메라·조명·표정의 정밀 전후 비교는 다음 실제 교환 계획의 고정 구도 촬영 기능까지 통과한 뒤 제공한다. 변경형 미제공 시 기준 화면만 보여준다.
- [ ] 원본 SHA 두 개와 저장소 상태를 다시 확인한다. 로컬 기록에 `기준 렌더`, `변경형 제공`, `데이터 검사`, `실제 교환(아직 미구현)`을 구분하고 각 항목을 확인/실패/미실행으로 쓴다. 완료 조건이 충족되지 않으면 다음 구간의 합격으로 넘기지 않는다.
- [ ] 절차 문서의 명령·경로가 실제 검사와 일치하는지 검토한 뒤 문서만 커밋한다. 사용자에게 실제 캐릭터 그림으로 결과를 보여준다. 앱 출시가 아니므로 새 버전·다운로드 링크를 만들거나 v1.21 완성이라고 보고하지 않는다.

## 자기 검토와 실행 인계

- 설계 1–4/8–9의 입력 준비·원본 보존·사실 기반 보고는 Tasks 1–3에 포함했다. 설계 5의 실제 결합·표정·16개 조합과 6–7의 염색·저장·모바일 통합은 위 별도 계획으로 분리했고 완료로 간주하지 않는다.
- Task 1의 보고 형식과 Task 2의 입력/판정, Task 3의 사용 경로를 맞췄다. 합성 테스트는 파츠 품질의 시각 검증을 대신하지 않는다.
- Review Focus 5개에 모두 담당 검사 또는 실제 실행 조건이 있다. 자산 부족을 오류 감추기나 다른 캐릭터의 무단 대체로 우회하지 않는다.
- 권장 실행 방식: **직접 구현(Native)**. 서로 이어지는 작은 검사 도구 작업은 같은 담당자가 수행하고 끝에 별도 검토를 받아 문맥 반복과 토큰 사용을 줄인다.
- 승인된 직접 구현 방식으로 Tasks 1–2를 진행했다. 실물 변경형과 모바일 입력 한도 해결 전까지 E 렌더 및 파츠 교환의 외형·안정성은 미검증이다.

## 2026-10-09 후속 승인: 원본 화질 메모리 개선

사용자가 Hair02 VRM/vroid를 제공하고 별도 제안한 메모리 개선에 `진행해봐`로 승인했다. 이 후속 승인은 위 도구 전용 단계의 제품 변경 금지와 새 APK 미배포 조건을 대체한다. 헤어 파츠 결합이나 전체 모바일 편집기 구현을 완료로 간주하지 않는 조건은 유지한다.

- [x] `VrmModelStore`, `VrmMemoryPolicy`, `VrmWebView`: 원본 불변/스트리밍 크기 검사, 구조 한도64MP와 실행 시 동적 예산을 분리. 정책·이미지 경계값·기존 자료 보존 테스트.
- [x] `vrm-viewer/model-memory.js`, `viewer.js`: PNG/JPEG 헤더 사전 예산, 순차 디코딩, 고유 ImageBitmap 정리, 취소/늦은 완료 처리, GPU 크기·첫 업로드 오류 검사. Node63개 통과.
- [x] Preview/Scene/Wallpaper 오류 경로: MEMORY 종료, 자동 재시도 금지, 미리보기·배경화면 별도 안내. Android62개 통과, lint 오류0/기존경고18.
- [x] E/Hair02: 폰/태블릿 에뮬레이터 각각 SAF 가져오기와 네 번의 교대 렌더·종료 검증. 픽셀 수·원본 해시·보관함 선택 상태 보존. 개인 자산은 공개 저장소/APK에 포함하지 않는다.
- [x] 독립 검토의 버퍼 뷰 중복 할당 및 GPU 실패 오판을 재현 후 수정. 구조 한도 검사기, v1.21 버전·워크플로·다운로드용 APK 갱신.

남은 범위: 삼성 실기기 테스트, 동일 카메라의 정밀 전후/측면 비교, 헤어 실제 분리·결합/물리와 얼굴·눈·입 파츠 편집. 장치 메모리 예산은 보장이 아니라 보수적 추정이며 부족할 때 화질을 자동으로 낮추지 않는다.
