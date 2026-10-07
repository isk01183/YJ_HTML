# 휴대전화 없이 화면을 보면서 개발하기

## 준비된 환경

| 항목 | 설정 |
|---|---|
| Android Studio 프로젝트 | `C:\GPT_Workspace\StellarSanctuaryMCP\MagicCircleAndroid` |
| 실제 소스 폴더 | `C:\Users\jtn28\OneDrive\Documents\ChatGPT\New project\stellar-sanctuary-v112\MagicCircleAndroid` |
| 가상 휴대전화 | `MagicCircle_Phone_API36` / Pixel 7 Pro 화면 규격 / 1440×3120 |
| 가상 태블릿 | `MagicCircle_Tablet_API36` / Pixel Tablet 화면 규격 |
| 운영체제 | Google Android 16 / API 36 / x86_64 |
| 실행 가속 | Windows Hypervisor Platform(WHPX) 확인 |
| 비용 | PC에서 실행. 별도 유료 API·클라우드 기기 서비스 없음 |

두 경로는 같은 프로젝트를 가리킵니다. 삼성 One UI 자체를 복제한 가상 기기는 아닙니다.
v1.15 검증에서 휴대전화와 태블릿 모두 실제 부팅·앱 설치·편집 화면 확인을 마쳤습니다. 태블릿은 2560×1600 가로와 세로 회전을 확인했습니다. 상세 결과는 [v1.15 안내](V1_15_KO.md)를 보세요.

## Android Studio에서 보는 방법

1. **Tools → Device Manager**에서 `MagicCircle_Phone_API36`의 실행 버튼을 누릅니다. 이미 켜져 있다면 다시 만들지 않습니다.
2. 상단 실행 대상 목록에서 **MagicCircle_Phone_API36**을 선택합니다. USB로 연결된 실제 폰과 혼동하지 않습니다.
3. 실행 구성은 기존 **app**을 사용합니다. 초록색 **▶ Run**은 앱 실행, 벌레 모양 **Debug**는 중단점 디버깅입니다.
4. 에뮬레이터 창 또는 **View → Tool Windows → Running Devices**에서 화면을 확인합니다.
5. **Logcat**에서 해당 가상 기기를 고르고 `package:com.yj.magiccircle`로 앱 로그를 좁혀 봅니다.

Codex의 Android Studio 자동 Run은 대상 기기를 명시할 수 없어 안전 검사에서 차단됐습니다. 대신 Android CLI에 가상 기기 serial을 명시해 설치·실행·화면 캡처를 검증했습니다. Android Studio의 직접 Run/Debug는 2번에서 대상을 먼저 선택하세요.

## 앞으로 개발할 때의 순서

코드 수정 → 테스트와 빌드 → 가상 기기 설치 → 실제 화면 캡처 확인 → 오류 로그 확인 → 필요한 디자인 조정.
이 순서를 저장소 `AGENTS.md`에도 기록했습니다. 빌드 성공만 보고 디자인까지 확인했다고 판단하지 않습니다.
휴대전화는 세로/가로, 태블릿은 넓은 화면의 배치를 확인합니다. 메모리 사용을 줄이려면 가상 기기를 하나씩 실행합니다.
가상 기기의 확장 메뉴 **… → Battery**에서 충전 상태·배터리 양을 바꿀 수 있습니다. 앱 접근성 서비스를 켠 뒤 충전 연결/해제를 반복하여 확인하며, 이것이 삼성 실기기 검증을 대체하지는 않습니다.

## 명령으로 실행하는 경우

Android Studio에서 연 프로젝트의 터미널(PowerShell) 기준입니다.

```powershell
$androidSdk = 'C:\Users\jtn28\AppData\Local\Android\Sdk'
& "$androidSdk\cmdline-tools\latest\bin\android.exe" emulator start MagicCircle_Phone_API36
& "$androidSdk\platform-tools\adb.exe" devices -l
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
# 아래 serial은 위 목록에 표시된 가상 기기 번호와 일치하는지 먼저 확인합니다.
& "$androidSdk\cmdline-tools\latest\bin\android.exe" run --device=emulator-5554 --use-delta-install=false --apks="$PWD\app\build\outputs\apk\debug\app-debug.apk"
```

기존 CLI의 빠른 설치 보조 프로세스에서 Android 16 호환 오류가 기록돼 일반 설치 경로(`--use-delta-install=false`)를 권장합니다. 앱 자체 오류와 구분해야 합니다. 실제 설치·앱 실행은 성공했습니다.

## 2026-10-07 v1.14 당시 확인 결과

- Gradle 테스트·lint·앱 및 계측 APK 빌드 성공. 단위 테스트 보고서 44개 통과, lint 오류 0개·경고 4개.
- API 36 가상 휴대전화에서 계측 검사 성공: `V113_CHECKS_OK`.
- 검사 대상: 보관함 저장·가져오기, 배경화면 정책, PNG/JPG 화면 채움, GIF 프레임 변화·반복, 상태 패널, R01/W03 렌더링.
- 초기 검사는 손상된 PNG 테스트 데이터 때문에 실패했습니다. 데이터만 정상화하고 재실행하여 통과했습니다. 배포 APK 해시는 그대로입니다.
- 첫 화면의 두 선택 메뉴, 한국어 표시, R01 미리보기, 취소 후 첫 화면 복귀 확인. 배경화면은 실제로 적용하지 않았습니다.
- 삼성 실기기 충전 재연결·잠금화면·절전·재부팅 지속성은 미검증입니다.

화면 증거: [첫 화면](emulator-v114-home.png), [R01 미리보기](emulator-v114-r01.png).
