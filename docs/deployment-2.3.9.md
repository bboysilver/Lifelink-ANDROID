# 2.3.9 (47) Closed testing 제출 기록 — 2026-10-23

## 검증한 소스와 산출물

- [PR #26](https://github.com/bboysilver/Lifelink-ANDROID/pull/26) 병합 완료. PR 검증과 서명 CI가 모두 성공한 뒤 병합했습니다.
- 서명 빌드 소스: `e3d5392fdeb7db8f8b47e0f507219ddac5968a6d`.
- main 병합: `5251042d5b167950701f6c234c465d25cd802389`. `git diff --exit-code`로 두 커밋의 전체 파일 내용이 동일함을 확인했습니다.
- [PR CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39790646741), [서명 릴리스 CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39790644127), [main CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39947920531): 모두 성공. main 검증까지 완료한 뒤 AAB를 업로드했습니다.
- 서명 CI 증빙의 XML 집계: **343 tests / 0 failures / 0 errors / 0 skipped**, lint **No issues found**. 로컬 전체 `test lint assembleDebug bundleRelease`도 성공했습니다.
- CI의 최종 Manifest 검사, strict jarsigner, 인증서와 checksum 검증 성공. min API24 / target36, 기존 권한·기능 조건 유지. 새 클라우드·결제·광고·위치 기능 없음.
- [개인정보처리방침 Pages 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39947920560): 성공. 공개 URL에서 실제 2.3.9 방침과 부팅·경과 시간 기준값의 로컬 처리 설명을 확인했습니다.

## 실제 파일

- `release-artifacts/2.3.9-47/lifelink-2.3.9-47-signed.aab` (Git 제외), **4,180,549 bytes**.
- SHA-256: `A962BB1450C985CDB3799600995753F8E6CB60F5E515530D0ADBA813BC492B4B`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 다운로드한 실제 AAB의 Get-FileHash와 CI checksum, keytool로 읽은 실제 AAB 인증서 지문이 일치했습니다. 개인 키·비밀번호는 내려받지 않았으며 로컬 unsigned 파일은 업로드하지 않습니다.
- CI 증빙 폴더: `release-artifacts/2.3.9-47/submission-signed-ci/` (Git 제외).

## Console 작업

1. 재개 시 기존 2.3.8 (46)은 `Available to selected testers / Released on Oct22`였습니다.
2. 기존 Closed test1에 `2.3.9 (47) 문자 저장·재부팅·감시 복구 안정화`를 작성하고 위에서 검증한 서명 AAB와 한국어 변경 안내를 업로드했습니다. 이전46 번들은 새 릴리스에 포함하지 않았습니다.
3. Console 처리 결과:47 (2.3.9), API24+, target36, ABI4/화면유형4, ReTrace mapping 포함. 지원 감소0 / 신규 지원0. 현재 카탈로그 Phone12,102 / Tablet2,744 / TV1이며 수치는 시점에 따라 달라집니다. 실제 SMS 가능 여부는 기존 런타임 검사 대상입니다.
4. 차단 오류0. 이전과 같은 네이티브 디버그 심볼 미제공 권고1건은 비차단 권고로 남아 있습니다. 공급되지 않은 심볼을 가짜 파일로 업로드하거나 작동 중인 의존성을 제거하지 않았습니다.
5. Save / Go to overview 후 게시 개요에 Closed test1의2.3.9 한 건만 있는지 확인했습니다. `Submit1change for review`와 최종 `Send changes for review`를 실행했습니다.
6. 최종 **Changes in review**와2.3.9 항목을 확인했습니다. 증빙 당시 quick checks는 최대14분 안내로 진행 중입니다. 이는 심사 전송 요청 접수이며 사전 검사 통과·승인·테스터 배포 완료를 뜻하지 않습니다.
7. Managed publishing off 유지. Production, 다른 트랙·테스터·국가·보안 설정·정책 선언은 변경하지 않았습니다.

증빙: `release-artifacts/2.3.9-47/play-review-submitted.jpg` (Git 제외). 배포 후 테스터는 앱 버전2.3.9/47을 확인하고 기존 설정 보존과 최종 실기기 시험을 진행해야 합니다.

## 출시 판단

[프로덕션 안정화 검증](production-hardening-2.3.9.md)의 수정 범위와 남은 실기기·정책 조건을 유지합니다. 코드·343개 자동 검사와 서명 검증이 실제 SIM 수신률, OEM 절전 신뢰성, 별도 SMS 권한 승인 또는 프로덕션 자격을 증명하지는 않습니다. 공개 출시나 유료 안전 서비스로 전환하는 작업은 실행하지 않았습니다.
