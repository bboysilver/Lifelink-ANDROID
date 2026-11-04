# G1 현장 신뢰성 변경안 — 검토용 / 제안2.3.10

2026-10-24. 기준 `eb8f54f` /2.3.9(47), 검토 브랜치 `codex/g1-field-inspection-snapshot`. **배포 버전 번호·서명·Manifest·Room을 변경하지 않았습니다.** 다음2.3.10과versionCode는 승인된 릴리스 단계에서 별도 확정해야 합니다. 현재 개발 AAB는 업로드 대상이 아닙니다.

후속 승인(2026-10-24): 사용자가 GitHub 푸시와 Play 업로드를 요청해2.3.10(48)로 버전을 올리고 기존 서명 CI를 사용합니다. 아래 표와 checksum은 최초 G0/G1 검증 당시2.3.9(47) 개발 산출물의 이력이며, 새 릴리스 서명·업로드의 결과와 혼동하면 안 됩니다. main 병합·프로덕션 전환·심사 최종 제출은 이번 후속 작업에 포함하지 않습니다.

## 체감 변화

기존 `안심 > 작동 상태 점검 > 현장 점검·요약 공유`를 재사용합니다. 새 점검 모드/감시 중지/센서 무시 기능은 없습니다.

- 진입 시 시작 기록을 고정하고 이후 앱 내 관찰과 나란히 구분합니다. 시작 기록의 서비스 응답 신선도는 캡처 시각으로 판단하며 원본 정보를 읽은 시각도 보관합니다. 과거 기록을 현재 정상으로 표시하지 않습니다.
- 마지막 전체 활동과 마지막 자동 활동을 구분합니다. 진입 전 앱 열기/조작도 이미 전체 활동에 반영됐을 수 있습니다. 시각 비교로 조작자나 실제 사람의 활동을 추론하지 않습니다.
- 조작 구분은 기본 미기록인 제한된 수동 선택입니다. 직원/이용자 표시 모두 담당자가 고른 라벨이지 Android의 신원 판정이 아닙니다.
- 설정 확인 수(7개 조건), 수동 시험기록(7종), 상태 다시 읽기 횟수를 구분합니다. 조건 수나 수동 기록은 실기기 시험 합격률·공식 동의·안전 인증이 아닙니다. 결론은 기본 미실행입니다.
- 설치/시험SMS/실제SMS/SOS·Doze·복구 등 기대 조건을 안내합니다. 실제 차이·수신 시각은 동의한 담당자의 별도 내부 시험표에 기록합니다. 자유 입력 개인정보를 새 화면에 받지 않습니다.
- 외부 진단 창에서도 최근 실제 경고(수신자1명)와 시험 문자 상태를 분리합니다. 대기·발송 확인·전달 확인·미확인·실패와 사람의 안부 확인은 다릅니다.
- 시작 기록·수동 라벨·미리보기는 UI saved-state로만 복원합니다. 점검을 닫고 다시 열면 새 세션이며 Room/모니터링 preferences에 저장하지 않습니다. 시스템이 복원하지 못하면 임시 상태가 없어질 수 있습니다.

## 개인정보·SMS diff 검토

| 경계 | 변경/유지 |
| --- | --- |
| 공유 | 기존 미리보기→별도 명시 동의→Android Sharesheet 유지. 고정 시작 기술 요약과 이후 기술 요약, 수동 enum 라벨/시험 결론만 추가. 별칭은 기존 임시코드 opt-in만 |
| 제외 | 이름·번호·정확한 활동/heartbeat/SMS 결과 시각·활동 이유·원시 센서·문자 본문·사건ID·SIM 사용자 이름·기종·담당자 신원 제외 |
| 임시 상태 | 화면 복원에 필요한 제한된 문자열만 저장. 원래 사용자 별칭/오류 본문/전화번호를 복사하지 않음. 실제 수신·열람·안전 확인으로 기록하지 않음 |
| 취소 | 공유 취소로 파일/클립보드/네트워크 전송 없음. OS 공유창을 연 것을 전송 성공으로 기록하지 않음. 선택한 외부 앱은 별도 인터넷/보관 정책을 사용할 수 있음 |
| 안전 엔진 | `data/`, `monitoring/`, ViewModel의 타이머/발송 코드 변경0. incidentID·multipart·3회 한도·5분 간격·SOS5초 취소·안부·부팅/시계 보정 유지 |
| schema/권한/도구 | AppDatabase/Models/migrations·Manifest·Gradle·의존성·CI·앱ID/min/target·서명 설정 변경0 |
| 공개 정책 | 현재 공개 개인정보처리방침·Console 선언은 변경/배포하지 않음. 후속 릴리스 승인 전에 이 G1 공유 라벨/임시 snapshot 설명과 실제 공개 방침을 재검토할 것 |

