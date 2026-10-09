# 헤어 파츠 준비: 원본을 지키며 머리만 바꾸기

## 2026-10-09 실제 헤어 결합 결과

**E의 얼굴·몸·옷·토끼 귀·꼬리 + Hair02의 머리**로 단일 VRM을 새로 조립했다. Hair02 완성 캐릭터를 대신 선택한 것이 아니다. 아래의 이전 v1.21 준비 기록과 구분한다. 이번 결과는 로컬 결합 증명이며 모바일 파츠 선택·개별 염색·편집 캐릭터 저장 기능의 출시는 아니다.

| 확인 항목 | 결과 |
| --- | --- |
| 보호 부위 | 21개 primitive의 참조 정점·UV·법선·표정·가중치·재질·이미지 지문 불변 |
| 보호 뼈/물리 | 공통 변환·바인드, 보호 spring 35개, collider 28개 유지 |
| 교체 머리 | 기존 Body 뒤머리 + 별도 머리 제거, Hair02 머리·전용 뼈·spring 결합 |
| 원본 보존 | E/Hair02의 VRM 2개 및 vroid 2개 SHA-256 불변 |
| 결합 파일 | 20,145,728 bytes, SHA-256 `c1853aa3c22b5c3b58ba8f819c4b2c4e7bd318aeeefaa9739f7c9f3bb205a708` |
| 텍스처 | PNG 재인코딩/축소 없음, 선언 이미지 54,067,328픽셀; 동일 이미지 바이트만 중복 제거 |
| 자동 검사 | Node 76개, Android 단위 62개; 오프라인 lint/debug/test APK 빌드 성공 |
| 가상 기기 | API 36 휴대전화 1440×3120, 태블릿 2560×1600 |
| 렌더 증거 | 기기별 원본/결합 각각 전신·상반신 4방향 + 눈감기, 총 18장 |
| 물리 | 실제 spring 관절 128→133, 중복 없음·수치 유효·머리 회전 자극에 갱신됨 |
| 반복 검사 | 두 기기 모두 `vrm-hair OK`, 별도 `vrm-memory OK`와 `INSTRUMENTATION_CODE: -1` |
| 자원 해제 | 네 번 전환 후 매회 추적 liveBitmaps/gpuTextures/activeDecodes 모두 0 |
| 사용자 상태 | 기존 선택 Hair02 복원; 배경 슬롯·태블릿 pending 및 시스템 홈/잠금 배경 ID 유지 |

육안 확인한 정면·좌우·후면·눈감기에서 기존 뒤머리 중복, 뚜렷한 두피 구멍, 목·어깨 관통, 귀·꼬리 소실은 보이지 않았다. 정지 구도의 합격이 모든 자세에서의 충돌 방지를 보증하지는 않는다. 실제 삼성 휴대전화/태블릿의 장시간 테스트는 이번 단계에 포함하지 않았다.

개인 결과는 저장소 밖 `C:/Users/jtn28/OneDrive/Documents/ChatGPT/New project/design-studies/v122-hair-assembly/`에 있다.

- `E-with-Hair02-proof-01.vrm`: 결합 결과. 같은 이름의 `.vrm.json`은 출처 해시와 검사 결과다.
- `phone/`, `tablet/`: 원본과 결합 결과의 실제 앱 창 촬영 및 `review.json` 카메라/물리 기록.
- 개인 모델·텍스처·화면은 Git/공개 APK에 추가하지 않았다. 배포 버전은 v1.21 그대로다.

개발자 실행:

```powershell
node tools/assemble-hair.mjs 'C:/절대경로/AvatarSample_E.vrm' 'C:/절대경로/AvatarSample_E_Hair02.vrm' 'C:/새경로/E-with-Hair02.vrm'
```

검사 후보가 아니거나 기존 출력/보고 파일이 있으면 실패한다. 출력도 다시 파싱해 보호 부위와 기증 머리의 지문을 확인한 뒤 게시한다. 이번 조립기는 확인된 E 계열과 PNG만 지원하며, 불명확한 형식은 축소·추측 없이 거절한다. 원래 머리의 공통 Head/Neck/Shoulder 뼈는 보존하고, 공통 바인드가 동일한 Hair02 skin에 E 보호 정점 가중치를 다시 연결한다.

```powershell
adb -s <가상기기> shell am instrument -w -r -e checks vrm-hair -e model <E-ID> -e alternate <결합-ID> -e pixels 54067328 com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation
adb -s <가상기기> shell am instrument -w -r -e checks vrm-memory -e model <E-ID> -e alternate <결합-ID> -e pixels 54067328 com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation
```

