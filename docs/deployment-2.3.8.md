# 2.3.8 (46) Closed testing 제출 기록 — 2026-10-22

## 검증한 소스·빌드

- [PR #25](https://github.com/bboysilver/Lifelink-ANDROID/pull/25): 병합 완료.
- 빌드 소스: `028c2c61aa9c1fb62fea23a67bc99f04292e02bc`.
- main 병합: `9a826d24cdca5ab8c9da1ec05464e2050b364674`. 두 커밋 전체 파일 내용이 같음을 `git diff --exit-code`로 확인했습니다. 서명·CI 완료 전 병합하지 않았습니다.
- [PR CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39782563963), [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39782558226), [main CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39783385826): 모두 성공.
- 서명 artifact에서 **327 tests / 0 failures / 0 errors / 0 skipped**, lint **No issues found**를 확인했습니다. 로컬 전체 test/lint/assembleDebug/bundleRelease도 성공했습니다.
- 최종 Manifest 권한 검사·strict jarsigner·기존 업로드 인증서 비교·AAB checksum 검증 성공. 새 권한·클라우드·SDK/DB 스키마 변경 없음.
- Actions의 Gradle 버전 권고·Ubuntu 이미지 안내는 앱 lint 문제와 다릅니다. 도구 조합은 그대로 유지했습니다.
- [Pages 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39783385823) 성공. 공개 방침의 2026-10-22 / 2.3.8과 감시 상태 확인 불가 안내를 브라우저에서 확인했습니다.

## 실제 제출 파일

- `release-artifacts/2.3.8-46/lifelink-2.3.8-46-signed.aab` (Git 제외), 4,177,673 bytes.
- SHA-256: `F2270F9D5F8DE2145F3269494B42877E84F7C724DEE08EEBB5BF7280379E76E3`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 다운로드한 실제 AAB의 Get-FileHash와 CI checksum, 실제 AAB keytool 지문이 일치했습니다. 개인 키·비밀번호는 내려받거나 변경·커밋하지 않았고 로컬 unsigned AAB는 제출하지 않았습니다.
- CI 증빙: `release-artifacts/2.3.8-46/submission-signed-ci/`.

## Console 확인·수정·제출

1. Dashboard: 12 testers / 4 days continuously, Production Inactive, Apply for production 비활성. 프로덕션 신청을 실행하지 않았습니다.
2. App content: Need attention 요구 없음, 14 Actioned 선언. SEND_SMS declared, physical safety/emergency alert 용도 선택 상태. 이 표시는 별도 권한 승인 증명이 아닙니다.
3. SMS 검토 설명에 남아 있던 GPS 전송·과도한 신뢰성 표현을 사용자 승인 후 실제 구현에 맞춰 정정했습니다. Save 후 `Your changes have been saved`를 확인했습니다. 권한 용도 선택과 선언 체크박스는 변경하지 않았습니다.
4. 기존 Closed test 1의 2.3.7 (45)는 선택된 테스터에게 배포된 상태였습니다. 같은 트랙에 `2.3.8 (46) 출시 전 문자·복구 안정화`를 만들고 검증 AAB·한국어 릴리스 노트를 올렸습니다. 이전 45 번들을 새 릴리스에 중복 포함하지 않았습니다.
5. 처리된 번들: 46 (2.3.8), API 24+, target 36, ReTrace mapping 포함, ABI 4/화면 유형 4. 지원 감소·신규 증가 모두 0. 현재 카탈로그 Phone 12,096 / Tablet 2,745 / TV 1이며 카탈로그 수치는 시점에 따라 달라집니다. 실제 SMS 가능 여부는 기존 런타임 검사 대상입니다.
6. 차단 오류 없음. 네이티브 코드 디버그 심볼 미제공 권고 1건은 이전과 같은 비차단 권고로 남아 있습니다. 번들 비교의 표시 차이는 다운로드/DEX 크기 등에 관한 정보이며 최소/대상 API나 지원 기기 축소가 아닙니다.
7. 게시 개요에서 Closed test 1 / 2.3.8 한 건과 `What you've told us: Update SMS and Call log permissions declaration`을 확인했습니다. Submit 1 change / Send changes for review를 실행하고 **Changes in review / 1 change sent for review**를 확인했습니다. 증빙 촬영 당시 자동 quick checks가 최대 14분 안내로 진행 중이므로 제출 요청 접수와 승인·배포 완료를 구분합니다.
8. 기존 Managed publishing off, 다른 트랙·테스터·국가·보안 설정은 변경하지 않았습니다. Production은 신청·배포하지 않았습니다.

증빙: `release-artifacts/2.3.8-46/play-review-submitted.jpg`, `release-artifacts/2.3.8-46/sms-declaration-corrected.jpg` (Git 제외).

저장한 SMS 설명(496자):

> LifeLink sends carrier SMS from the user's selected SIM to up to 3 registered guardians after configured inactivity, manual SOS or an optional missed daily check-in. User-triggered test SMS verifies setup. Messages include a device alias, event/activity times and battery status when available. No location is collected or sent; no SMS inbox or call logs are read. This is a supplementary safety tool. Power-off, force-stop, battery restrictions or SIM/network faults may delay or prevent alerts.

## 공개 출시 판단은 별도

[출시 전 검토·수정 내역·실기기 시험·차별화·보호자 대응](preproduction-review-2026-10-22.md)을 따릅니다. 코드·자동 검사는 실제 기기 통신사 수신 성공률이나 보호자의 대응을 증명하지 않습니다. 수정본의 장시간·보급형/구형·절전·복구 시험과 정책 심사 확인 후 공개 여부를 결정해야 합니다. 현재 24시간 유료 안전 서비스로 판매할 수 있다는 판단은 하지 않습니다.
