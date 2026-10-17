# LifeLink 2.3.4 (42) Closed testing 배포 기록

기록일: 2026-10-17. 공개 출시·Google 심사 승인·테스터 실제 배포 완료를 의미하지 않습니다.

## 최종 소스와 검증

- 최종 빌드 소스: `5c413417d07582b6d1fb1f076cde835d384d94e3`, GitHub main 반영.
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39037164932): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39037164717): 성공. 최종 Manifest 검사·엄격한 JAR 서명 검사·예상 업로드 인증서 일치 검사를 통과했습니다.
- 로컬·서명 CI 전체 테스트 251개: 실패 0, 오류 0, 건너뜀 0.
- lint: 로컬 오류 0, 경고 77, Hint 1. CI 오류 0, 경고 79, Hint 1. 기존 권고 경고가 남습니다.
- [개인정보처리방침 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39037164915): 성공. [공개 페이지](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)의 HTTP 200, 2.3.4 표기, 같은 무활동 상태의 반복 발송·새 활동 후 타이머·보호자 확인 한계 고지를 확인했습니다.
- 상세 원인·정책·수정·검증·실기기 절차: [verification-2.3.4.md](verification-2.3.4.md).
- 중간 소스 `bf960c7`도 서명 CI에 성공했으나 제출하지 않았습니다. 처리 완료 이후 시계가 뒤로 바뀌어도 새 카운트다운처럼 표시하지 않는 추가 회귀 보완까지 포함한 위 최종 소스만 제출했습니다.

## 제출 파일

- `release-artifacts/2.3.4-42/lifelink-2.3.4-42-signed.aab`, 3,590,970 bytes.
- AAB SHA-256: `1CDB211087EFCCFF73FCD491F11A959A935EC824D7945BFD4108372512B8FBB6`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 다운로드 후 CI 체크섬 및 공개 인증서를 다시 대조했습니다. 로컬 unsigned 파일·중간 소스 파일은 제출하지 않았습니다.
- 테스트·lint·서명 증빙은 Actions artifact 및 로컬 `release-artifacts/2.3.4-42/signed-ci/`에 있습니다. 개인 키·비밀번호를 다운로드하거나 문서에 포함하지 않았습니다.

## Play Console

- 기존 **Closed testing — Closed test 1**만 업데이트했습니다. Production·다른 트랙·국가·테스터 목록은 변경하지 않았습니다.
- 이전 2.3.3 (41)이 `Available to selected testers`, 10/16 22:09 출시로 표시되는 것을 확인했습니다. 보고한 기기의 실제 설치 버전은 별도로 확인해야 합니다.
- 새 릴리스: `2.3.4 (42) 경고 후 상태·타이머 안내 개선`, 릴리스 화면 ID 15.
- Play 처리 결과: 버전 코드 42, 버전 이름 2.3.4, 최소 API 24, target SDK 36, ReTrace mapping 포함.
- 이전 릴리스 대비 지원 기기 증감 없음. Phone 12,083, Tablet 2,740, TV 1 모델로 표시됐습니다. 설치 가능 여부와 실제 SMS 가능 여부는 별개로 런타임 검사를 유지합니다.
- 제출 차단 오류 없음. 네이티브 디버그 심볼 미첨부 권고 경고 1개가 남습니다.
- 릴리스를 저장하고 게시 개요에서 해당 트랙·버전의 변경 1건만 확인해 `Send changes for review`를 확정했습니다.
- `1 change sent for review`, **Changes in review**, 해당 2.3.4 (42) 행을 확인했습니다.
- 제출 직후 Google 사전 자동 검사가 진행 중이었습니다. 검사 통과 후 심사로 전달되며, 승인·새 버전 실제 테스터 배포는 미확인입니다.
- 증빙 화면: `release-artifacts/2.3.4-42/play-review-submitted.jpg`.

## 이번 변경의 의미와 한계

10/16 21:40 마지막 활동 이후 10/17 03:42 첫 문자를 받고, 계속 활동이 없어 다음 6시간 문자가 없었던 보고는 기존의 사건별 중복 방지 설계와 일치합니다. 발송을 사용자 활동으로 취급하거나 타이머를 자동 초기화하지 않습니다. 새 유효 활동이 감지되면 다음 설정 시간을 계산합니다. 실패 재시도·SOS는 유지합니다.

이번 버전은 만료된 0 타이머 대신 홈에 실제 문자 결과·활동 대기를 표시하고 실행 알림을 무음 갱신하며, 보호자 SMS에 추가 문자를 기다리지 말고 직접 확인하도록 안내합니다. 문자 발송은 보호자의 열람·응답이나 사용자의 안전 확인이 아닙니다.

실기기가 연결되어 있지 않아 실제 A24 SMS·장기 절전은 재현하지 못했습니다. 새 문자가 없다는 사실만으로 서비스가 계속 살아 있었는지 판정할 수 없습니다. 이전의 백그라운드 감시 지연 보고는 별도 실기기 검증을 계속해야 하며 공개 출시 준비가 완료됐다고 판단하지 않습니다.