Android [UI saved-state 공식 지침](https://developer.android.com/develop/ui/compose/state-saving)에 따라 primitive-only `listSaver`와 상태 복원 검사를 사용합니다. 안전 엔진의 영속 상태와 혼합하지 않습니다.

## 상태와 검사

| 항목 | 상태 |
| --- | --- |
| G0 자동화 기준선343개 | PASS — [원본 증빙](roadmap-g0-evidence.md) |
| G1 snapshot/라벨/요약/설정·시험 진단 | DONE — 신규14개 포함357개 테스트 통과 |
| SMS·시간/부팅·콜백·migration 전체 | PASS — 기존343개를 제외하지 않고 전체 재실행, 실패·오류·skip0 |
| 실제 SIM·A24/밤샘Doze·실제 업데이트·TalkBack | NOT RUN / 실기기 연결 없음 |
| SMS/FGS/건강 앱 심사 승인 상태 | NOT CHECKED / 이번 작업에서 Console 조작 안 함 |
| 공개·기관 실사용 G0 게이트 | BLOCKED — 실제 시험·정책 증빙 필요 |
| G2 합성 DEMO / G3 Family / G4 Care / G5 결제 | NOT STARTED — 별도 승인 필요 |
| main 병합·서명 Secret·서명 CI·Play·운영 배포 | NOT PERFORMED |

## 실제 빌드·테스트 증빙

설치된 JBR21.0.10과 SDK37.0을 그대로 사용했습니다. 첫 전체 빌드는 강제 재실행으로 기존 테스트까지 컴파일했고, 마지막에는 새 lint 힌트 수정 후 동일 산출물 작업들을 다시 실행했습니다.

| 실행 | 실제 결과 |
| --- | --- |
| `gradlew.bat test lint assembleDebug bundleRelease --rerun-tasks --console=plain` | SUCCESS /1분47초 /107 tasks 실행.357 tests,53 suites, failures0/errors0/skipped0. lint 오류0·경고0·힌트1(새 refreshCount primitive state) |
| `gradlew.bat test lint assembleDebug bundleRelease --console=plain` | SUCCESS /1분23초 /107 tasks 중32 실행·75 up-to-date.357 tests,53 suites, failures0/errors0/skipped0. lint `No issues found`(오류·경고·힌트0) |
| compiler 진단 | 첫 전체 재컴파일51건(기존 테스트 API deprecated49건 및 기존 테스트의 불필요 `!!`/safe-call2건). 마지막 증분 빌드 출력0건은 테스트 소스 재컴파일이 생략된 결과이지 기존 경고를 모두 수정했다는 뜻이 아님 |
| APK 서명 | `apksigner verify --verbose --print-certs`: 검증 성공, v2=true, signer1명, `CN=Android Debug` — 배포 서명이 아님 |
| release AAB 서명 | `jarsigner -verify`: `jar is unsigned.` — 빌드 성공을 릴리스 서명 성공으로 처리하지 않음 |
| 안전 경계 | 기준 `eb8f54f` 대비 `data/`, `monitoring/`, ViewModel, Manifest, Gradle, CI diff0. `git diff --check` 통과 |

새 검사는 immutable snapshot/원본 읽기 시각·stale/future heartbeat, 수동 라벨 기본값·상태 복원·재진입, 권한/SIM/배터리 오류, 공유 허용 목록·동의/취소·미리보기 고정, 모니터링 preferences/사건 불변,200% 글꼴의 가로/어두운 테마를 다룹니다. 기존 공유 필터·권한 경계·200% 세로 화면 검사도 함께 실행했습니다. 실제 SMS 전달·밤샘 Doze·삼성 절전·TalkBack은 여전히 **NOT RUN**입니다.

최종 산출물과 원본 결과는 Git 제외 폴더 `release-artifacts/roadmap-g1-2026-10-24/`에 보존합니다. `build.log`는 최종 실행, `initial-build-with-one-hint.log`는 수정 전 힌트1건이 있던 전체 재실행입니다. `test-results/TEST-*.xml`, `lint-results-debug.txt`, `app-debug.apk`, `app-release-unsigned.aab`가 있습니다. 버전은2.3.9(47)이므로 기존 설치본을 삭제하거나 같은versionCode로 Play에 올리지 마세요.

| 최종 파일 | 크기(bytes) | SHA-256 |
| --- | ---: | --- |
| `app-debug.apk` | 19,990,736 | `092C722C8847862CEE4B3D3A961BA218E3DFF260584FD7175ABF400E66250569` |
| `app-release-unsigned.aab` | 4,186,688 | `DEC86D6A7A177A829BA2EB7A8137D42DEBCC91218854276D33902165AF626976` |

## PR 초안 요약

제목: `G1: separate inspection baseline from later observations without changing safety behavior`

변경 파일: `ui/FieldVisitSession.kt`(불변 시작 기록·Saver·제한된 라벨/시험 종류·공유 허용 목록), `ui/FieldVisitCheck.kt`(설정 진단/안내), `ui/FieldVisitCheckUi.kt`(구분 화면·수동 선택), `ui/SafetyToolsUi.kt`와 `ui/ReliableLifeLinkApp.kt`(기존 진단 창의 시험/실제SMS 연결), `FieldVisitSessionTest.kt`와 `FieldVisitCheckUiTest.kt`, 이 G0/G1 문서3개 및 README 안내.

리뷰 초점: 고정 snapshot 보존, 상태 복원/재진입, 수동 라벨 오인 금지, 민감값 허용 목록, 안전 엔진 diff0. 자동 merge/Play upload 금지. 실기기 절차는 [G1 시험표](roadmap-g1-device-test-plan.md)를 따릅니다.
