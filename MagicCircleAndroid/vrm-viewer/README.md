# 오프라인 VRM 미리보기 (v1.18)

앱의 **캐릭터 만들기 → VRM 3D 미리보기 → VRM 불러오기**에서 VRoid Studio가 내보낸 `.vrm`을 선택한다. 파일은 서버로 전송하거나 APK에 넣지 않고 앱의 `noBackupFilesDir/vrm-preview/model.vrm`에만 저장한다. 원본 파일을 수정하지 않는다.

- VRM 1.0, 내장 PNG/JPEG 텍스처, 최대 64MiB. VRM 0.x는 재내보내기가 필요하다.
- 모델 1개 미리보기, 전신/얼굴 보기, 드래그 회전, 두 손가락 확대·이동, T 포즈, 눈 깜박임.
- three.js 0.180.0 + pixiv three-vrm 3.5.5의 WebGL/MToon 렌더러. 의존성은 앱에 번들되며 CDN이나 유료 API를 호출하지 않는다. 라이선스는 앱 자산 `vrm-preview/THIRD_PARTY.txt`에 포함한다.
- JavaScript 외부 요청과 탐색을 차단하며 JavaScript→Android 브리지를 만들지 않는다. Android의 INTERNET 권한도 추가하지 않는다.
- 최대 30fps, 렌더 해상도 배율 1.5 제한, 백그라운드 렌더 중지, 종료 시 GPU 자원 해제.
- 크기/오프셋/엄격한 JSON/텍스처 크기 검사 후 파일을 원자적으로 교체한다. 검사 실패 시 기존 모델을 보존한다. 구조 검사를 통과해도 잘못된 재질·지원하지 않는 glTF 확장 등은 렌더러에서 표시 실패할 수 있다.

**범위:** 기존 2.5D 캐릭터 편집기와 별개다. VRM의 헤어·체형 편집, 배경화면/충전 화면 적용, 춤·걷기 모션은 아직 제공하지 않는다. `.vrm`을 `.vroid`로 이름만 바꿔도 편집 원본이 되지 않는다. 편집용 원본은 VRoid Studio의 저장 기능으로 별도로 보관해야 한다.

## 재현 빌드

이 폴더에서 `npm ci`, `npm test`, `npm run build`를 실행하면 `app/src/main/assets/vrm-preview/viewer.js`가 갱신된다. HTML/CSS는 해당 자산 폴더에서 수정한다. 생성된 JS를 함께 커밋하므로 GitHub Actions의 Gradle APK 빌드는 npm 설치 없이도 오프라인 자산을 포함한다. CI는 Node 내장 카메라 검사와 Android 단위 테스트·lint·APK 빌드를 수행한다.

Android 기기 검사는 기존 `V113Instrumentation`에 `checks=vrm-preview`(검증/보존), `vrm-preview-screen`(차단/화면 복구), `vrm-preview-render`(실제 모델/정지·재개/버튼)를 전달한다. 마지막 검사는 가상 기기에서 파일 선택기로 모델을 먼저 가져와야 한다. 개인 모델은 테스트 소스나 공개 저장소에 넣지 않는다.

`minSdk=23`은 유지하되 3D 화면에는 WebGL2를 지원하는 Android System WebView가 필요하다. 미지원/메모리 부족은 오류 안내를 표시한다. 가상 기기 검증은 Samsung 실기기 성능·장시간 발열·배터리 사용량 검증을 대신하지 않는다.
