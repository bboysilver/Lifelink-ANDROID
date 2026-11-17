# 2.3.12 (50) 프로덕션 제출 기록

2026-11-17. 사용자가 Closed testing 완료를 알리고 새 번들 생성 및 리뷰 보내기를 요청했습니다.

## 변경 범위

- 기준 `a54b166`은 2.3.11 코드 `1c6defa` 이후 배포 문서만 변경된 상태입니다. 작업 시작 시 깨끗한 작업 트리이며 원격 FETCH_HEAD와 일치했습니다.
- 버전 49/2.3.11을 50/2.3.12로 변경하고 현재 버전 안내 및 릴리스 노트만 정리합니다. 새 기능이나 새로운 버그 수정을 주장하지 않습니다.
- 활동 감지·무활동/SOS/선택 안부 문자·콜백/재시도·Room 스키마·기존 설정·권한·의존성·서명 설정을 변경하지 않습니다. 2.3.11 대비 동작 변경 없는 출시 후보입니다.
- 기존 검토 브랜치 `codex/g1-field-inspection-snapshot`을 사용하며 main 병합은 하지 않습니다.

## 시작 시 Console 확인

- Dashboard: Google Play production access가 승인되었습니다. Production은 아직 Inactive입니다. 프로덕션 접근 신청을 다시 작성하거나 제출할 필요가 없습니다.
- Closed test 1: 2.3.11(49), 11월 5일 Google Play 테스터 제공/Full rollout 표시. 최고 업로드 versionCode는 49입니다.
- 게시 개요에는 기존 국가 설정 2건(177개 국가·지역 및 나머지 지역)이 미제출 상태였습니다. Managed publishing off입니다. 국가 범위와 승인 후 자동 게시를 사용자에게 확인 중이며, 테스트 완료 진술만으로 구체적인 실기기 시험 횟수·성공률·의견 내용을 만들어 기록하지 않습니다.

## 검증 및 제출 상태

첫 전체 실행은 362개/53개 스위트 실패·오류·건너뜀 0이지만, 린트의 Robolectric 4.18 신규 버전 안내(`NewerVersionAvailable`)가 warningsAsErrors에 의해 실패 처리됐습니다. 앱 실행 오류나 보안 취약점 탐지 결과가 아닙니다. 검증된 테스트 전용 4.17을 유지하고 이 버전 항목에만 해당 안내를 억제하는 주석을 추가했습니다. 다른 린트/안전 검사는 그대로이며 4.18 이행은 별도 검증 과제입니다.

- 로컬 재실행 `test lint assembleDebug bundleRelease --rerun-tasks --console=plain`: 성공, 1분 32초, 107개 작업 실행. 새 결과 XML 53개 스위트/362개 테스트, 실패·오류·건너뜀 0. Lint `No issues found`. 일부 deprecated API 및 테스트 픽스처 CloseGuard 경고는 별개입니다.
- 로컬 AAB는 미서명 개발 산출물이며 업로드하지 않습니다. `bundletool validate`, min24/target36, 2.3.12(50), 기존 권한 11개 동일, PAGE_ALIGNMENT_16K 및 4개 ABI ELF LOAD 16KB 정렬을 확인했습니다. 디버그 APK `zipalign -c -P 16 4`도 통과했습니다.
- [서명 CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/44067756386)는 소스 `5697ccd30000bafcded591c94ad79b14c5414a5b`에서 성공(5분 4초). 다운로드한 결과도 362개 테스트 실패·오류·건너뜀 0, Lint 문제 없음입니다. CI의 keystore 기반 strict 서명 검사와 예상 인증서 대조도 통과했습니다.
- 업로드용 `release-artifacts/2.3.12-50/lifelink-2.3.12-50-signed.aab`(Git 제외): 4,197,164바이트, SHA-256 `B50EB2DDFAD9BDAD308503BCB734DA4F5C0ED8AEF9BB325FDE44BAE4EAECEF25`. CI 체크섬과 일치합니다. 업로드 인증서 SHA-256 `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`는 기존 인증서와 같습니다.
- 실제 서명 파일의 `bundletool validate`, 버전/권한/16KB 설정과 네이티브 정렬 검사도 통과했습니다. 로컬 `jarsigner`는 `jar verified`이며 truststore 미지정 자체 서명/체인/타임스탬프 및 기존 POSIX/JarInputStream 순서 경고는 남습니다. 경고를 없애려고 번들을 재압축하거나 서명 키를 내려받지 않았습니다.
- 별도 [PR 검증](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/44067765104) 최초 실행은 Robolectric `MavenArtifactFetcher`의 `IOException`으로 테스트 초기화가 실패(138개 보고/36개 실패)했습니다. 같은 소스의 두 번째 실행은 3분 18초에 테스트·린트·디버그 빌드 모두 성공했습니다. 최초 실패를 숨기거나 통과로 처리하지 않습니다.
- 실제 SMS/삼성 절전/업데이트 현장 시험은 이번 작업에서 실행하지 않았습니다. 사용자의 테스트 완료 보고와 이번 자동 검증을 구분합니다.

## Play 업로드 결과 및 남은 확인

- Production 트랙에 검증된 서명 AAB 50(2.3.12)와 한국어 출시 안내를 업로드·저장했습니다. 기존 Closed test 1이나 테스터 목록을 수정하지 않았습니다.
- 릴리스 검토의 비차단 경고 1개는 네이티브 디버그 심볼 미제공입니다. 업로드 파일은 API 24+, target36, 4개 ABI와 ReTrace 매핑을 인식했습니다.
- 자동 사전 검사가 `android.hardware.telephony` 필수 선언으로 인한 기기 제한을 제시했습니다. 이는 SIM 문자 발송이 핵심이고 인터넷 문자 대체 수단이 없는 앱의 의도된 호환성 제한입니다. `Proceed anyway > Other`에 이 사실을 설명해 처리했으며 Manifest를 optional로 바꾸거나 Wi-Fi 전용 기기가 지원된다고 주장하지 않았습니다.
- 게시 개요에 Production 릴리스 및 기존 국가 설정 2건, 총 3건이 미제출 상태입니다. `Submit 3 changes for review` 버튼은 활성화됐고 자동 quick checks는 당시 진행 중입니다.
- 현재 저장된 전 세계 범위(177개 국가·지역 + 나머지 지역)와 `Managed publishing off`에 따른 승인 후 자동 공개를 사용자에게 확인 요청했습니다. **해당 선택 답변을 받기 전에는 최종 리뷰 보내기를 누르지 않았습니다. 심사 제출·승인·공개 출시 완료가 아닙니다.**
- 화면 증빙: `release-artifacts/2.3.12-50/production-ready-for-review.jpg`(Git 제외). 기존 서명·테스터·권한 선언·국가 설정·게시 방식은 변경하지 않았습니다.
