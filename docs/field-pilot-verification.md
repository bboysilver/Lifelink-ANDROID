# R0/R1 검증 기록 — 2.3.5 (43)

검사일: 2026-10-18, Windows 로컬 작업트리. 실서비스 안전 인증이나 Play 심사 승인이 아니다.

## R0 기준선

- 초기 working tree 깨끗함. 초기 브랜치 `codex/safety-tools-2.3.0`.
- 초기/당시 원격 main SHA: `ac86c4aaf6da93c66695f7c7269f4f8616ea4573`; 앱 소스 직전 수정 `5c413417d07582b6d1fb1f076cde835d384d94e3`.
- 초기 버전 2.3.4 (42), Room v3, applicationId `com.bboysilver.lifelink`, minSdk 24/targetSdk 36.
- 변경 전 `gradlew.bat --version`, `gradlew.bat test lint assembleDebug --console=plain`: 성공. 테스트 출력은 입력 일치로 UP-TO-DATE; 기존 XML 251개 검사, 실패/오류/스킵 0. lint 오류 0·경고 77·hint 1.
- 최초 sandbox 실행은 사용자 Gradle 캐시 `.lck` 접근 제한으로 실패했다. 권한 허용 후 동일 기존 캐시로 검사했으며 프로젝트 빌드 결함으로 처리하지 않았다.
- Java 21.0.10(Android Studio1 JBR), Gradle 9.3.1, Android SDK 36, JVM target 17, Windows 11. 새 빌드·서명 키/비밀값 생성 없음.

## 최종 명령과 실제 결과

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Home\AppData\Local\Android\Sdk'
.\gradlew.bat test lint assembleDebug bundleRelease --console=plain
# BUILD SUCCESSFUL (2m 9s)

node scripts/verify-care-contract.mjs
# PASS: 8 API operations, 71 local references ... No server or network requests executed.

