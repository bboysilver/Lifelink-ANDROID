# LifeLink 2.3.6 (44) Closed testing 배포 기록

기록일: 2026-10-18. 공개 출시 또는 Google 심사 승인 기록과 구분합니다.

## 소스와 검증

- [PR #21](https://github.com/bboysilver/Lifelink-ANDROID/pull/21) 병합 후 좁은 버전 고정 예외와 새 lint 경고 차단을 추가했습니다.
- 실제 제출용 소스: main `0008f54103bea835c6f2e642be72b835576e2116`. 이후 배포 문서 커밋은 앱 소스 변경이 아닙니다.
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39134710270): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39134710531): 성공. 최종 Manifest, 엄격한 JAR 서명, 예상 업로드 인증서 일치 검사 통과.
- 로컬·최종 서명 CI 테스트 284개: 실패 0, 오류 0, 건너뜀 0.
- 최종 서명 CI lint: 오류 0, 경고 0, Hint 0. 텍스트 `No issues found.`, XML issue 요소 0개를 다운로드 후 확인했습니다.
- 기존 77개는 라이브러리/빌드 업데이트·KTX·미사용 리소스·기기 필터 관련 권고였습니다. 필요한 동기 저장/Boolean 결과와 검증된 버전은 좁게 예외 처리했습니다. 전체 검사를 끄거나 baseline으로 숨기지 않았습니다.
- `warningsAsErrors=true`로 새 미검토 lint 경고는 빌드를 차단합니다. Actions 자체의 Gradle 업데이트/Ubuntu 호스트 이전 안내와 테스트 API deprecation 안내는 Android lint와 별개이며 남습니다.
- [상세 검증 기록](verification-2.3.6.md)에 중간 CI의 2개/1개 권고와 보완 과정을 구분합니다. 해당 중간 파일은 업로드하지 않았습니다.
- [Pages 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/39134710266): 성공. [공개 방침](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)에서 2.3.6 표기, 로컬 처리·동의 후 수동 점검 공유·기능 한계를 확인했습니다.

## 제출 파일

- `release-artifacts/2.3.6-44/lifelink-2.3.6-44-signed.aab`, 4,167,642 bytes.
- SHA-256: `724636B1C3B96EB06A8AA2F81745792C14844F0EA9ABF3147DB93E8B70CD7CB2`.
- 실제 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- CI의 해시·인증서 증빙과 다운로드한 AAB의 실제 SHA-256·keytool 인증서를 대조하고 복사한 업로드 파일도 다시 해시 확인했습니다.
- 증빙 폴더: `release-artifacts/2.3.6-44/submission-signed-ci/`. 개인 키·비밀번호는 내려받거나 커밋하지 않았습니다. 로컬 unsigned AAB는 사용하지 않았습니다.

## Play Console

- 대상은 기존 **Closed testing — Closed test 1**만입니다. Production·alpha·Internal testing·국가·테스터 목록은 변경하지 않습니다.
- 기존 2.3.4 (42)는 `Available to selected testers`, Oct 17 11:10 PM 배포로 표시됐습니다.
- 새 릴리스 이름: `2.3.6 (44) 현장 점검·검증 경고 정리`.
- Play 파일 처리 결과: 44 (2.3.6), 최소 API 24+, target 36, ReTrace mapping 포함, 4 ABI/화면 유형 4개. 이전 대비 지원 기기 증감 0: Phone 12,082 / Tablet 2,741 / TV 1. 기기 카탈로그 숫자는 시점에 따라 변하고 설치 가능과 실제 SMS 가능은 별개이므로 런타임 SIM 검사를 유지합니다.
- 제출 차단 오류 없음. Play 권고 1개는 네이티브 디버그 심볼 미첨부입니다. AAB의 네이티브 파일은 AndroidX `libandroidx.graphics.path.so` 4 ABI이며 자체 C/C++ 소스가 아닙니다. 이번 파일에 일치하는 심볼은 포함되지 않았고 관계없는 파일을 대신 첨부하지 않았습니다. 향후 네이티브 크래시 진단용 심볼 관리가 남으며, lint 0개 또는 제출 가능과 구분합니다.
- 기존 Data safety를 읽기 전용으로 확인했습니다: 수집/공유 질문 Yes, 전송 암호화 No, 계정 생성/외부 로그인 없음, Name/SMS or MMS/Diagnostics, 제3자 공유 없음(명시적 동의·이용자 주도 전송 예외), 기존 방침 URL입니다. 선언 답변은 변경하거나 재저장하지 않았습니다.
- 새 릴리스를 저장하고 게시 개요의 변경이 **Closed test 1 / 2.3.6 (44) 한 건뿐**임을 확인했습니다. 기존 Managed publishing off 설정을 변경하지 않았습니다.
- `Submit 1 change for review` → `Send changes for review`를 확정했고 **Changes in review** 아래 해당 트랙·버전 행이 표시됐습니다.
- 제출 직후 Google 사전 자동 검사 `Running quick checks ...`, 최대 14분 남음으로 표시됐습니다. 성공적으로 끝나면 심사로 전달되며, 이는 심사 승인·실제 테스터 배포 완료가 아닙니다. Production에는 제출하지 않았습니다.
- 제출 증빙: `release-artifacts/2.3.6-44/play-review-submitted.jpg`. 화면에 Changes in review / Closed test 1 / 2.3.6 (44)가 함께 표시됩니다.

## 한계

기존 SIM 기기에서 실제 시험/무활동 SMS 수신 성공은 사용자의 보고이며, 최신 2.3.6 실기기 인증으로 대체하지 않습니다. Android 7/API 24, target API 36, SIM SMS·타이머·사건별 중복 방지·재시도·기존 DB/권한을 유지합니다. R1 현장 점검·수동 공유·가족 기록 필터가 추가되지만 기관 서버/원격 관제/결제는 구현·배포하지 않습니다.

승인 후 같은 Play 서명 업데이트로 기존 설정·연락처 보존과 실제 시험 문자, 활동 갱신, 무활동 경고, A24 절전/재부팅 동작을 계속 검증해야 합니다. 기존 앱을 삭제해 덮어설치 문제를 해결하지 않습니다. 문자 수신은 보호자의 열람·사용자의 안전을 증명하지 않으며 공개 출시 준비가 완료됐다는 판정이 아닙니다.
