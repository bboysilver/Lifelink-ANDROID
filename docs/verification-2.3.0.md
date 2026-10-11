# LifeLink 2.3.0 (38) 로컬 검증 — 2026-10-11

검증 대상은 추가 기능이 포함된 커밋 전 로컬 변경사항입니다. GitHub 푸시, 스토어 업로드나 심사 제출을 완료했다는 의미가 아닙니다.

## 실행 결과

Java 21(Android Studio JBR)에서 다음 명령을 실행했습니다. 앱 JVM 타깃은 기존 17을 유지합니다.

```powershell
.\gradlew.bat test lint assembleDebug bundleRelease --console=plain
```

- 전체 단위/Robolectric/Compose 테스트 204개: 실패 0, 오류 0, 건너뜀 0.
- lint 오류 0, 경고 77, Hint 1. 이번에 추가한 `SafetyDiagnostics`·`SafetyToolsUi`·`FamilyFollowUp` 파일의 lint 경고는 없습니다.
- 디버그 APK와 난독화 릴리스 AAB 생성 성공.
- 업그레이드 테스트는 Room v1 → v3와 v2 → v3에서 기존 연락처·설정·기록·문자 상태를 보존하는지 확인합니다.
- 200% 글자 크기에서 간편 홈 스위치, 점검 버튼·닫기, 시간·분·초 표시를 확인합니다.
- 최종 병합 Manifest에는 광고 ID·위치·SMS 수신/읽기·인터넷 권한을 추가하지 않았습니다.
- `git diff --check` 통과.

처음 Java 17에서 시도한 Robolectric 실행은 API 36 테스트가 Java 21을 요구해 실패했습니다. Java 21로 바꾼 뒤 위 전체 검증이 통과했습니다. 저장소 CI도 Java 21을 사용합니다.

## 빌드 파일

`release-artifacts/2.3.0-38/`에 버전이 명시된 파일을 보관합니다.

- `lifelink-2.3.0-38-debug.apk`: 디버그 서명 테스트용 APK.
  SHA-256: `125540EDBAE6ADA470BD53FA0F8720644BDAF4F21C021F78AC5A649282734A85`
- `lifelink-2.3.0-38-unsigned.aab`: 미서명 릴리스 AAB. `jarsigner -verify`에서 `jar is unsigned`를 확인했습니다. 그대로 Play Console에 업로드하면 안 됩니다.
  SHA-256: `8EEA6DC55D1246D400FAC305E8892C02329BD6E7E515B8FADDC5EF85BB936437`

디버그 APK는 Play 설치본의 정식 업데이트 파일이 아닙니다. 기존 실사용 앱을 삭제해 데이터가 사라지게 하지 말고 별도 테스트 기기/에뮬레이터에서 확인하세요. Play용 배포에는 기존 업로드 키를 사용하는 서명된 AAB가 필요합니다.

## 남은 실제 환경 검증

ADB에 연결된 실기기·에뮬레이터가 없으므로 이번 변경 후 실제 통신사 SMS 발송, 기기 설치·장기 절전 테스트는 실행하지 않았습니다. 이전 베타의 실제 발송 성공을 이번 버전의 실기기 검증 결과로 간주하지 않습니다.

스토어 배포 전에는 서명된 빌드와 새 개인정보처리방침을 배포하고, 베타 기기에서 기존 12시간 설정 보존·활동 후 갱신·매일 안부 자동 완료·SOS 취소·문자 발송/실패/재시도·재부팅을 확인해야 합니다. 세부 항목은 [추가 기능 명세](features-2.3.0.md)를 참고하세요.
