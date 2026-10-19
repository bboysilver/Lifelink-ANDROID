# LifeLink 2.3.7 (45) Closed testing 배포 기록

기록일: 2026-10-19. 활동 수신 보완과 진단을 추가한 테스트 릴리스입니다. 공개 출시 승인이나 A24 실기기 문제의 재현·해결 확정을 뜻하지 않습니다.

## 소스와 검증

- [PR #24](https://github.com/bboysilver/Lifelink-ANDROID/pull/24) 병합 완료.
- 빌드 소스: `ee872ebd9f3141667642996852d49e2afeb7a815`.
- main 병합 커밋: `837c3aecc8dd9d7c72723a49df2befc4bd69e98d`. 두 커밋의 전체 파일 내용이 같음을 `git diff --exit-code`로 확인했습니다. 서명 작업은 검증된 PR 소스에서 실행했고, 병합 후 앱 코드는 추가로 변경하지 않았습니다.
- [PR Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39260548672): 성공.
- [main Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39261132156): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39260843300): 성공. test/lint/bundleRelease, 최종 Manifest 권한 검사, strict jarsigner, 기존 업로드 인증서 비교와 AAB 체크섬 생성이 통과했습니다.
- 서명 CI artifact를 내려받아 **314개 테스트, 실패 0, 오류 0, 건너뜀 0**, lint `No issues found`를 다시 확인했습니다. 로컬 검사도 같은 결과입니다.
- Actions의 Gradle 9.3.1 업데이트 권고와 ubuntu-latest 이미지 이전 안내는 앱 lint 오류가 아닙니다. 이번에는 검증한 도구 조합을 유지했습니다.
- [Pages 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39261132076): 성공. [공개 방침](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)에서 2.3.7 시행일, 최신 자동 신호 1건의 로컬 진단·삭제·공유 제외를 확인했습니다.

## 제출한 서명 파일

- 경로: `release-artifacts/2.3.7-45/lifelink-2.3.7-45-signed.aab` (Git 제외).
- 크기: 4,172,037 bytes.
- AAB SHA-256: `B2073D40D632705893DA09EC21011139C12198F53056521110A406C753E7BD97`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- CI checksum과 실제 다운로드 파일의 Get-FileHash가 일치하며, keytool의 실제 AAB 인증서 지문도 같습니다. 비밀 키·비밀번호는 내려받거나 커밋하지 않았습니다. 로컬 unsigned AAB는 제출하지 않았습니다.
- 검증 증빙: `release-artifacts/2.3.7-45/submission-signed-ci/`.

## Play Console에서 확인한 상태

1. 기존 2.3.6 (44)는 선택된 테스터에게 배포된 상태였습니다.
2. Closed test 1에 `2.3.7 (45) 자동 활동 감지·점검 보완` 릴리스를 만들고 위 서명 AAB를 업로드했습니다. 이전 44 번들을 새 릴리스에 중복 포함하지 않았습니다.
3. Play 처리 결과: 45 (2.3.7), 최소 API 24+, target 36, ReTrace mapping 포함, 4 ABI/4 화면 유형. 이전 대비 지원 기기 증감 0: Phone 12,082 / Tablet 2,741 / TV 1. 기기 카탈로그 숫자는 변할 수 있고 실제 SMS 가능 여부는 기존 런타임 검사로 확인합니다.
4. 차단 오류는 없었습니다. 경고 1건은 네이티브 코드 디버그 심볼 미제공 권고이며 앱 lint 0건과 구분합니다. 이전과 같은 권고로, 동작을 변경하거나 사용할 수 없는 심볼 파일을 만들어 업로드하지 않았습니다.
5. 게시 개요의 변경이 **Closed test 1 / 2.3.7 (45) 한 건뿐**임을 확인하고 `Submit 1 change for review`와 확인창의 `Send changes for review`를 실행했습니다.
6. 최종 화면은 **Changes in review**이며 **Running quick checks**가 진행 중입니다(화면 안내 최대 14분). 사전 검사 완료 후 심사로 넘어가는 제출 요청이 접수된 상태입니다. 승인·배포 완료로 표현하지 않습니다.
7. Managed publishing off를 포함한 기존 설정은 변경하지 않았습니다. Production·다른 트랙·테스터·국가·보안 설정도 변경하지 않았습니다.

제출 증빙: `release-artifacts/2.3.7-45/play-review-submitted.jpg`. 화면에 Changes in review / Closed test 1 / 2.3.7 (45)가 함께 표시됩니다.

## 실기기와 다음 개발 단계

[정확한 감지 조건·A24 시험 순서·단계별 로드맵](activity-detection-2.3.7.md)과 [자동 검사 기록](verification-2.3.7.md)을 참고하세요. Galaxy A24/PIN 잠금/2.3.4 추정 버전의 업무 중 안전 알림 보고를 반영했지만 실제 기기가 연결되지 않아 삼성 절전 상태에서 재현하지 못했습니다.

승인 후 동일 Play 앱을 삭제 없이 업데이트하고 실제 버전, 마지막 자동 감지, 서비스 응답, PIN 잠금 해제·반복 걷기·장시간 꺼진 화면을 확인해야 합니다. 최신 버전 실기기에서 문제가 남으면 공개 출시보다 원인 확인을 우선합니다. 서버 heartbeat·보호자 연결·기관 관제·구독·iOS는 이번 릴리스에 구현·배포하지 않았습니다.
