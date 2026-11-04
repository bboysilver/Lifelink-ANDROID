# 2.3.11 (49) — Closed testing 재검증 후보

2026-11-04. 기준은 현재 Closed test 1에서 제공 중인 2.3.10 (48)입니다. 이번 수정은 재현된 안전 경계 문제 두 가지로 제한합니다. Production access 승인, 실제 단말 검증 또는 공개 출시를 뜻하지 않습니다.

## 수정 범위

| 재현 조건 | 수정 및 기대 결과 |
| --- | --- |
| 시험 문자 요청을 `PENDING`으로 기록한 뒤, 실제 발송 상태/결과 확인 작업을 저장하기 전에 프로세스가 종료됨 | 앱 재진입 시 해당 시험의 상태 확인 작업을 다시 예약합니다. 전체 복구도 현재 대기 중인 시험을 포함합니다. 저장된 콜백이 있으면 확인 결과를 복원하고, 발송을 확인할 기록이 없으면 제한된 대기 후 확인 불가를 표시합니다. WorkManager의 실제 실행은 시스템 상태에 따라 늦어질 수 있습니다. |
| 현재 시험의 성공 콜백과 이전 실패/시간 초과 처리 순서가 뒤바뀜 | 같은 요청의 확인된 발송 성공을 실패로 되돌리지 않습니다. 이전 시험의 결과가 새로운 요청을 승인하지 않습니다. 시험 문자는 복구 중 자동으로 다시 발송하지 않습니다. |
| 새 사전 알림이 이전과 같은 PendingIntent를 갱신함 | ‘무사합니다’ 동작을 무활동 주기별로 분리하고, 현재 주기 확인과 타이머 갱신을 같은 잠금 안에서 처리합니다. 이전 알림은 다음 주기를 변경하지 않고, 시각 보정으로 마감 표시만 바뀐 현재 주기의 알림은 사용할 수 있습니다. 구버전 알림은 기존 마감 검사로 처리합니다. |

센서/잠금 해제 감지 기준, 무활동 시간, SMS 발송·재시도 정책, SOS 취소, 연락처·Room 스키마·권한·앱 ID·서명 설정은 유지합니다. 새 SDK, 광고, 네트워크, 로그 수집 또는 개인정보 전송은 없습니다.

## 검증 기준과 결과