git diff --check
# whitespace errors 없음 (Git LF/CRLF 정규화 안내만 있음)
```

- 최종 전체 검사 **276개**, 실패 0·오류 0·스킵 0. 기존 251개 + R1 추가 25개.
- lint: 오류 0·경고 77·hint 1. 기존 권고 경고는 남아 있으며 전부 해결했다고 주장하지 않는다. 새 코드의 KTX 권고 1건은 수정해 기준선 수와 같다.
- `assembleDebug` 성공; R8 난독화 `bundleRelease` 성공. 로컬 release 키가 구성되지 않아 AAB는 **unsigned**다. `jarsigner -verify` 출력 `jar is unsigned`를 확인했다. 서명 검증 성공/Play 업로드 가능으로 판정하지 않았다. Signed Android release CI는 실행하지 않았다.
- 중간 검사 실패 2건을 수정했다: (1) 권한 테스트가 기존 WorkManager의 WAKE_LOCK/ACCESS_NETWORK_STATE를 빠뜨림 → 2.3.4 기존 릴리스 Manifest와 대조해 예상값 보정, (2) 새 체크리스트 복원 시험에서 동일 ‘조치 필요’ 문구 여러 개를 선택 → 옵션별 testTag로 구분. 최종 성공만 나열해 중간 실패를 숨기지 않는다.
- R2 검사는 계약 JSON 참조·인증 요구 표기·입력 상한·동의·수동 확인·권한 역할·금지 필드의 **오프라인 정적 검사**다. 정식 OpenAPI 전체 스키마 검증기나 실제 서버/RBAC/철회 E2E 검사 대신이 아니다. API/웹 서버는 구현하지 않았다.

## 산출물 (로컬, Git/Play 업로드 안 함)

| 파일 | 크기 | SHA-256 |
|---|---:|---|
| `release-artifacts/field-pilot-2.3.5-43/lifelink-field-pilot-2.3.5-debug.apk` | 18,700,817 bytes | `FFFC29E010C17C3501B25E0BA9911940DDDF32ACD990C895B490B152D2657ACE` |
| `release-artifacts/field-pilot-2.3.5-43/lifelink-field-pilot-2.3.5-unsigned.aab` | 3,642,795 bytes | `8B1C77969464482B802BFC9EB1764011BEBA676F51539E6FB311E3B808D6C076` |

산출물은 커밋 전 R1 소스 작업트리로 생성한 비공식 로컬 검증 파일이며 Git 커밋 SHA로 고정된 공식 릴리스 산출물이 아니다. debug APK는 Play 설치본과 서명이 달라 덮어설치가 거절될 수 있다. 기존 앱을 삭제해서 맞추지 말고 별도 시험기기를 사용한다. 공식 서명·동일 서명 업그레이드 검증은 승인 후 기존 Signed CI 경로로 별도 수행해야 한다.

## 회귀·개인정보 경계 확인

- `monitoring/`, `MainActivity.kt`, `MonitoringStore.kt`, `AppDatabase.kt`, Manifest, 의존성 목록, CI 워크플로는 기준선 대비 변경 없음. Gradle은 versionName/Code만 2.3.5 (43)으로 변경.
- 최종 release merged Manifest: package `com.bboysilver.lifelink`, minSdk 24, version 2.3.5 (43). SEND_SMS/기존 FGS·부팅·기타 권한 유지. INTERNET/위치/AD_ID/READ_SMS/RECEIVE_SMS/연락처/카메라/마이크/결제 권한 없음. 기존 WorkManager merged WAKE_LOCK/ACCESS_NETWORK_STATE 및 라이브러리 내부 receiver permission은 그대로다.
- Room v3의 엔티티·migration은 그대로다. 추가 DAO는 읽기 전용 projection/집계다. 기존 v1/v2 업그레이드·연락처/설정/사건 보존 검사 포함 통과.
- 무활동 사건·동일 사건 추가 6/12시간 중복 방지, 실패 재시도·콜백·일일 안부·SOS·부팅/서비스·시각 보정의 기존 검사 통과.
- 가족 메모/상태 조회가 deadline·incident ID·발송 상태/시도 횟수·미완료 사건을 변경하지 않는지 검사했다. 완료 이력 삭제와 진행 재시도 보존도 검사했다.
- 오래된/미래/없는 heartbeat·중단 서비스·센서/권한/SIM 없음·비행기 모드에서 새 요약을 정상으로 표시하지 않는지 검사했다. QUEUED 시간 초과나 미래 SMS 시각은 UNKNOWN이며 성공/확정 실패로 꾸미지 않는다.
- 기본 공유 텍스트의 허용 목록, 개인정보·정확한 활동 이력 제외, 임시 별칭 opt-in/opt-out, 동의 재확인, 취소로 저장소 불변, text/plain chooser 구성·수신 앱 추적 없음 검사 통과.
- 200% 글씨·스크롤·동의/닫기 버튼, 가로 화면과 saved state 복원·체크리스트 유지, Android 7(API 24) 로컬 진단/공유 Intent 호환 검사 통과. 실제 TalkBack 발음·초점·기기 회전의 수동 검증을 대체하지 않는다.
- 새 코드가 타이머를 직접 초기화하지 않지만 **기존 앱 열기/직접 조작의 활동 기록은 유지**된다. 점검자 조작도 활동일 수 있으므로 사람의 안전 증거로 사용하지 않는다는 안내를 추가했다.

## 미실행·승인 게이트

- ADB 서버를 시작해 조회했으나 연결 실기기/에뮬레이터 없음. 실제 통신사 SMS·Galaxy A24 6시간/Doze·SIM 변경·재부팅·같은 서명 업그레이드는 미실행. [수동 시험표](field-pilot-test-plan.md)에 별도 기록해야 한다.
- 실제 외부 앱으로 점검 자료 전송, 개인정보/기관 실데이터 업로드, Play Console 제출, 공개 Pages 정책 배포, 신규 Secret 입력은 수행하지 않았다.
- 현재 공개 방침 URL은 main의 2.3.4 내용이다. 브랜치의 2.3.5 방침은 변경안이며 리뷰/배포 승인 전 공개 주소에 반영되지 않는다. 실제 배포 전 방침·스토어 선언과 앱의 일치를 재검토해야 한다.
- R2 인증·서버·웹·outbox·철회/삭제·기관 접근권한·운영 보안: 미구현, 별도 사용자 승인·기관 검토 필요. ‘기본 OFF 토글이 구현됨’으로 오인하지 않는다. R1에는 R2 네트워크 경로 자체가 없다.
- R1은 로컬 기술 검증용으로만 준비됨. 안전 앱 공개 출시 준비 완료 판정은 하지 않는다. 기존 A24 지연·OEM 제약은 [위험 등록부](field-pilot-risk-register.md)에 남아 있다.
