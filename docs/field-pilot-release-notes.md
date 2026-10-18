# LifeLink 2.3.5 (43) — R1 로컬 현장 파일럿

기준일: 2026-10-18. R0 기준선 + R1 로컬 MVP + R2 설계/API 계약만 포함한다. **Play 업로드·공개 출시·기관 원격 관제·서버 배포가 아니다.** 검토 브랜치에서 리뷰하고 실기기 검증 후 배포를 별도 결정한다.

## 화면과 동작

`안심 → 작동 상태 점검 → 현장 점검·요약 공유`:

- 실제 서비스 응답 시각/신선도, 관찰된 활동(사람 안전과 구분), 보호자 수, SIM 슬롯, 권한·센서·알림·비행기 모드·배터리 제한을 확인한다. 보호자 이름·번호와 SIM의 사용자 지정 이름은 새 화면에 표시하지 않는다.
- 최근 실제 SMS의 수신자 1명 결과와 최근 시험 SMS를 나눠 표시한다. 기록 시각, QUEUED/SENT/DELIVERED/FAILED/UNKNOWN을 구분한다. 최근 1건은 전체 사건 성공이 아니다. 콜백 대기 초과·미래/없는 시각은 미확인이다.
- 권한/알림·배터리 시스템 설정, 보호자/수동 시험 문자 화면으로 이동할 수 있다. 현장 점검이 초기 설정을 다시 시작하거나 문자를 자동 발송하지 않는다.
- 준비 체크리스트 7항목은 기본 미확인이고 사용자가 확인됨/조치 필요를 직접 선택한다. 점검 세션의 임시 UI 상태일 뿐 공식 동의/안전 인증으로 저장하지 않는다. 미리보기 복귀·구성 복원 시 표시를 유지하고 화면을 닫으면 종료한다.
- 공유 문구는 미리보기를 여는 시점에 고정된다. 별칭은 기본 제외이며 기존 사용자 이름이 아닌 `방문기기-XXXXXX` 임시 코드만 선택 가능하다. 공유 범위 변경 시 동의를 다시 요구한다.
- 이름·전화·정확한 활동 시각과 이유·원시 센서값·문자 원문·사건 ID는 공유하지 않는다. 기술 상태·점검 시각/시간대·앱 버전/API·한계만 허용 목록으로 생성한다.
- 동의 후 Android ACTION_SEND + 시스템 공유창만 사용한다. 앱이 네트워크 전송/수신 앱 추적/자동 클립보드 복사/수신 성공 기록을 하지 않는다. 선택한 외부 앱의 전송·보관은 해당 앱과 수신자가 관리한다.

`기록 → 가족 안부 확인 기록`:

- 전체/미기록/연락 중/직접 안전 확인/도움 필요 필터와 사건 발생 시각 최신순 표시(기존 최근 20건 범위).
- SMS 발송 인원·전달 인원과 `이 단말에 기록한 메모`를 별도 줄로 표시한다. 전달/열람 미확인을 안전 확인으로 바꾸지 않는다. 기존 수동 저장 API를 그대로 사용한다.

## 보존한 경계

SEND_SMS 발송기·콜백·수신자별 재시도/중복 방지, 무활동 사건 ID/타이머, SOS 5초 취소, 일일 안부, 센서·FGS·WorkManager·부팅 수신기는 수정하지 않았다. Room v3 유지, migration 없음. minSdk 24, applicationId와 서명 설정 유지. Manifest/의존성/CI 변경 없음. 기존 WorkManager가 합치는 WAKE_LOCK/ACCESS_NETWORK_STATE도 기존 그대로이며 새 네트워크 권한을 추가하지 않았다.

새 점검/공유/메모 코드가 활동이나 발송 상태를 직접 변경하지 않는다. 단, 기존 MainActivity는 앱을 직접 열거나 터치한 것을 활동으로 기록할 수 있다. 현장 담당자가 대신 조작한 것도 해당될 수 있어 화면에 한계를 안내한다. 점검만으로 이용자의 안전을 확정하지 않는다. 동일 무활동 사건을 N시간마다 반복 발송하지 않는 2.3.4 규칙을 유지한다.

## 변경 파일

앱:

- `app/build.gradle.kts`: 로컬 버전 2.3.5 (43)만 변경.
- `app/src/main/java/com/example/data/Models.kt`, `Daos.kt`, `LifeLinkRepository.kt`: 읽기 전용 최근 SMS 조회·전달 인원 집계, DB 스키마 그대로.
- `app/src/main/java/com/example/ui/FieldVisitCheck.kt`, `FieldVisitCheckUi.kt`: 상태 변환·공유 허용 목록·점검·체크리스트·동의/공유 UI.
- `app/src/main/java/com/example/ui/ReliableLifeLinkApp.kt`, `ReliableLifeLinkViewModel.kt`: 화면 진입·기존 데이터 연결·기록 필터·개인정보 안내.
- `app/src/main/java/com/example/ui/SafetyDiagnostics.kt`, `SafetyToolsUi.kt`: 비행기 모드 읽기·진입 버튼·SMS와 로컬 메모 라벨.

검사:

- `app/src/test/java/com/example/ui/FieldVisitCheckTest.kt`, `FieldVisitCheckUiTest.kt`, `FieldVisitPermissionBoundaryTest.kt`.
- `app/src/test/java/com/example/data/FieldVisitReadModelTest.kt`, 기존 `ui/DashboardUiTest.kt`의 진입 회귀 검사.
- `scripts/verify-care-contract.mjs`: 오프라인 계약 참조·허용 필드·동의·권한/범위 요구사항 정적 검사. 실제 서버/RBAC 검증 아님.

문서:

- `README.md`, `docs/privacy-policy.html`(미배포 변경안).
- `docs/field-pilot-test-plan.md`, `field-pilot-risk-register.md`, `field-pilot-verification.md`, 이 릴리스 노트.
- `docs/care-connected-design.md`, `care-api.openapi.json`: R2 비활성 설계/계약 스캐폴드만. 서버/웹 구현 없음.

## 검증·배포 판단

디버그 APK는 개발 시험용이며 Play 설치본과 서명이 다를 수 있다. 기존 Play 앱 위에 덮어설치가 거절되더라도 기존 앱을 삭제하지 않는다(기록·설정 손실). 별도 시험기기를 사용한다. 기존 데이터 보존의 실기기 검증은 같은 서명의 승인된 업데이트 경로로 수행해야 한다. 로컬 AAB 생성만으로 업로드·심사 승인을 의미하지 않는다.

실제 명령·테스트 수·lint·산출물은 [검증 기록](field-pilot-verification.md)에 기록한다. [수동 시험표](field-pilot-test-plan.md)의 실기기 검사는 별도이며 자동 검사로 대체하지 않는다. 2.3.4의 A24 장기 지연 보고가 이번 UI 변경만으로 해결됐다고 주장하지 않는다.

[위험 등록부](field-pilot-risk-register.md)와 [R2 설계 게이트](care-connected-design.md)를 먼저 확인한다. R1 개인정보 안내의 공개 방침 반영/Play 선언 검토, 실제 단말 장기 검사와 리뷰가 끝나기 전 자동 병합·배포하지 않는다. R2 실데이터 연결·키 생성·기관 운영은 **BLOCKED — 별도 사용자 승인·기관/보안 검토 필요**다.

공유 구현 근거: [Android Sharesheet 공식 문서](https://developer.android.com/develop/ui/compose/sharing/send). 이용자 주도 텍스트 공유이며 실제 전송·열람/안전 확인 보증 기능이 아니다.