- 기존 구현에서 추가한 회귀 4개가 실패한 것을 확인했습니다. 수정 및 시계 보정 검사 추가 후 두 관련 클래스 **26개 통과**(실패/오류/건너뜀 0). Robolectric은 실제 통신사 발송 시험이 아닙니다.
- JDK 21, Android SDK 37 환경에서 `test lint assembleDebug bundleRelease --rerun-tasks` 성공(107개 작업 실행). 테스트 362개 / 53개 스위트, 실패·오류·건너뜀 0. Lint는 문제 없음. 테스트 픽스처의 SQLite CloseGuard 및 일부 deprecated API 빌드 경고는 남아 있습니다.
- 로컬 AAB는 미서명이며 Play 업로드용이 아닙니다. `bundletool validate` 통과, 번들 설정 `PAGE_ALIGNMENT_16K`, 디버그 APK `zipalign -c -P 16 4` 통과. Play 배포용 검증에는 별도 서명 AAB와 업로드 인증서 확인이 필요합니다.
- 소스 커밋 `1c6defac1a5cd0185290fd67bfc3c1850f565c75`의 [PR 검증](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/41831680244)과 [서명 빌드](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/41831672905) 성공. 서명 작업 4분 56초. CI 산출물의 테스트도 362개 / 53개 스위트, 실패·오류·건너뜀 0, Lint 문제 없음.
- 서명 AAB `release-artifacts/2.3.11-49/lifelink-2.3.11-49-signed.aab`(Git 제외), 4,197,221바이트. SHA-256 `4C51A38097EAEDA8C5112E1340668FCC4D541660F038DEA267B865685EBAB8A9`는 CI 체크섬과 일치. 업로드 인증서 SHA-256 `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`는 기존 인증서 및 CI 기록과 일치. CI strict 검증 및 로컬 `jar verified` 확인. 로컬 truststore 미지정으로 자체 서명/인증서 체인/타임스탬프 경고가 있고, 기존 POSIX 속성 및 JarFile/JarInputStream 항목 순서 경고도 남아 있습니다.
- 실제 서명 파일의 `bundletool validate` 통과, `com.bboysilver.lifelink` 2.3.11(49), min24/target36, 2.3.10과 권한 11개 동일, AD_ID/INTERNET/위치 권한 없음. 서명 번들 `PAGE_ALIGNMENT_16K` 및 4개 ABI의 네이티브 라이브러리 ELF LOAD 16KB 정렬 확인. Play 분할 APK의 실기기 설치/실행까지 검증했다는 뜻은 아닙니다.
- API 24 이상, target 36 유지. [Google Play 대상 API 요건](https://support.google.com/googleplay/android-developer/answer/11926878), [16KB 페이지 지원 확인 방법](https://developer.android.com/guide/practices/page-sizes).

## Play Console 저장 결과

- 11월 4일 사용자 승인에 따라 기존 Closed test 1에 위 서명 AAB와 한국어 릴리스 노트를 업로드하고 `Save`로 저장했습니다. 이전 버전 48은 새 릴리스에 포함하지 않았으며, 현재 배포본 자체는 중지하거나 삭제하지 않았습니다.
- Play가 49(2.3.11), API 24+, target 36, 4개 ABI와 ReTrace 매핑을 인식했습니다. 검토 화면 차단 오류 0, 비차단 경고 1(네이티브 디버그 심볼 미제공). 지원 단말 추가·감소 0입니다.
- 게시 개요에 `Changes not yet submitted for review` / `Submit 1 change for review`와 2.3.11 한 건이 표시됩니다. 자동 사전 확인은 당시 진행 중이었습니다. 업로드·저장 완료이며, 심사 제출·승인·테스터 배포·프로덕션 신청은 하지 않았습니다. 테스터·국가·다른 트랙·정책 선언은 변경하지 않았습니다.
- 완료 화면: `release-artifacts/2.3.11-49/play-upload-saved.jpg`(Git 제외). 테스터는 승인·배포 전까지 기존 2.3.10을 그대로 사용합니다.

## Closed testing 현장 확인

기존 Closed test 1과 기존 opt-in을 유지합니다. 새 번들이 승인·배포된 뒤에만 테스터에게 2.3.11 (49) 업데이트를 요청합니다. Play 설치본은 삭제하지 말고 업데이트합니다. 테스트 계정/연락처·실제 수신 정보는 [기존 비공개 기록 방식](production-access-retest-2026-11-02.md)에 따릅니다.

| 시험 | 기대 결과 | 현재 |
| --- | --- | --- |
| 기존 48에서 49로 업데이트, 보호자·시간·설정 확인 | 기존 값 보존, 모니터링 상태를 실제로 확인 | NOT RUN |
| 동의한 보호자에게 시험 문자 1회, 앱 재진입 | 상태가 발송 확인 또는 확인 불가로 수렴. 실제 수신은 별도 기록. 자동 재발송 없음 | NOT RUN |
| 별도 시험폰에서 시험 문자 직후 앱 재시작, 완료/실패 화면과 다시 시도 확인 | 무한 대기 없음. ‘발송 확인’과 ‘보호자 수신’은 별개 | NOT RUN |
| 과거/현재 사전 알림, 시각 변경 및 정상 잠금 해제 | 유효한 현재 동작만 타이머 갱신. 화면 열기/직접 확인 자체도 활동임을 기록 | NOT RUN |
| 삼성 A24 등 실제 SIM 기기의 평소 사용·6/12시간 무활동·밤샘 절전·재부팅, SOS 취소 | 오발송·누락·중복·조용한 중단을 확인. 예정 마감, 점검, SENT와 실제 수신을 분리 | NOT RUN |

## 프로덕션 판단

11월4일 Dashboard는 12명이 연속 2일 참여했고, 11월2일 심사일로부터 추가 14일이 필요하며 신청 버튼은 비활성화라고 표시합니다. 가장 이른 예상은 11월16일 전후이며 Console의 실제 상태가 기준입니다. 설치·실사용·의견·조치·재시험 증빙이 추가로 필요합니다. 이 코드 검증을 실사용 증빙으로 대신하지 않습니다.

실제 수신·업데이트 보존·절전·재부팅 시험과 SMS/FGS 등 선언·심사 상태가 확인되기 전에는 공개 출시를 보류합니다. Console의 별도 `Declare your trader status` 안내는 계정 소유자가 검토할 항목입니다. Google 안내상 기존 계정 신고 기한은 2027-02-01이며, 앱 테스트 결과 또는 현재 거절 사유와 동일하지 않습니다. 계정 신고나 개인정보 공개를 대신하지 않았습니다. [공식 안내](https://support.google.com/googleplay/android-developer/answer/17594752)
