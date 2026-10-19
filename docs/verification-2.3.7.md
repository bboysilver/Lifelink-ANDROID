# 2.3.7 (45) 검증 기록

검증일: 2026-10-19. 이 문서는 코드·자동 검사 증빙이며 실제 기기 안전 성능이나 Play 승인 증명이 아닙니다.

## 범위와 결과

- 걸음 수 센서 보완, 관찰한 잠금 해제 전환 확인, 마지막 자동 감지 진단 분리.
- 기존 무활동 계산·SMS 사건·재시도·SIM·권한·대상 API·DB 스키마는 유지합니다.
- 기존 284개에서 30개를 추가해 **314개 테스트 통과, 실패 0, 오류 0, 건너뜀 0**.
- 전체 lint: **No issues found**. debug APK와 release AAB 로컬 빌드 성공.
- 읽기 전용 독립 검토 후 오래된 잠금 관찰을 현재 활동으로 오인할 수 있는 경로를 수정했습니다. 최근 30초의 잠금 관찰만 사용하고 장시간 공백 회귀 검사를 추가했습니다.
- 최종 소스의 서명·CI·업로드 상태는 실제 배포 기록에서 별도로 확인해야 합니다. 로컬 AAB를 서명 검증 없이 제출하지 않습니다.

## 실행 명령

Android Studio JBR(Java 21), Android SDK 37.0, Windows PowerShell 환경입니다.

```powershell
.\gradlew.bat testDebugUnitTest --tests com.example.data.ActivityEvidenceTest --tests com.example.data.AutomaticActivityStoreTest --tests com.example.data.SensorMonitorTest --tests com.example.monitoring.MonitoringServiceTest --console=plain
.\gradlew.bat test lint assembleDebug bundleRelease --console=plain
```

전체 명령 결과: `BUILD SUCCESSFUL`, 107 tasks (41 executed, 66 up-to-date). 테스트 XML의 tests/failures/errors/skipped와 `app/build/reports/lint-results-debug.txt`를 확인했습니다.

## 추가한 검사

- 자동 화면 점등·잠금 없는 재시작·오래된 잠금 상태는 새 활동이 아님.
- 잠금 해제 방송 누락 후 최근 관찰 전환 복구, 방송/폴링 중복 방지.
- 걸음 수 센서만 있는 기기, 첫 누적값·긴 공백·누적 초기화·잘못된 센서 값.
- 두 걸음 센서의 보고 간격 공유와 잠긴 기기의 단일 흔들림 제외.
- 자동 감지 원래 시각 유지, 오래된 이벤트의 타이머 되돌림 방지.
- 직접 앱 열기와 자동 기록 분리, 재시작 복원, 시각 변경, 기록 삭제 경계.
- 실제 센서 지원 표기와 큰 글자에서의 진단 화면 기존 동작.

## 남은 출시 조건

`adb devices -l`에 연결 기기가 없어 A24/PIN/삼성 절전 환경의 재현·장시간 테스트는 수행하지 못했습니다. 실제 SMS는 보내지 않았습니다. 업무 중 오탐의 단일 원인을 확정하거나 이번 수정으로 실기기 문제가 모두 해결됐다고 주장하지 않습니다.

[활동 감지 안내와 실기기 시험 순서](activity-detection-2.3.7.md)를 따라 Closed testing에서 검증해야 합니다. 강제 종료·전원 꺼짐·제조사 절전·SIM 장애와 다른 앱의 모든 사용을 관찰하지 못하는 로컬 구조의 한계는 남습니다. 공개 출시 및 서버·보호자 연결 확대는 이 결과와 별도 정책·운영 검토 후 진행합니다.