고정 구도는 E에서 한 번 결정하여 결합 결과에도 동일 적용한다. 검토 카메라는 테스트 호스트가 제공한 표식이 있을 때만 노출된다. Android CLI와 UiAutomation 촬영이 충돌하여, [PixelCopy의 앱 창 캡처](https://developer.android.com/reference/android/view/PixelCopy)로 변경했다. 화면 픽셀을 실제로 복사하며 가짜 렌더나 목업으로 대체하지 않는다. 메모리 674,571,840 bytes는 결합 모델의 보수적 **추정값**으로 실제 프로세스 RAM 측정치가 아니다.

다음 제품 단계: 검증된 헤어 선택 → 머리/홍채 독립 팔레트·RGB·HEX → 별도 캐릭터 저장 → 이미지와 배경 합성에 같은 결과 전달. 아직 없는 얼굴·눈·입 모양을 작동하는 선택지처럼 표시하지 않는다.

## 지금 준비된 것과 아직 아닌 것

로컬 검사 도구가 완성 VRM 두 개를 읽어 머리 외의 얼굴·옷·뼈·표정이 함께 바뀌었는지 검사한다. 도구는 파일을 수정하거나 업로드하지 않는다. 검사 결과 `candidate`는 **실제 결합 시험 후보**이며, 머리 교환·모바일 렌더·물리 검증에 합격했다는 뜻이 아니다.

앱 v1.21에서 E와 Hair02 완성 VRM을 원본 텍스처로 표시할 수 있도록 메모리 처리를 개선했다. 아직 모바일에서 머리·얼굴·눈·입 파츠를 자유롭게 교환하는 편집기는 아니다.

## 사용자가 준비할 자료

1. VRoid Studio에서 보존한 `AvatarSample_E.vroid`를 연다.
2. **머리 모양 하나만** 바꾼다. 앞머리와 뒷머리는 하나의 스타일로 취급한다. 얼굴형·눈·입·색상·체형·의상·토끼 귀·꼬리는 그대로 둔다.
3. 기존 원본을 덮어쓰지 않고 `AvatarSample_E_Hair02.vroid`로 따로 저장한다.
4. VRM 1.0으로 `AvatarSample_E_Hair02.vrm`을 내보낸다. 기준 파일과 동일한 축소·텍스처·재질 병합 설정을 사용한다. 설정을 바꿔야 한다면 원래 모양의 비교 기준도 같은 설정으로 새로 내보낸다.
5. 파일 두 개를 사용 중인 `vRoid` 폴더에 둔다. 같은 이름의 파일이 이미 있으면 다른 이름으로 저장한다.

`.vroid`는 편집 원본, `.vrm`은 앱에서 보여줄 출력물이다. 확장자만 바꿔도 편집 원본이 되지는 않는다. 이 작업 환경에는 VRoid의 네이티브 화면을 직접 조작할 수 있는 연결이 없으므로, 위 실제 헤어 선택과 내보내기는 사용자의 조작이 필요하다.

## 이전 가져오기 제한과 v1.21 개선

2026-10-09 E 원본 출력물 검사 결과:

| 항목 | 결과 |
| --- | --- |
| 원본 VRM 크기 | 22,349,452 bytes: 64 MiB 파일 한도 이내 |
| 머리카락 분류 | 4개 primitive, 재질 12·22·23·24 |
| 유지할 얼굴·몸·옷·액세서리 | 21개 primitive |
| 내장 이미지 | PNG 39개 |
| PNG 헤더 기준 전체 픽셀 | 58,523,840 |
| 기존 앱 전체 이미지 안전 한도 | 41,943,040 픽셀 |
| v1.20 가상 휴대전화 가져오기 | 실패, 기존 선택 모델 보존 |
| v1.21 파일 구조 검사 한도 | 67,108,864 픽셀, 각 이미지 최대 4096×4096 |
| v1.21 가상 휴대전화 가져오기 | E와 Hair02 모두 성공, 원본 파일 해시 보존 |

파일 용량과 이미지 메모리 크기는 다르다. 압축된 VRM 파일이 작아도 화면에 펼칠 이미지가 많으면 앱의 안전 한도를 초과할 수 있다. E는 이 한도를 초과한다. PNG 헤더 수치는 예산 사전 진단이며 이미지 디코딩이나 실제 GPU 사용량 측정을 대신하지 않는다.

완전히 동일한 PNG 바이트를 하나씩만 계산해도 56,426,624 픽셀이므로 중복 제거만으로는 이전 한도에 들어가지 않았다. v1.21은 해상도를 낮추지 않고, 디코딩을 한 번에 하나씩 수행한다. 이미지 크기 검사도 전체 압축 이미지를 복사하지 않는 제한 스트림으로 바꿨다.

화면을 열 때 메모리 예산은 `min(768 MiB, 전체 RAM/4, (가용 RAM−시스템 임계값)/2)`이다. 저메모리 상태에서는 열지 않는다. 파일·중복 버퍼 뷰·정점·CPU 이미지·GPU 텍스처와 mipmap·단일 디코딩 여유분을 미리 계산해 예산 초과를 차단한다. GPU 최대 크기를 넘는 이미지는 자동 축소하지 않고 거절한다. 첫 프레임 업로드 오류도 성공으로 표시하지 않는다. 이 값은 보수적인 **추정치**이지 OS/GPU 메모리 예약이나 실제 사용량 측정은 아니다.

종료·실패·교체 시 GPU 자원과 고유 ImageBitmap을 해제한다. 메모리 부족은 별도 안내하며 자동 재시도하지 않는다. `.vroid`와 `.vrm` 원본은 변경하지 않는다. 과거 `e-import-blocked-phone-20261009.png`에는 이전 VRM 0.x가 남아 있으며, 이번 실제 E/Hair02 성공 화면과 구분해야 한다.

새 `AvatarSample_E_Hair02.vroid`/`.vrm` 파일을 확인했다. 읽기 전용 상세 비교에서 얼굴·몸·옷 등 보호 대상 21개 primitive의 실데이터는 유지되었지만, 기존 보수적 헤어 비교기는 새로운 헤어 재질명과 내보내기 번호 변경을 자동 승인하지 않는다. **완성 VRM 전환 성공은 헤어 파츠 결합 성공이 아니다.**

## 개발자용 검사 실행

실행 위치: `MagicCircleAndroid/vrm-viewer`. Node.js 설치본을 사용하며 별도 유료 API나 추가 패키지가 필요하지 않다.

```powershell
npm test
node tools/inspect-hair-source.mjs 'C:/절대경로/AvatarSample_E.vrm'
node tools/compare-hair-source.mjs 'C:/절대경로/AvatarSample_E.vrm' 'C:/절대경로/AvatarSample_E_Hair02.vrm'
```

- `inspected`: 검사기가 지원하는 데이터 구조. 모바일 가져오기 가능 여부가 아니다. `dependencies.textureBudget`도 따로 확인한다.
- `identical`: 비교 대상에 의미 있는 차이가 없으므로 새 헤어로 세지 않는다.
- `candidate`: 머리 영역 차이만 확인했다. 육안·결합·물리 검증이 남는다.
- `rejected`: 보호 영역·뼈·표정·장면 설정 변화가 있다.
- `unsupported`: 알려지지 않은 재질·불명확한 대응·지원하지 않는 구조가 있다. 추측해서 승인하지 않는다.
- 종료 코드: 허용된 검사 결과 0, 미지원/거절 2, 손상/파일 누락/읽기 실패 1.

지원 범위는 이번 E의 재질 이름과 내장 버퍼 VRM 1.0, 비압축·비희소·비인터리브 데이터다. 다른 캐릭터 `test.vrm`/`AvatarSample_Y.vrm`은 알 수 없는 재질로 미지원 판정되었다. 이 결과가 두 캐릭터 자체의 고장이나 모든 앱에서의 미지원을 의미하지 않는다.

별도 내장 애니메이션이 있는 파일과 예상한 정점 속성 형식이 아닌 파일은 미지원으로 처리한다. 반복 인덱스·공유 메시·표정 변형을 포함한 비교량은 파일당 16,777,216개 수치로 제한하고, 정점 자료는 조금씩 해시하여 메모리 폭증을 막는다. 이 제한은 앱의 텍스처 한도와 별개다.

개인 파일은 저장소·APK 자산·테스트 fixture·공개 배포에 넣지 않는다. 테스트는 직접 만든 삼각형과 뼈 자료만 사용한다. 진단 출력에 원본 바이너리나 전체 메타데이터는 포함하지 않는다.

## 모바일 회귀 검사

Android 프로젝트에서:

```powershell
.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

`android info` 등으로 가상 기기의 serial과 API를 확인한다. 개인 휴대전화가 아닌 에뮬레이터만 대상으로 설치·검사한다. 같은 이름의 기기 파일을 덮어쓰지 말고 로컬 파일 선택기로 가져온다. 시스템 배경 적용 버튼은 누르지 않는다.

```powershell
adb -s <확인한-에뮬레이터-serial> shell am instrument -w -r -e checks vrm-preview-render com.yj.magiccircle.test/com.yj.magiccircle.V113Instrumentation
```

raw 출력에서 `vrm-preview-render OK`와 `INSTRUMENTATION_CODE: -1`을 확인한다. `V113_CHECKS_FAILED`·검은 화면·오류는 실패다. 어떤 모델을 선택하여 검사했는지도 기록해야 한다. 기존 모델 검사 성공을 E 성공으로 바꾸어 보고하지 않는다.

기존 뷰어는 모델 크기에 따라 카메라 구도를 자동 계산한다. 모델별 개별 확인은 가능하지만, 헤어 교환 전후의 엄밀한 동일 배율 비교는 고정 카메라와 표정 촬영 기능이 준비된 뒤 진행한다.

## 2026-10-09 v1.21 확인 범위

- Node 전체 검사 63개 통과. 메모리 예산, 겹치는 버퍼 뷰, GPU 크기·업로드 실패, 순차 디코딩, 중간 취소와 늦게 도착한 이미지 해제를 포함한다.
- Android 단위 검사 62개 통과. 오프라인 lint/앱·테스트 APK 빌드 성공.
- lint: 오류 0, 기존 경고 18개 보존.
- `MagicCircle_Phone_API36`: 파일 선택기로 E/Hair02 가져오기와 화면 표시 성공. E→Hair02→E→Hair02 검사 통과. 각 종료 후 liveBitmaps와 gpuTextures 0, activeDecodes 0, 렌더 프레임 정지 확인. 선언 이미지 총 픽셀 58,523,840 / 54,067,392 유지.
- E 메모리 추정 754,568,026 bytes, Hair02 697,711,753 bytes. 디코딩 동시 실행 최대 1. 이 수치는 GPU/프로세스 실제 메모리 측정값이 아니다.
- 잘못된 파일·이미지 경계값·원본 보존을 검사하는 `vrm-preview` Android 검사 통과.
- Hair02 확대·포즈·눈깜박임과 저장 실패 복구 `vrm-preview-render` 검사 통과. `MagicCircle_Tablet_API36`에서도 E/Hair02 화면 표시와 네 번의 전환·자원 해제 `vrm-memory` 검사 통과.
- E/Hair02 원본 VRM·vroid는 변경하지 않는다. 실제 삼성 기기 테스트, 정밀 측면/후면 비교, 파츠 결합·물리 교환은 별도 검증이 필요하다.

개인 화면 증거는 저장소 밖 `design-studies/v121-parts/`에 보관하며 공개 커밋·APK에 개인 VRM/vroid를 포함하지 않는다. 배포 APK에서도 본인의 VRM을 직접 가져와야 한다.

추가 외부 의존성·유료 API·화질 축소는 없다. 기존 화면 렌더 배율 상한과 30fps 정책은 바꾸지 않았다. VRoid Studio와 조명·안티앨리어싱이 완전히 동일하다고 보장하지 않는다.

## 테스트 APK

- `releases/MagicCircleCharging-v1.21-local-update.apk`: 기존 로컬 디버그 서명을 사용한 업데이트용 APK, 21,898,595 bytes.
- SHA-256: `50067232cf278d51c44a8bb281b256209a390da220991b10f5a58f67f9817d42`.
- 로컬 빌드는 기존 사용자 작업 상태(AGP 9.4.1/Gradle 9.6.0 포함)를 보존한 채 실행했다. 해당 기존 변경은 이번 커밋에 포함하지 않는다. GitHub Actions는 저장소에 커밋된 빌드 도구/소스로 별도 검증하며 CI 임시 서명 APK와 위 로컬 업데이트 APK는 동일한 파일이 아니다.
- 설치가 서명 충돌로 거절되면 앱을 지우지 말고 사용 중인 설치본을 확인해야 한다. 앱 삭제 시 보관함 자료가 사라질 수 있다.
- 앱에서 `캐릭터 만들기 → VRM 캐릭터 · 배경화면 → VRM 불러오기`로 본인의 파일을 선택한다. `.vroid`는 PC 편집 원본이며 앱에서는 `.vrm`을 선택한다.
