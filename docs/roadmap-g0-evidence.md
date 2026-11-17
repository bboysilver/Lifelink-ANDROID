# G0 기준선 증빙 — 2026-10-24

## 범위와 작업 순서

G0 기준선 확인 → 기존 R1의 G1 관찰·수동 라벨·공유 경계 보강 → 집중/전체 회귀 검사 → diff·산출물·미실행 항목 기록. G2/Family/Care 연결은 **NOT STARTED / 승인 대기**. Play·운영 배포·main 병합·Secret 입력은 범위 밖입니다.

외부 입력 문서: `LifeLink_Codex_Development_Roadmap.md` v1.0(2026-10-24). 실행 지시는 사용자의 G0/G1 범위에 한정하고 문서의 후속 G2~G5를 구현 승인으로 해석하지 않았습니다.

## 작업 시작 사실과 로드맵 비교

| 항목 | 로드맵/요청 | 실제 HEAD와 조정 |
| --- | --- | --- |
| Git | 최신 HEAD·미커밋 보존 | main `eb8f54f`(2.3.9 배포 기록), 작업 전 clean. 검토 브랜치 `codex/g1-field-inspection-snapshot` |
| 앱 | 기준2.3.9(47), 제안2.3.10 | 일치. 이번 검토 빌드는2.3.9(47) 유지.2.3.10/다음versionCode 확정·서명·업로드는 별도 승인 후. 같은47 개발 AAB를 기존 Play에 올리면 안 됨 |
| 플랫폼 | API24+ / target36 | 일치. compile37.0, Java21 JBR / JVM17. 도구 버전 변경 없음 |
| R1 | 기존 점검·공유·필터 재사용 | 이미 있음. 시작 snapshot 분리가 없고 live report만 표시해 G1에서 보완. 기존 R1 문서의2.3.5 표제는 당시 이력이며 최신 버전 표기가 아님 |
| Room | schema·기존 설정 보존 | v3, 6 entities, v1→v2→v3 migrations, `exportSchema=false`. `app/schemas` JSON 폴더는 없음. Models/AppDatabase DDL·기존 migration 테스트로 확인, schema/migration 변경 안 함 |
| 자동 검사 | 과거343개 통과 | 이번 수정 전343개 실제 재실행 통과. 과거 기록을 재사용해 PASS로 기재하지 않음 |
| Play | 2.3.9 심사 제출 기록 | 문서 기록만 확인. 이번 세션 Console 조회·수정·승인 판정 안 함 |
| G0 실기기 | SMS·A24/Doze·업데이트·정책 확인 | 연결 기기 없음. 미실행이며 G0 전체 통과/기관 운영 가능 상태가 아님 |
| R2 | 설계/API 스캐폴드 | 일치. 기존 `.invalid` 설계 문서만 유지, 서버·인증·운영키·인터넷·동기화 없음 |

README, Gradle, Manifest, Models/AppDatabase, 주요 SMS/부팅/시간 변경/콜백·DB·현장 점검 테스트와 배포/안정화/현장 계획/활동 감지/R2 설계 문서를 확인했습니다.

## 수정 전 실제 실행

- 환경: Windows PowerShell, Android Studio JBR `21.0.10`, SDK37.0. `JAVA_HOME`/`ANDROID_HOME`은 설치 경로만 지정했고 서명 변수/비밀번호는 입력하지 않았습니다.
- 명령: `gradlew.bat test lint assembleDebug --rerun-tasks --console=plain`.
- 결과: **BUILD SUCCESSFUL / 1분49초 / 58 tasks 실행**.
- XML 집계: **343 tests, failures0, errors0, skipped0** / 52 suites. lint: **No issues found(0)**.
- 기존 테스트용 deprecated Robolectric/Compose API, `!!`/불필요 safe-call compiler 경고가 발생했습니다. 앱 lint 경고와 구분하며 기준선 compiler 경고의 정확한 총계는 별도 파일로 집계하지 않았습니다. 대규모 SDK/API 교체로 숨기지 않았습니다.
- 기준선 보존(모두 Git 제외): `release-artifacts/roadmap-g0-2026-10-24/test-results/TEST-*.xml`, `lint-results-debug.txt`, `app-debug.apk`. 원본 HTML 보고서: `app/build/reports/tests/testDebugUnitTest/index.html`(후속 실행 시 갱신됨).
- ADB 첫 sandbox 호출은 daemon 시작에 실패했지만 권한이 허용된 재확인에서 `List of devices attached`가 빈 목록이었습니다. **실기기 시험 NOT RUN**. API24/36 Robolectric은 실제 단말이 아닙니다.

## 보호해야 하는 위험별 기준선

| 위험 | 자동화 기준선 | 실제 기기/정책 상태 |
| --- | --- | --- |
| A24/PIN·반복 걸음 | SensorMonitor/AutomaticActivity/Interaction 회귀 포함 | NOT RUN · 업무 중 오경보 보고를 해결 확정하지 않음 |
| 화면OFF/Doze·배터리·강제 종료 | 알람·서비스·watchdog 정책 검사 포함 | NOT RUN · 실행/수신 정시성 보장 못 함 |
| 실제 무활동 SMS·동일 사건 반복 금지 | InactivityDeadline/SmsDispatch/immutable incident 검사 포함 | 실제 발송·수신 NOT RUN |
| multipart·late callback·총3회·5분 간격 | SmsDecisionSafety/SmsStatusReceiver/SmsDispatchStore/RetryWorker 포함 | 통신사 전달 NOT RUN |
| 벽시계·부팅·업데이트 보존 | MonitoringStore/BootReceiver/TimeChange/migration 포함 | 실제 OEM 부팅·업데이트 NOT RUN |
| SOS5초 취소·일일 안부 | 기존 Sos/DailyCheckIn 검사 포함 | 실제 전송/취소 NOT RUN |
| 공유·권한·SIM·DB | 기존 FieldVisit/permission/migration 검사 포함 | 실제 Sharesheet/TalkBack 기기 검증 NOT RUN |
| SMS 권한·FGS·건강 앱 심사 | 코드는 기존 Manifest 유지 | 이번 세션 NOT CHECKED · 별도 심사 결과 필요 |

G0의 자동화 기준선 부분만 PASS입니다. 실제 장기 신뢰성·정책 게이트는 **BLOCKED(실기기/승인 증빙 필요)**이며 자동 검사로 대체하지 않습니다.
