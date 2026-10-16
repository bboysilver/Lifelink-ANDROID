# LifeLink 2.3.3 (41) Closed testing 배포 기록

기록일: 2026-10-16. 공개 출시·Google 심사 승인·실제 테스터 배포 완료를 의미하지 않습니다.

## 소스와 검증

- 최종 빌드 소스: `e6c91be715e4d111fff8a521f70033ee83eb5efd`, GitHub main 반영.
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38899681698): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38899677168): 성공. 최종 Manifest 안전 권한 검사, 엄격한 JAR 서명 검사, 예상 업로드 인증서 일치 검사를 통과했습니다.
- 로컬·서명 CI 테스트: 237개, 실패·오류·건너뜀 0개.
- CI lint: 오류 0, 경고 79, Hint 1. 로컬은 오류 0, 경고 77, Hint 1. 기존 권고 경고가 남습니다.
- [개인정보처리방침 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38899682003): 성공. [공개 페이지](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)의 HTTP 200, 2.3.3 표기, 지연 점검 시각 고지를 확인했습니다.
- 상세 원인·수정·검증 한계: [verification-2.3.3.md](verification-2.3.3.md).
- 중간 소스 `19023c2`도 CI에 성공했으나 제출하지 않았습니다. Activity 재생성을 활동으로 오인하지 않는 회귀 보완을 포함한 위 최종 소스만 제출했습니다.

## 제출 파일

- `release-artifacts/2.3.3-41/lifelink-2.3.3-41-signed.aab`, 3,585,339 bytes.
- AAB SHA-256: `5F98C5B5D0F53D1E466FBBFA3840E31EF934950340A57BAE84AB5A40D0C23D99`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 최종 CI 산출물 다운로드 후 체크섬과 공개 인증서를 다시 대조했습니다. 로컬 unsigned 파일이나 중간 소스 산출물은 제출하지 않았습니다.
- 증빙은 해당 Actions artifact와 로컬 `release-artifacts/2.3.3-41/signed-ci/`에 있습니다. 개인 키·비밀번호는 다운로드하거나 문서에 포함하지 않았습니다.

## Play Console

- 기존 **Closed testing — Closed test 1**만 업데이트했습니다. Production·다른 트랙·국가·테스터 목록은 변경하지 않았습니다.
- 이전 2.3.2 (40)이 `Available to selected testers`인 것을 확인했습니다.
- 새 릴리스: `2.3.3 (41) 앱 복귀·지연 점검·배지 개선`, 릴리스 화면 ID 14.
- Play 처리 결과: 버전 코드 41, 버전 이름 2.3.3, 최소 API 24, target SDK 36, ReTrace mapping 포함.
- 지원 기기 증감 없음. Phone 12,080, Tablet 2,745, TV 1 모델로 표시됐습니다. 설치 가능 여부와 SMS 지원 여부는 별개이며 런타임 SMS·SIM 검사를 유지합니다.
- 제출 차단 오류 없음. 네이티브 디버그 심볼 미첨부 권고 경고 1개가 남습니다.
- 저장 후 게시 개요에서 해당 트랙·버전의 변경 1건만 확인하고 `Send changes for review`를 확정했습니다.
- `1 change sent for review`, **Changes in review**, 해당 2.3.3 (41) 행을 확인했습니다.
- 제출 직후 Google 사전 자동 검사가 진행 중이었습니다. 검사 성공 후 심사로 넘어가며 승인·실제 테스터 배포는 미확인입니다.
- 증빙 화면: `release-artifacts/2.3.3-41/play-review-submitted.jpg`.

## 반드시 남겨둘 한계

Galaxy A24의 보고된 두 사건은 마감보다 각각 23시간 4분 10초, 52분 41초 늦었습니다. 두 번째는 사용자의 앱 열기 및 서비스 시작과 발송 요청이 일치합니다. 앱 복귀 전에 실제로 종일 활동을 감지했는지 확인되지 않았습니다.

이번 패치는 직접 앱 진입을 복구보다 먼저 활동으로 기록하고, 지연 문자·진단 로그를 명확히 하며, 모든 알림의 배지를 제외합니다. OS·삼성 절전으로 인한 감시 중단이나 정확한 마감 발송을 해결했다고 보증하지 않습니다. ADB 연결 실기기가 없어 실제 A24의 백그라운드·SMS·배지는 미검증입니다. 실제 무활동 경고·SOS·재시도는 유지했습니다.

승인 후 기존 앱을 삭제하지 않고 2.3.3 (41)로 업데이트해 배터리 제한을 한 번 확인하고, LifeLink를 열지 않은 채 다른 앱을 사용해도 활동이 반영되는지 재검증해야 합니다. 앱을 수시로 열어야만 오경보를 막을 수 있다면 불합격입니다. 장기 실기기 검증 전 공개 안전 서비스 출시는 계속 보류합니다.
